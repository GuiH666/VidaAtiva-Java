package br.com.vidaativa.ui;

import br.com.vidaativa.api.ApiServer;
import br.com.vidaativa.model.Usuario;
import br.com.vidaativa.ui.panels.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Janela principal do software: barra no topo (marca, usuário logado, botão Sair), menu à esquerda e a tela
 * escolhida ao centro. É aberta pelo {@link LoginFrame} depois de um login bem-sucedido.
 * <p>
 * O menu muda conforme o perfil do usuário (é aqui que se decide quem vê o quê):
 * <ul>
 *   <li>GERENTE: Painel, Equipe, Alunos (com edição) e Pagamentos;</li>
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
    // Botões do menu na ordem de criação; usados para pintar o item selecionado e para abrir o primeiro ao iniciar.
    private final List<JButton> botoesMenu = new ArrayList<>();
    // 0 linhas = quantas forem precisas; 1 coluna: os botões ficam empilhados, com 8 px entre eles.
    private final JPanel menu = new JPanel(new GridLayout(0, 1, 0, 8));

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
        add(criarTopo(), BorderLayout.NORTH);

        // --- Menu lateral ---
        // O menu fica dentro de um painel BorderLayout, no NORTH, para os botões ficarem no topo com altura própria
        // em vez de esticarem até o fim da janela.
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER), BorderFactory.createEmptyBorder(18, 14, 14, 14)));
        lateral.add(menu, BorderLayout.NORTH);
        lateral.setPreferredSize(new Dimension(210, 10));
        add(lateral, BorderLayout.WEST);
        add(conteudo, BorderLayout.CENTER);

        montarMenu();
        setSize(1240, 780);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
        // doClick() simula o clique do usuário: assim a primeira tela já nasce aberta e o item fica destacado.
        if (!botoesMenu.isEmpty()) botoesMenu.get(0).doClick();
    }

    /**
     * Cria os itens do menu de acordo com o perfil do usuário. É a única barreira de acesso entre perfis na interface:
     * quem não tem o item no menu não consegue abrir a tela. Para dar ou tirar acesso, edite este método.
     */
    private void montarMenu() {
        switch (usuario.perfil().toUpperCase()) {
            case "GERENTE" -> {
                item("Painel", () -> new ResumoPanel(usuario));
                item("Equipe", () -> new EquipePanel(usuario));
                item("Alunos", () -> new AlunosPanel(true));
                item("Pagamentos", PagamentosPanel::new);
            }
            // Funcionário (recepção): cuida de alunos e cobranças, mas não vê a Equipe.
            case "FUNCIONARIO" -> {
                item("Painel", () -> new ResumoPanel(usuario));
                item("Alunos", () -> new AlunosPanel(true));
                item("Pagamentos", PagamentosPanel::new);
            }
            // Professor: consulta os alunos (false = somente leitura) e cuida das fichas e avaliações.
            case "PROFESSOR" -> {
                item("Painel", () -> new ResumoPanel(usuario));
                item("Alunos", () -> new AlunosPanel(false));
                item("Fichas de treino", () -> new FichasPanel(usuario));
                item("Avaliações físicas", () -> new AvaliacoesPanel(usuario));
            }
            // Perfil desconhecido: acesso mínimo, só o painel inicial.
            default -> item("Painel", () -> new ResumoPanel(usuario));
        }
    }

    /**
     * Registra um item do menu: cria o botão e guarda como construir a tela dele (sem construí-la agora).
     *
     * @param rotulo  texto do botão; também é a chave da tela nos mapas e o nome da carta no CardLayout (não repita)
     * @param fabrica quem cria a tela quando o item for aberto pela primeira vez
     */
    private void item(String rotulo, Supplier<PainelBase> fabrica) {
        fabricas.put(rotulo, fabrica);
        JButton b = Theme.button(rotulo);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.addActionListener(e -> abrir(rotulo, b));
        botoesMenu.add(b);
        menu.add(b);
    }

    /**
     * Mostra a tela do item clicado: destaca o botão, cria a tela se for a primeira vez, exibe e recarrega os dados.
     *
     * @param rotulo      rótulo do item de menu
     * @param selecionado botão que foi clicado (fica verde; os outros voltam ao cinza)
     */
    private void abrir(String rotulo, JButton selecionado) {
        // Pinta o botão ativo de verde e os demais de cinza. As cores são trocadas direto no botão (não pelo tema).
        for (JButton b : botoesMenu) {
            boolean ativo = b == selecionado;
            b.setBackground(ativo ? Theme.GREEN : Theme.SURFACE);
            b.setForeground(ativo ? Color.BLACK : Theme.TEXT);
        }
        PainelBase tela = telas.get(rotulo);
        // Criação preguiçosa (lazy): a tela só é construída no primeiro clique, o que evita consultas ao banco de telas que ninguém abriu.
        if (tela == null) {
            tela = fabricas.get(rotulo).get();
            telas.put(rotulo, tela);
            conteudo.add(tela, rotulo);
        }
        cartas.show(conteudo, rotulo);
        // Recarrega a cada abertura (não só na primeira), porque os dados podem ter mudado desde a última vez (ex.: cadastro feito em outra tela).
        tela.recarregar();
    }

    /**
     * Monta a faixa superior: marca à esquerda; à direita, o aviso da API, nome e perfil do usuário e o botão Sair.
     */
    private JComponent criarTopo() {
        JPanel topo = new JPanel(new BorderLayout());
        topo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        JLabel marca = new JLabel("VIDA ATIVA");
        marca.setFont(marca.getFont().deriveFont(Font.BOLD, 22f));
        marca.setForeground(Theme.GREEN);
        topo.add(marca, BorderLayout.WEST);

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        direita.setOpaque(false);
        // O aviso verde só aparece se a API do site foi iniciada junto com o software (veja Main e api.autostart).
        if (ApiServer.rodando()) {
            JLabel api = UI.rotulo("● API do site ligada (porta " + ApiServer.porta() + ")");
            api.setForeground(Theme.GREEN);
            direita.add(api);
        }
        direita.add(new JLabel(usuario.nome() + "  •  " + rotuloPerfil(usuario.perfil())));
        JButton sair = Theme.button("Sair");
        // Sair: fecha esta janela e volta ao login (sem encerrar o programa, pois ainda abre outra janela).
        sair.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
        direita.add(sair);
        topo.add(direita, BorderLayout.EAST);
        return topo;
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
}
