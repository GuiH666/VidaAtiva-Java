package br.com.vidaativa.ui.estilo;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Ícone das caixas de marcação ({@code JCheckBox}): quadrado arredondado que fica verde com um "visto" quando marcado. */
public final class CheckIcone implements Icon, UIResource {
    private static final int T = 18;

    @Override public int getIconWidth() { return T; }
    @Override public int getIconHeight() { return T; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        AbstractButton b = (AbstractButton) c;
        boolean marcado = b.isSelected();
        Graphics2D g2 = Theme.suave(g);
        RoundRectangle2D caixa = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, T - 1, T - 1, 7, 7);
        if (marcado) {
            g2.setColor(b.isEnabled() ? Theme.GREEN : Theme.alfa(Theme.GREEN, 90));
            g2.fill(caixa);
            Path2D visto = new Path2D.Float();
            visto.moveTo(x + 4.6, y + 9.4);
            visto.lineTo(x + 7.6, y + 12.4);
            visto.lineTo(x + 13.4, y + 5.8);
            g2.setColor(new Color(6, 26, 14));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(visto);
        } else {
            g2.setColor(b.getModel().isRollover() ? Theme.HOVER : Theme.SURFACE);
            g2.fill(caixa);
            g2.setColor(b.getModel().isRollover() ? Theme.MUTED : Theme.BORDER);
            g2.draw(caixa);
        }
        g2.dispose();
    }
}
