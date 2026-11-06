package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Cartão de indicador do painel: título pequeno, número grande, uma linha de detalhe e um ícone colorido no canto.
 * <p>
 * O detalhe pode ser um texto neutro ({@link #detalhe}) ou uma variação com seta e cor ({@link #variacao}), como
 * "▲ 12% vs. mês anterior" em verde. As cores seguem o significado: verde = bom, amarelo = atenção, vermelho = problema.
 */
public class KpiCard extends Cartao {
    private final JLabel titulo = new JLabel();
    private final JLabel valor = new JLabel();
    private final JLabel detalhe = new JLabel(" ");
    private final Icones.Tipo icone;
    private Color cor;

    /**
     * @param titulo nome do indicador (ex.: "Recebido no mês")
     * @param icone  ícone do canto
     * @param cor    cor de destaque do ícone e do número
     */
    public KpiCard(String titulo, Icones.Tipo icone, Color cor) {
        super(new BorderLayout(0, 2), 16, 18);
        this.icone = icone;
        this.cor = cor;
        this.titulo.setText(titulo);
        this.titulo.setForeground(Theme.MUTED);
        this.titulo.setFont(Theme.fonte(12.5f, Font.PLAIN));
        valor.setFont(Theme.fonte(26f, Font.BOLD));
        valor.setForeground(Theme.TEXT);
        detalhe.setFont(Theme.fonte(12f, Font.PLAIN));
        detalhe.setForeground(Theme.MUTED);

        JPanel texto = new JPanel(new GridLayout(3, 1, 0, 1));
        texto.setOpaque(false);
        // O título deixa espaço à direita para o ícone, que é desenhado por cima no canto.
        this.titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 34));
        texto.add(this.titulo);
        texto.add(valor);
        texto.add(detalhe);
        add(texto, BorderLayout.CENTER);
        setPreferredSize(new Dimension(160, 112));
    }

    /** Define o número grande (texto já formatado) e a cor dele. */
    public KpiCard valor(String texto, Color cor) {
        valor.setText(texto);
        valor.setForeground(cor == null ? Theme.TEXT : cor);
        return this;
    }

    /** Linha de detalhe neutra, em cinza. */
    public KpiCard detalhe(String texto) {
        detalhe.setText(texto == null || texto.isBlank() ? " " : texto);
        detalhe.setForeground(Theme.MUTED);
        return this;
    }

    /** Linha de detalhe com seta e cor: positivo verde ▲, negativo vermelho ▼, zero cinza. {@code bomSeSobe=false} inverte as cores. */
    public KpiCard variacao(double percentual, String complemento, boolean bomSeSobe) {
        boolean sobe = percentual > 0.0049, desce = percentual < -0.0049;
        String seta = sobe ? "▲ " : desce ? "▼ " : "• ";
        Color c = !sobe && !desce ? Theme.MUTED : (sobe == bomSeSobe) ? Theme.GREEN : Theme.DANGER;
        detalhe.setText(seta + String.format("%.0f%%", Math.abs(percentual)) + " " + complemento);
        detalhe.setForeground(c);
        return this;
    }

    /** Detalhe de variação quando não há base de comparação (mês anterior zerado). */
    public KpiCard semBase(String texto) {
        detalhe.setText(texto);
        detalhe.setForeground(Theme.MUTED);
        return this;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = Theme.suave(g);
        int t = 30, x = getWidth() - t - 14, y = 14;
        g2.setColor(Theme.alfa(cor, 34));
        g2.fill(new RoundRectangle2D.Float(x, y, t, t, 10, 10));
        Icones.desenhar(g2, icone, x + 6, y + 6, t - 12, cor);
        g2.dispose();
    }
}
