package br.com.vidaativa.api;

import br.com.vidaativa.config.AppConfig;
import br.com.vidaativa.model.*;
import br.com.vidaativa.online.PagamentoOnline;
import br.com.vidaativa.online.ServicoPagamentoOnline;
import br.com.vidaativa.security.TokenService;
import br.com.vidaativa.service.Precificacao;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * API REST do site do aluno. Lê o MESMO banco que o software grava (banco central), então tudo que a
 * recepção e os professores cadastram aparece no site sem copiar dados.
 *
 * <h2>Rotas</h2>
 * Todas as respostas são JSON em UTF-8. Erros têm sempre o formato {@code {"erro": "mensagem"}}.
 * <pre>
 * Método  Caminho                     Login?  O que devolve
 * ------  --------------------------  ------  ------------------------------------------------------------------
 * GET     /api/status                 não     {sistema, status}: serve para testar se a API está no ar
 * GET     /api/planos                 não     lista de planos (id, nome, valorMensal, descricao), p/ a página Planos
 * POST    /api/login                  não     corpo {login, senha} -> {token, expiraEmSegundos, nome}.
 *                                             Só ALUNO entra (gerente/funcionário/professor recebem 401)
 * GET     /api/aluno/me               sim     perfil do aluno logado (PerfilAluno), com plano, vencimento e situação
 * GET     /api/aluno/me/fichas        sim     fichas de treino ATIVAS do aluno (com treinos e exercícios)
 * GET     /api/aluno/me/pagamentos    sim     mensalidades do aluno (as canceladas não aparecem)
 * GET     /api/aluno/me/avaliacoes    sim     avaliações físicas do aluno (com o IMC calculado)
 * POST    /api/aluno/me/senha         sim     corpo {senhaAtual, novaSenha} -> {ok: true}
 * GET     /api/aluno/me/pagamento-online                        sim  {ativo, provedor, demonstracao, metodos}: o pagamento online está ligado?
 * POST    /api/aluno/me/pagamentos/AAAA-MM/online               sim  corpo {metodo: PIX|CARTAO, retorno} -> começa (ou retoma) o pagamento
 * GET     /api/aluno/me/pagamentos/AAAA-MM/online               sim  situação do pagamento online (confere com o gateway)
 * POST    /api/aluno/me/pagamentos/AAAA-MM/online/simular       sim  só no modo demonstração: corpo {aprovar: true|false}
 * POST    /api/pagamentos/webhook/mercadopago                   não  aviso do Mercado Pago (assinatura conferida; nada do corpo é aceito como prova)
 * </pre>
 * As rotas de pagamento online respondem 404 quando {@code pagamento.provedor=desligado}. Detalhes, segurança e limites em
 * {@link ServicoPagamentoOnline} e docs/PAGAMENTO-ONLINE.md.
 * "Login? sim" significa enviar o cabeçalho {@code Authorization: Bearer <token>} com o token recebido em /api/login.
 * Outros códigos HTTP: 204 (pré-voo CORS, resposta a OPTIONS em qualquer caminho), 400 (JSON inválido, campo faltando ou
 * senha nova inválida), 401 (sem login, token inválido/expirado ou credenciais erradas), 404 (rota inexistente),
 * 405 (método HTTP errado para a rota), 413 (corpo maior que o limite), 429 (muitas tentativas de login) e
 * 500 (erro inesperado; os detalhes vão só para o console, nunca para o cliente).
 *
 * <h2>Token de login</h2>
 * Depois do login o aluno recebe um token assinado com HMAC-SHA256 (veja {@link TokenService}) que vale por 8 horas
 * ({@code VALIDADE_TOKEN_SEGUNDOS}). Ele carrega o id do USUÁRIO e a data de expiração; o servidor não guarda sessões.
 * O segredo da assinatura vem do {@code api.secret} do config.properties.
 *
 * <h2>Limite de tentativas de login</h2>
 * Cada IP pode errar o login no máximo 5 vezes (MAX_FALHAS_LOGIN) dentro de 10 minutos (JANELA_FALHAS_MS). Na 6ª
 * tentativa dentro da janela a API responde 429 sem nem olhar a senha, mesmo que ela esteja certa. Um login bem-sucedido
 * zera o contador daquele IP. A contagem fica só em memória: reiniciar a API libera todo mundo. Se a API ficar atrás de
 * um proxy reverso, todos os usuários aparecerão com o IP do proxy e dividirão o mesmo limite.
 *
 * <h2>CORS</h2>
 * O navegador só deixa o site (que roda em outro endereço/porta que a API) ler as respostas se a API autorizar a origem
 * dele. A origem permitida é o {@code api.cors.origin} do config.properties; vazio ou "*" libera qualquer origem (bom
 * para desenvolvimento; em produção coloque o endereço exato do site). O pré-voo (requisição OPTIONS que o navegador faz
 * antes) é respondido com 204 e os cabeçalhos Access-Control-*. Como o token vai no cabeçalho Authorization (e não em
 * cookie), liberar "*" não deixa outro site agir em nome do aluno logado, mas ainda assim convém restringir.
 *
 * <h2>Limite do corpo</h2>
 * O corpo de uma requisição POST pode ter no máximo 8 KB (MAX_CORPO); acima disso a API responde 413 sem ler o resto.
 * Login e troca de senha só recebem textos curtos, então isso protege a memória do servidor contra envios gigantes.
 *
 * <h2>Por que nenhuma rota lista todos os alunos</h2>
 * Os dados dos alunos (documento, telefone, data de nascimento, plano, pagamentos) são pessoais. Por isso toda rota
 * {@code /api/aluno/me/*} descobre QUEM é o aluno pelo token e nunca por um id enviado pelo cliente: assim um aluno
 * logado não consegue ler os dados de outro trocando um número na URL, e não existe uma rota "todos os alunos" que
 * vazaria a base inteira para qualquer um que tivesse um login (ou um token roubado). Os dados enviados também passam por
 * records próprios (PerfilAluno, FichaPublica...) que deixam de fora documento, ids e nome de usuário. O teste
 * {@code TestesApi} verifica isso. Se for criar uma rota nova, mantenha esse padrão.
 *
 * <p>Para uma rota nova: acrescente um {@code case} no switch de {@link #rotear}, chame {@code exigir} com o método HTTP
 * e, se exigir login, obtenha o aluno com {@link #aluno}.
 *
 * <p>A lógica de dados fica atrás da interface {@link PortalDados} (banco de verdade em {@link DadosBanco}; os testes
 * usam uma versão em memória). Quem inicia a API: {@code Main} (via {@link #iniciarEmSegundoPlano}) ou o {@link #main}.
 */
public class ApiServer {
    // Limites de segurança (explicados no Javadoc da classe). MAX_CORPO em bytes; JANELA_FALHAS_MS em milissegundos
    // (10 minutos); VALIDADE_TOKEN_SEGUNDOS em segundos (8 horas).
    private static final int MAX_CORPO = 8 * 1024;
    private static final int MAX_FALHAS_LOGIN = 5;
    private static final long JANELA_FALHAS_MS = 10 * 60 * 1000L;
    private static final long VALIDADE_TOKEN_SEGUNDOS = 8 * 60 * 60L;

    // API iniciada por iniciarEmSegundoPlano(); serve para não subir duas vezes e para rodando()/porta() responderem.
    private static ApiServer ativo;

    private final PortalDados dados;
    private final TokenService tokens;
    private final String corsOrigin;
    // Pagamento online (Pix/cartão). null = desligado: as rotas de pagamento online respondem 404.
    private final ServicoPagamentoOnline online;
    private ScheduledExecutorService conferencia;
    // serializeNulls: campos sem valor saem como null no JSON (em vez de sumir), assim o site sempre encontra a chave.
    // O adaptador de LocalDate existe porque o Gson não consegue serializar java.time sozinho no Java 17+ (os módulos do
    // JDK bloqueiam o acesso por reflexão); com ele as datas viram texto "AAAA-MM-DD".
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new DataAdapter().nullSafe())
            .serializeNulls()
            .create();
    // Para cada IP, os instantes (em ms) das últimas falhas de login, da mais antiga para a mais nova.
    private final Map<String, Deque<Long>> falhasLogin = new ConcurrentHashMap<>();
    private HttpServer server;

    /**
     * @param dados de onde vêm os dados (banco de verdade ou, nos testes, uma versão em memória)
     * @param tokens emissor/validador dos tokens de login
     * @param corsOrigin origem do site autorizada no CORS; nulo ou vazio vira "*" (qualquer origem)
     */
    public ApiServer(PortalDados dados, TokenService tokens, String corsOrigin) {
        this(dados, tokens, corsOrigin, null);
    }

    /**
     * @param online serviço de pagamento online, ou null para deixar o recurso desligado
     */
    public ApiServer(PortalDados dados, TokenService tokens, String corsOrigin, ServicoPagamentoOnline online) {
        this.online = online;
        this.dados = dados;
        this.tokens = tokens;
        this.corsOrigin = corsOrigin == null || corsOrigin.isBlank() ? "*" : corsOrigin;
    }

    // ---------------------------------------------------------------- ciclo de vida

    /**
     * Sobe o servidor HTTP.
     *
     * @param porta porta TCP; use 0 para o sistema escolher uma livre (os testes fazem isso)
     * @return a porta realmente em uso
     * @throws IOException se não conseguir abrir a porta (ex.: {@link BindException} se já estiver ocupada)
     */
    public int start(int porta) throws IOException {
        server = HttpServer.create(new InetSocketAddress(porta), 0);
        // Um único "contexto" na raiz recebe TUDO; a escolha da rota é feita à mão em rotear().
        server.createContext("/", this::rotear);
        server.setExecutor(Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "api-vida-ativa");
            // Thread "daemon" não impede o programa de encerrar: ao fechar a janela do software, a API cai junto.
            t.setDaemon(true);
            return t;
        }));
        server.start();
        if (online != null) {
            // A cada 30 s pergunta ao gateway pelos pagamentos em aberto: é o que contabiliza o pagamento mesmo quando o aviso
            // (webhook) do gateway não consegue chegar a este computador (a API roda na rede local da academia).
            conferencia = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "conferencia-pagamentos");
                t.setDaemon(true);
                return t;
            });
            conferencia.scheduleWithFixedDelay(() -> {
                try { online.conferirAbertas(); }
                catch (Throwable e) { System.err.println("[Pagamento] Conferência periódica falhou: " + e.getMessage()); }
            }, 15, 30, TimeUnit.SECONDS);
        }
        return server.getAddress().getPort();
    }

    /** Para o servidor imediatamente (sem esperar requisições em andamento). Não faz nada se nunca foi iniciado. */
    public void stop() {
        if (conferencia != null) conferencia.shutdownNow();
        if (server != null) server.stop(0);
    }

    /**
     * Sobe a API junto com o software (chamado pelo Main). Se a porta estiver ocupada, só avisa no console.
     * Nunca lança exceção: a falha da API não pode impedir o software de abrir. Chamar de novo depois de já iniciada não faz nada.
     */
    public static synchronized void iniciarEmSegundoPlano() {
        if (ativo != null) return;
        try {
            ApiServer api = new ApiServer(new DadosBanco(), new TokenService(AppConfig.apiSecret(), VALIDADE_TOKEN_SEGUNDOS), AppConfig.corsOrigin(), PagamentoOnline.daConfiguracao());
            int porta = api.start(AppConfig.apiPort());
            ativo = api;
            System.out.println("API Vida Ativa em http://localhost:" + porta + "/api/status");
        } catch (BindException e) {
            System.err.println("[API] A porta " + AppConfig.apiPort() + " já está em uso (a API pode estar rodando em outro programa). O software abre normalmente.");
        } catch (Exception e) {
            System.err.println("[API] Não foi possível iniciar: " + e.getMessage());
        }
    }

    /** True se a API foi iniciada por {@link #iniciarEmSegundoPlano()} neste programa. */
    public static boolean rodando() { return ativo != null; }
    /** Porta da API iniciada em segundo plano, ou 0 se ela não estiver rodando. */
    public static int porta() { return ativo == null ? 0 : ativo.server.getAddress().getPort(); }

    /** Para subir só a API (sem a janela do software). */
    public static void main(String[] args) throws Exception {
        ApiServer api = new ApiServer(new DadosBanco(), new TokenService(AppConfig.apiSecret(), VALIDADE_TOKEN_SEGUNDOS), AppConfig.corsOrigin(), PagamentoOnline.daConfiguracao());
        int porta = api.start(AppConfig.apiPort());
        System.out.println("API Vida Ativa em http://localhost:" + porta + "/api/status  (Ctrl+C para parar)");
        Thread.currentThread().join();
    }

    // ---------------------------------------------------------------- roteamento

    /**
     * Porta de entrada de TODA requisição: aplica os cabeçalhos, escolhe a rota pelo caminho e traduz as exceções em
     * respostas JSON de erro. Cada rota confere o método HTTP com {@link #exigir} antes de qualquer outra coisa, então
     * método errado dá 405 mesmo sem login.
     */
    private void rotear(HttpExchange ex) throws IOException {
        try {
            // Primeiro de tudo, para que até as respostas de erro e de pré-voo (OPTIONS) já saiam com os cabeçalhos de CORS.
            cabecalhos(ex);
            String metodo = ex.getRequestMethod();
            String caminho = ex.getRequestURI().getPath();
            // Aceita "/api/status/" igual a "/api/status" (tira a barra final, menos no caminho "/").
            if (caminho.length() > 1 && caminho.endsWith("/")) caminho = caminho.substring(0, caminho.length() - 1);

            if ("OPTIONS".equals(metodo)) {            // pré-voo do navegador (CORS)
                ex.sendResponseHeaders(204, -1);
                return;
            }
            switch (caminho) {
                case "/api/status" -> { exigir(ex, "GET"); json(ex, 200, Map.of("sistema", "Vida Ativa", "status", "online")); }
                case "/api/planos" -> { exigir(ex, "GET"); json(ex, 200, dados.planos()); }
                case "/api/login" -> { exigir(ex, "POST"); login(ex); }
                case "/api/aluno/me" -> { exigir(ex, "GET"); json(ex, 200, perfil(aluno(ex))); }
                case "/api/aluno/me/fichas" -> { exigir(ex, "GET"); json(ex, 200, fichas(aluno(ex))); }
                case "/api/aluno/me/pagamentos" -> { exigir(ex, "GET"); json(ex, 200, pagamentos(aluno(ex))); }
                case "/api/aluno/me/avaliacoes" -> { exigir(ex, "GET"); json(ex, 200, avaliacoes(aluno(ex))); }
                case "/api/aluno/me/senha" -> { exigir(ex, "POST"); senha(ex); }
                case "/api/aluno/me/pagamento-online" -> { exigir(ex, "GET"); aluno(ex); json(ex, 200, online == null ? Map.of("ativo", false) : online.opcoes()); }
                case "/api/pagamentos/webhook/mercadopago" -> { exigir(ex, "POST"); webhook(ex); }
                default -> {
                    // Rotas com o mês na URL: /api/aluno/me/pagamentos/2026-11/online e .../online/simular
                    java.util.regex.Matcher m = ROTA_ONLINE.matcher(caminho);
                    if (!m.matches()) throw new HttpErro(404, "Rota não encontrada.");
                    pagamentoOnline(ex, m.group(1), m.group(2) != null, metodo);
                }
            }
        } catch (HttpErro e) {
            // Erro "esperado", lançado de propósito pelas rotas com o código HTTP certo (401, 404, 429...).
            json(ex, e.status, Map.of("erro", e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Vem da camada de validação (ex.: senha nova curta) e a mensagem já é própria para o usuário: vira 400.
            json(ex, 400, Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            // Qualquer outra falha (banco fora do ar, bug...). O detalhe vai só para o console; o cliente recebe uma
            // mensagem genérica para não vazar informação interna do sistema.
            System.err.println("[API] Erro em " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + ": " + e);
            json(ex, 500, Map.of("erro", "Erro interno. Tente novamente em instantes."));
        } finally {
            // Garante que a conexão HTTP seja encerrada em qualquer caminho (sucesso ou erro).
            ex.close();
        }
    }

    // ---------------------------------------------------------------- rotas

    /**
     * POST /api/login. Confere usuário e senha, aplica o limite de tentativas por IP e devolve o token.
     * Só vale para ALUNO: o login de gerente/funcionário/professor dá o mesmo 401 que senha errada.
     */
    private void login(HttpExchange ex) throws Exception {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        // O bloqueio é conferido ANTES de ler o corpo e de olhar a senha: quem está bloqueado não consegue continuar
        // chutando (nem a senha certa passa) e a API não gasta o cálculo pesado do PBKDF2 com ele.
        if (bloqueado(ip)) throw new HttpErro(429, "Muitas tentativas de login. Aguarde alguns minutos e tente de novo.");
        JsonObject corpo = corpoJson(ex);
        String login = texto(corpo, "login");
        String senha = texto(corpo, "senha");
        // A senha é testada com isEmpty (e não isBlank) porque espaços podem fazer parte de uma senha válida.
        if (login.isBlank() || senha.isEmpty()) throw new HttpErro(400, "Informe usuário e senha.");

        Usuario u = dados.autenticar(login, senha);
        // Credencial certa de um perfil que não é ALUNO (ex.: gerente) também falha aqui: a área do site é só do aluno.
        Aluno a = u != null && "ALUNO".equalsIgnoreCase(u.perfil()) ? dados.alunoPorUsuario(u.id()) : null;
        if (a == null) {
            registrarFalha(ip);
            // Mesma mensagem para usuário inexistente, senha errada e perfil não permitido: assim quem ataca não descobre
            // quais usuários existem.
            throw new HttpErro(401, "Usuário ou senha inválidos.");
        }
        // Login certo: apaga o histórico de falhas deste IP.
        falhasLogin.remove(ip);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("token", tokens.emitir(u.id()));
        resp.put("expiraEmSegundos", tokens.validadeSegundos());
        resp.put("nome", a.nome());
        json(ex, 200, resp);
    }

    /**
     * POST /api/aluno/me/senha. Troca a senha do aluno logado. Resposta 400 se a senha atual estiver errada ou se a nova
     * for inválida (esta última lançada como IllegalArgumentException por {@code dados.alterarSenha}).
     */
    private void senha(HttpExchange ex) throws Exception {
        // O token é validado ANTES de ler o corpo: quem não está logado é rejeitado (401) sem a API sequer processar o
        // conteúdo enviado.
        Aluno a = aluno(ex);
        JsonObject corpo = corpoJson(ex);
        boolean ok = dados.alterarSenha(a.usuarioId(), texto(corpo, "senhaAtual"), texto(corpo, "novaSenha"));
        // 400 e não 401: o aluno ESTÁ logado, o que está errado é o dado enviado.
        if (!ok) throw new HttpErro(400, "A senha atual está incorreta.");
        json(ex, 200, Map.of("ok", true));
    }

    /** Mês (AAAA-MM) e, opcionalmente, "/simular" depois de ".../online". */
    private static final java.util.regex.Pattern ROTA_ONLINE =
            java.util.regex.Pattern.compile("/api/aluno/me/pagamentos/(\\d{4}-\\d{2})/online(/simular)?");
    /** Aviso do gateway: até 16 KB. */
    private static final int MAX_CORPO_WEBHOOK = 16 * 1024;

    /**
     * Rotas do pagamento online de uma mensalidade. O aluno vem do token; a mensalidade é achada POR ele (aluno + mês),
     * então um aluno nunca alcança a cobrança de outro nem escolhe o valor.
     */
    private void pagamentoOnline(HttpExchange ex, String referencia, boolean simular, String metodo) throws Exception {
        if (online == null) throw new HttpErro(404, "O pagamento online não está ativado.");
        Aluno a = aluno(ex);   // 401 antes de qualquer outra coisa
        try {
            if (simular) {
                exigir(ex, "POST");
                if (!online.gateway().demonstracao()) throw new HttpErro(404, "Rota não encontrada.");
                JsonObject corpo = corpoJson(ex);
                JsonElement aprovar = corpo.get("aprovar");
                json(ex, 200, online.simular(a, referencia, aprovar != null && aprovar.isJsonPrimitive() && aprovar.getAsBoolean()));
            } else if ("GET".equals(metodo)) {
                json(ex, 200, online.estado(a, referencia));
            } else {
                exigir(ex, "POST");
                JsonObject corpo = corpoJson(ex);
                json(ex, 200, online.iniciar(a, referencia, texto(corpo, "metodo"), texto(corpo, "retorno")));
            }
        } catch (ServicoPagamentoOnline.LimiteExcedido e) {
            throw new HttpErro(429, e.getMessage());
        } catch (IllegalStateException e) {
            // Gateway fora do ar ou recusou: a mensagem já é própria para o aluno. 503 = tente de novo mais tarde.
            throw new HttpErro(503, e.getMessage());
        }
    }

    /**
     * POST /api/pagamentos/webhook/mercadopago. Rota pública (o Mercado Pago não faz login). É segura porque o corpo do aviso
     * nunca é usado como prova: confere-se a assinatura e o pagamento é consultado na API do Mercado Pago com o token da academia.
     */
    private void webhook(HttpExchange ex) throws Exception {
        if (online == null) throw new HttpErro(404, "O pagamento online não está ativado.");
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_CORPO_WEBHOOK + 1);
        if (bytes.length > MAX_CORPO_WEBHOOK) throw new HttpErro(413, "Requisição grande demais.");
        Map<String, String> cabecalhos = new HashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> { if (!v.isEmpty()) cabecalhos.put(k.toLowerCase(Locale.ROOT), v.get(0)); });
        try {
            online.notificar(cabecalhos, ex.getRequestURI().getRawQuery(), new String(bytes, StandardCharsets.UTF_8));
        } catch (SecurityException e) {
            throw new HttpErro(401, "Assinatura inválida.");
        }
        json(ex, 200, Map.of("ok", true));
    }

    // ---------------------------------------------------------------- DTOs enviados ao site

    // Os records abaixo definem o JSON que o site recebe (os nomes dos campos são os nomes das chaves). Eles existem
    // para NÃO expor os models do banco (Aluno, Pagamento...) diretamente: deixam de fora documento, ids internos e
    // nome de usuário. Se o site precisar de um campo novo, acrescente-o aqui E leia-o em site/assets/js.
    /** Plano do aluno: o nome e o valor de TABELA do plano (não o que ele paga; veja valorMensalidade em PerfilAluno). */
    record PlanoInfo(String nome, BigDecimal valorMensal) { }

    /**
     * Resposta de GET /api/aluno/me. {@code clienteDesde} é a data de início do aluno; {@code proximoVencimento} é
     * {@code Aluno.vencimento}; {@code valorMensalidade} é o valor REALMENTE cobrado (já com a regra do TotalPass, vinda de
     * {@code Precificacao}); {@code situacaoPagamento} é EM_DIA, PENDENTE ou ATRASADO (veja {@link #perfil}).
     */
    record PerfilAluno(String nome, String email, String telefone, PlanoInfo plano, String tipoCliente, String formaPagamento,
                       LocalDate clienteDesde, LocalDate proximoVencimento, BigDecimal valorMensalidade, String situacaoPagamento) { }

    /** Uma ficha de treino como o aluno a vê. {@code professor} é o NOME do professor responsável. */
    record FichaPublica(String nome, String objetivo, Integer frequenciaSemanal, LocalDate dataInicio, LocalDate dataRevisao,
                        String observacoes, String professor, List<Treino> treinos) { }

    /** Uma mensalidade. {@code referencia} é o mês (AAAA-MM) e {@code dataPagamento} é nula enquanto não paga. */
    record PagamentoPublico(String referencia, LocalDate vencimento, BigDecimal valor, String status, String formaPagamento,
                            LocalDate dataPagamento) { }

    /** Uma avaliação física. Peso em kg, altura em metros; {@code imc} é calculado, não gravado no banco. */
    record AvaliacaoPublica(LocalDate data, BigDecimal peso, BigDecimal altura, BigDecimal imc, BigDecimal percentualGordura,
                            String observacoes, String professor) { }

    /**
     * Monta o perfil do aluno. A situação do pagamento resume as cobranças dele: ATRASADO se houver qualquer uma atrasada
     * (tem prioridade), senão PENDENTE se houver alguma pendente, senão EM_DIA. Cobranças PAGAS não pesam.
     */
    private PerfilAluno perfil(Aluno a) throws Exception {
        String situacao = "EM_DIA";
        for (Pagamento p : dados.pagamentos(a.id())) {
            if (p.status().equals("ATRASADO")) { situacao = "ATRASADO"; break; }
            if (p.status().equals("PENDENTE")) situacao = "PENDENTE";
        }
        return new PerfilAluno(a.nome(), a.email(), a.telefone(), new PlanoInfo(a.plano(), a.valorPlano()), a.tipoCliente(),
                a.formaPagamento(), a.dataInicio(), a.vencimento(),
                Precificacao.valorMensalidade(a.plano(), a.valorPlano(), a.tipoCliente()), situacao);
    }

    /** Converte as fichas ATIVAS do aluno (rascunhos e inativas não chegam até aqui) para o formato do site. */
    private List<FichaPublica> fichas(Aluno a) throws Exception {
        List<FichaPublica> lista = new ArrayList<>();
        for (FichaTreino f : dados.fichasAtivas(a.id())) {
            lista.add(new FichaPublica(f.nome(), f.objetivo(), f.frequenciaSemanal(), f.dataInicio(), f.dataRevisao(),
                    f.observacoes(), f.professorNome(), f.treinos()));
        }
        return lista;
    }

    private List<PagamentoPublico> pagamentos(Aluno a) throws Exception {
        List<PagamentoPublico> lista = new ArrayList<>();
        for (Pagamento p : dados.pagamentos(a.id())) {
            lista.add(new PagamentoPublico(p.referencia(), p.vencimento(), p.valor(), p.status(), p.formaPagamento(), p.data()));
        }
        return lista;
    }

    private List<AvaliacaoPublica> avaliacoes(Aluno a) throws Exception {
        List<AvaliacaoPublica> lista = new ArrayList<>();
        for (AvaliacaoFisica v : dados.avaliacoes(a.id())) {
            lista.add(new AvaliacaoPublica(v.data(), v.peso(), v.altura(), v.imc(), v.percentualGordura(), v.observacoes(), v.professorNome()));
        }
        return lista;
    }

    // ---------------------------------------------------------------- autenticação / apoio

    /**
     * Identifica o aluno pelo token do cabeçalho {@code Authorization: Bearer <token>}. É o ÚNICO jeito de uma rota
     * saber de quem são os dados; o aluno nunca vem de parâmetro da requisição.
     *
     * @throws HttpErro 401 se não houver token, ele for inválido/expirado, ou o usuário do token não for um aluno ativo
     */
    private Aluno aluno(HttpExchange ex) throws Exception {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        // regionMatches(true, ...) ignora maiúsculas/minúsculas no "Bearer " (o esquema é case-insensitive no HTTP).
        if (h == null || !h.regionMatches(true, 0, "Bearer ", 0, 7)) throw new HttpErro(401, "Faça login para continuar.");
        int usuarioId = tokens.validar(h.substring(7).trim());
        if (usuarioId < 0) throw new HttpErro(401, "Sessão expirada. Faça login novamente.");
        Aluno a = dados.alunoPorUsuario(usuarioId);
        // Token válido não basta: o usuário precisa continuar sendo um aluno ATIVO agora. Assim, desativar um aluno (ou
        // apresentar o token de um gerente) bloqueia o acesso imediatamente, sem esperar as 8 horas do token.
        if (a == null) throw new HttpErro(401, "Acesso não autorizado.");
        return a;
    }

    /** True se o IP já errou o login {@code MAX_FALHAS_LOGIN} vezes ou mais dentro da janela de {@code JANELA_FALHAS_MS}. */
    private boolean bloqueado(String ip) {
        Deque<Long> f = falhasLogin.get(ip);
        if (f == null) return false;
        // O mapa é seguro para várias threads, mas o ArrayDeque de dentro dele não é: por isso o synchronized no deque.
        synchronized (f) {
            // "Janela deslizante": descarta falhas mais velhas que 10 minutos. A fila está em ordem cronológica, então
            // basta remover da frente até achar uma falha recente.
            long limite = System.currentTimeMillis() - JANELA_FALHAS_MS;
            while (!f.isEmpty() && f.peekFirst() < limite) f.pollFirst();
            return f.size() >= MAX_FALHAS_LOGIN;
        }
    }

    /** Anota agora o horário de uma falha de login deste IP. */
    private void registrarFalha(String ip) {
        Deque<Long> f = falhasLogin.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (f) { f.addLast(System.currentTimeMillis()); }
    }

    /**
     * Confere o método HTTP da rota.
     * @throws HttpErro 405 (com o cabeçalho Allow) se a requisição usou outro método
     */
    private void exigir(HttpExchange ex, String metodo) {
        if (!metodo.equals(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Allow", metodo + ", OPTIONS");
            throw new HttpErro(405, "Método não permitido.");
        }
    }

    /**
     * Lê o corpo da requisição como objeto JSON.
     * @throws HttpErro 413 se passar de {@code MAX_CORPO} bytes; 400 se estiver vazio, não for JSON ou não for um objeto {...}
     */
    private JsonObject corpoJson(HttpExchange ex) throws IOException {
        // Lê no máximo MAX_CORPO + 1 bytes: o byte a mais serve só para saber que estourou o limite, sem carregar na
        // memória um corpo gigante enviado de propósito.
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_CORPO + 1);
        if (bytes.length > MAX_CORPO) throw new HttpErro(413, "Requisição grande demais.");
        try {
            // Corpo vazio não lança exceção: o parser devolve JsonNull, que também cai no 400 logo abaixo.
            JsonElement e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!e.isJsonObject()) throw new HttpErro(400, "JSON inválido.");
            return e.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new HttpErro(400, "JSON inválido.");
        }
    }

    /** Lê um campo de texto do JSON; devolve "" se faltar, for null ou não for um valor simples (ex.: objeto ou lista). */
    private static String texto(JsonObject o, String campo) {
        JsonElement e = o.get(campo);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }

    /** Cabeçalhos de CORS e de segurança, colocados em TODA resposta. */
    private void cabecalhos(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", corsOrigin);
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        // O navegador pode guardar o resultado do pré-voo por 600 s (10 min) em vez de repeti-lo a cada requisição.
        h.set("Access-Control-Max-Age", "600");
        // nosniff: o navegador não "adivinha" outro tipo de conteúdo; Cache-Control no-store: dados pessoais não ficam em cache.
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", "no-store");
        // Com uma origem específica, a resposta depende do cabeçalho Origin; o Vary avisa caches intermediários disso.
        if (!"*".equals(corsOrigin)) h.set("Vary", "Origin");
    }

    /** Envia {@code corpo} como JSON UTF-8 com o status HTTP dado. */
    private void json(HttpExchange ex, int status, Object corpo) throws IOException {
        // Converte antes para saber o tamanho exato em bytes (o Content-Length precisa ser em bytes, não em caracteres).
        byte[] bytes = gson.toJson(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Erro com código HTTP, lançado pelas rotas e convertido em JSON {"erro": ...} por {@link #rotear}. Não checada para poder ser lançada de qualquer método. */
    private static final class HttpErro extends RuntimeException {
        final int status;
        HttpErro(int status, String mensagem) { super(mensagem); this.status = status; }
    }

    /**
     * Ensina o Gson a converter {@link LocalDate} de/para texto "AAAA-MM-DD" (o Gson não consegue ler java.time sozinho
     * no Java 17+). Na prática a API só usa o {@code write} (só devolve JSON); o {@code read} existe por simetria.
     * Registrado com {@code nullSafe()}, então datas nulas viram {@code null} no JSON.
     */
    private static final class DataAdapter extends TypeAdapter<LocalDate> {
        @Override public void write(JsonWriter out, LocalDate v) throws IOException { out.value(v.toString()); }
        @Override public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDate.parse(in.nextString());
        }
    }
}
