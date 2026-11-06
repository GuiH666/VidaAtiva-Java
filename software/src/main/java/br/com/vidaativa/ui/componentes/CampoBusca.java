package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Campo de texto de busca: lupa à esquerda e texto de exemplo em cinza enquanto está vazio
 * (ex.: "Buscar por nome, documento ou telefone"). Funciona como um {@code JTextField} normal.
 */
public class CampoBusca extends JTextField {
    private final String dica;

    public CampoBusca(String dica, int colunas) {
        super(colunas);
        this.dica = dica;
        setToolTipText(dica);
    }

    @Override
    public Insets getInsets() {
        Insets i = super.getInsets();
        // Abre espaço à esquerda para a lupa.
        return new Insets(i.top, i.left + 24, i.bottom, i.right);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.suave(g);
        Icones.desenhar(g2, Icones.Tipo.BUSCA, 12, (getHeight() - 16) / 2.0, 16, Theme.MUTED);
        if (getText().isEmpty()) {
            g2.setFont(getFont());
            g2.setColor(Theme.alfa(Theme.MUTED, 170));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(dica, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}
