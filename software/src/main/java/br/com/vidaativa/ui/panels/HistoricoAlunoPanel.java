package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.PagamentoDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.service.HistoricoPagamentos;
import br.com.vidaativa.service.HistoricoPagamentos.Resumo;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.Cartao;
import br.com.vidaativa.ui.componentes.Opcoes;
import br.com.vidaativa.ui.componentes.PilhaVertical;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Painel "Histórico de pagamentos" de um único aluno, mostrado ao lado da lista na tela Pagamentos
 * (gerente e funcionário). Ao selecionar uma cobrança na lista, o painel passa a mostrar só o histórico daquele aluno:
 * <ul>
 *   <li>resumo: total pago, em aberto, em atraso, último pagamento, pontualidade e primeira cobrança;</li>
 *   <li>filtros: situação (todas, pagas, pendentes, atrasadas, canceladas) e ano;</li>
 *   <li>tabela com todas as cobranças: referência, vencimento, dia em que foi pago, valor, forma e situação.</li>
 * </ul>
 * Os cálculos ficam em {@link HistoricoPagamentos} (testado); aqui só há tela. Sem aluno selecionado, mostra uma dica.
 * <p>
 * O construtor com três funções existe para as capturas de tela de demonstração (dados de exemplo, sem banco).
 */
class HistoricoAlunoPanel extends Cartao {
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final List<String> SITUACOES = List.of("", "PAGO", "PENDENTE", "ATRASADO", "CANCELADO");

    private final Function<Integer, List<Pagamento>> fonteCobrancas;
    private final Function<Integer, Aluno> fonteAluno;
    private final Supplier<LocalDate> hoje;
    /** De onde vem o "pago online": id da cobrança -> provedor. Consultada a cada seleção de aluno. */
    private final Supplier<java.util.Map<Integer, String>> fonteOnline;

    private final CardLayout cartas = new CardLayout();
    private final JPanel conteudo = new JPanel(cartas);
    private final JLabel nome = new JLabel(" ");
    private final JLabel detalhe = new JLabel(" ");
    private final JPanel estatisticas = new JPanel(new GridLayout(3, 2, 10, 10));
    private final Opcoes<String> situacao = new Opcoes<>(SITUACOES, PainelRotulos::situacao, false);
    private final JComboBox<String> ano = new JComboBox<>();
    private final JLabel contagem = new JLabel(" ");
    private final JTable tabela = UI.tabela("Venc.", "Pago em", "Valor", "Forma", "Situação");

    private int alunoId = -1;
    private List<Pagamento> todas = new ArrayList<>();
    private boolean montandoAnos;
    private java.util.Map<Integer, String> online = java.util.Map.of();

    /** Rótulos das pílulas de situação (classe à parte só para a lambda de {@code Opcoes} ficar legível). */
    private static final class PainelRotulos {
        static String situacao(String s) {
            return switch (s) {
                case "PAGO" -> "Pagas";
                case "PENDENTE" -> "Pendentes";
                case "ATRASADO" -> "Atrasadas";
                case "CANCELADO" -> "Canceladas";
                default -> "Todas";
            };
        }
    }

    /** Versão normal: lê do banco. */
    HistoricoAlunoPanel() {
        this(id -> {
            try { return new PagamentoDAO().historicoDoAluno(id); }
            catch (SQLException e) { throw new IllegalStateException(e.getMessage(), e); }
        }, id -> {
            try { return new AlunoDAO().buscarPorId(id); }
            catch (SQLException e) { return null; }   // o cabeçalho funciona sem os dados do cadastro
        }, LocalDate::now, () -> new br.com.vidaativa.dao.TransacaoDAO().origemOnline());
    }

    /** Versão para demonstração: sem marcação de "online". */
    HistoricoAlunoPanel(Function<Integer, List<Pagamento>> fonteCobrancas, Function<Integer, Aluno> fonteAluno, Supplier<LocalDate> hoje) {
        this(fonteCobrancas, fonteAluno, hoje, java.util.Map::of);
    }

