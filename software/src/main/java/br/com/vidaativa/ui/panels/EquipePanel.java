package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.FuncionarioDAO;
import br.com.vidaativa.dao.ProfessorDAO;
import br.com.vidaativa.model.Usuario;

import javax.swing.*;
import java.util.List;

/**
 * Tela do gerente: cadastro e manutenção de funcionários e professores (item "Equipe" do menu).
 * <p>
 * Só o gerente vê este item; a regra está em {@code DashboardFrame.montarMenu}. Tem duas abas, uma para funcionários e
 * outra para professores. As duas usam o mesmo componente, {@link PessoalPanel}; aqui só se liga cada aba ao DAO certo
 * ({@link FuncionarioDAO} ou {@link ProfessorDAO}).
 */
public class EquipePanel extends PainelBase {
    private final PessoalPanel funcionarios;
    private final PessoalPanel professores;

    /**
     * @param logado usuário logado (o gerente); seu id é repassado para que ele não consiga desativar a si mesmo
     */
    public EquipePanel(Usuario logado) {
        super("Equipe", "Somente o gerente cadastra funcionários e professores.");
        FuncionarioDAO fdao = new FuncionarioDAO();
        ProfessorDAO pdao = new ProfessorDAO();

        // --- Aba Funcionários ---
        // Classe anônima que adapta o FuncionarioDAO à interface Servico: converte cada Funcionario (model) em Pessoa (tela)
        // e repassa as demais chamadas ao DAO. O "true" indica que o cargo é obrigatório.
        funcionarios = new PessoalPanel("funcionário", "Cargo", true, new PessoalPanel.Servico() {
            public List<PessoalPanel.Pessoa> listar() throws Exception {
                return fdao.listar().stream().map(f -> new PessoalPanel.Pessoa(f.id(), f.usuarioId(), f.nome(), f.email(),
                        f.username(), f.cargo(), f.perfil(), f.ativo())).toList();
            }
            public void cadastrar(String n, String e, String u, String s, String funcao) throws Exception { fdao.cadastrar(n, e, u, s, funcao); }
            public void atualizar(int id, String n, String e, String funcao, String s) throws Exception { fdao.atualizar(id, n, e, funcao, s); }
            public void definirAtivo(int id, boolean a) throws Exception { fdao.definirAtivo(id, a); }
        }, logado.id());

        // --- Aba Professores ---
        // Mesma ideia, com o ProfessorDAO. A especialidade é opcional (false). O Professor não tem campo de perfil,
        // por isso o valor "PROFESSOR" é informado direto.
        professores = new PessoalPanel("professor", "Especialidade", false, new PessoalPanel.Servico() {
            public List<PessoalPanel.Pessoa> listar() throws Exception {
                return pdao.listar().stream().map(p -> new PessoalPanel.Pessoa(p.id(), p.usuarioId(), p.nome(), p.email(),
                        p.username(), p.especialidade(), "PROFESSOR", p.ativo())).toList();
            }
            public void cadastrar(String n, String e, String u, String s, String funcao) throws Exception { pdao.cadastrar(n, e, u, s, funcao); }
            public void atualizar(int id, String n, String e, String funcao, String s) throws Exception { pdao.atualizar(id, n, e, funcao, s); }
            public void definirAtivo(int id, boolean a) throws Exception { pdao.definirAtivo(id, a); }
        }, logado.id());

        // --- Abas ---
        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Funcionários", funcionarios);
        abas.addTab("Professores", professores);
        add(abas, java.awt.BorderLayout.CENTER);
    }

    /** Recarrega as duas abas (funcionários e professores) a cada vez que a tela é aberta. */
    @Override
    public void recarregar() {
        funcionarios.recarregar();
        professores.recarregar();
    }
}
