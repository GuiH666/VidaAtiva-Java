package br.com.vidaativa.ui.panels;

import br.com.vidaativa.model.Exercicio;
import br.com.vidaativa.model.Treino;
import br.com.vidaativa.ui.Formulario;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma aba da ficha de treino: nome do treino, dia da semana e a tabela de exercícios (editável direto na célula).
 * <p>
 * Quem usa: o {@code FichaDialog} cria um {@code TreinoEditor} para cada treino da ficha (uma aba por treino) e, ao
 * salvar, chama {@link #toTreino()} em cada um para ler o que está na tela. A classe é visível só dentro do pacote.
 * <p>
 * Modo somente consulta: com {@code editavel = false} tudo fica bloqueado (campos, botões e tabela).
 * <p>
 * Esta classe não grava no banco. Quem grava é o {@code FichaTreinoDAO}, que recria todos os treinos e exercícios a cada
 * salvamento; por isso a ordem das linhas da tabela é a ordem em que os exercícios serão gravados e exibidos.
 */
class TreinoEditor extends JPanel {
    // A posição 0 é vazia de propósito: significa "sem dia definido" (o treino é gravado com dia nulo).
    static final String[] DIAS = {"", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};
    // Títulos das colunas, na mesma ordem dos índices usados em toTreino (0 = Exercício ... 6 = Observações).
    private static final String[] COLUNAS = {"Exercício", "Grupo muscular", "Séries", "Repetições", "Carga", "Descanso", "Observações"};

    // Sem "private": o FichaDialog lê este campo para atualizar o título da aba enquanto o usuário digita e para citar o nome ao confirmar a remoção.
    final JTextField nome = new JTextField();
    private final JComboBox<String> dia = new JComboBox<>(DIAS);
    private final DefaultTableModel modelo = new DefaultTableModel(COLUNAS, 0);
    private final JTable tabela = new JTable(modelo);
    // O id e a descrição do treino não aparecem na tela; ficam guardados aqui para voltarem intactos em toTreino().
    // Para um treino novo valem 0 e null.
    private final int idOriginal;
    private final String descricao;

    /**
     * @param t        treino a editar, ou null para começar em branco
     * @param editavel false para abrir só para consulta (tudo bloqueado)
     */
    TreinoEditor(Treino t, boolean editavel) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        idOriginal = t == null ? 0 : t.id();
        descricao = t == null ? null : t.descricao();
        // --- Preenche a tela com o treino existente ---
        if (t != null) {
            nome.setText(t.nome());
            // Se o dia gravado não estiver na lista DIAS, o combo ignora o valor e fica em branco.
            if (t.diaSemana() != null) dia.setSelectedItem(t.diaSemana());
            for (Exercicio e : t.exercicios()) {
                // A tabela guarda tudo como texto, inclusive as séries (por isso o toString), e usa "" no lugar de null.
                modelo.addRow(new Object[]{e.nome(), vazio(e.grupoMuscular()), e.series() == null ? "" : e.series().toString(),
                        vazio(e.repeticoes()), vazio(e.carga()), vazio(e.descanso()), vazio(e.observacoes())});
            }
        }

        // --- Tabela de exercícios ---
        UI.estilizarTabela(tabela);
        // Começa a digitar assim que o usuário aperta uma tecla numa célula selecionada, com o foco indo para o editor da célula.
        tabela.setSurrendersFocusOnKeystroke(true);
        // Se o foco sair da tabela no meio da edição (ex.: clicar em Salvar), o texto digitado é confirmado em vez de perdido.
        tabela.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        int[] larguras = {210, 120, 60, 90, 80, 80, 220};
        for (int i = 0; i < larguras.length; i++) tabela.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);

        // --- Cabeçalho: nome do treino e dia da semana ---
        Formulario cab = new Formulario();
        cab.adicionarPar("Nome do treino *", nome, "Dia da semana", dia);

        // --- Botões da tabela ---
        JButton add = Theme.button("+ Exercício");
        add.addActionListener(e -> {
            // Nova linha já vem com valores comuns: 3 séries (coluna Séries), 10-12 repetições e 60 s de descanso.
            modelo.addRow(new Object[]{"", "", "3", "10-12", "", "60 s", ""});
            // Seleciona a linha nova e já abre a primeira célula (nome do exercício) para digitar.
            int r = modelo.getRowCount() - 1;
            tabela.setRowSelectionInterval(r, r);
            tabela.editCellAt(r, 0);
            Component ed = tabela.getEditorComponent();
            if (ed != null) ed.requestFocusInWindow();
        });
        JButton rem = Theme.button("Remover exercício");
        rem.addActionListener(e -> {
            // Encerra qualquer edição em andamento antes de remover a linha, para não deixar um editor aberto numa linha que sumiu.
            parar();
            int r = tabela.getSelectedRow();
            if (r >= 0) modelo.removeRow(r);
        });
        JButton sobe = Theme.button("▲");
        sobe.setToolTipText("Mover para cima");
        sobe.addActionListener(e -> mover(-1));
        JButton desce = Theme.button("▼");
        desce.setToolTipText("Mover para baixo");
        desce.addActionListener(e -> mover(1));
        JPanel barra = UI.botoes(add, rem, sobe, desce);

        // --- Montagem da aba ---
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(cab, BorderLayout.NORTH);
        topo.add(barra, BorderLayout.SOUTH);
        add(topo, BorderLayout.NORTH);
        add(UI.rolagem(tabela), BorderLayout.CENTER);

        // Modo consulta: habilitarTudo bloqueia campos, combo e botões, mas não mexe em JTable; por isso a tabela é desabilitada à parte.
        if (!editavel) {
            Formulario.habilitarTudo(this, false);
            tabela.setEnabled(false);
        }
    }

    /** Troca null por texto vazio (as células da tabela não devem receber null). */
    private static String vazio(String s) { return s == null ? "" : s; }

    /** Confirma a edição de célula em andamento, se houver, para que o valor digitado entre no modelo antes de ler ou mudar linhas. */
    private void parar() {
        if (tabela.isEditing()) tabela.getCellEditor().stopCellEditing();
    }

    /**
     * Move a linha selecionada para cima ou para baixo e mantém a seleção nela.
     * Não faz nada se não há linha selecionada ou se ela já está na ponta.
     *
     * @param delta -1 sobe uma posição; 1 desce uma posição
     */
    private void mover(int delta) {
        parar();
        int r = tabela.getSelectedRow();
        int destino = r + delta;
        if (r < 0 || destino < 0 || destino >= modelo.getRowCount()) return;
        modelo.moveRow(r, r, destino);
        tabela.setRowSelectionInterval(destino, destino);
    }

    /**
     * Lê o que está na tela e monta o {@link Treino} para o {@code FichaDialog} gravar.
     * <p>
     * Linhas totalmente vazias são ignoradas. O dia em branco vira null. O id e a descrição originais são preservados.
     * Os ids dos exercícios saem sempre 0, pois o DAO recria todos ao salvar.
     * <p>
     * Esta validação só cobre o que a tabela precisa: o nome do treino em branco é recusado depois, pelo DAO.
     *
     * @throws IllegalArgumentException se uma linha tem dados mas não tem o nome do exercício, ou se "Séries" não é um número inteiro;
     *                                  a mensagem cita o treino e a linha para o usuário achar o problema
     */
    Treino toTreino() {
        // Garante que a última célula digitada (ainda em edição) seja incluída na leitura.
        parar();
        List<Exercicio> exs = new ArrayList<>();
        for (int r = 0; r < modelo.getRowCount(); r++) {
            String[] v = new String[COLUNAS.length];
            boolean tudoVazio = true;
            for (int c = 0; c < COLUNAS.length; c++) {
                Object o = modelo.getValueAt(r, c);
                v[c] = o == null ? "" : o.toString().trim();
                if (!v[c].isEmpty()) tudoVazio = false;
            }
            // Linha criada por engano (ou limpa pelo usuário): ignora em vez de acusar erro.
            if (tudoVazio) continue;
            if (v[0].isEmpty()) throw new IllegalArgumentException("Treino \"" + nome.getText().trim() + "\": a linha " + (r + 1) + " não tem o nome do exercício.");
            // Séries é a única coluna numérica; é opcional, mas se vier preenchida precisa ser um inteiro.
            Integer series = null;
            if (!v[2].isEmpty()) {
                try {
                    series = Integer.parseInt(v[2]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Treino \"" + nome.getText().trim() + "\": \"Séries\" de " + v[0] + " precisa ser um número (ex.: 4).");
                }
            }
            exs.add(new Exercicio(0, v[0], v[1], series, v[3], v[4], v[5], v[6]));
        }
        // Dia em branco (item "" da lista) vira null, que é como o banco e o site representam "dia livre".
        String d = (String) dia.getSelectedItem();
        return new Treino(idOriginal, nome.getText().trim(), d == null || d.isEmpty() ? null : d, descricao, exs);
    }
}
