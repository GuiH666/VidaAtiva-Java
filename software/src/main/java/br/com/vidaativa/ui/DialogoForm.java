package br.com.vidaativa.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Janela modal padrão: conteúdo no centro e botões Cancelar / Salvar embaixo.
 * <p>
 * Quem usa: todos os cadastros e edições do software (aluno, funcionário/professor, ficha de treino, avaliação física,
 * registro de pagamento). A tela monta o conteúdo (em geral um {@link Formulario}) e passa a ação de salvar; esta classe
 * cuida da janela, dos botões, das teclas e da exibição de erros.
 * <p>
 * Fluxo: {@link #abrir} mostra a janela e só retorna quando ela fecha. Se a ação de salvar lançar exceção, a mensagem
 * é mostrada e a janela continua aberta para o usuário corrigir; se terminar sem erro, a janela fecha.
 * Atalhos: Enter aciona o botão principal (botão padrão) e Esc fecha a janela sem salvar.
 */
public final class DialogoForm {
    // Classe utilitária: só tem o método estático abrir.
    private DialogoForm() { }

    /**
     * Ação do botão principal (normalmente: ler os campos e gravar no banco). Se lançar exceção, o erro é mostrado
     * por {@link UI#erro} e a janela continua aberta. A interface permite {@code throws Exception} para a tela
     * poder chamar DAOs ({@code SQLException}) e lançar {@code IllegalArgumentException} de validação sem try/catch.
     */
    public interface Acao { void executar() throws Exception; }

    /** Lance dentro da {@link Acao} quando o usuário desistir (ex.: respondeu "Não" a uma confirmação): a janela fica aberta, sem mensagem de erro. */
    public static final class Desistiu extends Exception {
        // Os dois "false" desligam a supressão e o stack trace: esta exceção é só um sinal de controle, não um erro, então não precisa do rastro.
        public Desistiu() { super("Desistiu", null, false, false); }
    }

    /**
     * Abre a janela modal e espera ela fechar (a chamada bloqueia até lá).
     *
     * @param pai         componente da tela que chamou; a janela abre centralizada sobre a janela dele (pode ser null)
     * @param titulo      título da janela
     * @param conteudo    o que aparece no centro; se for um {@link Formulario} sem linha grande, é colado no topo
     * @param textoSalvar texto do botão principal; null = janela só de consulta (apenas "Fechar")
     * @param salvar      ação executada ao clicar no botão principal (ignorada se {@code textoSalvar} for null)
     * @param largura     largura inicial em pixels
     * @param altura      altura inicial em pixels
     * @return true se o usuário salvou com sucesso; false se fechou/cancelou (ou se era só consulta)
     */
    public static boolean abrir(Component pai, String titulo, JComponent conteudo, String textoSalvar, Acao salvar,
                                int largura, int altura) {
        // A janela do pai vira "dona" do diálogo: ele fica sempre na frente dela e abre centralizado nela.
        Window dono = pai == null ? null : SwingUtilities.getWindowAncestor(pai);
        JDialog d = new JDialog(dono, titulo, Dialog.ModalityType.APPLICATION_MODAL);
        // Array de 1 posição: o lambda do botão não pode alterar uma variável local comum, mas pode alterar o conteúdo de um array.
        boolean[] salvou = {false};

        JPanel raiz = new JPanel(new BorderLayout(0, 14));
        raiz.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
        // Formulário sem linha grande iria se esticar na vertical e ficar com os campos espalhados; noTopo() mantém tudo no topo.
        raiz.add(conteudo instanceof Formulario f && !f.temLinhaGrande() ? f.noTopo() : conteudo, BorderLayout.CENTER);

        // --- Rodapé: botões à direita ---
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rodape.setOpaque(false);
        JButton cancelar = Theme.button(textoSalvar == null ? "Fechar" : "Cancelar");
        cancelar.addActionListener(e -> d.dispose());
        rodape.add(cancelar);
        if (textoSalvar != null) {
            JButton ok = Theme.primary(textoSalvar);
            ok.addActionListener(e -> {
                try {
                    salvar.executar();
                    salvou[0] = true;
                    d.dispose();
                } catch (Desistiu ignorado) {
                    // O usuário desistiu no meio da ação (ex.: respondeu "Não" a uma confirmação): continua na janela, sem erro.
                } catch (Exception ex) {
                    // Qualquer falha (validação ou banco) é mostrada e a janela fica aberta, preservando o que o usuário digitou.
                    UI.erro(d, ex);
                }
            });
            rodape.add(ok);
            // Botão padrão: a tecla Enter o aciona.
            d.getRootPane().setDefaultButton(ok);
        }
        raiz.add(rodape, BorderLayout.SOUTH);

        // --- Tecla Esc, tamanho e posição ---
        d.setContentPane(raiz);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(largura, altura);
        // Tamanho mínimo: 420 x 300 px, ou o tamanho inicial se este já for menor que isso.
        d.setMinimumSize(new Dimension(Math.min(largura, 420), Math.min(altura, 300)));
        d.setLocationRelativeTo(dono);
        // Como a janela é modal, setVisible(true) só retorna depois que ela fecha; aí já dá para ler o resultado.
        d.setVisible(true);
        return salvou[0];
    }
}
