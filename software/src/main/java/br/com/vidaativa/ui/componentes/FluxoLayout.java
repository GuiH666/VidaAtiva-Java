package br.com.vidaativa.ui.componentes;

import javax.swing.SwingUtilities;
import java.awt.*;

/**
 * {@code FlowLayout} que quebra a linha de verdade: o tamanho preferido do painel já considera as linhas extras.
 * <p>
 * O {@code FlowLayout} padrão do Swing quebra a linha mas continua informando altura de uma linha só, o que corta o
 * conteúdo. Esta versão calcula a altura com base na largura atual do painel. Usado nas fileiras de "pílulas".
 */
public class FluxoLayout extends FlowLayout {
    /** Largura do painel na última vez em que a altura foi calculada; e a última largura para a qual já se pediu nova medição. */
    private int larguraMedida = -1, larguraAgendada = -1;

    public FluxoLayout(int alinhamento, int espacoH, int espacoV) { super(alinhamento, espacoH, espacoV); }

    /**
     * Posiciona as pílulas e confere se a altura informada aos pais foi calculada com a largura que o painel ganhou.
     * Se não foi (o pai mediu antes de a largura ser conhecida), pede uma nova medição uma única vez por largura.
     */
    @Override public void layoutContainer(Container pai) {
        super.layoutContainer(pai);
        int w = pai.getWidth();
        if (w > 0 && w != larguraMedida && w != larguraAgendada) {
            larguraAgendada = w;
            SwingUtilities.invokeLater(() -> { pai.invalidate(); pai.revalidate(); });
        }
    }

    @Override public Dimension preferredLayoutSize(Container pai) { return medir(pai, true); }
    @Override public Dimension minimumLayoutSize(Container pai) { Dimension d = medir(pai, false); d.width -= getHgap() + 1; return d; }

    private Dimension medir(Container pai, boolean preferido) {
        synchronized (pai.getTreeLock()) {
            int largura = pai.getWidth();
            larguraMedida = largura;
            if (largura == 0) largura = Integer.MAX_VALUE;
            Insets in = pai.getInsets();
            int maxLinha = largura - (in.left + in.right + getHgap() * 2);
            Dimension total = new Dimension(0, 0);
            int larguraLinha = 0, alturaLinha = 0;
            for (Component c : pai.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = preferido ? c.getPreferredSize() : c.getMinimumSize();
                if (larguraLinha + d.width > maxLinha && larguraLinha > 0) {
                    total.width = Math.max(total.width, larguraLinha);
                    total.height += alturaLinha + getVgap();
                    larguraLinha = 0; alturaLinha = 0;
                }
                if (larguraLinha > 0) larguraLinha += getHgap();
                larguraLinha += d.width;
                alturaLinha = Math.max(alturaLinha, d.height);
            }
            total.width = Math.max(total.width, larguraLinha);
            total.height += alturaLinha;
            total.width += in.left + in.right + getHgap() * 2;
            total.height += in.top + in.bottom + getVgap() * 2;
            return total;
        }
    }
}
