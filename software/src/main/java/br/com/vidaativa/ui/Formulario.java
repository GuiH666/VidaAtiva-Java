package br.com.vidaativa.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Painel de formulário: cada campo tem o rótulo pequeno em cima e o campo embaixo; dá para pôr um ou dois campos por linha.
 * <p>
 * Quem usa: o login e todos os diálogos de cadastro (aluno, funcionário, professor, avaliação, pagamento).
 * Normalmente o formulário é entregue ao {@link DialogoForm#abrir}, que o coloca dentro de uma janela com Cancelar/Salvar.
 * <p>
 * Como usar: crie o {@code Formulario} e chame, na ordem em que as linhas devem aparecer, {@link #adicionar},
 * {@link #adicionarPar}, {@link #adicionarGrande}, {@link #secao} e {@link #ajuda}. Cada chamada ocupa a próxima linha.
 * <p>
 * Internamente usa {@code GridBagLayout} com 2 colunas iguais; o rótulo de cada campo fica numa linha acima dele.
 */
public class Formulario extends JPanel {
    private int linha = 0;
    private boolean temLinhaGrande = false;

    /** Cria o formulário vazio, com fundo transparente (aparece o fundo da janela). */
    public Formulario() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    /**
     * Uma linha: rótulo em cima e o campo ocupando a largura toda.
     *
     * @param rotulo texto do rótulo (por convenção do projeto, campos obrigatórios terminam com " *")
     */
    public void adicionar(String rotulo, JComponent campo) {
        colocar(rotulo, campo, 0, 2, false);
        linha += 2;
    }

    /** Uma linha com dois campos lado a lado, cada um com seu rótulo em cima. */
    public void adicionarPar(String rotulo1, JComponent campo1, String rotulo2, JComponent campo2) {
        colocar(rotulo1, campo1, 0, 1, false);
        colocar(rotulo2, campo2, 1, 1, false);
        linha += 2;
    }

    /**
     * Linha que cresce na vertical (para caixas de texto grandes). Em geral use uma só por formulário,
     * porque as linhas grandes dividem entre si o espaço extra da altura.
     */
    public void adicionarGrande(String rotulo, JComponent campo) {
        temLinhaGrande = true;
        colocar(rotulo, campo, 0, 2, true);
        linha += 2;
    }

    /** Põe o rótulo (linha {@code linha}) e o campo (linha {@code linha + 1}) na coluna {@code col}. */
    private void colocar(String rotulo, JComponent campo, int col, int largura, boolean cresce) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = col; g.gridy = linha; g.gridwidth = largura; g.weightx = largura;
        g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        // Quando há dois campos na linha, há folga entre eles.
        int folgaDir = largura == 1 && col == 0 ? 14 : 0;
        g.insets = new Insets(linha == 0 ? 0 : 10, 0, 5, folgaDir);
        JLabel l = UI.rotulo("<html>" + rotulo.replace(" *", " <font color='#22C55E'>*</font>") + "</html>");
        add(l, g);
        GridBagConstraints c = (GridBagConstraints) g.clone();
        c.gridy = linha + 1;
        c.insets = new Insets(0, 0, 0, folgaDir);
        if (cresce) { c.weighty = 1; c.fill = GridBagConstraints.BOTH; }
        add(campo, c);
    }

    /** True se alguma linha cresce na vertical (então o formulário deve ocupar toda a altura disponível). */
    public boolean temLinhaGrande() { return temLinhaGrande; }

    /** Embrulha o formulário para ele começar no topo em vez de ficar centralizado na vertical. */
    public JPanel noTopo() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(this, BorderLayout.NORTH);
        return p;
    }

    /** Título de seção dentro do formulário: texto em negrito com uma linha embaixo. Ocupa as 2 colunas. */
    public void secao(String titulo) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = linha; g.gridwidth = 2; g.weightx = 1;
        g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(linha == 0 ? 0 : 20, 0, 2, 0);
        JLabel l = new JLabel(titulo.toUpperCase());
        l.setForeground(Theme.GREEN);
        l.setFont(Theme.fonte(11.5f, Font.BOLD));
        l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        l.setPreferredSize(new Dimension(10, 26));
        add(l, g);
        linha++;
    }

    /**
     * Texto de ajuda (cinza, pequeno) ocupando a largura toda, logo abaixo do campo anterior.
     * Aceita quebra de linha em HTML ({@code <br>}).
     */
    public void ajuda(String texto) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = linha; g.gridwidth = 2; g.weightx = 1;
        g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 4, 0);
        JLabel l = new JLabel("<html>" + texto + "</html>");
        l.setForeground(Theme.MUTED);
        l.setFont(Theme.fonte(11.5f, Font.PLAIN));
        add(l, g);
        linha++;
    }

    /**
     * Liga/desliga a edição de todos os campos de um contêiner (usado no modo "somente consulta").
     * Percorre os componentes filhos recursivamente.
     * <ul>
     *   <li>Campos de texto: usa {@code setEditable}, para o texto continuar com a aparência normal e dar para selecionar e copiar.</li>
     *   <li>Botões, combos, caixas de marcação e spinners: usa {@code setEnabled}.</li>
     * </ul>
     * Atenção: desabilita TODOS os botões do contêiner, inclusive os de adicionar/remover. Tabelas ({@code JTable})
     * não são afetadas, quem precisar deve tratá-las à parte (como faz o {@code TreinoEditor}).
     *
     * @param c          contêiner a percorrer (pode ser o próprio formulário ou um painel qualquer)
     * @param habilitado {@code true} para permitir edição, {@code false} para bloquear
     */
    public static void habilitarTudo(Container c, boolean habilitado) {
        for (Component filho : c.getComponents()) {
            // Campo de texto usa setEditable (e não setEnabled) para manter a aparência normal e permitir selecionar/copiar o texto.
            if (filho instanceof javax.swing.text.JTextComponent t) t.setEditable(habilitado);
            else if (filho instanceof JButton || filho instanceof JComboBox || filho instanceof JCheckBox || filho instanceof JSpinner)
                filho.setEnabled(habilitado);
            if (filho instanceof Container sub) habilitarTudo(sub, habilitado);
        }
    }
}
