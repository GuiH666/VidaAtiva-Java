package br.com.vidaativa.dao;

import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.model.Resumo;

import java.sql.SQLException;
import java.util.List;

/**
 * Carrega do banco tudo o que o painel do gerente precisa, de uma só vez: alunos, cobranças e as contagens gerais.
 * Os cálculos (por mês, por dia, atrasos...) são feitos depois, em memória, por
 * {@link br.com.vidaativa.service.Indicadores}; assim o gerente troca de mês sem nova consulta ao banco.
 * <p>
 * Para uma academia (centenas de alunos, uma cobrança por aluno por mês) o volume é pequeno; se um dia crescer muito,
 * a troca natural é filtrar as cobranças por período aqui, sem mexer no resto.
 */
public class DashboardDAO {
    /** Tudo o que foi lido do banco. {@code resumo} traz funcionários, professores e fichas ativas. */
    public record Dados(List<Aluno> alunos, List<Pagamento> pagamentos, Resumo resumo) { }

    /**
     * Lê alunos (ativos e inativos), todas as cobranças e o resumo geral. O {@code PagamentoDAO.listar} e o
     * {@code ResumoDAO.carregar} também marcam como ATRASADO as cobranças pendentes já vencidas.
     */
    public Dados carregar() throws SQLException {
        Resumo resumo = new ResumoDAO().carregar();
        List<Aluno> alunos = new AlunoDAO().listar(true);
        List<Pagamento> pagamentos = new PagamentoDAO().listar(null, null);
        return new Dados(alunos, pagamentos, resumo);
    }
}
