package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;

/** Campo de texto que mostra um texto de exemplo em cinza (ex.: "Nome do exercício") enquanto está vazio. */
public class CampoDica extends JTextField {
    private final String dica;

    public CampoDica(String dica) {
        this.dica = dica;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && isEnabled()) {
            Graphics2D g2 = Theme.suave(g);
            g2.setFont(getFont());
            g2.setColor(Theme.alfa(Theme.MUTED, 150));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(dica, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
