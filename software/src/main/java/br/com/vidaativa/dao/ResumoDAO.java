package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Resumo;

import java.math.BigDecimal;
import java.sql.*;

/**
 * Calcula os números do painel inicial ({@code ResumoPanel}); cada perfil (gerente, funcionário, professor) mostra um
 * subconjunto deles. Lê as tabelas {@code alunos}, {@code funcionarios}, {@code professores}, {@code fichas_treino}
 * e {@code pagamentos}; não grava nada além do ajuste de atrasos descrito em {@link PagamentoDAO#marcarAtrasados}.
 *
 * <p>Regras: contagens só consideram registros ativos (alunos, funcionários e professores); funcionários não incluem
 * o gerente; a receita do mês é o que foi RECEBIDO neste mês (pela data do pagamento).
 */
public class ResumoDAO {
    /**
     * Executa as consultas (uma por número) numa mesma conexão e monta o {@link Resumo}.
     * Efeito colateral: antes de contar, marca como ATRASADO as cobranças pendentes vencidas.
     */
    public Resumo carregar() throws SQLException {
        try (Connection c = Database.getConnection()) {
            // Primeiro corrige os atrasos; senão as contagens de pendentes e atrasados ficariam desatualizadas.
            PagamentoDAO.marcarAtrasados(c);
            return new Resumo(
                    contar(c, "SELECT COUNT(*) FROM alunos WHERE ativo = 1"),
                    // O JOIN com usuarios existe só para filtrar perfil = 'FUNCIONARIO': o gerente também tem linha em funcionarios,
                    // mas não entra nesta contagem.
                    contar(c, "SELECT COUNT(*) FROM funcionarios f JOIN usuarios u ON u.id = f.usuario_id WHERE f.ativo = 1 AND u.perfil = 'FUNCIONARIO'"),
                    contar(c, "SELECT COUNT(*) FROM professores WHERE ativo = 1"),
                    contar(c, "SELECT COUNT(*) FROM fichas_treino WHERE status = 'ATIVA'"),
                    // Pendentes e atrasados somam as cobranças de TODOS os meses, não só as do mês atual.
                    contar(c, "SELECT COUNT(*) FROM pagamentos WHERE status = 'PENDENTE'"),
                    contar(c, "SELECT COUNT(*) FROM pagamentos WHERE status = 'ATRASADO'"),
                    receita(c));
        }
    }

    /** Roda um {@code SELECT COUNT(*)} e devolve o número (0 se não vier linha). */
    private int contar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Soma o valor das cobranças PAGAS cujo pagamento ocorreu no mês atual. */
    private BigDecimal receita(Connection c) throws SQLException {
        // COALESCE: SUM sem nenhuma linha devolve NULL, e aqui queremos 0. O DATE_FORMAT 'AAAA-MM' compara o mês do recebimento
        // (data_pagamento) com o mês de hoje, independentemente do mês de referência ou do vencimento da cobrança.
        String sql = "SELECT COALESCE(SUM(valor), 0) FROM pagamentos WHERE status = 'PAGO' "
                + "AND DATE_FORMAT(data_pagamento, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m')";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }
}
