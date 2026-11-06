package br.com.vidaativa.service;

import br.com.vidaativa.model.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Histórico de pagamentos de UM aluno: filtros e resumo calculados sobre a lista de cobranças dele.
 * <p>
 * É lógica pura (sem banco nem tela), com testes em {@code TestesLogica}. Quem usa: {@code HistoricoAlunoPanel}, o
 * painel que a tela Pagamentos mostra ao selecionar um aluno (gerente e funcionário).
 *
 * <p><b>Definições:</b>
 * <ul>
 *   <li><b>Situação</b>: a mesma do dashboard ({@link Indicadores#situacao}): pendente já vencida conta como atrasada.</li>
 *   <li><b>Total pago</b>: soma das cobranças PAGAS; <b>em aberto</b>: pendentes (ainda a vencer) + atrasadas.</li>
 *   <li><b>Pontual</b>: paga em dia, isto é, com data de pagamento até a data de vencimento.
 *       O percentual de pontualidade é calculado só sobre as cobranças pagas.</li>
 *   <li><b>Último pagamento</b>: a data de pagamento mais recente entre as cobranças pagas.</li>
 * </ul>
 */
public final class HistoricoPagamentos {
    private HistoricoPagamentos() { }

    /** Números do resumo de um aluno. {@code percentualPontual} é NaN se ainda não há pagamento; datas nulas se não houver. */
    public record Resumo(int cobrancas, int pagas, int pendentes, int atrasadas, int canceladas,
                         BigDecimal totalPago, BigDecimal emAberto, BigDecimal emAtraso,
                         LocalDate ultimoPagamento, BigDecimal valorUltimoPagamento,
                         int pontuais, double percentualPontual, LocalDate primeiraCobranca) { }

    /** Calcula o resumo de todas as cobranças informadas (canceladas são contadas à parte e não entram em valores). */
    public static Resumo resumir(List<Pagamento> cobrancas, LocalDate hoje) {
        int pagas = 0, pend = 0, atras = 0, canc = 0, pontuais = 0;
        BigDecimal pago = BigDecimal.ZERO, aberto = BigDecimal.ZERO, atrasado = BigDecimal.ZERO, ultimoValor = null;
        LocalDate ultimo = null, primeira = null;
        for (Pagamento p : cobrancas) {
            BigDecimal v = p.valor() == null ? BigDecimal.ZERO : p.valor();
            String sit = Indicadores.situacao(p, hoje);
            if (p.vencimento() != null && (primeira == null || p.vencimento().isBefore(primeira))) primeira = p.vencimento();
            switch (sit) {
                case "PAGO" -> {
                    pagas++;
                    pago = pago.add(v);
                    if (p.data() != null) {
                        if (p.vencimento() != null && !p.data().isAfter(p.vencimento())) pontuais++;
                        if (ultimo == null || p.data().isAfter(ultimo)) { ultimo = p.data(); ultimoValor = v; }
                    }
                }
                case "PENDENTE" -> { pend++; aberto = aberto.add(v); }
                case "ATRASADO" -> { atras++; aberto = aberto.add(v); atrasado = atrasado.add(v); }
                case "CANCELADO" -> canc++;
                default -> { }
            }
        }
        double pct = pagas == 0 ? Double.NaN : pontuais * 100.0 / pagas;
        return new Resumo(cobrancas.size(), pagas, pend, atras, canc, pago, aberto, atrasado, ultimo, ultimoValor, pontuais, pct, primeira);
    }

    /**
     * Situação financeira de um aluno hoje, para a coluna "Financeiro" da lista de alunos:
     * <ul>
     *   <li>{@code ATRASADO}: tem qualquer cobrança atrasada (de qualquer mês);</li>
     *   <li>{@code EM_DIA}: não está atrasado e a cobrança do mês escolhido já foi paga;</li>
     *   <li>{@code A_VENCER}: não está atrasado e a cobrança do mês escolhido ainda vai vencer;</li>
     *   <li>{@code SEM_COBRANCA}: não há cobrança válida (não cancelada) no mês escolhido.</li>
     * </ul>
     */
    public static String situacaoFinanceira(List<Pagamento> doAluno, YearMonth mes, LocalDate hoje) {
        String doMes = null;
        for (Pagamento p : doAluno) {
            String sit = Indicadores.situacao(p, hoje);
            if (sit.equals("ATRASADO")) return "ATRASADO";
            if (mes.toString().equals(p.referencia()) && !sit.equals("CANCELADO")) doMes = sit;
        }
        if (doMes == null) return "SEM_COBRANCA";
        return doMes.equals("PAGO") ? "EM_DIA" : "A_VENCER";
    }

    /**
     * Filtra a lista do aluno, mantendo a ordem recebida.
     *
     * @param situacao PAGO, PENDENTE, ATRASADO ou CANCELADO; null ou vazio = todas
     * @param ano      ano da referência da cobrança (ex.: 2026); null = todos
     */
    public static List<Pagamento> filtrar(List<Pagamento> cobrancas, String situacao, Integer ano, LocalDate hoje) {
        List<Pagamento> r = new ArrayList<>();
        for (Pagamento p : cobrancas) {
            if (situacao != null && !situacao.isBlank() && !situacao.equals(Indicadores.situacao(p, hoje))) continue;
            if (ano != null && !ano.equals(anoDe(p))) continue;
            r.add(p);
        }
        return r;
    }

    /** Anos que aparecem nas referências das cobranças, do mais novo para o mais antigo (alimenta o seletor de ano). */
    public static List<Integer> anos(List<Pagamento> cobrancas) {
        TreeSet<Integer> s = new TreeSet<>(Comparator.reverseOrder());
        for (Pagamento p : cobrancas) { Integer a = anoDe(p); if (a != null) s.add(a); }
        return new ArrayList<>(s);
    }

    /** Ano da referência "AAAA-MM"; se ela estiver fora do padrão, usa o ano do vencimento; null se nenhum dos dois existir. */
    private static Integer anoDe(Pagamento p) {
        String ref = p.referencia();
        if (ref != null && ref.length() == 7) {
            try { return Integer.parseInt(ref.substring(0, 4)); } catch (NumberFormatException ignorada) { /* cai no vencimento */ }
        }
        return p.vencimento() == null ? null : p.vencimento().getYear();
    }
}
