package br.com.vidaativa.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Painel de formulário: rótulo à esquerda, campo à direita, linha por linha (ou dois campos por linha).
 * <p>
 * Quem usa: o login e todos os diálogos de cadastro (aluno, funcionário, professor, ficha, avaliação, pagamento).
 * Normalmente o formulário é entregue ao {@link DialogoForm#abrir}, que o coloca dentro de uma janela com Cancelar/Salvar.
 * <p>
 * Como usar: crie o {@code Formulario} e chame, na ordem em que as linhas devem aparecer, {@link #adicionar},
 * {@link #adicionarPar}, {@link #adicionarGrande}, {@link #secao} e {@link #ajuda}. Cada chamada ocupa a próxima linha.
 * <p>
 * Internamente usa {@code GridBagLayout} com 4 colunas: rótulo 1, campo 1, rótulo 2, campo 2. As colunas de campo
 * (1 e 3) é que se esticam; as de rótulo ficam do tamanho do texto.
 */
public class Formulario extends JPanel {
    // Próxima linha livre da grade (gridy). Cada método que adiciona algo incrementa este contador.
    private int linha = 0;
    // Vira true quando há uma linha que cresce na vertical; o DialogoForm usa isso para decidir se centraliza ou não o formulário.
    private boolean temLinhaGrande = false;

    /** Cria o formulário vazio, com fundo transparente (aparece o fundo da janela). */
    public Formulario() {
        super(new GridBagLayout());
        setOpaque(false);
    }

    /**
     * Uma linha: rótulo + campo ocupando o resto da largura.
     *
     * @param rotulo texto à esquerda (por convenção do projeto, campos obrigatórios terminam com " *")
     */
    public void adicionar(String rotulo, JComponent campo) {
        GridBagConstraints g = base();
        g.gridx = 0; g.gridy = linha; g.weightx = 0;
        add(UI.rotulo(rotulo), g);
        // gridwidth = 3: o campo atravessa as colunas 1 a 3, então fica alinhado com os campos das linhas em par.
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1;
        add(campo, g);
        linha++;
    }

    /** Uma linha com dois pares rótulo + campo. */
    public void adicionarPar(String rotulo1, JComponent campo1, String rotulo2, JComponent campo2) {
        GridBagConstraints g = base();
        g.gridy = linha;
        g.gridx = 0; g.weightx = 0; add(UI.rotulo(rotulo1), g);
        g.gridx = 1; g.weightx = 1; add(campo1, g);
        // O segundo rótulo ganha mais espaço à esquerda (18) para separar visualmente os dois pares.
        g.gridx = 2; g.weightx = 0; g.insets = new Insets(5, 18, 5, 8); add(UI.rotulo(rotulo2), g);
        g.gridx = 3; g.weightx = 1; g.insets = new Insets(5, 0, 5, 0); add(campo2, g);
        linha++;
    }

    /**
     * Linha que cresce na vertical (para caixas de texto grandes ou abas). Em geral use uma só por formulário,
     * porque as linhas grandes dividem entre si o espaço extra da altura. O rótulo fica alinhado ao topo.
     */
    public void adicionarGrande(String rotulo, JComponent campo) {
        temLinhaGrande = true;
        GridBagConstraints g = base();
        g.gridx = 0; g.gridy = linha; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(9, 0, 5, 8);
        add(UI.rotulo(rotulo), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1; g.weighty = 1; g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(5, 0, 5, 0);
        add(campo, g);
        linha++;
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

    /** Título de seção dentro do formulário: texto verde em negrito com uma linha embaixo. Ocupa as 4 colunas. */
    public void secao(String titulo) {
        GridBagConstraints g = base();
        g.gridx = 0; g.gridy = linha; g.gridwidth = 4; g.weightx = 1;
        // Sem espaço extra em cima quando a seção é a primeira linha; nas demais, 14 px separam da seção anterior.
        g.insets = new Insets(linha == 0 ? 0 : 14, 0, 4, 0);
        JLabel l = new JLabel(titulo);
        l.setForeground(Theme.GREEN);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER));
        add(l, g);
        linha++;
    }

    /**
     * Texto de ajuda (cinza, pequeno) ocupando a largura toda, alinhado com os campos (abaixo de um campo).
     * Aceita quebra de linha em HTML ({@code <br>}).
     */
    public void ajuda(String texto) {
        GridBagConstraints g = base();
        g.gridx = 1; g.gridy = linha; g.gridwidth = 3; g.weightx = 1;
        // O HTML faz o JLabel quebrar o texto em várias linhas quando for longo.
        JLabel l = new JLabel("<html>" + texto + "</html>");
        l.setForeground(Theme.MUTED);
        l.setFont(l.getFont().deriveFont(11.5f));
        add(l, g);
        linha++;
    }

    /** Cria as restrições padrão de uma célula: margem, alinhamento à esquerda e esticar na horizontal. Cada método ajusta o que precisa. */
    private GridBagConstraints base() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 0, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        return g;
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
