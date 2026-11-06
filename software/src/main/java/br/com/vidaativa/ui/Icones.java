package br.com.vidaativa.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Ícones do software, desenhados com formas (Java2D) em vez de imagens ou emojis: assim ficam nítidos em qualquer tela
 * e funcionam em qualquer computador, sem depender de fonte instalada.
 * <p>
 * Cada ícone é desenhado numa grade de 24 x 24 e escalado para o tamanho pedido. Para usar em um componente:
 * {@code new JLabel(Icones.icone(Icones.Tipo.ALUNOS, 20, Theme.MUTED))}, ou chame {@link #desenhar} dentro de um
 * {@code paintComponent}.
 */
public final class Icones {
    /** Ícones disponíveis. */
    public enum Tipo {
        PAINEL, ALUNOS, PAGAMENTOS, EQUIPE, FICHAS, AVALIACOES, SAIR, MAIS, BUSCA, SETA_ESQ, SETA_DIR, LIXO, SOBE, DESCE,
        FECHAR, COPIAR, LIVRO, VISTO, ALERTA, CALENDARIO, DINHEIRO, USUARIO_MAIS, HALTER, RELOGIO, ATUALIZAR, GRAFICO
    }

    private Icones() { }

    /** Ícone pronto para colocar em {@code JLabel} ou {@code JButton}. */
    public static Icon icone(Tipo tipo, int tamanho, Color cor) {
        return new Icon() {
            @Override public int getIconWidth() { return tamanho; }
            @Override public int getIconHeight() { return tamanho; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.suave(g);
                desenhar(g2, tipo, x, y, tamanho, cor);
                g2.dispose();
            }
        };
    }

    /**
     * Desenha o ícone no retângulo (x, y, tamanho, tamanho).
     *
     * @param g2 contexto gráfico já com suavização ({@link Theme#suave})
     */
    public static void desenhar(Graphics2D g2, Tipo tipo, double x, double y, double tamanho, Color cor) {
        Graphics2D g = (Graphics2D) g2.create();
        g.translate(x, y);
        double k = tamanho / 24.0;
        g.scale(k, k);
        g.setColor(cor);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (tipo) {
            case PAINEL -> {
                g.draw(new RoundRectangle2D.Double(3.5, 3.5, 7, 8, 2, 2));
                g.draw(new RoundRectangle2D.Double(13.5, 3.5, 7, 5, 2, 2));
                g.draw(new RoundRectangle2D.Double(13.5, 11.5, 7, 9, 2, 2));
                g.draw(new RoundRectangle2D.Double(3.5, 14.5, 7, 6, 2, 2));
            }
            case ALUNOS -> {
                g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                g.draw(new Arc2D.Double(4.5, 14, 15, 13, 0, 180, Arc2D.OPEN));
            }
            case EQUIPE -> {
                g.draw(new Ellipse2D.Double(3.5, 5, 7, 7));
                g.draw(new Arc2D.Double(1.5, 14, 11, 10, 0, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(13.5, 5, 7, 7));
                g.draw(new Arc2D.Double(11.5, 14, 11, 10, 0, 180, Arc2D.OPEN));
            }
            case PAGAMENTOS -> {
                g.draw(new RoundRectangle2D.Double(2.5, 5.5, 19, 13, 3, 3));
                g.draw(new Line2D.Double(2.5, 10.5, 21.5, 10.5));
                g.draw(new Line2D.Double(6.5, 15, 10, 15));
            }
            case FICHAS -> {
                g.draw(new RoundRectangle2D.Double(5, 4, 14, 17, 3, 3));
                g.draw(new RoundRectangle2D.Double(9, 2, 6, 4, 1.5, 1.5));
                g.draw(new Line2D.Double(8.5, 11, 15.5, 11));
                g.draw(new Line2D.Double(8.5, 15, 13.5, 15));
            }
            case AVALIACOES -> {
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 12.5); p.lineTo(7, 12.5); p.lineTo(9.5, 6); p.lineTo(13.5, 19); p.lineTo(16, 12.5); p.lineTo(21.5, 12.5);
                g.draw(p);
            }
            case SAIR -> {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 4); p.lineTo(5.5, 4); p.quadTo(4, 4, 4, 5.5); p.lineTo(4, 18.5); p.quadTo(4, 20, 5.5, 20); p.lineTo(10, 20);
                g.draw(p);
                g.draw(new Line2D.Double(10, 12, 20, 12));
                g.draw(new Line2D.Double(16.5, 8.5, 20, 12));
                g.draw(new Line2D.Double(16.5, 15.5, 20, 12));
            }
            case MAIS -> { g.draw(new Line2D.Double(12, 5, 12, 19)); g.draw(new Line2D.Double(5, 12, 19, 12)); }
            case BUSCA -> { g.draw(new Ellipse2D.Double(4, 4, 11.5, 11.5)); g.draw(new Line2D.Double(14, 14, 20, 20)); }
            case SETA_ESQ -> { Path2D p = new Path2D.Double(); p.moveTo(14.5, 5.5); p.lineTo(8, 12); p.lineTo(14.5, 18.5); g.draw(p); }
            case SETA_DIR -> { Path2D p = new Path2D.Double(); p.moveTo(9.5, 5.5); p.lineTo(16, 12); p.lineTo(9.5, 18.5); g.draw(p); }
            case SOBE -> { Path2D p = new Path2D.Double(); p.moveTo(5.5, 14.5); p.lineTo(12, 8); p.lineTo(18.5, 14.5); g.draw(p); }
            case DESCE -> { Path2D p = new Path2D.Double(); p.moveTo(5.5, 9.5); p.lineTo(12, 16); p.lineTo(18.5, 9.5); g.draw(p); }
            case FECHAR -> { g.draw(new Line2D.Double(6, 6, 18, 18)); g.draw(new Line2D.Double(18, 6, 6, 18)); }
            case LIXO -> {
                g.draw(new Line2D.Double(4, 7, 20, 7));
                Path2D p = new Path2D.Double();
                p.moveTo(6.5, 7); p.lineTo(7.5, 19.5); p.quadTo(7.6, 20.5, 8.6, 20.5); p.lineTo(15.4, 20.5); p.quadTo(16.4, 20.5, 16.5, 19.5); p.lineTo(17.5, 7);
                g.draw(p);
                g.draw(new Line2D.Double(9.5, 7, 9.5, 4.5)); g.draw(new Line2D.Double(9.5, 4.5, 14.5, 4.5)); g.draw(new Line2D.Double(14.5, 4.5, 14.5, 7));
            }
            case COPIAR -> {
                g.draw(new RoundRectangle2D.Double(8.5, 8.5, 12, 12, 3, 3));
                Path2D p = new Path2D.Double();
                p.moveTo(15.5, 8.5); p.lineTo(15.5, 6); p.quadTo(15.5, 3.5, 13, 3.5); p.lineTo(6, 3.5); p.quadTo(3.5, 3.5, 3.5, 6); p.lineTo(3.5, 13); p.quadTo(3.5, 15.5, 6, 15.5); p.lineTo(8.5, 15.5);
                g.draw(p);
            }
            case LIVRO -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 6.5); p.quadTo(8, 3.5, 3.5, 4.5); p.lineTo(3.5, 18.5); p.quadTo(8, 17.5, 12, 20.5);
                p.quadTo(16, 17.5, 20.5, 18.5); p.lineTo(20.5, 4.5); p.quadTo(16, 3.5, 12, 6.5);
                g.draw(p);
                g.draw(new Line2D.Double(12, 6.5, 12, 20.5));
            }
            case VISTO -> { Path2D p = new Path2D.Double(); p.moveTo(5, 12.5); p.lineTo(10, 17.5); p.lineTo(19, 7); g.draw(p); }
            case ALERTA -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5); p.lineTo(21.5, 20); p.lineTo(2.5, 20); p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(12, 9.5, 12, 14.5));
                g.fill(new Ellipse2D.Double(11, 16.2, 2, 2));
            }
            case CALENDARIO -> {
                g.draw(new RoundRectangle2D.Double(3.5, 5, 17, 15.5, 3, 3));
                g.draw(new Line2D.Double(3.5, 10, 20.5, 10));
                g.draw(new Line2D.Double(8, 3, 8, 6.5)); g.draw(new Line2D.Double(16, 3, 16, 6.5));
            }
            case DINHEIRO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                Path2D p = new Path2D.Double();
                p.moveTo(14.5, 9); p.quadTo(14, 7.5, 12, 7.5); p.quadTo(9.5, 7.5, 9.5, 9.5); p.quadTo(9.5, 11.3, 12, 12);
                p.quadTo(14.5, 12.7, 14.5, 14.5); p.quadTo(14.5, 16.5, 12, 16.5); p.quadTo(10, 16.5, 9.5, 15);
                g.draw(p);
                g.draw(new Line2D.Double(12, 5.5, 12, 7.5)); g.draw(new Line2D.Double(12, 16.5, 12, 18.5));
            }
            case USUARIO_MAIS -> {
                g.draw(new Ellipse2D.Double(3.5, 5, 9, 9));
                g.draw(new Arc2D.Double(1.5, 16, 13, 11, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(18, 7, 18, 13)); g.draw(new Line2D.Double(15, 10, 21, 10));
            }
            case HALTER -> {
                g.draw(new Line2D.Double(7, 12, 17, 12));
                g.draw(new RoundRectangle2D.Double(3.5, 8, 3.5, 8, 1.5, 1.5));
                g.draw(new RoundRectangle2D.Double(17, 8, 3.5, 8, 1.5, 1.5));
                g.draw(new Line2D.Double(2, 10.5, 2, 13.5)); g.draw(new Line2D.Double(22, 10.5, 22, 13.5));
            }
            case RELOGIO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                Path2D p = new Path2D.Double(); p.moveTo(12, 7); p.lineTo(12, 12); p.lineTo(15.5, 14); g.draw(p);
            }
            case ATUALIZAR -> {
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 40, 270, Arc2D.OPEN));
                Path2D p = new Path2D.Double(); p.moveTo(18, 2.5); p.lineTo(18.5, 7.5); p.lineTo(13.5, 7.5); g.draw(p);
            }
            case GRAFICO -> {
                g.draw(new Line2D.Double(4, 20, 20, 20));
                g.draw(new RoundRectangle2D.Double(5.5, 12, 3, 8, 1, 1));
                g.draw(new RoundRectangle2D.Double(10.5, 6, 3, 14, 1, 1));
                g.draw(new RoundRectangle2D.Double(15.5, 9.5, 3, 10.5, 1, 1));
            }
        }
        g.dispose();
    }
}
