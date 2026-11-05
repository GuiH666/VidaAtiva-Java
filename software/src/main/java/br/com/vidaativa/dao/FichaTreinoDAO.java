package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Exercicio;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Treino;
import br.com.vidaativa.service.Validacao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Acesso às fichas de treino: {@code fichas_treino} (cabeçalho), {@code treinos} (Treino A, B, ... da ficha) e
 * {@code exercicios} (de cada treino). A hierarquia é ficha 1-N treinos 1-N exercícios, com ON DELETE CASCADE:
 * apagar a ficha apaga treinos e exercícios. Lê {@code alunos}, {@code professores} e {@code usuarios} para os nomes.
 *
 * <p>Quem usa: as telas de fichas do professor ({@code FichasPanel} e {@code FichaDialog}) e a API do site
 * ({@code DadosBanco}, {@code GET /api/aluno/me/fichas}, via {@link #listarAtivasDoAluno}).
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>status RASCUNHO (só o professor vê), ATIVA (o aluno vê no site) ou INATIVA (histórico);</li>
 *   <li>cada aluno tem no máximo uma ficha ATIVA: salvar uma ATIVA passa as outras dele para INATIVA;</li>
 *   <li>ficha ATIVA precisa de pelo menos um exercício; frequência semanal, se informada, vai de 1 a 7;</li>
 *   <li>salvar grava a ficha inteira numa transação e RECRIA treinos e exercícios, então os ids deles mudam a cada edição;</li>
 *   <li>este DAO não confere se o plano do aluno inclui ficha ({@code Precificacao.incluiFicha}): essa regra fica na tela.</li>
 * </ul>
 */
public class FichaTreinoDAO {
    // Valores do ENUM fichas_treino.status (database/schema.sql).
    private static final Set<String> STATUS = Set.of("RASCUNHO", "ATIVA", "INATIVA");

    // Cabeçalho da ficha (f) com o nome do aluno e o do professor. Os dois nomes estão na MESMA tabela, usuarios;
    // por isso ela é ligada duas vezes, com aliases diferentes: ua (via alunos a) e up (via professores p),
    // resultando em aluno_nome e professor_nome. Não traz treinos/exercícios (veja carregarTreinos).
    // Termina com espaço: cada método acrescenta WHERE/ORDER BY.
    private static final String SELECT =
            "SELECT f.id, f.aluno_id, f.professor_id, ua.nome AS aluno_nome, up.nome AS professor_nome, f.nome, f.objetivo, "
            + "f.frequencia_semanal, f.data_inicio, f.data_revisao, f.observacoes, f.status "
            + "FROM fichas_treino f "
            + "JOIN alunos a ON a.id = f.aluno_id JOIN usuarios ua ON ua.id = a.usuario_id "
            + "JOIN professores p ON p.id = f.professor_id JOIN usuarios up ON up.id = p.usuario_id ";

    /**
     * Todas as fichas, sem os treinos (só o resumo para a tabela da tela). Ordem: ATIVA, RASCUNHO, INATIVA; depois nome do aluno.
     * Para a ficha completa use {@link #buscar}.
     */
    public List<FichaTreino> listar() throws SQLException {
        List<FichaTreino> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY FIELD(f.status,'ATIVA','RASCUNHO','INATIVA'), ua.nome, f.id DESC");
             ResultSet rs = ps.executeQuery()) {
            // Lista vazia de treinos de propósito: carregar tudo aqui seria lento e a tabela não precisa disso.
            while (rs.next()) lista.add(mapear(rs, List.of()));
        }
        return lista;
    }

    /**
     * Uma ficha completa, com treinos e exercícios. {@code null} se não existir.
     * @param fichaId id da tabela {@code fichas_treino}
     */
    public FichaTreino buscar(int fichaId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(SELECT + "WHERE f.id = ?")) {
            ps.setInt(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapear(rs, carregarTreinos(c, fichaId));
            }
        }
    }

    /**
     * Fichas ATIVAS de um aluno, completas (com treinos e exercícios). É isso que o aluno vê no site.
     * Normalmente vem no máximo uma, pois {@link #salvar} inativa as anteriores. Mais recentes (maior id) primeiro.
     */
    public List<FichaTreino> listarAtivasDoAluno(int alunoId) throws SQLException {
        List<FichaTreino> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE f.aluno_id = ? AND f.status = 'ATIVA' ORDER BY f.id DESC")) {
            ps.setInt(1, alunoId);
            try (ResultSet rs = ps.executeQuery()) {
                // Reaproveita a mesma conexão para buscar os treinos de cada ficha enquanto percorre o ResultSet
                // (funciona porque o driver MySQL, por padrão, já trouxe o resultado inteiro para a memória).
                while (rs.next()) lista.add(mapear(rs, carregarTreinos(c, rs.getInt("id"))));
            }
        }
        return lista;
    }

    /**
     * Cria (id = 0) ou atualiza a ficha inteira, com treinos e exercícios, numa única transação.
     * Ao salvar como ATIVA, as outras fichas ativas do mesmo aluno passam para INATIVA
     * (o aluno sempre vê uma ficha atual). Retorna o id da ficha.
     *
     * <p>Na edição, treinos e exercícios antigos são apagados e recriados a partir do objeto recebido; o professor da
     * ficha (professor_id) só é gravado na criação e não muda ao editar.
     *
     * @throws IllegalArgumentException se faltar aluno/professor/nome, status inválido, frequência fora de 1 a 7, revisão antes
     *         do início, ficha ATIVA sem exercício, treino ou exercício sem nome, ou séries menores que 1
     * @throws SQLException em erro de banco (inclusive aluno ou professor inexistente, em mensagem amigável)
     */
    public int salvar(FichaTreino f) throws SQLException {
        // Todas as validações acontecem antes de abrir a conexão: dado inválido não encosta no banco.
        if (f.alunoId() <= 0) throw new IllegalArgumentException("Selecione o aluno.");
        if (f.professorId() <= 0) throw new IllegalArgumentException("Professor não identificado.");
        String nome = Validacao.obrigatorio(f.nome(), "Nome da ficha");
        if (!STATUS.contains(f.status())) throw new IllegalArgumentException("Status inválido.");
        if (f.frequenciaSemanal() != null && (f.frequenciaSemanal() < 1 || f.frequenciaSemanal() > 7))
            throw new IllegalArgumentException("Frequência semanal deve ser de 1 a 7 dias.");
        if (f.dataInicio() != null && f.dataRevisao() != null && f.dataRevisao().isBefore(f.dataInicio()))
            throw new IllegalArgumentException("A data de revisão não pode ser antes do início.");
        List<Treino> treinos = f.treinos() == null ? List.of() : f.treinos();
        if ("ATIVA".equals(f.status())) {
            // Rascunho pode ficar vazio; ficha ATIVA vazia apareceria em branco para o aluno no site.
            boolean temExercicio = treinos.stream().anyMatch(t -> t.exercicios() != null && !t.exercicios().isEmpty());
            if (!temExercicio) throw new IllegalArgumentException("Uma ficha ATIVA precisa ter pelo menos um exercício. Salve como rascunho ou adicione exercícios.");
        }
        for (Treino t : treinos) {
            Validacao.obrigatorio(t.nome(), "Nome do treino");
            if (t.exercicios() == null) continue;
            for (Exercicio e : t.exercicios()) {
                Validacao.obrigatorio(e.nome(), "Nome do exercício (treino \"" + t.nome() + "\")");
                if (e.series() != null && e.series() < 1) throw new IllegalArgumentException("Séries deve ser um número maior que zero (" + e.nome() + ").");
            }
        }

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // id == 0 significa ficha nova (INSERT); qualquer outro valor é edição (UPDATE da ficha existente).
                int fichaId = f.id();
                if (fichaId == 0) {
                    String sql = "INSERT INTO fichas_treino(aluno_id,professor_id,nome,objetivo,frequencia_semanal,data_inicio,data_revisao,observacoes,status) "
                            + "VALUES(?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, f.alunoId());
                        ps.setInt(2, f.professorId());
                        ps.setString(3, nome);
                        Sql.setTexto(ps, 4, f.objetivo());
                        Sql.setInteiro(ps, 5, f.frequenciaSemanal());
                        Sql.setData(ps, 6, f.dataInicio());
                        Sql.setData(ps, 7, f.dataRevisao());
                        Sql.setTexto(ps, 8, f.observacoes());
                        ps.setString(9, f.status());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("Não foi possível criar a ficha.");
                            fichaId = rs.getInt(1);
                        }
                    }
                } else {
                    // Repare que professor_id não está no UPDATE: quem criou a ficha continua sendo o professor dela.
                    String sql = "UPDATE fichas_treino SET aluno_id=?, nome=?, objetivo=?, frequencia_semanal=?, data_inicio=?, "
                            + "data_revisao=?, observacoes=?, status=? WHERE id=?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setInt(1, f.alunoId());
                        ps.setString(2, nome);
                        Sql.setTexto(ps, 3, f.objetivo());
                        Sql.setInteiro(ps, 4, f.frequenciaSemanal());
                        Sql.setData(ps, 5, f.dataInicio());
                        Sql.setData(ps, 6, f.dataRevisao());
                        Sql.setTexto(ps, 7, f.observacoes());
                        ps.setString(8, f.status());
                        ps.setInt(9, fichaId);
                        ps.executeUpdate();
                    }
                    // Em vez de comparar treino a treino, apaga todos e recria abaixo. Os exercícios saem junto por ON DELETE CASCADE.
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM treinos WHERE ficha_id = ?")) { // exercícios saem por CASCADE
                        ps.setInt(1, fichaId);
                        ps.executeUpdate();
                    }
                }

                // Para cada treino: insere o treino (precisa do id gerado) e depois os exercícios dele, todos de uma vez (batch).
                // Esses INSERTs ainda estão dentro da transação: se algo falhar, a ficha antiga não é perdida.
                for (Treino t : treinos) {
                    int treinoId;
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO treinos(ficha_id,nome,dia_semana,descricao) VALUES(?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, fichaId);
                        ps.setString(2, t.nome().trim());
                        Sql.setTexto(ps, 3, t.diaSemana());
                        Sql.setTexto(ps, 4, t.descricao());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); treinoId = rs.getInt(1); }
                    }
                    if (t.exercicios() == null) continue;
                    String exSql = "INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes) VALUES(?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = c.prepareStatement(exSql)) {
                        for (Exercicio e : t.exercicios()) {
                            ps.setInt(1, treinoId);
                            ps.setString(2, e.nome().trim());
                            Sql.setTexto(ps, 3, e.grupoMuscular());
                            Sql.setInteiro(ps, 4, e.series());
                            Sql.setTexto(ps, 5, e.repeticoes());
                            Sql.setTexto(ps, 6, e.carga());
                            Sql.setTexto(ps, 7, e.descanso());
                            Sql.setTexto(ps, 8, e.observacoes());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                // Só depois de gravar tudo: se esta ficha é ATIVA, as outras ATIVAS do mesmo aluno viram INATIVA
                // ("id <> ?" poupa a que acabou de ser salva). Na mesma transação, o aluno nunca fica com duas ativas
                // nem sem nenhuma por causa de uma falha no meio.
                if ("ATIVA".equals(f.status())) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE fichas_treino SET status='INATIVA' WHERE aluno_id = ? AND id <> ? AND status = 'ATIVA'")) {
                        ps.setInt(1, f.alunoId());
                        ps.setInt(2, fichaId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
                return fichaId;
            } catch (SQLException e) {
                c.rollback();
                throw Sql.amigavel(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Exclui a ficha e seus treinos/exercícios (ON DELETE CASCADE). O cadastro do aluno não é afetado.
     * É definitivo: para guardar o histórico, salve a ficha como INATIVA em vez de excluir.
     */
    public void excluir(int fichaId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM fichas_treino WHERE id = ?")) {
            ps.setInt(1, fichaId);
            ps.executeUpdate();
        }
    }

    /**
     * Quantidade de fichas ATIVAS (de todos os alunos). No momento nenhuma tela chama este método:
     * o painel inicial usa a mesma contagem dentro de {@code ResumoDAO}.
     */
    public int contarAtivas() throws SQLException {
        try (Connection c = Database.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fichas_treino WHERE status = 'ATIVA'")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Carrega os treinos da ficha (por id) e, para cada um, seus exercícios. Faz uma consulta de exercícios por treino:
     * não é o jeito mais eficiente, mas as fichas são pequenas e o código fica simples.
     * Recebe a conexão aberta por quem chama.
     */
    private List<Treino> carregarTreinos(Connection c, int fichaId) throws SQLException {
        List<Treino> treinos = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, nome, dia_semana, descricao FROM treinos WHERE ficha_id = ? ORDER BY id")) {
            ps.setInt(1, fichaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    treinos.add(new Treino(rs.getInt("id"), rs.getString("nome"), rs.getString("dia_semana"),
                            rs.getString("descricao"), new ArrayList<>()));
                }
            }
        }
        // Treino é um record (imutável): não dá para colocar exercícios num Treino já criado. Por isso os treinos foram
        // lidos com lista vazia e aqui são recriados já com os exercícios.
        List<Treino> completos = new ArrayList<>();
        for (Treino t : treinos) {
            List<Exercicio> exs = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, nome, grupo_muscular, series, repeticoes, carga, descanso, observacoes FROM exercicios WHERE treino_id = ? ORDER BY id")) {
                ps.setInt(1, t.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        exs.add(new Exercicio(rs.getInt("id"), rs.getString("nome"), rs.getString("grupo_muscular"),
                                Sql.inteiro(rs, "series"), rs.getString("repeticoes"), rs.getString("carga"),
                                rs.getString("descanso"), rs.getString("observacoes")));
                    }
                }
            }
            completos.add(new Treino(t.id(), t.nome(), t.diaSemana(), t.descricao(), exs));
        }
        return completos;
    }

    private FichaTreino mapear(ResultSet rs, List<Treino> treinos) throws SQLException {
        return new FichaTreino(rs.getInt("id"), rs.getInt("aluno_id"), rs.getInt("professor_id"), rs.getString("aluno_nome"),
                rs.getString("professor_nome"), rs.getString("nome"), rs.getString("objetivo"),
                Sql.inteiro(rs, "frequencia_semanal"), Sql.data(rs, "data_inicio"), Sql.data(rs, "data_revisao"),
                rs.getString("observacoes"), rs.getString("status"), treinos);
    }
}
