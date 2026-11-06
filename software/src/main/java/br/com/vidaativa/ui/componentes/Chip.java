package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Selo de status: texto curto colorido sobre um fundo da mesma cor com transparência (ex.: "Pago" em verde, "Atrasado"
 * em vermelho). Existe como componente ({@code new Chip("Ativa", Theme.GREEN)}) e como desenho avulso
 * ({@link #pintar}), usado pelas células das tabelas.
 */
public class Chip extends JComponent {
    private String texto;
    private Color cor;

    public Chip(String texto, Color cor) {
        this.texto = texto;
        this.cor = cor;
        setFont(Theme.fonte(12f, Font.BOLD));
        setOpaque(false);
    }

    /** Troca texto e cor (ex.: quando o status da ficha muda na tela). */
    public void definir(String texto, Color cor) {
        this.texto = texto;
        this.cor = cor;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(texto) + 22, fm.getHeight() + 8);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.suave(g);
        g2.setFont(getFont());
        pintar(g2, texto, cor, 0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    /**
     * Desenha o selo centralizado no retângulo dado, com o tamanho do texto (limitado à largura disponível).
     *
     * @param g2 contexto com suavização e com a fonte já definida
     */
    public static void pintar(Graphics2D g2, String texto, Color cor, int x, int y, int w, int h) {
        FontMetrics fm = g2.getFontMetrics();
        int largura = Math.min(w, fm.stringWidth(texto) + 22);
        int altura = Math.min(h, fm.getHeight() + 6);
        int px = x + (w - largura) / 2, py = y + (h - altura) / 2;
        g2.setColor(Theme.alfa(cor, 40));
        g2.fill(new RoundRectangle2D.Float(px, py, largura, altura, altura, altura));
        g2.setColor(cor);
        g2.drawString(texto, px + (largura - fm.stringWidth(texto)) / 2f, py + (altura - fm.getHeight()) / 2f + fm.getAscent());
    }
}
