package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.FichaTreinoDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.model.Treino;
import br.com.vidaativa.service.Precificacao;
import br.com.vidaativa.ui.*;
import br.com.vidaativa.ui.UI.Opcao;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Janela de criação/edição/consulta de uma ficha de treino, aberta por {@link FichasPanel}.
 *
 * <p>Parte de cima: dados gerais (aluno, nome, objetivo, dias por semana, datas, situação,
 * observações). Parte de baixo: uma aba por treino (A, B, C...), cada uma com seus exercícios,
 * montada por {@link TreinoEditor}.</p>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>O aluno de uma ficha existente não pode ser trocado.</li>
 *   <li>Em modo somente leitura (ficha de outro professor) todos os campos ficam travados e não há botão Salvar.</li>
 *   <li>Salvar como "Ativa" um aluno de plano sem ficha incluída pede confirmação ({@link Precificacao#incluiFicha}).</li>
 *   <li>Quem grava, valida (ficha ativa precisa de exercício) e desativa as outras fichas ativas é o {@link FichaTreinoDAO#salvar}.</li>
 * </ul>
 *
 * <p>Classe utilitária: não se cria objeto, só se chama {@link #abrir}.</p>
 */
final class FichaDialog {
    private FichaDialog() { }

    /**
     * Abre a janela e espera o usuário fechar (diálogo modal).
     *
     * @param pai componente sobre o qual a janela aparece
     * @param professor professor logado; vira o autor da ficha quando ela é nova
     * @param existente ficha já completa (com treinos) ou null para criar
     * @param somenteLeitura true para apenas consultar
     * @return true se salvou
     */
    static boolean abrir(Component pai, FichaTreino existente, Professor professor, boolean somenteLeitura) {
        boolean novo = existente == null;
        // --- Lista de alunos para escolher ---
        List<Aluno> alunos;
        try {
            alunos = new ArrayList<>(new AlunoDAO().listar(false));
            // Se o aluno da ficha foi desativado depois, ele não vem em listar(false); busca à parte para a ficha abrir.
            if (!novo && alunos.stream().noneMatch(a -> a.id() == existente.alunoId())) {
                Aluno a = new AlunoDAO().buscarPorId(existente.alunoId());
                if (a != null) alunos.add(0, a);
            }
        } catch (Exception e) {
            UI.erro(pai, e);
            return false;
        }
        if (alunos.isEmpty()) {
            UI.erro(pai, new IllegalStateException("Não há alunos ativos cadastrados. A recepção precisa cadastrar o aluno primeiro."));
            return false;
        }

        // --- Dados gerais da ficha ---
        JComboBox<Aluno> aluno = new JComboBox<>(alunos.toArray(new Aluno[0]));
        aluno.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Component c = super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Aluno a) setText(a.nome() + "  —  plano " + a.plano());
                return c;
            }
        });
        if (!novo) {
            for (Aluno a : alunos) if (a.id() == existente.alunoId()) aluno.setSelectedItem(a);
            aluno.setEnabled(false); // a ficha não troca de aluno depois de criada
        }

        JTextField nome = new JTextField(novo ? "" : existente.nome());
        // Objetivo é editável: o professor pode escolher da lista ou digitar o próprio.
        JComboBox<String> objetivo = new JComboBox<>(new String[]{"Hipertrofia", "Emagrecimento", "Condicionamento físico", "Força", "Reabilitação", "Saúde e bem-estar"});
        objetivo.setEditable(true);
        objetivo.setSelectedItem(novo || existente.objetivo() == null ? "Hipertrofia" : existente.objetivo());
        JSpinner freq = new JSpinner(new SpinnerNumberModel(novo || existente.frequenciaSemanal() == null ? 3 : existente.frequenciaSemanal(), 1, 7, 1));
        DateField inicio = new DateField("Início");
        DateField revisao = new DateField("Revisão");
        inicio.setData(novo ? LocalDate.now() : existente.dataInicio());
        revisao.setData(novo ? LocalDate.now().plusDays(60) : existente.dataRevisao());
        JComboBox<Opcao> status = new JComboBox<>(new Opcao[]{
                new Opcao("ATIVA", "Ativa — o aluno vê no site"),
                new Opcao("RASCUNHO", "Rascunho — só o professor vê"),
                new Opcao("INATIVA", "Inativa — histórico")});
        if (!novo) UI.selecionarOpcao(status, existente.status());
        JTextArea obs = UI.areaTexto(2);
        if (!novo && existente.observacoes() != null) obs.setText(existente.observacoes());

        // --- Montagem do formulário de cima ---
        Formulario f = new Formulario();
        f.adicionarPar("Aluno *", aluno, "Nome da ficha *", nome);
        f.adicionarPar("Objetivo", objetivo, "Dias por semana", freq);
        f.adicionarPar("Início", inicio, "Revisão", revisao);
        f.adicionar("Situação *", status);
        f.adicionarGrande("Observações", new JScrollPane(obs));

        // --- Treinos: uma aba por treino; "editores" guarda o editor de cada aba, na mesma ordem das abas ---
        JTabbedPane abas = new JTabbedPane();
        boolean editavel = !somenteLeitura;
        List<TreinoEditor> editores = new ArrayList<>();
        // Array de 1 posição só para a lambda poder se referir a si mesma depois (Java não deixa usar variável não-final).
        Runnable[] novoTreino = new Runnable[1];
        novoTreino[0] = () -> {
            // Sugere Treino A, B, C... conforme a quantidade de abas já existentes.
            String sugestao = "Treino " + (char) ('A' + Math.min(editores.size(), 25));
            adicionarAba(abas, editores, new Treino(0, sugestao, null, null, List.of()), true);
        };
        if (!novo) for (Treino t : existente.treinos()) adicionarAba(abas, editores, t, editavel);
        else if (editavel) novoTreino[0].run();

        JButton addTreino = Theme.button("+ Treino");
        addTreino.addActionListener(e -> { novoTreino[0].run(); abas.setSelectedIndex(abas.getTabCount() - 1); });
        JButton remTreino = Theme.button("Remover treino");
        remTreino.addActionListener(e -> {
            int i = abas.getSelectedIndex();
            if (i < 0) return;
            if (!UI.confirmar(pai, "Remover o treino \"" + editores.get(i).nome.getText() + "\" e todos os exercícios dele?")) return;
            abas.removeTabAt(i);
            editores.remove(i);
        });
        // --- Montagem da parte de baixo (título + botões + abas) e junção com o formulário ---
        JPanel barraTreinos = new JPanel(new BorderLayout());
        barraTreinos.setOpaque(false);
        JLabel tituloTreinos = new JLabel("Treinos da ficha");
        tituloTreinos.setForeground(Theme.GREEN);
        tituloTreinos.setFont(tituloTreinos.getFont().deriveFont(Font.BOLD));
        barraTreinos.add(tituloTreinos, BorderLayout.WEST);
        if (editavel) barraTreinos.add(UI.botoes(addTreino, remTreino), BorderLayout.EAST);

        JPanel treinos = new JPanel(new BorderLayout(0, 6));
        treinos.setOpaque(false);
        treinos.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        treinos.add(barraTreinos, BorderLayout.NORTH);
        treinos.add(abas, BorderLayout.CENTER);

        JPanel conteudo = new JPanel(new BorderLayout(0, 8));
        conteudo.setOpaque(false);
        conteudo.add(f, BorderLayout.NORTH);
        conteudo.add(treinos, BorderLayout.CENTER);
        if (somenteLeitura) Formulario.habilitarTudo(f, false);

        String titulo = novo ? "Nova ficha de treino" : somenteLeitura ? "Ficha de treino (consulta)" : "Editar ficha de treino";
        // O trecho dentro do () -> { } roda ao clicar em Salvar: lê os campos, valida o plano e grava no banco.
        return DialogoForm.abrir(pai, titulo, conteudo, somenteLeitura ? null : "Salvar ficha", () -> {
            Aluno a = (Aluno) aluno.getSelectedItem();
            List<Treino> lidos = new ArrayList<>();
            for (TreinoEditor ed : editores) lidos.add(ed.toTreino());
            String st = ((Opcao) status.getSelectedItem()).valor();
            if (st.equals("ATIVA") && !Precificacao.incluiFicha(a.plano())
                    && !UI.confirmar(pai, "O plano " + a.plano() + " de " + a.nome() + " não inclui ficha de treino personalizada.\n\nSalvar a ficha mesmo assim?"))
                throw new DialogoForm.Desistiu();
            Object obj = objetivo.getEditor().getItem();
            FichaTreino nova = new FichaTreino(novo ? 0 : existente.id(), a.id(), novo ? professor.id() : existente.professorId(),
                    a.nome(), professor.nome(), nome.getText(), obj == null ? null : obj.toString(), (Integer) freq.getValue(),
                    inicio.getData(), revisao.getData(), obs.getText(), st, lidos);
            new FichaTreinoDAO().salvar(nova);
        }, 980, 800);
    }

    /** Cria o editor de um treino, coloca em uma nova aba e mantém o título da aba igual ao nome digitado. */
    private static void adicionarAba(JTabbedPane abas, List<TreinoEditor> editores, Treino t, boolean editavel) {
        TreinoEditor ed = new TreinoEditor(t, editavel);
        editores.add(ed);
        abas.addTab(titulo(t.nome()), ed);
        ed.nome.getDocument().addDocumentListener(new DocumentListener() {
            private void atualizar() {
                int i = abas.indexOfComponent(ed);
                if (i >= 0) abas.setTitleAt(i, titulo(ed.nome.getText()));
            }
            public void insertUpdate(DocumentEvent e) { atualizar(); }
            public void removeUpdate(DocumentEvent e) { atualizar(); }
            public void changedUpdate(DocumentEvent e) { atualizar(); }
        });
    }

    /** Título da aba: o nome do treino, "Novo treino" se estiver vazio, e cortado em 28 caracteres para caber. */
    private static String titulo(String nome) {
        String n = nome == null || nome.isBlank() ? "Novo treino" : nome.trim();
        return n.length() > 28 ? n.substring(0, 27) + "…" : n;
    }
}
