package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Rolagem com cantos arredondados e contorno fino, usada em volta das tabelas. O conteúdo (linhas da tabela) é
 * recortado no formato arredondado, para a última linha não "vazar" pelos cantos.
 */
public class RolagemArredondada extends JScrollPane {
    public RolagemArredondada(JComponent conteudo) {
        super(conteudo);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Theme.PANEL);
        setBackground(Theme.PANEL);
        getVerticalScrollBar().setUnitIncrement(28);
    }

    private Shape forma() {
        float r = Theme.RAIO;
        return new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, r * 2, r * 2);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = Theme.suave(g);
        g2.clip(forma());
        super.paint(g2);
        g2.dispose();
        Graphics2D g3 = Theme.suave(g);
        g3.setColor(Theme.BORDER);
        g3.draw(forma());
        g3.dispose();
    }
}
