package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.estilo.FundoCampo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Painel com fundo arredondado e borda fina: o "cartão" onde ficam indicadores, formulários e gráficos.
 * <p>
 * Os filhos são adicionados normalmente ({@code add}). O espaçamento interno é dado no construtor. Como o fundo é
 * desenhado pelo próprio cartão (o painel não é opaco), ele implementa {@link FundoCampo}: assim os campos de texto
 * colocados dentro dele recortam os cantos com a cor certa.
 */
public class Cartao extends JPanel implements FundoCampo {
    private Color fundo = Theme.PANEL;
    private Color borda = Theme.BORDER;

    /** Cartão com o layout dado e espaçamento interno de {@code padding} pixels. */
    public Cartao(LayoutManager layout, int padding) {
        this(layout, padding, padding);
    }

    /** Cartão com espaçamento interno diferente na vertical e na horizontal. */
    public Cartao(LayoutManager layout, int vertical, int horizontal) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }

    /** Troca as cores do cartão (ex.: um fundo levemente colorido para destacar um aviso). */
    public Cartao cores(Color fundo, Color borda) {
        this.fundo = fundo;
        this.borda = borda;
        repaint();
        return this;
    }

    @Override public Color fundoParaCampos() { return fundo; }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.suave(g);
        float r = Theme.RAIO;
        RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, r * 2, r * 2);
        g2.setColor(fundo);
        g2.fill(forma);
        if (borda != null) {
            g2.setColor(borda);
            g2.draw(forma);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
