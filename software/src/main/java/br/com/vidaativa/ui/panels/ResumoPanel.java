package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.ResumoDAO;
import br.com.vidaativa.model.Resumo;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;

import javax.swing.*;
import java.awt.*;

/**
 * Painel inicial (item "Painel" do menu): cartões com os números da academia. É a primeira tela que todo perfil vê.
 * <p>
 * Os cartões mudam conforme o perfil do usuário logado:
 * <ul>
 *   <li>GERENTE: alunos ativos, funcionários, professores, receita do mês, cobranças pendentes e em atraso;</li>
 *   <li>FUNCIONARIO: alunos ativos, cobranças pendentes e em atraso;</li>
 *   <li>PROFESSOR (e qualquer outro perfil): alunos ativos, fichas ativas e professores.</li>
 * </ul>
 * Os números vêm do {@link ResumoDAO}. Veja em {@link Resumo} o que cada contagem considera.
 */
public class ResumoPanel extends PainelBase {
    private final Usuario usuario;
    // Grade de 3 colunas; o número de linhas cresce conforme a quantidade de cartões (0 = automático).
    private final JPanel cartoes = new JPanel(new GridLayout(0, 3, 14, 14));

    /**
     * @param usuario usuário logado: o nome aparece na saudação e o perfil define quais cartões aparecem
     */
    public ResumoPanel(Usuario usuario) {
        super("Painel inicial", "Olá, " + usuario.nome() + "! Resumo da academia.");
        this.usuario = usuario;
        cartoes.setOpaque(false);
        // Os cartões vão no NORTH de um painel envoltório: assim têm altura própria em vez de esticarem até o fim da tela.
        JPanel envoltorio = new JPanel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(cartoes, BorderLayout.NORTH);
        add(envoltorio, BorderLayout.CENTER);
    }

    /**
     * Refaz os cartões com os números atuais do banco.
     * Se o banco falhar, mostra a mensagem de erro e um único cartão vermelho "Sem conexão".
     */
    @Override
    public void recarregar() {
        cartoes.removeAll();
        try {
            // O carregar() também marca como ATRASADO as cobranças pendentes já vencidas, antes de contar.
            Resumo r = new ResumoDAO().carregar();
            // Os três ramos do switch montam conjuntos diferentes de cartões; ver a lista de perfis no Javadoc da classe.
            switch (usuario.perfil().toUpperCase()) {
                case "GERENTE" -> {
                    cartoes.add(cartao("Alunos ativos", String.valueOf(r.alunosAtivos()), Theme.GREEN));
                    cartoes.add(cartao("Funcionários", String.valueOf(r.funcionariosAtivos()), Theme.GREEN));
                    cartoes.add(cartao("Professores", String.valueOf(r.professoresAtivos()), Theme.GREEN));
                    cartoes.add(cartao("Receita do mês", UI.moeda(r.receitaMes()), Theme.GREEN));
                    cartoes.add(cartao("Cobranças pendentes", String.valueOf(r.pagamentosPendentes()), Theme.WARN));
                    // Atrasos ficam vermelhos para chamar atenção; com zero atrasos, ficam verdes.
                    cartoes.add(cartao("Cobranças em atraso", String.valueOf(r.pagamentosAtrasados()), r.pagamentosAtrasados() > 0 ? Theme.DANGER : Theme.GREEN));
                }
                case "FUNCIONARIO" -> {
                    cartoes.add(cartao("Alunos ativos", String.valueOf(r.alunosAtivos()), Theme.GREEN));
                    cartoes.add(cartao("Cobranças pendentes", String.valueOf(r.pagamentosPendentes()), Theme.WARN));
                    cartoes.add(cartao("Cobranças em atraso", String.valueOf(r.pagamentosAtrasados()), r.pagamentosAtrasados() > 0 ? Theme.DANGER : Theme.GREEN));
                }
                // Professor e qualquer outro perfil.
                default -> {
                    cartoes.add(cartao("Alunos ativos", String.valueOf(r.alunosAtivos()), Theme.GREEN));
                    cartoes.add(cartao("Fichas ativas", String.valueOf(r.fichasAtivas()), Theme.GREEN));
                    cartoes.add(cartao("Professores", String.valueOf(r.professoresAtivos()), Theme.GREEN));
                }
            }
        } catch (Exception e) {
            UI.erro(this, e);
            cartoes.add(cartao("Banco de dados", "Sem conexão", Theme.DANGER));
        }
        // Depois de adicionar/remover componentes, o Swing precisa recalcular o layout (revalidate) e redesenhar (repaint).
        cartoes.revalidate();
        cartoes.repaint();
    }

    /**
     * Monta um cartão: o título pequeno em cinza em cima e o valor grande embaixo.
     *
     * @param titulo nome do indicador (ex.: "Alunos ativos")
     * @param valor  valor já formatado como texto
     * @param cor    cor do valor (verde = normal, amarelo = atenção, vermelho = problema)
     */
    private JPanel cartao(String titulo, String valor, Color cor) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 4));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        JLabel t = new JLabel(titulo);
        t.setForeground(Theme.MUTED);
        JLabel v = new JLabel(valor);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 30f));
        v.setForeground(cor);
        p.add(t);
        p.add(v);
        return p;
    }
}
