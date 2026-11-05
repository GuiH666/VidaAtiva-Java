package br.com.vidaativa.dao;

import br.com.vidaativa.config.Database;
import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;
import br.com.vidaativa.service.Precificacao;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Acesso à tabela {@code pagamentos}: as cobranças (mensalidades) dos alunos. Lê {@code alunos} e {@code usuarios}
 * só para trazer o nome do aluno.
 *
 * <p>Quem usa: a tela Pagamentos ({@code PagamentosPanel}: gerente e funcionário), {@code ResumoDAO}
 * (que reaproveita {@link #marcarAtrasados}) e a API do site ({@code DadosBanco}, {@code GET /api/aluno/me/pagamentos}
 * via {@link #listarPorAluno}).
 *
 * <p>Regras importantes:
 * <ul>
 *   <li>status: PENDENTE, PAGO, ATRASADO ou CANCELADO. Ninguém grava ATRASADO diretamente: não existe tarefa agendada,
 *       então toda listagem começa convertendo PENDENTE vencida em ATRASADO ({@link #marcarAtrasados});</li>
 *   <li>{@code referencia} é o mês da cobrança no formato AAAA-MM e há UNIQUE (aluno, referencia): um aluno não tem duas
 *       cobranças no mesmo mês;</li>
 *   <li>o valor gerado vem de {@code Precificacao.valorMensalidade} (cliente TotalPass paga menos ou nada);</li>
 *   <li>receber ({@link #registrarPagamento}) também avança o próximo vencimento do aluno;</li>
 *   <li>cobrança não é apagada: pode ser cancelada ({@link #cancelar}) se ainda não foi paga.</li>
 * </ul>
 */
public class PagamentoDAO {
    // Valores permitidos para a forma de pagamento (mesmos do ENUM alunos.forma_pagamento; em pagamentos a coluna é texto livre).
    private static final Set<String> FORMAS = Set.of("PIX", "CARTAO", "BOLETO", "DINHEIRO");

    // pagamentos (p) não guarda o nome do aluno: ele está em usuarios. Por isso o caminho p -> alunos (a) -> usuarios (u),
    // com o alias aluno_nome. Termina com espaço: cada método acrescenta WHERE/ORDER BY.
    private static final String SELECT =
            "SELECT p.id, p.aluno_id, u.nome AS aluno_nome, p.referencia, p.data_pagamento, p.valor, "
            + "p.forma_pagamento, p.status, p.vencimento "
            + "FROM pagamentos p JOIN alunos a ON a.id = p.aluno_id JOIN usuarios u ON u.id = a.usuario_id ";

    /**
     * Lista cobranças para a tela de pagamentos (inclusive as canceladas).
     * Ordem: ATRASADO, PENDENTE, PAGO, CANCELADO; dentro de cada status, vencimento mais recente primeiro.
     * Efeito colateral: antes de consultar, grava ATRASADO nas pendentes vencidas.
     *
     * @param status filtra por status (PENDENTE, PAGO, ATRASADO ou CANCELADO); null ou vazio = todos
     * @param termo parte do nome do aluno ({@code LIKE %termo%}); null ou vazio = todos
     */
    public List<Pagamento> listar(String status, String termo) throws SQLException {
        // "WHERE 1=1" é só um ponto de partida para encadear "AND ..." sem checar se o filtro é o primeiro.
        // Os valores dos filtros vão como parâmetros (?) e nunca concatenados ao SQL (evita SQL injection).
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1=1 ");
        List<String> params = new ArrayList<>();
        if (status != null && !status.isBlank()) { sql.append("AND p.status = ? "); params.add(status); }
        if (termo != null && !termo.isBlank()) { sql.append("AND u.nome LIKE ? "); params.add("%" + termo.trim() + "%"); }
        // FIELD() ordena pela posição na lista (atrasados primeiro) em vez da ordem alfabética do ENUM.
        sql.append("ORDER BY FIELD(p.status,'ATRASADO','PENDENTE','PAGO','CANCELADO'), p.vencimento DESC, p.id DESC");
        List<Pagamento> lista = new ArrayList<>();
        try (Connection c = Database.getConnection()) {
            // Atualiza antes de ler, na mesma conexão, para a lista já mostrar os atrasos corretos.
            marcarAtrasados(c);
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i));
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /**
     * Cobranças de um aluno (sem as canceladas), mais recentes primeiro. Usado pela API do site.
     * Também marca como ATRASADO as pendentes vencidas antes de consultar.
     */
    public List<Pagamento> listarPorAluno(int alunoId) throws SQLException {
        List<Pagamento> lista = new ArrayList<>();
        try (Connection c = Database.getConnection()) {
            marcarAtrasados(c);
            try (PreparedStatement ps = c.prepareStatement(
                    SELECT + "WHERE p.aluno_id = ? AND p.status <> 'CANCELADO' ORDER BY p.vencimento DESC, p.id DESC")) {
                ps.setInt(1, alunoId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /**
     * Gera a mensalidade do mês para um aluno. No momento a tela só usa {@link #gerarMensalidadesDoMes};
     * este método serve para gerar a de um aluno específico.
     *
     * @param mes mês de referência (vira o texto AAAA-MM em {@code referencia})
     * @return {@code true} se criou; {@code false} se já existia cobrança desse mês ou se não há valor a cobrar
     *         (TotalPass no plano Básico)
     * @throws SQLException "Aluno não encontrado ou inativo." ou erro de banco
     */
    public boolean gerarMensalidade(int alunoId, YearMonth mes) throws SQLException {
        Aluno a = new AlunoDAO().buscarPorId(alunoId);
        if (a == null || !a.ativo()) throw new SQLException("Aluno não encontrado ou inativo.");
        try (Connection c = Database.getConnection()) {
            return inserirMensalidade(c, a, mes);
        }
    }

    /**
     * Gera a mensalidade do mês para todos os alunos ativos. Retorna quantas cobranças novas foram criadas.
     * Quem já tem cobrança no mês é ignorado (não duplica). Não usa transação: cada cobrança é gravada sozinha,
     * então, se der erro no meio, as já criadas permanecem; rodar de novo completa só as que faltam.
     */
    public int gerarMensalidadesDoMes(YearMonth mes) throws SQLException {
        List<Aluno> alunos = new AlunoDAO().listar(false);
        int criadas = 0;
        try (Connection c = Database.getConnection()) {
            for (Aluno a : alunos) if (inserirMensalidade(c, a, mes)) criadas++;
        }
        return criadas;
    }

    /**
     * Cria uma cobrança PENDENTE do mês para o aluno. Devolve {@code false} se o valor for zero ou se já existir
     * cobrança desse aluno nesse mês.
     */
    private boolean inserirMensalidade(Connection c, Aluno a, YearMonth mes) throws SQLException {
        BigDecimal valor = Precificacao.valorMensalidade(a.plano(), a.valorPlano(), a.tipoCliente());
        // Valor zero (TotalPass no Básico, coberto pelo TotalPass) não gera cobrança.
        if (valor.signum() <= 0) return false;
        // O vencimento da cobrança usa só o DIA de alunos.vencimento (padrão 10 se vazio), aplicado ao mês gerado.
        // Math.min ajusta meses curtos (dia 31 em fevereiro vira 28/29).
        int dia = a.vencimento() != null ? a.vencimento().getDayOfMonth() : 10;
        LocalDate venc = mes.atDay(Math.min(dia, mes.lengthOfMonth()));
        // A cobrança nasce PENDENTE e herda a forma de pagamento do cadastro do aluno (ela pode ser trocada ao registrar o recebimento).
        // referencia = YearMonth.toString(), ex.: "2026-03".
        String sql = "INSERT INTO pagamentos(aluno_id,valor,forma_pagamento,status,vencimento,referencia) VALUES(?,?,?,'PENDENTE',?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a.id());
            ps.setBigDecimal(2, valor);
            ps.setString(3, a.formaPagamento());
            Sql.setData(ps, 4, venc);
            ps.setString(5, mes.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Não consultamos antes se já existe: tentamos inserir e deixamos o UNIQUE (aluno_id, referencia) barrar a duplicata.
            // Qualquer violação de integridade cai aqui (não só a de duplicidade), e todas são tratadas como "já existe".
            return false; // já existe cobrança desse aluno nesse mês
        }
    }

    /**
     * Registra o recebimento de uma cobrança: status PAGO, {@code data_pagamento} = hoje (data do servidor MySQL) e a forma
     * de pagamento realmente usada. Também avança em um mês o próximo vencimento do aluno ({@code alunos.vencimento}).
     * Tudo numa transação.
     *
     * @param forma PIX, CARTAO, BOLETO ou DINHEIRO
     * @throws IllegalArgumentException se a forma for inválida
     * @throws IllegalStateException se a cobrança não existir, já estiver paga ou estiver cancelada (não é SQLException)
     * @throws SQLException em erro de banco
     */
    public void registrarPagamento(int pagamentoId, String forma) throws SQLException {
        if (!FORMAS.contains(forma)) throw new IllegalArgumentException("Selecione a forma de pagamento.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int alunoId;
                LocalDate vencimento;
                // FOR UPDATE trava a linha até o fim da transação: duas pessoas recebendo a mesma cobrança ao mesmo tempo
                // não conseguem pagá-la duas vezes (a segunda enxerga PAGO e recebe a mensagem de erro).
                try (PreparedStatement ps = c.prepareStatement("SELECT aluno_id, vencimento, status FROM pagamentos WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, pagamentoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Cobrança não encontrada.");
                        String status = rs.getString("status");
                        if ("PAGO".equals(status)) throw new IllegalStateException("Esta cobrança já foi paga.");
                        if ("CANCELADO".equals(status)) throw new IllegalStateException("Esta cobrança foi cancelada.");
                        alunoId = rs.getInt("aluno_id");
                        vencimento = Sql.data(rs, "vencimento");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pagamentos SET status='PAGO', data_pagamento=CURDATE(), forma_pagamento=? WHERE id=?")) {
                    ps.setString(1, forma);
                    ps.setInt(2, pagamentoId);
                    ps.executeUpdate();
                }
                // Próximo vencimento do aluno = vencimento DESTA cobrança + 1 mês (não a data de hoje).
                // O WHERE só atualiza se o vencimento atual do aluno estiver vazio ou antes do novo: pagar uma cobrança antiga
                // depois de uma mais nova não faz o vencimento do aluno voltar no tempo.
                if (vencimento != null) {
                    LocalDate proximo = vencimento.plusMonths(1);
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE alunos SET vencimento = ? WHERE id = ? AND (vencimento IS NULL OR vencimento < ?)")) {
                        Sql.setData(ps, 1, proximo);
                        ps.setInt(2, alunoId);
                        Sql.setData(ps, 3, proximo);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                // RuntimeException também desfaz: as IllegalStateException de regra de negócio são lançadas com a transação aberta.
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Cancela uma cobrança que ainda não foi paga (status CANCELADO; a linha não é apagada).
     * @throws IllegalStateException se nenhuma linha foi alterada: a cobrança já está paga ou o id não existe
     */
    public void cancelar(int pagamentoId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE pagamentos SET status='CANCELADO' WHERE id=? AND status <> 'PAGO'")) {
            ps.setInt(1, pagamentoId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Só é possível cancelar cobranças que ainda não foram pagas.");
        }
    }

    /**
     * Converte PENDENTE vencida (vencimento anterior a hoje) em ATRASADO. Visível só no pacote: {@code ResumoDAO} também usa.
     * O banco não muda sozinho com o passar dos dias, então este UPDATE roda no começo de cada leitura de cobranças
     * e dos números do painel. Ou seja, algumas consultas de "leitura" gravam no banco.
     */
    static void marcarAtrasados(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE pagamentos SET status='ATRASADO' WHERE status='PENDENTE' AND vencimento IS NOT NULL AND vencimento < CURDATE()");
        }
    }

    private Pagamento mapear(ResultSet rs) throws SQLException {
        return new Pagamento(rs.getInt("id"), rs.getInt("aluno_id"), rs.getString("aluno_nome"), rs.getString("referencia"),
                Sql.data(rs, "data_pagamento"), rs.getBigDecimal("valor"), rs.getString("forma_pagamento"),
                rs.getString("status"), Sql.data(rs, "vencimento"));
    }
}
