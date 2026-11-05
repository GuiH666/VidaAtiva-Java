package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Funcionario;
import br.com.vidaativa.service.Validacao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso à tabela {@code funcionarios} (cargo e situação) em conjunto com {@code usuarios} (nome, e-mail, login e perfil).
 *
 * <p>Quem usa: a tela Equipe ({@code EquipePanel}/{@code PessoalPanel}), exclusiva do gerente.
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>cadastrar cria o login (perfil FUNCIONARIO) e a linha de funcionário na mesma transação;</li>
 *   <li>o gerente também tem linha em {@code funcionarios} (perfil GERENTE, cargo "Gerente da Academia"); ele aparece em
 *       {@link #listar()}, mas este DAO só cadastra FUNCIONARIO. A proteção contra desativar o gerente ou o próprio
 *       usuário logado está na tela ({@code PessoalPanel}), não aqui;</li>
 *   <li>não há exclusão: desativar ({@link #definirAtivo}) é a forma de "remover" e também bloqueia o login.</li>
 * </ul>
 */
public class FuncionarioDAO {
    // Junta funcionarios (f) com usuarios (u): cargo e ativo estão em funcionarios; nome, e-mail, username e perfil
    // só existem em usuarios. O campo "ativo" lido é o de f (definirAtivo mantém f.ativo e u.ativo iguais).
    // Termina com espaço para cada método acrescentar o seu ORDER BY/WHERE.
    private static final String SELECT =
            "SELECT f.id, f.usuario_id, u.nome, u.email, u.username, f.cargo, u.perfil, f.ativo "
            + "FROM funcionarios f JOIN usuarios u ON u.id = f.usuario_id ";

    /**
     * Cadastra um funcionário (login com perfil FUNCIONARIO) e retorna o id em {@code funcionarios}.
     * As duas inserções (usuarios e funcionarios) ocorrem numa transação: se uma falhar, nada é gravado.
     *
     * @throws IllegalArgumentException se nome/cargo estiverem vazios, e-mail ou username inválidos ou senha com menos de 6 caracteres
     * @throws SQLException em erro de banco; e-mail ou username já usados viram mensagem amigável ({@code Sql.amigavel})
     */
    public int cadastrar(String nome, String email, String username, String senha, String cargo) throws SQLException {
        nome = Validacao.obrigatorio(nome, "Nome");
        email = Validacao.email(email);
        username = Validacao.username(username);
        Validacao.senha(senha);
        cargo = Validacao.obrigatorio(cargo, "Cargo");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Os dois INSERTs (usuarios e funcionarios) estão na mesma transação (autoCommit desligado acima): sem isso,
                // uma falha no segundo deixaria um login órfão em usuarios.
                int usuarioId = UsuarioDAO.criar(c, nome, email, username, senha, "FUNCIONARIO");
                try (PreparedStatement f = c.prepareStatement("INSERT INTO funcionarios(usuario_id,cargo,ativo) VALUES(?,?,1)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    f.setInt(1, usuarioId);
                    f.setString(2, cargo);
                    f.executeUpdate();
                    c.commit();
                    try (ResultSet rs = f.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : 0; }
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
     * Lista todos os funcionários, ativos e inativos (inclusive o gerente), em ordem alfabética pelo nome do login.
     */
    public List<Funcionario> listar() throws SQLException {
        List<Funcionario> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY u.nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    /**
     * Atualiza nome, e-mail e cargo. Se {@code novaSenha} vier preenchida, troca a senha. Username e perfil não mudam.
     * Transação sobre {@code usuarios} + {@code funcionarios}.
     *
     * @param funcionarioId id da tabela {@code funcionarios} (não é o id do usuário)
     * @throws SQLException "Funcionário não encontrado." se o id não existir, ou erro de banco (e-mail repetido, etc.)
     * @throws IllegalArgumentException para dados inválidos (nome, e-mail, cargo ou nova senha curta)
     */
    public void atualizar(int funcionarioId, String nome, String email, String cargo, String novaSenha) throws SQLException {
        nome = Validacao.obrigatorio(nome, "Nome");
        email = Validacao.email(email);
        cargo = Validacao.obrigatorio(cargo, "Cargo");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Nome, e-mail e senha ficam em usuarios; por isso primeiro descobrimos o usuario_id deste funcionário.
                int usuarioId;
                try (PreparedStatement ps = c.prepareStatement("SELECT usuario_id FROM funcionarios WHERE id = ?")) {
                    ps.setInt(1, funcionarioId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Funcionário não encontrado.");
                        usuarioId = rs.getInt(1);
                    }
                }
                UsuarioDAO.atualizarDados(c, usuarioId, nome, email, novaSenha);
                try (PreparedStatement ps = c.prepareStatement("UPDATE funcionarios SET cargo = ? WHERE id = ?")) {
                    ps.setString(1, cargo);
                    ps.setInt(2, funcionarioId);
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
     * Ativa ou desativa o funcionário e o login dele de uma só vez (desativado não consegue entrar no software).
     * Um único UPDATE com JOIN altera as duas tabelas, então não há estado intermediário. Se o id não existir, nada acontece
     * (sem exceção). Não impede desativar o gerente; essa checagem é feita pela tela.
     */
    public void definirAtivo(int funcionarioId, boolean ativo) throws SQLException {
        String sql = "UPDATE funcionarios f JOIN usuarios u ON u.id = f.usuario_id SET f.ativo = ?, u.ativo = ? WHERE f.id = ?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, ativo);
            ps.setBoolean(2, ativo);
            ps.setInt(3, funcionarioId);
            ps.executeUpdate();
        }
    }

    private Funcionario mapear(ResultSet rs) throws SQLException {
        return new Funcionario(rs.getInt("id"), rs.getInt("usuario_id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("username"), rs.getString("cargo"), rs.getString("perfil"), rs.getBoolean("ativo"));
    }
}
