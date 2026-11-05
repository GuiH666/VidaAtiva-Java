package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.service.Validacao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso à tabela {@code professores} (especialidade e situação) em conjunto com {@code usuarios} (nome, e-mail, login).
 *
 * <p>Quem usa: a tela Equipe ({@code EquipePanel}, gerente) para cadastrar e manter professores; e as telas de fichas de
 * treino e avaliações físicas, que chamam {@link #buscarPorUsuarioId} para descobrir qual professor está logado, pois
 * {@code fichas_treino} e {@code avaliacoes_fisicas} guardam o id de {@code professores}, não o do usuário.
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>cadastrar cria o login (perfil PROFESSOR) e a linha de professor na mesma transação;</li>
 *   <li>a especialidade é opcional (vazia vira NULL);</li>
 *   <li>não há exclusão: desativar ({@link #definirAtivo}) bloqueia também o login e preserva as fichas e avaliações antigas.</li>
 * </ul>
 */
public class ProfessorDAO {
    // Junta professores (p) com usuarios (u): especialidade e ativo estão em professores; nome, e-mail e username só em usuarios.
    // Termina com espaço para cada método acrescentar o seu ORDER BY/WHERE.
    private static final String SELECT =
            "SELECT p.id, p.usuario_id, u.nome, u.email, u.username, p.especialidade, p.ativo "
            + "FROM professores p JOIN usuarios u ON u.id = p.usuario_id ";

    /**
     * Cadastra um professor (login com perfil PROFESSOR) e retorna o id em {@code professores}.
     * As duas inserções (usuarios e professores) ocorrem numa transação: se uma falhar, nada é gravado.
     *
     * @throws IllegalArgumentException se nome vazio, e-mail ou username inválidos ou senha com menos de 6 caracteres
     * @throws SQLException em erro de banco; e-mail ou username já usados viram mensagem amigável ({@code Sql.amigavel})
     */
    public int cadastrar(String nome, String email, String username, String senha, String especialidade) throws SQLException {
        nome = Validacao.obrigatorio(nome, "Nome");
        email = Validacao.email(email);
        username = Validacao.username(username);
        Validacao.senha(senha);
        especialidade = Validacao.opcional(especialidade);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Os dois INSERTs (usuarios e professores) estão na mesma transação (autoCommit desligado acima): sem isso,
                // uma falha no segundo deixaria um login órfão em usuarios.
                int usuarioId = UsuarioDAO.criar(c, nome, email, username, senha, "PROFESSOR");
                try (PreparedStatement p = c.prepareStatement("INSERT INTO professores(usuario_id,especialidade,ativo) VALUES(?,?,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    p.setInt(1, usuarioId);
                    Sql.setTexto(p, 2, especialidade);
                    p.executeUpdate();
                    c.commit();
                    try (ResultSet rs = p.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : 0; }
                }
            } catch (SQLException e) {
                c.rollback();
                throw Sql.amigavel(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Lista todos os professores, ativos e inativos, em ordem alfabética pelo nome do login. */
    public List<Professor> listar() throws SQLException {
        List<Professor> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY u.nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /**
     * Descobre o professor a partir do usuário logado (necessário para gravar fichas e avaliações).
     * Não filtra por {@code ativo}.
     * @param usuarioId id da tabela {@code usuarios} (o do {@code Usuario} autenticado)
     * @return o professor ou {@code null} se esse usuário não tiver linha em {@code professores}
     */
    public Professor buscarPorUsuarioId(int usuarioId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "WHERE p.usuario_id = ?")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Atualiza nome, e-mail e especialidade; se {@code novaSenha} vier preenchida, troca a senha. Username e perfil não mudam.
     * Transação sobre {@code usuarios} + {@code professores}.
     *
     * @param professorId id da tabela {@code professores} (não é o id do usuário)
     * @throws SQLException "Professor não encontrado." se o id não existir, ou erro de banco (e-mail repetido, etc.)
     * @throws IllegalArgumentException para dados inválidos (nome, e-mail ou nova senha curta)
     */
    public void atualizar(int professorId, String nome, String email, String especialidade, String novaSenha) throws SQLException {
        nome = Validacao.obrigatorio(nome, "Nome");
        email = Validacao.email(email);
        especialidade = Validacao.opcional(especialidade);
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Nome, e-mail e senha ficam em usuarios; por isso primeiro descobrimos o usuario_id deste professor.
                int usuarioId;
                try (PreparedStatement ps = c.prepareStatement("SELECT usuario_id FROM professores WHERE id = ?")) {
                    ps.setInt(1, professorId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Professor não encontrado.");
                        usuarioId = rs.getInt(1);
                    }
                }
                UsuarioDAO.atualizarDados(c, usuarioId, nome, email, novaSenha);
                try (PreparedStatement ps = c.prepareStatement("UPDATE professores SET especialidade = ? WHERE id = ?")) {
                    Sql.setTexto(ps, 1, especialidade);
                    ps.setInt(2, professorId);
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
     * Ativa ou desativa o professor e o login dele de uma só vez (desativado não consegue entrar no software).
     * Um único UPDATE com JOIN altera {@code professores} e {@code usuarios}. Se o id não existir, nada acontece (sem exceção).
     * Fichas e avaliações já gravadas continuam ligadas a ele.
     */
    public void definirAtivo(int professorId, boolean ativo) throws SQLException {
        String sql = "UPDATE professores p JOIN usuarios u ON u.id = p.usuario_id SET p.ativo = ?, u.ativo = ? WHERE p.id = ?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, ativo);
            ps.setBoolean(2, ativo);
            ps.setInt(3, professorId);
            ps.executeUpdate();
        }
    }

    private Professor mapear(ResultSet rs) throws SQLException {
        return new Professor(rs.getInt("id"), rs.getInt("usuario_id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("username"), rs.getString("especialidade"), rs.getBoolean("ativo"));
    }
}
