package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.AlunoDAO;
import br.com.vidaativa.dao.PagamentoDAO;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.service.MensagemCobranca;
import br.com.vidaativa.ui.*;
import br.com.vidaativa.ui.UI.Opcao;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Pagamentos" (menus do Gerente e do Funcionário).
 *
 * <p>Lista as mensalidades dos alunos e permite: gerar as cobranças do mês, registrar o recebimento
 * (na recepção) e cancelar uma cobrança. O aluno só consulta essas mesmas cobranças no site.</p>
 *
 * <p>Regras (aplicadas por {@link PagamentoDAO}):</p>
 * <ul>
 *   <li>Uma cobrança por aluno por mês: gerar duas vezes não duplica.</li>
 *   <li>Aluno TotalPass no plano Básico não gera cobrança (valor zero).</li>
 *   <li>Cobrança pendente com vencimento passado vira "Atrasado" automaticamente.</li>
 *   <li>Ao registrar um pagamento, o próximo vencimento do aluno avança um mês.</li>
 * </ul>
 */
public class PagamentosPanel extends PainelBase {
    private final PagamentoDAO dao = new PagamentoDAO();
    // Colunas da tabela (a ordem aqui precisa bater com o addRow em recarregar()).
    private final JTable tabela = UI.tabela("Aluno", "Vencimento", "Valor", "Status");
    private final JComboBox<Opcao> filtroStatus = new JComboBox<>(new Opcao[]{
            new Opcao("", "Todos os status"), new Opcao("ATRASADO", "Atrasados"), new Opcao("PENDENTE", "Pendentes"),
            new Opcao("PAGO", "Pagos"), new Opcao("CANCELADO", "Cancelados")});
    private final JTextField busca = new br.com.vidaativa.ui.componentes.CampoBusca("Buscar aluno", 14);
    private final JLabel totais = new JLabel(" ");
    /** Cobranças que estão na tabela agora; a linha selecionada na tabela é o mesmo índice desta lista. */
    private List<Pagamento> lista = new ArrayList<>();
    /** Histórico do aluno da cobrança selecionada (lado direito da tela). */
    private final HistoricoAlunoPanel historico;

    public PagamentosPanel() {
        this(new HistoricoAlunoPanel());
    }

