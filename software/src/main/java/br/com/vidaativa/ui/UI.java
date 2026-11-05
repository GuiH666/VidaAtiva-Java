package br.com.vidaativa.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
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
     * Aplica o visual padrão a uma tabela: linhas mais altas, grade escura, uma linha selecionável por vez
     * e cabeçalho com colunas fixas (não dá para arrastar). Também é usada por tabelas editáveis, como a do {@code TreinoEditor}.
     */
    public static void estilizarTabela(JTable t) {
        t.setRowHeight(28);
        t.setShowGrid(true);
        t.setGridColor(new Color(52, 52, 52));
        // Faz a tabela pintar o fundo até o fim da área visível, mesmo com poucas linhas.
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(10, 30));
        t.setIntercellSpacing(new Dimension(1, 1));
    }

    /** Envolve um componente (normalmente uma tabela) em um painel de rolagem com borda e fundo do tema. */
    public static JScrollPane rolagem(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.PANEL);
        return sp;
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
     * Pinta o texto de uma coluna de status com cor (verde, vermelho, amarelo, cinza).
     *
     * @param coluna        índice da coluna no modelo, começando em 0
     * @param corPorTexto   função que recebe o texto exibido na célula (ex.: "Pago") e devolve a cor
     */
    public static void colorirColuna(JTable t, int coluna, java.util.function.Function<String, Color> corPorTexto) {
        t.getColumnModel().getColumn(coluna).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foco, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, sel, foco, r, c);
                // Linha selecionada fica com texto branco para continuar legível sobre o verde escuro da seleção.
                comp.setForeground(sel ? Color.WHITE : corPorTexto.apply(String.valueOf(v)));
                comp.setFont(comp.getFont().deriveFont(Font.BOLD));
                return comp;
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
        return a;
    }
}