    HistoricoAlunoPanel(Function<Integer, List<Pagamento>> fonteCobrancas, Function<Integer, Aluno> fonteAluno, Supplier<LocalDate> hoje,
                        Supplier<java.util.Map<Integer, String>> fonteOnline) {
        super(new BorderLayout(), 20, 20);
        this.fonteOnline = fonteOnline;
        this.fonteCobrancas = fonteCobrancas;
        this.fonteAluno = fonteAluno;
        this.hoje = hoje;
        setPreferredSize(new Dimension(520, 10));

        // --- Estado "nenhum aluno selecionado" ---
        JLabel dica = new JLabel("<html><div style='text-align:center;width:260px'>Selecione uma cobrança na lista para ver o <b>histórico de pagamentos</b> daquele aluno.</div></html>",
                Icones.icone(Icones.Tipo.RELOGIO, 34, Theme.MUTED), SwingConstants.CENTER);
        dica.setVerticalTextPosition(SwingConstants.BOTTOM);
        dica.setHorizontalTextPosition(SwingConstants.CENTER);
        dica.setIconTextGap(14);
        dica.setForeground(Theme.MUTED);
        JPanel vazio = new JPanel(new GridBagLayout());
        vazio.setOpaque(false);
        vazio.add(dica);

        // --- Estado com aluno: cabeçalho, resumo, filtros e tabela ---
        nome.setFont(Theme.fonte(19f, Font.BOLD));
        nome.setForeground(Theme.TEXT);
        detalhe.setForeground(Theme.MUTED);
        JPanel cab = new JPanel(new GridLayout(2, 1, 0, 3));
        cab.setOpaque(false);
        cab.add(nome);
        cab.add(detalhe);

        estatisticas.setOpaque(false);

        situacao.setSelecionado("");
        situacao.aoMudar(s -> aplicarFiltro());
        ano.setPreferredSize(new Dimension(180, 40));
        ano.addActionListener(e -> { if (!montandoAnos) aplicarFiltro(); });
        contagem.setForeground(Theme.MUTED);
        JPanel linhaAno = new JPanel(new BorderLayout(10, 0));
        linhaAno.setOpaque(false);
        linhaAno.add(contagem, BorderLayout.CENTER);
        linhaAno.add(ano, BorderLayout.EAST);

        JPanel topo = new JPanel(new PilhaVertical(14));
        topo.setOpaque(false);
        topo.add(cab);
        topo.add(estatisticas);
        topo.add(situacao);
        topo.add(linhaAno);

        tabela.putClientProperty("folgaColunas", 30);
        UI.colorirColuna(tabela, 4, s -> switch (s) {
            case "Pago" -> Theme.GREEN;
            case "Atrasado" -> Theme.DANGER;
            case "Pendente" -> Theme.WARN;
            default -> Theme.MUTED;
        });
        JPanel comAluno = new JPanel(new BorderLayout(0, 12));
        comAluno.setOpaque(false);
        comAluno.add(topo, BorderLayout.NORTH);
        comAluno.add(UI.rolagem(tabela), BorderLayout.CENTER);

        conteudo.setOpaque(false);
        conteudo.add(vazio, "vazio");
        conteudo.add(comAluno, "aluno");
        add(conteudo, BorderLayout.CENTER);
        cartas.show(conteudo, "vazio");
    }

    /** Id do aluno mostrado agora, ou -1 se nenhum. */
    int alunoAtual() { return alunoId; }

    /** Nome do aluno mostrado agora. */
    String nomeAtual() { return nome.getText(); }

    /** Volta ao estado "selecione uma cobrança". */
    void limpar() {
        alunoId = -1;
        todas = new ArrayList<>();
        cartas.show(conteudo, "vazio");
    }

    /**
     * Carrega e mostra o histórico de um aluno. Se já era o aluno mostrado, mantém os filtros escolhidos
     * (por exemplo, depois de registrar um pagamento a lista continua filtrada como estava).
     */
    void mostrar(int id, String nomeAluno) {
        boolean mesmo = id == alunoId;
        alunoId = id;
        try {
            todas = fonteCobrancas.apply(id);
        } catch (RuntimeException e) {
            UI.erro(this, e);
            return;
        }
        try { online = fonteOnline.get(); } catch (RuntimeException e) { online = java.util.Map.of(); }
        nome.setText(nomeAluno);
        Aluno a = fonteAluno.apply(id);
        detalhe.setText(a == null ? " " : "Plano " + a.plano() + "  ·  " + ("TOTALPASS".equals(a.tipoCliente()) ? "TotalPass" : "Convencional")
                + (a.vencimento() == null ? "" : "  ·  vence dia " + a.vencimento().getDayOfMonth()) + (a.ativo() ? "" : "  ·  inativo"));

        montandoAnos = true;
        Object anterior = mesmo ? ano.getSelectedItem() : null;
        ano.removeAllItems();
        ano.addItem("Todos os anos");
        for (Integer y : HistoricoPagamentos.anos(todas)) ano.addItem(String.valueOf(y));
        if (anterior != null) ano.setSelectedItem(anterior);
        if (ano.getSelectedIndex() < 0) ano.setSelectedIndex(0);
        montandoAnos = false;
        if (!mesmo) situacao.setSelecionado("");

        preencherEstatisticas();
        aplicarFiltro();
        cartas.show(conteudo, "aluno");
    }

