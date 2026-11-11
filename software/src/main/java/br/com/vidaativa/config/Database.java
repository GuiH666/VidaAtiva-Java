package br.com.vidaativa.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Centraliza a conexão com o MySQL. Usuário, senha e URL vêm do {@link AppConfig}.
 *
 * <p>Quem usa: todos os DAOs (pacote {@code dao}), sempre assim: {@code try (Connection c = Database.getConnection()) { ... }}.
 *
 * <p>Não existe pool de conexões: cada chamada abre uma conexão NOVA, e quem chamou é responsável por fechá-la
 * (o try-with-resources acima faz isso). Para um sistema de academia pequeno isso basta; se o volume crescer,
 * este é o lugar para introduzir um pool.
 */
public final class Database {
    private Database() { }

    /**
     * Abre uma nova conexão com o banco configurado.
     *
     * @return conexão aberta (quem recebe deve fechar)
     * @throws SQLException se não conseguir conectar; a mensagem já orienta a conferir o MySQL e o config.properties
     */
    public static Connection getConnection() throws SQLException {
        try {
            Connection c = DriverManager.getConnection(AppConfig.dbUrl(), AppConfig.dbUser(), AppConfig.dbPassword());
            // Garante acentos e travessões (—) em qualquer config.properties, mesmo um antigo sem characterEncoding na URL.
            try (java.sql.Statement st = c.createStatement()) {
                st.execute("SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignorado) {
                // servidor sem utf8mb4: segue com o que a URL negociou
            }
            return c;
        } catch (SQLException e) {
            // Relança com mensagem em português (aparece para o usuário na tela), mas preserva SQLState, código de erro
            // e a causa original para não perder informação de diagnóstico.
            throw new SQLException("Não foi possível conectar ao banco de dados. Confira o MySQL e o arquivo "
                    + "config.properties (usuário/senha). Detalhe: " + e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
        }
    }
}
