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
import br.com.vidaativa.ui.componentes.*;
import br.com.vidaativa.ui.grafico.GraficoBarras;
import br.com.vidaativa.ui.grafico.GraficoBarras.Serie;
import br.com.vidaativa.ui.grafico.GraficoRosca;
import br.com.vidaativa.ui.grafico.GraficoRosca.Fatia;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Painel do gerente (primeira tela do perfil GERENTE): o controle geral da academia em uma só tela.
 * <p>
 * Mostra, para o mês escolhido no seletor do topo (◀ mês ▶):
 * <ul>
 *   <li>indicadores: alunos ativos, cadastros hoje e no mês, recebido, a receber e em atraso;</li>
 *   <li>gráficos: cadastros por dia, situação das mensalidades (pagas, pendentes, atrasadas), receita dos últimos
 *       6 meses, alunos por plano e receita por forma de pagamento;</li>
 *   <li>listas: mensalidades em atraso (quem cobrar), vencimentos dos próximos 7 dias e cadastros recentes.</li>
 * </ul>
 * Os dados vêm de uma única leitura do banco ({@link DashboardDAO}); os números de cada mês são calculados em memória
 * por {@link Indicadores}, então trocar de mês é instantâneo. O botão Atualizar (e abrir a tela) lê o banco de novo.
 * As definições exatas de cada número estão no Javadoc de {@link Indicadores}.
 */
public class GerentePainel extends PainelBase {
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final Usuario usuario;
    private final Supplier<DashboardDAO.Dados> fonte;
    private final Supplier<LocalDate> relogio;
    private DashboardDAO.Dados dados;
    private YearMonth mes;

    private final JLabel rotuloMes = new JLabel("", SwingConstants.CENTER);
    private final ConteudoRolavel corpo = new ConteudoRolavel(new BorderLayout());

