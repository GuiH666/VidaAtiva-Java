package br.com.vidaativa.ui.estilo;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * Borda dos campos de texto, combos e áreas de texto: contorno arredondado cinza que fica verde quando o campo tem o foco.
 * <p>
 * Como o fundo do campo é um retângulo, a borda também pinta os quatro cantos com a cor que está atrás do campo
 * (o primeiro ancestral opaco ou {@link FundoCampo}), o que dá a aparência arredondada sem precisar trocar o UI do campo.
 * A borda é pintada depois do fundo, por isso consegue "cobrir" os cantos.
 */
public final class CampoBorda implements Border, UIResource {
    private final Insets margem;

    /** Borda com a folga padrão (8 em cima e embaixo, 12 nos lados) entre o contorno e o texto. */
    public CampoBorda() { this(8, 12, 8, 12); }

    /** Borda com folga própria (usada em combos e rolagens, que já têm espaço interno). */
    public CampoBorda(int cima, int esq, int baixo, int dir) { margem = new Insets(cima, esq, baixo, dir); }

    @Override public Insets getBorderInsets(Component c) { return (Insets) margem.clone(); }

    @Override public boolean isBorderOpaque() { return false; }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
        Graphics2D g2 = Theme.suave(g);
        float r = Theme.RAIO_CAMPO;
        // 1) Recorta os cantos com a cor do fundo que está atrás do campo.
        Area cantos = new Area(new Rectangle(x, y, w, h));
        cantos.subtract(new Area(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2)));
        g2.setColor(fundoAtras(c));
        g2.fill(cantos);
        // 2) Contorno: verde com foco, cinza normal, apagado se desabilitado.
        boolean foco = temFoco(c);
        g2.setColor(!c.isEnabled() ? Theme.alfa(Theme.BORDER, 120) : foco ? Theme.GREEN : Theme.BORDER);
        g2.setStroke(new BasicStroke(foco ? 1.6f : 1f));
        g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1f, h - 1f, r * 2, r * 2));
        g2.dispose();
    }

    /** True se o componente (ou um filho dele, como o editor de um combo editável) tem o foco do teclado. */
    private static boolean temFoco(Component c) {
        Component dono = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return dono != null && SwingUtilities.isDescendingFrom(dono, c);
    }

    /** Cor do que está atrás do campo: sobe pelos pais até achar um {@link FundoCampo} ou um componente opaco. */
    private static Color fundoAtras(Component c) {
        for (Component p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof FundoCampo f) return f.fundoParaCampos();
            if (p instanceof JComponent jc && jc.isOpaque()) return jc.getBackground();
            if (!(p instanceof JComponent)) return p.getBackground();
        }
        return Theme.BG;
    }
}