    /** Construtor para as capturas de demonstração: recebe o painel de histórico já montado. */
    PagamentosPanel(HistoricoAlunoPanel historico) {
        super("Pagamentos", "Gere as mensalidades, registre recebimentos e veja o histórico de cada aluno ao selecionar uma cobrança.");
        this.historico = historico;

        // --- Botões de ação ---
        JButton gerar = Theme.primary("Gerar mensalidades");
        gerar.addActionListener(e -> gerarDoMes());
        JButton receber = Theme.button("Registrar pagamento");
        receber.addActionListener(e -> registrar());
        JButton avisar = Theme.button("Avisar aluno");
        avisar.setToolTipText("Abre uma mensagem pronta de cobrança (WhatsApp ou e-mail) para a cobrança selecionada");
        avisar.addActionListener(e -> avisar());
        JButton cancelar = Theme.button("Cancelar");
        cancelar.setToolTipText("Cancelar a cobrança selecionada (só se ainda não foi paga)");
        cancelar.addActionListener(e -> cancelar());

        // --- Filtros: qualquer mudança (status ou texto digitado) recarrega a lista ---
        filtroStatus.addActionListener(e -> recarregar());
        busca.setToolTipText("Buscar pelo nome do aluno");
        busca.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { recarregar(); }
            public void removeUpdate(DocumentEvent e) { recarregar(); }
            public void changedUpdate(DocumentEvent e) { recarregar(); }
        });

        // --- Montagem do layout: botões à esquerda, filtros à direita, tabela no centro, totais embaixo ---
        // Duas linhas, porque a lista divide a tela com o histórico: botões (que quebram de linha se faltar espaço) e, abaixo, busca + situação.
        JPanel acoes = new JPanel(new br.com.vidaativa.ui.componentes.FluxoLayout(FlowLayout.LEFT, 8, 8));
        acoes.setOpaque(false);
        acoes.add(gerar);
        acoes.add(receber);
        acoes.add(avisar);
        acoes.add(cancelar);
        filtroStatus.setPreferredSize(new Dimension(170, 40));
        JPanel filtros = new JPanel(new BorderLayout(10, 0));
        filtros.setOpaque(false);
        filtros.add(busca, BorderLayout.CENTER);
        filtros.add(filtroStatus, BorderLayout.EAST);
        JPanel topo = new JPanel(new br.com.vidaativa.ui.componentes.PilhaVertical(10));
        topo.setOpaque(false);
        topo.add(acoes);
        topo.add(filtros);

        totais.setForeground(Theme.MUTED);
        JPanel corpo = new JPanel(new BorderLayout(0, 10));
        corpo.setOpaque(false);
        corpo.add(topo, BorderLayout.NORTH);
        corpo.add(UI.rolagem(tabela), BorderLayout.CENTER);
        corpo.add(totais, BorderLayout.SOUTH);
        JPanel principal = new JPanel(new BorderLayout(18, 0));
        principal.setOpaque(false);
        principal.add(corpo, BorderLayout.CENTER);
        principal.add(historico, BorderLayout.EAST);
        add(principal, BorderLayout.CENTER);

        // Selecionar uma cobrança mostra o histórico daquele aluno (só dele) no painel da direita.
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || atualizando) return;
            int r = tabela.getSelectedRow();
            if (r >= 0 && r < lista.size()) historico.mostrar(lista.get(r).alunoId(), lista.get(r).alunoNome());
        });

        // Coluna 3 = Status: cor ajuda a achar rápido quem está em atraso.
        UI.colorirColuna(tabela, 3, s -> switch (s) {
            case "Pago" -> Theme.GREEN;
            case "Atrasado" -> Theme.DANGER;
            case "Pendente" -> Theme.WARN;
            default -> Theme.MUTED;
        });
    }

    /** Verdadeiro enquanto a tabela é refeita, para a seleção automática não recarregar o histórico duas vezes. */
    private boolean atualizando;

    /** Busca as cobranças no banco com os filtros atuais, redesenha a tabela e atualiza o total em aberto. */
    @Override
    public void recarregar() {
        try {
            String status = ((Opcao) filtroStatus.getSelectedItem()).valor();
            // Filtro vazio vira null = "sem filtro" para o DAO.
            lista = buscar(status.isEmpty() ? null : status, busca.getText());
            atualizando = true;
            DefaultTableModel m = UI.modelo(tabela);
            m.setRowCount(0);
            BigDecimal aberto = BigDecimal.ZERO;
            int qtdAberto = 0;
            for (Pagamento p : lista) {
                m.addRow(new Object[]{p.alunoNome(), UI.data(p.vencimento()), UI.moeda(p.valor()), UI.rotuloStatusPagamento(p.status())});
                if (p.status().equals("PENDENTE") || p.status().equals("ATRASADO")) { aberto = aberto.add(p.valor()); qtdAberto++; }
            }
            totais.setText(lista.size() + " cobrança(s) listada(s)  •  Em aberto nesta lista: " + UI.moeda(aberto) + " (" + qtdAberto + ")");
            // Mantém o histórico do aluno que estava aberto (ex.: depois de registrar um pagamento, ele já aparece lá).
            int aluno = historico.alunoAtual();
            if (aluno >= 0) {
                int idx = -1;
                for (int i = 0; i < lista.size() && idx < 0; i++) if (lista.get(i).alunoId() == aluno) idx = i;
                if (idx >= 0) tabela.setRowSelectionInterval(idx, idx);
                // Se o aluno não está mais na lista filtrada, o histórico dele continua aberto, só atualizado.
                historico.mostrar(aluno, idx >= 0 ? lista.get(idx).alunoNome() : historico.nomeAtual());
            }
            atualizando = false;
        } catch (Exception e) {
            atualizando = false;
            UI.erro(this, e);
        }
    }

    /** Lê as cobranças do banco (método à parte para as capturas de demonstração poderem trocar a fonte dos dados). */
    List<Pagamento> buscar(String status, String termo) throws Exception {
        return dao.listar(status, termo);
    }

    /** Converte a referência do banco (AAAA-MM) para o formato brasileiro MM/AAAA. */
    private static String formatarReferencia(String ref) {
        if (ref == null || ref.length() != 7) return UI.texto(ref);
        return ref.substring(5) + "/" + ref.substring(0, 4);
    }

    /** Cobrança da linha selecionada, ou null (com aviso na tela) se nada estiver selecionado. */
    private Pagamento selecionado() {
        int r = tabela.getSelectedRow();
        if (r < 0 || r >= lista.size()) { UI.info(this, "Selecione uma cobrança na tabela."); return null; }
        return lista.get(r);
    }

    /** Gera a mensalidade do mês atual para todos os alunos ativos (pede confirmação antes). */
    private void gerarDoMes() {
        YearMonth mes = YearMonth.now();
        String nomeMes = mes.format(DateTimeFormatter.ofPattern("MM/yyyy"));
        if (!UI.confirmar(this, "Gerar a mensalidade de " + nomeMes + " para todos os alunos ativos?\n\n"
                + "Quem já tem cobrança nesse mês não é duplicado. Alunos TotalPass no plano Básico não geram cobrança.")) return;
        try {
            int criadas = dao.gerarMensalidadesDoMes(mes);
            UI.info(this, criadas + " cobrança(s) gerada(s) para " + nomeMes + ".");
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }

    /** Abre o diálogo para confirmar o recebimento da cobrança selecionada e a forma de pagamento usada. */
    private void registrar() {
        Pagamento p = selecionado();
        if (p == null) return;
        if (p.status().equals("PAGO")) { UI.info(this, "Esta cobrança já está paga."); return; }
        if (p.status().equals("CANCELADO")) { UI.info(this, "Esta cobrança foi cancelada."); return; }
        JComboBox<Opcao> forma = UI.comboFormas();
        UI.selecionarOpcao(forma, p.formaPagamento());
        Formulario f = new Formulario();
        f.adicionar("Aluno", new JLabel(p.alunoNome()));
        f.adicionar("Valor", new JLabel(UI.moeda(p.valor())));
        f.adicionar("Forma recebida *", forma);
        f.ajuda("Ao confirmar, a cobrança fica como paga hoje e o próximo vencimento do aluno avança um mês.");
        boolean ok = DialogoForm.abrir(this, "Registrar pagamento", f, "Confirmar recebimento",
                () -> dao.registrarPagamento(p.id(), ((Opcao) forma.getSelectedItem()).valor()), 520, 330);
        if (ok) recarregar();
    }

    /** Abre a janela com a mensagem de cobrança pronta para a cobrança selecionada (só em aberto: pendente ou atrasada). */
    private void avisar() {
        Pagamento p = selecionado();
        if (p == null) return;
        LocalDate hoje = LocalDate.now();
        if (!MensagemCobranca.podeAvisar(p, hoje)) {
            UI.info(this, "Só é possível avisar cobranças em aberto (pendentes ou atrasadas). Esta já está " + (p.status().equals("PAGO") ? "paga." : "cancelada."));
            return;
        }
        Aluno aluno = null;
        try { aluno = new AlunoDAO().buscarPorId(p.alunoId()); } catch (Exception e) { /* sem o cadastro, ainda dá para copiar a mensagem */ }
        AvisoCobrancaDialog.abrir(this, p, aluno, hoje);
    }

    /** Cancela a cobrança selecionada (o DAO só permite cancelar cobranças ainda não pagas). */
    private void cancelar() {
        Pagamento p = selecionado();
        if (p == null) return;
        if (!UI.confirmar(this, "Cancelar a cobrança de " + p.alunoNome() + " (" + formatarReferencia(p.referencia()) + ")?")) return;
        try {
            dao.cancelar(p.id());
            recarregar();
        } catch (Exception e) {
            UI.erro(this, e);
        }
    }
}
