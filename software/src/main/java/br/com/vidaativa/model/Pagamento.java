package br.com.vidaativa.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma cobrança de mensalidade de um aluno (tabela {@code pagamentos}). Cada aluno tem no máximo uma cobrança por mês.
 *
 * <p>Quem usa: {@code PagamentoDAO}, a tela de pagamentos (recepção/gerente) e a API do site
 * ({@code GET /api/aluno/me/pagamentos}, que não lista as canceladas).
 *
 * <p>Campos que não são óbvios:
 * <ul>
 *   <li>{@code referencia}: mês a que a mensalidade se refere, no formato {@code AAAA-MM} (ex.: "2026-10").</li>
 *   <li>{@code status}: {@code PENDENTE} | {@code PAGO} | {@code ATRASADO} | {@code CANCELADO}. O banco não vira
 *       ATRASADO sozinho: o {@code PagamentoDAO} converte PENDENTE vencida em ATRASADO a cada leitura.</li>
 *   <li>{@code vencimento}: data limite DESTA cobrança (diferente de {@code Aluno.vencimento}, que é o próximo vencimento do aluno).</li>
 *   <li>{@code data}: dia em que foi efetivamente pago (coluna {@code data_pagamento}); {@code null} enquanto não pago.</li>
 *   <li>{@code valor}: valor cobrado, já com a regra de {@code Precificacao} aplicada na hora em que a cobrança foi gerada.</li>
 *   <li>{@code formaPagamento}: texto ({@code PIX}, {@code CARTAO}, {@code BOLETO} ou {@code DINHEIRO}); começa igual
 *       ao cadastro do aluno e pode mudar ao registrar o recebimento.</li>
 *   <li>{@code alunoNome}: vem de um JOIN com {@code usuarios}; não é coluna desta tabela.</li>
 * </ul>
 */
public record Pagamento(int id, int alunoId, String alunoNome, String referencia, LocalDate data,
                        BigDecimal valor, String formaPagamento, String status, LocalDate vencimento) { }
