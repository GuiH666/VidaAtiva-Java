package br.com.vidaativa.service;

import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Números do painel do gerente, calculados a partir das listas de alunos e de cobranças.
 * <p>
 * É lógica pura (não acessa o banco nem a tela), por isso tem testes automáticos em {@code TestesLogica}. O painel
 * carrega os dados uma vez ({@code DashboardDAO}) e chama {@link #calcular} cada vez que o gerente troca de mês.
 *
 * <p><b>Definições (o que cada número significa):</b>
 * <ul>
 *   <li><b>Cadastro</b>: aluno cuja data de início ("cliente desde", gravada com a data de hoje no cadastro) cai no período.
 *       Alunos desativados depois continuam contando, porque o cadastro aconteceu.</li>
 *   <li><b>Cobranças do mês</b>: as de {@code referencia} igual ao mês escolhido (ex.: "2026-11"), por situação.
 *       Uma cobrança PENDENTE com vencimento anterior a hoje conta como ATRASADA, mesmo que o banco ainda não tenha atualizado.</li>
 *   <li><b>Recebido no mês</b>: soma das cobranças PAGAS cuja data de pagamento cai no mês (regime de caixa; pode incluir
 *       mensalidade de mês anterior paga com atraso).</li>
 *   <li><b>A receber</b>: soma das cobranças PENDENTES do mês que ainda não venceram.</li>
 *   <li><b>Em atraso (total)</b>: soma de TODAS as cobranças atrasadas, de qualquer mês: é o que a recepção precisa cobrar.</li>
 *   <li><b>Adimplência do mês</b>: pagas / (pagas + pendentes + atrasadas) das cobranças do mês; canceladas ficam de fora.</li>
 * </ul>
 */
public final class Indicadores {
    private Indicadores() { }

    /** Recebido e em aberto de um mês (uma barra dupla do gráfico de receita). */
    public record MesReceita(YearMonth mes, BigDecimal recebido, BigDecimal emAberto) { }

    /** Uma cobrança atrasada, com os dias de atraso já calculados. */
    public record Atraso(int alunoId, String aluno, String referencia, LocalDate vencimento, long diasAtraso, BigDecimal valor) { }

    /** Resultado completo para um mês. */
    public record Resultado(
            YearMonth mes, LocalDate hoje,
            int alunosAtivos, int alunosInativos,
            int cadastrosHoje, int cadastrosMes, int cadastrosMesAnterior, int[] cadastrosPorDia,
            int cobrancasPagas, int cobrancasPendentes, int cobrancasAtrasadas, int cobrancasCanceladas,
            BigDecimal recebidoMes, BigDecimal recebidoMesAnterior, BigDecimal recebidoHoje,
            BigDecimal aReceberMes, BigDecimal atrasadoMes, BigDecimal atrasadoTotal, int alunosEmAtraso,
            double adimplencia,
            List<MesReceita> ultimosMeses,
            LinkedHashMap<String, Integer> alunosPorPlano, int totalPass, int convencional,
            LinkedHashMap<String, BigDecimal> recebidoPorForma,
            List<Atraso> atrasos, List<Pagamento> vencemEmBreve, List<Aluno> cadastrosRecentes) {

        /** Variação percentual de {@code atual} sobre {@code anterior}; {@code NaN} quando não há base (anterior zero). */
        public static double variacao(double atual, double anterior) {
            return anterior == 0 ? Double.NaN : (atual - anterior) / anterior * 100.0;
        }
    }

    /** Situação da cobrança considerando a data de hoje (PENDENTE já vencida vira ATRASADO). */
    public static String situacao(Pagamento p, LocalDate hoje) {
        if ("PENDENTE".equals(p.status()) && p.vencimento() != null && p.vencimento().isBefore(hoje)) return "ATRASADO";
        return p.status();
    }

    /**
     * Calcula todos os indicadores de um mês.
     *
     * @param mes        mês que o gerente está vendo
     * @param hoje       data de hoje (parâmetro para os testes poderem fixar o dia)
     * @param alunos     todos os alunos (ativos e inativos)
     * @param pagamentos todas as cobranças
     */
    public static Resultado calcular(YearMonth mes, LocalDate hoje, List<Aluno> alunos, List<Pagamento> pagamentos) {
        // ---- cadastros ----
        YearMonth anterior = mes.minusMonths(1);
        int[] porDia = new int[mes.lengthOfMonth()];
        int cadMes = 0, cadAnt = 0, cadHoje = 0, ativos = 0, inativos = 0, totalPass = 0, convencional = 0;
        Map<String, Integer> planos = new HashMap<>();
        for (Aluno a : alunos) {
            if (a.ativo()) {
                ativos++;
                planos.merge(a.plano(), 1, Integer::sum);
                if ("TOTALPASS".equals(a.tipoCliente())) totalPass++; else convencional++;
            } else inativos++;
            LocalDate d = a.dataInicio();
            if (d == null) continue;
            YearMonth ym = YearMonth.from(d);
            if (ym.equals(mes)) { cadMes++; porDia[d.getDayOfMonth() - 1]++; }
            else if (ym.equals(anterior)) cadAnt++;
            if (d.equals(hoje)) cadHoje++;
        }
        LinkedHashMap<String, Integer> alunosPorPlano = new LinkedHashMap<>();
        planos.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> alunosPorPlano.put(e.getKey(), e.getValue()));

        // ---- cobranças ----
        String ref = mes.toString();
        int pagas = 0, pend = 0, atras = 0, canc = 0;
        BigDecimal recMes = BigDecimal.ZERO, recAnt = BigDecimal.ZERO, recHoje = BigDecimal.ZERO;
        BigDecimal aReceber = BigDecimal.ZERO, atrasMes = BigDecimal.ZERO, atrasTotal = BigDecimal.ZERO;
        Map<YearMonth, BigDecimal> recebidoPorMes = new HashMap<>(), abertoPorMes = new HashMap<>();
        LinkedHashMap<String, BigDecimal> porForma = new LinkedHashMap<>();
        List<Atraso> atrasos = new ArrayList<>();
        List<Pagamento> proximas = new ArrayList<>();
        Set<Integer> alunosAtrasados = new HashSet<>();

        for (Pagamento p : pagamentos) {
            String sit = situacao(p, hoje);
            BigDecimal valor = p.valor() == null ? BigDecimal.ZERO : p.valor();
            boolean doMes = ref.equals(p.referencia());

            if ("PAGO".equals(sit) && p.data() != null) {
                YearMonth pm = YearMonth.from(p.data());
                recebidoPorMes.merge(pm, valor, BigDecimal::add);
                if (pm.equals(mes)) {
                    recMes = recMes.add(valor);
                    porForma.merge(p.formaPagamento() == null ? "—" : p.formaPagamento(), valor, BigDecimal::add);
                }
                if (pm.equals(anterior)) recAnt = recAnt.add(valor);
                if (p.data().equals(hoje)) recHoje = recHoje.add(valor);
            }
            if (("PENDENTE".equals(sit) || "ATRASADO".equals(sit)) && p.referencia() != null && p.referencia().length() == 7) {
                try { abertoPorMes.merge(YearMonth.parse(p.referencia()), valor, BigDecimal::add); } catch (RuntimeException ignorada) { /* referência fora do padrão */ }
            }
            if ("ATRASADO".equals(sit)) {
                atrasTotal = atrasTotal.add(valor);
                alunosAtrasados.add(p.alunoId());
                long dias = p.vencimento() == null ? 0 : hoje.toEpochDay() - p.vencimento().toEpochDay();
                atrasos.add(new Atraso(p.alunoId(), p.alunoNome(), p.referencia(), p.vencimento(), dias, valor));
            }
            if ("PENDENTE".equals(sit) && p.vencimento() != null && !p.vencimento().isAfter(hoje.plusDays(7))) proximas.add(p);
            if (doMes) {
                switch (sit) {
                    case "PAGO" -> pagas++;
                    case "PENDENTE" -> { pend++; aReceber = aReceber.add(valor); }
                    case "ATRASADO" -> { atras++; atrasMes = atrasMes.add(valor); }
                    case "CANCELADO" -> canc++;
                    default -> { }
                }
            }
        }
        atrasos.sort(Comparator.comparingLong(Atraso::diasAtraso).reversed().thenComparing(Atraso::aluno));
        proximas.sort(Comparator.comparing(Pagamento::vencimento).thenComparing(Pagamento::alunoNome));

        List<MesReceita> ultimos = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = mes.minusMonths(i);
            ultimos.add(new MesReceita(m, recebidoPorMes.getOrDefault(m, BigDecimal.ZERO), abertoPorMes.getOrDefault(m, BigDecimal.ZERO)));
        }
        int base = pagas + pend + atras;
        double adimplencia = base == 0 ? Double.NaN : pagas * 100.0 / base;

        List<Aluno> recentes = alunos.stream().filter(a -> a.dataInicio() != null)
                .sorted(Comparator.comparing(Aluno::dataInicio).reversed().thenComparing(Aluno::nome))
                .limit(6).toList();

        return new Resultado(mes, hoje, ativos, inativos, cadHoje, cadMes, cadAnt, porDia,
                pagas, pend, atras, canc, recMes, recAnt, recHoje, aReceber, atrasMes, atrasTotal, alunosAtrasados.size(),
                adimplencia, ultimos, alunosPorPlano, totalPass, convencional, porForma, atrasos, proximas, recentes);
    }
}
