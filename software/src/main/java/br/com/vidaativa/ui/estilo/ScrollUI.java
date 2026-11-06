package br.com.vidaativa.ui.estilo;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Barra de rolagem fina e discreta: sem setas, sem trilho, só uma "pílula" que clareia quando o mouse chega perto. */
public final class ScrollUI extends BasicScrollBarUI {
    private static final int LARGURA = 10;

    /** Chamado pelo Swing para criar o desenho de cada barra de rolagem. */
    public static ComponentUI createUI(JComponent c) { return new ScrollUI(); }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        scrollbar.setOpaque(false);
    }

    @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(LARGURA + 4, LARGURA + 4); }

    private static JButton semBotao() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override protected JButton createDecreaseButton(int orientation) { return semBotao(); }
    @Override protected JButton createIncreaseButton(int orientation) { return semBotao(); }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle area) { /* sem trilho */ }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle t) {
        if (t.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = Theme.suave(g);
        boolean forte = isDragging || isThumbRollover();
        g2.setColor(forte ? Theme.alfa(Theme.MUTED, 170) : Theme.alfa(Theme.MUTED, 80));
        boolean vertical = scrollbar.getOrientation() == Adjustable.VERTICAL;
        int x = vertical ? t.x + (t.width - LARGURA) / 2 : t.x;
        int y = vertical ? t.y : t.y + (t.height - LARGURA) / 2;
        int w = vertical ? LARGURA : t.width;
        int h = vertical ? t.height : LARGURA;
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, LARGURA, LARGURA));
        g2.dispose();
    }
}
