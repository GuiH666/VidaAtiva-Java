package br.com.vidaativa.online;

import com.google.gson.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Integração com o Mercado Pago (Pix e cartão). Escrita com base na documentação pública da API do Mercado Pago, mas
 * <b>nunca executada contra o Mercado Pago de verdade</b> (precisa da conta e do token da academia). Os testes
 * ({@code TestesApi}) usam um servidor falso que imita as respostas; antes de ir para produção, faça um pagamento de teste
 * com as credenciais de TESTE do Mercado Pago (veja docs/PAGAMENTO-ONLINE.md).
 *
 * <h2>Como funciona</h2>
 * <ul>
 *   <li><b>Pix:</b> {@code POST /v1/payments} com {@code payment_method_id=pix}. A resposta traz o código copia e cola
 *       ({@code qr_code}) e a imagem do QR ({@code qr_code_base64}).</li>
 *   <li><b>Cartão:</b> {@code POST /checkout/preferences} (Checkout Pro): o aluno é levado à página do Mercado Pago
 *       ({@code init_point}), digita o cartão LÁ e volta ao site. Nenhum dado de cartão passa por aqui.</li>
 *   <li><b>Consulta:</b> {@code GET /v1/payments/{id}} (Pix) ou {@code GET /v1/payments/search?external_reference=...}
 *       (cartão). {@code external_reference} é o nosso código da transação ({@code VA-<id>}).</li>
 *   <li><b>Aviso (webhook):</b> o corpo do aviso nunca é usado como prova. Confere-se a assinatura {@code x-signature}
 *       (HMAC-SHA256 com o segredo do webhook) quando o segredo está configurado e, em qualquer caso, o pagamento é
 *       consultado de novo na API com o token da academia.</li>
 * </ul>
 * O token ({@code access_token}) é secreto: vem só do config.properties/variável de ambiente e nunca é registrado em log.
 */
public final class MercadoPagoGateway implements GatewayPagamento {
    private static final DateTimeFormatter ISO_COM_FUSO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final String base;
    private final String token;
    private final String segredoWebhook;
    private final String urlNotificacao;
    private final HttpClient http;

    /**
     * @param base            "https://api.mercadopago.com" (nos testes, o servidor falso)
     * @param token           access token da conta (de TESTE ou de PRODUÇÃO)
     * @param segredoWebhook  segredo da assinatura dos avisos (painel do Mercado Pago); vazio = não confere assinatura
     * @param urlNotificacao  URL pública HTTPS onde o Mercado Pago deve avisar; vazio = sem webhook (só consulta periódica)
     */
    public MercadoPagoGateway(String base, String token, String segredoWebhook, String urlNotificacao) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Falta o access token do Mercado Pago (pagamento.mp.access_token).");
        this.base = base.replaceAll("/+$", "");
        this.token = token.trim();
        this.segredoWebhook = segredoWebhook == null ? "" : segredoWebhook.trim();
        this.urlNotificacao = urlNotificacao == null ? "" : urlNotificacao.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Override public String nome() { return "MERCADOPAGO"; }
    @Override public boolean demonstracao() { return false; }

    // ------------------------------------------------------------------ criação

    @Override public Resposta criarPix(Pedido p) throws Exception {
        JsonObject corpo = new JsonObject();
        corpo.addProperty("transaction_amount", p.valor());
        corpo.addProperty("description", p.descricao());
        corpo.addProperty("payment_method_id", "pix");
        corpo.addProperty("external_reference", p.referenciaExterna());
        corpo.addProperty("date_of_expiration", ISO_COM_FUSO.format(p.expiraEm().atZone(FUSO)));
        if (urlNotificacaoValida()) corpo.addProperty("notification_url", urlNotificacao);
        JsonObject pagador = new JsonObject();
        pagador.addProperty("email", p.emailPagador());
        String[] nome = separarNome(p.nomePagador());
        pagador.addProperty("first_name", nome[0]);
        pagador.addProperty("last_name", nome[1]);
        corpo.add("payer", pagador);

        // O Mercado Pago exige X-Idempotency-Key em criações: repetir a mesma chave não cria um segundo pagamento.
        JsonObject r = enviar("POST", "/v1/payments", corpo, p.referenciaExterna());
        JsonObject dados = r.has("point_of_interaction") && r.getAsJsonObject("point_of_interaction").has("transaction_data")
                ? r.getAsJsonObject("point_of_interaction").getAsJsonObject("transaction_data") : null;
        String copiaCola = dados == null ? null : texto(dados, "qr_code");
        if (copiaCola == null || copiaCola.isBlank()) throw new IllegalStateException("O Mercado Pago não devolveu o código Pix.");
        return new Resposta(texto(r, "id"), copiaCola, dados == null ? null : texto(dados, "qr_code_base64"), null);
    }

