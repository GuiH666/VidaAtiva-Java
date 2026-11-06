package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.DashboardDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.service.Indicadores;
import br.com.vidaativa.service.Indicadores.Atraso;
import br.com.vidaativa.service.Indicadores.Resultado;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.ConteudoRolavel;
import br.com.vidaativa.ui.componentes.GradeFluida;
import br.com.vidaativa.ui.componentes.KpiCard;
import br.com.vidaativa.ui.componentes.PilhaVertical;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;

/**
 * Painel inicial do FUNCIONÁRIO (recepção): o que precisa de atenção hoje, em vez de gráficos.
 * <ul>
 *   <li>indicadores: alunos ativos, cadastros hoje, recebido hoje, vencem hoje, em atraso e alunos sem mensalidade no mês;</li>
 *   <li>"Cobrar agora": mensalidades em atraso, da mais antiga para a mais nova;</li>
 *   <li>"Vencem nos próximos 7 dias": para avisar o aluno antes;</li>
 *   <li>"Sem mensalidade neste mês": alunos que deveriam ter cobrança e ainda não têm (use Gerar mensalidades em Pagamentos);</li>
 *   <li>aniversariantes do mês e últimos cadastros.</li>
 * </ul>
 * Os números vêm de {@link Indicadores} (as definições estão no Javadoc dele), com a mesma leitura única do banco
 * ({@link DashboardDAO}) usada pelo painel do gerente.
 */
public class FuncionarioPainel extends PainelBase {
    private final Usuario usuario;
    private final Supplier<DashboardDAO.Dados> fonte;
    private final Supplier<LocalDate> relogio;
    private final ConteudoRolavel corpo = new ConteudoRolavel(new BorderLayout());

