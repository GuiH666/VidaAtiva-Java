package br.com.vidaativa.ui;

import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.service.AutenticacaoService;

import javax.swing.*;
import java.awt.*;

/**
 * Tela de login do software: é a primeira janela que aparece (aberta por {@link Main}) e também a que volta
 * quando o usuário clica em Sair no {@link DashboardFrame}.
 * <p>
 * O usuário digita o nome de usuário OU o e-mail e a senha. Se estiverem corretos, esta janela fecha e abre o
 * {@link DashboardFrame} com o usuário logado.
 * <p>
 * Regra importante: só GERENTE, FUNCIONARIO e PROFESSOR entram. Alunos têm login válido, mas usam o site da academia,
 * então o software recusa o acesso deles (veja {@link AutenticacaoService#perfilPodeUsarSoftware}).
 */
public class LoginFrame extends JFrame {
    private final JTextField login = new JTextField();
    private final JPasswordField senha = new JPasswordField();
    private final JButton entrar = Theme.primary("Entrar");
    private final AutenticacaoService auth = new AutenticacaoService();

    /** Monta a janela (a tela só aparece quando quem criou chamar {@code setVisible(true)}). */
    public LoginFrame() {
        setTitle("Vida Ativa — Software de Gestão");
        // Fechar a tela de login encerra o programa inteiro (não há outra janela aberta nesse momento).
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // --- Cabeçalho: marca e subtítulo ---
        JPanel raiz = new JPanel(new BorderLayout(0, 22));
        raiz.setBorder(BorderFactory.createEmptyBorder(30, 36, 28, 36));

        JLabel titulo = new JLabel("VIDA ATIVA");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 30f));
        titulo.setForeground(Theme.GREEN);
        JLabel sub = UI.rotulo("Acesso ao sistema interno da academia");
        JPanel cabecalho = new JPanel(new GridLayout(2, 1));
        cabecalho.setOpaque(false);
        cabecalho.add(titulo);
        cabecalho.add(sub);
        raiz.add(cabecalho, BorderLayout.NORTH);

        // --- Campos de login e senha ---
        Formulario form = new Formulario();
        form.adicionar("Usuário ou e-mail", login);
        form.adicionar("Senha", senha);
        raiz.add(form.noTopo(), BorderLayout.CENTER);

        // --- Botão Entrar e aviso de quem usa esta tela ---
        JPanel rodape = new JPanel(new BorderLayout(0, 8));
        rodape.setOpaque(false);
        rodape.add(entrar, BorderLayout.NORTH);
        JLabel aviso = UI.rotulo("<html>Gerente, funcionário e professor entram aqui.<br>Alunos acessam pelo site da academia.</html>");
        aviso.setFont(aviso.getFont().deriveFont(11.5f));
        rodape.add(aviso, BorderLayout.SOUTH);
        raiz.add(rodape, BorderLayout.SOUTH);

        setContentPane(raiz);
        // Botão padrão: apertar Enter em qualquer campo equivale a clicar em Entrar.
        getRootPane().setDefaultButton(entrar);
        entrar.addActionListener(e -> entrar());
        setSize(460, 400);
        // Tamanho fixo: a tela de login é pequena e simples.
        setResizable(false);
        setLocationRelativeTo(null);
    }

    /**
     * Tenta fazer o login com o que foi digitado. Acionado pelo botão Entrar (ou pela tecla Enter).
     * <p>
     * Os erros de uso (campo vazio, senha errada, perfil sem acesso) são mostrados como aviso e a janela continua aberta.
     * Falha de banco aparece como erro. Dá para repetir quantas vezes quiser.
     */
    private void entrar() {
        String l = login.getText().trim();
        // getPassword() devolve char[] (e não String) por segurança; aqui convertemos só para conferir se foi preenchida e enviar ao serviço.
        String s = new String(senha.getPassword());
        if (l.isEmpty() || s.isEmpty()) {
            UI.erro(this, new IllegalArgumentException("Informe usuário e senha."));
            return;
        }
        // Desabilita o botão durante a conferência para evitar cliques duplos. O login roda na thread da tela, então a janela
        // pode "congelar" um instante se o banco demorar a responder.
        entrar.setEnabled(false);
        try {
            Usuario u = auth.login(l, s);
            // null = usuário inexistente, inativo ou senha errada. A mensagem é a mesma para os casos, de propósito.
            if (u == null) {
                UI.erro(this, new IllegalArgumentException("Usuário ou senha inválidos."));
                senha.setText("");
                return;
            }
            // A senha estava certa, mas é um aluno: ele só pode usar o site.
            if (!auth.perfilPodeUsarSoftware(u)) {
                UI.erro(this, new IllegalArgumentException("Alunos acessam a área do aluno pelo site da academia, não por este software."));
                senha.setText("");
                return;
            }
            // Sucesso: fecha o login e abre a janela principal. (O finally abaixo roda em todos os caminhos, inclusive nos return acima, e reabilita o botão.)
            dispose();
            new DashboardFrame(u).setVisible(true);
        } catch (Exception ex) {
            UI.erro(this, ex);
        } finally {
            entrar.setEnabled(true);
        }
    }
}
