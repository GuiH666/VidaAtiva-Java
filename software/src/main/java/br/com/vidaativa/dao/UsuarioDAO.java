package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.security.PasswordUtil;
import br.com.vidaativa.service.Validacao;

import java.sql.*;

/**
 * Acesso à tabela {@code usuarios}: o login de TODOS os perfis (GERENTE, FUNCIONARIO, PROFESSOR e ALUNO).
 * Aqui ficam nome, e-mail, username, hash da senha, perfil e a flag {@code ativo}. Os dados próprios de cada perfil
 * ficam em {@code funcionarios}, {@code professores} e {@code alunos} (cada uma com {@code usuario_id} único),
 * atendidas por FuncionarioDAO, ProfessorDAO e AlunoDAO.
 *
 * <p>Quem usa: {@code AutenticacaoService} (login do software Swing, tela LoginFrame) e {@code DadosBanco}
 * (login e troca de senha do aluno no site, via {@code ApiServer}).
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>a senha nunca é gravada em texto: {@code PasswordUtil.hash} gera o hash PBKDF2 com salt;</li>
 *   <li>o login aceita e-mail OU username, e usuário com {@code ativo = 0} não consegue entrar;</li>
 *   <li>os métodos {@code static} sem {@code public} no fim da classe recebem uma {@link Connection} aberta pelo DAO
 *       chamador e NÃO fazem commit/rollback: assim o usuário e a linha do perfil (aluno, professor ou funcionário)
 *       são gravados na mesma transação.</li>
 * </ul>
 */
public class UsuarioDAO {

    /**
     * Confere login e senha.
     * Efeito colateral: se o hash gravado ainda estiver no formato antigo (SHA-256 puro), ele é regravado em PBKDF2
     * no primeiro login bem-sucedido.
     *
     * @param login e-mail ou username (espaços nas pontas são ignorados)
     * @param senha senha em texto, como digitada
     * @return o {@link Usuario} (sem a senha) ou {@code null} se faltar dado, o login não existir, a senha estiver errada
     *         ou o usuário estiver inativo
     * @throws SQLException se o banco estiver inacessível
     */
    public Usuario autenticar(String login, String senha) throws SQLException {
        if (login == null || login.isBlank() || senha == null || senha.isEmpty()) return null;
        String sql = "SELECT id, nome, email, perfil, ativo, senha_hash FROM usuarios WHERE (email = ? OR username = ?) AND ativo = 1";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login.trim());
            ps.setString(2, login.trim());
            try (ResultSet rs = ps.executeQuery()) {
                // While e não if: e-mail e username são UNIQUE separadamente, então o mesmo texto pode, em tese,
                // casar com duas linhas (e-mail de um usuário e username de outro). Vale a que tiver a senha correta.
                while (rs.next()) {
                    String hash = rs.getString("senha_hash");
                    if (!PasswordUtil.verificar(senha, hash)) continue;
                    Usuario u = new Usuario(rs.getInt("id"), rs.getString("nome"), rs.getString("email"),
                            rs.getString("perfil"), rs.getBoolean("ativo"));
                    // Migração silenciosa: só aqui temos a senha em texto, então é a única hora de trocar o hash antigo pelo novo.
                    if (PasswordUtil.precisaAtualizar(hash)) definirSenha(c, u.id(), senha);
                    return u;
                }
            }
        }
        return null;
    }

    /**
     * Busca um usuário pelo id da tabela {@code usuarios}. Não filtra por {@code ativo} e não traz a senha.
     * @return o usuário ou {@code null} se não existir
     */
    public Usuario buscarPorId(int id) throws SQLException {
        String sql = "SELECT id, nome, email, perfil, ativo FROM usuarios WHERE id = ?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Usuario(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)) : null;
            }
        }
    }

    /**
     * Troca a senha exigindo a senha atual. Usado pelo aluno no site ({@code POST /api/aluno/me/senha}).
     *
     * @return {@code true} se trocou; {@code false} se o usuário não existe/está inativo ou a senha atual estiver errada
     * @throws IllegalArgumentException se a nova senha tiver menos de 6 caracteres (validado antes de consultar o banco)
     * @throws SQLException se o banco estiver inacessível
     */
    public boolean alterarSenha(int usuarioId, String senhaAtual, String novaSenha) throws SQLException {
        Validacao.senha(novaSenha);
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT senha_hash FROM usuarios WHERE id = ? AND ativo = 1")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verificar(senhaAtual, rs.getString(1))) return false;
            }
            definirSenha(c, usuarioId, novaSenha);
            return true;
        }
    }

    // ---------- operações reutilizadas dentro de transações ----------
    // Daqui para baixo: operações usadas por outros DAOs dentro das transações deles. Recebem a Connection pronta,
    // não abrem nem fecham conexão e não fazem commit; quem chama é responsável por commit/rollback.

    /**
     * Insere uma linha em {@code usuarios} (sempre {@code ativo = 1}) e devolve o id gerado.
     * A senha chega em texto e é gravada como hash. Não valida nada: quem chama deve validar antes com {@code Validacao}.
     * E-mail ou username repetidos disparam {@link SQLException} (erro 1062), que o DAO chamador traduz com {@code Sql.amigavel}.
     *
     * @param perfil um dos valores do ENUM da coluna: GERENTE, FUNCIONARIO, PROFESSOR ou ALUNO
     */
    static int criar(Connection c, String nome, String email, String username, String senha, String perfil) throws SQLException {
        String sql = "INSERT INTO usuarios(nome,email,username,senha_hash,perfil,ativo) VALUES(?,?,?,?,?,1)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setString(2, email);
            ps.setString(3, username);
            ps.setString(4, PasswordUtil.hash(senha));
            ps.setString(5, perfil);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("Não foi possível criar o usuário.");
                return rs.getInt(1);
            }
        }
    }

    /**
     * Atualiza nome e e-mail do usuário; se {@code novaSenha} não for vazia, troca a senha também.
     * Username e perfil nunca mudam por aqui. Senha null ou vazia significa "manter a atual" (campo em branco na tela de edição).
     *
     * @throws IllegalArgumentException se a nova senha foi informada e tem menos de 6 caracteres
     */
    static void atualizarDados(Connection c, int usuarioId, String nome, String email, String novaSenha) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET nome = ?, email = ? WHERE id = ?")) {
            ps.setString(1, nome);
            ps.setString(2, email);
            ps.setInt(3, usuarioId);
            ps.executeUpdate();
        }
        if (novaSenha != null && !novaSenha.isEmpty()) definirSenha(c, usuarioId, Validacao.senha(novaSenha));
    }

    /**
     * Grava o hash de uma nova senha. Não valida o tamanho: {@code alterarSenha} e {@code atualizarDados} validam antes;
     * no login, é usada só para regravar a mesma senha no formato novo.
     */
    static void definirSenha(Connection c, int usuarioId, String senha) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET senha_hash = ? WHERE id = ?")) {
            ps.setString(1, PasswordUtil.hash(senha));
            ps.setInt(2, usuarioId);
            ps.executeUpdate();
        }
    }
}
