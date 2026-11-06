package br.com.vidaativa.ui.grafico;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.IntFunction;

/**
 * Gráfico de barras com uma ou mais séries por categoria (barras agrupadas), eixo vertical com linhas de grade
 * "redondas" (0, 5, 10... ou 0, 500, 1.000...) e dica ao passar o mouse.
 * <p>
 * Uso: {@code new GraficoBarras().dados(rotulos, serie1, serie2).formato(UI::moedaCurta).dica(i -> "Dia " + (i + 1))}.
 * Com mais de uma série, a legenda aparece no topo. {@link #destaque(int)} pinta uma categoria (ex.: o dia de hoje)
 * com a cor cheia enquanto as demais ficam mais suaves.
 */
public class GraficoBarras extends JComponent {
    /** Uma série de barras: nome (legenda), cor e um valor por categoria. */
    public record Serie(String nome, Color cor, double[] valores) { }

    private String[] categorias = new String[0];
    private final List<Serie> series = new ArrayList<>();
    private DoubleFunction<String> formato = v -> String.valueOf((long) v);
    private IntFunction<String> dica = i -> null;
    private int destaque = -1;
    private int sobre = -1;
    private String vazio = "Sem registros no período";
    private int passoRotulo = 1;

    public GraficoBarras() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 230));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { atualizarSobre(e.getX(), e.getY()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { if (sobre != -1) { sobre = -1; repaint(); } }
        });
    }

    /** Define as categorias (eixo horizontal) e as séries. Cada série precisa ter um valor por categoria. */
    public GraficoBarras dados(String[] categorias, Serie... series) {
        this.categorias = categorias;
        this.series.clear();
        this.series.addAll(List.of(series));
        repaint();
        return this;
    }

    /** Como formatar os números do eixo vertical (ex.: moeda abreviada). */
    public GraficoBarras formato(DoubleFunction<String> f) { this.formato = f; return this; }

    /** Texto da dica de cada categoria (índice da categoria -> texto, com quebras de linha em HTML). */
    public GraficoBarras dica(IntFunction<String> d) { this.dica = d; return this; }

    /** Categoria destacada (índice) ou -1; as demais ficam levemente apagadas. */
    public GraficoBarras destaque(int indice) { this.destaque = indice; repaint(); return this; }

    /** Texto mostrado quando todos os valores são zero. */
    public GraficoBarras textoVazio(String t) { this.vazio = t; return this; }

    /** Mostra um rótulo do eixo horizontal a cada {@code passo} categorias (evita rótulos amontoados em gráficos diários). */
    public GraficoBarras passoRotulo(int passo) { this.passoRotulo = Math.max(1, passo); repaint(); return this; }

    // ---------------------------------------------------------------- geometria

    private double maximo() {
        double m = 0;
        for (Serie s : series) for (double v : s.valores()) m = Math.max(m, v);
        return m;
    }

    /** Limite superior "redondo" do eixo e o passo entre linhas de grade (4 intervalos). */
    private static double[] escala(double max) {
        if (max <= 0) return new double[]{4, 1};
        double bruto = max / 4.0;
        double pot = Math.pow(10, Math.floor(Math.log10(bruto)));
        double f = bruto / pot;
        double passo = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * pot;
        if (passo < 1 && max >= 1) passo = 1;
        return new double[]{Math.ceil(max / passo) * passo, passo};
    }

    private Rectangle area(FontMetrics fm, double topo) {
        int esquerda = 10 + fm.stringWidth(formato.apply(topo)) + 8;
        int cima = series.size() > 1 ? 30 : 10;
        return new Rectangle(esquerda, cima, Math.max(10, getWidth() - esquerda - 8), Math.max(10, getHeight() - cima - 28));
    }

    private int categoriaEm(int x, int y) {
        if (categorias.length == 0) return -1;
        double[] esc = escala(maximo());
        Rectangle a = area(getFontMetrics(Theme.fonte(11.5f, Font.PLAIN)), esc[0]);
        if (x < a.x || x > a.x + a.width || y < a.y || y > a.y + a.height + 22) return -1;
        return Math.min(categorias.length - 1, (int) ((x - a.x) / (a.width / (double) categorias.length)));
    }

    private void atualizarSobre(int x, int y) {
        int i = categoriaEm(x, y);
        if (i != sobre) { sobre = i; repaint(); }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = categoriaEm(e.getX(), e.getY());
        return i < 0 ? null : dica.apply(i);
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.suave(g);
        Font fonte = Theme.fonte(11.5f, Font.PLAIN);
        g2.setFont(fonte);
        FontMetrics fm = g2.getFontMetrics();
        double[] esc = escala(maximo());
        double topo = esc[0], passo = esc[1];
        Rectangle a = area(fm, topo);

        // Linhas de grade e números do eixo vertical.
        for (double v = 0; v <= topo + passo / 2; v += passo) {
            int y = (int) Math.round(a.y + a.height - (v / topo) * a.height);
            g2.setColor(v == 0 ? Theme.BORDER : Theme.alfa(Theme.BORDER, 110));
            g2.drawLine(a.x, y, a.x + a.width, y);
            String t = formato.apply(v);
            g2.setColor(Theme.MUTED);
            g2.drawString(t, a.x - 8 - fm.stringWidth(t), y + fm.getAscent() / 2f - 1);
        }

        int n = categorias.length;
        if (n > 0 && !series.isEmpty()) {
            double slot = a.width / (double) n;
            int ns = series.size();
            double grupo = Math.min(slot * 0.72, 46.0 * ns);
            double largura = grupo / ns - (ns > 1 ? 2 : 0);
            Shape clipAntes = g2.getClip();
            g2.setClip(new Rectangle(a.x, 0, a.width, a.y + a.height + 1));
            for (int i = 0; i < n; i++) {
                double x0 = a.x + i * slot + (slot - grupo) / 2;
                if (i == sobre) {
                    g2.setColor(Theme.alfa(Theme.TEXT, 14));
                    g2.fill(new RoundRectangle2D.Double(a.x + i * slot + 1, a.y, slot - 2, a.height, 8, 8));
                }
                for (int s = 0; s < ns; s++) {
                    double v = series.get(s).valores()[i];
                    if (v <= 0) continue;
                    double h = Math.max(2, v / topo * a.height);
                    Color cor = series.get(s).cor();
                    boolean apagar = destaque >= 0 && i != destaque;
                    g2.setColor(apagar ? Theme.alfa(cor, 120) : cor);
                    double bx = x0 + s * (largura + (ns > 1 ? 2 : 0));
                    // A barra tem o topo arredondado: o retângulo arredondado é desenhado mais alto e o excesso embaixo fica fora do clip.
                    g2.fill(new RoundRectangle2D.Double(bx, a.y + a.height - h, largura, h + 8, 7, 7));
                }
            }
            g2.setClip(clipAntes);

            // Rótulos do eixo horizontal (não desenha todos se estiverem amontoados).
            g2.setColor(Theme.MUTED);
            int minPasso = 1;
            for (String c : categorias) minPasso = Math.max(minPasso, (int) Math.ceil((fm.stringWidth(c) + 6) / slot));
            int passoReal = Math.max(passoRotulo, minPasso);
            for (int i = 0; i < n; i++) {
                if (i % passoReal != 0 && i != destaque) continue;
                String c = categorias[i];
                g2.setColor(i == destaque ? Theme.GREEN : Theme.MUTED);
                g2.drawString(c, (float) (a.x + i * slot + (slot - fm.stringWidth(c)) / 2), a.y + a.height + 18);
            }
            // Legenda (só com mais de uma série).
            if (ns > 1) {
                int lx = a.x;
                for (Serie s : series) {
                    g2.setColor(s.cor());
                    g2.fill(new RoundRectangle2D.Float(lx, 8, 10, 10, 4, 4));
                    g2.setColor(Theme.MUTED);
                    g2.drawString(s.nome(), lx + 16, 8 + fm.getAscent() - 1);
                    lx += 16 + fm.stringWidth(s.nome()) + 18;
                }
            }
        }
        if (maximo() <= 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.fonte(13f, Font.PLAIN));
            FontMetrics f2 = g2.getFontMetrics();
            g2.drawString(vazio, a.x + (a.width - f2.stringWidth(vazio)) / 2, a.y + a.height / 2);
        }
        g2.dispose();
    }
}
