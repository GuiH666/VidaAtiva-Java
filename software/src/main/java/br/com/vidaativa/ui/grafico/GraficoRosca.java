package br.com.vidaativa.ui.grafico;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Gráfico de rosca com legenda: uma fatia por item, texto grande no centro (ex.: "86%" e "em dia") e, ao lado, a lista
 * de itens com quantidade e porcentagem. Passar o mouse em uma fatia ou em uma linha da legenda destaca o item.
 */
public class GraficoRosca extends JComponent {
    /** Uma fatia: nome, valor (quantidade ou dinheiro) e cor. */
    public record Fatia(String nome, double valor, Color cor) { }

    private final List<Fatia> fatias = new ArrayList<>();
    private DoubleFunction<String> formato = v -> String.valueOf((long) v);
    private String centro = "", legendaCentro = "";
    private int sobre = -1;
    private String vazio = "Sem dados";

    // Geometria calculada em paintComponent e usada para saber qual fatia está sob o mouse.
    private double cx, cy, raioExterno, raioInterno;
    private int legendaX, legendaY, linhaAltura = 26;

    public GraficoRosca() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, 230));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = fatiaEm(e.getX(), e.getY());
                if (i != sobre) { sobre = i; repaint(); }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { if (sobre != -1) { sobre = -1; repaint(); } }
        });
    }

    /** Define as fatias. Fatias com valor zero continuam na legenda, mas não aparecem no anel. */
    public GraficoRosca dados(Fatia... itens) {
        fatias.clear();
        fatias.addAll(List.of(itens));
        repaint();
        return this;
    }

    /** Como mostrar o valor de cada item na legenda (quantidade, moeda...). */
    public GraficoRosca formato(DoubleFunction<String> f) { this.formato = f; return this; }

    /** Texto grande e legenda pequena no centro do anel. */
    public GraficoRosca centro(String grande, String pequeno) { this.centro = grande; this.legendaCentro = pequeno; repaint(); return this; }

    public GraficoRosca textoVazio(String t) { this.vazio = t; return this; }

    private double total() { return fatias.stream().mapToDouble(Fatia::valor).sum(); }

    private int fatiaEm(int x, int y) {
        double total = total();
        if (total <= 0) return -1;
        // Linhas da legenda.
        if (x >= legendaX && y >= legendaY && y < legendaY + fatias.size() * linhaAltura) return (y - legendaY) / linhaAltura;
        double dx = x - cx, dy = y - cy, d = Math.hypot(dx, dy);
        if (d < raioInterno - 4 || d > raioExterno + 4) return -1;
        double ang = Math.toDegrees(Math.atan2(-dy, dx));       // 0° à direita, anti-horário
        double desdeTopo = ((90 - ang) % 360 + 360) % 360;      // 0° no topo, horário
        double acc = 0;
        for (int i = 0; i < fatias.size(); i++) {
            acc += fatias.get(i).valor() / total * 360;
            if (desdeTopo <= acc) return fatias.get(i).valor() > 0 ? i : -1;
        }
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = fatiaEm(e.getX(), e.getY());
        if (i < 0) return null;
        Fatia f = fatias.get(i);
        return f.nome() + ": " + formato.apply(f.valor()) + " (" + Math.round(f.valor() / total() * 100) + "%)";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Theme.suave(g);
        int w = getWidth(), h = getHeight();
        double total = total();
        // Em cartões estreitos a legenda vai para BAIXO do anel (em colunas largas fica ao lado).
        boolean vertical = w < 400;
        linhaAltura = vertical ? 25 : 28;
        int alturaLegenda = vertical ? fatias.size() * linhaAltura + 6 : 0;
        double diametro = vertical ? Math.max(60, Math.min(w - 40, h - alturaLegenda - 12)) : Math.max(60, Math.min(h - 16, w * 0.46));
        raioExterno = diametro / 2;
        double espessura = Math.max(14, diametro * 0.17);
        raioInterno = raioExterno - espessura;
        cx = vertical ? w / 2.0 : 10 + raioExterno;
        cy = vertical ? 6 + raioExterno : h / 2.0;

        if (total <= 0) {
            g2.setColor(Theme.alfa(Theme.BORDER, 200));
            g2.setStroke(new BasicStroke((float) espessura));
            g2.draw(new Arc2D.Double(cx - raioExterno + espessura / 2, cy - raioExterno + espessura / 2, diametro - espessura, diametro - espessura, 0, 360, Arc2D.OPEN));
            g2.setFont(Theme.fonte(13f, Font.PLAIN));
            g2.setColor(Theme.MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(vazio, (float) (cx - fm.stringWidth(vazio) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 2));
            g2.dispose();
            return;
        }

        // Anel: cada fatia é um arco grosso, com uma pequena folga entre elas.
        double inicio = 90;
        double meio = raioExterno - espessura / 2;
        int nAtivas = (int) fatias.stream().filter(f -> f.valor() > 0).count();
        for (int i = 0; i < fatias.size(); i++) {
            Fatia f = fatias.get(i);
            if (f.valor() <= 0) continue;
            double ext = f.valor() / total * 360;
            double folga = nAtivas > 1 ? Math.min(3, ext / 3) : 0;
            boolean destaque = i == sobre;
            g2.setColor(sobre >= 0 && !destaque ? Theme.alfa(f.cor(), 120) : f.cor());
            g2.setStroke(new BasicStroke((float) (espessura + (destaque ? 4 : 0)), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g2.draw(new Arc2D.Double(cx - meio, cy - meio, meio * 2, meio * 2, inicio - folga / 2, -(ext - folga), Arc2D.OPEN));
            inicio -= ext;
        }

        // Texto do centro.
        if (!centro.isEmpty()) {
            // O texto grande diminui até caber dentro do furo do anel (valores em dinheiro são mais largos que porcentagens).
            float tamanho = (float) Math.max(18, diametro * 0.17);
            g2.setFont(Theme.fonte(tamanho, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            while (tamanho > 12 && fm.stringWidth(centro) > raioInterno * 1.55) {
                tamanho -= 1;
                g2.setFont(Theme.fonte(tamanho, Font.BOLD));
                fm = g2.getFontMetrics();
            }
            g2.setColor(Theme.TEXT);
            float yBase = (float) (cy + (legendaCentro.isEmpty() ? fm.getAscent() / 2.0 - 2 : -2));
            g2.drawString(centro, (float) (cx - fm.stringWidth(centro) / 2.0), yBase);
            if (!legendaCentro.isEmpty()) {
                g2.setFont(Theme.fonte(11.5f, Font.PLAIN));
                FontMetrics f2 = g2.getFontMetrics();
                g2.setColor(Theme.MUTED);
                g2.drawString(legendaCentro, (float) (cx - f2.stringWidth(legendaCentro) / 2.0), (float) (cy + f2.getAscent() + 4));
            }
        }

        // Legenda: bolinha, nome, valor e porcentagem (ao lado do anel, ou embaixo em cartões estreitos).
        if (vertical) {
            legendaX = 10;
            legendaY = (int) (cy + raioExterno + 14);
        } else {
            legendaX = (int) (cx + raioExterno + 22);
            legendaY = (int) (cy - fatias.size() * linhaAltura / 2.0);
        }
        int larguraLeg = w - legendaX - (vertical ? 10 : 6);
        g2.setFont(Theme.fonte(12.5f, Font.PLAIN));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < fatias.size(); i++) {
            Fatia f = fatias.get(i);
            int y = legendaY + i * linhaAltura;
            if (i == sobre) {
                g2.setColor(Theme.alfa(Theme.TEXT, 14));
                g2.fill(new RoundRectangle2D.Float(legendaX - 8, y, larguraLeg + 10, linhaAltura - 2, 8, 8));
            }
            g2.setColor(f.cor());
            g2.fillOval(legendaX, y + linhaAltura / 2 - 6, 9, 9);
            String pct = Math.round(f.valor() / total * 100) + "%";
            String valor = formato.apply(f.valor());
            int xDir = legendaX + larguraLeg;
            g2.setColor(Theme.MUTED);
            g2.drawString(pct, xDir - fm.stringWidth(pct), y + linhaAltura / 2 + fm.getAscent() / 2 - 2);
            g2.setColor(Theme.TEXT);
            int xValor = xDir - fm.stringWidth(pct) - 12 - fm.stringWidth(valor);
            g2.drawString(valor, xValor, y + linhaAltura / 2 + fm.getAscent() / 2 - 2);
            // O nome é cortado com reticências se não couber ao lado do valor.
            String nome = f.nome();
            int maxNome = xValor - (legendaX + 16) - 8;
            while (nome.length() > 3 && fm.stringWidth(nome) > maxNome) nome = nome.substring(0, nome.length() - 2) + "…";
            g2.setColor(Theme.TEXT);
            g2.drawString(nome, legendaX + 16, y + linhaAltura / 2 + fm.getAscent() / 2 - 2);
        }
        g2.dispose();
    }
}
