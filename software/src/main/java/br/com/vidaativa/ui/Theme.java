package br.com.vidaativa.ui;

import br.com.vidaativa.ui.estilo.BotaoUI;
import br.com.vidaativa.ui.estilo.CampoBorda;
import br.com.vidaativa.ui.estilo.CheckIcone;
import br.com.vidaativa.ui.estilo.ComboUI;
import br.com.vidaativa.ui.estilo.ScrollUI;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;
import java.util.Set;

/**
 * Identidade visual do software (tema escuro, verde Vida Ativa): paleta, fontes e fábricas de botões.
 * <p>
 * Todas as telas usam as constantes daqui (em vez de escrever cores no código) para manter o visual igual em todo o
 * programa. Para mudar a identidade visual, altere as cores abaixo.
 * <p>
 * {@link #apply()} é chamada uma única vez, em {@link Main}, antes de criar qualquer janela. Ela configura o
 * {@link UIManager} (a tabela global de aparência do Swing) e instala os componentes desenhados à mão do pacote
 * {@code ui.estilo}: botões arredondados, campos com borda que acende no foco, combos e barras de rolagem finas.
 * <p>
 * Os nomes das constantes antigas ({@link #GREEN}, {@link #BG}, {@link #PANEL}...) foram mantidos para que as telas
 * existentes continuem compilando; as constantes novas (camadas, cores de gráfico, raios) estão logo abaixo delas.
 */
public final class Theme {
    // ----- Paleta base (nomes antigos mantidos) -----
    /** Cor de destaque (botão principal, item de menu ativo, números positivos). */
    public static final Color GREEN = new Color(34, 197, 94);
    /** Verde escuro, usado em linhas selecionadas de tabelas e listas. */
    public static final Color GREEN_DARK = new Color(21, 74, 46);
    /** Fundo das janelas. */
    public static final Color BG = new Color(11, 14, 13);
    /** Fundo de cartões e áreas de conteúdo (tabelas). */
    public static final Color PANEL = new Color(20, 25, 23);
    /** Fundo de campos e botões neutros. */
    public static final Color SURFACE = new Color(28, 34, 32);
    /** Cor de bordas e divisórias. */
    public static final Color BORDER = new Color(42, 50, 47);
    /** Cor padrão do texto. */
    public static final Color TEXT = new Color(236, 241, 239);
    /** Texto secundário (rótulos, subtítulos, itens inativos). */
    public static final Color MUTED = new Color(138, 149, 145);
    /** Vermelho: erros, atrasos, ações destrutivas. */
    public static final Color DANGER = new Color(248, 113, 113);
    /** Amarelo: avisos e itens pendentes. */
    public static final Color WARN = new Color(251, 191, 36);

    // ----- Camadas e cores novas -----
    /** Fundo do menu lateral (um pouco mais escuro que o fundo das telas). */
    public static final Color SIDEBAR = new Color(8, 11, 10);
    /** Cor de itens sob o mouse (menu, linhas, botões neutros). */
    public static final Color HOVER = new Color(36, 44, 41);
    /** Azul: informação e série secundária dos gráficos. */
    public static final Color INFO = new Color(56, 189, 248);
    /** Roxo: terceira série dos gráficos. */
    public static final Color ROXO = new Color(167, 139, 250);
    /** Laranja: quarta série dos gráficos. */
    public static final Color LARANJA = new Color(251, 146, 60);
    /** Sequência de cores para séries de gráficos (rosca, legendas). */
    public static final Color[] SERIE = {GREEN, INFO, ROXO, LARANJA, WARN, DANGER};

    /** Raio dos cantos de cartões e painéis. */
    public static final int RAIO = 14;
    /** Raio dos cantos de botões e campos. */
    public static final int RAIO_CAMPO = 10;

    /** Família de fonte escolhida em {@link #apply()} (a primeira instalada do computador). */
    private static String familia = "SansSerif";

    // Classe utilitária (só membros static): não deve ser instanciada.
    private Theme() { }

    // ------------------------------------------------------------------ fontes e cores

    /** Fonte do tema no tamanho e estilo pedidos (a família é escolhida em {@link #apply()}). */
    public static Font fonte(float tamanho, int estilo) {
        return new Font(familia, estilo, 1).deriveFont(tamanho);
    }

