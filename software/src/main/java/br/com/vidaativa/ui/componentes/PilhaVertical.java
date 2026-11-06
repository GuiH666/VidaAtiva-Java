package br.com.vidaativa.ui.componentes;

import java.awt.*;

/**
 * Empilha os componentes na vertical, cada um com a largura toda e a altura que ele pede PARA essa largura, com um
 * espaço fixo entre eles.
 * <p>
 * Existe porque o {@code BoxLayout} guarda as alturas calculadas antes de a largura ser conhecida; com filhos que
 * mudam de altura conforme a largura (como {@link GradeFluida}, que troca de colunas) isso deixava buracos na tela.
 * Aqui, a largura do contêiner é passada a cada filho antes de perguntar a altura.
 */
public class PilhaVertical implements LayoutManager {
    private final int espaco;

    public PilhaVertical(int espaco) { this.espaco = espaco; }

    @Override public void addLayoutComponent(String nome, Component c) { }
    @Override public void removeLayoutComponent(Component c) { }

    private int altura(Component c, int largura) {
        if (c.getWidth() != largura) c.setSize(largura, Math.max(1, c.getHeight()));
        return c.getPreferredSize().height;
    }

    @Override
    public Dimension preferredLayoutSize(Container pai) {
        synchronized (pai.getTreeLock()) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() > 0 ? pai.getWidth() - in.left - in.right : 600;
            int h = 0, n = 0;
            for (Component c : pai.getComponents()) {
                if (!c.isVisible()) continue;
                h += altura(c, largura) + (n++ > 0 ? espaco : 0);
            }
            return new Dimension(300 + in.left + in.right, h + in.top + in.bottom);
        }
    }

    @Override public Dimension minimumLayoutSize(Container pai) { return new Dimension(300, 10); }

    @Override
    public void layoutContainer(Container pai) {
        synchronized (pai.getTreeLock()) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() - in.left - in.right;
            int y = in.top;
            for (Component c : pai.getComponents()) {
                if (!c.isVisible()) continue;
                int h = altura(c, largura);
                c.setBounds(in.left, y, largura, h);
                y += h + espaco;
            }
        }
    }
}