    /** Aplica situação e ano escolhidos e redesenha a tabela. O resumo do topo sempre considera todas as cobranças. */
    private void aplicarFiltro() {
        LocalDate h = hoje.get();
        String sit = situacao.getSelecionado();
        String escolhido = (String) ano.getSelectedItem();
        Integer a = escolhido == null || escolhido.startsWith("Todos") ? null : Integer.valueOf(escolhido);
        List<Pagamento> vistas = HistoricoPagamentos.filtrar(todas, sit == null || sit.isEmpty() ? null : sit, a, h);
        DefaultTableModel m = UI.modelo(tabela);
        m.setRowCount(0);
        for (Pagamento p : vistas) {
            String s = br.com.vidaativa.service.Indicadores.situacao(p, h);
            m.addRow(new Object[]{data(p.vencimento()), data(p.data()), UI.moeda(p.valor()),
                    rotuloForma(p), UI.rotuloStatusPagamento(s)});
        }
        contagem.setText(vistas.size() + (vistas.size() == 1 ? " cobrança" : " cobranças") + (vistas.size() == todas.size() ? "" : " de " + todas.size()));
    }

    /** "Pix", ou "Pix · online" quando o próprio aluno pagou pelo site ("· demo" se foi no modo de demonstração). */
    private String rotuloForma(Pagamento p) {
        String base = UI.rotuloForma(p.formaPagamento());
        String prov = online.get(p.id());
        if (prov == null || !"PAGO".equals(p.status())) return base;
        return base + ("SIMULADO".equals(prov) ? " · demo" : " · online");
    }

    private void preencherEstatisticas() {
        Resumo r = HistoricoPagamentos.resumir(todas, hoje.get());
        estatisticas.removeAll();
        estatisticas.add(estatistica("Total pago", UI.moeda(r.totalPago()), r.pagas() == 0 ? Theme.TEXT : Theme.GREEN,
                r.pagas() + (r.pagas() == 1 ? " pagamento" : " pagamentos")));
        estatisticas.add(estatistica("Último pagamento", r.ultimoPagamento() == null ? "—" : UI.data(r.ultimoPagamento()), Theme.TEXT,
                r.valorUltimoPagamento() == null ? "nenhum ainda" : UI.moeda(r.valorUltimoPagamento())));
        estatisticas.add(estatistica("Em aberto", UI.moeda(r.emAberto()), r.emAberto().signum() > 0 ? Theme.WARN : Theme.TEXT,
                (r.pendentes() + r.atrasadas()) + ((r.pendentes() + r.atrasadas()) == 1 ? " cobrança" : " cobranças")));
        estatisticas.add(estatistica("Em atraso", UI.moeda(r.emAtraso()), r.emAtraso().signum() > 0 ? Theme.DANGER : Theme.GREEN,
                r.atrasadas() == 0 ? "tudo em dia" : r.atrasadas() + (r.atrasadas() == 1 ? " cobrança" : " cobranças")));
        estatisticas.add(estatistica("Pagou em dia", Double.isNaN(r.percentualPontual()) ? "—" : Math.round(r.percentualPontual()) + "%",
                Double.isNaN(r.percentualPontual()) ? Theme.TEXT : r.percentualPontual() >= 80 ? Theme.GREEN : r.percentualPontual() >= 50 ? Theme.WARN : Theme.DANGER,
                r.pagas() == 0 ? "sem pagamentos" : r.pontuais() + " de " + r.pagas() + " no prazo"));
        estatisticas.add(estatistica("Primeira cobrança", r.primeiraCobranca() == null ? "—" : referencia(String.format("%d-%02d", r.primeiraCobranca().getYear(), r.primeiraCobranca().getMonthValue())),
                Theme.TEXT, r.cobrancas() + (r.cobrancas() == 1 ? " cobrança no total" : " cobranças no total")
                        + (r.canceladas() > 0 ? " (" + r.canceladas() + " cancel.)" : "")));
        estatisticas.revalidate();
        estatisticas.repaint();
    }

    /** Bloco pequeno do resumo: título cinza, valor grande e detalhe. */
    private static JComponent estatistica(String titulo, String valor, Color cor, String detalhe) {
        JPanel p = new JPanel(new GridLayout(3, 1, 0, 1)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(Theme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        JLabel t = new JLabel(titulo);
        t.setFont(Theme.fonte(11.5f, Font.PLAIN));
        t.setForeground(Theme.MUTED);
        JLabel v = new JLabel(valor);
        v.setFont(Theme.fonte(17f, Font.BOLD));
        v.setForeground(cor);
        JLabel d = new JLabel(detalhe);
        d.setFont(Theme.fonte(11.5f, Font.PLAIN));
        d.setForeground(Theme.MUTED);
        p.add(t);
        p.add(v);
        p.add(d);
        return p;
    }

    private static String data(LocalDate d) { return d == null ? "—" : DATA_CURTA.format(d); }

    /** AAAA-MM vira MM/AAAA. */
    private static String referencia(String ref) {
        if (ref == null || ref.length() != 7) return UI.texto(ref);
        return ref.substring(5) + "/" + ref.substring(0, 4);
    }
}
