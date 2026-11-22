package br.com.vidaativa;

import br.com.vidaativa.api.ApiServer;
import br.com.vidaativa.model.*;
import br.com.vidaativa.online.*;
import br.com.vidaativa.online.GatewayPagamento.Consulta;
import br.com.vidaativa.online.GatewayPagamento.Situacao;
import br.com.vidaativa.security.TokenService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testes do pagamento online (Pix e cartão no site do aluno). Sem JUnit, sem MySQL e sem internet: usa um repositório em
 * memória, o gateway de demonstração e, para o Mercado Pago, um servidor HTTP falso que imita as respostas dele.
 *
 * <p>O que NÃO prova: que a API REAL do Mercado Pago aceita exatamente estes pedidos. O servidor falso foi escrito a partir
 * da documentação; só um pagamento de teste com as credenciais de teste do Mercado Pago confirma isso.
 *
 * <p>Grupos: {@link #servico()} regras e segurança; {@link #concorrencia()} baixa única; {@link #mercadoPago()} adaptador
 * contra o servidor falso; {@link #api()} rotas HTTP (login, isolamento entre alunos, valor vindo do servidor, webhook).
 */
public class TestesPagamentoOnline {
    private static int ok = 0, falhas = 0;
    private static final LocalDate HOJE = LocalDate.now();

    // ------------------------------------------------------------------ repositório em memória

    /** Imita {@code TransacaoDAO} e as regras de {@code PagamentoDAO.registrarPagamento} (já paga / cancelada = IllegalStateException). */
    static class Repo implements RepositorioTransacoes {
        final List<Pagamento> pagamentos = new ArrayList<>();
        final Map<Integer, TransacaoOnline> transacoes = new LinkedHashMap<>();
        final AtomicInteger proximo = new AtomicInteger(1);
        final AtomicInteger baixas = new AtomicInteger();
        /** Se preenchido, baixar() lança esta exceção (simula banco fora do ar). */
        volatile RuntimeException falhaNaBaixa;

        synchronized void pag(int id, int alunoId, String ref, String valor, String status) {
            pagamentos.add(new Pagamento(id, alunoId, "Aluno " + alunoId, ref, null, new BigDecimal(valor), "PIX", status, HOJE.plusDays(3)));
        }
        synchronized Pagamento pagId(int id) { return pagamentos.stream().filter(p -> p.id() == id).findFirst().orElse(null); }
        synchronized void trocarStatus(int id, String status) {
            for (int i = 0; i < pagamentos.size(); i++) if (pagamentos.get(i).id() == id) {
                Pagamento p = pagamentos.get(i);
                pagamentos.set(i, new Pagamento(p.id(), p.alunoId(), p.alunoNome(), p.referencia(), p.data(), p.valor(), p.formaPagamento(), status, p.vencimento()));
            }
        }
        @Override public synchronized Pagamento pagamentoDoAluno(int alunoId, String ref) {
            return pagamentos.stream().filter(p -> p.alunoId() == alunoId && p.referencia().equals(ref)).findFirst().orElse(null);
        }
        @Override public Pagamento pagamento(int id) { return pagId(id); }
        @Override public synchronized int inserir(TransacaoOnline t) {
            int id = proximo.getAndIncrement();
            transacoes.put(id, new TransacaoOnline(id, t.pagamentoId(), t.alunoId(), t.provedor(), t.metodo(), null, t.status(), t.valor(), null, null, null, t.expiraEm(), t.criadoEm(), null));
            return id;
        }
        @Override public synchronized void registrarResposta(int id, GatewayPagamento.Resposta r) {
            TransacaoOnline t = transacoes.get(id);
            transacoes.put(id, new TransacaoOnline(id, t.pagamentoId(), t.alunoId(), t.provedor(), t.metodo(), r.idExterno(), "AGUARDANDO", t.valor(),
                    r.pixCopiaCola(), r.pixQrBase64(), r.urlCheckout(), t.expiraEm(), t.criadoEm(), t.observacao()));
        }
        @Override public synchronized TransacaoOnline porId(int id) { return transacoes.get(id); }
        @Override public synchronized TransacaoOnline ultimaDoPagamento(int pagId) {
            TransacaoOnline r = null;
            for (TransacaoOnline t : transacoes.values()) if (t.pagamentoId() == pagId) r = t;
            return r;
        }
        @Override public synchronized List<TransacaoOnline> abertas() {
            List<TransacaoOnline> l = new ArrayList<>();
            for (TransacaoOnline t : transacoes.values()) if (t.aberta()) l.add(t);
            return l;
        }
        @Override public synchronized boolean mudarStatus(int id, Set<String> de, String para, String obs) {
            TransacaoOnline t = transacoes.get(id);
            if (t == null || !de.contains(t.status())) return false;
            transacoes.put(id, t.com(para, obs != null ? obs : t.observacao()));
            return true;
        }
        @Override public synchronized int contarCriadasDesde(int alunoId, LocalDateTime desde) {
            int n = 0;
            for (TransacaoOnline t : transacoes.values()) if (t.alunoId() == alunoId && !t.criadoEm().isBefore(desde)) n++;
            return n;
        }
        @Override public void baixar(int pagId, String forma) {
            if (falhaNaBaixa != null) throw falhaNaBaixa;
            synchronized (this) {
                Pagamento p = pagId(pagId);
                if (p == null) throw new IllegalStateException("Cobrança não encontrada.");
                if (p.status().equals("PAGO")) throw new IllegalStateException("Esta cobrança já foi paga.");
                if (p.status().equals("CANCELADO")) throw new IllegalStateException("Esta cobrança foi cancelada.");
                for (int i = 0; i < pagamentos.size(); i++) if (pagamentos.get(i).id() == pagId)
                    pagamentos.set(i, new Pagamento(p.id(), p.alunoId(), p.alunoNome(), p.referencia(), HOJE, p.valor(), forma, "PAGO", p.vencimento()));
                baixas.incrementAndGet();
            }
        }
    }

    static final Aluno PABLO = new Aluno(7, 70, "Pablo Henrique", "pablo@email.com", "pablo", "000", "(11) 99999-9999", null, LocalDate.of(2026, 7, 15), 2,
            "Plus", new BigDecimal("129.90"), "CONVENCIONAL", "PIX", LocalDate.of(2026, 10, 30), null, true);
    static final Aluno MARIA = new Aluno(8, 80, "Maria Outra", "maria@email.com", "maria", "111", "(11) 98888-8888", null, LocalDate.of(2026, 1, 1), 3,
            "Premium", new BigDecimal("199.90"), "CONVENCIONAL", "BOLETO", LocalDate.of(2026, 11, 5), null, true);
    static final Aluno SEM_EMAIL = new Aluno(9, 90, "Sem Email", "", "sememail", "222", "", null, LocalDate.of(2026, 1, 1), 2,
            "Plus", new BigDecimal("129.90"), "CONVENCIONAL", "PIX", null, null, true);
    static final String RETORNO = "https://site.exemplo/pages/pagamento.html";

    /** Relógio que o teste controla. */
    static class Relogio implements java.util.function.Supplier<LocalDateTime> {
        volatile LocalDateTime agora = LocalDateTime.of(2026, 11, 9, 10, 0);
        public LocalDateTime get() { return agora; }
    }

    static Repo repoPadrao() {
        Repo r = new Repo();
        r.pag(1, 7, "2026-11", "129.90", "PENDENTE");
        r.pag(2, 7, "2026-10", "129.90", "PAGO");
        r.pag(3, 7, "2026-09", "129.90", "CANCELADO");
        r.pag(4, 7, "2026-08", "0.00", "PENDENTE");
        r.pag(5, 7, "2026-07", "129.90", "ATRASADO");
        r.pag(10, 8, "2026-11", "199.90", "PENDENTE");
        return r;
    }

    static ServicoPagamentoOnline servico(GatewayPagamento g, Repo r, Relogio rel) {
        return new ServicoPagamentoOnline(g, r, rel, 30, 120, "", "https://site.exemplo");
    }

    public static void main(String[] args) throws Exception {
        servico();
        revisao();
        concorrencia();
        mercadoPago();
        api();
        System.out.println("\n" + ok + " verificações ok, " + falhas + " falha(s).");
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ regras e segurança

    static void servico() throws Exception {
        System.out.println("[Pagamento online: regras]");
        Repo repo = repoPadrao();
        Relogio rel = new Relogio();
        SimuladoGateway gw = new SimuladoGateway();
        ServicoPagamentoOnline s = servico(gw, repo, rel);

        var o = s.opcoes();
        check("opções: ligado, demonstração, Pix e cartão", o.ativo() && o.demonstracao() && o.metodos().equals(List.of("PIX", "CARTAO")));

        var pix = s.iniciar(PABLO, "2026-11", "PIX", null);
        check("Pix: aguardando, valor do banco e código copia e cola", pix.status().equals("AGUARDANDO") && pix.valor().compareTo(new BigDecimal("129.90")) == 0
                && pix.pixCopiaCola() != null && pix.pixCopiaCola().startsWith("DEMO-PIX"));
        check("Pix: validade de 30 minutos contada pelo servidor", pix.segundosRestantes() == 1800);
        var denovo = s.iniciar(PABLO, "2026-11", "PIX", null);
        check("segundo clique reaproveita o mesmo Pix (1 transação só)", denovo.pixCopiaCola().equals(pix.pixCopiaCola()) && repo.transacoes.size() == 1);

        check("mensalidade já paga é recusada", erro(() -> s.iniciar(PABLO, "2026-10", "PIX", null)).contains("já está paga"));
        check("mensalidade cancelada some (não encontrada)", erro(() -> s.iniciar(PABLO, "2026-09", "PIX", null)).contains("não encontrada"));
        check("mensalidade sem valor não gera pagamento", erro(() -> s.iniciar(PABLO, "2026-08", "PIX", null)).contains("não tem valor"));
        check("mês inexistente para o aluno", erro(() -> s.iniciar(PABLO, "2026-01", "PIX", null)).contains("não encontrada"));
        check("mês em formato inválido", erro(() -> s.iniciar(PABLO, "2026-13", "PIX", null)).contains("inválida") && erro(() -> s.iniciar(PABLO, "'; DROP", "PIX", null)).contains("inválida"));
        check("método inválido (boleto/vazio)", erro(() -> s.iniciar(PABLO, "2026-11", "BOLETO", null)).contains("Pix ou cartão") && erro(() -> s.iniciar(PABLO, "2026-11", null, null)).contains("Pix ou cartão"));
        check("aluno sem e-mail é orientado", erro(() -> {
            Repo r2 = new Repo(); r2.pag(50, 9, "2026-11", "129.90", "PENDENTE");
            servico(new SimuladoGateway(), r2, new Relogio()).iniciar(SEM_EMAIL, "2026-11", "PIX", null);
        }).contains("e-mail"));
        check("atrasada também pode ser paga online", s.iniciar(PABLO, "2026-07", "PIX", null).status().equals("AGUARDANDO"));

        // Isolamento: Maria pede o mesmo mês e recebe a DELA (outro valor); nunca a do Pablo.
        var daMaria = s.iniciar(MARIA, "2026-11", "PIX", null);
        check("cada aluno paga só a própria cobrança (valor da Maria)", daMaria.valor().compareTo(new BigDecimal("199.90")) == 0);

        // Aprovação (demonstração) dá baixa.
        var aprov = s.simular(PABLO, "2026-11", true);
        check("aprovado: status APROVADA", aprov.status().equals("APROVADA") && aprov.pixCopiaCola() == null);
        Pagamento p1 = repo.pagId(1);
        check("cobrança baixada: PAGO, hoje, forma PIX", p1.status().equals("PAGO") && HOJE.equals(p1.data()) && p1.formaPagamento().equals("PIX"));
        check("uma única baixa", repo.baixas.get() == 1);
        check("simular de novo não faz nada (sem pagamento em aberto)", erro(() -> s.simular(PABLO, "2026-11", true)).contains("em aberto") || erro(() -> s.simular(PABLO, "2026-11", true)).contains("já está paga"));
        check("estado depois de pago mostra APROVADA", s.estado(PABLO, "2026-11").status().equals("APROVADA"));
        check("não dá para pagar de novo uma mensalidade já paga online", erro(() -> s.iniciar(PABLO, "2026-11", "CARTAO", RETORNO)).contains("já está paga"));
        check("estado de mês sem tentativa = NENHUMA", servico(new SimuladoGateway(), repoPadrao(), new Relogio()).estado(PABLO, "2026-07").status().equals("NENHUMA"));

        // Recusa e nova tentativa.
        var r2 = s.simular(PABLO, "2026-07", false);
        check("recusado: status RECUSADA e cobrança continua em aberto", r2.status().equals("RECUSADA") && !repo.pagId(5).status().equals("PAGO"));
        var novo = s.iniciar(PABLO, "2026-07", "PIX", null);
        check("depois de recusado dá para gerar outro Pix", novo.status().equals("AGUARDANDO") && !novo.pixCopiaCola().equals(pix.pixCopiaCola()));

        // Cartão: link do gateway e validação do endereço de retorno.
        Repo rc = repoPadrao();
        ServicoPagamentoOnline sc = servico(new SimuladoGateway(), rc, new Relogio());
        var cartao = sc.iniciar(PABLO, "2026-11", "CARTAO", RETORNO);
        check("cartão devolve a página do gateway (nunca pede dados de cartão aqui)", cartao.urlCheckout() != null && cartao.urlCheckout().contains("checkout-demo.html") && cartao.pixCopiaCola() == null);
        check("cartão sem endereço de retorno é recusado", erro(() -> sc.iniciar(MARIA, "2026-11", "CARTAO", "")).contains("retorno"));
        for (String ruim : new String[]{"https://malicioso.com/pages/pagamento.html", "javascript:alert(1)", "https://site.exemplo/outra.html",
                "https://usuario@site.exemplo/pages/pagamento.html", "https://site.exemplo/pages/pagamento.html?x=1", "ftp://site.exemplo/pages/pagamento.html"})
            check("endereço de retorno recusado: " + ruim, erro(() -> sc.iniciar(MARIA, "2026-11", "CARTAO", ruim)).contains("retorno"));
        check("endereço de retorno do site configurado é aceito", sc.iniciar(MARIA, "2026-11", "CARTAO", "https://site.exemplo/pages/pagamento.html").status().equals("AGUARDANDO"));

        // Expiração e aprovação tardia.
        Repo re = repoPadrao(); Relogio rr = new Relogio(); SimuladoGateway ge = new SimuladoGateway();
        ServicoPagamentoOnline se = servico(ge, re, rr);
        se.iniciar(PABLO, "2026-11", "PIX", null);
        rr.agora = rr.agora.plusMinutes(31);
        check("passou do prazo: EXPIRADA", se.estado(PABLO, "2026-11").status().equals("EXPIRADA"));
        TransacaoOnline expirada = re.ultimaDoPagamento(1);
        ge.definir(expirada.idExterno(), Situacao.APROVADO);
        se.conferirAbertas();   // EXPIRADA não é mais "aberta": só um aviso/consulta direta a reabre
        check("expirada fica expirada se ninguém avisar", re.pagId(1).status().equals("PENDENTE"));
        se.aplicar(expirada, new Consulta(Situacao.APROVADO, new BigDecimal("129.90"), expirada.idExterno(), "VA-" + expirada.id(), "aviso tardio"));
        check("pagamento que chega DEPOIS do prazo ainda é contabilizado", re.pagId(1).status().equals("PAGO") && re.porId(expirada.id()).status().equals("APROVADA"));
        se.aplicar(re.porId(expirada.id()), new Consulta(Situacao.APROVADO, new BigDecimal("129.90"), expirada.idExterno(), "VA-" + expirada.id(), "repetido"));
        check("aviso repetido não baixa duas vezes", re.baixas.get() == 1);

        // Recepção recebeu antes / cobrança cancelada: o dinheiro não some.
        Repo rx = repoPadrao(); SimuladoGateway gx = new SimuladoGateway();
        ServicoPagamentoOnline sx = servico(gx, rx, new Relogio());
        sx.iniciar(PABLO, "2026-11", "PIX", null);
        rx.baixar(1, "DINHEIRO");   // a recepção recebeu em dinheiro enquanto o Pix estava aberto
        var tarde = sx.simular(PABLO, "2026-11", true);
        check("pago na recepção + Pix pago: não baixa de novo e sinaliza conferência", rx.baixas.get() == 1
                && rx.ultimaDoPagamento(1).status().equals("APROVADA_SEM_BAIXA") && tarde.status().equals("APROVADA")
                && rx.ultimaDoPagamento(1).observacao().contains("devolver") && rx.pagId(1).formaPagamento().equals("DINHEIRO"));
        Repo rk = repoPadrao(); SimuladoGateway gk = new SimuladoGateway();
        ServicoPagamentoOnline sk = servico(gk, rk, new Relogio());
        sk.iniciar(PABLO, "2026-11", "PIX", null);
        rk.trocarStatus(1, "CANCELADO");
        sk.simular(PABLO, "2026-11", true);
        check("cobrança cancelada + Pix pago: não baixa e sinaliza conferência", rk.baixas.get() == 0 && rk.ultimaDoPagamento(1).status().equals("APROVADA_SEM_BAIXA")
                && rk.ultimaDoPagamento(1).observacao().contains("cancelada"));

        // Valor diferente do cobrado.
        for (BigDecimal pago : new BigDecimal[]{new BigDecimal("1.00"), new BigDecimal("129.89"), new BigDecimal("999.00"), null}) {
            Repo rd = repoPadrao();
            GatewayPagamento falso = new GatewayFixo(Situacao.APROVADO, pago);
            ServicoPagamentoOnline sd = servico(falso, rd, new Relogio());
            sd.iniciar(PABLO, "2026-11", "PIX", null);
            sd.conferirAbertas();
            check("valor pago " + pago + " diferente de 129.90: DIVERGENTE, nada baixado", rd.baixas.get() == 0 && rd.ultimaDoPagamento(1).status().equals("DIVERGENTE")
                    && rd.pagId(1).status().equals("PENDENTE"));
        }
        Repo rv = repoPadrao();
        ServicoPagamentoOnline sv = servico(new GatewayFixo(Situacao.APROVADO, new BigDecimal("129.9")), rv, new Relogio());
        sv.iniciar(PABLO, "2026-11", "PIX", null);
        sv.conferirAbertas();
        check("129.9 e 129.90 são o mesmo valor", rv.baixas.get() == 1);

        // Limite de criações.
        Repo rl = repoPadrao(); SimuladoGateway gl = new SimuladoGateway();
        ServicoPagamentoOnline sl = servico(gl, rl, new Relogio());
        for (int i = 0; i < 8; i++) { sl.iniciar(PABLO, "2026-11", "PIX", null); sl.simular(PABLO, "2026-11", false); }
        boolean limitou = false;
        try { sl.iniciar(PABLO, "2026-11", "PIX", null); } catch (ServicoPagamentoOnline.LimiteExcedido e) { limitou = true; }
        check("9ª tentativa em 10 minutos é barrada", limitou);
        check("o limite é por aluno (Maria não é afetada)", sl.iniciar(MARIA, "2026-11", "PIX", null).status().equals("AGUARDANDO"));

        // Falha do gateway ao criar.
        Repo rf = repoPadrao();
        ServicoPagamentoOnline sf = servico(new GatewayQuebrado(), rf, new Relogio());
        String msg = erro(() -> sf.iniciar(PABLO, "2026-11", "PIX", null));
        check("gateway fora do ar: mensagem amigável, sem vazar detalhe", msg.contains("Não foi possível iniciar") && !msg.contains("TOKEN-SECRETO"));
        check("falha deixa a transação FALHOU e nada cobrado", rf.ultimaDoPagamento(1).status().equals("FALHOU") && rf.abertas().isEmpty());

        // Falha técnica ao dar baixa: volta a aguardar e a próxima conferência conclui.
        Repo rb = repoPadrao(); SimuladoGateway gb = new SimuladoGateway();
        ServicoPagamentoOnline sb = servico(gb, rb, new Relogio());
        sb.iniciar(PABLO, "2026-11", "PIX", null);
        rb.falhaNaBaixa = new IllegalArgumentException("banco fora do ar");
        gb.definir(rb.ultimaDoPagamento(1).idExterno(), Situacao.APROVADO);
        sb.conferirAbertas();
        check("banco fora na hora da baixa: continua AGUARDANDO (será repetido)", rb.ultimaDoPagamento(1).status().equals("AGUARDANDO") && rb.baixas.get() == 0);
        rb.falhaNaBaixa = null;
        sb.conferirAbertas();
        check("na conferência seguinte a baixa acontece", rb.pagId(1).status().equals("PAGO") && rb.baixas.get() == 1);

        // Sem webhook: a conferência periódica contabiliza sozinha.
        Repo rp = repoPadrao(); SimuladoGateway gp = new SimuladoGateway();
        ServicoPagamentoOnline sp = servico(gp, rp, new Relogio());
        sp.iniciar(PABLO, "2026-11", "PIX", null);
        sp.iniciar(MARIA, "2026-11", "PIX", null);
        gp.definir(rp.ultimaDoPagamento(1).idExterno(), Situacao.APROVADO);
        int n = sp.conferirAbertas();
        check("conferência periódica baixa só quem pagou", n == 2 && rp.pagId(1).status().equals("PAGO") && rp.pagId(10).status().equals("PENDENTE"));

        // Demonstração: só o gateway simulado simula.
        check("modo real não tem simulação", erro(() -> servico(new GatewayFixo(Situacao.AGUARDANDO, null), repoPadrao(), new Relogio()).simular(PABLO, "2026-11", true)).contains("demonstração"));
    }

    // ------------------------------------------------------------------ achados da revisão independente

    /** Gateway cujo aviso (webhook) devolve a consulta que o teste mandar. */
    static class GatewayComAviso extends GatewayFixo {
        volatile Consulta aviso;
        GatewayComAviso() { super(Situacao.AGUARDANDO, null); }
        @Override public Consulta processarNotificacao(Map<String, String> c, String q, String corpo) { return aviso; }
    }

    static void revisao() throws Exception {
        System.out.println("\n[Pagamento online: achados da revisão]");
        BigDecimal v = new BigDecimal("129.90");

        // Pix: o aviso precisa ser do MESMO pagamento criado para a transação.
        Repo r1 = repoPadrao(); GatewayComAviso g1 = new GatewayComAviso();
        ServicoPagamentoOnline s1 = servico(g1, r1, new Relogio());
        s1.iniciar(PABLO, "2026-11", "PIX", null);
        TransacaoOnline t1 = r1.ultimaDoPagamento(1);
        g1.aviso = new GatewayPagamento.Consulta(Situacao.APROVADO, v, "OUTRO-PAGAMENTO", "VA-" + t1.id(), "x");
        s1.notificar(Map.of(), "", "");
        check("Pix: aviso de OUTRO pagamento com a mesma referência é ignorado", r1.pagId(1).status().equals("PENDENTE") && r1.baixas.get() == 0);
        g1.aviso = new GatewayPagamento.Consulta(Situacao.APROVADO, v, t1.idExterno(), "VA-" + t1.id(), "x");
        s1.notificar(Map.of(), "", "");
        check("Pix: aviso do pagamento certo baixa", r1.pagId(1).status().equals("PAGO") && r1.baixas.get() == 1);

        // Cartão: o id do pagamento é diferente do id da preferência; não pode ser barrado.
        Repo r2 = repoPadrao(); GatewayComAviso g2 = new GatewayComAviso();
        ServicoPagamentoOnline s2 = servico(g2, r2, new Relogio());
        s2.iniciar(PABLO, "2026-11", "CARTAO", "https://site.exemplo/pagamento.html");
        TransacaoOnline t2 = r2.ultimaDoPagamento(1);
        g2.aviso = new GatewayPagamento.Consulta(Situacao.APROVADO, v, "PAGAMENTO-REAL-123", "VA-" + t2.id(), "x");
        s2.notificar(Map.of(), "", "");
        check("cartão: aviso com id de pagamento diferente do da preferência baixa normalmente", r2.pagId(1).status().equals("PAGO") && r2.baixas.get() == 1);

        // Transação fechada como RECUSADA/FALHOU e depois paga: não pode sumir em silêncio.
        Repo r3 = repoPadrao(); GatewayComAviso g3 = new GatewayComAviso();
        ServicoPagamentoOnline s3 = servico(g3, r3, new Relogio());
        s3.iniciar(PABLO, "2026-11", "CARTAO", "https://site.exemplo/pagamento.html");
        TransacaoOnline t3 = r3.ultimaDoPagamento(1);
        r3.mudarStatus(t3.id(), Set.of("AGUARDANDO"), "RECUSADA", "recusado");
        g3.aviso = new GatewayPagamento.Consulta(Situacao.APROVADO, v, "PAG-9", "VA-" + t3.id(), "x");
        s3.notificar(Map.of(), "", "");
        check("aprovado depois de RECUSADA: vira APROVADA_SEM_BAIXA para a recepção conferir (nada de baixa automática)",
                r3.porId(t3.id()).status().equals("APROVADA_SEM_BAIXA") && r3.pagId(1).status().equals("PENDENTE") && r3.baixas.get() == 0);
        s3.notificar(Map.of(), "", "");
        check("e repetir o aviso não muda nada", r3.porId(t3.id()).status().equals("APROVADA_SEM_BAIXA") && r3.baixas.get() == 0);

        // Falha técnica anterior (FALHOU) seguida de pagamento também é sinalizada.
        Repo r4 = repoPadrao(); GatewayComAviso g4 = new GatewayComAviso();
        ServicoPagamentoOnline s4 = servico(g4, r4, new Relogio());
        s4.iniciar(PABLO, "2026-11", "CARTAO", "https://site.exemplo/pagamento.html");
        TransacaoOnline t4 = r4.ultimaDoPagamento(1);
        r4.mudarStatus(t4.id(), Set.of("AGUARDANDO"), "FALHOU", "erro");
        g4.aviso = new GatewayPagamento.Consulta(Situacao.APROVADO, v, "PAG-10", "VA-" + t4.id(), "x");
        s4.notificar(Map.of(), "", "");
        check("aprovado depois de FALHOU: também sinalizado", r4.porId(t4.id()).status().equals("APROVADA_SEM_BAIXA") && r4.baixas.get() == 0);

        // Cartão: a validade configurável tem teto de 47 h (a conferência só olha transações de até 48 h).
        check("validade máxima do cartão cabe na janela de conferência (48 h)", br.com.vidaativa.config.AppConfig.pagamentoCartaoMinutos() <= 2820);
    }

    // ------------------------------------------------------------------ concorrência

    static void concorrencia() throws Exception {
        System.out.println("\n[Pagamento online: concorrência]");
        for (int rodada = 0; rodada < 20; rodada++) {
            Repo repo = repoPadrao(); SimuladoGateway gw = new SimuladoGateway();
            ServicoPagamentoOnline s = servico(gw, repo, new Relogio());
            s.iniciar(PABLO, "2026-11", "PIX", null);
            TransacaoOnline t = repo.ultimaDoPagamento(1);
            gw.definir(t.idExterno(), Situacao.APROVADO);
            ExecutorService pool = Executors.newFixedThreadPool(12);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 12; i++) fs.add(pool.submit(() -> { largada.await(); s.conferirAbertas(); return null; }));
            largada.countDown();
            for (Future<?> f : fs) f.get(10, TimeUnit.SECONDS);
            pool.shutdown();
            if (repo.baixas.get() != 1 || !repo.ultimaDoPagamento(1).status().equals("APROVADA")) {
                check("12 conferências simultâneas: baixa única (rodada " + rodada + ", baixas=" + repo.baixas.get() + ", status=" + repo.ultimaDoPagamento(1).status() + ")", false);
                return;
            }
        }
        check("12 conferências simultâneas, 20 rodadas: sempre uma única baixa e status APROVADA", true);
    }

    // ------------------------------------------------------------------ Mercado Pago contra servidor falso

    /** Servidor HTTP que imita o Mercado Pago e anota o que recebeu. */
    static class MpFalso {
        final HttpServer server;
        final List<String[]> pedidos = new CopyOnWriteArrayList<>();   // {método, caminho+query, authorization, idempotência, corpo}
        final Map<String, String> respostas = new ConcurrentHashMap<>(); // "GET /v1/payments/123" -> JSON
        volatile int statusForcado = 0;
        MpFalso() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String caminho = ex.getRequestURI().getRawPath() + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
                pedidos.add(new String[]{ex.getRequestMethod(), caminho, ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst("X-Idempotency-Key"), corpo});
                String resp = respostas.get(ex.getRequestMethod() + " " + caminho);
                if (resp == null) resp = respostas.get(ex.getRequestMethod() + " " + ex.getRequestURI().getRawPath());
                int status = statusForcado != 0 ? statusForcado : resp == null ? 404 : 200;
                byte[] b = (resp == null ? "{\"message\":\"não achei\"}" : resp).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(status, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void parar() { server.stop(0); }
        String[] ultimo() { return pedidos.get(pedidos.size() - 1); }
    }

    static void mercadoPago() throws Exception {
        System.out.println("\n[Pagamento online: adaptador do Mercado Pago contra servidor falso]");
        MpFalso mp = new MpFalso();
        try {
            check("sem access token não monta", erro(() -> new MercadoPagoGateway(mp.url(), " ", "", "")).contains("access token"));
            MercadoPagoGateway gw = new MercadoPagoGateway(mp.url(), "TOKEN-SECRETO-123", "segredo-do-webhook", "https://api.academia.com.br/api/pagamentos/webhook/mercadopago");
            check("não é demonstração", !gw.demonstracao() && gw.nome().equals("MERCADOPAGO"));

            // --- Pix
            mp.respostas.put("POST /v1/payments", "{\"id\":987654321,\"status\":\"pending\",\"external_reference\":\"VA-5\",\"point_of_interaction\":{\"transaction_data\":"
                    + "{\"qr_code\":\"00020126580014br.gov.bcb.pix0136TESTE\",\"qr_code_base64\":\"iVBORw0KGgoAAAANSUhEUg==\",\"ticket_url\":\"https://mp/x\"}}}");
            var pedido = new GatewayPagamento.Pedido("VA-5", new BigDecimal("129.90"), "Mensalidade Vida Ativa 2026-11", "pablo@email.com", "Pablo Henrique",
                    LocalDateTime.of(2026, 11, 9, 10, 30), RETORNO);
            var r = gw.criarPix(pedido);
            String[] req = mp.ultimo();
            JsonObject corpo = JsonParser.parseString(req[4]).getAsJsonObject();
            check("Pix: id, copia e cola e QR lidos da resposta", r.idExterno().equals("987654321") && r.pixCopiaCola().startsWith("0002012658") && r.pixQrBase64().startsWith("iVBOR"));
            check("Pix: POST /v1/payments com Bearer e chave de idempotência", req[0].equals("POST") && req[1].equals("/v1/payments") && req[2].equals("Bearer TOKEN-SECRETO-123") && req[3].equals("VA-5"));
            check("Pix: valor numérico 129.90, método pix, referência e e-mail do pagador", corpo.get("transaction_amount").getAsBigDecimal().compareTo(new BigDecimal("129.90")) == 0
                    && corpo.get("transaction_amount").getAsJsonPrimitive().isNumber() && corpo.get("payment_method_id").getAsString().equals("pix")
                    && corpo.get("external_reference").getAsString().equals("VA-5") && corpo.getAsJsonObject("payer").get("email").getAsString().equals("pablo@email.com")
                    && corpo.getAsJsonObject("payer").get("first_name").getAsString().equals("Pablo") && corpo.getAsJsonObject("payer").get("last_name").getAsString().equals("Henrique"));
            check("Pix: expiração com fuso (-03:00) e notification_url HTTPS", corpo.get("date_of_expiration").getAsString().equals("2026-11-09T10:30:00.000-03:00")
                    && corpo.get("notification_url").getAsString().equals("https://api.academia.com.br/api/pagamentos/webhook/mercadopago"));

            // --- Cartão (Checkout Pro)
            mp.respostas.put("POST /checkout/preferences", "{\"id\":\"PREF-1\",\"init_point\":\"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=PREF-1\"}");
            var c = gw.criarCartao(pedido);
            JsonObject pc = JsonParser.parseString(mp.ultimo()[4]).getAsJsonObject();
            check("Cartão: devolve a página do Mercado Pago", c.urlCheckout().startsWith("https://www.mercadopago.com.br/") && c.idExterno().equals("PREF-1") && c.pixCopiaCola() == null);
            check("Cartão: item com valor, referência e 1 parcela", pc.getAsJsonArray("items").get(0).getAsJsonObject().get("unit_price").getAsBigDecimal().compareTo(new BigDecimal("129.90")) == 0
                    && pc.get("external_reference").getAsString().equals("VA-5") && pc.getAsJsonObject("payment_methods").get("installments").getAsInt() == 1);
            check("Cartão: exclui boleto, caixa eletrônico e transferência", pc.getAsJsonObject("payment_methods").getAsJsonArray("excluded_payment_types").size() == 3);
            check("Cartão: volta ao site com a referência; auto_return só com HTTPS", pc.getAsJsonObject("back_urls").get("success").getAsString().startsWith(RETORNO + "?retorno=sucesso&ref=VA-5")
                    && pc.has("auto_return"));
            gw.criarCartao(new GatewayPagamento.Pedido("VA-6", new BigDecimal("10.00"), "x", "a@b.c", "Ana", LocalDateTime.of(2026, 11, 9, 12, 0), "http://localhost:5500/pages/pagamento.html"));
            check("Cartão: sem auto_return em endereço http de teste", !JsonParser.parseString(mp.ultimo()[4]).getAsJsonObject().has("auto_return"));

            // --- Consulta Pix
            mp.respostas.put("GET /v1/payments/987654321", "{\"id\":987654321,\"status\":\"approved\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            var q = gw.consultar("PIX", "987654321", "VA-5");
            check("consulta Pix aprovado: valor e referência", q.situacao() == Situacao.APROVADO && q.valorPago().compareTo(new BigDecimal("129.90")) == 0 && q.referenciaExterna().equals("VA-5"));
            mp.respostas.put("GET /v1/payments/987654321", "{\"id\":987654321,\"status\":\"pending\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            check("consulta Pix pendente = aguardando", gw.consultar("PIX", "987654321", "VA-5").situacao() == Situacao.AGUARDANDO);
            mp.respostas.put("GET /v1/payments/987654321", "{\"id\":987654321,\"status\":\"cancelled\",\"status_detail\":\"expired\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            check("consulta Pix cancelado por prazo = expirado", gw.consultar("PIX", "987654321", "VA-5").situacao() == Situacao.EXPIRADO);
            mp.respostas.put("GET /v1/payments/987654321", "{\"id\":987654321,\"status\":\"rejected\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            check("consulta Pix rejeitado = recusado", gw.consultar("PIX", "987654321", "VA-5").situacao() == Situacao.RECUSADO);

            // --- Consulta cartão (busca por referência)
            String busca = "GET /v1/payments/search?external_reference=VA-6&sort=date_created&criteria=desc&limit=20";
            mp.respostas.put(busca, "{\"results\":[{\"id\":1,\"status\":\"rejected\",\"transaction_amount\":10.0,\"external_reference\":\"VA-6\"},"
                    + "{\"id\":2,\"status\":\"approved\",\"transaction_amount\":10.0,\"external_reference\":\"VA-6\"}]}");
            check("cartão: recusa seguida de aprovação = APROVADO", gw.consultar("CARTAO", "PREF-6", "VA-6").situacao() == Situacao.APROVADO);
            mp.respostas.put(busca, "{\"results\":[{\"id\":1,\"status\":\"rejected\",\"transaction_amount\":10.0,\"external_reference\":\"VA-6\"}]}");
            check("cartão: só recusado NÃO encerra (aluno pode tentar outro cartão)", gw.consultar("CARTAO", "PREF-6", "VA-6").situacao() == Situacao.AGUARDANDO);
            mp.respostas.put(busca, "{\"results\":[{\"id\":9,\"status\":\"approved\",\"transaction_amount\":10.0,\"external_reference\":\"OUTRA-REF\"}]}");
            check("cartão: pagamento de OUTRA referência é ignorado", gw.consultar("CARTAO", "PREF-6", "VA-6").situacao() == Situacao.AGUARDANDO);
            mp.respostas.put(busca, "{\"results\":[]}");
            check("cartão: sem tentativas = aguardando", gw.consultar("CARTAO", "PREF-6", "VA-6").situacao() == Situacao.AGUARDANDO);

            // --- Erros do Mercado Pago não vazam o token
            mp.statusForcado = 401;
            String e401 = erro(() -> gw.consultar("PIX", "987654321", "VA-5"));
            check("credencial recusada: mensagem clara e sem o token", e401.contains("credenciais") && !e401.contains("TOKEN-SECRETO"));
            mp.statusForcado = 500;
            check("erro 500 do Mercado Pago vira mensagem genérica", erro(() -> gw.consultar("PIX", "987654321", "VA-5")).contains("500"));
            mp.statusForcado = 0;

            // --- Avisos (webhook): assinatura
            mp.respostas.put("GET /v1/payments/555", "{\"id\":555,\"status\":\"approved\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            String ts = "1704908010";
            String v1 = MercadoPagoGateway.hmacHex("segredo-do-webhook", "id:555;request-id:req-1;ts:" + ts + ";");
            Map<String, String> bom = Map.of("x-signature", "ts=" + ts + ",v1=" + v1, "x-request-id", "req-1");
            var n = gw.processarNotificacao(bom, "data.id=555&type=payment", "{}");
            check("aviso com assinatura válida é aceito e o pagamento é CONSULTADO na API", n != null && n.situacao() == Situacao.APROVADO && n.referenciaExterna().equals("VA-5")
                    && mp.ultimo()[1].equals("/v1/payments/555"));
            check("aviso sem assinatura é recusado", erro(() -> gw.processarNotificacao(Map.of(), "data.id=555&type=payment", "{}")).contains("assinatura"));
            check("aviso com assinatura errada é recusado", erro(() -> gw.processarNotificacao(Map.of("x-signature", "ts=" + ts + ",v1=" + "0".repeat(64), "x-request-id", "req-1"),
                    "data.id=555&type=payment", "{}")).contains("Assinatura inválida"));
            check("assinatura de OUTRO pagamento não vale (id diferente)", erro(() -> gw.processarNotificacao(bom, "data.id=556&type=payment", "{}")).contains("Assinatura inválida"));
            check("aviso de outro assunto (não é pagamento) é ignorado", gw.processarNotificacao(bom, "data.id=555&type=merchant_order", "{}") == null);
            int antes = mp.pedidos.size();
            check("data.id que não é número é ignorado sem consultar a API", gw.processarNotificacao(Map.of("x-signature", "ts=" + ts + ",v1=" + MercadoPagoGateway.hmacHex("segredo-do-webhook", "id:../x;request-id:req-1;ts:" + ts + ";"), "x-request-id", "req-1"),
                    "data.id=../x&type=payment", "{}") == null && mp.pedidos.size() == antes);

            mp.respostas.put("GET /v1/payments/557", "{\"id\":557,\"status\":\"rejected\",\"transaction_amount\":129.9,\"external_reference\":\"VA-5\"}");
            String v557 = MercadoPagoGateway.hmacHex("segredo-do-webhook", "id:557;request-id:req-1;ts:" + ts + ";");
            var rej = gw.processarNotificacao(Map.of("x-signature", "ts=" + ts + ",v1=" + v557, "x-request-id", "req-1"), "data.id=557&type=payment", "{}");
            check("aviso de pagamento RECUSADO não encerra a transação (pode ser cartão; o aluno tenta outro)", rej != null && rej.situacao() == Situacao.AGUARDANDO);

            // --- Fluxo completo pelo serviço, com o Mercado Pago falso
            Repo repo = repoPadrao(); Relogio rel = new Relogio();
            ServicoPagamentoOnline s = servico(gw, repo, rel);
            mp.respostas.put("POST /v1/payments", "{\"id\":777,\"status\":\"pending\",\"point_of_interaction\":{\"transaction_data\":{\"qr_code\":\"PIXREAL\",\"qr_code_base64\":\"QUJD\"}}}");
            var d = s.iniciar(PABLO, "2026-11", "PIX", null);
            int idTrans = repo.ultimaDoPagamento(1).id();
            check("serviço + Mercado Pago: Pix criado com referência VA-<id> da transação", d.pixCopiaCola().equals("PIXREAL") && mp.ultimo()[3].equals("VA-" + idTrans)
                    && JsonParser.parseString(mp.ultimo()[4]).getAsJsonObject().get("external_reference").getAsString().equals("VA-" + idTrans));
            mp.respostas.put("GET /v1/payments/777", "{\"id\":777,\"status\":\"pending\",\"transaction_amount\":129.90,\"external_reference\":\"VA-" + idTrans + "\"}");
            s.conferirAbertas();
            check("ainda pendente: nada baixado", repo.pagId(1).status().equals("PENDENTE"));
            mp.respostas.put("GET /v1/payments/777", "{\"id\":777,\"status\":\"approved\",\"transaction_amount\":129.90,\"external_reference\":\"VA-" + idTrans + "\"}");
            s.conferirAbertas();
            check("Mercado Pago aprovou: cobrança baixada como PIX", repo.pagId(1).status().equals("PAGO") && repo.pagId(1).formaPagamento().equals("PIX") && repo.baixas.get() == 1);
        } finally {
            mp.parar();
        }
    }

    // ------------------------------------------------------------------ rotas HTTP

    static final HttpClient HTTP = HttpClient.newHttpClient();
    static String base;

    static HttpResponse<String> http(String metodo, String caminho, String corpo, String token, Map<String, String> extra) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (corpo != null) b.header("Content-Type", "application/json");
        if (extra != null) extra.forEach(b::header);
        b.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Aluno fixo para a API de teste: Pablo (usuário 70) e Maria (usuário 80). Os pagamentos vivem no Repo, não aqui. */
    static class Dados implements br.com.vidaativa.api.PortalDados {
        public Usuario autenticar(String l, String s) { return null; }
        public Aluno alunoPorUsuario(int u) { return u == 70 ? PABLO : u == 80 ? MARIA : null; }
        public List<Plano> planos() { return List.of(); }
        public List<FichaTreino> fichasAtivas(int a) { return List.of(); }
        public List<Pagamento> pagamentos(int a) { return List.of(); }
        public List<AvaliacaoFisica> avaliacoes(int a) { return List.of(); }
        public boolean alterarSenha(int u, String a, String n) { return false; }
    }

    static void api() throws Exception {
        System.out.println("\n[Pagamento online: rotas da API]");
        TokenService tokens = new TokenService("segredo-de-teste-com-mais-de-16", 3600);
        String tPablo = tokens.emitir(70), tMaria = tokens.emitir(80);

        // 1) recurso desligado
        ApiServer desligada = new ApiServer(new Dados(), tokens, "https://site.exemplo");
        base = "http://localhost:" + desligada.start(0);
        try {
            check("desligado: opções dizem ativo=false", JsonParser.parseString(http("GET", "/api/aluno/me/pagamento-online", null, tPablo, null).body()).getAsJsonObject().get("ativo").getAsBoolean() == false);
            check("desligado: iniciar = 404", http("POST", "/api/aluno/me/pagamentos/2026-11/online", "{\"metodo\":\"PIX\"}", tPablo, null).statusCode() == 404);
            check("desligado: webhook = 404", http("POST", "/api/pagamentos/webhook/mercadopago?data.id=1&type=payment", "{}", null, null).statusCode() == 404);
        } finally { desligada.stop(); }

        // 2) demonstração
        Repo repo = repoPadrao(); SimuladoGateway gw = new SimuladoGateway();
        ApiServer api = new ApiServer(new Dados(), tokens, "https://site.exemplo", servico(gw, repo, new Relogio()));
        base = "http://localhost:" + api.start(0);
        try {
            String rota = "/api/aluno/me/pagamentos/2026-11/online";
            check("sem login: opções 401", http("GET", "/api/aluno/me/pagamento-online", null, null, null).statusCode() == 401);
            check("sem login: iniciar 401", http("POST", rota, "{\"metodo\":\"PIX\"}", null, null).statusCode() == 401);
            check("sem login: estado 401", http("GET", rota, null, null, null).statusCode() == 401);
            check("sem login: simular 401", http("POST", rota + "/simular", "{\"aprovar\":true}", null, null).statusCode() == 401);
            check("token falso 401", http("POST", rota, "{\"metodo\":\"PIX\"}", "abc.def", null).statusCode() == 401);
            check("método errado 405", http("DELETE", rota, null, tPablo, null).statusCode() == 405);
            check("mês malformado na URL = 404", http("POST", "/api/aluno/me/pagamentos/2026-1/online", "{\"metodo\":\"PIX\"}", tPablo, null).statusCode() == 404);
            check("injeção no mês da URL = 404", http("POST", "/api/aluno/me/pagamentos/2026-11'%20OR%201=1/online", "{\"metodo\":\"PIX\"}", tPablo, null).statusCode() == 404);

            var op = JsonParser.parseString(http("GET", "/api/aluno/me/pagamento-online", null, tPablo, null).body()).getAsJsonObject();
            check("opções: ativo e demonstração", op.get("ativo").getAsBoolean() && op.get("demonstracao").getAsBoolean());

            // O cliente tenta impor o valor e a cobrança: tudo é ignorado.
            var r = http("POST", rota, "{\"metodo\":\"PIX\",\"valor\":0.01,\"pagamentoId\":10,\"alunoId\":8}", tPablo, null);
            JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
            check("iniciar Pix = 200 com valor do BANCO (129.90), ignorando valor/ids enviados", r.statusCode() == 200 && j.get("valor").getAsBigDecimal().compareTo(new BigDecimal("129.90")) == 0
                    && j.get("status").getAsString().equals("AGUARDANDO"));
            check("resposta não expõe ids internos nem dados do aluno", !r.body().contains("\"id\"") && !r.body().contains("pagamentoId") && !r.body().contains("alunoId") && !r.body().contains("documento"));
            check("Maria pagando o mesmo mês recebe a cobrança DELA (199.90)",
                    JsonParser.parseString(http("POST", rota, "{\"metodo\":\"PIX\"}", tMaria, null).body()).getAsJsonObject().get("valor").getAsBigDecimal().compareTo(new BigDecimal("199.90")) == 0);
            check("Pablo não alcança mês de que não é dono (404/400, nunca a cobrança de outro)", http("GET", "/api/aluno/me/pagamentos/2026-12/online", null, tPablo, null).statusCode() == 400);
            check("método inválido = 400", http("POST", rota, "{\"metodo\":\"BOLETO\"}", tPablo, null).statusCode() == 400);
            check("corpo inválido = 400", http("POST", rota, "{oi", tPablo, null).statusCode() == 400);
            check("estado = AGUARDANDO", JsonParser.parseString(http("GET", rota, null, tPablo, null).body()).getAsJsonObject().get("status").getAsString().equals("AGUARDANDO"));

            // Cartão: endereço de retorno de outro site é recusado.
            check("cartão com retorno de outro site = 400", http("POST", "/api/aluno/me/pagamentos/2026-07/online", "{\"metodo\":\"CARTAO\",\"retorno\":\"https://malicioso.com/pages/pagamento.html\"}", tPablo, null).statusCode() == 400);
            var rc = http("POST", "/api/aluno/me/pagamentos/2026-07/online", "{\"metodo\":\"CARTAO\",\"retorno\":\"" + RETORNO + "\"}", tPablo, null);
            check("cartão com retorno do site = 200 e link do gateway", rc.statusCode() == 200 && JsonParser.parseString(rc.body()).getAsJsonObject().get("urlCheckout").getAsString().contains("checkout-demo.html"));

            // Simulação aprova e baixa.
            var sim = http("POST", rota + "/simular", "{\"aprovar\":true}", tPablo, null);
            check("simular aprovado = 200 APROVADA", sim.statusCode() == 200 && JsonParser.parseString(sim.body()).getAsJsonObject().get("status").getAsString().equals("APROVADA"));
            check("cobrança do Pablo paga, a da Maria intacta", repo.pagId(1).status().equals("PAGO") && repo.pagId(10).status().equals("PENDENTE"));
            check("depois de paga: iniciar outra vez = 400 (já paga)", http("POST", rota, "{\"metodo\":\"PIX\"}", tPablo, null).statusCode() == 400);
            check("Maria simulando afeta só a cobrança dela", http("POST", rota + "/simular", "{\"aprovar\":true}", tMaria, null).statusCode() == 200
                    && repo.pagId(10).status().equals("PAGO") && repo.baixas.get() == 2);
        } finally { api.stop(); }

        // 3) Mercado Pago (servidor falso): sem simulação e com webhook
        MpFalso mp = new MpFalso();
        Repo rm = repoPadrao(); Relogio relogio = new Relogio();
        MercadoPagoGateway mpg = new MercadoPagoGateway(mp.url(), "TOKEN-SECRETO-123", "segredo-do-webhook", "");
        ApiServer apiMp = new ApiServer(new Dados(), tokens, "https://site.exemplo", servico(mpg, rm, relogio));
        base = "http://localhost:" + apiMp.start(0);
        try {
            String rota = "/api/aluno/me/pagamentos/2026-11/online";
            mp.respostas.put("POST /v1/payments", "{\"id\":4242,\"status\":\"pending\",\"point_of_interaction\":{\"transaction_data\":{\"qr_code\":\"PIXREAL\",\"qr_code_base64\":\"QUJD\"}}}");
            check("Mercado Pago: iniciar Pix = 200", http("POST", rota, "{\"metodo\":\"PIX\"}", tPablo, null).statusCode() == 200);
            check("modo real: rota de simulação NÃO existe (404)", http("POST", rota + "/simular", "{\"aprovar\":true}", tPablo, null).statusCode() == 404);
            check("modo real: opções dizem demonstracao=false", !JsonParser.parseString(http("GET", "/api/aluno/me/pagamento-online", null, tPablo, null).body()).getAsJsonObject().get("demonstracao").getAsBoolean());
            int idT = rm.ultimaDoPagamento(1).id();

            // Aviso FALSO dizendo "aprovado" no corpo, mas o Mercado Pago (consultado) diz pendente: nada acontece.
            mp.respostas.put("GET /v1/payments/4242", "{\"id\":4242,\"status\":\"pending\",\"transaction_amount\":129.90,\"external_reference\":\"VA-" + idT + "\"}");
            String ts = "1704908010";
            Map<String, String> cab = Map.of("x-signature", "ts=" + ts + ",v1=" + MercadoPagoGateway.hmacHex("segredo-do-webhook", "id:4242;request-id:r1;ts:" + ts + ";"), "x-request-id", "r1");
            var forjado = http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{\"status\":\"approved\",\"transaction_amount\":129.90}", null, cab);
            check("aviso com corpo 'aprovado' mas gateway diz pendente: nada baixado", forjado.statusCode() == 200 && rm.pagId(1).status().equals("PENDENTE"));
            check("aviso sem assinatura = 401", http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{}", null, null).statusCode() == 401);
            check("aviso com assinatura errada = 401", http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{}", null,
                    Map.of("x-signature", "ts=1,v1=" + "a".repeat(64), "x-request-id", "r1")).statusCode() == 401 && rm.pagId(1).status().equals("PENDENTE"));
            check("webhook por GET = 405", http("GET", "/api/pagamentos/webhook/mercadopago", null, null, null).statusCode() == 405);

            // Aviso verdadeiro: o Mercado Pago confirma na consulta.
            mp.respostas.put("GET /v1/payments/4242", "{\"id\":4242,\"status\":\"approved\",\"transaction_amount\":129.90,\"external_reference\":\"VA-" + idT + "\"}");
            var certo = http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{}", null, cab);
            check("aviso assinado + consulta aprovada: cobrança baixada", certo.statusCode() == 200 && rm.pagId(1).status().equals("PAGO") && rm.baixas.get() == 1);
            http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{}", null, cab);
            http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "{}", null, cab);
            check("aviso repetido (o Mercado Pago reenvia) não baixa de novo", rm.baixas.get() == 1 && rm.ultimaDoPagamento(1).status().equals("APROVADA"));
            // Referência de transação que não existe.
            mp.respostas.put("GET /v1/payments/4243", "{\"id\":4243,\"status\":\"approved\",\"transaction_amount\":129.90,\"external_reference\":\"VA-9999\"}");
            Map<String, String> cab2 = Map.of("x-signature", "ts=" + ts + ",v1=" + MercadoPagoGateway.hmacHex("segredo-do-webhook", "id:4243;request-id:r2;ts:" + ts + ";"), "x-request-id", "r2");
            check("aviso de pagamento que não é nosso (referência desconhecida) é ignorado", http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4243&type=payment", "{}", null, cab2).statusCode() == 200
                    && rm.baixas.get() == 1);
            var grande = http("POST", "/api/pagamentos/webhook/mercadopago?data.id=4242&type=payment", "x".repeat(20000), null, cab);
            check("aviso gigante = 413", grande.statusCode() == 413);
        } finally { apiMp.stop(); mp.parar(); }
    }

    // ------------------------------------------------------------------ apoio

    /** Gateway que sempre responde o mesmo (para testar valores divergentes). */
    static class GatewayFixo implements GatewayPagamento {
        final Situacao situacao; final BigDecimal valor;
        GatewayFixo(Situacao s, BigDecimal v) { situacao = s; valor = v; }
        public String nome() { return "FIXO"; }
        public boolean demonstracao() { return false; }
        public Resposta criarPix(Pedido p) { return new Resposta("EXT-" + p.referenciaExterna(), "PIX", null, null); }
        public Resposta criarCartao(Pedido p) { return new Resposta("EXT-" + p.referenciaExterna(), null, null, "https://gateway/x"); }
        public Consulta consultar(String m, String id, String ref) { return new Consulta(situacao, valor, id, ref, "fixo"); }
        public Consulta processarNotificacao(Map<String, String> c, String q, String corpo) { return null; }
    }

    /** Gateway que sempre falha, com um token "secreto" na mensagem de erro (para provar que não vaza). */
    static class GatewayQuebrado extends GatewayFixo {
        GatewayQuebrado() { super(Situacao.AGUARDANDO, null); }
        @Override public Resposta criarPix(Pedido p) { throw new IllegalStateException("falha com TOKEN-SECRETO"); }
    }

    interface Acao { void rodar() throws Exception; }

    /** Roda a ação e devolve a mensagem da exceção ("" se não lançou nada). */
    static String erro(Acao a) {
        try { a.rodar(); return ""; } catch (Exception e) { return String.valueOf(e.getMessage()); }
    }

    static void check(String nome, boolean cond) {
        if (cond) { ok++; System.out.println("  ok    " + nome); }
        else { falhas++; System.out.println("  FALHOU " + nome); }
    }
}
