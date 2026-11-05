package br.com.vidaativa.ui.panels;

import br.com.vidaativa.ui.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista + cadastro de funcionários ou professores (mesma tela, serviço diferente). Usada pelo gerente.
 * <p>
 * Aparece como uma das abas da tela Equipe ({@link EquipePanel} cria duas instâncias: uma para funcionários e outra
 * para professores). Mostra uma tabela com botões Novo, Editar e Desativar/Reativar; duplo clique em uma linha também edita.
 * <p>
 * Para servir aos dois cadastros sem duplicar código, esta classe não conhece os DAOs: ela fala com a interface
 * {@link Servico}, que o {@code EquipePanel} implementa chamando o {@code FuncionarioDAO} ou o {@code ProfessorDAO}.
 * O que muda entre os dois é só o nome da função ("Cargo" é obrigatório; "Especialidade" é opcional).
 * <p>
 * Regras: ninguém é excluído, apenas desativado (o cadastro é mantido e a pessoa não consegue mais entrar no software);
 * o gerente e o próprio usuário logado não podem ser desativados.
 * <p>
 * Observação: não é um {@link PainelBase} (não tem cabeçalho); o {@code EquipePanel} chama o {@link #recarregar()} dela.
 */
public class PessoalPanel extends JPanel {

    /**
     * Linha da tabela, já independente de ser funcionário ou professor.
     *
     * @param id        id na tabela do cadastro ({@code funcionarios} ou {@code professores}); é o usado em atualizar e definirAtivo
     * @param usuarioId id na tabela {@code usuarios} (o login); serve para saber se é o usuário logado
     * @param username  nome de login
     * @param funcao    cargo (funcionário) ou especialidade (professor); pode ser null
     * @param perfil    GERENTE, FUNCIONARIO ou PROFESSOR; usado para proteger o gerente contra desativação
     * @param ativo     se a pessoa está ativa
     */
    public record Pessoa(int id, int usuarioId, String nome, String email, String username, String funcao,
                         String perfil, boolean ativo) { }

    /**
     * Operações que a tela precisa; cada tipo de cadastro fornece a sua implementação (veja {@link EquipePanel}).
     * Todas podem lançar exceção: a tela mostra a mensagem ao usuário com {@link UI#erro}.
     */
    public interface Servico {
        /** Todas as pessoas, ativas e inativas, na ordem em que devem aparecer na tabela. */
        List<Pessoa> listar() throws Exception;
        /** Cria o cadastro com login. {@code funcao} é o cargo ou a especialidade. */
        void cadastrar(String nome, String email, String usuario, String senha, String funcao) throws Exception;
        /** Altera os dados. {@code novaSenha} vazia significa manter a senha atual; o login (usuário) nunca muda. */
        void atualizar(int id, String nome, String email, String funcao, String novaSenha) throws Exception;
        /** Ativa ou desativa a pessoa (e o login dela). */
        void definirAtivo(int id, boolean ativo) throws Exception;
    }

    private final String singular;       // "funcionário"
    private final String rotuloFuncao;   // "Cargo" ou "Especialidade"
    private final boolean funcaoObrigatoria;
    private final Servico servico;
    // id do usuário que está logado (o gerente); impede que ele desative a si mesmo.
    private final int usuarioLogadoId;
    private final JTable tabela;
    // Um só botão serve para as duas ações; o texto muda entre "Desativar" e "Reativar" conforme a linha selecionada.
    private final JButton btnAlternar = Theme.button("Desativar");
    // Cópia da lista exibida. A linha n da tabela corresponde ao item n desta lista (a tabela não é ordenável).
    private List<Pessoa> pessoas = new ArrayList<>();

    /**
     * @param singular          nome da pessoa no singular, em minúsculas, usado nos textos de botões e mensagens (ex.: "funcionário")
     * @param rotuloFuncao      nome do campo extra: "Cargo" ou "Especialidade"
     * @param funcaoObrigatoria true se esse campo não pode ficar vazio (cargo é obrigatório; especialidade não)
     * @param servico           quem lista, cadastra, atualiza e (des)ativa de verdade
     * @param usuarioLogadoId   id do usuário logado, para protegê-lo contra a própria desativação
     */
    public PessoalPanel(String singular, String rotuloFuncao, boolean funcaoObrigatoria, Servico servico, int usuarioLogadoId) {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        this.singular = singular;
        this.rotuloFuncao = rotuloFuncao;
        this.funcaoObrigatoria = funcaoObrigatoria;
        this.servico = servico;
        this.usuarioLogadoId = usuarioLogadoId;
        setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

        // --- Tabela ---
        tabela = UI.tabela("Nome", "E-mail", "Usuário", rotuloFuncao, "Status");
        // Coluna 4 = Status. O texto "Ativo" (definido em recarregar) é comparado aqui para escolher a cor.
        UI.colorirColuna(tabela, 4, s -> s.equals("Ativo") ? Theme.GREEN : Theme.MUTED);
        tabela.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editar(); }
        });
        // Ao mudar a linha selecionada, o botão passa a dizer "Desativar" ou "Reativar" conforme a situação dela.
        tabela.getSelectionModel().addListSelectionListener(e -> atualizarBotaoAlternar());

        // --- Botões ---
        JButton novo = Theme.primary("Novo " + singular);
        novo.addActionListener(e -> abrirDialogo(null));
        JButton editar = Theme.button("Editar");
        editar.addActionListener(e -> editar());
        btnAlternar.addActionListener(e -> alternarAtivo());
        add(UI.botoes(novo, editar, btnAlternar), BorderLayout.NORTH);
        add(UI.rolagem(tabela), BorderLayout.CENTER);
    }

    /**
     * Busca a lista atualizada e refaz a tabela. Chamado pelo {@link EquipePanel} ao abrir a tela e depois de cada alteração.
     * Erros são mostrados ao usuário (a tabela fica como estava).
     */
    public void recarregar() {
        try {
            pessoas = servico.listar();
            DefaultTableModel m = UI.modelo(tabela);
            m.setRowCount(0);
            for (Pessoa p : pessoas) {
                m.addRow(new Object[]{p.nome(), p.email(), p.username(), UI.texto(p.funcao()), p.ativo() ? "Ativo" : "Inativo"});
            }
            atualizarBotaoAlternar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Pessoa da linha selecionada, ou null se não há seleção. */
    private Pessoa selecionada() {
        int r = tabela.getSelectedRow();
        return r < 0 || r >= pessoas.size() ? null : pessoas.get(r);
    }

    /** Ajusta o texto do botão: "Reativar" se a linha selecionada está inativa; nos demais casos, "Desativar". */
    private void atualizarBotaoAlternar() {
        Pessoa p = selecionada();
        btnAlternar.setText(p != null && !p.ativo() ? "Reativar" : "Desativar");
    }

    /** Abre o diálogo de edição da linha selecionada; sem seleção, avisa o usuário. */
    private void editar() {
        Pessoa p = selecionada();
        if (p == null) { UI.info(this, "Selecione um " + singular + " na tabela."); return; }
        abrirDialogo(p);
    }

    /**
     * Desativa a pessoa selecionada (ou reativa, se já estiver inativa), depois de pedir confirmação.
     * Não há exclusão de verdade: o cadastro é mantido. Desativar bloqueia o login.
     */
    private void alternarAtivo() {
        Pessoa p = selecionada();
        if (p == null) { UI.info(this, "Selecione um " + singular + " na tabela."); return; }
        boolean desativar = p.ativo();
        // Proteção só na tela: o DAO não impede. Sem ela, o gerente poderia se trancar para fora do sistema.
        if (desativar && ("GERENTE".equals(p.perfil()) || p.usuarioId() == usuarioLogadoId)) {
            UI.erro(this, new IllegalStateException("Não é possível desativar o gerente nem o seu próprio usuário."));
            return;
        }
        String acao = desativar ? "Desativar" : "Reativar";
        String aviso = desativar ? "\n\n" + p.nome() + " não conseguirá mais entrar no software. O cadastro é mantido." : "";
        if (!UI.confirmar(this, acao + " " + singular + " " + p.nome() + "?" + aviso)) return;
        try {
            // Se estava ativo, desativar = true, então o novo valor de "ativo" é o contrário.
            servico.definirAtivo(p.id(), !desativar);
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /**
     * Abre o formulário de cadastro ou de edição.
     *
     * @param existente pessoa a editar, ou null para cadastrar uma nova
     */
    private void abrirDialogo(Pessoa existente) {
        boolean novo = existente == null;
        JTextField nome = new JTextField(novo ? "" : existente.nome());
        JTextField email = new JTextField(novo ? "" : existente.email());
        JTextField usuario = new JTextField(novo ? "" : existente.username());
        // O login (usuário) só pode ser definido no cadastro; depois disso não muda, porque o DAO não atualiza o username.
        usuario.setEditable(novo);
        JPasswordField senha = new JPasswordField();
        JTextField funcao = new JTextField(novo || existente.funcao() == null ? "" : existente.funcao());

        // --- Campos do formulário ---
        Formulario f = new Formulario();
        f.adicionar("Nome *", nome);
        f.adicionar("E-mail *", email);
        f.adicionar("Usuário (login) *", usuario);
        f.adicionar(novo ? "Senha *" : "Nova senha", senha);
        f.ajuda(novo ? "Mínimo de 6 caracteres. A pessoa usa o usuário e a senha para entrar no software."
                : "Deixe em branco para manter a senha atual. O usuário (login) não pode ser alterado.");
        f.adicionar(rotuloFuncao + (funcaoObrigatoria ? " *" : ""), funcao);

        String titulo = (novo ? "Novo " : "Editar ") + singular;
        // O bloco abaixo é a ação do botão Salvar. Se lançar exceção (validação ou banco), a janela continua aberta.
        boolean salvou = DialogoForm.abrir(this, titulo, f, "Salvar", () -> {
            String func = funcao.getText();
            if (funcaoObrigatoria && func.isBlank()) throw new IllegalArgumentException("Preencha o campo \"" + rotuloFuncao + "\".");
            // Na edição, senha vazia significa "manter a atual" (o DAO só troca a senha se vier preenchida).
            String pwd = new String(senha.getPassword());
            if (novo) servico.cadastrar(nome.getText(), email.getText(), usuario.getText(), pwd, func);
            else servico.atualizar(existente.id(), nome.getText(), email.getText(), func, pwd);
        }, 520, novo ? 420 : 430);
        // Só recarrega a tabela se algo foi gravado.
        if (salvou) recarregar();
    }
}