    /** Painel ligado ao banco de dados real. */
    public FuncionarioPainel(Usuario usuario) {
        this(usuario, () -> {
            try { return new DashboardDAO().carregar(); } catch (java.sql.SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
        }, LocalDate::now);
    }

    /** Painel com origem de dados e relógio próprios (demonstração e testes visuais, sem banco). */
    public FuncionarioPainel(Usuario usuario, Supplier<DashboardDAO.Dados> fonte, Supplier<LocalDate> relogio) {
        super("Painel da recepção", "Resumo do dia");
        this.usuario = usuario;
        this.fonte = fonte;
        this.relogio = relogio;
        JButton atualizar = Theme.button("Atualizar");
        atualizar.setIcon(Icones.icone(Icones.Tipo.ATUALIZAR, 16, Theme.TEXT));
        atualizar.setIconTextGap(8);
        atualizar.addActionListener(e -> recarregar());
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acoes.setOpaque(false);
        acoes.add(atualizar);
        acoesDoTopo(acoes);
        add(ConteudoRolavel.embrulhar(corpo), BorderLayout.CENTER);
    }

    @Override
    public void recarregar() {
        try {
            desenhar(fonte.get());
        } catch (Exception e) {
            UI.erro(this, e instanceof IllegalStateException && e.getCause() != null ? e.getCause() : e);
        }
    }

    private void desenhar(DashboardDAO.Dados d) {
        LocalDate hoje = relogio.get();
        Resultado r = Indicadores.calcular(YearMonth.from(hoje), hoje, d.alunos(), d.pagamentos());
        subtitulo("Olá, " + GerentePainel.primeiroNome(usuario.nome()) + "! Hoje é " + GerentePainel.dataExtenso(hoje) + ".");

        JPanel conteudo = new JPanel(new PilhaVertical(18));
        conteudo.setOpaque(false);
        conteudo.add(kpis(r));
        conteudo.add(linha(460, 2, cartaoCobrar(r), cartaoProximos(r)));
        conteudo.add(linha(330, 3, cartaoSemMensalidade(r), cartaoAniversariantes(r, hoje), cartaoCadastros(r)));
        corpo.removeAll();
        corpo.add(conteudo, BorderLayout.NORTH);
        corpo.revalidate();
        corpo.repaint();
    }

    private static JComponent linha(int larguraMinima, int maxColunas, JComponent... cartoes) {
        JPanel p = new JPanel(new GradeFluida(larguraMinima, maxColunas, 16));
        p.setOpaque(false);
        for (JComponent c : cartoes) p.add(c);
        return p;
    }

    private JComponent kpis(Resultado r) {
        JPanel grade = new JPanel(new GradeFluida(172, 6, 14));
        grade.setOpaque(false);
        grade.add(new KpiCard("Alunos ativos", Icones.Tipo.ALUNOS, Theme.GREEN)
                .valor(String.valueOf(r.alunosAtivos()), Theme.TEXT).detalhe(r.alunosInativos() + (r.alunosInativos() == 1 ? " inativo" : " inativos")));
        grade.add(new KpiCard("Cadastros hoje", Icones.Tipo.USUARIO_MAIS, Theme.INFO)
                .valor(String.valueOf(r.cadastrosHoje()), Theme.TEXT).detalhe(r.cadastrosMes() + " no mês"));
        grade.add(new KpiCard("Recebido hoje", Icones.Tipo.DINHEIRO, Theme.GREEN)
                .valor(UI.moeda(r.recebidoHoje()), r.recebidoHoje().signum() > 0 ? Theme.GREEN : Theme.TEXT)
                .detalhe(r.recebidoHojeQtd() + (r.recebidoHojeQtd() == 1 ? " pagamento" : " pagamentos")));
        grade.add(new KpiCard("Vencem hoje", Icones.Tipo.RELOGIO, Theme.WARN)
                .valor(String.valueOf(r.vencemHojeQtd()), r.vencemHojeQtd() > 0 ? Theme.WARN : Theme.TEXT)
                .detalhe(r.vencemHojeQtd() == 0 ? "nenhuma hoje" : UI.moeda(r.vencemHojeValor()) + " a receber"));
        boolean atraso = r.atrasadoTotal().signum() > 0;
        grade.add(new KpiCard("Em atraso", Icones.Tipo.ALERTA, atraso ? Theme.DANGER : Theme.GREEN)
                .valor(UI.moeda(r.atrasadoTotal()), atraso ? Theme.DANGER : Theme.GREEN)
                .detalhe(atraso ? r.alunosEmAtraso() + (r.alunosEmAtraso() == 1 ? " aluno" : " alunos") : "tudo em dia"));
        int sem = r.semCobrancaNoMes().size();
        grade.add(new KpiCard("Sem mensalidade", Icones.Tipo.PAGAMENTOS, sem > 0 ? Theme.WARN : Theme.GREEN)
                .valor(String.valueOf(sem), sem > 0 ? Theme.WARN : Theme.GREEN)
                .detalhe(sem > 0 ? "gerar no mês" : "todas geradas"));
        return grade;
    }

    private JComponent cartaoCobrar(Resultado r) {
        JPanel lista = GerentePainel.listaVertical();
        List<Atraso> atrasos = r.atrasos();
        if (atrasos.isEmpty()) lista.add(GerentePainel.vazio("Nenhuma mensalidade em atraso. Tudo em dia!", true));
        for (int i = 0; i < Math.min(7, atrasos.size()); i++) {
            Atraso a = atrasos.get(i);
            lista.add(GerentePainel.item(a.aluno(), "venceu em " + UI.data(a.vencimento()) + " · " + a.diasAtraso() + (a.diasAtraso() == 1 ? " dia" : " dias") + " de atraso",
                    UI.moeda(a.valor()), Theme.DANGER, Theme.DANGER));
        }
        if (atrasos.size() > 7) lista.add(GerentePainel.maisLinha("+ " + (atrasos.size() - 7) + " outras em Pagamentos (selecione para ver o histórico)"));
        String sub = atrasos.isEmpty() ? "nenhuma pendência vencida" : atrasos.size() + (atrasos.size() == 1 ? " cobrança" : " cobranças") + " · " + UI.moeda(r.atrasadoTotal()) + " no total";
        return GerentePainel.cartao("Cobrar agora", sub, lista);
    }

    private JComponent cartaoProximos(Resultado r) {
        JPanel lista = GerentePainel.listaVertical();
        List<Pagamento> prox = r.vencemEmBreve();
        if (prox.isEmpty()) lista.add(GerentePainel.vazio("Nenhum vencimento nos próximos 7 dias.", false));
        for (int i = 0; i < Math.min(7, prox.size()); i++) {
            Pagamento p = prox.get(i);
            long dias = p.vencimento().toEpochDay() - r.hoje().toEpochDay();
            lista.add(GerentePainel.item(p.alunoNome(), (dias <= 0 ? "vence hoje" : dias == 1 ? "vence amanhã" : "vence em " + dias + " dias") + " · " + UI.data(p.vencimento()),
                    UI.moeda(p.valor()), dias <= 0 ? Theme.WARN : Theme.INFO, Theme.TEXT));
        }
        if (prox.size() > 7) lista.add(GerentePainel.maisLinha("+ " + (prox.size() - 7) + " vencimentos"));
        return GerentePainel.cartao("Vencem nos próximos 7 dias", "bom momento para avisar o aluno", lista);
    }

    private JComponent cartaoSemMensalidade(Resultado r) {
        JPanel lista = GerentePainel.listaVertical();
        List<Aluno> sem = r.semCobrancaNoMes();
        if (sem.isEmpty()) lista.add(GerentePainel.vazio("Todos os alunos ativos têm a mensalidade do mês.", true));
        for (int i = 0; i < Math.min(5, sem.size()); i++) {
            Aluno a = sem.get(i);
            lista.add(GerentePainel.item(a.nome(), "plano " + a.plano() + " · " + ("TOTALPASS".equals(a.tipoCliente()) ? "TotalPass" : "convencional"), "", Theme.WARN, Theme.TEXT));
        }
        if (sem.size() > 5) lista.add(GerentePainel.maisLinha("+ " + (sem.size() - 5) + " alunos"));
        if (!sem.isEmpty()) lista.add(GerentePainel.maisLinha("Em Pagamentos, use “Gerar mensalidades”."));
        return GerentePainel.cartao("Sem mensalidade neste mês", "alunos ativos sem cobrança gerada", lista);
    }

    private JComponent cartaoAniversariantes(Resultado r, LocalDate hoje) {
        JPanel lista = GerentePainel.listaVertical();
        List<Aluno> an = r.aniversariantes();
        if (an.isEmpty()) lista.add(GerentePainel.vazio("Nenhum aniversariante neste mês.", false));
        for (int i = 0; i < Math.min(5, an.size()); i++) {
            Aluno a = an.get(i);
            int dia = a.dataNascimento().getDayOfMonth();
            boolean eHoje = dia == hoje.getDayOfMonth();
            int idade = hoje.getYear() - a.dataNascimento().getYear();
            lista.add(GerentePainel.item(a.nome(), eHoje ? "faz " + idade + " anos hoje" : "faz " + idade + " anos", String.format("%02d/%02d", dia, a.dataNascimento().getMonthValue()),
                    eHoje ? Theme.GREEN : Theme.ROXO, eHoje ? Theme.GREEN : Theme.MUTED));
        }
        if (an.size() > 5) lista.add(GerentePainel.maisLinha("+ " + (an.size() - 5) + " aniversariantes"));
        return GerentePainel.cartao("Aniversariantes do mês", an.size() + (an.size() == 1 ? " aluno" : " alunos"), lista);
    }

    private JComponent cartaoCadastros(Resultado r) {
        JPanel lista = GerentePainel.listaVertical();
        List<Aluno> rec = r.cadastrosRecentes();
        if (rec.isEmpty()) lista.add(GerentePainel.vazio("Nenhum aluno cadastrado ainda.", false));
        for (int i = 0; i < Math.min(5, rec.size()); i++) {
            Aluno a = rec.get(i);
            lista.add(GerentePainel.item(a.nome(), "plano " + a.plano() + " · " + ("TOTALPASS".equals(a.tipoCliente()) ? "TotalPass" : "convencional"),
                    UI.data(a.dataInicio()), Theme.GREEN, Theme.MUTED));
        }
        return GerentePainel.cartao("Últimos cadastros", "alunos mais recentes", lista);
    }
}
