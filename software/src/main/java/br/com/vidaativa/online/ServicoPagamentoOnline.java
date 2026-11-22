package br.com.vidaativa.online;

import br.com.vidaativa.model.Aluno;
import br.com.vidaativa.model.Pagamento;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Regras do pagamento online de mensalidades (Pix e cartão) pelo site do aluno. É lógica sem tela e sem HTTP: a
 * {@code ApiServer} só chama os métodos daqui, e os testes usam um gateway e um repositório falsos.
 *
 * <h2>Fluxo</h2>
 * <ol>
 *   <li>{@link #iniciar}: o aluno escolhe a mensalidade e o método. O valor e a mensalidade são decididos AQUI, pelo banco
 *       (nunca pelo navegador). A transação é gravada ANTES de falar com o gateway; se a conversa com o gateway falhar,
 *       fica FALHOU e nada é cobrado.</li>
 *   <li>O aluno paga no aplicativo do banco (Pix) ou na página do gateway (cartão).</li>
 *   <li>A confirmação chega por três caminhos, todos passando por {@link #aplicar}: o aluno consulta ({@link #estado}),
 *       o gateway avisa ({@link #notificar}) ou a conferência periódica pergunta ({@link #conferirAbertas}). O último garante
 *       que o pagamento seja contabilizado mesmo se o aviso do gateway não alcançar o computador da academia.</li>
 *   <li>{@link #aplicar} só dá baixa se o gateway confirmar APROVADO e o valor recebido for IGUAL ao da cobrança.</li>
 * </ol>
 *
 * <h2>Proteções</h2>
 * <ul>
 *   <li>Só cobranças em aberto (PENDENTE/ATRASADO) com valor maior que zero, e só do aluno logado.</li>
 *   <li>Uma transação aberta do mesmo método é reaproveitada (não gera Pix repetido a cada clique).</li>
 *   <li>Limite de {@value #MAX_CRIACOES} transações novas por aluno a cada 10 minutos.</li>
 *   <li>A baixa é atômica: só quem conseguir mudar a transação para APROVADA dá baixa, então a cobrança nunca é paga duas vezes.</li>
 *   <li>Pagamento que chega quando a cobrança já foi paga (pela recepção, por exemplo) ou cancelada NÃO some: fica
 *       APROVADA_SEM_BAIXA para a recepção conferir e devolver o dinheiro.</li>
 * </ul>
 */
public final class ServicoPagamentoOnline {
    static final int MAX_CRIACOES = 8;
    private static final Duration JANELA_CRIACOES = Duration.ofMinutes(10);
    /** Não pergunta ao gateway mais de uma vez a cada 3 s pela mesma transação (o navegador consulta de 4 em 4 s). */
    private static final long INTERVALO_MIN_CONSULTA_MS = 3000;

    private final GatewayPagamento gateway;
    private final RepositorioTransacoes repo;
    private final Supplier<LocalDateTime> relogio;
    private final int minutosPix;
    private final int minutosCartao;
    private final String urlSiteConfigurada;
    private final String origemPermitida;
    private final Map<Integer, Long> ultimaConsulta = new ConcurrentHashMap<>();

    /** Passa do limite de criações. A API traduz em HTTP 429. */
    public static final class LimiteExcedido extends RuntimeException {
        public LimiteExcedido(String m) { super(m); }
    }

    /**
     * @param minutosPix         validade do Pix
     * @param minutosCartao      validade da página de cartão
     * @param urlSiteConfigurada endereço do site (pagamento.url_site); vazio = aceita o que o site informar, se for um endereço plausível
     * @param origemPermitida    origem do CORS (api.cors.origin); "*" ou vazio = qualquer
     */
    public ServicoPagamentoOnline(GatewayPagamento gateway, RepositorioTransacoes repo, Supplier<LocalDateTime> relogio,
                                  int minutosPix, int minutosCartao, String urlSiteConfigurada, String origemPermitida) {
        this.gateway = gateway;
        this.repo = repo;
        this.relogio = relogio;
        this.minutosPix = minutosPix;
        this.minutosCartao = minutosCartao;
        this.urlSiteConfigurada = urlSiteConfigurada == null ? "" : urlSiteConfigurada.trim();
        this.origemPermitida = origemPermitida == null ? "*" : origemPermitida.trim();
    }

    public GatewayPagamento gateway() { return gateway; }

    // ------------------------------------------------------------------ o que a API devolve

    /** Opções para o site: qual gateway, se é demonstração e os métodos disponíveis. */
    public record Opcoes(boolean ativo, String provedor, boolean demonstracao, List<String> metodos) { }

    public Opcoes opcoes() { return new Opcoes(true, gateway.nome(), gateway.demonstracao(), List.of("PIX", "CARTAO")); }

    /**
     * Estado de um pagamento online, como o site o vê. {@code status}: NENHUMA (nunca tentou), AGUARDANDO, APROVADA, RECUSADA,
     * EXPIRADA, FALHOU ou EM_ANALISE. {@code segundosRestantes} vem do servidor (o relógio do navegador pode estar errado).
     */
    public record Detalhe(String referencia, String metodo, String status, BigDecimal valor, String pixCopiaCola, String pixQrBase64,
                          String urlCheckout, long segundosRestantes, boolean demonstracao, String mensagem) { }

    // ------------------------------------------------------------------ operações do aluno

    /**
     * Começa (ou retoma) o pagamento online de uma mensalidade.
     *
     * @param referencia mês da mensalidade (AAAA-MM)
     * @param metodo     PIX ou CARTAO
     * @param urlRetorno página do site para onde o gateway volta (precisa terminar em /pagamento.html)
     * @throws IllegalArgumentException dado inválido ou cobrança que não pode ser paga online (vira HTTP 400)
     * @throws LimiteExcedido           muitas tentativas (HTTP 429)
     */
    public Detalhe iniciar(Aluno aluno, String referencia, String metodo, String urlRetorno) throws Exception {
        if (metodo == null || !(metodo.equals("PIX") || metodo.equals("CARTAO")))
            throw new IllegalArgumentException("Escolha Pix ou cartão.");
        Pagamento p = pagamentoPagavel(aluno, referencia);

        // Reaproveita um pagamento do mesmo método que ainda está valendo.
        TransacaoOnline ultima = repo.ultimaDoPagamento(p.id());
        if (ultima != null && ultima.aberta() && ultima.metodo().equals(metodo) && ultima.idExterno() != null && !vencida(ultima)) {
            return detalhe(referencia, ultima);
        }
        String retorno = metodo.equals("CARTAO") ? validarRetorno(urlRetorno) : "";
        LocalDateTime agora = relogio.get();
        if (repo.contarCriadasDesde(aluno.id(), agora.minus(JANELA_CRIACOES)) >= MAX_CRIACOES)
            throw new LimiteExcedido("Muitas tentativas de pagamento. Aguarde alguns minutos e tente de novo.");
        if (aluno.email() == null || !aluno.email().contains("@"))
            throw new IllegalArgumentException("Seu cadastro está sem e-mail. Peça à recepção para cadastrar um e-mail antes de pagar online.");

        LocalDateTime expira = agora.plusMinutes(metodo.equals("PIX") ? minutosPix : minutosCartao);
        // 1) grava; 2) só então fala com o gateway. Assim nunca existe pagamento no gateway sem registro aqui.
        int id = repo.inserir(new TransacaoOnline(0, p.id(), aluno.id(), gateway.nome(), metodo, null, TransacaoOnline.CRIADA, p.valor(),
                null, null, null, expira, agora, null));
        GatewayPagamento.Pedido pedido = new GatewayPagamento.Pedido("VA-" + id, p.valor(),
                "Mensalidade Vida Ativa " + referencia, aluno.email().trim(), aluno.nome(), expira, retorno);
        try {
            GatewayPagamento.Resposta r = metodo.equals("PIX") ? gateway.criarPix(pedido) : gateway.criarCartao(pedido);
            repo.registrarResposta(id, r);
        } catch (Exception e) {
            repo.mudarStatus(id, TransacaoOnline.ABERTAS, TransacaoOnline.FALHOU, limitar(e.getMessage()));
            System.err.println("[Pagamento] Falha ao criar " + metodo + " (transação " + id + "): " + e.getMessage());
            throw new IllegalStateException("Não foi possível iniciar o pagamento agora. Tente de novo em instantes ou pague na recepção.");
        }
        return detalhe(referencia, repo.porId(id));
    }

    /**
     * Situação do pagamento online da mensalidade. Se estiver aberto, confere com o gateway antes de responder
     * (o site chama isto de poucos em poucos segundos enquanto o aluno paga).
     */
    public Detalhe estado(Aluno aluno, String referencia) throws Exception {
        Pagamento p = repo.pagamentoDoAluno(aluno.id(), referencia);
        if (p == null) throw new IllegalArgumentException("Mensalidade não encontrada.");
        TransacaoOnline t = repo.ultimaDoPagamento(p.id());
        if (t == null) return new Detalhe(referencia, null, "NENHUMA", p.valor(), null, null, null, 0, gateway.demonstracao(), null);
        if (t.aberta()) {
            long agora = System.currentTimeMillis();
            Long antes = ultimaConsulta.get(t.id());
            if (antes == null || agora - antes >= INTERVALO_MIN_CONSULTA_MS) {
                ultimaConsulta.put(t.id(), agora);
                conferir(t);
            }
            t = repo.porId(t.id());
        }
        return detalhe(referencia, t);
    }

    /**
     * Só no modo demonstração: aprova ou recusa o pagamento em aberto, como se o aluno tivesse pagado.
     * @throws IllegalStateException se o gateway não for o simulado
     */
    public Detalhe simular(Aluno aluno, String referencia, boolean aprovar) throws Exception {
        if (!(gateway instanceof SimuladoGateway sim)) throw new IllegalStateException("A simulação só existe no modo de demonstração.");
        Pagamento p = repo.pagamentoDoAluno(aluno.id(), referencia);
        if (p == null) throw new IllegalArgumentException("Mensalidade não encontrada.");
        TransacaoOnline t = repo.ultimaDoPagamento(p.id());
        if (t == null || !t.aberta() || t.idExterno() == null) throw new IllegalArgumentException("Não há pagamento em aberto para simular.");
        sim.definir(t.idExterno(), aprovar ? GatewayPagamento.Situacao.APROVADO : GatewayPagamento.Situacao.RECUSADO);
        conferir(t);
        return detalhe(referencia, repo.porId(t.id()));
    }

    // ------------------------------------------------------------------ confirmação

    /** Trata o aviso (webhook) do gateway. Uma assinatura errada lança {@link SecurityException}; avisos irrelevantes são ignorados. */
    public void notificar(Map<String, String> cabecalhos, String query, String corpo) throws Exception {
        GatewayPagamento.Consulta c = gateway.processarNotificacao(cabecalhos, query, corpo);
        if (c == null || c.referenciaExterna() == null || !c.referenciaExterna().matches("VA-\\d{1,9}")) return;
        TransacaoOnline t = repo.porId(Integer.parseInt(c.referenciaExterna().substring(3)));
        if (t == null) return;
        // Pix: o pagamento do aviso precisa ser o MESMO que foi criado para esta transação (cartão usa outro id: o da preferência).
        if ("PIX".equals(t.metodo()) && t.idExterno() != null && c.idExterno() != null && !t.idExterno().equals(c.idExterno())) return;
        aplicar(t, c);
    }

    /** Pergunta ao gateway por todas as transações abertas (rodada a cada 30 s pela API). Devolve quantas foram conferidas. */
    public int conferirAbertas() throws Exception {
        int n = 0;
        for (TransacaoOnline t : repo.abertas()) {
            try { conferir(t); n++; }
            catch (Exception e) { System.err.println("[Pagamento] Não consegui conferir a transação " + t.id() + ": " + e.getMessage()); }
        }
        return n;
    }

    /** Consulta o gateway sobre uma transação e aplica o resultado. */
    private void conferir(TransacaoOnline t) throws Exception {
        if (t.idExterno() == null) {
            // Ficou CRIADA sem resposta do gateway (o programa caiu no meio): depois de 10 minutos, desiste.
            if (relogio.get().isAfter(t.criadoEm().plusMinutes(10))) repo.mudarStatus(t.id(), Set.of(TransacaoOnline.CRIADA), TransacaoOnline.FALHOU, "Sem resposta do gateway");
            return;
        }
        GatewayPagamento.Consulta c = gateway.consultar(t.metodo(), t.idExterno(), "VA-" + t.id());
        aplicar(t, c);
        // Se continua aberta e o prazo acabou, expira (a aprovação tardia ainda seria aceita por aplicar()).
        TransacaoOnline atual = repo.porId(t.id());
        if (atual != null && atual.aberta() && vencida(atual))
            repo.mudarStatus(atual.id(), TransacaoOnline.ABERTAS, TransacaoOnline.EXPIRADA, null);
    }

    /**
     * Aplica a resposta do gateway à transação. É AQUI, e só aqui, que uma cobrança vira paga.
     * Seguro para ser chamado várias vezes e por várias threads ao mesmo tempo.
     */
    public void aplicar(TransacaoOnline t, GatewayPagamento.Consulta c) throws Exception {
        switch (c.situacao()) {
            case APROVADO -> aprovar(t, c);
            case RECUSADO -> repo.mudarStatus(t.id(), TransacaoOnline.ABERTAS, TransacaoOnline.RECUSADA, c.detalhe());
            case EXPIRADO -> repo.mudarStatus(t.id(), TransacaoOnline.ABERTAS, TransacaoOnline.EXPIRADA, c.detalhe());
            case AGUARDANDO -> { /* nada a fazer */ }
        }
    }

    private void aprovar(TransacaoOnline t, GatewayPagamento.Consulta c) throws Exception {
        // Valor recebido precisa ser exatamente o cobrado. Sem valor informado também não baixa.
        if (c.valorPago() == null || c.valorPago().compareTo(t.valor()) != 0) {
            repo.mudarStatus(t.id(), TransacaoOnline.ACEITAM_APROVACAO, TransacaoOnline.DIVERGENTE,
                    "Valor recebido " + c.valorPago() + " diferente do cobrado " + t.valor() + ". Conferir no gateway.");
            return;
        }
        // "Reivindica" a transação: só uma thread consegue mudar para APROVADA, e só ela dá baixa.
        if (!repo.mudarStatus(t.id(), TransacaoOnline.ACEITAM_APROVACAO, TransacaoOnline.APROVADA, null)) {
            // Não pode ser descartado em silêncio: se a transação já tinha sido fechada como recusada/falha mas o dinheiro entrou, a recepção precisa saber.
            repo.mudarStatus(t.id(), Set.of(TransacaoOnline.RECUSADA, TransacaoOnline.FALHOU), TransacaoOnline.APROVADA_SEM_BAIXA,
                    "Pagamento aprovado depois de a transação ter sido marcada como recusada/falha. Conferir no gateway e dar baixa ou devolver.");
            return;
        }
        try {
            Pagamento p = repo.pagamento(t.pagamentoId());
            if (p == null || p.valor().compareTo(t.valor()) != 0) {
                repo.mudarStatus(t.id(), Set.of(TransacaoOnline.APROVADA), TransacaoOnline.DIVERGENTE,
                        "O valor da cobrança mudou depois do pagamento. Conferir e, se preciso, devolver.");
                return;
            }
            repo.baixar(t.pagamentoId(), t.metodo());
        } catch (IllegalStateException jaPaga) {
            // A recepção recebeu antes, ou a cobrança foi cancelada: o dinheiro entrou e precisa de atenção humana.
            repo.mudarStatus(t.id(), Set.of(TransacaoOnline.APROVADA), TransacaoOnline.APROVADA_SEM_BAIXA,
                    "Pago online, mas a cobrança já estava " + (jaPaga.getMessage().contains("cancelada") ? "cancelada" : "paga")
                            + ". Conferir e devolver o valor ao aluno, se for o caso.");
            System.err.println("[Pagamento] ATENÇÃO: transação " + t.id() + " paga online mas a cobrança " + t.pagamentoId() + " não aceitou a baixa.");
        } catch (Exception e) {
            // Falha técnica (banco fora do ar): volta para AGUARDANDO, a próxima conferência tenta de novo.
            repo.mudarStatus(t.id(), Set.of(TransacaoOnline.APROVADA), TransacaoOnline.AGUARDANDO, "Falha ao dar baixa; será repetido");
            throw e;
        }
    }

    // ------------------------------------------------------------------ apoio

    private Pagamento pagamentoPagavel(Aluno aluno, String referencia) throws Exception {
        if (referencia == null || !referencia.matches("\\d{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("Mensalidade inválida.");
        Pagamento p = repo.pagamentoDoAluno(aluno.id(), referencia);
        if (p == null || "CANCELADO".equals(p.status())) throw new IllegalArgumentException("Mensalidade não encontrada.");
        if ("PAGO".equals(p.status())) throw new IllegalArgumentException("Esta mensalidade já está paga.");
        if (p.valor() == null || p.valor().signum() <= 0) throw new IllegalArgumentException("Esta mensalidade não tem valor a pagar.");
        return p;
    }

    private boolean vencida(TransacaoOnline t) { return t.expiraEm() != null && relogio.get().isAfter(t.expiraEm()); }

    /**
     * Confere o endereço de retorno informado pelo site. Precisa ser http(s), sem usuário na URL, terminar em
     * {@code /pagamento.html}, e bater com a configuração (pagamento.url_site) ou com a origem do CORS, se houver.
     * No modo de demonstração também aceita file:// (site aberto direto do arquivo).
     */
    String validarRetorno(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Endereço de retorno ausente.");
        URI u;
        try { u = URI.create(url.trim()); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Endereço de retorno inválido."); }
        String esquema = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        boolean arquivo = esquema.equals("file") && gateway.demonstracao();
        if (!(esquema.equals("http") || esquema.equals("https") || arquivo) || u.getUserInfo() != null || u.getQuery() != null
                || u.getFragment() != null || u.getPath() == null || !u.getPath().endsWith("/pagamento.html"))
            throw new IllegalArgumentException("Endereço de retorno inválido.");
        String limpa = url.trim();
        if (!urlSiteConfigurada.isEmpty() && !limpa.startsWith(urlSiteConfigurada.replaceAll("/+$", "") + "/"))
            throw new IllegalArgumentException("Endereço de retorno não autorizado.");
        if (!arquivo && !origemPermitida.isEmpty() && !origemPermitida.equals("*")
                && !limpa.startsWith(origemPermitida.replaceAll("/+$", "") + "/"))
            throw new IllegalArgumentException("Endereço de retorno não autorizado.");
        return limpa;
    }

    private Detalhe detalhe(String referencia, TransacaoOnline t) {
        String status = switch (t.status()) {
            case TransacaoOnline.CRIADA, TransacaoOnline.AGUARDANDO -> "AGUARDANDO";
            case TransacaoOnline.APROVADA, TransacaoOnline.APROVADA_SEM_BAIXA -> "APROVADA";
            case TransacaoOnline.DIVERGENTE -> "EM_ANALISE";
            default -> t.status();   // RECUSADA, EXPIRADA, FALHOU
        };
        String mensagem = switch (t.status()) {
            case TransacaoOnline.APROVADA -> "Pagamento confirmado. Obrigado!";
            case TransacaoOnline.APROVADA_SEM_BAIXA -> "Pagamento recebido. A recepção vai conferir a sua mensalidade.";
            case TransacaoOnline.DIVERGENTE -> "Recebemos o pagamento, mas o valor precisa de conferência. A recepção vai entrar em contato.";
            case TransacaoOnline.RECUSADA -> "O pagamento foi recusado. Tente de novo ou pague na recepção.";
            case TransacaoOnline.EXPIRADA -> "O prazo para pagar acabou. Gere um novo pagamento.";
            case TransacaoOnline.FALHOU -> "Não foi possível iniciar o pagamento. Tente de novo ou pague na recepção.";
            default -> null;
        };
        long restante = 0;
        if (t.aberta() && t.expiraEm() != null) restante = Math.max(0, Duration.between(relogio.get(), t.expiraEm()).getSeconds());
        boolean aberta = t.aberta();
        return new Detalhe(referencia, t.metodo(), status, t.valor(), aberta ? t.pixCopiaCola() : null, aberta ? t.pixQrBase64() : null,
                aberta ? t.urlCheckout() : null, restante, gateway.demonstracao(), mensagem);
    }

    private static String limitar(String s) {
        if (s == null) return null;
        return s.length() > 250 ? s.substring(0, 250) : s;
    }

    /** Data de hoje segundo o relógio do serviço (usado por testes). */
    LocalDate hoje() { return relogio.get().toLocalDate(); }
}
