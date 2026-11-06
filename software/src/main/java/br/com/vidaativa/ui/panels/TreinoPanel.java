package br.com.vidaativa.ui.panels;

import br.com.vidaativa.model.Exercicio;
import br.com.vidaativa.model.Treino;
import br.com.vidaativa.service.BibliotecaExercicios;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.Cartao;
import br.com.vidaativa.ui.componentes.CampoDica;
import br.com.vidaativa.ui.componentes.ConteudoRolavel;
import br.com.vidaativa.ui.componentes.Opcoes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conteúdo de um treino da ficha (uma "aba" do editor): nome, dia da semana e a lista de exercícios.
 * <p>
 * Cada exercício é um cartão com nome, grupo muscular, séries, repetições, carga, descanso e observações, mais botões
 * para subir, descer e remover. Os exercícios entram pelo botão "+ Exercício" ou pela biblioteca ao lado (valores
 * sugeridos já preenchidos). Substitui a antiga tabela editável, que obrigava a digitar célula por célula.
 * <p>
 * Esta classe não grava no banco: {@code FichaEditor} chama {@link #toTreino()} ao salvar e entrega o resultado ao
 * {@code FichaTreinoDAO}, que recria todos os treinos e exercícios; por isso a ordem dos cartões é a ordem gravada.
 */
class TreinoPanel extends JPanel {
    /** Dias da semana gravados no banco, na ordem; o rótulo curto é só para as pílulas. */
    static final Map<String, String> DIAS = new java.util.LinkedHashMap<>();
    static {
        DIAS.put("Seg", "Segunda-feira"); DIAS.put("Ter", "Terça-feira"); DIAS.put("Qua", "Quarta-feira");
        DIAS.put("Qui", "Quinta-feira"); DIAS.put("Sex", "Sexta-feira"); DIAS.put("Sáb", "Sábado"); DIAS.put("Dom", "Domingo");
    }

    /** Campo do nome do treino; o editor lê para atualizar o título da aba enquanto o professor digita. */
    final JTextField nome = new CampoDica("Ex.: Treino A — Peito e tríceps");
    private final Opcoes<String> dia = Opcoes.texto(new ArrayList<>(DIAS.keySet()), true);
    private final JPanel linhas = new JPanel();
    private final JPanel vazio;
    private final JScrollPane rolagem;
    private final List<Linha> exercicios = new ArrayList<>();
    private final JButton adicionar = Theme.button("Exercício em branco");
    // O id e a descrição do treino não aparecem na tela; ficam guardados para voltarem intactos em toTreino(). Treino novo: 0 e null.
    private final int idOriginal;
    private final String descricao;
    private Runnable aoAlterar = () -> { };
    private boolean editavel = true;

    /**
     * @param t        treino a mostrar, ou null para começar em branco
     * @param editavel false para abrir só para consulta (tudo bloqueado)
     */
    TreinoPanel(Treino t, boolean editavel) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        idOriginal = t == null ? 0 : t.id();
        descricao = t == null ? null : t.descricao();

        // --- Cabeçalho: nome do treino e dia da semana ---
        JPanel cab = new JPanel(new GridBagLayout());
        cab.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0; g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.weightx = 1; g.insets = new Insets(0, 0, 5, 16);
        cab.add(UI.rotulo("<html>Nome do treino <font color='#22C55E'>*</font></html>"), g);
        g.gridx = 1; g.weightx = 0; g.insets = new Insets(0, 0, 5, 0);
        cab.add(UI.rotulo("Dia da semana (opcional)"), g);
        g.gridy = 1; g.gridx = 0; g.weightx = 1; g.insets = new Insets(0, 0, 0, 16);
        cab.add(nome, g);
        g.gridx = 1; g.weightx = 0; g.insets = new Insets(0, 0, 0, 0);
        cab.add(dia, g);
        add(cab, BorderLayout.NORTH);

        // --- Lista de exercícios ---
        linhas.setOpaque(false);
        linhas.setLayout(new BoxLayout(linhas, BoxLayout.Y_AXIS));
        vazio = estadoVazio();
        ConteudoRolavel conteudo = new ConteudoRolavel(new BorderLayout());
        conteudo.add(linhas, BorderLayout.NORTH);
        rolagem = ConteudoRolavel.embrulhar(conteudo);
        add(rolagem, BorderLayout.CENTER);

        adicionar.setIcon(Icones.icone(Icones.Tipo.MAIS, 16, Theme.TEXT));
        adicionar.setIconTextGap(8);
        adicionar.addActionListener(e -> { Linha l = novaLinha(null, "", null, "", "", "", ""); l.focarNome(); });
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rodape.setOpaque(false);
        rodape.add(adicionar);
        add(rodape, BorderLayout.SOUTH);

        // --- Preenche com o treino existente ---
        if (t != null) {
            nome.setText(t.nome());
            // Se o dia gravado não estiver na lista, a seleção fica em branco.
            DIAS.forEach((curto, completo) -> { if (completo.equals(t.diaSemana())) dia.setSelecionado(curto); });
            for (Exercicio e : t.exercicios())
                novaLinha(e.nome(), vazioSe(e.grupoMuscular()), e.series(), vazioSe(e.repeticoes()), vazioSe(e.carga()), vazioSe(e.descanso()), vazioSe(e.observacoes()));
        }
        nome.getDocument().addDocumentListener(simples(() -> aoAlterar.run()));
        dia.aoMudar(d -> aoAlterar.run());
        atualizarVazio();
        habilitar(editavel);
    }

    private static String vazioSe(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ API para o editor

    /** Chamado sempre que algo que afeta o resumo muda (nome, dia, exercícios, séries). */
    void aoAlterar(Runnable r) { this.aoAlterar = r; }

    int quantidadeExercicios() { return exercicios.size(); }

    int totalSeries() {
        int t = 0;
        for (Linha l : exercicios) t += l.series();
        return t;
    }

    /** Adiciona um exercício do catálogo (com os valores sugeridos) e leva o cursor para a carga, que o professor preenche em seguida. */
    void adicionar(BibliotecaExercicios.Modelo m) {
        Linha l = novaLinha(m.nome(), m.grupo(), m.series(), m.repeticoes(), "", m.descanso(), "");
        l.focarCarga();
        // Rola até o fim para o professor ver o exercício que acabou de entrar.
        SwingUtilities.invokeLater(() -> { JScrollBar b = rolagem.getVerticalScrollBar(); b.setValue(b.getMaximum()); });
    }

    /** Troca todos os exercícios pelos do catálogo com os nomes dados (usado pelos modelos de ficha). */
    void substituirPorModelo(List<String> nomes) {
        for (Linha l : new ArrayList<>(exercicios)) remover(l, false);
        for (String n : nomes) BibliotecaExercicios.buscar(n).ifPresent(m -> novaLinha(m.nome(), m.grupo(), m.series(), m.repeticoes(), "", m.descanso(), ""));
        atualizarVazio();
        aoAlterar.run();
    }

    /** Define o nome e o dia (completo, como no banco) do treino; usado ao aplicar um modelo. */
    void definirNomeEDia(String nomeTreino, String diaCompleto) {
        nome.setText(nomeTreino);
        dia.setSelecionado(null);
        DIAS.forEach((curto, completo) -> { if (completo.equals(diaCompleto)) dia.setSelecionado(curto); });
    }

    boolean temExercicios() { return !exercicios.isEmpty(); }

    /** Liga/desliga a edição (modo consulta de ficha de outro professor). */
    void habilitar(boolean h) {
        editavel = h;
        nome.setEditable(h);
        dia.habilitar(h);
        adicionar.setVisible(h);
        for (Linha l : exercicios) l.habilitar(h);
    }

    // ------------------------------------------------------------------ linhas

    private Linha novaLinha(String nomeEx, String grupo, Integer series, String reps, String carga, String descanso, String obs) {
        Linha l = new Linha(nomeEx == null ? "" : nomeEx, grupo, series == null ? "" : series.toString(), reps, carga, descanso, obs);
        l.habilitar(editavel);
        exercicios.add(l);
        linhas.add(l);
        renumerar();
        atualizarVazio();
        revalidate();
        repaint();
        aoAlterar.run();
        return l;
    }

    private void remover(Linha l, boolean avisar) {
        exercicios.remove(l);
        linhas.remove(l);
        renumerar();
        if (avisar) { atualizarVazio(); revalidate(); repaint(); aoAlterar.run(); }
    }

    private void mover(Linha l, int delta) {
        int i = exercicios.indexOf(l), j = i + delta;
        if (j < 0 || j >= exercicios.size()) return;
        exercicios.remove(i);
        exercicios.add(j, l);
        linhas.remove(l);
        linhas.add(l, j);
        renumerar();
        linhas.revalidate();
        linhas.repaint();
    }

    private void renumerar() {
        for (int i = 0; i < exercicios.size(); i++) exercicios.get(i).numero(i + 1, i == 0, i == exercicios.size() - 1);
    }

    private void atualizarVazio() {
        if (exercicios.isEmpty() && vazio.getParent() == null) linhas.add(vazio, 0);
        else if (!exercicios.isEmpty() && vazio.getParent() != null) linhas.remove(vazio);
    }

    private JPanel estadoVazio() {
        JLabel t = new JLabel("<html><div style='text-align:center'><b>Nenhum exercício neste treino</b><br>"
                + "<span style='color:#8A9591'>Abra a aba “Biblioteca de exercícios” à esquerda ou use “Exercício em branco”.</span></div></html>", SwingConstants.CENTER);
        t.setIcon(Icones.icone(Icones.Tipo.HALTER, 34, Theme.alfa(Theme.MUTED, 150)));
        t.setHorizontalTextPosition(SwingConstants.CENTER);
        t.setVerticalTextPosition(SwingConstants.BOTTOM);
        t.setIconTextGap(12);
        Cartao c = new Cartao(new BorderLayout(), 34);
        c.cores(Theme.alfa(Theme.PANEL, 140), Theme.BORDER);
        c.add(t, BorderLayout.CENTER);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // ------------------------------------------------------------------ leitura

    /**
     * Lê o que está na tela e monta o {@link Treino} para o editor gravar.
     * <p>
     * Cartões totalmente vazios são ignorados. O dia em branco vira null. O id e a descrição originais são preservados.
     * Os ids dos exercícios saem sempre 0, pois o DAO recria todos ao salvar. Esta validação só cobre o que a lista
     * precisa; o nome do treino em branco é recusado depois, pelo DAO.
     *
     * @throws IllegalArgumentException se um cartão tem dados mas não tem o nome do exercício; a mensagem cita o treino e o número
     */
    Treino toTreino() {
        List<Exercicio> exs = new ArrayList<>();
        for (int i = 0; i < exercicios.size(); i++) {
            Linha l = exercicios.get(i);
            if (l.vazia()) continue;
            String nomeEx = l.nome.getText().trim();
            if (nomeEx.isEmpty()) throw new IllegalArgumentException("Treino \"" + nome.getText().trim() + "\": o exercício " + (i + 1) + " não tem nome.");
            String s = l.series.getText().trim();
            Integer series = s.isEmpty() ? null : Integer.valueOf(s); // o campo só aceita dígitos
            exs.add(new Exercicio(0, nomeEx, l.grupoSelecionado(), series, l.reps.getText().trim(), l.carga.getText().trim(),
                    l.descanso.getText().trim(), l.obs.getText().trim()));
        }
        String curto = dia.getSelecionado();
        return new Treino(idOriginal, nome.getText().trim(), curto == null ? null : DIAS.get(curto), descricao, exs);
    }

    private static DocumentListener simples(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    // ------------------------------------------------------------------ um exercício

    /** Cartão de um exercício. */
    private final class Linha extends Cartao {
        final JTextField nome = new CampoDica("Nome do exercício");
        final JComboBox<String> grupo = new JComboBox<>();
        final JTextField series = new JTextField(3);
        final JTextField reps = new JTextField(6);
        final JTextField carga = new JTextField(6);
        final JTextField descanso = new JTextField(6);
        final JTextField obs = new CampoDica("Opcional");
        private final JLabel numero = new JLabel("1", SwingConstants.CENTER);
        private final JButton sobe = botao(Icones.Tipo.SOBE, "Mover para cima");
        private final JButton desce = botao(Icones.Tipo.DESCE, "Mover para baixo");
        private final JButton lixo = botao(Icones.Tipo.LIXO, "Remover exercício");

        Linha(String n, String g, String s, String r, String c, String d, String o) {
            super(new BorderLayout(0, 10), 14, 16);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            // O cartão não cresce além da altura preferida dentro do BoxLayout.
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            nome.setText(n);
            grupo.addItem("");
            for (String x : BibliotecaExercicios.GRUPOS) grupo.addItem(x);
            if (!g.isEmpty() && ((DefaultComboBoxModel<String>) grupo.getModel()).getIndexOf(g) < 0) grupo.addItem(g);
            grupo.setSelectedItem(g);
            grupo.setToolTipText("Grupo muscular");
            series.setText(s); reps.setText(r); carga.setText(c); descanso.setText(d); obs.setText(o);
            ((AbstractDocument) series.getDocument()).setDocumentFilter(new SoDigitos(2));
            series.setHorizontalAlignment(SwingConstants.CENTER);
            for (JTextField f : new JTextField[]{nome, series, reps, carga, descanso, obs}) f.getDocument().addDocumentListener(simples(() -> aoAlterar.run()));

            // Linha de cima: número, nome, grupo e botões.
            numero.setFont(Theme.fonte(13f, Font.BOLD));
            numero.setForeground(Theme.GREEN);
            numero.setOpaque(false);
            numero.setPreferredSize(new Dimension(30, 30));
            JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            botoes.setOpaque(false);
            botoes.add(sobe); botoes.add(desce); botoes.add(lixo);
            sobe.addActionListener(e -> mover(this, -1));
            desce.addActionListener(e -> mover(this, 1));
            lixo.addActionListener(e -> remover(this, true));
            JPanel cima = new JPanel(new GridBagLayout());
            cima.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.CENTER;
            gc.gridx = 0; gc.insets = new Insets(0, 0, 0, 12); cima.add(numero, gc);
            gc.gridx = 1; gc.weightx = 1; gc.insets = new Insets(0, 0, 0, 10); cima.add(nome, gc);
            gc.gridx = 2; gc.weightx = 0; cima.add(grupo, gc);
            gc.gridx = 3; gc.insets = new Insets(0, 0, 0, 0); cima.add(botoes, gc);
            grupo.setPreferredSize(new Dimension(150, nome.getPreferredSize().height));
            add(cima, BorderLayout.NORTH);

            // Linha de baixo: séries, repetições, carga, descanso e observações, cada um com o rótulo em cima.
            JPanel baixo = new JPanel(new GridBagLayout());
            baixo.setOpaque(false);
            baixo.setBorder(BorderFactory.createEmptyBorder(0, 42, 0, 0));
            String[] rot = {"Séries", "Repetições", "Carga", "Descanso", "Observações"};
            JTextField[] campos = {series, reps, carga, descanso, obs};
            double[] pesos = {0, 0, 0, 0, 1};
            for (int i = 0; i < campos.length; i++) {
                GridBagConstraints b = new GridBagConstraints();
                b.gridx = i; b.weightx = pesos[i]; b.fill = GridBagConstraints.HORIZONTAL; b.anchor = GridBagConstraints.WEST;
                b.insets = new Insets(0, 0, 3, i == campos.length - 1 ? 0 : 10);
                JLabel l = UI.rotulo(rot[i]);
                l.setFont(Theme.fonte(11.5f, Font.PLAIN));
                baixo.add(l, b);
                b.gridy = 1; b.insets = new Insets(0, 0, 0, i == campos.length - 1 ? 0 : 10);
                baixo.add(campos[i], b);
            }
            series.setPreferredSize(new Dimension(64, series.getPreferredSize().height));
            reps.setPreferredSize(new Dimension(104, reps.getPreferredSize().height));
            carga.setPreferredSize(new Dimension(104, carga.getPreferredSize().height));
            descanso.setPreferredSize(new Dimension(96, descanso.getPreferredSize().height));
            carga.setToolTipText("Ex.: 20 kg, peso do corpo, barra livre");
            descanso.setToolTipText("Ex.: 60 s");
            add(baixo, BorderLayout.CENTER);
        }

        private JButton botao(Icones.Tipo tipo, String dica) {
            JButton b = Theme.ghost("");
            b.setIcon(Icones.icone(tipo, 16, tipo == Icones.Tipo.LIXO ? Theme.DANGER : Theme.MUTED));
            b.setToolTipText(dica);
            b.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
            return b;
        }

        void numero(int n, boolean primeiro, boolean ultimo) {
            numero.setText(String.valueOf(n));
            sobe.setEnabled(!primeiro && editavel);
            desce.setEnabled(!ultimo && editavel);
        }

        int series() {
            String s = series.getText().trim();
            return s.isEmpty() ? 0 : Integer.parseInt(s);
        }

        boolean vazia() {
            return nome.getText().isBlank() && series.getText().isBlank() && reps.getText().isBlank()
                    && carga.getText().isBlank() && descanso.getText().isBlank() && obs.getText().isBlank();
        }

        String grupoSelecionado() {
            Object g = grupo.getSelectedItem();
            return g == null ? "" : g.toString();
        }

        void focarNome() { nome.requestFocusInWindow(); }
        void focarCarga() { carga.requestFocusInWindow(); }

        void habilitar(boolean h) {
            for (JTextField f : new JTextField[]{nome, series, reps, carga, descanso, obs}) f.setEditable(h);
            grupo.setEnabled(h);
            sobe.setVisible(h); desce.setVisible(h); lixo.setVisible(h);
            if (h) renumerar();
        }
    }

    /** Aceita só dígitos e no máximo {@code max} caracteres (campo de séries). */
    private static final class SoDigitos extends DocumentFilter {
        private final int max;
        SoDigitos(int max) { this.max = max; }

        @Override public void insertString(FilterBypass fb, int off, String t, AttributeSet a) throws BadLocationException { replace(fb, off, 0, t, a); }

        @Override public void replace(FilterBypass fb, int off, int len, String t, AttributeSet a) throws BadLocationException {
            String novo = t == null ? "" : t.replaceAll("\\D", "");
            int atual = fb.getDocument().getLength() - len;
            if (atual + novo.length() > max) novo = novo.substring(0, Math.max(0, max - atual));
            if (novo.isEmpty() && (t != null && !t.isEmpty())) return;
            super.replace(fb, off, len, novo, a);
        }
    }
}
