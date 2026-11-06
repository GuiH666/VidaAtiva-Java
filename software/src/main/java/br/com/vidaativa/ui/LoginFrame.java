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
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(painelMarca(), BorderLayout.WEST);

        // --- Lado do formulário ---
        JPanel direita = new JPanel(new BorderLayout(0, 26));
        direita.setBorder(BorderFactory.createEmptyBorder(52, 52, 40, 52));
        JLabel titulo = new JLabel("Bem-vindo de volta");
        titulo.setFont(Theme.fonte(28f, Font.BOLD));
        JLabel sub = UI.rotulo("Acesse o sistema interno da academia.");
        sub.setFont(Theme.fonte(13.5f, Font.PLAIN));
        JPanel cabecalho = new JPanel(new GridLayout(2, 1, 0, 6));
        cabecalho.setOpaque(false);
        cabecalho.add(titulo);
        cabecalho.add(sub);
        direita.add(cabecalho, BorderLayout.NORTH);

        Formulario form = new Formulario();
        form.adicionar("Usuário ou e-mail", login);
        form.adicionar("Senha", senha);
        direita.add(form.noTopo(), BorderLayout.CENTER);

        JPanel rodape = new JPanel(new BorderLayout(0, 14));
        rodape.setOpaque(false);
        entrar.setPreferredSize(new Dimension(10, 46));
        rodape.add(entrar, BorderLayout.NORTH);
        JLabel aviso = UI.rotulo("<html>Gerente, funcionário e professor entram aqui.<br>Alunos acessam pelo site da academia.</html>");
        aviso.setFont(Theme.fonte(12f, Font.PLAIN));
        rodape.add(aviso, BorderLayout.SOUTH);
        direita.add(rodape, BorderLayout.SOUTH);
        raiz.add(direita, BorderLayout.CENTER);

        setContentPane(raiz);
        getRootPane().setDefaultButton(entrar);
        entrar.addActionListener(e -> entrar());
        setSize(900, 560);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    /** Lado esquerdo: fundo em degradê verde-escuro, marca e três frases sobre o que o sistema faz. */
    private JComponent painelMarca() {
        JPanel p = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                int w = getWidth(), h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(10, 58, 34), w, h, new Color(6, 16, 11)));
                g2.fillRect(0, 0, w, h);
                // Círculos decorativos translúcidos.
                g2.setColor(Theme.alfa(Theme.GREEN, 22));
                g2.fillOval(w - 170, -90, 300, 300);
                g2.setColor(Theme.alfa(Theme.GREEN, 14));
                g2.fillOval(-120, h - 220, 330, 330);
                // Marca.
                g2.setColor(Theme.GREEN);
                g2.fill(new java.awt.geom.RoundRectangle2D.Float(44, 48, 46, 46, 14, 14));
                Icones.desenhar(g2, Icones.Tipo.HALTER, 51, 55, 32, new Color(6, 26, 14));
                g2.setColor(Theme.TEXT);
                g2.setFont(Theme.fonte(24f, Font.BOLD));
                g2.drawString("Vida Ativa", 104, 80);
                // Frase principal.
                g2.setFont(Theme.fonte(30f, Font.BOLD));
                g2.drawString("Cadastro, cobrança", 44, 232);
                g2.drawString("e treino num só lugar.", 44, 270);
                // Três vantagens.
                String[] itens = {"Alunos e mensalidades sempre em dia", "Fichas de treino claras para cada aluno", "Indicadores da academia em tempo real"};
                g2.setFont(Theme.fonte(14f, Font.PLAIN));
                for (int i = 0; i < itens.length; i++) {
                    int y = 330 + i * 38;
                    g2.setColor(Theme.alfa(Theme.GREEN, 50));
                    g2.fillOval(44, y - 17, 26, 26);
                    Icones.desenhar(g2, Icones.Tipo.VISTO, 48, y - 13, 18, Theme.GREEN);
                    g2.setColor(Theme.alfa(Theme.TEXT, 220));
                    g2.drawString(itens[i], 82, y + 1);
                }
                g2.dispose();
            }
        };
        p.setPreferredSize(new Dimension(420, 10));
        return p;
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
