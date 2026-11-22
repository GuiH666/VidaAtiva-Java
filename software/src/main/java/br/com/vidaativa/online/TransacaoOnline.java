package br.com.vidaativa.online;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Uma tentativa de pagamento ONLINE de uma cobrança (linha da tabela {@code transacoes_pagamento}).
 * <p>
 * Uma cobrança ({@code pagamentos}) pode ter várias transações (o aluno gera um Pix, deixa vencer, gera outro...). O dinheiro
 * só conta quando a transação vira {@link #APROVADA}: nesse instante a cobrança é baixada como paga pelo mesmo código que
 * a recepção usa ({@code PagamentoDAO.registrarPagamento}).
 *
 * <h2>Estados</h2>
 * <pre>
 * CRIADA              gravada, ainda não enviada ao gateway (se o programa cair aqui, o gateway nunca soube dela)
 * AGUARDANDO          o gateway criou o Pix/link; esperando o aluno pagar
 * APROVADA            paga e a cobrança foi baixada (estado final)
 * RECUSADA            o gateway recusou o Pix (final)
 * EXPIRADA            o prazo acabou sem pagamento (final; se o pagamento aparecer depois, ainda é aceito)
 * FALHOU              o gateway não conseguiu criar o pagamento (final)
 * APROVADA_SEM_BAIXA  pagou, mas a cobrança já estava paga/cancelada: precisa de conferência humana (devolução?)
 * DIVERGENTE          pagou um valor diferente do cobrado: precisa de conferência humana, nada foi baixado
 * CONFERIDA           a recepção já resolveu uma das duas acima (marcada pela tela do software)
 * </pre>
 *
 * @param idExterno identificador no gateway (null enquanto CRIADA)
 * @param pixQrBase64 imagem PNG do QR Code em base64 (só Pix; pode ser null)
 * @param urlCheckout página de pagamento do gateway (só cartão)
 */
public record TransacaoOnline(int id, int pagamentoId, int alunoId, String provedor, String metodo, String idExterno,
                              String status, BigDecimal valor, String pixCopiaCola, String pixQrBase64, String urlCheckout,
                              LocalDateTime expiraEm, LocalDateTime criadoEm, String observacao) {

    public static final String CRIADA = "CRIADA";
    public static final String AGUARDANDO = "AGUARDANDO";
    public static final String APROVADA = "APROVADA";
    public static final String RECUSADA = "RECUSADA";
    public static final String EXPIRADA = "EXPIRADA";
    public static final String FALHOU = "FALHOU";
    public static final String APROVADA_SEM_BAIXA = "APROVADA_SEM_BAIXA";
    public static final String DIVERGENTE = "DIVERGENTE";
    /** A recepção já olhou uma transação APROVADA_SEM_BAIXA/DIVERGENTE e resolveu (devolveu, baixou à mão...). Estado final. */
    public static final String CONFERIDA = "CONFERIDA";

    /** Estados em que ainda vale a pena perguntar ao gateway se o aluno pagou. */
    public static final Set<String> ABERTAS = Set.of(CRIADA, AGUARDANDO);
    /** Estados dos quais uma aprovação ainda é aceita (EXPIRADA entra: dinheiro recebido vale mais que o prazo local). */
    public static final Set<String> ACEITAM_APROVACAO = Set.of(CRIADA, AGUARDANDO, EXPIRADA);

    /** Está esperando o pagamento. */
    public boolean aberta() { return ABERTAS.contains(status); }

    /** Cópia com outro status e observação. */
    public TransacaoOnline com(String novoStatus, String novaObservacao) {
        return new TransacaoOnline(id, pagamentoId, alunoId, provedor, metodo, idExterno, novoStatus, valor, pixCopiaCola,
                pixQrBase64, urlCheckout, expiraEm, criadoEm, novaObservacao);
    }
}
