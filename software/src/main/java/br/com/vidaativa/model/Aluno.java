package br.com.vidaativa.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um aluno da academia. Junta duas tabelas: {@code alunos} (documento, telefone, plano, forma de pagamento...) e
 * {@code usuarios} (nome, e-mail e username, que são o login do aluno).
 *
 * <p>Quem usa: {@code AlunoDAO} (monta o record), as telas de alunos, pagamentos, fichas e avaliações do software e a
 * API do site ({@code ApiServer}, que NÃO envia este record inteiro: monta um {@code PerfilAluno} só com o que o aluno
 * pode ver, sem documento, id nem usuário).
 *
 * <p>Campos que não são óbvios:
 * <ul>
 *   <li>{@code id}: chave da tabela {@code alunos}. {@code usuarioId}: chave do login correspondente em {@code usuarios}
 *       (é por ele que o token do site identifica o aluno).</li>
 *   <li>{@code documento}: CPF ou RG, como foi digitado (único no banco).</li>
 *   <li>{@code dataInicio}: "cliente desde".</li>
 *   <li>{@code planoId}/{@code plano}: chave e NOME do plano ("Básico", "Plus" ou "Premium"). O nome é usado por
 *       {@code Precificacao} para decidir preço e benefícios.</li>
 *   <li>{@code valorPlano}: valor de tabela do plano ({@code planos.valor_mensal}), NÃO o que o aluno paga. O valor
 *       realmente cobrado depende também do tipo de cliente: veja {@code Precificacao.valorMensalidade}.</li>
 *   <li>{@code tipoCliente}: {@code CONVENCIONAL} ou {@code TOTALPASS}.</li>
 *   <li>{@code formaPagamento}: {@code PIX}, {@code CARTAO}, {@code BOLETO} ou {@code DINHEIRO}.</li>
 *   <li>{@code vencimento}: data do PRÓXIMO vencimento da mensalidade (não é o vencimento de uma cobrança específica,
 *       essas ficam em {@link Pagamento}). Avança um mês quando um pagamento é registrado.</li>
 *   <li>{@code ativo}: {@code false} é aluno desativado; ele não consegue mais entrar no site.</li>
 * </ul>
 */
public record Aluno(int id, int usuarioId, String nome, String email, String username, String documento,
                    String telefone, LocalDate dataNascimento, LocalDate dataInicio,
                    int planoId, String plano, BigDecimal valorPlano, String tipoCliente,
                    String formaPagamento, LocalDate vencimento, String observacoes, boolean ativo) {
    /** Devolve só o nome para o aluno aparecer legível em listas suspensas (combos) do Swing. */
    @Override public String toString() { return nome; }
}