    @Override public Resposta criarCartao(Pedido p) throws Exception {
        JsonObject item = new JsonObject();
        item.addProperty("title", p.descricao());
        item.addProperty("quantity", 1);
        item.addProperty("currency_id", "BRL");
        item.addProperty("unit_price", p.valor());
        JsonArray itens = new JsonArray();
        itens.add(item);

        JsonObject corpo = new JsonObject();
        corpo.add("items", itens);
        corpo.addProperty("external_reference", p.referenciaExterna());
        // Só cartão (crédito e débito), em 1 parcela: mensalidade não se parcela.
        JsonObject metodos = new JsonObject();
        JsonArray excluidos = new JsonArray();
        for (String tipo : new String[]{"ticket", "atm", "bank_transfer"}) {
            JsonObject t = new JsonObject();
            t.addProperty("id", tipo);
            excluidos.add(t);
        }
        metodos.add("excluded_payment_types", excluidos);
        metodos.addProperty("installments", 1);
        corpo.add("payment_methods", metodos);
        JsonObject volta = new JsonObject();
        volta.addProperty("success", p.urlRetorno() + "?retorno=sucesso&ref=" + p.referenciaExterna());
        volta.addProperty("pending", p.urlRetorno() + "?retorno=pendente&ref=" + p.referenciaExterna());
        volta.addProperty("failure", p.urlRetorno() + "?retorno=falha&ref=" + p.referenciaExterna());
        corpo.add("back_urls", volta);
        // auto_return só é aceito pelo Mercado Pago com endereço HTTPS; em teste local (http) o aluno clica em "Voltar ao site".
        if (p.urlRetorno().startsWith("https://")) corpo.addProperty("auto_return", "approved");
        corpo.addProperty("expires", true);
        corpo.addProperty("expiration_date_to", ISO_COM_FUSO.format(p.expiraEm().atZone(FUSO)));
        if (urlNotificacaoValida()) corpo.addProperty("notification_url", urlNotificacao);

        JsonObject r = enviar("POST", "/checkout/preferences", corpo, p.referenciaExterna());
        String url = texto(r, "init_point");
        if (url == null || url.isBlank()) throw new IllegalStateException("O Mercado Pago não devolveu a página de pagamento.");
        return new Resposta(texto(r, "id"), null, null, url);
    }

    // ------------------------------------------------------------------ consulta

    @Override public Consulta consultar(String metodo, String idExterno, String referenciaExterna) throws Exception {
        if ("PIX".equals(metodo)) {
            return traduzir(enviar("GET", "/v1/payments/" + URLEncoder.encode(idExterno, StandardCharsets.UTF_8), null, null), true);
        }
        // Cartão: o id guardado é o da preferência; os pagamentos feitos nela são achados pelo external_reference.
        JsonObject r = enviar("GET", "/v1/payments/search?external_reference=" + URLEncoder.encode(referenciaExterna, StandardCharsets.UTF_8)
                + "&sort=date_created&criteria=desc&limit=20", null, null);
        JsonArray resultados = r.has("results") && r.get("results").isJsonArray() ? r.getAsJsonArray("results") : new JsonArray();
        Consulta melhor = null;
        for (JsonElement e : resultados) {
            if (!e.isJsonObject()) continue;
            JsonObject pagamento = e.getAsJsonObject();
            // Confere de novo o código: a busca já filtra, mas aprovar pagamento de OUTRA referência seria grave.
            if (!referenciaExterna.equals(texto(pagamento, "external_reference"))) continue;
            Consulta c = traduzir(pagamento, false);
            if (c.situacao() == Situacao.APROVADO) return c;
            if (melhor == null) melhor = c;
        }
        return melhor != null ? melhor : new Consulta(Situacao.AGUARDANDO, null, idExterno, referenciaExterna, "sem tentativa de pagamento");
    }

    /**
     * Traduz um objeto "payment" do Mercado Pago. Para Pix, recusado/cancelado/expirado encerram a transação; para cartão
     * uma tentativa recusada NÃO encerra (o aluno pode tentar outro cartão na mesma página), então fica AGUARDANDO.
     */
    private Consulta traduzir(JsonObject pagamento, boolean pix) {
        String status = texto(pagamento, "status");
        String detalhe = texto(pagamento, "status_detail");
        Situacao s;
        if ("approved".equals(status)) s = Situacao.APROVADO;
        else if (("rejected".equals(status) || "cancelled".equals(status)) && pix)
            s = "expired".equals(detalhe) ? Situacao.EXPIRADO : Situacao.RECUSADO;
        else s = Situacao.AGUARDANDO;
        BigDecimal pago = null;
        if (pagamento.has("transaction_amount") && pagamento.get("transaction_amount").isJsonPrimitive())
            pago = pagamento.get("transaction_amount").getAsBigDecimal();
        return new Consulta(s, pago, texto(pagamento, "id"), texto(pagamento, "external_reference"), status + (detalhe == null ? "" : "/" + detalhe));
    }