    /** Painel ligado ao banco de dados real. */
    public GerentePainel(Usuario usuario) {
        this(usuario, () -> {
            try { return new DashboardDAO().carregar(); } catch (java.sql.SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
        }, LocalDate::now);
    }

    /**
     * Painel com origem de dados e relógio próprios (usado para demonstração e testes visuais, sem banco).
     *
     * @param fonte  quem fornece os dados a cada recarga
     * @param relogio quem informa a data de hoje
     */
    public GerentePainel(Usuario usuario, Supplier<DashboardDAO.Dados> fonte, Supplier<LocalDate> relogio) {
        super("Painel do gerente", "Visão geral da academia");
        this.usuario = usuario;
        this.fonte = fonte;
        this.relogio = relogio;
        this.mes = YearMonth.from(relogio.get());
        acoesDoTopo(criarSeletorMes());
        add(ConteudoRolavel.embrulhar(corpo), BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------ seletor de mês

    private JComponent criarSeletorMes() {
        JButton anterior = Theme.button("");
        anterior.setIcon(Icones.icone(Icones.Tipo.SETA_ESQ, 16, Theme.TEXT));
        anterior.setToolTipText("Mês anterior");
        anterior.addActionListener(e -> mudarMes(mes.minusMonths(1)));
        JButton proximo = Theme.button("");
        proximo.setIcon(Icones.icone(Icones.Tipo.SETA_DIR, 16, Theme.TEXT));
        proximo.setToolTipText("Próximo mês");
        proximo.addActionListener(e -> mudarMes(mes.plusMonths(1)));
        for (JButton b : new JButton[]{anterior, proximo}) b.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
        rotuloMes.setFont(Theme.fonte(14f, Font.BOLD));
        rotuloMes.setPreferredSize(new Dimension(150, 36));
        JButton hoje = Theme.button("Hoje");
        hoje.addActionListener(e -> mudarMes(YearMonth.from(relogio.get())));
        JButton atualizar = Theme.button("Atualizar");
        atualizar.setIcon(Icones.icone(Icones.Tipo.ATUALIZAR, 16, Theme.TEXT));
        atualizar.setIconTextGap(8);
        atualizar.addActionListener(e -> recarregar());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        p.add(anterior);
        p.add(rotuloMes);
        p.add(proximo);
        p.add(hoje);
        p.add(atualizar);
        return p;
    }

    /** Define o mês mostrado na próxima carga (usado pelos testes visuais). */
    void mesInicial(YearMonth m) { this.mes = m; }

    private void mudarMes(YearMonth novo) {
        mes = novo;
        desenhar();
    }

    // ------------------------------------------------------------------ dados

    /** Lê o banco de novo e redesenha o painel. */
    @Override
    public void recarregar() {
        try {
            dados = fonte.get();
            desenhar();
        } catch (Exception e) {
            UI.erro(this, e instanceof IllegalStateException && e.getCause() != null ? e.getCause() : e);
        }
    }

    /** Recalcula os indicadores do mês escolhido e refaz todos os cartões, gráficos e listas. */
    private void desenhar() {
        if (dados == null) return;
        LocalDate hoje = relogio.get();
        Resultado r = Indicadores.calcular(mes, hoje, dados.alunos(), dados.pagamentos());
        rotuloMes.setText(nomeMes(mes));
        subtitulo("Olá, " + primeiroNome(usuario.nome()) + "! Hoje é " + dataExtenso(hoje) + ".");

        JPanel conteudo = new JPanel(new PilhaVertical(18));
        conteudo.setOpaque(false);
        conteudo.add(titulo("Resumo de " + nomeMes(mes).toLowerCase(PT_BR)));
        conteudo.add(kpis(r));
        conteudo.add(linha(460, 2, cartaoCadastros(r), cartaoSituacao(r)));
        conteudo.add(linha(330, 3, cartaoReceita(r), cartaoPlanos(r), cartaoFormas(r)));
        conteudo.add(linha(460, 2, cartaoAtrasos(r), cartaoAgenda(r)));

        corpo.removeAll();
        corpo.add(conteudo, BorderLayout.NORTH);
        corpo.revalidate();
        corpo.repaint();
    }

    // ------------------------------------------------------------------ blocos

    /** Linha de cartões em colunas iguais que se adapta à largura da janela. */
    private static JComponent linha(int larguraMinima, int maxColunas, JComponent... cartoes) {
        JPanel p = new JPanel(new GradeFluida(larguraMinima, maxColunas, 16));
        p.setOpaque(false);
        for (JComponent c : cartoes) p.add(c);
        return p;
    }

    /** Título pequeno e cinza que abre uma seção do painel. */
    private static JLabel titulo(String texto) {
        JLabel t = new JLabel(texto);
        t.setFont(Theme.fonte(13f, Font.BOLD));
        t.setForeground(Theme.MUTED);
        return t;
    }

    /** Cartão com título, subtítulo cinza e o conteúdo (gráfico ou lista) embaixo. */
    static Cartao cartao(String titulo, String subtitulo, JComponent conteudo) {
        Cartao c = new Cartao(new BorderLayout(0, 14), 20, 22);
        JPanel cab = new JPanel(new GridLayout(2, 1, 0, 2));
        cab.setOpaque(false);
        cab.add(UI.titulo(titulo));
        JLabel s = UI.rotulo(subtitulo);
        cab.add(s);
        c.add(cab, BorderLayout.NORTH);
        c.add(conteudo, BorderLayout.CENTER);
        return c;
    }

    private JComponent kpis(Resultado r) {
        JPanel grade = new JPanel(new GradeFluida(172, 6, 14));
        grade.setOpaque(false);

        grade.add(new KpiCard("Alunos ativos", Icones.Tipo.ALUNOS, Theme.GREEN)
                .valor(String.valueOf(r.alunosAtivos()), Theme.TEXT)
                .detalhe(r.alunosInativos() + (r.alunosInativos() == 1 ? " inativo" : " inativos")));

        grade.add(new KpiCard("Cadastros hoje", Icones.Tipo.USUARIO_MAIS, Theme.INFO)
                .valor(String.valueOf(r.cadastrosHoje()), Theme.TEXT)
                .detalhe(shortData(r.hoje())));

        KpiCard cadMes = new KpiCard("Cadastros no mês", Icones.Tipo.CALENDARIO, Theme.ROXO)
                .valor(String.valueOf(r.cadastrosMes()), Theme.TEXT);
        double vc = Resultado.variacao(r.cadastrosMes(), r.cadastrosMesAnterior());
        if (Double.isNaN(vc)) cadMes.semBase(r.cadastrosMes() > 0 ? "sem cadastros no mês anterior" : "nenhum no mês anterior");
        else cadMes.variacao(vc, "vs. mês anterior", true);
        grade.add(cadMes);

        KpiCard rec = new KpiCard("Recebido no mês", Icones.Tipo.DINHEIRO, Theme.GREEN)
                .valor(UI.moeda(r.recebidoMes()), Theme.GREEN);
        double vr = Resultado.variacao(r.recebidoMes().doubleValue(), r.recebidoMesAnterior().doubleValue());
        if (Double.isNaN(vr)) rec.semBase(r.recebidoHoje().signum() > 0 ? "hoje: " + UI.moeda(r.recebidoHoje()) : "sem base no mês anterior");
        else rec.variacao(vr, "vs. mês anterior", true);
        grade.add(rec);

        grade.add(new KpiCard("A receber", Icones.Tipo.RELOGIO, Theme.WARN)
                .valor(UI.moeda(r.aReceberMes()), Theme.WARN)
                .detalhe(r.cobrancasPendentes() + (r.cobrancasPendentes() == 1 ? " cobrança a vencer" : " cobranças a vencer")));

        boolean temAtraso = r.atrasadoTotal().signum() > 0;
        grade.add(new KpiCard("Em atraso", Icones.Tipo.ALERTA, temAtraso ? Theme.DANGER : Theme.GREEN)
                .valor(UI.moeda(r.atrasadoTotal()), temAtraso ? Theme.DANGER : Theme.GREEN)
                .detalhe(temAtraso ? r.alunosEmAtraso() + (r.alunosEmAtraso() == 1 ? " aluno · todos os meses" : " alunos · todos os meses") : "ninguém em atraso"));
        return grade;
    }

    private Cartao cartaoCadastros(Resultado r) {
        int dias = r.cadastrosPorDia().length;
        String[] rotulos = new String[dias];
        double[] valores = new double[dias];
        for (int i = 0; i < dias; i++) { rotulos[i] = String.valueOf(i + 1); valores[i] = r.cadastrosPorDia()[i]; }
        boolean mesAtual = r.mes().equals(YearMonth.from(r.hoje()));
        GraficoBarras g = new GraficoBarras()
                .dados(rotulos, new Serie("Cadastros", Theme.GREEN, valores))
                .destaque(mesAtual ? r.hoje().getDayOfMonth() - 1 : -1)
                .dica(i -> "<html><b>" + String.format("%02d/%02d", i + 1, r.mes().getMonthValue()) + "</b><br>"
                        + (int) valores[i] + (valores[i] == 1 ? " cadastro" : " cadastros") + "</html>")
                .textoVazio("Nenhum cadastro neste mês");
        g.setPreferredSize(new Dimension(300, 240));
        return cartao("Cadastros por dia", r.cadastrosMes() + (r.cadastrosMes() == 1 ? " novo aluno em " : " novos alunos em ") + nomeMes(r.mes()).toLowerCase(PT_BR), g);
    }

    private Cartao cartaoSituacao(Resultado r) {
        GraficoRosca g = new GraficoRosca()
                .dados(new Fatia("Pagas", r.cobrancasPagas(), Theme.GREEN),
                        new Fatia("A vencer", r.cobrancasPendentes(), Theme.WARN),
                        new Fatia("Atrasadas", r.cobrancasAtrasadas(), Theme.DANGER),
                        new Fatia("Canceladas", r.cobrancasCanceladas(), Theme.MUTED))
                .formato(v -> String.valueOf((int) v))
                .textoVazio("Sem cobranças neste mês");
        if (Double.isNaN(r.adimplencia())) g.centro("", "");
        else g.centro(Math.round(r.adimplencia()) + "%", "em dia");
        g.setPreferredSize(new Dimension(300, 240));
        int total = r.cobrancasPagas() + r.cobrancasPendentes() + r.cobrancasAtrasadas() + r.cobrancasCanceladas();
        return cartao("Mensalidades do mês", total + (total == 1 ? " cobrança" : " cobranças") + " com referência " + r.mes(), g);
    }

    private Cartao cartaoReceita(Resultado r) {
        List<Indicadores.MesReceita> m = r.ultimosMeses();
        String[] rotulos = new String[m.size()];
        double[] rec = new double[m.size()], aberto = new double[m.size()];
        for (int i = 0; i < m.size(); i++) {
            rotulos[i] = abreviar(m.get(i).mes());
            rec[i] = m.get(i).recebido().doubleValue();
            aberto[i] = m.get(i).emAberto().doubleValue();
        }
        GraficoBarras g = new GraficoBarras()
                .dados(rotulos, new Serie("Recebido", Theme.GREEN, rec), new Serie("Em aberto", Theme.WARN, aberto))
                .formato(UI::moedaCurta)
                .destaque(m.size() - 1)
                .dica(i -> "<html><b>" + nomeMes(m.get(i).mes()) + "</b><br>Recebido: " + UI.moeda(m.get(i).recebido())
                        + "<br>Em aberto: " + UI.moeda(m.get(i).emAberto()) + "</html>")
                .textoVazio("Sem movimentação nos últimos meses");
        g.setPreferredSize(new Dimension(300, 300));
        return cartao("Receita dos últimos 6 meses", "recebido x em aberto (a vencer e atrasado)", g);
    }

    private Cartao cartaoPlanos(Resultado r) {
        List<Fatia> fatias = new ArrayList<>();
        int i = 0;
        for (var e : r.alunosPorPlano().entrySet()) fatias.add(new Fatia(e.getKey(), e.getValue(), Theme.SERIE[i++ % Theme.SERIE.length]));
        GraficoRosca g = new GraficoRosca().dados(fatias.toArray(new Fatia[0]))
                .formato(v -> String.valueOf((int) v))
                .centro(String.valueOf(r.alunosAtivos()), "alunos ativos")
                .textoVazio("Sem alunos ativos");
        g.setPreferredSize(new Dimension(300, 300));
        return cartao("Alunos por plano", "TotalPass: " + r.totalPass() + " · Convencional: " + r.convencional(), g);
    }

    private Cartao cartaoFormas(Resultado r) {
        List<Fatia> fatias = new ArrayList<>();
        int i = 0;
        for (var e : r.recebidoPorForma().entrySet())
            fatias.add(new Fatia(UI.rotuloForma(e.getKey()), e.getValue().doubleValue(), Theme.SERIE[i++ % Theme.SERIE.length]));
        fatias.sort((a, b) -> Double.compare(b.valor(), a.valor()));
        GraficoRosca g = new GraficoRosca().dados(fatias.toArray(new Fatia[0]))
                .formato(UI::moedaCurta)
                .centro(UI.moedaCurta(r.recebidoMes().doubleValue()), "recebido")
                .textoVazio("Nenhum recebimento no mês");
        g.setPreferredSize(new Dimension(300, 300));
        return cartao("Forma de pagamento", "recebido em " + nomeMes(r.mes()).toLowerCase(PT_BR), g);
    }

    private Cartao cartaoAtrasos(Resultado r) {
        JPanel lista = listaVertical();
        List<Atraso> atrasos = r.atrasos();
        if (atrasos.isEmpty()) lista.add(vazio("Nenhuma mensalidade em atraso. Tudo em dia!", true));
        for (int i = 0; i < Math.min(6, atrasos.size()); i++) {
            Atraso a = atrasos.get(i);
            lista.add(item(a.aluno(), "ref. " + a.referencia() + " · venceu em " + UI.data(a.vencimento()) + " · " + a.diasAtraso() + (a.diasAtraso() == 1 ? " dia" : " dias") + " de atraso",
                    UI.moeda(a.valor()), Theme.DANGER, Theme.DANGER));
        }
        if (atrasos.size() > 6) lista.add(maisLinha("+ " + (atrasos.size() - 6) + " outras em Pagamentos"));
        String sub = atrasos.isEmpty() ? "nenhuma pendência vencida" : atrasos.size() + (atrasos.size() == 1 ? " cobrança vencida" : " cobranças vencidas") + " · " + UI.moeda(r.atrasadoTotal()) + " no total";
        return cartao("Mensalidades em atraso", sub, lista);
    }

    private Cartao cartaoAgenda(Resultado r) {
        JPanel lista = listaVertical();
        List<Pagamento> prox = r.vencemEmBreve();
        lista.add(tituloLista("Vencem nos próximos 7 dias"));
        if (prox.isEmpty()) lista.add(vazio("Nenhum vencimento nesta semana.", false));
        for (int i = 0; i < Math.min(3, prox.size()); i++) {
            Pagamento p = prox.get(i);
            long dias = p.vencimento().toEpochDay() - r.hoje().toEpochDay();
            lista.add(item(p.alunoNome(), (dias <= 0 ? "vence hoje" : dias == 1 ? "vence amanhã" : "vence em " + dias + " dias") + " · " + UI.data(p.vencimento()),
                    UI.moeda(p.valor()), Theme.WARN, Theme.TEXT));
        }
        if (prox.size() > 3) lista.add(maisLinha("+ " + (prox.size() - 3) + " vencimentos"));
        lista.add(Box.createVerticalStrut(10));
        lista.add(tituloLista("Últimos cadastros"));
        List<Aluno> rec = r.cadastrosRecentes();
        if (rec.isEmpty()) lista.add(vazio("Nenhum aluno cadastrado ainda.", false));
        for (int i = 0; i < Math.min(3, rec.size()); i++) {
            Aluno a = rec.get(i);
            lista.add(item(a.nome(), "plano " + a.plano() + " · " + ("TOTALPASS".equals(a.tipoCliente()) ? "TotalPass" : "convencional"),
                    UI.data(a.dataInicio()), Theme.GREEN, Theme.MUTED));
        }
        return cartao("Agenda e novidades", "o que pede atenção nos próximos dias", lista);
    }

    // ------------------------------------------------------------------ itens de lista

    static JPanel listaVertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    static JLabel tituloLista(String texto) {
        JLabel l = new JLabel(texto.toUpperCase(PT_BR));
        l.setFont(Theme.fonte(11f, Font.BOLD));
        l.setForeground(Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 2, 6, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JComponent maisLinha(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Theme.fonte(12f, Font.PLAIN));
        l.setForeground(Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static JComponent vazio(String texto, boolean positivo) {
        JLabel l = new JLabel(texto);
        l.setIcon(Icones.icone(positivo ? Icones.Tipo.VISTO : Icones.Tipo.CALENDARIO, 18, positivo ? Theme.GREEN : Theme.MUTED));
        l.setIconTextGap(10);
        l.setForeground(positivo ? Theme.GREEN : Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Linha de lista: bolinha com a inicial, nome, detalhe em cinza e um valor à direita. */
    static JComponent item(String nome, String detalhe, String direita, Color corBolinha, Color corDireita) {
        String inicial = nome == null || nome.isBlank() ? "?" : nome.trim().substring(0, 1).toUpperCase();
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(Theme.alfa(corBolinha, 38));
                g2.fill(new Ellipse2D.Float(2, (getHeight() - 34) / 2f, 34, 34));
                g2.setColor(corBolinha);
                g2.setFont(Theme.fonte(14f, Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(inicial, 2 + (34 - fm.stringWidth(inicial)) / 2f, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
                g2.setColor(Theme.alfa(Theme.BORDER, 150));
                g2.fillRect(46, getHeight() - 1, getWidth() - 46, 1);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 48, 8, 0));
        JLabel n = new JLabel(nome);
        n.setFont(Theme.fonte(13.5f, Font.BOLD));
        JLabel d = new JLabel(detalhe);
        d.setFont(Theme.fonte(12f, Font.PLAIN));
        d.setForeground(Theme.MUTED);
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 1));
        textos.setOpaque(false);
        textos.add(n);
        textos.add(d);
        JLabel v = new JLabel(direita);
        v.setFont(Theme.fonte(13.5f, Font.BOLD));
        v.setForeground(corDireita);
        p.add(textos, BorderLayout.CENTER);
        p.add(v, BorderLayout.EAST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        return p;
    }

    // ------------------------------------------------------------------ textos

    static String primeiroNome(String nome) {
        String n = nome == null ? "" : nome.trim();
        int e = n.indexOf(' ');
        return e > 0 ? n.substring(0, e) : n;
    }

    /** "Novembro de 2026". */
    static String nomeMes(YearMonth m) {
        String n = m.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1) + " de " + m.getYear();
    }

    /** "nov" (abreviação sem ponto, para o eixo do gráfico). */
    private static String abreviar(YearMonth m) {
        return m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
    }

    static String dataExtenso(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR) + ", " + d.getDayOfMonth() + " de "
                + d.getMonth().getDisplayName(TextStyle.FULL, PT_BR) + " de " + d.getYear();
    }

    private static String shortData(LocalDate d) {
        return d.getDayOfMonth() + " de " + d.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
    }
}
