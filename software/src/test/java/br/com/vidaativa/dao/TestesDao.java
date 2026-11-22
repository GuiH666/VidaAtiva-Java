package br.com.vidaativa.dao;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Testes das regras dos DAOs que não precisam de MySQL (ficam neste pacote porque {@code Sql} não é pública).
 * Sem JUnit, no mesmo estilo dos outros testes: cada verificação imprime "ok" ou "FALHOU" e o processo termina com
 * código 1 se algo falhar.
 *
 * <p>O que NÃO prova: o SQL em si. As consultas só são exercitadas contra um MySQL de verdade (ainda pendente).
 */
public class TestesDao {
    private static int ok = 0, falhas = 0;

    public static void main(String[] args) {
        duplicidade();
        mensagens();
        System.out.println("\n" + ok + " verificações ok, " + falhas + " falha(s).");
        if (falhas > 0) System.exit(1);
    }

    static void duplicidade() {
        var duplicada = new SQLIntegrityConstraintViolationException("Duplicate entry '1-2026-11' for key 'pagamentos.uq_pag_aluno_ref'", "23000", 1062);
        var chaveEstrangeira = new SQLIntegrityConstraintViolationException("Cannot add or update a child row", "23000", 1452);
        var generico = new SQLException("Connection refused", "08S01", 0);
        check("1062 (valor repetido) é duplicidade", Sql.ehChaveDuplicada(duplicada));
        check("1452 (chave estrangeira) NÃO é duplicidade", !Sql.ehChaveDuplicada(chaveEstrangeira));
        check("erro de conexão NÃO é duplicidade", !Sql.ehChaveDuplicada(generico));
    }

    static void mensagens() {
        check("duplicidade da cobrança do mês", Sql.mensagem(new SQLException("Duplicate entry for key 'pagamentos.uq_pag_aluno_ref'", "23000", 1062)).equals("Este aluno já tem uma cobrança nesse mês."));
        check("e-mail repetido", Sql.mensagem(new SQLException("Duplicate entry 'a@b.com' for key 'usuarios.email'", "23000", 1062)).contains("e-mail"));
        check("chave estrangeira inexistente", Sql.mensagem(new SQLException("fk", "23000", 1452)).contains("não existe mais"));
        check("erro desconhecido devolve a mensagem original", Sql.mensagem(new SQLException("falhou X", "HY000", 9999)).equals("falhou X"));
        var envolvida = Sql.amigavel(new SQLException("Duplicate entry for key 'usuarios.username'", "23000", 1062));
        check("amigavel mantém SQLState e código", "23000".equals(envolvida.getSQLState()) && envolvida.getErrorCode() == 1062);
    }

    static void check(String nome, boolean cond) {
        if (cond) { ok++; System.out.println("  ok    " + nome); }
        else { falhas++; System.out.println("  FALHOU " + nome); }
    }
}
