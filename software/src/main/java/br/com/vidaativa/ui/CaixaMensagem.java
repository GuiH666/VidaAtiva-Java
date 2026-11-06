package br.com.vidaativa.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;

/**
 * Caixa de mensagem do software (confirmações, avisos, erros e informações), no mesmo visual das outras telas.
 * <p>
 * Substitui o {@code JOptionPane}, que no tema escuro aparecia como uma janela preta enorme com texto e botões invisíveis.
 * Aqui o tamanho é calculado pelo conteúdo (a janela se ajusta ao texto) e tudo é desenhado com as cores do {@link Theme}:
 * um ícone redondo colorido à esquerda (? confirmar, ! atenção, i informação, × erro), título, mensagem e botões embaixo.
 * <p>
 * Quem usa: {@link UI#confirmar}, {@link UI#info} e {@link UI#erro} (as telas não chamam esta classe diretamente).
 * Atalhos: Enter aciona o botão principal e Esc fecha (em confirmação, fechar equivale a "Não").
 */
final class CaixaMensagem {
    /** Tipo da mensagem: define a cor, o símbolo do ícone e o título padrão. */
    enum Tipo {
        CONFIRMAR("?", Theme.GREEN, "Confirmar"),
        ATENCAO("!", Theme.WARN, "Atenção"),
        ERRO("×", Theme.DANGER, "Algo deu errado"),
        INFO("i", Theme.INFO, "Informação");

        final String simbolo, titulo;
        final Color cor;
        Tipo(String simbolo, Color cor, String titulo) { this.simbolo = simbolo; this.cor = cor; this.titulo = titulo; }
    }

    private static final int LARGURA_TEXTO = 380;

    private CaixaMensagem() { }

    /** Mostra a caixa só com o botão "OK" e espera ela fechar. */
    static void mostrar(Component pai, Tipo tipo, String mensagem) {
        abrir(pai, tipo, mensagem, "OK", null);
    }

    /** Mostra a pergunta com "Cancelar" e "Sim"; devolve true só se o usuário clicar em Sim. */
    static boolean perguntar(Component pai, String mensagem) {
        return abrir(pai, Tipo.CONFIRMAR, mensagem, "Sim, confirmar", "Cancelar");
    }

    /**
     * Monta e exibe a caixa (modal: só retorna quando ela fecha).
     *
     * @param textoOk       texto do botão principal
     * @param textoCancelar texto do botão secundário; null = não mostra (caixa só informativa)
     * @return true se o botão principal foi clicado
     */
    private static boolean abrir(Component pai, Tipo tipo, String mensagem, String textoOk, String textoCancelar) {
        Window dono = pai == null ? null : SwingUtilities.getWindowAncestor(pai);
        JDialog d = new JDialog(dono, tipo.titulo, Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] ok = {false};

        JButton principal = tipo == Tipo.ERRO && textoCancelar == null ? Theme.button(textoOk) : Theme.primary(textoOk);
        principal.addActionListener(e -> { ok[0] = true; d.dispose(); });
        JButton secundario = null;
        if (textoCancelar != null) {
            secundario = Theme.button(textoCancelar);
            secundario.addActionListener(e -> d.dispose());
        }

        d.setContentPane(painel(tipo, mensagem, principal, secundario));
        d.getRootPane().setDefaultButton(principal);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        IconeApp.aplicar(d);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(dono);
        d.setVisible(true);
        return ok[0];
    }

    /**
     * Conteúdo da caixa (separado para poder ser fotografado nas telas de demonstração).
     * Os botões chegam prontos; {@code secundario} pode ser null.
     */
    static JComponent painel(Tipo tipo, String mensagem, JButton principal, JButton secundario) {
        JPanel raiz = new JPanel(new BorderLayout(0, 20));
        raiz.setBackground(Theme.PANEL);
        raiz.setBorder(BorderFactory.createEmptyBorder(22, 24, 16, 24));

        JLabel icone = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(Theme.alfa(tipo.cor, 40));
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.setColor(Theme.alfa(tipo.cor, 150));
                g2.draw(new Ellipse2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f));
                g2.setColor(tipo.cor);
                g2.setFont(Theme.fonte(24f, Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(tipo.simbolo, (getWidth() - fm.stringWidth(tipo.simbolo)) / 2f,
                        (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }
        };
        icone.setPreferredSize(new Dimension(48, 48));
        JPanel colunaIcone = new JPanel(new BorderLayout());
        colunaIcone.setOpaque(false);
        colunaIcone.add(icone, BorderLayout.NORTH);

        JLabel titulo = new JLabel(tipo.titulo);
        titulo.setFont(Theme.fonte(17f, Font.BOLD));
        titulo.setForeground(Theme.TEXT);
        JLabel texto = new JLabel(html(mensagem));
        texto.setFont(Theme.fonte(14f, Font.PLAIN));
        texto.setForeground(Theme.MUTED);
        texto.setVerticalAlignment(SwingConstants.TOP);

        JPanel corpo = new JPanel(new BorderLayout(0, 8));
        corpo.setOpaque(false);
        corpo.add(titulo, BorderLayout.NORTH);
        corpo.add(texto, BorderLayout.CENTER);

        JPanel topo = new JPanel(new BorderLayout(16, 0));
        topo.setOpaque(false);
        topo.add(colunaIcone, BorderLayout.WEST);
        topo.add(corpo, BorderLayout.CENTER);

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botoes.setOpaque(false);
        if (secundario != null) botoes.add(secundario);
        botoes.add(principal);

        raiz.add(topo, BorderLayout.CENTER);
        raiz.add(botoes, BorderLayout.SOUTH);
        return raiz;
    }

    /**
     * Texto em HTML com largura fixa: é isso que faz o JLabel quebrar a linha e calcular a altura certa.
     * Caracteres especiais são escapados (nomes de alunos podem ter "&" ou "<") e a quebra de linha vira {@code <br>}.
     */
    private static String html(String mensagem) {
        String limpo = (mensagem == null ? "" : mensagem).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r\n", "\n").replace("\n", "<br>");
        return "<html><body style='width:" + LARGURA_TEXTO + "px'>" + limpo + "</body></html>";
    }
}
