package br.com.vidaativa.ui.componentes;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Seletor em "pílulas": uma fileira de opções clicáveis em que uma fica marcada (verde). Substitui combos e spinners
 * quando as opções são poucas e é melhor vê-las todas de uma vez (situação da ficha, dias por semana, dia do treino).
 * <p>
 * As pílulas quebram de linha quando faltam largura. Com {@code permitirNenhuma}, clicar na opção marcada a desmarca
 * (útil para "dia da semana", que pode ficar em branco).
 *
 * @param <T> tipo do valor de cada opção (texto, número, enum...)
 */
public class Opcoes<T> extends JPanel {
    private final List<T> valores = new ArrayList<>();
    private final List<Pilula> pilulas = new ArrayList<>();
    private final boolean permitirNenhuma;
    private final Function<T, String> rotulo;
    private int selecionada = -1;
    private Consumer<T> aoMudar = v -> { };
    private boolean habilitado = true;

    public Opcoes(List<T> valores, Function<T, String> rotulo, boolean permitirNenhuma) {
        super(new FluxoLayout(FlowLayout.LEFT, 6, 6));
        setOpaque(false);
        this.rotulo = rotulo;
        this.permitirNenhuma = permitirNenhuma;
        for (T v : valores) adicionar(v);
    }

    /** Opções de texto: o rótulo é o próprio texto. */
    public static Opcoes<String> texto(List<String> opcoes, boolean permitirNenhuma) {
        return new Opcoes<>(opcoes, s -> s, permitirNenhuma);
    }

    /** Acrescenta uma opção ao final (usado quando o valor gravado não está na lista padrão). */
    public final void adicionar(T valor) {
        valores.add(valor);
        Pilula p = new Pilula(rotulo.apply(valor), valores.size() - 1);
        pilulas.add(p);
        add(p);
    }

    /** Valor marcado, ou {@code null} se nenhum. */
    public T getSelecionado() { return selecionada < 0 ? null : valores.get(selecionada); }

    /** Marca a opção igual a {@code valor} (null desmarca). Não dispara o ouvinte. */
    public void setSelecionado(T valor) {
        selecionada = valor == null ? -1 : valores.indexOf(valor);
        repaint();
    }

    public boolean contem(T valor) { return valores.contains(valor); }

    /** Ouvinte chamado quando o USUÁRIO muda a escolha (recebe o novo valor, ou null se desmarcou). */
    public void aoMudar(Consumer<T> ouvinte) { this.aoMudar = ouvinte; }

    /** Liga/desliga o seletor (modo somente consulta). */
    public void habilitar(boolean h) {
        habilitado = h;
        for (Pilula p : pilulas) p.setCursor(Cursor.getPredefinedCursor(h ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private void escolher(int i) {
        if (!habilitado) return;
        if (selecionada == i) { if (!permitirNenhuma) return; selecionada = -1; }
        else selecionada = i;
        repaint();
        aoMudar.accept(getSelecionado());
    }

    private final class Pilula extends JComponent {
        private final String texto;
        private final int indice;
        private boolean sobre;

        Pilula(String texto, int indice) {
            this.texto = texto;
            this.indice = indice;
            setFont(Theme.fonte(13f, Font.PLAIN));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { escolher(Pilula.this.indice); }
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { sobre = false; repaint(); }
            });
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(texto) + 26, 32);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.suave(g);
            boolean marcada = indice == selecionada;
            RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, getHeight(), getHeight());
            g2.setColor(marcada ? Theme.alfa(Theme.GREEN, habilitado ? 46 : 28) : sobre && habilitado ? Theme.HOVER : Theme.SURFACE);
            g2.fill(forma);
            g2.setColor(marcada ? Theme.alfa(Theme.GREEN, habilitado ? 200 : 100) : Theme.BORDER);
            g2.draw(forma);
            g2.setFont(marcada ? getFont().deriveFont(Font.BOLD) : getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(marcada ? Theme.GREEN : habilitado ? Theme.TEXT : Theme.MUTED);
            g2.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2f, (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }
}
