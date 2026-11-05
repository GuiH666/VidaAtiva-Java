package br.com.vidaativa.ui;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

/**
 * Tema escuro do software (verde Vida Ativa): paleta de cores e fábricas de botões estilizados.
 * <p>
 * Todas as telas usam as constantes daqui (em vez de escrever cores no código) para manter o visual
 * igual em todo o programa. Para mudar a identidade visual, altere as cores abaixo.
 * <p>
 * {@link #apply()} é chamada uma única vez, em {@link Main}, antes de criar qualquer janela.
 * Ela configura o {@link UIManager}, que é a tabela global de aparência do Swing.
 */
public final class Theme {
    // --- Paleta ---
    /** Cor de destaque (títulos, botão principal, item de menu selecionado). */
    public static final Color GREEN = new Color(10, 190, 10);
    /** Verde escuro, usado em linhas selecionadas de tabelas e listas. */
    public static final Color GREEN_DARK = new Color(18, 110, 30);
    /** Fundo das janelas. */
    public static final Color BG = new Color(15, 15, 15);
    /** Fundo de áreas de conteúdo (tabelas, cartões). */
    public static final Color PANEL = new Color(28, 28, 28);
    /** Fundo de campos e botões neutros. */
    public static final Color SURFACE = new Color(38, 38, 38);
    /** Cor de bordas e divisórias. */
    public static final Color BORDER = new Color(64, 64, 64);
    /** Cor padrão do texto. */
    public static final Color TEXT = new Color(235, 235, 235);
    /** Texto secundário (rótulos, subtítulos, itens inativos). */
    public static final Color MUTED = new Color(150, 150, 150);
    /** Vermelho: erros, atrasos, ações destrutivas. */
    public static final Color DANGER = new Color(235, 90, 90);
    /** Amarelo: avisos e itens pendentes. */
    public static final Color WARN = new Color(240, 185, 50);

    // Classe utilitária (só membros static): não deve ser instanciada.
    private Theme() { }

    /**
     * Aplica o tema escuro a todo o Swing: look and feel, fonte, cores de fundo, texto, seleção e barras.
     * Deve ser chamada antes de criar qualquer componente, porque os componentes leem esses valores
     * quando são criados.
     */
    public static void apply() {
        // Se não for possível trocar o look and feel, o catch vazio abaixo ignora o erro e o programa segue com o look and feel padrão.
        try {
            // Metal respeita as cores definidas abaixo em qualquer sistema operacional.
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }

        // --- Fonte ---
        // Troca a fonte de todos os componentes: toda chave do UIManager que termina em ".font" recebe a mesma fonte.
        Font base = new Font("SansSerif", Font.PLAIN, 13);
        for (Enumeration<Object> keys = UIManager.getDefaults().keys(); keys.hasMoreElements(); ) {
            Object k = keys.nextElement();
            if (k.toString().endsWith(".font")) UIManager.put(k, new FontUIResource(base));
        }

        // --- Cores gerais: fundo escuro e texto claro ---
        for (String k : new String[]{"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "Viewport.background", "ScrollPane.background", "TabbedPane.background", "TabbedPane.contentAreaColor",
                "SplitPane.background", "ToggleButton.background", "TabbedPane.unselectedBackground"})
            UIManager.put(k, BG);
        for (String k : new String[]{"Label.foreground", "CheckBox.foreground", "RadioButton.foreground", "OptionPane.messageForeground",
                "TabbedPane.foreground", "TitledBorder.titleColor", "ToggleButton.foreground"})
            UIManager.put(k, TEXT);

        // --- Botões ---
        // Button.select = cor do botão enquanto o mouse está pressionado; Button.focus = cor do contorno de foco.
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", GREEN_DARK);
        UIManager.put("Button.focus", GREEN);

        // --- Campos de entrada, listas e tabelas ---
        // Tabela e lista usam o fundo PANEL; os demais campos usam SURFACE (um pouco mais claro).
        for (String c : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Spinner", "List", "Table"}) {
            UIManager.put(c + ".background", c.equals("Table") || c.equals("List") ? PANEL : SURFACE);
            UIManager.put(c + ".foreground", TEXT);
            UIManager.put(c + ".selectionBackground", GREEN_DARK);
            UIManager.put(c + ".selectionForeground", Color.WHITE);
            UIManager.put(c + ".caretForeground", TEXT);
        }
        // Campos não editáveis (ex.: consulta) e combos desabilitados aparecem em cinza.
        UIManager.put("TextField.inactiveForeground", MUTED);
        UIManager.put("TextArea.inactiveForeground", MUTED);
        UIManager.put("ComboBox.disabledBackground", SURFACE);
        UIManager.put("ComboBox.disabledForeground", MUTED);
        UIManager.put("ComboBox.buttonBackground", SURFACE);
        UIManager.put("Table.gridColor", new Color(52, 52, 52));
        UIManager.put("TableHeader.background", new Color(44, 44, 44));
        UIManager.put("TableHeader.foreground", TEXT);
        // --- Abas, barras de rolagem, dicas e divisórias ---
        UIManager.put("TabbedPane.selected", PANEL);
        UIManager.put("TabbedPane.selectHighlight", GREEN);
        UIManager.put("TabbedPane.light", BORDER);
        UIManager.put("TabbedPane.shadow", BORDER);
        UIManager.put("TabbedPane.darkShadow", BORDER);
        UIManager.put("TabbedPane.focus", GREEN);
        UIManager.put("ScrollBar.background", BG);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("ToolTip.background", SURFACE);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("Separator.background", BG);
        // Chaves "de base" do Metal: várias partes do visual (bordas 3D, fundo de componentes) derivam delas.
        UIManager.put("control", BG);
        UIManager.put("controlShadow", BORDER);
        UIManager.put("controlDkShadow", BORDER);
        UIManager.put("controlHighlight", BORDER);
        UIManager.put("text", TEXT);
    }

    /**
     * Botão neutro (cinza), para ações comuns como Editar, Cancelar e Sair.
     *
     * @param text texto do botão
     */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        estilizar(b, SURFACE, TEXT);
        return b;
    }

    /**
     * Botão de ação principal (verde, texto em negrito). Use um por tela ou janela, como Salvar ou Entrar.
     *
     * @param text texto do botão
     */
    public static JButton primary(String text) {
        JButton b = new JButton(text);
        estilizar(b, GREEN, Color.BLACK);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    /**
     * Botão de ação destrutiva (vermelho), para ações difíceis de desfazer.
     *
     * @param text texto do botão
     */
    public static JButton danger(String text) {
        JButton b = new JButton(text);
        estilizar(b, new Color(150, 45, 45), Color.WHITE);
        return b;
    }

    /** Aplica as cores, a borda e o cursor de mão em um botão. Usada pelas três fábricas acima. */
    private static void estilizar(JButton b, Color fundo, Color texto) {
        b.setBackground(fundo);
        b.setForeground(texto);
        b.setFocusPainted(false);
        // O Metal pode ignorar a cor de fundo se o botão não for opaco; por isso o setOpaque(true).
        b.setOpaque(true);
        // A borda do botão neutro usa BORDER; os coloridos usam uma versão mais escura da própria cor.
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fundo.equals(SURFACE) ? BORDER : fundo.darker(), 1),
                BorderFactory.createEmptyBorder(7, 16, 7, 16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
