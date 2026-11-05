package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.AvaliacaoDAO;
import br.com.vidaativa.dao.ProfessorDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.AvaliacaoFisica;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.service.Precificacao;
import br.com.vidaativa.ui.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Avaliações físicas" (menu do Professor).
 *
 * <p>Registra e exclui avaliações (peso, altura, % de gordura e observações) e mostra o histórico de
 * todos os alunos. O IMC não é digitado: é calculado por {@link AvaliacaoFisica#imc()}.
 * O aluno vê o próprio histórico na área dele, no site.</p>
 *
 * <p>Regra de plano: a avaliação física está incluída só no plano Premium
 * ({@link Precificacao#incluiAvaliacao}). Para outros planos o professor pode registrar mesmo assim,
 * depois de confirmar um aviso.</p>
 */
public class AvaliacoesPanel extends PainelBase {
    /** Usuário logado; usado para descobrir qual professor ele é. */
    private final Usuario usuario;
    private final AvaliacaoDAO dao = new AvaliacaoDAO();
    private final JTable tabela = UI.tabela("Data", "Aluno", "Peso (kg)", "Altura (m)", "IMC", "% gordura", "Professor");
    /** Professor logado (carregado na primeira vez em recarregar()); null se o usuário não é professor. */
    private Professor professor;
    /** Avaliações que estão na tabela; a linha selecionada é o mesmo índice desta lista. */
    private List<AvaliacaoFisica> lista = new ArrayList<>();

    public AvaliacoesPanel(Usuario usuario) {
        super("Avaliações físicas", "Registre a evolução do aluno. O aluno vê o histórico na área dele no site.");
        this.usuario = usuario;
        // --- Botões e tabela ---
        JButton nova = Theme.primary("Nova avaliação");
        nova.addActionListener(e -> nova());
        JButton excluir = Theme.danger("Excluir");
        excluir.addActionListener(e -> excluir());
        JPanel corpo = new JPanel(new BorderLayout(0, 10));
        corpo.setOpaque(false);
        corpo.add(UI.botoes(nova, excluir), BorderLayout.NORTH);
        corpo.add(UI.rolagem(tabela), BorderLayout.CENTER);
        add(corpo, BorderLayout.CENTER);
    }

    /** Recarrega o histórico de avaliações do banco e redesenha a tabela. */
    @Override
    public void recarregar() {
        try {
            if (professor == null) professor = new ProfessorDAO().buscarPorUsuarioId(usuario.id());
            lista = dao.listar();
            DefaultTableModel m = UI.modelo(tabela);
            m.setRowCount(0);
            for (AvaliacaoFisica v : lista) {
                m.addRow(new Object[]{UI.data(v.data()), v.alunoNome(), v.peso(), v.altura(),
                        v.imc() == null ? "—" : v.imc().toPlainString(),
                        v.percentualGordura() == null ? "—" : v.percentualGordura() + " %", v.professorNome()});
            }
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Só professores cadastrados podem registrar avaliações; mostra o motivo se o usuário não for. */
    private boolean exigirProfessor() {
        if (professor != null) return true;
        UI.erro(this, new IllegalStateException("Seu usuário não está cadastrado como professor. Peça ao gerente para cadastrar você na aba Professores."));
        return false;
    }

    /**
     * Converte o texto de um campo numérico (aceita vírgula ou ponto) em BigDecimal.
     *
     * @param obrigatorio se true e o campo estiver vazio, lança erro; se false, devolve null
     * @throws IllegalArgumentException com mensagem amigável se o campo estiver vazio (obrigatório) ou não for número
     */
    private static BigDecimal decimal(String texto, String campo, boolean obrigatorio) {
        String t = texto == null ? "" : texto.trim().replace(',', '.');
        if (t.isEmpty()) {
            if (obrigatorio) throw new IllegalArgumentException("Preencha o campo \"" + campo + "\".");
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + campo + "\" precisa ser um número (ex.: 78,5).");
        }
    }

    /** Abre o formulário de nova avaliação para um aluno ativo. */
    private void nova() {
        if (!exigirProfessor()) return;
        List<Aluno> alunos;
        try {
            alunos = new AlunoDAO().listar(false);
        } catch (Exception e) {
            UI.erro(this, e);
            return;
        }
        if (alunos.isEmpty()) { UI.erro(this, new IllegalStateException("Não há alunos ativos cadastrados.")); return; }

        // --- Campos do formulário ---
        // O combo mostra "Nome — plano X" para o professor ver o plano na hora de escolher.
        JComboBox<Aluno> aluno = new JComboBox<>(alunos.toArray(new Aluno[0]));
        aluno.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Component c = super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Aluno a) setText(a.nome() + "  —  plano " + a.plano());
                return c;
            }
        });
        DateField data = new DateField("Data da avaliação");
        data.setData(LocalDate.now());
        JTextField peso = new JTextField();
        JTextField altura = new JTextField();
        JTextField gordura = new JTextField();
        JTextArea obs = UI.areaTexto(4);

        // --- Montagem do formulário ---
        Formulario f = new Formulario();
        f.adicionar("Aluno *", aluno);
        f.adicionarPar("Data *", data, "Peso (kg) *", peso);
        f.adicionarPar("Altura (m) *", altura, "% de gordura", gordura);
        f.ajuda("Use vírgula ou ponto. Altura em metros, por exemplo 1,75.");
        f.adicionarGrande("Observações", new JScrollPane(obs));

        boolean ok = DialogoForm.abrir(this, "Nova avaliação física", f, "Salvar avaliação", () -> {
            Aluno a = (Aluno) aluno.getSelectedItem();
            // Desistiu = o professor respondeu "Não" ao aviso de plano; fecha sem salvar e sem mostrar erro.
            if (!Precificacao.incluiAvaliacao(a.plano())
                    && !UI.confirmar(this, "O plano " + a.plano() + " de " + a.nome() + " não inclui avaliação física (apenas Premium).\n\nRegistrar mesmo assim?"))
                throw new DialogoForm.Desistiu();
            dao.registrar(new AvaliacaoFisica(0, a.id(), professor.id(), a.nome(), professor.nome(), data.getDataObrigatoria(),
                    decimal(peso.getText(), "Peso", true), decimal(altura.getText(), "Altura", true),
                    decimal(gordura.getText(), "% de gordura", false), obs.getText().isBlank() ? null : obs.getText().trim()));
        }, 600, 480);
        if (ok) recarregar();
    }

    /** Exclui a avaliação selecionada (pede confirmação; não dá para desfazer). */
    private void excluir() {
        int r = tabela.getSelectedRow();
        if (r < 0 || r >= lista.size()) { UI.info(this, "Selecione uma avaliação na tabela."); return; }
        AvaliacaoFisica v = lista.get(r);
        if (!UI.confirmar(this, "Excluir a avaliação de " + v.alunoNome() + " do dia " + UI.data(v.data()) + "?\n\nEsta ação não pode ser desfeita.")) return;
        try {
            dao.excluir(v.id());
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }
}
