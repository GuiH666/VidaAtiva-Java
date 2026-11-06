package br.com.vidaativa.ui.estilo;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Desenho dos botões: cantos arredondados, cor própria por estilo e reação ao mouse (passar por cima e apertar).
 * <p>
 * Instalado para todos os {@code JButton} do programa por {@link Theme#apply()}; por isso até os botões das caixas de
 * mensagem ficam no padrão. Para escolher o estilo de um botão use {@link Theme#button}, {@link Theme#primary},
 * {@link Theme#danger} ou {@link Theme#ghost}, ou chame {@link #estilo(AbstractButton, Estilo)}.
 */
public final class BotaoUI extends BasicButtonUI {
    /** Aparências disponíveis para um botão. */
    public enum Estilo { NEUTRO, PRINCIPAL, PERIGO, DISCRETO }

    private static final String CHAVE = "va.estilo";
    private static final Color TEXTO_SOBRE_VERDE = new Color(6, 26, 14);

    /** Chamado pelo Swing para criar o desenho de cada botão. */
    public static ComponentUI createUI(JComponent c) { return new BotaoUI(); }

    /** Define o estilo do botão e ajusta o que depende dele (borda interna, cursor, fonte, cor do texto). */
    public static void estilo(AbstractButton b, Estilo e) {
        b.putClientProperty(CHAVE, e);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        b.setHorizontalAlignment(SwingConstants.CENTER);
        boolean forte = e == Estilo.PRINCIPAL || e == Estilo.PERIGO;
        b.setFont(Theme.fonte(13.5f, forte ? Font.BOLD : Font.PLAIN));
        b.setForeground(corTexto(e));
        b.repaint();
    }

    private static Estilo estiloDe(AbstractButton b) {
        return b.getClientProperty(CHAVE) instanceof Estilo e ? e : Estilo.NEUTRO;
    }

    private static Color corTexto(Estilo e) {
        return switch (e) {
            case PRINCIPAL -> TEXTO_SOBRE_VERDE;
            case PERIGO -> Theme.DANGER;
            case DISCRETO -> Theme.MUTED;
            default -> Theme.TEXT;
        };
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        // Botões criados sem passar pelas fábricas do Theme (ex.: caixas de mensagem) entram como botão neutro.
        if (b.getClientProperty(CHAVE) == null) estilo(b, Estilo.NEUTRO);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        Estilo e = estiloDe(b);
        ButtonModel m = b.getModel();
        boolean ativo = b.isEnabled();
        boolean sobre = ativo && (m.isRollover() || m.isArmed());
        boolean apertado = ativo && m.isPressed();

        Graphics2D g2 = Theme.suave(g);
        float r = Theme.RAIO_CAMPO;
        RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1f, c.getHeight() - 1f, r * 2, r * 2);

        Color fundo; Color borda = null;
        switch (e) {
            case PRINCIPAL -> {
                fundo = apertado ? Theme.misturar(Theme.GREEN, Color.BLACK, 0.18) : sobre ? Theme.misturar(Theme.GREEN, Color.WHITE, 0.16) : Theme.GREEN;
            }
            case PERIGO -> {
                fundo = Theme.alfa(Theme.DANGER, apertado ? 80 : sobre ? 60 : 34);
                borda = Theme.alfa(Theme.DANGER, 120);
            }
            case DISCRETO -> fundo = sobre ? Theme.HOVER : new Color(0, 0, 0, 0);
            default -> {
                fundo = apertado ? Theme.misturar(Theme.SURFACE, Color.BLACK, 0.25) : sobre ? Theme.HOVER : Theme.SURFACE;
                borda = Theme.BORDER;
            }
        }
        if (!ativo) fundo = Theme.alfa(fundo, Math.max(40, fundo.getAlpha() / 2));
        g2.setColor(fundo);
        g2.fill(forma);
        if (borda != null) {
            g2.setColor(ativo ? borda : Theme.alfa(borda, 60));
            g2.draw(forma);
        }
        g2.dispose();

        // Texto: no estilo discreto, sobe para a cor normal quando o mouse está em cima.
        if (e == Estilo.DISCRETO) b.setForeground(sobre ? Theme.TEXT : Theme.MUTED);
        super.paint(g, c);
    }

    @Override
    protected void paintText(Graphics g, JComponent c, Rectangle area, String texto) {
        AbstractButton b = (AbstractButton) c;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setFont(b.getFont());
        FontMetrics fm = g2.getFontMetrics();
        Color cor = b.isEnabled() ? b.getForeground() : Theme.alfa(b.getForeground(), 100);
        g2.setColor(cor);
        g2.drawString(texto, area.x, area.y + fm.getAscent());
        g2.dispose();
    }
}
