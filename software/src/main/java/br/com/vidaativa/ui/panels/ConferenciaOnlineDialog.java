package br.com.vidaativa.ui.panels;

import br.com.vidaativa.dao.PagamentoDAO;
import br.com.vidaativa.dao.TransacaoDAO;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.online.TransacaoOnline;
import br.com.vidaativa.ui.DialogoForm;
import br.com.vidaativa.ui.Theme;
import br.com.vidaativa.ui.UI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Janela "Pagamentos online para conferir", aberta pela faixa de aviso da tela Pagamentos.
 * <p>
 * Lista as transações em que o aluno pagou pelo site mas o software NÃO deu baixa sozinho:
 * <ul>
 *   <li><b>Cobrança já estava paga ou cancelada</b> (a recepção recebeu antes, por exemplo): o dinheiro entrou duas vezes
 *       ou entrou numa cobrança cancelada. A recepção devolve o valor pelo painel do Mercado Pago, se for o caso.</li>
 *   <li><b>Valor diferente do cobrado</b>: nada foi baixado; conferir no painel do gateway.</li>
 * </ul>
 * O software nunca devolve dinheiro sozinho. Depois de resolver, o botão "Marcar como conferido" tira a linha da lista
 * (a transação vira CONFERIDA e o motivo original continua guardado).
 */
final class ConferenciaOnlineDialog {
    private ConferenciaOnlineDialog() { }

    /** Uma linha da lista: a transação e a cobrança a que ela se refere (a cobrança pode ser null se foi apagada). */
    record Item(TransacaoOnline transacao, Pagamento cobranca) { }

    /** Lê do banco as transações a conferir, já com os dados da cobrança. */
    static List<Item> carregar() {
        List<Item> itens = new ArrayList<>();
        PagamentoDAO pagamentos = new PagamentoDAO();
        for (TransacaoOnline t : new TransacaoDAO().pendentesDeConferencia()) {
            Pagamento p = null;
            try { p = pagamentos.buscarPorId(t.pagamentoId()); } catch (Exception e) { /* mostra só a transação */ }
            itens.add(new Item(t, p));
        }
        return itens;
    }

    /** Abre a janela; {@code aoMudar} é chamado quando alguma transação é marcada como conferida (a tela atualiza a faixa). */
    static void abrir(Component pai, Runnable aoMudar) {
        DialogoForm.abrir(pai, "Pagamentos online para conferir", painel(ConferenciaOnlineDialog::carregar,
                id -> new TransacaoDAO().marcarConferida(id, null), aoMudar), null, null, 760, 520);
    }

    /** Ação de marcar: devolve false se a transação já não estava esperando conferência. */
    interface Marcar { boolean marcar(int transacaoId) throws Exception; }

    /** Conteúdo da janela (as fontes de dados entram por parâmetro para as capturas de demonstração e os testes). */
    static JComponent painel(Supplier<List<Item>> fonte, Marcar marcar, Runnable aoMudar) {
        JTextArea explicacao = texto("O aluno pagou pelo site, mas o software não deu baixa sozinho porque algo não bate. "
                + "Veja o motivo, resolva (por exemplo, devolvendo o valor pelo painel do Mercado Pago) e marque como conferido.", Theme.MUTED);

        JTable tabela = UI.tabela("Aluno", "Referência", "Pago online", "Forma", "Motivo");
        JTextArea detalhe = texto(" ", Theme.TEXT);
        JButton conferido = Theme.primary("Marcar como conferido");
        conferido.setEnabled(false);
        List<Item> itens = new ArrayList<>();

        Runnable preencher = () -> {
            itens.clear();
            itens.addAll(fonte.get());
            DefaultTableModel m = UI.modelo(tabela);
            m.setRowCount(0);
            for (Item i : itens) {
                Pagamento p = i.cobranca();
                m.addRow(new Object[]{p == null ? "Cobrança " + i.transacao().pagamentoId() : p.alunoNome(),
                        p == null ? "—" : referencia(p.referencia()), UI.moeda(i.transacao().valor()),
                        UI.rotuloForma(i.transacao().metodo()), titulo(i.transacao())});
            }
            detalhe.setText(itens.isEmpty() ? "Nada para conferir. Tudo certo." : "Selecione uma linha para ver os detalhes.");
            conferido.setEnabled(false);
        };
        preencher.run();

        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int r = tabela.getSelectedRow();
            conferido.setEnabled(r >= 0 && r < itens.size());
            if (r >= 0 && r < itens.size()) {
                String obs = itens.get(r).transacao().observacao();
                detalhe.setText(obs == null || obs.isBlank() ? "Sem detalhes registrados." : obs);
            }
        });
        conferido.addActionListener(e -> {
            int r = tabela.getSelectedRow();
            if (r < 0 || r >= itens.size()) return;
            Item i = itens.get(r);
            if (!UI.confirmar(conferido, "Marcar este pagamento online como conferido?\n\nFaça isso depois de resolver (devolver o valor ou ajustar a cobrança). A linha sai desta lista.")) return;
            try {
                if (!marcar.marcar(i.transacao().id())) UI.info(conferido, "Este pagamento já foi conferido por outra pessoa.");
                preencher.run();
                if (aoMudar != null) aoMudar.run();
            } catch (Exception ex) {
                UI.erro(conferido, ex);
            }
        });

        JPanel rodape = new JPanel(new BorderLayout(12, 0));
        rodape.setOpaque(false);
        detalhe.setRows(3);
        rodape.add(detalhe, BorderLayout.CENTER);
        rodape.add(conferido, BorderLayout.EAST);
        JPanel raiz = new JPanel(new BorderLayout(0, 12));
        raiz.setOpaque(false);
        raiz.add(explicacao, BorderLayout.NORTH);
        raiz.add(UI.rolagem(tabela), BorderLayout.CENTER);
        raiz.add(rodape, BorderLayout.SOUTH);
        return raiz;
    }

    /** Motivo em poucas palavras para a coluna da tabela. */
    static String titulo(TransacaoOnline t) {
        return TransacaoOnline.DIVERGENTE.equals(t.status()) ? "Valor diferente" : "Cobrança já estava paga/cancelada";
    }

    private static String referencia(String ref) {
        return ref != null && ref.length() == 7 ? ref.substring(5) + "/" + ref.substring(0, 4) : UI.texto(ref);
    }

    /** Texto que quebra linha sozinho conforme a largura da janela (mais seguro que HTML com largura fixa). */
    private static JTextArea texto(String conteudo, Color cor) {
        JTextArea t = new JTextArea(conteudo);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(null);
        t.setForeground(cor);
        t.setFont(Theme.fonte(13.5f, Font.PLAIN));
        return t;
    }
}
