package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.PagamentoDAO;
import br.com.vidaativa.dao.PlanoDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.model.Plano;
import br.com.vidaativa.service.HistoricoPagamentos;
import br.com.vidaativa.service.Precificacao;
import br.com.vidaativa.ui.*;
import br.com.vidaativa.ui.UI.Opcao;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tela "Alunos".
 *
 * <ul>
 *   <li><b>editavel = true</b> (Gerente e Funcionário): cadastra, edita, desativa e reativa alunos.</li>
 *   <li><b>editavel = false</b> (Professor): só consulta; o formulário abre com todos os campos travados.</li>
 * </ul>
 *
 * <p>Cadastrar um aluno cria, de uma vez, o login dele (usuário e senha para entrar no site) e o
 * cadastro do aluno ({@link AlunoDAO#cadastrar}). Aluno desativado não entra mais no site, mas o
 * histórico (pagamentos, fichas, avaliações) é mantido e ele pode ser reativado.</p>
 *
 * <p>A mensalidade mostrada no formulário vem de {@link Precificacao#valorMensalidade}: o plano
 * define o valor base e, para clientes TotalPass, o plano Básico fica coberto (R$ 0) e Plus/Premium
 * cobram só o upgrade.</p>
 */
public class AlunosPanel extends PainelBase {
    /** true = pode cadastrar/editar/desativar; false = só consulta (professor). */
    private final boolean editavel;
    private final AlunoDAO dao = new AlunoDAO();
    /** Quem edita (recepção e gerente) também vê a coluna "Financeiro"; o professor só consulta os dados do aluno. */
    private final JTable tabela;
    private final JCheckBox soAtrasados = new JCheckBox("Só em atraso");
    /** Cobranças de cada aluno (id do aluno -> lista), para a coluna "Financeiro" e o filtro "Só em atraso". */
    private Map<Integer, List<Pagamento>> cobrancas = new HashMap<>();
    private final JTextField busca = new JTextField(22);
    private final JCheckBox mostrarInativos = new JCheckBox("Mostrar inativos");
    /** Botão que vira "Desativar" ou "Reativar" conforme o aluno selecionado. */
    private final JButton btnAlternar = Theme.button("Desativar");
    /** Todos os alunos vindos do banco (inclusive inativos). */
    private List<Aluno> todos = new ArrayList<>();
    /** Alunos que passaram pelos filtros e estão na tabela; a linha selecionada é o mesmo índice desta lista. */
    private List<Aluno> visiveis = new ArrayList<>();

    public AlunosPanel(boolean editavel) {
        super(editavel ? "Alunos" : "Alunos",
                editavel ? "Cadastre, edite e desative alunos. O aluno entra no site com o usuário e a senha definidos aqui."
                        : "Consulta dos alunos. O cadastro pessoal é alterado pela recepção.");
        this.editavel = editavel;
        tabela = editavel
                ? UI.tabela("Nome", "Documento", "Telefone", "Plano", "Tipo", "Pagamento", "Vencimento", "Financeiro", "Status")
                : UI.tabela("Nome", "Documento", "Telefone", "Plano", "Tipo", "Pagamento", "Vencimento", "Status");

        // --- Barra de cima: botões à esquerda (só os de edição aparecem se editavel) e busca à direita ---
        JPanel topo = new JPanel(new BorderLayout(12, 0));
        topo.setOpaque(false);
        JPanel esquerda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        esquerda.setOpaque(false);
        if (editavel) {
            JButton novo = Theme.primary("Novo aluno");
            novo.addActionListener(e -> abrirDialogo(null, false));
            esquerda.add(novo);
        }
        JButton ver = Theme.button(editavel ? "Editar" : "Ver cadastro");
        ver.addActionListener(e -> abrirSelecionado());
        esquerda.add(ver);
        if (editavel) {
            btnAlternar.addActionListener(e -> alternarAtivo());
            esquerda.add(btnAlternar);
        }
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        direita.setOpaque(false);
        // Busca e checkbox filtram a lista que já está na memória.
        busca.setToolTipText("Buscar por nome, documento ou telefone");
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filtrar(); }
            public void removeUpdate(DocumentEvent e) { filtrar(); }
            public void changedUpdate(DocumentEvent e) { filtrar(); }
        });
        mostrarInativos.setOpaque(false);
        mostrarInativos.addActionListener(e -> filtrar());
        direita.add(UI.rotulo("Buscar:"));
        direita.add(busca);
        if (editavel) {
            soAtrasados.setOpaque(false);
            soAtrasados.setToolTipText("Mostra só os alunos que têm mensalidade atrasada");
            soAtrasados.addActionListener(e -> filtrar());
            direita.add(soAtrasados);
        }
        direita.add(mostrarInativos);
        topo.add(esquerda, BorderLayout.WEST);
        topo.add(direita, BorderLayout.EAST);

        JPanel corpo = new JPanel(new BorderLayout(0, 10));
        corpo.setOpaque(false);
        corpo.add(topo, BorderLayout.NORTH);
        corpo.add(UI.rolagem(tabela), BorderLayout.CENTER);
        add(corpo, BorderLayout.CENTER);

        // Coluna "Financeiro" (só quem edita): situação das mensalidades do aluno.
        if (editavel) UI.colorirColuna(tabela, 7, s -> switch (s) {
            case "Em atraso" -> Theme.DANGER;
            case "Em dia" -> Theme.GREEN;
            case "A vencer" -> Theme.WARN;
            default -> Theme.MUTED;
        });
        // Última coluna = Status. Duplo clique abre o cadastro. Trocar a seleção atualiza o texto do botão Desativar/Reativar.
        UI.colorirColuna(tabela, editavel ? 8 : 7, s -> s.equals("Ativo") ? Theme.GREEN : Theme.MUTED);
        tabela.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) abrirSelecionado(); }
        });
        tabela.getSelectionModel().addListSelectionListener(e -> atualizarBotaoAlternar());
    }

    /** Recarrega todos os alunos (inclusive inativos) do banco e aplica os filtros. */
    @Override
    public void recarregar() {
        try {
            todos = dao.listar(true);
            carregarCobrancas();
            filtrar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Lê as cobranças de todos os alunos de uma vez e agrupa por aluno. Se falhar, a lista de alunos continua funcionando (sem a coluna preenchida). */
    private void carregarCobrancas() {
        cobrancas = new HashMap<>();
        if (!editavel) return;
        try {
            for (Pagamento p : new PagamentoDAO().listar(null, null)) cobrancas.computeIfAbsent(p.alunoId(), k -> new ArrayList<>()).add(p);
        } catch (Exception e) {
            // Sem as cobranças, a coluna mostra "Sem cobrança"; o erro real aparece ao abrir Pagamentos.
        }
    }

    /** Código da situação financeira (ATRASADO, EM_DIA, A_VENCER, SEM_COBRANCA) do aluno, no mês atual. */
    private String situacaoFinanceira(Aluno a) {
        return HistoricoPagamentos.situacaoFinanceira(cobrancas.getOrDefault(a.id(), List.of()), YearMonth.now(), LocalDate.now());
    }

    private static String rotuloFinanceiro(String codigo) {
        return switch (codigo) {
            case "ATRASADO" -> "Em atraso";
            case "EM_DIA" -> "Em dia";
            case "A_VENCER" -> "A vencer";
            default -> "Sem cobrança";
        };
    }

    /** Aplica busca e "Mostrar inativos" sobre {@link #todos} e redesenha a tabela. */
    private void filtrar() {
        String termo = busca.getText().trim().toLowerCase(Locale.ROOT);
        // Versão só com números: permite achar "(11) 99999-1234" digitando 999991234.
        String termoDigitos = termo.replaceAll("\\D", "");
        visiveis = new ArrayList<>();
        DefaultTableModel m = UI.modelo(tabela);
        m.setRowCount(0);
        for (Aluno a : todos) {
            if (!a.ativo() && !mostrarInativos.isSelected()) continue;
            if (!termo.isEmpty()) {
                boolean bate = a.nome().toLowerCase(Locale.ROOT).contains(termo)
                        || (a.documento() != null && a.documento().toLowerCase(Locale.ROOT).contains(termo))
                        || (!termoDigitos.isEmpty() && a.telefone() != null && a.telefone().replaceAll("\\D", "").contains(termoDigitos))
                        || (!termoDigitos.isEmpty() && a.documento() != null && a.documento().replaceAll("\\D", "").contains(termoDigitos));
                if (!bate) continue;
            }
            String fin = editavel ? situacaoFinanceira(a) : "";
            if (editavel && soAtrasados.isSelected() && !fin.equals("ATRASADO")) continue;
            visiveis.add(a);
            Object[] base = {a.nome(), a.documento(), UI.texto(a.telefone()), a.plano(),
                    a.tipoCliente().equals("TOTALPASS") ? "TotalPass" : "Convencional", UI.rotuloForma(a.formaPagamento()), UI.data(a.vencimento())};
            Object[] linha = java.util.Arrays.copyOf(base, editavel ? 9 : 8);
            if (editavel) linha[7] = a.ativo() ? rotuloFinanceiro(fin) : "—";
            linha[linha.length - 1] = a.ativo() ? "Ativo" : "Inativo";
            m.addRow(linha);
        }
        atualizarBotaoAlternar();
    }

    /** Aluno da linha selecionada, ou null se não há seleção. */
    private Aluno selecionado() {
        int r = tabela.getSelectedRow();
        return r < 0 || r >= visiveis.size() ? null : visiveis.get(r);
    }

    /** Mostra "Reativar" se o aluno selecionado está inativo, senão "Desativar". */
    private void atualizarBotaoAlternar() {
        Aluno a = selecionado();
        btnAlternar.setText(a != null && !a.ativo() ? "Reativar" : "Desativar");
    }

    /** Abre o cadastro do aluno selecionado (editável ou só leitura, conforme o perfil). */
    private void abrirSelecionado() {
        Aluno a = selecionado();
        if (a == null) { UI.info(this, "Selecione um aluno na tabela."); return; }
        abrirDialogo(a, !editavel);
    }

    /** Desativa ou reativa o aluno selecionado (não apaga nada), depois de confirmar. */
    private void alternarAtivo() {
        Aluno a = selecionado();
        if (a == null) { UI.info(this, "Selecione um aluno na tabela."); return; }
        boolean desativar = a.ativo();
        String msg = desativar
                ? "Desativar o aluno " + a.nome() + "?\n\nEle não conseguirá mais entrar no site. O cadastro, os pagamentos e as fichas ficam guardados e ele pode ser reativado."
                : "Reativar o aluno " + a.nome() + "?";
        if (!UI.confirmar(this, msg)) return;
        try {
            dao.definirAtivo(a.id(), !desativar);
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /**
     * Abre o formulário do aluno.
     *
     * @param existente aluno a editar, ou null para cadastrar um novo
     * @param somenteLeitura true para apenas consultar (professor)
     */
    private void abrirDialogo(Aluno existente, boolean somenteLeitura) {
        boolean novo = existente == null;
        List<Plano> planos;
        try {
            planos = new PlanoDAO().listar();
        } catch (Exception e) {
            UI.erro(this, e);
            return;
        }
        if (planos.isEmpty()) { UI.erro(this, new IllegalStateException("Não há planos cadastrados no banco.")); return; }

        // --- Campos (preenchidos com os dados atuais quando é edição) ---
        JTextField nome = new JTextField(novo ? "" : existente.nome());
        JTextField email = new JTextField(novo ? "" : existente.email());
        JTextField telefone = new JTextField(novo ? "" : UI.texto(existente.telefone()).replace("—", ""));
        JTextField documento = new JTextField(novo ? "" : existente.documento());
        DateField nascimento = new DateField("Data de nascimento");
        if (!novo) nascimento.setData(existente.dataNascimento());
        JTextField usuario = new JTextField(novo ? "" : existente.username());
        usuario.setEditable(novo); // o login não muda depois de criado
        JPasswordField senha = new JPasswordField();

        JComboBox<Plano> plano = new JComboBox<>(planos.toArray(new Plano[0]));
        plano.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Component c = super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Plano p) setText(p.nome() + "  —  " + UI.moeda(p.valorMensal()) + "/mês");
                return c;
            }
        });
        if (!novo) for (Plano p : planos) if (p.id() == existente.planoId()) plano.setSelectedItem(p);

        JComboBox<Opcao> tipo = new JComboBox<>(new Opcao[]{new Opcao("CONVENCIONAL", "Convencional"), new Opcao("TOTALPASS", "TotalPass")});
        JComboBox<Opcao> forma = UI.comboFormas();
        if (!novo) { UI.selecionarOpcao(tipo, existente.tipoCliente()); UI.selecionarOpcao(forma, existente.formaPagamento()); }

        // Dia de vencimento escolhido pela recepção; se o aluno já tem um dia fora da lista, ele é acrescentado.
        JComboBox<Integer> dia = new JComboBox<>(new Integer[]{5, 10, 15, 20, 25});
        if (!novo && existente.vencimento() != null) {
            int d = existente.vencimento().getDayOfMonth();
            if (((DefaultComboBoxModel<Integer>) dia.getModel()).getIndexOf(d) < 0) dia.addItem(d);
            dia.setSelectedItem(d);
        }
        JTextArea obs = UI.areaTexto(3);
        if (!novo && existente.observacoes() != null) obs.setText(existente.observacoes());

        // Rótulo que mostra a mensalidade calculada e se atualiza ao trocar plano ou tipo de cliente.
        JLabel valor = new JLabel();
        valor.setForeground(Theme.GREEN);
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 14f));
        Runnable atualizarValor = () -> {
            Plano p = (Plano) plano.getSelectedItem();
            Opcao t = (Opcao) tipo.getSelectedItem();
            if (p == null || t == null) return;
            var v = Precificacao.valorMensalidade(p.nome(), p.valorMensal(), t.valor());
            valor.setText(UI.moeda(v) + (t.valor().equals("TOTALPASS") && v.signum() == 0 ? "  (coberto pelo TotalPass)"
                    : t.valor().equals("TOTALPASS") ? "  (upgrade TotalPass)" : ""));
        };
        plano.addActionListener(e -> atualizarValor.run());
        tipo.addActionListener(e -> atualizarValor.run());
        atualizarValor.run();

        // --- Montagem do formulário em seções ---
        Formulario f = new Formulario();
        f.secao("Dados pessoais");
        f.adicionar("Nome completo *", nome);
        f.adicionarPar("Documento (CPF/RG) *", documento, "Telefone *", telefone);
        f.adicionarPar("E-mail *", email, "Nascimento", nascimento);
        f.secao("Acesso ao site");
        f.adicionarPar("Usuário (login) *", usuario, novo ? "Senha *" : "Nova senha", senha);
        f.ajuda(novo ? "Mínimo de 6 caracteres. O aluno poderá trocar a senha depois no site."
                : "Deixe a senha em branco para manter a atual. O usuário (login) não pode ser alterado.");
        f.secao("Plano e pagamento");
        f.adicionarPar("Plano *", plano, "Tipo de cliente *", tipo);
        f.adicionarPar("Forma de pagamento *", forma, "Dia de vencimento *", dia);
        f.adicionar("Mensalidade", valor);
        f.adicionarGrande("Observações", new JScrollPane(obs));

        if (somenteLeitura) Formulario.habilitarTudo(f, false);

        // Rolagem porque o formulário é alto e a janela pode ser menor que a tela do computador.
        JScrollPane rolagem = new JScrollPane(f);
        rolagem.setBorder(null);
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);

        String titulo = novo ? "Novo aluno" : somenteLeitura ? "Cadastro do aluno" : "Editar aluno";
        boolean salvou = DialogoForm.abrir(this, titulo, rolagem, somenteLeitura ? null : "Salvar aluno", () -> {
            Plano p = (Plano) plano.getSelectedItem();
            // Vencimento: se o dia não mudou, mantém a data atual; se mudou (ou é novo), calcula o próximo vencimento.
            int diaVenc = (Integer) dia.getSelectedItem();
            LocalDate venc;
            if (!novo && existente.vencimento() != null && existente.vencimento().getDayOfMonth() == diaVenc) venc = existente.vencimento();
            else venc = AlunoDAO.proximoVencimento(diaVenc, LocalDate.now());
            Aluno a = new Aluno(novo ? 0 : existente.id(), novo ? 0 : existente.usuarioId(), nome.getText(), email.getText(),
                    usuario.getText(), documento.getText(), telefone.getText(), nascimento.getData(), null, p.id(), p.nome(),
                    p.valorMensal(), ((Opcao) tipo.getSelectedItem()).valor(), ((Opcao) forma.getSelectedItem()).valor(), venc,
                    obs.getText(), true);
            // Cadastro novo exige senha; na edição, senha em branco mantém a atual (regra aplicada no DAO).
            String pwd = new String(senha.getPassword());
            if (novo) dao.cadastrar(a, pwd);
            else dao.atualizar(a, pwd);
        }, 760, 700);
        if (salvou) recarregar();
    }
}
