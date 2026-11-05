package br.com.vidaativa.model;

import java.math.BigDecimal;

/**
 * Plano de assinatura da academia (tabela {@code planos}): Básico, Plus ou Premium.
 *
 * <p>Quem usa: {@code PlanoDAO} (lista só os planos ativos), o formulário de aluno e a API pública do site
 * ({@code GET /api/planos}, página de Planos).
 *
 * <p>Atenção: {@code valorMensal} é o preço de tabela (cliente convencional). O que cada plano inclui e quanto paga
 * um cliente TotalPass são regras de {@code Precificacao}, que identifica o plano pelo NOME; por isso não renomeie
 * planos no banco sem ajustar essa classe.
 */
public record Plano(int id, String nome, BigDecimal valorMensal, String descricao) {
    /** Devolve só o nome para o plano aparecer legível em listas suspensas (combos) do Swing. */
    @Override public String toString() { return nome; }
}
