package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.FichaTreinoDAO;
import br.com.vidaativa.dao.ProfessorDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.CampoBusca;
import br.com.vidaativa.ui.componentes.Opcoes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela "Fichas de treino" (menu do Professor).
 *
 * <p>Lista as fichas de todos os alunos, com busca e filtro por situação. O professor logado cria novas fichas, edita,
 * copia e exclui <b>apenas as próprias</b>; as fichas dos colegas abrem em modo somente leitura (e podem ser copiadas
 * para um aluno seu).</p>
 *
 * <p>Situação da ficha: Rascunho (ainda em edição), Ativa (a que o aluno vê no site) ou Inativa. Ao salvar uma ficha
 * como Ativa, o {@link FichaTreinoDAO} deixa as outras fichas ativas do mesmo aluno como Inativas.</p>
 *
 * <p>Criar e editar acontecem no {@link FichaEditor}, que ocupa a tela toda no lugar da lista (sem janelinha) e devolve
 * para a lista ao salvar ou voltar.</p>
 */
public class FichasPanel extends PainelBase {
    private static final String LISTA = "lista", EDITOR = "editor";

    /** Usuário logado; serve para descobrir qual professor ele é (o login do professor está em usuarios, o perfil em professores). */
    private final Usuario usuario;
    private final FichaTreinoDAO dao = new FichaTreinoDAO();
    private final JTable tabela = UI.tabela("Aluno", "Ficha", "Objetivo", "Dias/sem.", "Revisão", "Situação", "Professor");
    private final JTextField busca = new CampoBusca("Buscar por aluno, ficha ou objetivo", 22);
    private final Opcoes<String> filtro = Opcoes.texto(List.of("Todas", "Ativas", "Rascunhos", "Inativas"), false);
    private final JCheckBox soMinhas = new JCheckBox("Só as minhas");
    private final JLabel contagem = new JLabel(" ");
    private final CardLayout cartas = new CardLayout();
    private final JPanel pilha = new JPanel(cartas);
    private JComponent editorAtual;
    /** Professor logado, carregado na primeira vez em recarregar(); null se o usuário não tem cadastro de professor. */
    private Professor professor;
    /** Todas as fichas vindas do banco. */
    private List<FichaTreino> todas = new ArrayList<>();
    /** Fichas que passaram pelos filtros e estão na tabela; a linha selecionada é o mesmo índice desta lista. */
    private List<FichaTreino> visiveis = new ArrayList<>();

