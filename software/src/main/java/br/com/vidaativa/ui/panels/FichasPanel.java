package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.FichaTreinoDAO;
import br.com.vidaativa.dao.ProfessorDAO;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.*;

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
 * <p>Lista as fichas de todos os alunos. O professor logado cria novas fichas, edita e exclui
 * <b>apenas as próprias</b>; as fichas dos colegas abrem em modo somente leitura.</p>
 *
 * <p>Situação da ficha: Rascunho (ainda em edição), Ativa (a que o aluno vê no site) ou Inativa.
 * Ao salvar uma ficha como Ativa, o {@link FichaTreinoDAO} deixa as outras fichas ativas do mesmo
 * aluno como Inativas. A edição em si acontece em {@link FichaDialog}.</p>
 */
public class FichasPanel extends PainelBase {
    /** Usuário logado; serve para descobrir qual professor ele é (o login do professor está em usuarios, o perfil em professores). */
    private final Usuario usuario;
    private final FichaTreinoDAO dao = new FichaTreinoDAO();
    private final JTable tabela = UI.tabela("Aluno", "Ficha", "Objetivo", "Dias/sem.", "Revisão", "Situação", "Professor");
    private final JTextField busca = new JTextField(20);
    /** Professor logado, carregado na primeira vez em recarregar(); null se o usuário não tem cadastro de professor. */
    private Professor professor;
    /** Todas as fichas vindas do banco. */
    private List<FichaTreino> todas = new ArrayList<>();
    /** Fichas que passaram pela busca e estão na tabela; a linha selecionada é o mesmo índice desta lista. */
    private List<FichaTreino> visiveis = new ArrayList<>();

    public FichasPanel(Usuario usuario) {
        super("Fichas de treino", "Crie, edite e exclua fichas. Fichas \"Ativas\" aparecem para o aluno no site.");
        this.usuario = usuario;

        // --- Botões de ação ---
        JButton nova = Theme.primary("Nova ficha");
        nova.addActionListener(e -> nova());
        JButton abrir = Theme.button("Abrir / editar");
        abrir.addActionListener(e -> abrirSelecionada());
        JButton excluir = Theme.danger("Excluir");
        excluir.addActionListener(e -> excluir());

        // --- Busca: filtra a lista que já está na memória (não vai ao banco a cada letra) ---
        busca.setToolTipText("Buscar por aluno, ficha ou objetivo");
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        direita.setOpaque(false);
        direita.add(UI.rotulo("Buscar:"));
        direita.add(busca);
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(UI.botoes(nova, abrir, excluir), BorderLayout.WEST);
        topo.add(direita, BorderLayout.EAST);

        JPanel corpo = new JPanel(new BorderLayout(0, 10));
        corpo.setOpaque(false);
        corpo.add(topo, BorderLayout.NORTH);
        corpo.add(UI.rolagem(tabela), BorderLayout.CENTER);
        add(corpo, BorderLayout.CENTER);

        // Coluna 5 = Situação da ficha. Duplo clique em uma linha abre a ficha.
        UI.colorirColuna(tabela, 5, s -> switch (s) {
            case "Ativa" -> Theme.GREEN;
            case "Rascunho" -> Theme.WARN;
            default -> Theme.MUTED;
        });
        tabela.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) abrirSelecionada(); }
        });
    }

    /** Recarrega as fichas do banco (e descobre quem é o professor logado, só na primeira vez). */
    @Override
    public void recarregar() {
        try {
            if (professor == null) professor = new ProfessorDAO().buscarPorUsuarioId(usuario.id());
            todas = dao.listar();
            filtrar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Aplica o texto da busca (aluno, nome da ficha ou objetivo) sobre {@link #todas} e redesenha a tabela. */
    private void filtrar() {
        String termo = busca.getText().trim().toLowerCase(Locale.ROOT);
        visiveis = new ArrayList<>();
        DefaultTableModel m = UI.modelo(tabela);
        m.setRowCount(0);
        for (FichaTreino f : todas) {
            String texto = (f.alunoNome() + " " + f.nome() + " " + (f.objetivo() == null ? "" : f.objetivo())).toLowerCase(Locale.ROOT);
            if (!termo.isEmpty() && !texto.contains(termo)) continue;
            visiveis.add(f);
            m.addRow(new Object[]{f.alunoNome(), f.nome(), UI.texto(f.objetivo()),
                    f.frequenciaSemanal() == null ? "—" : f.frequenciaSemanal().toString(), UI.data(f.dataRevisao()),
                    rotuloStatus(f.status()), f.professorNome()});
        }
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
        if (FichaDialog.abrir(this, null, professor, false)) recarregar();
    }

    /** Abre a ficha selecionada: editável se for do professor logado, somente leitura se for de outro. */
    private void abrirSelecionada() {
        FichaTreino resumo = selecionada();
        if (resumo == null || !exigirProfessor()) return;
        try {
            // A lista traz só o resumo; os treinos e exercícios são buscados por inteiro ao abrir.
            FichaTreino completa = dao.buscar(resumo.id());
            if (completa == null) { UI.info(this, "Esta ficha não existe mais."); recarregar(); return; }
            boolean minha = completa.professorId() == professor.id();
            if (!minha) UI.info(this, "Esta ficha é do(a) professor(a) " + completa.professorNome() + ". Você pode apenas consultar.");
            if (FichaDialog.abrir(this, completa, professor, !minha)) recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
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
