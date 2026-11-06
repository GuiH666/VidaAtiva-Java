package br.com.vidaativa.ui.componentes;

import java.awt.*;

/**
 * Layout em colunas iguais que se adapta à largura: usa quantas colunas couberem (respeitando uma largura mínima por
 * coluna e um máximo de colunas) e passa os itens que sobram para a linha de baixo.
 * <p>
 * É o que faz o painel do gerente ficar bom tanto em tela grande (seis indicadores numa linha) quanto em tela
 * pequena (três por linha). Todos os itens de uma mesma linha ficam com a mesma altura. Deve ficar dentro de um
 * contêiner que acompanha a largura da janela (veja {@link ConteudoRolavel}).
 */
public class GradeFluida implements LayoutManager {
    private final int larguraMinima, maxColunas, espaco;

    /**
     * @param larguraMinima menor largura aceitável para uma coluna
     * @param maxColunas    máximo de colunas por linha
     * @param espaco        espaço entre as colunas e entre as linhas
     */
    public GradeFluida(int larguraMinima, int maxColunas, int espaco) {
        this.larguraMinima = larguraMinima;
        this.maxColunas = maxColunas;
        this.espaco = espaco;
    }

    private int colunas(int largura, int itens) {
        int n = Math.max(1, (largura + espaco) / (larguraMinima + espaco));
        n = Math.min(n, Math.min(maxColunas, Math.max(1, itens)));
        // Evita deixar um item sozinho na última linha quando dá para distribuir melhor (ex.: 6 itens em 4 colunas -> 3 + 3).
        if (itens > n) {
            int linhas = (itens + n - 1) / n;
            n = (itens + linhas - 1) / linhas;
        }
        return Math.max(1, n);
    }

    private int visiveis(Container pai) {
        int n = 0;
        for (Component c : pai.getComponents()) if (c.isVisible()) n++;
        return n;
    }

    @Override public void addLayoutComponent(String nome, Component c) { }
    @Override public void removeLayoutComponent(Component c) { }

    @Override
    public Dimension preferredLayoutSize(Container pai) {
        synchronized (pai.getTreeLock()) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() > 0 ? pai.getWidth() - in.left - in.right : larguraMinima * 3;
            int itens = visiveis(pai);
            int n = colunas(largura, itens);
            int colLargura = (largura - (n - 1) * espaco) / n;
            int altura = 0, linhaAltura = 0, i = 0;
            for (Component c : pai.getComponents()) {
                if (!c.isVisible()) continue;
                linhaAltura = Math.max(linhaAltura, alturaPara(c, colLargura));
                if (++i % n == 0) { altura += linhaAltura + espaco; linhaAltura = 0; }
            }
            if (linhaAltura > 0) altura += linhaAltura + espaco;
            if (altura > 0) altura -= espaco;
            return new Dimension(larguraMinima + in.left + in.right, altura + in.top + in.bottom);
        }
    }

    @Override public Dimension minimumLayoutSize(Container pai) { return new Dimension(larguraMinima, 10); }

    @Override
    public void layoutContainer(Container pai) {
        synchronized (pai.getTreeLock()) {
            Insets in = pai.getInsets();
            int largura = pai.getWidth() - in.left - in.right;
            int n = colunas(largura, visiveis(pai));
            int colLargura = (largura - (n - 1) * espaco) / n;
            int y = in.top, i = 0;
            Component[] linha = new Component[n];
            int alturaLinha = 0;
            for (Component c : pai.getComponents()) {
                if (!c.isVisible()) continue;
                linha[i % n] = c;
                alturaLinha = Math.max(alturaLinha, alturaPara(c, colLargura));
                i++;
                if (i % n == 0) { posicionar(linha, in.left, y, colLargura, alturaLinha); y += alturaLinha + espaco; alturaLinha = 0; linha = new Component[n]; }
            }
            if (i % n != 0) posicionar(linha, in.left, y, colLargura, alturaLinha);
        }
    }

    private void posicionar(Component[] linha, int x0, int y, int colLargura, int altura) {
        for (int k = 0; k < linha.length; k++) {
            if (linha[k] != null) linha[k].setBounds(x0 + k * (colLargura + espaco), y, colLargura, altura);
        }
    }

    /** Altura preferida do item quando ele tiver a largura da coluna (componentes com texto quebrado dependem da largura). */
    private static int alturaPara(Component c, int largura) {
        if (c.getWidth() != largura) c.setSize(largura, Math.max(1, c.getHeight()));
        return c.getPreferredSize().height;
    }
}
