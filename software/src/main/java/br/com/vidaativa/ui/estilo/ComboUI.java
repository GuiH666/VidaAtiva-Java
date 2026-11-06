package br.com.vidaativa.ui.estilo;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * Desenho dos combos ({@code JComboBox}): campo no mesmo estilo dos campos de texto, seta em "v" fina e lista suspensa
 * escura com itens mais altos e destaque verde. A borda é a {@link CampoBorda}, que também acende no foco.
 */
public final class ComboUI extends BasicComboBoxUI {
    /** Chamado pelo Swing para criar o desenho de cada combo. */
    public static ComponentUI createUI(JComponent c) { return new ComboUI(); }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        comboBox.setOpaque(true);
        comboBox.setBackground(Theme.SURFACE);
        comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        comboBox.setBorder(new CampoBorda(2, 2, 2, 2));
    }

    @Override
    protected JButton createArrowButton() {
        JButton b = new JButton() {
            @Override public void paint(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                Path2D seta = new Path2D.Float();
                seta.moveTo(cx - 4.5, cy - 2);
                seta.lineTo(cx, cy + 2.5);
                seta.lineTo(cx + 4.5, cy - 2);
                g2.setColor(comboBox.isEnabled() ? Theme.MUTED : Theme.alfa(Theme.MUTED, 90));
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(seta);
                g2.dispose();
            }
        };
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(30, 20));
        return b;
    }

    @Override
    protected ListCellRenderer<Object> createRenderer() {
        return new Item();
    }

    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup p = new BasicComboPopup(comboBox);
        p.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        p.getList().setBackground(Theme.PANEL);
        p.getList().setSelectionBackground(Theme.GREEN_DARK);
        p.getList().setFixedCellHeight(-1);
        return p;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean temFoco) {
        g.setColor(comboBox.isEnabled() ? comboBox.getBackground() : Theme.SURFACE);
        g.fillRect(r.x, r.y, r.width, r.height);
    }

    /** Item da lista (e valor atual do combo): texto com folga e cores do tema. */
    private static final class Item extends DefaultListCellRenderer implements UIResource {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foco) {
            super.getListCellRendererComponent(l, v, i, sel, false);
            // i < 0 é o valor mostrado no próprio combo (fechado); i >= 0 são as linhas da lista suspensa.
            setBorder(BorderFactory.createEmptyBorder(i < 0 ? 6 : 8, 10, i < 0 ? 6 : 8, 10));
            setBackground(i < 0 ? Theme.SURFACE : sel ? Theme.GREEN_DARK : Theme.PANEL);
            setForeground(sel && i >= 0 ? Color.WHITE : Theme.TEXT);
            setOpaque(true);
            return this;
        }
    }
}
