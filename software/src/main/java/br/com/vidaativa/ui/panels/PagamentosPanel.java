package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.PagamentoDAO;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.ui.*;
import br.com.vidaativa.ui.UI.Opcao;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
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
    private final JTable tabela = UI.tabela("Aluno", "Referência", "Vencimento", "Valor", "Forma", "Status", "Pago em");
    private final JComboBox<Opcao> filtroStatus = new JComboBox<>(new Opcao[]{
            new Opcao("", "Todos os status"), new Opcao("ATRASADO", "Atrasados"), new Opcao("PENDENTE", "Pendentes"),
            new Opcao("PAGO", "Pagos"), new Opcao("CANCELADO", "Cancelados")});
    private final JTextField busca = new JTextField(18);
    private final JLabel totais = new JLabel(" ");
    /** Cobranças que estão na tabela agora; a linha selecionada na tabela é o mesmo índice desta lista. */
    private List<Pagamento> lista = new ArrayList<>();

    public PagamentosPanel() {
        super("Pagamentos", "Gere as mensalidades do mês, registre os recebimentos e acompanhe quem está em atraso.");

        // --- Botões de ação ---
        JButton gerar = Theme.primary("Gerar mensalidades do mês");
        gerar.addActionListener(e -> gerarDoMes());
        JButton receber = Theme.button("Registrar pagamento");
        receber.addActionListener(e -> registrar());
        JButton cancelar = Theme.button("Cancelar cobrança");
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
        JPanel acoes = UI.botoes(gerar, receber, cancelar);
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filtros.setOpaque(false);
        filtros.add(filtroStatus);
        filtros.add(UI.rotulo("Aluno:"));
        filtros.add(busca);
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);
        topo.add(acoes, BorderLayout.WEST);
        topo.add(filtros, BorderLayout.EAST);

        totais.setForeground(Theme.MUTED);
        JPanel corpo = new JPanel(new BorderLayout(0, 10));
        corpo.setOpaque(false);
        corpo.add(topo, BorderLayout.NORTH);
        corpo.add(UI.rolagem(tabela), BorderLayout.CENTER);
        corpo.add(totais, BorderLayout.SOUTH);
        add(corpo, BorderLayout.CENTER);

        // Coluna 5 = Status: cor ajuda a achar rápido quem está em atraso.
        UI.colorirColuna(tabela, 5, s -> switch (s) {
            case "Pago" -> Theme.GREEN;
            case "Atrasado" -> Theme.DANGER;
            case "Pendente" -> Theme.WARN;
            default -> Theme.MUTED;
        });
    }

    /** Busca as cobranças no banco com os filtros atuais, redesenha a tabela e atualiza o total em aberto. */
    @Override
    public void recarregar() {
        try {
            String status = ((Opcao) filtroStatus.getSelectedItem()).valor();
            // Filtro vazio vira null = "sem filtro" para o DAO.
            lista = dao.listar(status.isEmpty() ? null : status, busca.getText());
            DefaultTableModel m = UI.modelo(tabela);
            m.setRowCount(0);
            BigDecimal aberto = BigDecimal.ZERO;
            int qtdAberto = 0;
            for (Pagamento p : lista) {
                m.addRow(new Object[]{p.alunoNome(), formatarReferencia(p.referencia()), UI.data(p.vencimento()), UI.moeda(p.valor()),
                        UI.rotuloForma(p.formaPagamento()), UI.rotuloStatusPagamento(p.status()), UI.data(p.data())});
                if (p.status().equals("PENDENTE") || p.status().equals("ATRASADO")) { aberto = aberto.add(p.valor()); qtdAberto++; }
            }
            totais.setText(lista.size() + " cobrança(s) listada(s)  •  Em aberto nesta lista: " + UI.moeda(aberto) + " (" + qtdAberto + ")");
        } catch (Exception e) {
            UI.erro(this, e);
        }
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
