package br.com.vidaativa.dao;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

/**
 * Utilitários internos dos DAOs. Não acessa nenhuma tabela: só dá apoio às outras classes deste pacote
 * (a classe não é {@code public}, então telas e API nem enxergam).
 *
 * Dois grupos de métodos:
 * <ul>
 *   <li>ler/gravar valores que podem ser NULL no banco (data, inteiro, decimal e texto). O JDBC devolve 0 para um
 *       INT nulo e não aceita {@code null} em {@code setInt}/{@code setDate}, por isso existem estes auxiliares;</li>
 *   <li>traduzir erros do MySQL ({@link SQLException}) em mensagens em português que as telas mostram direto
 *       ao usuário ({@link #amigavel} e {@link #mensagem}).</li>
 * </ul>
 * Os parâmetros de índice ({@code i}) seguem a convenção do JDBC: o primeiro {@code ?} do SQL é o 1.
 */
final class Sql {
    private Sql() { }

    /** Lê uma coluna DATE como {@code LocalDate}; devolve null quando a coluna é NULL no banco. */
    static LocalDate data(ResultSet rs, String coluna) throws SQLException {
        java.sql.Date d = rs.getDate(coluna);
        return d == null ? null : d.toLocalDate();
    }

    /**
     * Lê uma coluna INT que aceita NULL. {@code getInt} devolve 0 para NULL, então {@code wasNull()} é usado
     * para separar o zero de verdade do campo vazio.
     */
    static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        // wasNull() vale para a última coluna lida, por isso precisa ser chamado logo depois do getInt.
        return rs.wasNull() ? null : v;
    }

    /** Grava uma data no parâmetro {@code i}; {@code null} vira NULL no banco. */
    static void setData(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) ps.setNull(i, Types.DATE); else ps.setDate(i, java.sql.Date.valueOf(d));
    }

    /** Grava um inteiro opcional; {@code null} vira NULL (o {@code setInt} não aceita null). */
    static void setInteiro(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v);
    }

    /** Grava um decimal opcional (peso, altura, etc.); {@code null} vira NULL. */
    static void setDecimal(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DECIMAL); else ps.setBigDecimal(i, v);
    }

    /**
     * Grava um texto opcional: tira os espaços das pontas e converte texto null, vazio ou só com espaços em NULL.
     * Usado em colunas que aceitam NULL; campos obrigatórios são validados antes com {@code Validacao.obrigatorio}.
     */
    static void setTexto(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null || v.isBlank()) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v.trim());
    }

    /**
     * Reempacota o erro do banco com uma mensagem em português que a tela pode mostrar direto.
     * Mantém SQLState, código de erro e a exceção original como causa (útil para depurar no log).
     * Os DAOs usam no {@code catch} das operações que gravam, depois do rollback.
     */
    static SQLException amigavel(SQLException e) {
        return new SQLException(mensagem(e), e.getSQLState(), e.getErrorCode(), e);
    }

    /**
     * Escolhe a mensagem amigável a partir do código de erro do MySQL e do texto da mensagem original.
     * Se o erro não for um dos tratados abaixo, devolve a mensagem original do driver.
     */
    static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        // 1062 = valor repetido em coluna UNIQUE. O MySQL cita na mensagem o nome da coluna/índice violado
        // (ex.: "for key 'usuarios.username'"); é por esse texto que descobrimos qual campo repetiu.
        // Depende, portanto, dos nomes definidos em database/schema.sql (uq_pag_aluno_ref é o índice único de pagamentos).
        if (e.getErrorCode() == 1062) {
            if (m.contains("username")) return "Já existe um usuário cadastrado com esse login (usuário).";
            if (m.contains("email")) return "Já existe um cadastro com esse e-mail.";
            if (m.contains("documento")) return "Já existe um aluno cadastrado com esse documento.";
            if (m.contains("uq_pag_aluno_ref")) return "Este aluno já tem uma cobrança nesse mês.";
            return "Já existe um registro com esses dados.";
        }
        // 1451 = tentou apagar/alterar uma linha que outra tabela referencia por chave estrangeira.
        if (e.getErrorCode() == 1451) return "Este registro não pode ser removido porque está sendo usado em outro cadastro.";
        // 1452 = tentou gravar uma chave estrangeira que não existe (aluno, plano ou professor removido/inexistente).
        if (e.getErrorCode() == 1452) return "Algum dado informado não existe mais (aluno, plano ou professor). Atualize a tela e tente de novo.";
        return e.getMessage();
    }
}
