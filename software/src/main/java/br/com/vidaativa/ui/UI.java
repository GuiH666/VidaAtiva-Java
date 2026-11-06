package br.com.vidaativa.ui;

import br.com.vidaativa.ui.componentes.Chip;
import br.com.vidaativa.ui.componentes.RolagemArredondada;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Caixa de ferramentas da interface: métodos {@code static} que todas as telas usam para não repetir código.
 * <p>
 * Reúne quatro grupos de funções:
 * <ul>
 *   <li>formatação de valores para exibir (data, moeda, texto vazio e rótulos de status e forma de pagamento);</li>
 *   <li>mensagens ao usuário ({@link #erro}, {@link #info} e {@link #confirmar});</li>
 *   <li>montagem de componentes com o visual do tema (tabelas, rolagem, faixa de botões, áreas de texto);</li>
 *   <li>combos de opções ({@link Opcao}), em que o valor gravado no banco é diferente do texto mostrado.</li>
 * </ul>
 * Quem usa: praticamente todas as telas de {@code ui} e {@code ui.panels}. Esta classe não acessa o banco.
 */
public final class UI {
    // Data no padrão brasileiro. Serve só para EXIBIR; a leitura de datas digitadas é feita pelo DateField.
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Moeda em reais (ex.: R$ 1.234,56), conforme o idioma pt-BR.
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    // Classe utilitária: não deve ser instanciada.
    private UI() { }

    /**
     * Opção de combo: guarda o valor gravado no banco e o texto mostrado na tela.
     * O {@code toString} devolve o rótulo porque é ele que o {@code JComboBox} desenha.
     * Para ler a escolha do usuário: {@code ((Opcao) combo.getSelectedItem()).valor()}.
     */
    public record Opcao(String valor, String rotulo) {
        @Override public String toString() { return rotulo; }
    }

    /** Formata a data como dd/MM/aaaa; devolve um traço (—) se for nula. */
    public static String data(LocalDate d) { return d == null ? "—" : DATA.format(d); }

    /**
     * Formata o valor em reais; devolve um traço (—) se for nulo.
     * O código troca o espaço "não separável" que o Java coloca depois do R$ por um espaço comum.
     */
    public static String moeda(BigDecimal v) { return v == null ? "—" : MOEDA.format(v).replace(' ', ' '); }

    /** Moeda abreviada para eixos de gráfico: "R$ 850", "R$ 1,2 mil", "R$ 2,5 mi". */
    public static String moedaCurta(double v) {
        java.text.DecimalFormat um = new java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));
        double a = Math.abs(v);
        if (a >= 1_000_000) return "R$ " + um.format(v / 1_000_000) + " mi";
        if (a >= 1_000) return "R$ " + um.format(v / 1_000) + " mil";
        return "R$ " + Math.round(v);
    }

    /** Devolve o texto, ou um traço (—) se for nulo ou só tiver espaços. Usado em células de tabela vazias. */
    public static String texto(String s) { return s == null || s.isBlank() ? "—" : s; }

    /**
     * Mostra o erro de forma amigável: dados inválidos viram aviso, falhas viram erro.
     * <p>
     * {@link IllegalArgumentException} e {@link IllegalStateException} são as exceções que o projeto usa para
     * regras de negócio e validação (a mensagem já é escrita para o usuário): abrem como "Atenção".
     * Qualquer outra abre como "Erro".
     *
     * @param pai componente sobre o qual a caixa abre (pode ser null)
     * @param e   exceção a mostrar; se não tiver mensagem, aparece o nome da classe
     */
    public static void erro(Component pai, Throwable e) {
        boolean validacao = e instanceof IllegalArgumentException || e instanceof IllegalStateException;
        // Validação e erro de SQL já têm mensagem clara; só os demais (falhas inesperadas) imprimem o stack trace no console.
        if (!validacao && !(e instanceof SQLException)) e.printStackTrace();
        String msg = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
        JOptionPane.showMessageDialog(pai, mensagemLarga(msg), validacao ? "Atenção" : "Erro",
                validacao ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Mostra uma mensagem informativa (ex.: "Selecione um aluno na tabela").
     *
     * @param pai componente sobre o qual a caixa abre (pode ser null)
     */
    public static void info(Component pai, String msg) {
        JOptionPane.showMessageDialog(pai, mensagemLarga(msg), "Vida Ativa", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Pergunta Sim/Não ao usuário antes de uma ação (ex.: desativar alguém).
     *
     * @return {@code true} só se o usuário clicar em Sim; fechar a caixa conta como Não
     */
    public static boolean confirmar(Component pai, String msg) {
        return JOptionPane.showConfirmDialog(pai, mensagemLarga(msg), "Confirmar", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Mensagens longas quebram em linhas em vez de criar uma janela gigante. */
    private static Object mensagemLarga(String msg) {
        // O JOptionPane aceita qualquer objeto como "mensagem"; uma JTextArea com quebra de linha limita a largura da caixa.
        JTextArea area = new JTextArea(msg);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT);
        area.setColumns(Math.min(55, Math.max(24, msg.length())));
        area.setSize(new Dimension(460, 10));
        return area;
    }

    /** Cria um rótulo (JLabel) em cinza, usado ao lado dos campos dos formulários e em subtítulos. */
    public static JLabel rotulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.fonte(12.5f, Font.PLAIN));
        return l;
    }

    /** Título de cartão ou de seção: texto em negrito, tamanho 15. */
    public static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(Theme.fonte(15f, Font.BOLD));
        l.setForeground(Theme.TEXT);
        return l;
    }

    /**
     * Tabela somente leitura com o visual do tema.
     * Para preencher, use {@link #modelo(JTable)} e {@code addRow}; para esvaziar, {@link #limpar(JTable)}.
     *
     * @param colunas títulos das colunas, na ordem em que aparecem
     */
    public static JTable tabela(String... colunas) {
        // Sobrescreve isCellEditable para impedir que o usuário edite as células com duplo clique.
        DefaultTableModel m = new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(m);
        estilizarTabela(t);
        return t;
    }

    /**
     * Aplica o visual padrão a uma tabela: linhas altas e sem grade, listras suaves, destaque da linha sob o mouse,
     * seleção verde e cabeçalho em letras pequenas e cinzas. Uma linha selecionável por vez; colunas fixas (não dá
     * para arrastar).
     */
    public static void estilizarTabela(JTable t) {
        t.setRowHeight(44);
        t.setShowGrid(false);
        t.setShowHorizontalLines(false);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setOpaque(true);
        t.setBackground(Theme.PANEL);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFocusable(true);
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(10, 42));
        h.setDefaultRenderer(new CabecalhoRenderer());
        Celula celula = new Celula(t);
        t.setDefaultRenderer(Object.class, celula);
        t.setDefaultRenderer(Number.class, celula);
        // As colunas se ajustam ao conteúdo sempre que as linhas mudam (nomes e e-mails não ficam cortados com "...").
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        javax.swing.event.TableModelListener ouvinte = e -> ajustarColunas(t);
        t.getModel().addTableModelListener(ouvinte);
        t.addPropertyChangeListener("model", e -> {
            if (e.getOldValue() instanceof javax.swing.table.TableModel antigo) antigo.removeTableModelListener(ouvinte);
            t.getModel().addTableModelListener(ouvinte);
            ajustarColunas(t);
        });
        ajustarColunas(t);
    }

    /**
     * Define a largura preferida de cada coluna pelo maior texto (cabeçalho ou até 60 linhas) mais a folga das células.
     * O que sobrar de largura é repartido entre as colunas pelo próprio JTable.
     */
    public static void ajustarColunas(JTable t) {
        FontMetrics fm = t.getFontMetrics(t.getFont());
        FontMetrics fh = t.getFontMetrics(Theme.fonte(12f, Font.BOLD));
        int linhas = Math.min(t.getRowCount(), 60);
        for (int c = 0; c < t.getColumnCount(); c++) {
            int w = fh.stringWidth(String.valueOf(t.getColumnName(c))) + 40;
            for (int r = 0; r < linhas; r++) {
                Object v = t.getValueAt(r, c);
                if (v != null) w = Math.max(w, fm.stringWidth(v.toString()) + 44);
            }
            javax.swing.table.TableColumn col = t.getColumnModel().getColumn(c);
            col.setMinWidth(Math.min(w, 80));
            col.setPreferredWidth(Math.min(w, 380));
        }
    }

    /** Envolve um componente (normalmente uma tabela) em uma rolagem com cantos arredondados, borda e fundo do tema. */
    public static JScrollPane rolagem(JComponent c) {
        return new RolagemArredondada(c);
    }

    /** Linha de tabela: texto com folga, listra alternada, destaque sob o mouse e seleção verde. */
    private static final class Celula extends DefaultTableCellRenderer {
        private int linhaSob = -1;

        Celula(JTable t) {
            // Guarda a linha sob o mouse para pintá-la mais clara.
            t.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                    int r = t.rowAtPoint(e.getPoint());
                    if (r != linhaSob) { linhaSob = r; t.repaint(); }
                }
            });
            t.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseExited(java.awt.event.MouseEvent e) { linhaSob = -1; t.repaint(); }
            });
        }

        @Override
        public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foco, int r, int c) {
            super.getTableCellRendererComponent(tb, v, sel, false, r, c);
            setBorder(BorderFactory.createEmptyBorder(0, c == 0 ? 18 : 14, 0, 14));
            setHorizontalAlignment(SwingConstants.LEFT);
            setForeground(Theme.TEXT);
            setBackground(sel ? Theme.misturar(Theme.PANEL, Theme.GREEN, 0.20)
                    : r == linhaSob ? Theme.HOVER
                    : r % 2 == 1 ? Theme.misturar(Theme.PANEL, Color.WHITE, 0.025) : Theme.PANEL);
            return this;
        }
    }

    /** Cabeçalho da tabela: letras pequenas em maiúsculas, cinza, com uma linha embaixo. */
    private static final class CabecalhoRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foco, int r, int c) {
            super.getTableCellRendererComponent(tb, v == null ? "" : v.toString().toUpperCase(Locale.ROOT), false, false, r, c);
            setFont(Theme.fonte(11f, Font.BOLD));
            setForeground(Theme.MUTED);
            setBackground(Theme.PANEL);
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, c == 0 ? 18 : 14, 0, 14)));
            return this;
        }
    }

    /** Atalho para acessar o modelo de dados de uma tabela criada por {@link #tabela}. O cast só vale para tabelas que usam DefaultTableModel. */
    public static DefaultTableModel modelo(JTable t) { return (DefaultTableModel) t.getModel(); }

    /** Remove todas as linhas da tabela (as colunas continuam). */
    public static void limpar(JTable t) { modelo(t).setRowCount(0); }

    /** Faixa de botões alinhados à esquerda. */
    public static JPanel botoes(JComponent... itens) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (JComponent i : itens) p.add(i);
        return p;
    }

    /**
     * Mostra uma coluna de status como "selo" colorido (texto em negrito sobre fundo translúcido da mesma cor).
     *
     * @param coluna        índice da coluna no modelo, começando em 0
     * @param corPorTexto   função que recebe o texto exibido na célula (ex.: "Pago") e devolve a cor
     */
    public static void colorirColuna(JTable t, int coluna, java.util.function.Function<String, Color> corPorTexto) {
        t.getColumnModel().getColumn(coluna).setCellRenderer(new DefaultTableCellRenderer() {
            private String texto = "";
            private Color cor = Theme.MUTED;
            private Color fundo = Theme.PANEL;

            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foco, int r, int c) {
                texto = String.valueOf(v);
                cor = corPorTexto.apply(texto);
                Component base = tb.getDefaultRenderer(Object.class).getTableCellRendererComponent(tb, "", sel, false, r, c);
                fundo = base.getBackground();
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
                return this;
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(fundo);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setFont(Theme.fonte(12f, Font.BOLD));
                // O selo fica encostado à esquerda da célula, com a altura do texto.
                FontMetrics fm = g2.getFontMetrics();
                int larg = fm.stringWidth(texto) + 22;
                Chip.pintar(g2, texto, cor, 6, 0, Math.min(larg, getWidth() - 12), getHeight());
                g2.dispose();
            }
        });
    }

    /**
     * Converte o status gravado no banco ({@code PENDENTE}, {@code PAGO}, {@code ATRASADO}, {@code CANCELADO})
     * no texto mostrado na tela. Valores desconhecidos voltam como vieram.
     */
    public static String rotuloStatusPagamento(String s) {
        return switch (s) {
            case "PENDENTE" -> "Pendente";
            case "PAGO" -> "Pago";
            case "ATRASADO" -> "Atrasado";
            case "CANCELADO" -> "Cancelado";
            default -> s;
        };
    }

    /**
     * Converte a forma de pagamento gravada no banco ({@code PIX}, {@code CARTAO}, {@code BOLETO}, {@code DINHEIRO})
     * no texto da tela. Devolve um traço (—) se for nula (cobrança ainda não paga).
     */
    public static String rotuloForma(String f) {
        if (f == null) return "—";
        return switch (f) {
            case "PIX" -> "Pix";
            case "CARTAO" -> "Cartão";
            case "BOLETO" -> "Boleto";
            case "DINHEIRO" -> "Dinheiro";
            default -> f;
        };
    }

    /** Combo com as formas de pagamento aceitas. O valor de cada {@link Opcao} é o código gravado no banco. */
    public static JComboBox<Opcao> comboFormas() {
        return new JComboBox<>(new Opcao[]{new Opcao("PIX", "Pix"), new Opcao("CARTAO", "Cartão"),
                new Opcao("BOLETO", "Boleto"), new Opcao("DINHEIRO", "Dinheiro")});
    }

    /**
     * Seleciona no combo a opção cujo {@link Opcao#valor()} é igual a {@code valor}.
     * Se nenhuma bater, a seleção atual não muda.
     */
    public static void selecionarOpcao(JComboBox<Opcao> combo, String valor) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).valor().equals(valor)) { combo.setSelectedIndex(i); return; }
        }
    }

    /**
     * Área de texto de várias linhas (observações) com quebra automática. Normalmente vai dentro de um {@code JScrollPane}.
     *
     * @param linhas altura inicial, em linhas
     */
    public static JTextArea areaTexto(int linhas) {
        JTextArea a = new JTextArea(linhas, 20);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        a.setFont(Theme.fonte(13.5f, Font.PLAIN));
        return a;
    }
}
