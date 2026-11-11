package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.online.GatewayPagamento;
import br.com.vidaativa.online.RepositorioTransacoes;
import br.com.vidaativa.online.TransacaoOnline;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Acesso à tabela {@code transacoes_pagamento} (tentativas de pagamento online) e ponte para a baixa em {@code pagamentos}.
 * Implementa {@link RepositorioTransacoes}, usado por {@code ServicoPagamentoOnline}.
 *
 * <p>Quem usa também: a tela Pagamentos do software, para marcar "pago online" ({@link #origemOnline}) e perceber novos
 * pagamentos ({@link #ultimaAprovadaId}).
 *
 * <p>A tabela é criada sozinha na primeira vez ({@link #garantirTabela}); o mesmo comando está em {@code database/schema.sql}
 * e em {@code database/migrations/v3-pagamento-online.sql}.
 */
public class TransacaoDAO implements RepositorioTransacoes {
    private static final String COLUNAS = "id, pagamento_id, aluno_id, provedor, metodo, id_externo, status, valor, pix_copia_cola, "
            + "pix_qr_base64, url_checkout, expira_em, criado_em, observacao";

    /** Cria a tabela se ela ainda não existir. Lança SQLException se o usuário do banco não tiver permissão para isso. */
    public static void garantirTabela() throws SQLException {
        try (Connection c = Database.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS transacoes_pagamento ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, pagamento_id INT NOT NULL, aluno_id INT NOT NULL, "
                    + "provedor VARCHAR(20) NOT NULL, metodo ENUM('PIX','CARTAO') NOT NULL, id_externo VARCHAR(80), "
                    + "status VARCHAR(24) NOT NULL DEFAULT 'CRIADA', valor DECIMAL(10,2) NOT NULL, pix_copia_cola TEXT, "
                    + "pix_qr_base64 MEDIUMTEXT, url_checkout VARCHAR(600), expira_em DATETIME, "
                    + "criado_em DATETIME NOT NULL, atualizado_em DATETIME NOT NULL, observacao VARCHAR(300), "
                    + "CONSTRAINT fk_trans_pag FOREIGN KEY (pagamento_id) REFERENCES pagamentos(id), "
                    + "CONSTRAINT fk_trans_aluno FOREIGN KEY (aluno_id) REFERENCES alunos(id), "
                    + "KEY idx_trans_pag (pagamento_id), KEY idx_trans_status (status), "
                    + "UNIQUE KEY uq_trans_ext (provedor, id_externo))");
        }
    }

    @Override public Pagamento pagamentoDoAluno(int alunoId, String referencia) throws SQLException { return new PagamentoDAO().buscarDoAluno(alunoId, referencia); }
    @Override public Pagamento pagamento(int pagamentoId) throws SQLException { return new PagamentoDAO().buscarPorId(pagamentoId); }

    @Override public int inserir(TransacaoOnline t) throws SQLException {
        String sql = "INSERT INTO transacoes_pagamento(pagamento_id,aluno_id,provedor,metodo,status,valor,expira_em,criado_em,atualizado_em) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.pagamentoId());
            ps.setInt(2, t.alunoId());
            ps.setString(3, t.provedor());
            ps.setString(4, t.metodo());
            ps.setString(5, t.status());
            ps.setBigDecimal(6, t.valor());
            ps.setTimestamp(7, t.expiraEm() == null ? null : Timestamp.valueOf(t.expiraEm()));
            ps.setTimestamp(8, Timestamp.valueOf(t.criadoEm()));
            ps.setTimestamp(9, Timestamp.valueOf(t.criadoEm()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("Não foi possível gravar a transação.");
                return rs.getInt(1);
            }
        }
    }

    @Override public void registrarResposta(int id, GatewayPagamento.Resposta r) throws SQLException {
        String sql = "UPDATE transacoes_pagamento SET id_externo=?, pix_copia_cola=?, pix_qr_base64=?, url_checkout=?, status='AGUARDANDO', "
                + "atualizado_em=? WHERE id=? AND status='CRIADA'";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.idExterno());
            ps.setString(2, r.pixCopiaCola());
            ps.setString(3, r.pixQrBase64());
            ps.setString(4, r.urlCheckout());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(6, id);
            ps.executeUpdate();
        }
    }

    @Override public TransacaoOnline porId(int id) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM transacoes_pagamento WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapear(rs) : null; }
        }
    }

    @Override public TransacaoOnline ultimaDoPagamento(int pagamentoId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM transacoes_pagamento WHERE pagamento_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setInt(1, pagamentoId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? mapear(rs) : null; }
        }
    }

    @Override public List<TransacaoOnline> abertas() throws SQLException {
        List<TransacaoOnline> lista = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM transacoes_pagamento WHERE status IN ('CRIADA','AGUARDANDO') AND criado_em > ? ORDER BY id")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusHours(48)));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) lista.add(mapear(rs)); }
        }
        return lista;
    }

    /** Compara e troca numa única instrução SQL: o MySQL garante que só um chamador vê "1 linha alterada". */
    @Override public boolean mudarStatus(int id, Set<String> de, String para, String observacao) throws SQLException {
        String marcas = String.join(",", java.util.Collections.nCopies(de.size(), "?"));
        String sql = "UPDATE transacoes_pagamento SET status=?, observacao=COALESCE(?, observacao), atualizado_em=? WHERE id=? AND status IN (" + marcas + ")";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, para);
            ps.setString(2, observacao);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(4, id);
            int i = 5;
            for (String s : de) ps.setString(i++, s);
            return ps.executeUpdate() == 1;
        }
    }

    @Override public int contarCriadasDesde(int alunoId, LocalDateTime desde) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM transacoes_pagamento WHERE aluno_id=? AND criado_em >= ?")) {
            ps.setInt(1, alunoId);
            ps.setTimestamp(2, Timestamp.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    /** Mesma rotina de baixa da recepção: transação, trava da linha e avanço do próximo vencimento do aluno. */
    @Override public void baixar(int pagamentoId, String forma) throws SQLException { new PagamentoDAO().registrarPagamento(pagamentoId, forma); }

    // ------------------------------------------------------------------ para a tela do software

    /**
     * De quais cobranças foi o pagamento ONLINE: id da cobrança -> provedor ("SIMULADO" ou "MERCADOPAGO"). Só entram as
     * transações aprovadas e baixadas. Se a tabela ainda não existir (recurso nunca ligado), devolve vazio.
     */
    public Map<Integer, String> origemOnline() {
        Map<Integer, String> mapa = new HashMap<>();
        try (Connection c = Database.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT pagamento_id, provedor FROM transacoes_pagamento WHERE status='APROVADA'")) {
            while (rs.next()) mapa.put(rs.getInt(1), rs.getString(2));
        } catch (SQLException e) {
            // 42S02 = tabela inexistente: o pagamento online nunca foi ligado neste banco.
            if (!"42S02".equals(e.getSQLState())) System.err.println("[Pagamento] " + e.getMessage());
        }
        return mapa;
    }

    /** Id da transação aprovada mais recente (0 se não houver). A tela compara com o último visto para perceber pagamentos novos. */
    public int ultimaAprovadaId() {
        try (Connection c = Database.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id),0) FROM transacoes_pagamento WHERE status IN ('APROVADA','APROVADA_SEM_BAIXA','DIVERGENTE')")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Transações que precisam de conferência humana (pagou mas não foi baixada, ou valor diferente). */
    public List<TransacaoOnline> pendentesDeConferencia() {
        List<TransacaoOnline> lista = new ArrayList<>();
        try (Connection c = Database.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + COLUNAS + " FROM transacoes_pagamento WHERE status IN ('APROVADA_SEM_BAIXA','DIVERGENTE') ORDER BY id DESC")) {
            while (rs.next()) lista.add(mapear(rs));
        } catch (SQLException e) {
            // tabela ausente ou banco fora do ar: sem alertas
        }
        return lista;
    }

    /**
     * Marca como resolvida uma transação que estava esperando conferência. Só vale para APROVADA_SEM_BAIXA e DIVERGENTE
     * (comparação e troca numa única instrução, como em {@link #mudarStatus}). Devolve false se ela já não estava nesse estado.
     */
    public boolean marcarConferida(int id, String quem) throws SQLException {
        String nota = "Conferida na recepção" + (quem == null || quem.isBlank() ? "" : " por " + quem.trim()) + " em "
                + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(LocalDateTime.now()) + ".";
        // Mantém o motivo original (o que estava na observação) e acrescenta a nota; LEFT garante o limite da coluna.
        String sql = "UPDATE transacoes_pagamento SET status='CONFERIDA', observacao=LEFT(CONCAT(COALESCE(observacao,''), ' | ', ?), 300), "
                + "atualizado_em=? WHERE id=? AND status IN ('APROVADA_SEM_BAIXA','DIVERGENTE')";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nota);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(3, id);
            return ps.executeUpdate() == 1;
        }
    }

    private TransacaoOnline mapear(ResultSet rs) throws SQLException {
        Timestamp expira = rs.getTimestamp("expira_em");
        return new TransacaoOnline(rs.getInt("id"), rs.getInt("pagamento_id"), rs.getInt("aluno_id"), rs.getString("provedor"),
                rs.getString("metodo"), rs.getString("id_externo"), rs.getString("status"), rs.getBigDecimal("valor"),
                rs.getString("pix_copia_cola"), rs.getString("pix_qr_base64"), rs.getString("url_checkout"),
                expira == null ? null : expira.toLocalDateTime(), rs.getTimestamp("criado_em").toLocalDateTime(), rs.getString("observacao"));
    }
}
