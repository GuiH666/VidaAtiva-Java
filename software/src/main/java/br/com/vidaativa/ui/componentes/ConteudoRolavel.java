package br.com.vidaativa.ui.componentes;

import javax.swing.*;
import java.awt.*;

/**
 * Painel para colocar dentro de um {@code JScrollPane} que acompanha a largura da janela (só rola na vertical).
 * Sem isto o Swing daria ao conteúdo a largura preferida dele e criaria uma barra horizontal; com isto, layouts como
 * {@link GradeFluida} reorganizam as colunas quando a janela muda de tamanho.
 */
public class ConteudoRolavel extends JPanel implements Scrollable {
    public ConteudoRolavel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    /**
     * Quando a largura muda (janela redimensionada), as alturas dos filhos que dependem da largura precisam ser
     * recalculadas; por isso pede nova validação logo depois.
     */
    @Override
    public void setBounds(int x, int y, int w, int h) {
        boolean mudouLargura = w != getWidth();
        super.setBounds(x, y, w, h);
        if (mudouLargura) SwingUtilities.invokeLater(() -> { invalidate(); revalidate(); });
    }

    /** Cria o painel já dentro de uma rolagem sem borda e sem fundo. */
    public static JScrollPane embrulhar(ConteudoRolavel c) {
        JScrollPane sp = new JScrollPane(c, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        return sp;
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(80, r.height - 60); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