    public FichasPanel(Usuario usuario) {
        super("Fichas de treino", "Crie, edite e copie fichas. Fichas \"Ativas\" aparecem para o aluno no site.");
        this.usuario = usuario;

        // --- Botões de ação ---
        JButton nova = Theme.primary("Nova ficha");
        nova.setIcon(Icones.icone(Icones.Tipo.MAIS, 16, new Color(6, 26, 14)));
        nova.setIconTextGap(8);
        nova.addActionListener(e -> nova());
        JButton abrir = Theme.button("Abrir / editar");
        abrir.addActionListener(e -> abrirSelecionada());
        JButton copiar = Theme.button("Copiar para outro aluno");
        copiar.setIcon(Icones.icone(Icones.Tipo.COPIAR, 16, Theme.TEXT));
        copiar.setIconTextGap(8);
        copiar.addActionListener(e -> copiarSelecionada());
        JButton excluir = Theme.danger("Excluir");
        excluir.addActionListener(e -> excluir());

        // --- Filtros: filtram a lista que já está na memória (não vai ao banco a cada letra) ---
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        filtro.setSelecionado("Todas");
        filtro.aoMudar(f -> filtrar());
        soMinhas.setOpaque(false);
        soMinhas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        soMinhas.addActionListener(e -> filtrar());

        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        acoes.setOpaque(false);
        acoes.add(nova);
        acoes.add(abrir);
        acoes.add(copiar);
        acoes.add(excluir);
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        filtros.setOpaque(false);
        filtros.add(soMinhas);
        filtros.add(busca);
        JPanel linha1 = new JPanel(new BorderLayout());
        linha1.setOpaque(false);
        linha1.add(acoes, BorderLayout.WEST);
        linha1.add(filtros, BorderLayout.EAST);
        JPanel linha2 = new JPanel(new BorderLayout());
        linha2.setOpaque(false);
        linha2.add(filtro, BorderLayout.WEST);
        contagem.setForeground(Theme.MUTED);
        contagem.setFont(Theme.fonte(12.5f, Font.PLAIN));
        linha2.add(contagem, BorderLayout.EAST);
        JPanel topo = new JPanel(new GridLayout(2, 1, 0, 6));
        topo.setOpaque(false);
        topo.add(linha1);
        topo.add(linha2);

        JPanel lista = new JPanel(new BorderLayout(0, 8));
        lista.setOpaque(false);
        lista.add(topo, BorderLayout.NORTH);
        lista.add(UI.rolagem(tabela), BorderLayout.CENTER);
        pilha.setOpaque(false);
        pilha.add(lista, LISTA);
        add(pilha, BorderLayout.CENTER);

        // Coluna 5 = Situação da ficha. Duplo clique em uma linha abre a ficha.
        UI.colorirColuna(tabela, 5, s -> switch (s) {
            case "Ativa" -> Theme.GREEN;
            case "Rascunho" -> Theme.WARN;
            default -> Theme.MUTED;
        });
        int[] larguras = {190, 220, 150, 80, 110, 120, 170};
        for (int i = 0; i < larguras.length; i++) tabela.getColumnModel().getColumn(i).setPreferredWidth(larguras[i]);
        tabela.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) abrirSelecionada(); }
        });
    }

    /** Recarrega as fichas do banco (e descobre quem é o professor logado, só na primeira vez). */
    @Override
    public void recarregar() {
        // Voltar ao menu Fichas com um editor aberto mantém o editor (o trabalho não se perde ao trocar de tela).
        if (editorAtual != null) return;
        try {
            if (professor == null) professor = new ProfessorDAO().buscarPorUsuarioId(usuario.id());
            todas = dao.listar();
            filtrar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Aplica busca, situação e "só as minhas" sobre {@link #todas} e redesenha a tabela. */
    private void filtrar() {
        String termo = busca.getText().trim().toLowerCase(Locale.ROOT);
        String sit = filtro.getSelecionado();
        visiveis = new ArrayList<>();
        DefaultTableModel m = UI.modelo(tabela);
        m.setRowCount(0);
        for (FichaTreino f : todas) {
            String texto = (f.alunoNome() + " " + f.nome() + " " + (f.objetivo() == null ? "" : f.objetivo())).toLowerCase(Locale.ROOT);
            if (!termo.isEmpty() && !texto.contains(termo)) continue;
            String codigo = sit == null ? "" : switch (sit) { case "Ativas" -> "ATIVA"; case "Rascunhos" -> "RASCUNHO"; case "Inativas" -> "INATIVA"; default -> ""; };
            if (!codigo.isEmpty() && !codigo.equals(f.status())) continue;
            if (soMinhas.isSelected() && professor != null && f.professorId() != professor.id()) continue;
            visiveis.add(f);
            m.addRow(new Object[]{f.alunoNome(), f.nome(), UI.texto(f.objetivo()),
                    f.frequenciaSemanal() == null ? "—" : f.frequenciaSemanal().toString(), UI.data(f.dataRevisao()),
                    rotuloStatus(f.status()), f.professorNome()});
        }
        contagem.setText(visiveis.size() + (visiveis.size() == 1 ? " ficha" : " fichas") + (visiveis.size() != todas.size() ? " de " + todas.size() : ""));
    }

    /** Converte o status gravado no banco (ATIVA, RASCUNHO, INATIVA) para o texto mostrado na tela. */
    private static String rotuloStatus(String s) {
        return switch (s) {
            case "ATIVA" -> "Ativa";
            case "RASCUNHO" -> "Rascunho";
            default -> "Inativa";
        };
    }

    /** Ficha da linha selecionada, ou null (com aviso) se nada estiver selecionado. */
    private FichaTreino selecionada() {
        int r = tabela.getSelectedRow();
        if (r < 0 || r >= visiveis.size()) { UI.info(this, "Selecione uma ficha na tabela."); return null; }
        return visiveis.get(r);
    }

    /** Só professores cadastrados podem criar/editar fichas; mostra o motivo se o usuário não for. */
    private boolean exigirProfessor() {
        if (professor != null) return true;
        UI.erro(this, new IllegalStateException("Seu usuário não está cadastrado como professor. Peça ao gerente para cadastrar você na aba Professores."));
        return false;
    }

    private void nova() {
        if (!exigirProfessor()) return;
        abrirEditor(null, false, false);
    }

    /** Abre a ficha selecionada: editável se for do professor logado, somente leitura se for de outro. */
    private void abrirSelecionada() {
        FichaTreino resumo = selecionada();
        if (resumo == null || !exigirProfessor()) return;
        try {
            // A lista traz só o resumo; os treinos e exercícios são buscados por inteiro ao abrir.
            FichaTreino completa = dao.buscar(resumo.id());
            if (completa == null) { UI.info(this, "Esta ficha não existe mais."); recarregar(); return; }
            abrirEditor(completa, false, completa.professorId() != professor.id());
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Cria uma nova ficha a partir da selecionada (mesmos treinos), para o professor escolher outro aluno. */
    private void copiarSelecionada() {
        FichaTreino resumo = selecionada();
        if (resumo == null || !exigirProfessor()) return;
        try {
            FichaTreino completa = dao.buscar(resumo.id());
            if (completa == null) { UI.info(this, "Esta ficha não existe mais."); recarregar(); return; }
            abrirEditor(completa, true, false);
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Troca a lista pelo editor em tela cheia. */
    private void abrirEditor(FichaTreino base, boolean copia, boolean somenteLeitura) {
        List<Aluno> alunos;
        try {
            alunos = new ArrayList<>(new AlunoDAO().listar(false));
            // Se o aluno da ficha foi desativado depois, ele não vem em listar(false); busca à parte para a ficha abrir.
            if (base != null && !copia && alunos.stream().noneMatch(a -> a.id() == base.alunoId())) {
                Aluno a = new AlunoDAO().buscarPorId(base.alunoId());
                if (a != null) alunos.add(0, a);
            }
        } catch (Exception e) {
            UI.erro(this, e);
            return;
        }
        if (alunos.isEmpty()) {
            UI.erro(this, new IllegalStateException("Não há alunos ativos cadastrados. A recepção precisa cadastrar o aluno primeiro."));
            return;
        }
        mostrarEditor(new FichaEditor(base, copia, professor, alunos, somenteLeitura, this::fecharEditor, () -> { fecharEditor(); recarregar(); }, dao::salvar));
    }

    /** Coloca o editor na tela (esconde o cabeçalho da lista). */
    void mostrarEditor(FichaEditor editor) {
        editorAtual = editor;
        pilha.add(editor, EDITOR);
        mostrarCabecalho(false);
        cartas.show(pilha, EDITOR);
        pilha.revalidate();
    }

    /** Volta para a lista e descarta o editor. */
    private void fecharEditor() {
        if (editorAtual != null) pilha.remove(editorAtual);
        editorAtual = null;
        mostrarCabecalho(true);
        cartas.show(pilha, LISTA);
        pilha.revalidate();
        pilha.repaint();
    }

    /** Exclui a ficha selecionada (só se for do professor logado; pede confirmação). */
    private void excluir() {
        FichaTreino f = selecionada();
        if (f == null || !exigirProfessor()) return;
        if (f.professorId() != professor.id()) {
            UI.erro(this, new IllegalStateException("Só o(a) professor(a) " + f.professorNome() + " pode excluir esta ficha."));
            return;
        }
        if (!UI.confirmar(this, "Excluir a ficha \"" + f.nome() + "\" de " + f.alunoNome() + "?\n\nO cadastro do aluno continua no sistema. Esta ação não pode ser desfeita.")) return;
        try {
            dao.excluir(f.id());
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }
}