    /** A mesma cor com transparência ({@code alfa} de 0 a 255). */
    public static Color alfa(Color c, int alfa) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alfa)));
    }

    /** Mistura duas cores: {@code t = 0} devolve {@code a}, {@code t = 1} devolve {@code b}. */
    public static Color misturar(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Liga a suavização de bordas e de texto: todo desenho feito à mão no projeto começa por aqui. */
    public static Graphics2D suave(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    // ------------------------------------------------------------------ instalação do tema

    /**
     * Aplica o tema a todo o Swing: look and feel, fonte, cores, componentes desenhados à mão, seleção e barras.
     * Deve ser chamada antes de criar qualquer componente, porque os componentes leem esses valores quando são criados.
     */
    public static void apply() {
        // Se não for possível trocar o look and feel, o catch vazio abaixo ignora o erro e o programa segue com o padrão.
        try {
            // Metal é a base: respeita as cores abaixo em qualquer sistema operacional (e é sobre ele que os UIs próprios entram).
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }

        // --- Fonte: a primeira da lista que existir no computador (Windows, Mac, Linux), senão a padrão do Java ---
        Set<String> instaladas = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : new String[]{"Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Noto Sans"}) {
            if (instaladas.contains(f)) { familia = f; break; }
        }
        Font base = fonte(13.5f, Font.PLAIN);
        for (Enumeration<Object> keys = UIManager.getDefaults().keys(); keys.hasMoreElements(); ) {
            Object k = keys.nextElement();
            if (k.toString().endsWith(".font")) UIManager.put(k, new FontUIResource(base));
        }
        // Metal pinta textos de componentes desabilitados e negritos com fontes próprias; unifica.
        UIManager.put("Button.font", new FontUIResource(fonte(13.5f, Font.PLAIN)));
        UIManager.put("TableHeader.font", new FontUIResource(fonte(11.5f, Font.BOLD)));
        UIManager.put("ToolTip.font", new FontUIResource(fonte(12.5f, Font.PLAIN)));

        // --- Cores gerais: fundo escuro e texto claro ---
        for (String k : new String[]{"Panel.background", "OptionPane.background", "CheckBox.background", "RadioButton.background",
                "Viewport.background", "ScrollPane.background", "SplitPane.background", "ToggleButton.background",
                "Label.background", "Dialog.background", "RootPane.background", "ColorChooser.background"})
            UIManager.put(k, new ColorUIResource(BG));
        for (String k : new String[]{"Label.foreground", "CheckBox.foreground", "RadioButton.foreground", "OptionPane.messageForeground",
                "TitledBorder.titleColor", "ToggleButton.foreground", "Panel.foreground", "OptionPane.foreground"})
            UIManager.put(k, new ColorUIResource(TEXT));

        // --- Componentes desenhados à mão (pacote ui.estilo) ---
        UIManager.put("ButtonUI", BotaoUI.class.getName());
        UIManager.put("ComboBoxUI", ComboUI.class.getName());
        UIManager.put("ScrollBarUI", ScrollUI.class.getName());
        UIManager.put("CheckBox.icon", new CheckIcone());
        UIManager.put("Button.background", new ColorUIResource(SURFACE));
        UIManager.put("Button.foreground", new ColorUIResource(TEXT));
        UIManager.put("Button.select", new ColorUIResource(GREEN_DARK));
        UIManager.put("Button.focus", new ColorUIResource(alfa(GREEN, 0)));

        // --- Campos de entrada, listas e tabelas ---
        for (String c : new String[]{"TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Spinner", "List", "Table", "TextPane", "EditorPane"}) {
            UIManager.put(c + ".background", new ColorUIResource(c.equals("Table") || c.equals("List") ? PANEL : SURFACE));
            UIManager.put(c + ".foreground", new ColorUIResource(TEXT));
            UIManager.put(c + ".selectionBackground", new ColorUIResource(GREEN_DARK));
            UIManager.put(c + ".selectionForeground", new ColorUIResource(Color.WHITE));
            UIManager.put(c + ".caretForeground", new ColorUIResource(GREEN));
        }
        UIManager.put("TextField.border", new CampoBorda());
        UIManager.put("PasswordField.border", new CampoBorda());
        UIManager.put("FormattedTextField.border", new CampoBorda());
        UIManager.put("Spinner.border", new CampoBorda());
        UIManager.put("ComboBox.border", new CampoBorda());
        // Campo bloqueado (somente leitura) mantém o fundo escuro; sem isto o Metal pinta de cinza-claro.
        for (String k : new String[]{"TextField", "FormattedTextField", "TextArea", "PasswordField", "TextPane", "EditorPane"})
            UIManager.put(k + ".inactiveBackground", new ColorUIResource(SURFACE));
        UIManager.put("TextField.inactiveForeground", new ColorUIResource(MUTED));
        UIManager.put("TextArea.inactiveForeground", new ColorUIResource(MUTED));
        UIManager.put("ComboBox.disabledBackground", new ColorUIResource(SURFACE));
        UIManager.put("ComboBox.disabledForeground", new ColorUIResource(MUTED));
        UIManager.put("ComboBox.buttonBackground", new ColorUIResource(SURFACE));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(GREEN_DARK));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("Table.gridColor", new ColorUIResource(BORDER));
        UIManager.put("TableHeader.background", new ColorUIResource(PANEL));
        UIManager.put("TableHeader.foreground", new ColorUIResource(MUTED));
        UIManager.put("TextArea.margin", new Insets(6, 8, 6, 8));

        // --- Rolagem, dicas, divisórias e caixas de mensagem ---
        UIManager.put("ScrollBar.background", new ColorUIResource(BG));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(BORDER));
        UIManager.put("ScrollBar.track", new ColorUIResource(BG));
        // Rolagem de áreas de texto ganha o mesmo contorno dos campos; tabelas e listas trocam a borda por conta própria (UI.rolagem).
        UIManager.put("ScrollPane.border", new CampoBorda(1, 1, 1, 1));
        // Itens de lista (combos e listas) com folga e sem o retângulo de foco do Metal.
        UIManager.put("List.cellNoFocusBorder", BorderFactory.createEmptyBorder(8, 10, 8, 10));
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createEmptyBorder(8, 10, 8, 10));
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        UIManager.put("ToolTip.background", new ColorUIResource(SURFACE));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(5, 9, 5, 9)));
        UIManager.put("Separator.foreground", new ColorUIResource(BORDER));
        UIManager.put("Separator.background", new ColorUIResource(BG));
        UIManager.put("PopupMenu.background", new ColorUIResource(SURFACE));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        for (String m : new String[]{"MenuItem", "Menu", "CheckBoxMenuItem"}) {
            UIManager.put(m + ".background", new ColorUIResource(SURFACE));
            UIManager.put(m + ".foreground", new ColorUIResource(TEXT));
            UIManager.put(m + ".selectionBackground", new ColorUIResource(GREEN_DARK));
            UIManager.put(m + ".selectionForeground", new ColorUIResource(Color.WHITE));
            UIManager.put(m + ".border", BorderFactory.createEmptyBorder(8, 14, 8, 14));
            UIManager.put(m + ".acceleratorForeground", new ColorUIResource(MUTED));
        }
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(6, 12, 12, 12));
        UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(14, 14, 0, 14));
        // Chaves "de base" do Metal: várias partes do visual (bordas 3D, fundo de componentes) derivam delas.
        UIManager.put("control", new ColorUIResource(BG));
        UIManager.put("controlShadow", new ColorUIResource(BORDER));
        UIManager.put("controlDkShadow", new ColorUIResource(BORDER));
        UIManager.put("controlHighlight", new ColorUIResource(BORDER));
        UIManager.put("text", new ColorUIResource(TEXT));
        UIManager.put("window", new ColorUIResource(BG));
        UIManager.put("info", new ColorUIResource(SURFACE));
        UIManager.put("menu", new ColorUIResource(SURFACE));
        // Para suavizar texto também nos componentes padrão (labels, campos).
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        // Repinta o campo que ganha/perde o foco, para a borda dele acender (veja CampoBorda).
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
            if (e.getOldValue() instanceof Component c) repintarCampo(c);
            if (e.getNewValue() instanceof Component c) repintarCampo(c);
        });
    }

    /** Repinta o componente e o pai dele (o combo e o spinner são formados por filhos que recebem o foco). */
    private static void repintarCampo(Component c) {
        c.repaint();
        if (c.getParent() instanceof JComponent p) p.repaint();
    }

    // ------------------------------------------------------------------ botões

    /** Botão neutro (cinza), para ações comuns como Editar, Cancelar e Sair. */
    public static JButton button(String text) {
        JButton b = new JButton(text);
        BotaoUI.estilo(b, BotaoUI.Estilo.NEUTRO);
        return b;
    }

    /** Botão de ação principal (verde, texto em negrito). Use um por tela ou janela, como Salvar ou Entrar. */
    public static JButton primary(String text) {
        JButton b = new JButton(text);
        BotaoUI.estilo(b, BotaoUI.Estilo.PRINCIPAL);
        return b;
    }

    /** Botão de ação destrutiva (vermelho), para ações difíceis de desfazer. */
    public static JButton danger(String text) {
        JButton b = new JButton(text);
        BotaoUI.estilo(b, BotaoUI.Estilo.PERIGO);
        return b;
    }

    /** Botão discreto, sem fundo (só texto e ícone), para ações secundárias como "Limpar" ou os ▲▼ de uma linha. */
    public static JButton ghost(String text) {
        JButton b = new JButton(text);
        BotaoUI.estilo(b, BotaoUI.Estilo.DISCRETO);
        return b;
    }
}
