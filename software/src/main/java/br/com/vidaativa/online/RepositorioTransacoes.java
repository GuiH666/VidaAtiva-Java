package br.com.vidaativa.online;

import br.com.vidaativa.model.Pagamento;

import java.util.List;
import java.util.Set;

/**
 * Tudo que {@link ServicoPagamentoOnline} precisa guardar ou ler. Interface para os testes rodarem sem MySQL
 * (a versão real é {@code dao.TransacaoDAO}; a dos testes é em memória).
 */
public interface RepositorioTransacoes {
    /** Cobrança do aluno no mês (AAAA-MM), qualquer status, ou null. O aluno vem do token, nunca do cliente. */
    Pagamento pagamentoDoAluno(int alunoId, String referencia) throws Exception;

    /** Cobrança pelo id, ou null. */
    Pagamento pagamento(int pagamentoId) throws Exception;

    /** Grava a transação nova e devolve o id gerado. */
    int inserir(TransacaoOnline t) throws Exception;

    /** Grava o que o gateway devolveu (id externo, Pix, link) e passa para AGUARDANDO. */
    void registrarResposta(int id, GatewayPagamento.Resposta resposta) throws Exception;

    TransacaoOnline porId(int id) throws Exception;

    /** Transação mais recente da cobrança, ou null. */
    TransacaoOnline ultimaDoPagamento(int pagamentoId) throws Exception;

    /** Transações ainda abertas (CRIADA/AGUARDANDO), das últimas 48 horas, para a conferência periódica. */
    List<TransacaoOnline> abertas() throws Exception;

    /**
     * Troca o status SÓ se o atual for um dos de {@code de} (compara e troca numa operação atômica). Devolve true se trocou.
     * É isso que impede que duas threads (aluno consultando, webhook, conferência periódica) deem baixa duas vezes.
     */
    boolean mudarStatus(int id, Set<String> de, String para, String observacao) throws Exception;

    /** Quantas transações o aluno criou desde o instante dado (para o limite de criações). */
    int contarCriadasDesde(int alunoId, java.time.LocalDateTime desde) throws Exception;

    /**
     * Dá baixa na cobrança (status PAGO, data de hoje, forma PIX/CARTAO) pelo MESMO código da recepção.
     *
     * @throws IllegalStateException se a cobrança já estiver paga ou cancelada
     */
    void baixar(int pagamentoId, String forma) throws Exception;
}
