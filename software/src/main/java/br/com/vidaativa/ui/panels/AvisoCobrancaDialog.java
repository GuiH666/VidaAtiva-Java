package br.com.vidaativa.ui.panels;

import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.service.Indicadores;
import br.com.vidaativa.service.MensagemCobranca;
import br.com.vidaativa.ui.DialogoForm;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.time.LocalDate;

/**
 * Janela "Avisar aluno", aberta pelo botão da tela Pagamentos.
 * <p>
 * Mostra o aluno, a cobrança e uma mensagem pronta ({@link MensagemCobranca}) que a recepção pode editar. Três ações:
 * abrir o WhatsApp com o texto preenchido, abrir o programa de e-mail com o texto preenchido ou copiar a mensagem.
 * O software não envia nada sozinho e não guarda registro do aviso: quem confere e envia é a pessoa da recepção.
 * Sem telefone (ou com telefone inválido) o botão do WhatsApp fica desligado; sem e-mail, o do e-mail.
 */
final class AvisoCobrancaDialog {
    private AvisoCobrancaDialog() { }

    /** Abre a janela e espera o usuário fechar. {@code aluno} pode ser null (então só dá para copiar a mensagem). */
    static void abrir(Component pai, Pagamento p, Aluno aluno, LocalDate hoje) {
        DialogoForm.abrir(pai, "Avisar aluno", painel(p, aluno, hoje), null, null, 640, 560);
    }

    /** Conteúdo da janela (separado para as capturas de demonstração). */
    static JComponent painel(Pagamento p, Aluno aluno, LocalDate hoje) {
        String nome = aluno != null ? aluno.nome() : p.alunoNome();
        String situacao = Indicadores.situacao(p, hoje);
        long dias = hoje.toEpochDay() - p.vencimento().toEpochDay();

        JLabel titulo = new JLabel(nome);
        titulo.setFont(Theme.fonte(19f, Font.BOLD));
        titulo.setForeground(Theme.TEXT);
        String resumoCobranca = "Mensalidade " + p.referencia().substring(5) + "/" + p.referencia().substring(0, 4) + "  ·  vence " + UI.data(p.vencimento())
                + "  ·  " + UI.moeda(p.valor()) + "  ·  "
                + (situacao.equals("ATRASADO") ? dias + (dias == 1 ? " dia de atraso" : " dias de atraso") : dias == 0 ? "vence hoje" : "vence em " + (-dias) + (dias == -1 ? " dia" : " dias"));
        JLabel cobranca = new JLabel(resumoCobranca);
        cobranca.setForeground(situacao.equals("ATRASADO") ? Theme.DANGER : Theme.WARN);
        String telefone = aluno == null ? null : aluno.telefone();
        String email = aluno == null ? null : aluno.email();
        JLabel contatos = new JLabel("WhatsApp: " + (MensagemCobranca.numeroWhatsApp(telefone) == null ? "sem número válido" : telefone)
                + "   ·   E-mail: " + (email == null || email.isBlank() ? "não informado" : email));
        contatos.setForeground(Theme.MUTED);

        JPanel cab = new JPanel(new GridLayout(3, 1, 0, 4));
        cab.setOpaque(false);
        cab.add(titulo);
        cab.add(cobranca);
        cab.add(contatos);

        JTextArea mensagem = UI.areaTexto(9);
        mensagem.setText(MensagemCobranca.texto(nome, p, hoje));
        mensagem.setCaretPosition(0);
        JPanel meio = new JPanel(new BorderLayout(0, 6));
        meio.setOpaque(false);
        meio.add(UI.rotulo("Mensagem (você pode editar antes de enviar)"), BorderLayout.NORTH);
        meio.add(UI.rolagem(mensagem), BorderLayout.CENTER);

        JButton whats = Theme.primary("Abrir WhatsApp");
        boolean temWhats = MensagemCobranca.numeroWhatsApp(telefone) != null;
        whats.setEnabled(temWhats);
        if (!temWhats) whats.setToolTipText("O aluno não tem um telefone válido cadastrado.");
        whats.addActionListener(e -> abrirLink(whats, MensagemCobranca.linkWhatsApp(telefone, mensagem.getText())));

        JButton mail = Theme.button("Enviar por e-mail");
        boolean temEmail = email != null && email.contains("@");
        mail.setEnabled(temEmail);
        if (!temEmail) mail.setToolTipText("O aluno não tem e-mail cadastrado.");
        mail.addActionListener(e -> abrirLink(mail, MensagemCobranca.linkEmail(email, MensagemCobranca.assunto(p, hoje), mensagem.getText())));

        JButton copiar = Theme.button("Copiar mensagem");
        copiar.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(mensagem.getText()), null);
                copiar.setText("Copiado!");
                Timer volta = new Timer(1800, x -> copiar.setText("Copiar mensagem"));
                volta.setRepeats(false);
                volta.start();
            } catch (IllegalStateException ex) {
                UI.erro(copiar, new IllegalStateException("Não foi possível copiar. Selecione o texto e use Ctrl+C."));
            }
        });
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        botoes.setOpaque(false);
        botoes.setBorder(BorderFactory.createEmptyBorder(0, -10, 0, 0));
        botoes.add(whats);
        botoes.add(mail);
        botoes.add(copiar);

        JLabel nota = UI.rotulo("Abre o WhatsApp ou o e-mail com o texto pronto. Quem envia é você: nada sai sozinho.");

        JPanel baixo = new JPanel(new BorderLayout(0, 10));
        baixo.setOpaque(false);
        baixo.add(botoes, BorderLayout.NORTH);
        baixo.add(nota, BorderLayout.CENTER);

        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setOpaque(false);
        raiz.add(cab, BorderLayout.NORTH);
        raiz.add(meio, BorderLayout.CENTER);
        raiz.add(baixo, BorderLayout.SOUTH);
        return raiz;
    }

    /** Abre o link no programa padrão do computador (navegador para WhatsApp, programa de e-mail para mailto). */
    private static void abrirLink(Component origem, String link) {
        try {
            if (link == null) throw new IllegalStateException("Não foi possível montar o link de envio.");
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IllegalStateException("Este computador não permite abrir o navegador pelo software. Use “Copiar mensagem”.");
            Desktop.getDesktop().browse(URI.create(link));
        } catch (Exception ex) {
            UI.erro(origem, ex instanceof IllegalStateException ? ex : new IllegalStateException("Não foi possível abrir o programa de envio. Use “Copiar mensagem”."));
        }
    }
}
