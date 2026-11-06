package br.com.vidaativa.ui.panels;

import br.com.vidaativa.service.BibliotecaExercicios;
import br.com.vidaativa.service.BibliotecaExercicios.Modelo;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.Cartao;
import br.com.vidaativa.ui.componentes.CampoBusca;
import br.com.vidaativa.ui.componentes.Opcoes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Painel lateral do editor de fichas: catálogo de exercícios para o professor adicionar ao treino com um clique.
 * Tem busca por nome, filtro por grupo muscular (Peito, Costas...) e a lista de exercícios; clicar em um exercício
 * chama o ouvinte informado, que o coloca no treino aberto.
 */
class BibliotecaPanel extends Cartao {
    private final DefaultListModel<Modelo> modelo = new DefaultListModel<>();
    private final JList<Modelo> lista = new JList<>(modelo);
    private final CampoBusca busca = new CampoBusca("Buscar exercício", 14);
    private final Opcoes<String> grupos;
    private final JLabel contagem = new JLabel(" ");
    private int sobre = -1;

    /** @param aoEscolher chamado com o exercício clicado */
    BibliotecaPanel(Consumer<Modelo> aoEscolher) {
        super(new BorderLayout(0, 12), 16, 16);
        JLabel titulo = UI.titulo("Biblioteca de exercícios");
        JLabel dica = UI.rotulo("Clique para adicionar ao treino aberto");
        JPanel cab = new JPanel(new GridLayout(2, 1, 0, 2));
        cab.setOpaque(false);
        cab.add(titulo);
        cab.add(dica);

        List<String> opcoes = new ArrayList<>();
        opcoes.add("Todos");
        opcoes.addAll(BibliotecaExercicios.GRUPOS);
        grupos = Opcoes.texto(opcoes, false);
        grupos.setSelecionado("Todos");
        grupos.aoMudar(g -> filtrar());
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });

        JPanel filtros = new JPanel(new BorderLayout(0, 8));
        filtros.setOpaque(false);
        filtros.add(busca, BorderLayout.NORTH);
        filtros.add(grupos, BorderLayout.CENTER);
        JPanel topo = new JPanel(new BorderLayout(0, 12));
        topo.setOpaque(false);
        topo.add(cab, BorderLayout.NORTH);
        topo.add(filtros, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        lista.setOpaque(false);
        lista.setBackground(new Color(0, 0, 0, 0));
        lista.setFixedCellHeight(50);
        lista.setCellRenderer(new Linha());
        lista.setSelectionModel(new DefaultListSelectionModel() {
            // Clicar não "seleciona": a lista funciona como um conjunto de botões.
            @Override public void setSelectionInterval(int a, int b) { }
            @Override public void addSelectionInterval(int a, int b) { }
        });
        lista.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = indiceEm(e.getPoint());
                if (i != sobre) { sobre = i; lista.repaint(); lista.setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); }
            }
        });
        lista.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { sobre = -1; lista.repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                int i = indiceEm(e.getPoint());
                if (i >= 0) aoEscolher.accept(modelo.get(i));
            }
        });
        JScrollPane rolagem = new JScrollPane(lista, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setBorder(BorderFactory.createEmptyBorder());
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        add(rolagem, BorderLayout.CENTER);

        contagem.setForeground(Theme.MUTED);
        contagem.setFont(Theme.fonte(12f, Font.PLAIN));
        add(contagem, BorderLayout.SOUTH);
        filtrar();
    }

    private int indiceEm(Point p) {
        int i = lista.locationToIndex(p);
        return i >= 0 && lista.getCellBounds(i, i) != null && lista.getCellBounds(i, i).contains(p) ? i : -1;
    }

    private void filtrar() {
        String g = grupos.getSelecionado();
        modelo.clear();
        for (Modelo e : BibliotecaExercicios.filtrar("Todos".equals(g) ? null : g, busca.getText())) modelo.addElement(e);
        contagem.setText(modelo.size() + (modelo.size() == 1 ? " exercício" : " exercícios"));
        sobre = -1;
    }

    /** Linha da lista: nome, resumo em cinza e um "+" verde à direita que acende sob o mouse. */
    private final class Linha extends JComponent implements ListCellRenderer<Modelo> {
        private Modelo atual;
        private boolean destaque;

        @Override
        public Component getListCellRendererComponent(JList<? extends Modelo> l, Modelo v, int i, boolean sel, boolean foco) {
            atual = v;
            destaque = i == sobre;
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.suave(g);
            int w = getWidth(), h = getHeight();
            if (destaque) {
                g2.setColor(Theme.HOVER);
                g2.fill(new RoundRectangle2D.Float(0, 2, w, h - 4, 12, 12));
            }
            g2.setFont(Theme.fonte(13.5f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            String nome = atual.nome();
            while (nome.length() > 4 && fm.stringWidth(nome) > w - 62) nome = nome.substring(0, nome.length() - 2) + "…";
            g2.drawString(nome, 12, h / 2 - 3);
            g2.setFont(Theme.fonte(12f, Font.PLAIN));
            g2.setColor(Theme.MUTED);
            g2.drawString(atual.resumo(), 12, h / 2 + 15);
            // Botão "+" circular.
            int t = 26, x = w - t - 10, y = (h - t) / 2;
            g2.setColor(destaque ? Theme.GREEN : Theme.alfa(Theme.GREEN, 40));
            g2.fillOval(x, y, t, t);
            Icones.desenhar(g2, Icones.Tipo.MAIS, x + 5, y + 5, t - 10, destaque ? new Color(6, 26, 14) : Theme.GREEN);
            g2.dispose();
        }
    }
}
