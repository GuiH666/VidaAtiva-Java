package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.service.Validacao;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Acesso à tabela {@code alunos} em conjunto com {@code usuarios} (nome, e-mail, login) e {@code planos} (nome e valor).
 *
 * <p>Quem usa: a tela Alunos ({@code AlunosPanel}: gerente e funcionário editam, professor só consulta); as telas de fichas e
 * avaliações (combo de alunos ativos); {@code PagamentoDAO} (gera mensalidades a partir dos alunos ativos); e a API do site
 * ({@code DadosBanco}, endpoint {@code /api/aluno/me}).
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>cadastrar cria o login (perfil ALUNO) e a ficha cadastral na mesma transação;</li>
 *   <li>exclusão é lógica: {@link #definirAtivo} desativa aluno e login, mantendo histórico (pagamentos, fichas, avaliações);</li>
 *   <li>{@code vencimento} é a data do PRÓXIMO vencimento da mensalidade: é definida no cadastro/edição
 *       ({@link #proximoVencimento}) e avançada um mês por {@code PagamentoDAO.registrarPagamento};</li>
 *   <li>{@code dataInicio} ("cliente desde") é gravada como a data de hoje no cadastro e nunca mais muda;</li>
 *   <li>tipo de cliente e forma de pagamento só aceitam os valores dos ENUMs do schema.sql.</li>
 * </ul>
 */
public class AlunoDAO {
    // Valores permitidos = ENUMs alunos.tipo_cliente e alunos.forma_pagamento (database/schema.sql).
    // Conferimos aqui para mostrar uma mensagem clara em vez do erro genérico do MySQL.
    private static final Set<String> TIPOS = Set.of("CONVENCIONAL", "TOTALPASS");
    private static final Set<String> FORMAS = Set.of("PIX", "CARTAO", "BOLETO", "DINHEIRO");

    // Traz o aluno "completo" numa consulta só: alunos (a) + login em usuarios (u: nome, e-mail, username) + plano (p).
    // O nome do plano usa o alias "plano" para não se confundir com a coluna plano_id. JOIN simples basta porque
    // usuario_id e plano_id são NOT NULL com chave estrangeira. Termina com espaço: cada método acrescenta WHERE/ORDER BY.
    private static final String SELECT =
            "SELECT a.id, a.usuario_id, u.nome, u.email, u.username, a.documento, a.telefone, a.data_nascimento, "
            + "a.data_inicio, a.plano_id, p.nome AS plano, p.valor_mensal, a.tipo_cliente, a.forma_pagamento, "
            + "a.vencimento, a.observacoes, a.ativo "
            + "FROM alunos a JOIN usuarios u ON u.id = a.usuario_id JOIN planos p ON p.id = a.plano_id ";

    /**
     * Cadastra o aluno: cria o login (perfil ALUNO) e a ficha cadastral na mesma transação (tudo ou nada).
     * Os campos id/usuarioId/dataInicio do objeto são ignorados; o login usa a senha informada (mínimo 6 caracteres).
     * Nome e e-mail são gravados sem espaços nas pontas, e-mail e username em minúsculas.
     * Retorna o id do aluno (tabela {@code alunos}).
     *
     * @throws IllegalArgumentException se algum dado for inválido (veja {@code validar})
     * @throws SQLException em erro de banco; username, e-mail ou documento já cadastrados viram mensagem amigável
     */
    public int cadastrar(Aluno a, String senha) throws SQLException {
        validar(a);
        String username = Validacao.username(a.username());
        Validacao.senha(senha);
        // CPF já conferido e com máscara padrão: grava sempre no mesmo formato para a chave UNIQUE barrar duplicidade.
        String documento = Validacao.documento(a.documento());
        String sql = "INSERT INTO alunos(usuario_id,documento,telefone,data_nascimento,data_inicio,plano_id,tipo_cliente,"
                + "forma_pagamento,vencimento,observacoes,ativo) VALUES(?,?,?,?,?,?,?,?,?,?,1)";
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Primeiro o login, depois o aluno com o usuario_id gerado. Se o segundo INSERT falhar, o rollback desfaz os dois.
                int usuarioId = UsuarioDAO.criar(c, a.nome().trim(), a.email().trim().toLowerCase(), username, senha, "ALUNO");
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, usuarioId);
                    ps.setString(2, documento);
                    ps.setString(3, a.telefone().trim());
                    Sql.setData(ps, 4, a.dataNascimento());
                    // "Cliente desde" é sempre hoje; o dataInicio que vier no objeto é ignorado de propósito.
                    Sql.setData(ps, 5, LocalDate.now());
                    ps.setInt(6, a.planoId());
                    ps.setString(7, a.tipoCliente());
                    ps.setString(8, a.formaPagamento());
                    Sql.setData(ps, 9, a.vencimento());
                    Sql.setTexto(ps, 10, a.observacoes());
                    ps.executeUpdate();
                    int alunoId;
                    try (ResultSet rs = ps.getGeneratedKeys()) { alunoId = rs.next() ? rs.getInt(1) : 0; }
                    c.commit();
                    return alunoId;
                }
            } catch (SQLException e) {
                c.rollback();
                throw Sql.amigavel(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Atualiza o cadastro do aluno (login em {@code usuarios} + dados em {@code alunos}) numa transação.
     * O username e a data de início não mudam, e a situação (ativo) só muda por {@link #definirAtivo}.
     * Se {@code novaSenha} vier preenchida, troca a senha do aluno.
     * {@code a.id()} e {@code a.usuarioId()} devem ser do mesmo aluno: um atualiza {@code alunos}, o outro {@code usuarios}.
     * Se o id não existir nada é alterado, sem erro.
     *
     * @throws IllegalArgumentException se algum dado for inválido (veja {@code validar})
     * @throws SQLException em erro de banco (e-mail ou documento repetidos viram mensagem amigável)
     */
    public void atualizar(Aluno a, String novaSenha) throws SQLException {
        validar(a);
        String documento = Validacao.documento(a.documento()); // mesmo formato padrão do cadastro
        String sql = "UPDATE alunos SET documento=?, telefone=?, data_nascimento=?, plano_id=?, tipo_cliente=?, "
                + "forma_pagamento=?, vencimento=?, observacoes=? WHERE id=?";
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                UsuarioDAO.atualizarDados(c, a.usuarioId(), a.nome().trim(), a.email().trim().toLowerCase(), novaSenha);
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, documento);
                    ps.setString(2, a.telefone().trim());
                    Sql.setData(ps, 3, a.dataNascimento());
                    ps.setInt(4, a.planoId());
                    ps.setString(5, a.tipoCliente());
                    ps.setString(6, a.formaPagamento());
                    Sql.setData(ps, 7, a.vencimento());
                    Sql.setTexto(ps, 8, a.observacoes());
                    ps.setInt(9, a.id());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw Sql.amigavel(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Desativa (exclusão lógica) ou reativa o aluno e o login dele. Os dados e o histórico são mantidos.
     * Um único UPDATE com JOIN altera {@code alunos} e {@code usuarios}; aluno inativo não consegue fazer login
     * (o login é desativado junto) e deixa de aparecer em {@link #listar(boolean) listar(false)} e de receber novas mensalidades.
     */
    public void definirAtivo(int alunoId, boolean ativo) throws SQLException {
        String sql = "UPDATE alunos a JOIN usuarios u ON u.id = a.usuario_id SET a.ativo = ?, u.ativo = ? WHERE a.id = ?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, ativo);
            ps.setBoolean(2, ativo);
            ps.setInt(3, alunoId);
            ps.executeUpdate();
        }
    }

    /**
     * Lista os alunos em ordem alfabética pelo nome.
     * @param incluirInativos {@code false} traz só os ativos (uso normal); {@code true} traz também os desativados
     */
    public List<Aluno> listar(boolean incluirInativos) throws SQLException {
        String sql = SELECT + (incluirInativos ? "" : "WHERE a.ativo = 1 ") + "ORDER BY u.nome";
        List<Aluno> lista = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /**
     * Busca pelo id da tabela {@code alunos}. Traz também alunos inativos.
     * @return o aluno ou {@code null} se não existir
     */
    public Aluno buscarPorId(int alunoId) throws SQLException {
        return buscar(SELECT + "WHERE a.id = ?", alunoId);
    }

    /**
     * Busca pelo id do login ({@code usuarios.id}). Usado pela API do site para achar o aluno do usuário autenticado.
     * Traz também alunos inativos: quem chama ({@code DadosBanco}) é que descarta os inativos.
     * @return o aluno ou {@code null} se esse usuário não for aluno
     */
    public Aluno buscarPorUsuarioId(int usuarioId) throws SQLException {
        return buscar(SELECT + "WHERE a.usuario_id = ?", usuarioId);
    }

    /** Executa um SELECT que recebe um único {@code ?} numérico e devolve a primeira linha (ou null). */
    private Aluno buscar(String sql, int id) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Calcula a próxima data de vencimento para o dia do mês escolhido, sempre DEPOIS de {@code hoje}.
     * Exemplos: dia 10 estando em 05/03 dá 10/03; estando em 10/03 ou 20/03 dá 10/04; dia 31 em fevereiro vira o último
     * dia do mês. Usado pela tela de alunos ao cadastrar ou quando o dia de vencimento é trocado.
     *
     * @param diaDoMes dia desejado (1 a 31)
     * @param hoje data de referência (parâmetro para facilitar testes)
     */
    public static LocalDate proximoVencimento(int diaDoMes, LocalDate hoje) {
        // Math.min evita erro em meses curtos (ex.: dia 31 em abril vira 30).
        LocalDate candidata = hoje.withDayOfMonth(Math.min(diaDoMes, hoje.lengthOfMonth()));
        if (candidata.isAfter(hoje)) return candidata;
        // O dia deste mês já passou (ou é hoje): usa o mês seguinte. Parte do dia 1 e só depois ajusta o dia,
        // usando o tamanho do mês seguinte, não o do mês atual.
        LocalDate proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        return proximoMes.withDayOfMonth(Math.min(diaDoMes, proximoMes.lengthOfMonth()));
    }

    /**
     * Regras de preenchimento do aluno, compartilhadas por cadastrar e atualizar. Não devolve nada: lança
     * {@link IllegalArgumentException} com mensagem pronta para a tela. Nome, e-mail, documento, telefone, plano, tipo,
     * forma de pagamento e vencimento são obrigatórios; a data de nascimento é opcional, mas não pode estar no futuro.
     */
    private void validar(Aluno a) {
        Validacao.obrigatorio(a.nome(), "Nome");
        Validacao.email(a.email());
        Validacao.documento(a.documento());
        Validacao.telefone(a.telefone());
        if (a.planoId() <= 0) throw new IllegalArgumentException("Selecione o plano.");
        if (!TIPOS.contains(a.tipoCliente())) throw new IllegalArgumentException("Tipo de cliente inválido.");
        if (!FORMAS.contains(a.formaPagamento())) throw new IllegalArgumentException("Selecione a forma de pagamento.");
        if (a.vencimento() == null) throw new IllegalArgumentException("Selecione o dia de vencimento.");
        if (a.dataNascimento() != null && a.dataNascimento().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("A data de nascimento não pode ser no futuro.");
    }

    private Aluno mapear(ResultSet rs) throws SQLException {
        return new Aluno(rs.getInt("id"), rs.getInt("usuario_id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("username"), rs.getString("documento"), rs.getString("telefone"),
                Sql.data(rs, "data_nascimento"), Sql.data(rs, "data_inicio"), rs.getInt("plano_id"),
                rs.getString("plano"), rs.getBigDecimal("valor_mensal"), rs.getString("tipo_cliente"),
                rs.getString("forma_pagamento"), Sql.data(rs, "vencimento"), rs.getString("observacoes"),
                rs.getBoolean("ativo"));
    }
}
