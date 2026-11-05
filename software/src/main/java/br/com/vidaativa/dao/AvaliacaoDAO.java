package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.AvaliacaoFisica;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso à tabela {@code avaliacoes_fisicas} (peso, altura e % de gordura em uma data), com os nomes do aluno e do
 * professor vindos de {@code usuarios}.
 *
 * <p>Quem usa: a tela Avaliações físicas do professor ({@code AvaliacoesPanel}) e a API do site ({@code DadosBanco},
 * {@code GET /api/aluno/me/avaliacoes}, via {@link #listarPorAluno}).
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>só existe registrar e excluir: uma avaliação não é editada (para corrigir, exclui-se e registra-se outra);</li>
 *   <li>o IMC não é gravado: é calculado por {@code AvaliacaoFisica.imc()} (peso / altura ao quadrado);</li>
 *   <li>este DAO não confere se o plano do aluno inclui avaliação ({@code Precificacao.incluiAvaliacao}, só Premium):
 *       essa checagem fica na tela.</li>
 * </ul>
 */
public class AvaliacaoDAO {
    // Avaliação (v) com o nome do aluno e o do professor. Os dois nomes estão na MESMA tabela, usuarios; por isso ela é
    // ligada duas vezes, com aliases diferentes: ua (via alunos a) e up (via professores p), gerando aluno_nome e
    // professor_nome. O alias v é porque "a" já é de alunos. Termina com espaço: cada método acrescenta WHERE/ORDER BY.
    private static final String SELECT =
            "SELECT v.id, v.aluno_id, v.professor_id, ua.nome AS aluno_nome, up.nome AS professor_nome, v.data_avaliacao, "
            + "v.peso, v.altura, v.percentual_gordura, v.observacoes "
            + "FROM avaliacoes_fisicas v "
            + "JOIN alunos a ON a.id = v.aluno_id JOIN usuarios ua ON ua.id = a.usuario_id "
            + "JOIN professores p ON p.id = v.professor_id JOIN usuarios up ON up.id = p.usuario_id ";

    /**
     * Todas as avaliações de todos os alunos, da mais recente para a mais antiga (empate: maior id primeiro).
     */
    public List<AvaliacaoFisica> listar() throws SQLException {
        return consultar(SELECT + "ORDER BY v.data_avaliacao DESC, v.id DESC", null);
    }

    /**
     * Avaliações de um aluno, da mais recente para a mais antiga.
     * @param alunoId id da tabela {@code alunos}
     */
    public List<AvaliacaoFisica> listarPorAluno(int alunoId) throws SQLException {
        return consultar(SELECT + "WHERE v.aluno_id = ? ORDER BY v.data_avaliacao DESC, v.id DESC", alunoId);
    }

    /**
     * Grava uma nova avaliação física. Não usa transação: é um único INSERT.
     * Regras: aluno e professor informados, data obrigatória e não futura, peso em kg (maior que 0 e até 500),
     * altura em METROS (de 0,5 a 2,6; ex.: 1,75), percentual de gordura opcional (de 0 a 100).
     *
     * @return o id da avaliação criada
     * @throws IllegalArgumentException se algum valor violar as regras acima
     * @throws SQLException em erro de banco (ex.: aluno ou professor inexistente, em mensagem amigável)
     */
    public int registrar(AvaliacaoFisica v) throws SQLException {
        if (v.alunoId() <= 0) throw new IllegalArgumentException("Selecione o aluno.");
        if (v.professorId() <= 0) throw new IllegalArgumentException("Professor não identificado.");
        if (v.data() == null) throw new IllegalArgumentException("Informe a data da avaliação.");
        if (v.data().isAfter(LocalDate.now())) throw new IllegalArgumentException("A data da avaliação não pode ser no futuro.");
        // Peso e altura são obrigatórios aqui, embora as colunas aceitem NULL no banco: é esta checagem que garante.
        // Os limites (500 kg, 0,5 a 2,6 m) são apenas para barrar erro de digitação, como altura em centímetros.
        if (v.peso() == null || v.peso().signum() <= 0 || v.peso().compareTo(new BigDecimal("500")) > 0)
            throw new IllegalArgumentException("Informe um peso válido (em kg).");
        if (v.altura() == null || v.altura().compareTo(new BigDecimal("0.5")) < 0 || v.altura().compareTo(new BigDecimal("2.6")) > 0)
            throw new IllegalArgumentException("Informe a altura em metros (ex.: 1,75).");
        if (v.percentualGordura() != null && (v.percentualGordura().signum() < 0 || v.percentualGordura().compareTo(new BigDecimal("100")) > 0))
            throw new IllegalArgumentException("Percentual de gordura deve estar entre 0 e 100.");
        String sql = "INSERT INTO avaliacoes_fisicas(aluno_id,professor_id,data_avaliacao,peso,altura,percentual_gordura,observacoes) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, v.alunoId());
            ps.setInt(2, v.professorId());
            Sql.setData(ps, 3, v.data());
            Sql.setDecimal(ps, 4, v.peso());
            Sql.setDecimal(ps, 5, v.altura());
            Sql.setDecimal(ps, 6, v.percentualGordura());
            Sql.setTexto(ps, 7, v.observacoes());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) {
            throw Sql.amigavel(e);
        }
    }

    /**
     * Exclui a avaliação (exclusão definitiva, sem histórico). Se o id não existir, nada acontece (sem exceção).
     */
    public void excluir(int id) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM avaliacoes_fisicas WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Executa o SELECT recebido e converte cada linha em {@link AvaliacaoFisica}.
     * @param alunoId se não for null, é ligado ao único {@code ?} do SQL; {@code null} quando o SQL não tem filtro
     */
    private List<AvaliacaoFisica> consultar(String sql, Integer alunoId) throws SQLException {
        List<AvaliacaoFisica> lista = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            // Só o SQL de listarPorAluno tem "?"; em listar() alunoId é null e não há parâmetro para preencher.
            if (alunoId != null) ps.setInt(1, alunoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new AvaliacaoFisica(rs.getInt("id"), rs.getInt("aluno_id"), rs.getInt("professor_id"),
                            rs.getString("aluno_nome"), rs.getString("professor_nome"), Sql.data(rs, "data_avaliacao"),
                            rs.getBigDecimal("peso"), rs.getBigDecimal("altura"), rs.getBigDecimal("percentual_gordura"),
                            rs.getString("observacoes")));
                }
            }
        }
        return lista;
    }
}
