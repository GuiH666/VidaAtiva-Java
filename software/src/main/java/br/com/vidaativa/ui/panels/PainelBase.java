package br.com.vidaativa.ui.panels;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Base das telas do menu: título + subtítulo no topo (com uma área opcional de botões à direita) e conteúdo embaixo.
 * <p>
 * Quem usa: o {@code DashboardFrame} guarda cada tela do menu como um {@code PainelBase}. As telas concretas são
 * {@link ResumoPanel}, {@link GerentePainel}, {@link EquipePanel}, {@code AlunosPanel}, {@code PagamentosPanel},
 * {@code FichasPanel} e {@code AvaliacoesPanel}.
 * <p>
 * Como criar uma tela nova: estenda esta classe, chame {@code super(titulo, subtitulo)} no construtor, adicione o
 * conteúdo com {@code add(componente, BorderLayout.CENTER)} (o topo já está ocupado pelo cabeçalho) e implemente
 * {@link #recarregar()}. Para pôr botões ou filtros no canto direito do cabeçalho use {@link #acoesDoTopo(JComponent)}.
 * Depois registre a tela no menu do {@code DashboardFrame}.
 */
public abstract class PainelBase extends JPanel {
    private final JPanel cabecalho = new JPanel(new BorderLayout(16, 0));
    private final JLabel titulo = new JLabel();
    private final JLabel subtitulo = new JLabel();

    /**
     * Cria o painel com o cabeçalho (título grande e subtítulo cinza) já posicionado no topo.
     *
     * @param titulo    título da tela (ex.: "Equipe")
     * @param subtitulo frase curta explicando para que serve a tela
     */
    protected PainelBase(String titulo, String subtitulo) {
        super(new BorderLayout(0, 18));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));
        this.titulo.setText(titulo);
        this.titulo.setFont(Theme.fonte(26f, Font.BOLD));
        this.subtitulo.setText(subtitulo);
        this.subtitulo.setFont(Theme.fonte(13.5f, Font.PLAIN));
        this.subtitulo.setForeground(Theme.MUTED);
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 3));
        textos.setOpaque(false);
        textos.add(this.titulo);
        textos.add(this.subtitulo);
        cabecalho.setOpaque(false);
        cabecalho.add(textos, BorderLayout.CENTER);
        add(cabecalho, BorderLayout.NORTH);
    }

    /** Coloca um componente (botões, seletor de mês...) no canto direito do cabeçalho. */
    protected void acoesDoTopo(JComponent c) {
        cabecalho.add(c, BorderLayout.EAST);
    }

    /** Esconde ou mostra o cabeçalho (título e subtítulo); telas com editor em tela cheia escondem enquanto o editor está aberto. */
    protected void mostrarCabecalho(boolean visivel) {
        cabecalho.setVisible(visivel);
    }

    /** Troca a frase do subtítulo (ex.: saudação que depende dos dados carregados). */
    protected void subtitulo(String texto) {
        subtitulo.setText(texto);
    }

    /**
     * Chamado toda vez que a tela é aberta, para buscar os dados atualizados no banco.
     * Cada tela decide o que recarregar (tabela, cartões, etc.). Deve tratar os próprios erros de banco, mostrando-os
     * com {@code UI.erro}, para não derrubar a janela principal.
     */
    public abstract void recarregar();
}
