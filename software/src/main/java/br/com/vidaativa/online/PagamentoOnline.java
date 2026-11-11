package br.com.vidaativa.online;

import br.com.vidaativa.config.AppConfig;
import br.com.vidaativa.dao.TransacaoDAO;

import java.time.LocalDateTime;

/**
 * Monta o {@link ServicoPagamentoOnline} a partir do config.properties (chave {@code pagamento.provedor}).
 * Chamado uma vez, ao subir a API. Nunca lança exceção: se algo estiver errado, o pagamento online fica desligado e o
 * motivo aparece no console, e o software e o resto do site continuam funcionando como antes.
 */
public final class PagamentoOnline {
    private PagamentoOnline() { }

    /** O serviço pronto, ou null se estiver desligado ou mal configurado. */
    public static ServicoPagamentoOnline daConfiguracao() {
        String provedor = AppConfig.pagamentoProvedor();
        GatewayPagamento gateway;
        try {
            switch (provedor) {
                case "desligado", "" -> { return null; }
                case "simulado" -> {
                    gateway = new SimuladoGateway();
                    System.out.println("[Pagamento] ATENÇÃO: modo de DEMONSTRAÇÃO ligado. Nenhum dinheiro é cobrado, e o botão \"simular\" "
                            + "dá baixa de verdade nas mensalidades do banco. Não use com alunos reais.");
                }
                case "mercadopago" -> {
                    String base = AppConfig.mpUrlBase().toLowerCase();
                    if (!base.startsWith("https://") && !base.startsWith("http://localhost") && !base.startsWith("http://127.0.0.1"))
                        throw new IllegalStateException("pagamento.mp.url_base precisa começar com https:// (o token seguiria sem proteção).");
                    if (!AppConfig.pagamentoUrlApiPublica().isEmpty() && AppConfig.mpWebhookSecret().isEmpty())
                        throw new IllegalStateException("Há pagamento.url_api_publica, mas falta pagamento.mp.webhook_secret: sem o segredo qualquer um poderia chamar o aviso.");
                    if (AppConfig.pagamentoUrlSite().isEmpty())
                        System.err.println("[Pagamento] AVISO: pagamento.url_site vazio; o retorno do cartão aceita qualquer endereço do site. Preencha com o endereço do site dos alunos.");
                    gateway = new MercadoPagoGateway(AppConfig.mpUrlBase(), AppConfig.mpAccessToken(), AppConfig.mpWebhookSecret(),
                            AppConfig.pagamentoUrlApiPublica().isEmpty() ? "" : AppConfig.pagamentoUrlApiPublica().replaceAll("/+$", "") + "/api/pagamentos/webhook/mercadopago");
                    System.out.println("[Pagamento] Mercado Pago ligado" + (AppConfig.pagamentoUrlApiPublica().isEmpty()
                            ? " (sem webhook: a confirmação vem da conferência a cada 30 s)." : "."));
                }
                default -> {
                    System.err.println("[Pagamento] pagamento.provedor=\"" + provedor + "\" não existe. Use desligado, simulado ou mercadopago. Pagamento online DESLIGADO.");
                    return null;
                }
            }
            TransacaoDAO.garantirTabela();
            return new ServicoPagamentoOnline(gateway, new TransacaoDAO(), LocalDateTime::now, AppConfig.pagamentoPixMinutos(),
                    AppConfig.pagamentoCartaoMinutos(), AppConfig.pagamentoUrlSite(), AppConfig.corsOrigin());
        } catch (Exception e) {
            System.err.println("[Pagamento] Pagamento online DESLIGADO: " + e.getMessage());
            return null;
        }
    }
}
