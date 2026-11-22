package br.com.vidaativa.online;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway de DEMONSTRAÇÃO: não cobra nada e não fala com nenhuma empresa. Serve para apresentar e testar o fluxo inteiro
 * (aluno escolhe Pix ou cartão, o pagamento "cai" e a cobrança é baixada no software) sem precisar de conta em gateway.
 * <p>
 * O código Pix gerado é falso (não é pagável em nenhum banco) e o "cartão" abre a página {@code checkout-demo.html} do
 * próprio site, que NÃO pede dados de cartão: só tem os botões "Aprovar" e "Recusar". A aprovação chega pela rota
 * {@code .../online/simular} da API, que só existe quando este gateway está ativo ({@code pagamento.provedor=simulado}).
 * <p>
 * O estado fica só em memória: se o programa reiniciar, as transações em aberto da demonstração ficam sem resposta e
 * expiram. Para a demonstração isso basta.
 */
public final class SimuladoGateway implements GatewayPagamento {
    private final Map<String, Situacao> situacoes = new ConcurrentHashMap<>();
    private final Map<String, String> referencias = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> valores = new ConcurrentHashMap<>();

    @Override public String nome() { return "SIMULADO"; }
    @Override public boolean demonstracao() { return true; }

    @Override public Resposta criarPix(Pedido p) {
        String id = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        registrar(id, p);
        String codigo = "DEMO-PIX-" + id + "-CODIGO-DE-DEMONSTRACAO-NAO-PAGAVEL";
        return new Resposta(id, codigo, null, null);
    }

    @Override public Resposta criarCartao(Pedido p) {
        String id = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        registrar(id, p);
        // A página de demonstração fica ao lado da página de pagamentos do site.
        String base = p.urlRetorno().replaceAll("pagamento\\.html.*$", "");
        return new Resposta(id, null, null, base + "checkout-demo.html?t=" + id + "&ref=" + p.referenciaExterna());
    }

    private void registrar(String id, Pedido p) {
        situacoes.put(id, Situacao.AGUARDANDO);
        referencias.put(id, p.referenciaExterna());
        valores.put(id, p.valor());
    }

    /** Define o resultado de uma transação de demonstração (usado pela rota de simulação). */
    public void definir(String idExterno, Situacao situacao) {
        situacoes.computeIfPresent(idExterno, (k, atual) -> atual == Situacao.AGUARDANDO ? situacao : atual);
    }

    @Override public Consulta consultar(String metodo, String idExterno, String referenciaExterna) {
        Situacao s = situacoes.getOrDefault(idExterno, Situacao.AGUARDANDO);
        // O valor "pago" é o do pedido: na demonstração não há como pagar um valor diferente.
        return new Consulta(s, valores.get(idExterno), idExterno, referenciaExterna, "demonstração");
    }

    /** A demonstração não tem avisos (webhook). */
    @Override public Consulta processarNotificacao(Map<String, String> cabecalhos, String query, String corpo) { return null; }

    /** Código de referência que o gateway guardou para a transação (só para depuração e testes). */
    public String referenciaDe(String idExterno) { return referencias.get(idExterno); }
}
