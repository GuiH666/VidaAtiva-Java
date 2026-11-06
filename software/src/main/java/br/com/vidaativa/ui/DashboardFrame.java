package br.com.vidaativa.ui;

import br.com.vidaativa.api.ApiServer;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.panels.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Janela principal do software: menu lateral escuro (marca, itens, usuário logado e botão Sair) e, ao lado, a tela
 * escolhida. É aberta pelo {@link LoginFrame} depois de um login bem-sucedido.
 * <p>
 * O menu muda conforme o perfil do usuário (é aqui que se decide quem vê o quê):
 * <ul>
 *   <li>GERENTE: Painel (dashboard com indicadores e gráficos), Equipe, Alunos (com edição) e Pagamentos;</li>
 *   <li>FUNCIONARIO: Painel, Alunos (com edição) e Pagamentos;</li>
 *   <li>PROFESSOR: Painel, Alunos (somente consulta), Fichas de treino e Avaliações físicas;</li>
 *   <li>qualquer outro perfil: só o Painel.</li>
 * </ul>
 * Cada item do menu abre uma subclasse de {@link PainelBase}. As telas são criadas só na primeira vez que o item é
 * clicado e depois reaproveitadas; a cada clique elas recarregam os dados do banco.
 * <p>
 * Para criar uma nova tela: crie a classe filha de {@link PainelBase} e acrescente um {@code item(...)} no perfil certo
 * em {@link #montarMenu()}.
 */
public class DashboardFrame extends JFrame {
    private final Usuario usuario;
    // CardLayout mostra uma "carta" por vez no centro; o nome de cada carta é o rótulo do item de menu.
    private final CardLayout cartas = new CardLayout();
    private final JPanel conteudo = new JPanel(cartas);
    // Telas já criadas, por rótulo do menu (cache: cada tela é construída só uma vez).
    private final Map<String, PainelBase> telas = new HashMap<>();
    // Como criar cada tela (rótulo -> construtor). Só é chamado no primeiro clique no item.
    private final Map<String, Supplier<PainelBase>> fabricas = new HashMap<>();
    // Itens do menu na ordem de criação; usados para pintar o item selecionado e para abrir o primeiro ao iniciar.
    private final List<ItemMenu> itens = new ArrayList<>();
    private final JPanel menu = new JPanel(new GridLayout(0, 1, 0, 4));

    /**
     * Monta a janela para o usuário logado e já abre o primeiro item do menu (sempre o Painel).
     *
     * @param usuario usuário autenticado; o {@code perfil} dele define o menu
     */
    public DashboardFrame(Usuario usuario) {
        this.usuario = usuario;
        setTitle("Vida Ativa — " + rotuloPerfil(usuario.perfil()));
        // Fechar esta janela encerra o programa. (O botão Sair é diferente: volta para o login.)
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(criarLateral(), BorderLayout.WEST);
        conteudo.setBackground(Theme.BG);
        add(conteudo, BorderLayout.CENTER);

        montarMenu();
        setSize(1360, 840);
        setMinimumSize(new Dimension(1080, 680));
        setLocationRelativeTo(null);
        // Abre o primeiro item como se o usuário tivesse clicado: a primeira tela já nasce aberta e o item fica destacado.
        if (!itens.isEmpty()) itens.get(0).acionar();
    }

    /**
     * Cria os itens do menu de acordo com o perfil do usuário. É a única barreira de acesso entre perfis na interface:
     * quem não tem o item no menu não consegue abrir a tela. Para dar ou tirar acesso, edite este método.
     */
    private void montarMenu() {
        switch (usuario.perfil().toUpperCase()) {
            // O gerente vê o dashboard completo; os demais perfis veem o resumo simples.
            case "GERENTE" -> {
                item("Painel", Icones.Tipo.PAINEL, () -> new GerentePainel(usuario));
                item("Equipe", Icones.Tipo.EQUIPE, () -> new EquipePanel(usuario));
                item("Alunos", Icones.Tipo.ALUNOS, () -> new AlunosPanel(true));
                item("Pagamentos", Icones.Tipo.PAGAMENTOS, PagamentosPanel::new);
            }
            // Funcionário (recepção): cuida de alunos e cobranças, mas não vê a Equipe.
            case "FUNCIONARIO" -> {
                item("Painel", Icones.Tipo.PAINEL, () -> new ResumoPanel(usuario));
                item("Alunos", Icones.Tipo.ALUNOS, () -> new AlunosPanel(true));
                item("Pagamentos", Icones.Tipo.PAGAMENTOS, PagamentosPanel::new);
            }
            // Professor: consulta os alunos (false = somente leitura) e cuida das fichas e avaliações.
            case "PROFESSOR" -> {
                item("Painel", Icones.Tipo.PAINEL, () -> new ResumoPanel(usuario));
                item("Alunos", Icones.Tipo.ALUNOS, () -> new AlunosPanel(false));
                item("Fichas de treino", Icones.Tipo.FICHAS, () -> new FichasPanel(usuario));
                item("Avaliações físicas", Icones.Tipo.AVALIACOES, () -> new AvaliacoesPanel(usuario));
            }
            // Perfil desconhecido: acesso mínimo, só o painel inicial.
            default -> item("Painel", Icones.Tipo.PAINEL, () -> new ResumoPanel(usuario));
        }
    }

    /**
     * Registra um item do menu: cria o botão e guarda como construir a tela dele (sem construí-la agora).
     *
     * @param rotulo  texto do item; também é a chave da tela nos mapas e o nome da carta no CardLayout (não repita)
     * @param icone   ícone ao lado do texto
     * @param fabrica quem cria a tela quando o item for aberto pela primeira vez
     */
    private void item(String rotulo, Icones.Tipo icone, Supplier<PainelBase> fabrica) {
        fabricas.put(rotulo, fabrica);
        ItemMenu it = new ItemMenu(rotulo, icone, null);
        it.aoAcionar = () -> abrir(rotulo, it);
        itens.add(it);
        menu.add(it);
    }

    /**
     * Mostra a tela do item clicado: destaca o item, cria a tela se for a primeira vez, exibe e recarrega os dados.
     *
     * @param rotulo      rótulo do item de menu
     * @param selecionado item que foi clicado (fica verde; os outros voltam ao normal)
     */
    private void abrir(String rotulo, ItemMenu selecionado) {
        for (ItemMenu it : itens) it.definirAtivo(it == selecionado);
        PainelBase tela = telas.get(rotulo);
        // Criação preguiçosa (lazy): a tela só é construída no primeiro clique, o que evita consultas ao banco de telas que ninguém abriu.
        if (tela == null) {
            tela = criarTela(rotulo, fabricas.get(rotulo));
            telas.put(rotulo, tela);
            conteudo.add(tela, rotulo);
        }
        cartas.show(conteudo, rotulo);
        // Recarrega a cada abertura (não só na primeira), porque os dados podem ter mudado desde a última vez.
        tela.recarregar();
    }

    /**
     * Cria a tela de um item do menu. Existe como ponto de extensão: por padrão só chama a fábrica registrada; os testes
     * visuais (sem banco de dados) substituem as telas por versões com dados de demonstração.
     */
    protected PainelBase criarTela(String rotulo, Supplier<PainelBase> fabrica) {
        return fabrica.get();
    }

    /** Monta o menu lateral: marca no topo, itens no meio e, embaixo, a API, o usuário e o botão Sair. */
    private JComponent criarLateral() {
        JPanel lateral = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(Theme.SIDEBAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.BORDER);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        lateral.setOpaque(false);
        lateral.setPreferredSize(new Dimension(248, 10));
        lateral.setBorder(BorderFactory.createEmptyBorder(22, 16, 18, 16));

        // --- Marca ---
        JPanel marca = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(Theme.GREEN);
                g2.fill(new RoundRectangle2D.Float(2, 2, 38, 38, 12, 12));
                Icones.desenhar(g2, Icones.Tipo.HALTER, 8, 8, 26, new Color(6, 26, 14));
                g2.dispose();
            }
        };
        marca.setOpaque(false);
        marca.setPreferredSize(new Dimension(10, 48));
        JLabel nome = new JLabel("<html><span style='font-size:16pt'><b>Vida Ativa</b></span><br><span style='color:#8A9591'>Gestão da academia</span></html>");
        nome.setBorder(BorderFactory.createEmptyBorder(0, 52, 0, 0));
        marca.add(nome, BorderLayout.CENTER);

        JPanel topo = new JPanel(new BorderLayout(0, 24));
        topo.setOpaque(false);
        topo.add(marca, BorderLayout.NORTH);
        menu.setOpaque(false);
        JLabel secao = new JLabel("MENU");
        secao.setFont(Theme.fonte(11f, Font.BOLD));
        secao.setForeground(Theme.alfa(Theme.MUTED, 200));
        secao.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        JPanel navegacao = new JPanel(new BorderLayout(0, 8));
        navegacao.setOpaque(false);
        navegacao.add(secao, BorderLayout.NORTH);
        navegacao.add(menu, BorderLayout.CENTER);
        topo.add(navegacao, BorderLayout.CENTER);
        lateral.add(topo, BorderLayout.NORTH);

        // --- Rodapé: aviso da API, usuário e Sair ---
        JPanel rodape = new JPanel(new BorderLayout(0, 12));
        rodape.setOpaque(false);
        // O aviso verde só aparece se a API do site foi iniciada junto com o software (veja Main e api.autostart).
        if (ApiServer.rodando()) {
            JLabel api = new JLabel("●  API do site ligada (porta " + ApiServer.porta() + ")");
            api.setFont(Theme.fonte(12f, Font.PLAIN));
            api.setForeground(Theme.GREEN);
            api.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
            rodape.add(api, BorderLayout.NORTH);
        }
        rodape.add(cartaoUsuario(), BorderLayout.CENTER);
        // Sair: fecha esta janela e volta ao login (sem encerrar o programa, pois ainda abre outra janela).
        ItemMenu sair = new ItemMenu("Sair", Icones.Tipo.SAIR, null);
        sair.aoAcionar = () -> {
            dispose();
            new LoginFrame().setVisible(true);
        };
        rodape.add(sair, BorderLayout.SOUTH);
        lateral.add(rodape, BorderLayout.SOUTH);
        return lateral;
    }

    /** Bloco com a inicial do nome num círculo verde, o nome e o perfil. */
    private JComponent cartaoUsuario() {
        String inicial = usuario.nome().isBlank() ? "?" : usuario.nome().trim().substring(0, 1).toUpperCase();
        JPanel p = new JPanel(new BorderLayout(0, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.suave(g);
                g2.setColor(Theme.PANEL);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.setColor(Theme.alfa(Theme.GREEN, 50));
                g2.fill(new Ellipse2D.Float(12, 11, 34, 34));
                g2.setColor(Theme.GREEN);
                g2.setFont(Theme.fonte(15f, Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(inicial, 12 + (34 - fm.stringWidth(inicial)) / 2f, 11 + (34 - fm.getHeight()) / 2f + fm.getAscent());
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 58));
        JLabel texto = new JLabel("<html><b>" + escapar(primeiroNome(usuario.nome())) + "</b><br><span style='color:#8A9591'>" + rotuloPerfil(usuario.perfil()) + "</span></html>");
        texto.setBorder(BorderFactory.createEmptyBorder(0, 58, 0, 8));
        p.add(texto, BorderLayout.CENTER);
        return p;
    }

    private static String primeiroNome(String nome) {
        String n = nome == null ? "" : nome.trim();
        int e = n.indexOf(' ');
        return e > 0 ? n.substring(0, e) : n;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Converte o perfil do banco (ex.: "FUNCIONARIO") no texto exibido ("Funcionário"). Perfis desconhecidos voltam como vieram. */
    private static String rotuloPerfil(String p) {
        return switch (p.toUpperCase()) {
            case "GERENTE" -> "Gerente";
            case "FUNCIONARIO" -> "Funcionário";
            case "PROFESSOR" -> "Professor";
            default -> p;
        };
    }

    /** Item do menu lateral: ícone e texto, fundo ao passar o mouse e barra verde quando é o item aberto. */
    private static final class ItemMenu extends JComponent {
        private final String rotulo;
        private final Icones.Tipo icone;
        private boolean ativo, sobre;
        /** Ação do clique (definida depois da criação porque depende do próprio item). */
        Runnable aoAcionar;

        ItemMenu(String rotulo, Icones.Tipo icone, Runnable acao) {
            this.rotulo = rotulo;
            this.icone = icone;
            this.aoAcionar = acao;
            setPreferredSize(new Dimension(10, 44));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { acionar(); }
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { sobre = false; repaint(); }
            });
            // Teclado: Enter ou espaço abrem o item quando ele está com o foco.
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "abrir");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "abrir");
            getActionMap().put("abrir", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { acionar(); }
            });
        }

        void acionar() { if (aoAcionar != null) aoAcionar.run(); }

        void definirAtivo(boolean a) { ativo = a; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.suave(g);
            int w = getWidth(), h = getHeight();
            if (ativo) {
                g2.setColor(Theme.alfa(Theme.GREEN, 30));
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 22, 22));
                g2.setColor(Theme.GREEN);
                g2.fill(new RoundRectangle2D.Float(0, h / 2f - 10, 4, 20, 4, 4));
            } else if (sobre) {
                g2.setColor(Theme.HOVER);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 22, 22));
            }
            Color cor = ativo ? Theme.GREEN : sobre ? Theme.TEXT : Theme.MUTED;
            Icones.desenhar(g2, icone, 16, (h - 20) / 2.0, 20, cor);
            g2.setFont(Theme.fonte(14f, ativo ? Font.BOLD : Font.PLAIN));
            g2.setColor(ativo ? Theme.TEXT : cor);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(rotulo, 48, (h - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
        }
    }
}
