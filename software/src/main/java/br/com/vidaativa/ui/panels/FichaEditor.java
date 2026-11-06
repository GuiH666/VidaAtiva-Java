package br.com.vidaativa.ui.panels;

import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.FichaTreino;
import br.com.vidaativa.model.Professor;
import br.com.vidaativa.model.Treino;
import br.com.vidaativa.service.BibliotecaExercicios;
import br.com.vidaativa.service.BibliotecaExercicios.ModeloFicha;
import br.com.vidaativa.service.Precificacao;
import br.com.vidaativa.ui.DateField;
import br.com.vidaativa.ui.Formulario;
import br.com.vidaativa.ui.Icones;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;
import br.com.vidaativa.ui.componentes.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Editor de ficha de treino em tela cheia (abre no lugar da lista de {@link FichasPanel}; não é uma janelinha).
 *
 * <p>Três áreas lado a lado:</p>
 * <ul>
 *   <li><b>Dados da ficha</b> (esquerda): aluno, nome, objetivo e dias por semana em pílulas, datas, situação e observações;</li>
 *   <li><b>Treinos</b> (centro): uma aba por treino (A, B, C...), cada uma com a lista de exercícios em cartões ({@link TreinoPanel});</li>
 *   <li><b>Biblioteca</b> (direita): catálogo de exercícios para adicionar com um clique ({@link BibliotecaPanel}).</li>
 * </ul>
 * Há também modelos de ficha prontos (botão "Modelos"), duplicar treino e, na lista, copiar a ficha inteira para outro aluno.
 *
 * <p>Regras (as mesmas da versão com janela):</p>
 * <ul>
 *   <li>O aluno de uma ficha existente não pode ser trocado.</li>
 *   <li>Em modo somente leitura (ficha de outro professor) tudo fica travado e não há botão Salvar.</li>
 *   <li>Salvar como "Ativa" um aluno de plano sem ficha incluída pede confirmação ({@link Precificacao#incluiFicha}).</li>
 *   <li>Quem grava, valida (ficha ativa precisa de exercício) e desativa as outras fichas ativas é o {@code FichaTreinoDAO.salvar}.</li>
 * </ul>
 */
class FichaEditor extends JPanel {
    /** Quem grava a ficha (normalmente {@code FichaTreinoDAO::salvar}); separado para a tela poder ser testada sem banco. */
    interface Salvador { void salvar(FichaTreino ficha) throws Exception; }

    private static final List<String> OBJETIVOS = List.of("Hipertrofia", "Emagrecimento", "Condicionamento", "Força", "Reabilitação", "Saúde e bem-estar");
    private static final Map<String, String> SITUACOES = Map.of(
            "ATIVA", "O aluno vê esta ficha no site. As outras fichas ativas dele passam a inativas.",
            "RASCUNHO", "Só o professor vê. Use enquanto ainda está montando.",
            "INATIVA", "Fica guardada como histórico; o aluno não vê.");

    private final FichaTreino existente;
    private final boolean novo, somenteLeitura;
    private final Professor professor;
    private final Salvador salvador;
    private final Runnable aoSair, aoSalvar;

    private final JComboBox<Aluno> aluno;
    private final JTextField nome = new CampoDica("Ex.: Hipertrofia — ABC");
    private final Opcoes<String> objetivo = Opcoes.texto(OBJETIVOS, false);
    private final JTextField outroObjetivo = new CampoDica("Ou digite outro objetivo");
    private final Opcoes<Integer> dias = new Opcoes<>(List.of(1, 2, 3, 4, 5, 6, 7), String::valueOf, false);
    private final DateField inicio = new DateField("Início");
    private final DateField revisao = new DateField("Revisão");
    private final Opcoes<String> situacao = new Opcoes<>(List.of("ATIVA", "RASCUNHO", "INATIVA"),
            s -> switch (s) { case "ATIVA" -> "Ativa"; case "RASCUNHO" -> "Rascunho"; default -> "Inativa"; }, false);
    private final JLabel descSituacao = new JLabel();
    private final JTextArea obs = UI.areaTexto(3);
    private final Chip chipSituacao = new Chip("", Theme.GREEN);

    private final List<TreinoPanel> treinos = new ArrayList<>();
    private final CardLayout cartas = new CardLayout();
    private final JPanel areaTreinos = new JPanel(cartas);
    private final JPanel abas = new JPanel(new FluxoLayout(FlowLayout.LEFT, 8, 6));
    private final JLabel resumo = new JLabel(" ");
    private final JComponent biblioteca;
    private final CardLayout trocadorEsquerda = new CardLayout();
    private final JPanel cartasEsquerda = new JPanel(trocadorEsquerda);
    private final Opcoes<String> seletorEsquerda = new Opcoes<>(List.of("dados", "biblioteca"),
            s -> s.equals("dados") ? "Dados da ficha" : "Biblioteca de exercícios", false);
    private JComponent cartaoDados;
    private int atual = 0;
    private boolean sujo = false;

    /**
     * @param base          ficha a editar; ou, com {@code copia}, a ficha usada como ponto de partida; null para uma ficha em branco
     * @param copia         true para criar uma NOVA ficha com os mesmos treinos de {@code base} (para outro aluno, por exemplo)
     * @param professor     professor logado; vira o autor da ficha quando ela é nova
     * @param alunos        alunos que podem receber a ficha
     * @param somenteLeitura true para apenas consultar
     * @param aoSair        chamado ao voltar para a lista (sem salvar)
     * @param aoSalvar      chamado depois de salvar com sucesso
     * @param salvador      quem grava a ficha
     */
    FichaEditor(FichaTreino base, boolean copia, Professor professor, List<Aluno> alunos, boolean somenteLeitura,
                Runnable aoSair, Runnable aoSalvar, Salvador salvador) {
        super(new BorderLayout(0, 18));
        setOpaque(false);
        this.existente = base;
        this.novo = base == null || copia;
        this.somenteLeitura = somenteLeitura;
        this.professor = professor;
        this.aoSair = aoSair;
        this.aoSalvar = aoSalvar;
        this.salvador = salvador;

        // --- Aluno ---
        aluno = new JComboBox<>(alunos.toArray(new Aluno[0]));
        aluno.setRenderer(new ListCellRenderer<Aluno>() {
            private final DefaultListCellRenderer base = new DefaultListCellRenderer();
            @Override public Component getListCellRendererComponent(JList<? extends Aluno> l, Aluno a, int i, boolean sel, boolean foco) {
                JLabel c = (JLabel) base.getListCellRendererComponent(l, a, i, sel, false);
                c.setText(a == null ? "Selecione o aluno…" : a.nome() + "  ·  plano " + a.plano());
                c.setBorder(BorderFactory.createEmptyBorder(i < 0 ? 6 : 8, 10, i < 0 ? 6 : 8, 10));
                c.setOpaque(true);
                c.setBackground(i < 0 ? Theme.SURFACE : sel ? Theme.GREEN_DARK : Theme.PANEL);
                c.setForeground(a == null ? Theme.MUTED : sel && i >= 0 ? Color.WHITE : Theme.TEXT);
                return c;
            }
        });
        aluno.setSelectedIndex(-1);
        if (base != null && !copia) {
            for (Aluno a : alunos) if (a.id() == base.alunoId()) aluno.setSelectedItem(a);
            aluno.setEnabled(false); // a ficha não troca de aluno depois de criada
        } else if (alunos.size() == 1) aluno.setSelectedIndex(0);

        // --- Dados gerais ---
        if (base != null) {
            nome.setText(copia ? base.nome() + " (cópia)" : base.nome());
            if (base.objetivo() != null) {
                if (OBJETIVOS.contains(base.objetivo())) objetivo.setSelecionado(base.objetivo());
                else outroObjetivo.setText(base.objetivo());
            }
        } else objetivo.setSelecionado("Hipertrofia");
        // Objetivo: escolher uma pílula limpa o campo "outro"; digitar no campo "outro" desmarca a pílula.
        objetivo.aoMudar(o -> { if (o != null) outroObjetivo.setText(""); });
        outroObjetivo.getDocument().addDocumentListener(simples(() -> { if (!outroObjetivo.getText().isBlank()) objetivo.setSelecionado(null); }));

        dias.setSelecionado(base == null || base.frequenciaSemanal() == null ? 3 : base.frequenciaSemanal());
        inicio.setData(base == null || copia ? LocalDate.now() : base.dataInicio());
        revisao.setData(base == null || copia ? LocalDate.now().plusDays(60) : base.dataRevisao());
        situacao.setSelecionado(base == null ? "ATIVA" : copia ? "RASCUNHO" : base.status());
        situacao.aoMudar(s -> atualizarSituacao());
        if (base != null && base.observacoes() != null) obs.setText(base.observacoes());
        descSituacao.setFont(Theme.fonte(12f, Font.PLAIN));
        descSituacao.setForeground(Theme.MUTED);

        // --- Treinos ---
        areaTreinos.setOpaque(false);
        if (base != null) for (Treino t : base.treinos()) criarTreino(copia ? new Treino(0, t.nome(), t.diaSemana(), t.descricao(), t.exercicios()) : t);
        if (treinos.isEmpty()) criarTreino(null);

        biblioteca = new BibliotecaPanel(m -> { if (!treinos.isEmpty()) treinos.get(atual).adicionar(m); });

        add(criarTopo(), BorderLayout.NORTH);
        add(criarCorpo(), BorderLayout.CENTER);

        // --- Estado inicial ---
        atualizarSituacao();
        reconstruirAbas();
        selecionar(0);
        if (somenteLeitura) travar();
        // Qualquer mudança depois deste ponto conta como "alterado" (pede confirmação ao voltar sem salvar).
        marcarMudancas();
        sujo = false;
    }

    // ------------------------------------------------------------------ montagem

    private JComponent criarTopo() {
        JButton voltar = Theme.ghost("Fichas");
        voltar.setIcon(Icones.icone(Icones.Tipo.SETA_ESQ, 16, Theme.MUTED));
        voltar.setIconTextGap(6);
        voltar.addActionListener(e -> voltar());

        String titulo = somenteLeitura ? "Ficha de treino (consulta)" : existente == null ? "Nova ficha de treino" : novo ? "Copiar ficha de treino" : "Editar ficha de treino";
        JLabel t = new JLabel(titulo);
        t.setFont(Theme.fonte(24f, Font.BOLD));
        JLabel sub = UI.rotulo(somenteLeitura && existente != null ? "Ficha de " + existente.professorNome() + ". Você pode apenas consultar."
                : "Dados à esquerda, treinos no centro. A biblioteca adiciona exercícios com um clique.");
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 3));
        textos.setOpaque(false);
        JPanel linhaTitulo = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        linhaTitulo.setOpaque(false);
        linhaTitulo.add(t);
        linhaTitulo.add(chipSituacao);
        textos.add(linhaTitulo);
        textos.add(sub);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel esquerda = new JPanel(new BorderLayout(10, 0));
        esquerda.setOpaque(false);
        esquerda.add(voltar, BorderLayout.WEST);
        esquerda.add(textos, BorderLayout.CENTER);

        // Botões da direita.
        JButton modelos = Theme.button("Modelos");
        modelos.setIcon(Icones.icone(Icones.Tipo.COPIAR, 16, Theme.TEXT));
        modelos.setIconTextGap(8);
        modelos.setToolTipText("Começar de uma ficha pronta (A/B/C)");
        modelos.addActionListener(e -> menuModelos(modelos));
        JButton cancelar = Theme.button(somenteLeitura ? "Fechar" : "Cancelar");
        cancelar.addActionListener(e -> voltar());
        JButton salvar = Theme.primary("Salvar ficha");
        salvar.addActionListener(e -> salvar());
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        direita.setOpaque(false);
        if (!somenteLeitura) direita.add(modelos);
        direita.add(cancelar);
        if (!somenteLeitura) direita.add(salvar);

        JPanel topo = new JPanel(new BorderLayout(16, 0));
        topo.setOpaque(false);
        topo.add(esquerda, BorderLayout.CENTER);
        topo.add(direita, BorderLayout.EAST);
        return topo;
    }

    private JComponent criarCorpo() {
        JPanel corpo = new JPanel(new BorderLayout(18, 0));
        corpo.setOpaque(false);
        corpo.add(criarColunaEsquerda(), BorderLayout.WEST);
        corpo.add(criarAreaTreinos(), BorderLayout.CENTER);
        return corpo;
    }

    /** Coluna da esquerda: alterna entre "Dados da ficha" e "Biblioteca de exercícios" (as duas não cabem juntas ao lado dos treinos). */
    private JComponent criarColunaEsquerda() {
        cartasEsquerda.setOpaque(false);
        cartaoDados = criarDados();
        cartasEsquerda.add(cartaoDados, "dados");
        cartasEsquerda.add(biblioteca, "biblioteca");

        JPanel coluna = new JPanel(new BorderLayout(0, 12));
        coluna.setOpaque(false);
        coluna.setPreferredSize(new Dimension(400, 10));
        if (!somenteLeitura) {
            seletorEsquerda.setSelecionado("dados");
            seletorEsquerda.aoMudar(s -> trocadorEsquerda.show(cartasEsquerda, s));
            coluna.add(seletorEsquerda, BorderLayout.NORTH);
        }
        coluna.add(cartasEsquerda, BorderLayout.CENTER);
        return coluna;
    }

    /** Mostra a biblioteca de exercícios na coluna da esquerda (o professor também chega aqui pela pílula "Biblioteca de exercícios"). */
    void mostrarBiblioteca() {
        if (somenteLeitura) return;
        seletorEsquerda.setSelecionado("biblioteca");
        trocadorEsquerda.show(cartasEsquerda, "biblioteca");
    }

    /** Rótulo em cima, campo embaixo. O asterisco verde marca o que é obrigatório. */
    private static JComponent campo(String rotulo, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(UI.rotulo(rotulo.endsWith("*") ? "<html>" + rotulo.substring(0, rotulo.length() - 1) + "<font color='#22C55E'>*</font></html>" : rotulo), BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private JComponent criarDados() {
        JPanel objetivoPanel = new JPanel(new BorderLayout(0, 6));
        objetivoPanel.setOpaque(false);
        objetivoPanel.add(objetivo, BorderLayout.NORTH);
        objetivoPanel.add(outroObjetivo, BorderLayout.CENTER);
        JPanel datas = new JPanel(new GridLayout(1, 2, 12, 0));
        datas.setOpaque(false);
        datas.add(campo("Início", inicio));
        datas.add(campo("Revisão", revisao));
        JPanel sit = new JPanel(new BorderLayout(0, 8));
        sit.setOpaque(false);
        sit.add(situacao, BorderLayout.NORTH);
        sit.add(descSituacao, BorderLayout.CENTER);
        JScrollPane obsRolagem = new JScrollPane(obs);
        obsRolagem.setPreferredSize(new Dimension(10, 84));

        // PilhaVertical dá a cada bloco a largura toda e a altura que ele pede para essa largura (as pílulas quebram de linha).
        JPanel pilha = new JPanel(new PilhaVertical(18));
        pilha.setOpaque(false);
        pilha.add(campo("Aluno *", aluno));
        pilha.add(campo("Nome da ficha *", nome));
        pilha.add(campo("Objetivo", objetivoPanel));
        pilha.add(campo("Dias de treino por semana", dias));
        pilha.add(datas);
        pilha.add(campo("Situação *", sit));
        pilha.add(campo("Observações", obsRolagem));

        Cartao card = new Cartao(new BorderLayout(), 18, 18);
        ConteudoRolavel conteudo = new ConteudoRolavel(new BorderLayout());
        conteudo.add(pilha, BorderLayout.NORTH);
        card.add(ConteudoRolavel.embrulhar(conteudo), BorderLayout.CENTER);
        return card;
    }

    private JComponent criarAreaTreinos() {
        // Barra de abas: uma pílula por treino + "Novo treino".
        JButton duplicar = Theme.ghost("Duplicar treino");
        duplicar.setIcon(Icones.icone(Icones.Tipo.COPIAR, 15, Theme.MUTED));
        duplicar.setIconTextGap(6);
        duplicar.addActionListener(e -> duplicarTreino());
        JButton remover = Theme.ghost("Remover treino");
        remover.setIcon(Icones.icone(Icones.Tipo.LIXO, 15, Theme.DANGER));
        remover.setIconTextGap(6);
        remover.addActionListener(e -> removerTreino());
        JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        acoes.setOpaque(false);
        if (!somenteLeitura) { acoes.add(duplicar); acoes.add(remover); }

        abas.setOpaque(false);
        JPanel rodape = new JPanel(new BorderLayout(12, 0));
        rodape.setOpaque(false);
        rodape.add(resumo, BorderLayout.CENTER);
        rodape.add(acoes, BorderLayout.EAST);

        resumo.setFont(Theme.fonte(12.5f, Font.PLAIN));
        resumo.setForeground(Theme.MUTED);
        resumo.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.setOpaque(false);
        centro.add(abas, BorderLayout.NORTH);
        centro.add(areaTreinos, BorderLayout.CENTER);
        centro.add(rodape, BorderLayout.SOUTH);
        return centro;
    }

    // ------------------------------------------------------------------ treinos e abas

    private TreinoPanel criarTreino(Treino t) {
        Treino base = t != null ? t : new Treino(0, "Treino " + (char) ('A' + Math.min(treinos.size(), 25)), null, null, List.of());
        TreinoPanel p = new TreinoPanel(base, !somenteLeitura);
        p.aoAlterar(() -> { sujo = true; atualizarAbas(); });
        treinos.add(p);
        areaTreinos.add(p, "t" + System.identityHashCode(p));
        return p;
    }

    private String cartaDe(TreinoPanel p) { return "t" + System.identityHashCode(p); }

    private void selecionar(int i) {
        if (treinos.isEmpty()) return;
        atual = Math.max(0, Math.min(i, treinos.size() - 1));
        cartas.show(areaTreinos, cartaDe(treinos.get(atual)));
        atualizarAbas();
    }

    /** Refaz a fileira de abas (depois de adicionar, remover ou aplicar um modelo). */
    private void reconstruirAbas() {
        abas.removeAll();
        for (int i = 0; i < treinos.size(); i++) abas.add(new Aba(i));
        if (!somenteLeitura) {
            JButton novoTreino = Theme.ghost("Novo treino");
            novoTreino.setIcon(Icones.icone(Icones.Tipo.MAIS, 15, Theme.GREEN));
            novoTreino.setIconTextGap(6);
            novoTreino.setForeground(Theme.GREEN);
            novoTreino.addActionListener(e -> {
                criarTreino(null);
                reconstruirAbas();
                selecionar(treinos.size() - 1);
                sujo = true;
            });
            abas.add(novoTreino);
        }
        abas.revalidate();
        abas.repaint();
        atualizarAbas();
    }

    private void atualizarAbas() {
        for (Component c : abas.getComponents()) c.repaint();
        int ex = 0, se = 0;
        for (TreinoPanel t : treinos) { ex += t.quantidadeExercicios(); se += t.totalSeries(); }
        resumo.setText(treinos.size() + (treinos.size() == 1 ? " treino" : " treinos") + "  ·  " + ex + (ex == 1 ? " exercício" : " exercícios")
                + "  ·  " + se + (se == 1 ? " série" : " séries") + " no total");
    }

    private void duplicarTreino() {
        try {
            Treino t = treinos.get(atual).toTreino();
            TreinoPanel c = criarTreino(new Treino(0, t.nome().isBlank() ? "Treino" : t.nome() + " (cópia)", t.diaSemana(), t.descricao(), t.exercicios()));
            reconstruirAbas();
            selecionar(treinos.indexOf(c));
            sujo = true;
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    private void removerTreino() {
        if (treinos.isEmpty()) return;
        TreinoPanel p = treinos.get(atual);
        String n = p.nome.getText().isBlank() ? "este treino" : "o treino \"" + p.nome.getText().trim() + "\"";
        if (p.temExercicios() && !UI.confirmar(this, "Remover " + n + " e todos os exercícios dele?")) return;
        treinos.remove(atual);
        areaTreinos.remove(p);
        if (treinos.isEmpty()) criarTreino(null); // a ficha sempre tem pelo menos um treino aberto
        reconstruirAbas();
        selecionar(Math.min(atual, treinos.size() - 1));
        sujo = true;
    }

    // ------------------------------------------------------------------ modelos

    private void menuModelos(JComponent origem) {
        JPopupMenu menu = new JPopupMenu();
        for (ModeloFicha m : BibliotecaExercicios.MODELOS) {
            JMenuItem it = new JMenuItem("<html><b>" + m.nome() + "</b><br><span style='color:#8A9591'>" + m.descricao() + "</span></html>");
            it.addActionListener(e -> aplicarModelo(m));
            menu.add(it);
        }
        menu.show(origem, 0, origem.getHeight() + 4);
    }

    /** Troca os treinos atuais pelos do modelo (pede confirmação se já há exercícios) e ajusta os dias por semana. */
    private void aplicarModelo(ModeloFicha m) {
        boolean temDados = treinos.stream().anyMatch(TreinoPanel::temExercicios);
        if (temDados && !UI.confirmar(this, "Substituir os treinos atuais pelo modelo \"" + m.nome() + "\"?\n\nOs exercícios que você já digitou serão removidos.")) return;
        for (TreinoPanel p : treinos) areaTreinos.remove(p);
        treinos.clear();
        for (var tm : m.treinos()) {
            TreinoPanel p = criarTreino(null);
            p.definirNomeEDia(tm.nome(), tm.dia());
            p.substituirPorModelo(tm.exercicios());
        }
        dias.setSelecionado(m.diasPorSemana());
        if (nome.getText().isBlank()) nome.setText(m.nome());
        reconstruirAbas();
        selecionar(0);
        sujo = true;
    }

    // ------------------------------------------------------------------ situação, travas e mudanças

    private void atualizarSituacao() {
        String s = situacao.getSelecionado();
        descSituacao.setText("<html><div style='width:270px'>" + SITUACOES.get(s) + "</div></html>");
        chipSituacao.definir(switch (s) { case "ATIVA" -> "Ativa"; case "RASCUNHO" -> "Rascunho"; default -> "Inativa"; },
                switch (s) { case "ATIVA" -> Theme.GREEN; case "RASCUNHO" -> Theme.WARN; default -> Theme.MUTED; });
        sujo = true;
    }

    private void travar() {
        // Só os dados da ficha: os botões Voltar/Fechar do topo continuam ativos.
        Formulario.habilitarTudo(cartaoDados, false);
        aluno.setEnabled(false);
        for (Opcoes<?> o : List.of(objetivo, dias, situacao)) o.habilitar(false);
        outroObjetivo.setEditable(false);
        nome.setEditable(false);
        obs.setEditable(false);
        for (TreinoPanel t : treinos) t.habilitar(false);
    }

    private void marcarMudancas() {
        DocumentListener d = simples(() -> sujo = true);
        nome.getDocument().addDocumentListener(d);
        outroObjetivo.getDocument().addDocumentListener(d);
        inicio.getDocument().addDocumentListener(d);
        revisao.getDocument().addDocumentListener(d);
        obs.getDocument().addDocumentListener(d);
        aluno.addActionListener(e -> sujo = true);
        objetivo.aoMudar(o -> { sujo = true; if (o != null) outroObjetivo.setText(""); });
        dias.aoMudar(o -> sujo = true);
    }

    private static DocumentListener simples(Runnable r) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    /** Volta para a lista; se há alterações não salvas, pergunta antes. */
    private void voltar() {
        if (!somenteLeitura && sujo && !UI.confirmar(this, "Sair sem salvar?\n\nAs alterações feitas nesta ficha serão perdidas.")) return;
        aoSair.run();
    }

    // ------------------------------------------------------------------ salvar

    private void salvar() {
        try {
            Aluno a = (Aluno) aluno.getSelectedItem();
            if (a == null) throw new IllegalArgumentException("Escolha o aluno que vai receber a ficha.");
            if (nome.getText().isBlank()) throw new IllegalArgumentException("Dê um nome à ficha (ex.: \"Hipertrofia — ABC\").");
            List<Treino> lidos = new ArrayList<>();
            for (TreinoPanel p : treinos) lidos.add(p.toTreino());
            String st = situacao.getSelecionado();
            if (st.equals("ATIVA") && !Precificacao.incluiFicha(a.plano())
                    && !UI.confirmar(this, "O plano " + a.plano() + " de " + a.nome() + " não inclui ficha de treino personalizada.\n\nSalvar a ficha mesmo assim?"))
                return;
            String obj = !outroObjetivo.getText().isBlank() ? outroObjetivo.getText().trim() : objetivo.getSelecionado();
            boolean edicao = existente != null && !novo;
            FichaTreino ficha = new FichaTreino(edicao ? existente.id() : 0, a.id(), edicao ? existente.professorId() : professor.id(),
                    a.nome(), professor.nome(), nome.getText(), obj, dias.getSelecionado(),
                    inicio.getData(), revisao.getData(), obs.getText(), st, lidos);
            salvador.salvar(ficha);
            sujo = false;
            aoSalvar.run();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    // ------------------------------------------------------------------ aba

    /** Aba de um treino: pílula com o nome e a quantidade de exercícios; a do treino aberto fica verde. */
    private final class Aba extends JComponent {
        private final int indice;
        private boolean sobre;

        Aba(int indice) {
            this.indice = indice;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.fonte(13.5f, Font.BOLD));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { selecionar(Aba.this.indice); }
                @Override public void mouseEntered(MouseEvent e) { sobre = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { sobre = false; repaint(); }
            });
        }

        private String texto() {
            TreinoPanel t = treinos.get(indice);
            String n = t.nome.getText().isBlank() ? "Novo treino" : t.nome.getText().trim();
            if (n.length() > 22) n = n.substring(0, 21) + "…";
            return n;
        }

        private String contagem() { return String.valueOf(treinos.get(indice).quantidadeExercicios()); }

        @Override public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(texto()) + fm.stringWidth(contagem()) + 54, 38);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.suave(g);
            boolean ativa = indice == atual;
            int w = getWidth(), h = getHeight();
            RoundRectangle2D forma = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 14, 14);
            g2.setColor(ativa ? Theme.alfa(Theme.GREEN, 38) : sobre ? Theme.HOVER : Theme.SURFACE);
            g2.fill(forma);
            g2.setColor(ativa ? Theme.alfa(Theme.GREEN, 200) : Theme.BORDER);
            g2.draw(forma);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(ativa ? Theme.GREEN : Theme.TEXT);
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            String t = texto();
            g2.drawString(t, 16, y);
            // Quantidade de exercícios numa bolinha.
            String c = contagem();
            int bw = Math.max(22, fm.stringWidth(c) + 12), bx = 16 + fm.stringWidth(t) + 10;
            g2.setColor(ativa ? Theme.alfa(Theme.GREEN, 70) : Theme.alfa(Theme.MUTED, 50));
            g2.fill(new RoundRectangle2D.Float(bx, h / 2f - 10, bw, 20, 20, 20));
            g2.setFont(Theme.fonte(12f, Font.BOLD));
            FontMetrics f2 = g2.getFontMetrics();
            g2.setColor(ativa ? Theme.GREEN : Theme.MUTED);
            g2.drawString(c, bx + (bw - f2.stringWidth(c)) / 2f, (h - f2.getHeight()) / 2f + f2.getAscent());
            g2.dispose();
        }
    }
}
