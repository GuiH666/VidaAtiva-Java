package br.com.vidaativa.online;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Ponte entre o software e a empresa que processa o pagamento (o "gateway", ex.: Mercado Pago).
 * <p>
 * É uma interface para que o resto do sistema não dependa de nenhuma empresa: {@link SimuladoGateway} (demonstração, sem
 * dinheiro) e {@link MercadoPagoGateway} (real) são intercambiáveis; para usar outra (Asaas, Pagar.me, Stripe...) basta
 * escrever outra classe que implemente esta interface.
 *
 * <p><b>Regra de ouro de segurança:</b> dados de cartão NUNCA passam por este sistema. O cartão é digitado na página do
 * próprio gateway ({@code urlCheckout}); aqui só se recebe de volta "aprovado" ou "recusado". Pix gera um código (copia e
 * cola) que o aluno paga no aplicativo do banco.
 */
public interface GatewayPagamento {

    /** Nome gravado em {@code transacoes_pagamento.provedor}: "SIMULADO" ou "MERCADOPAGO". */
    String nome();

    /** True se NÃO é dinheiro de verdade (permite o botão "simular pagamento" e mostra o aviso de demonstração). */
    boolean demonstracao();

    /** Cria uma cobrança Pix (código copia e cola e, se o gateway fornecer, a imagem do QR Code). */
    Resposta criarPix(Pedido pedido) throws Exception;

    /** Cria uma página de pagamento com cartão, hospedada no gateway. */
    Resposta criarCartao(Pedido pedido) throws Exception;

    /**
     * Pergunta ao gateway como está um pagamento. É a ÚNICA fonte de verdade: o sistema nunca aceita "foi pago" vindo do
     * navegador do aluno nem do corpo de uma notificação, só desta consulta feita com a chave secreta da academia.
     *
     * @param metodo PIX ou CARTAO
     */
    Consulta consultar(String metodo, String idExterno, String referenciaExterna) throws Exception;

    /**
     * Trata um aviso (webhook) enviado pelo gateway: confere a assinatura, extrai de qual pagamento se trata e CONSULTA o
     * gateway (nunca confia no corpo). Devolve null se o aviso não for sobre pagamento.
     *
     * @param cabecalhos nomes em minúsculas
     * @param query      texto depois do "?" da URL (pode ser vazio)
     * @throws SecurityException se a assinatura estiver errada
     */
    Consulta processarNotificacao(Map<String, String> cabecalhos, String query, String corpo) throws Exception;

    /**
     * O que enviar ao gateway.
     *
     * @param referenciaExterna nosso código da transação (ex.: "VA-15"); volta nas consultas e avisos
     * @param urlRetorno        página do site para onde o gateway devolve o aluno depois de pagar (cartão)
     */
    record Pedido(String referenciaExterna, BigDecimal valor, String descricao, String emailPagador, String nomePagador,
                  LocalDateTime expiraEm, String urlRetorno) { }

    /** O que o gateway devolveu ao criar o pagamento. */
    record Resposta(String idExterno, String pixCopiaCola, String pixQrBase64, String urlCheckout) { }

    /** Situação de um pagamento no gateway, já traduzida para o nosso vocabulário. */
    enum Situacao { AGUARDANDO, APROVADO, RECUSADO, EXPIRADO }

    /**
     * Resposta de uma consulta. {@code valorPago} é o valor efetivamente recebido (só importa quando APROVADO);
     * {@code idExterno} é o id do pagamento no gateway; {@code referenciaExterna} é o nosso código ("VA-15").
     */
    record Consulta(Situacao situacao, BigDecimal valorPago, String idExterno, String referenciaExterna, String detalhe) { }
}