    // ------------------------------------------------------------------ aviso (webhook)

    @Override public Consulta processarNotificacao(Map<String, String> cabecalhos, String query, String corpo) throws Exception {
        String id = parametro(query, "data.id");
        String tipo = parametro(query, "type");
        if (tipo == null) tipo = parametro(query, "topic");
        if (id == null || tipo == null) {   // o Mercado Pago também manda o mesmo aviso no corpo JSON
            try {
                JsonObject o = JsonParser.parseString(corpo == null ? "" : corpo).getAsJsonObject();
                if (id == null && o.has("data") && o.get("data").isJsonObject()) id = texto(o.getAsJsonObject("data"), "id");
                if (tipo == null) tipo = texto(o, "type");
            } catch (RuntimeException ignorado) { /* corpo vazio ou sem JSON */ }
        }
        if (!segredoWebhook.isEmpty()) conferirAssinatura(cabecalhos.get("x-signature"), cabecalhos.get("x-request-id"), parametro(query, "data.id") != null ? parametro(query, "data.id") : id);
        if (!"payment".equals(tipo) || id == null || !id.matches("\\d{1,20}")) return null;
        // Nada do aviso é aceito como prova: o pagamento é consultado na API com o token da academia.
        // pix=false de propósito: o aviso pode ser de uma tentativa recusada de CARTÃO (o aluno ainda pode tentar outro cartão),
        // então ele só serve para confirmar aprovação. Recusa/expiração do Pix são percebidas pela conferência periódica.
        return traduzir(enviar("GET", "/v1/payments/" + id, null, null), false);
    }

    /**
     * Confere o cabeçalho {@code x-signature: ts=...,v1=...}. O texto assinado é
     * {@code id:<data.id em minúsculas>;request-id:<x-request-id>;ts:<ts>;} com HMAC-SHA256 e o segredo do webhook.
     *
     * @throws SecurityException se faltar a assinatura ou ela não bater
     */
    void conferirAssinatura(String xSignature, String requestId, String dataId) {
        if (xSignature == null || xSignature.isBlank()) throw new SecurityException("Aviso sem assinatura.");
        String ts = null, v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1];
            if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) throw new SecurityException("Assinatura malformada.");
        StringBuilder manifesto = new StringBuilder();
        if (dataId != null) manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (requestId != null) manifesto.append("request-id:").append(requestId).append(';');
        manifesto.append("ts:").append(ts).append(';');
        String esperado = hmacHex(segredoWebhook, manifesto.toString());
        // Comparação em tempo constante: não revela, pelo tempo de resposta, quantos caracteres coincidem.
        if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)))
            throw new SecurityException("Assinatura inválida.");
    }

    /** HMAC-SHA256 em hexadecimal minúsculo (também usado pelos testes para fabricar assinaturas). */
    public static String hmacHex(String segredo, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(texto.getBytes(StandardCharsets.UTF_8))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * Chama a API do Mercado Pago e devolve o JSON da resposta.
     *
     * @param idempotencia valor do cabeçalho X-Idempotency-Key (só nas criações)
     * @throws IllegalStateException se o Mercado Pago responder com erro; a mensagem NÃO inclui o token
     */
    private JsonObject enviar(String metodo, String caminho, JsonObject corpo, String idempotencia) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json");
        if (idempotencia != null) b.header("X-Idempotency-Key", idempotencia);
        if (corpo != null) b.header("Content-Type", "application/json");
        b.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo.toString(), StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() / 100 != 2) {
            System.err.println("[Pagamento] Mercado Pago respondeu " + r.statusCode() + " em " + metodo + " " + caminho.replaceAll("\\?.*", ""));
            throw new IllegalStateException(r.statusCode() == 401 || r.statusCode() == 403
                    ? "O Mercado Pago recusou as credenciais da academia. Confira o access token."
                    : "O Mercado Pago não conseguiu processar o pedido (código " + r.statusCode() + ").");
        }
        JsonElement e = JsonParser.parseString(r.body());
        if (!e.isJsonObject()) throw new IllegalStateException("Resposta inesperada do Mercado Pago.");
        return e.getAsJsonObject();
    }

    // ------------------------------------------------------------------ apoio

    private boolean urlNotificacaoValida() { return urlNotificacao.startsWith("https://"); }

    private static String texto(JsonObject o, String campo) {
        JsonElement e = o.get(campo);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    private static String parametro(String query, String nome) {
        if (query == null) return null;
        for (String par : query.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length == 2 && kv[0].equals(nome)) return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    /** "Maria da Silva Souza" -> {"Maria", "da Silva Souza"}; nome único -> {"Maria", "."}. */
    private static String[] separarNome(String nome) {
        String[] p = (nome == null ? "" : nome.trim()).split("\\s+", 2);
        return new String[]{p[0].isEmpty() ? "Aluno" : p[0], p.length > 1 ? p[1] : "."};
    }
}
