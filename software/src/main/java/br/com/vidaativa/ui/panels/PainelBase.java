package br.com.vidaativa.ui.panels;

import br.com.vidaativa.ui.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Base das telas do menu: título + subtítulo no topo e conteúdo embaixo.
 * <p>
 * Quem usa: o {@code DashboardFrame} guarda cada tela do menu como um {@code PainelBase}. As telas concretas são
 * {@link ResumoPanel}, {@link EquipePanel}, {@code AlunosPanel}, {@code PagamentosPanel}, {@code FichasPanel} e
 * {@code AvaliacoesPanel}.
 * <p>
 * Como criar uma tela nova: estenda esta classe, chame {@code super(titulo, subtitulo)} no construtor, adicione o
 * conteúdo com {@code add(componente, BorderLayout.CENTER)} (o topo já está ocupado pelo cabeçalho) e implemente
 * {@link #recarregar()}. Depois registre a tela no menu do {@code DashboardFrame}.
 */
public abstract class PainelBase extends JPanel {
    /**
     * Cria o painel com o cabeçalho (título grande e subtítulo cinza) já posicionado no topo.
     *
     * @param titulo    título da tela (ex.: "Equipe")
     * @param subtitulo frase curta explicando para que serve a tela
     */
    protected PainelBase(String titulo, String subtitulo) {
        // BorderLayout com 14 px de espaço vertical entre o cabeçalho (NORTH) e o conteúdo (CENTER) que a subclasse adicionar.
        super(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel t = new JLabel(titulo);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 24f));
        JLabel s = new JLabel(subtitulo);
        s.setForeground(Theme.MUTED);
        // Cabeçalho: duas linhas (título em cima, subtítulo embaixo). Fundo transparente para aparecer o fundo do tema.
        JPanel cab = new JPanel(new GridLayout(2, 1, 0, 2));
        cab.setOpaque(false);
        cab.add(t);
        cab.add(s);
        add(cab, BorderLayout.NORTH);
    }

    /**
     * Chamado toda vez que a tela é aberta, para buscar os dados atualizados no banco.
     * Cada tela decide o que recarregar (tabela, cartões, etc.). Deve tratar os próprios erros de banco, mostrando-os
     * com {@code UI.erro}, para não derrubar a janela principal.
     */
    public abstract void recarregar();
}
