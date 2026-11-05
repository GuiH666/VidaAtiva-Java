package br.com.vidaativa.model;

import java.math.BigDecimal;

/**
 * Números do painel inicial do software. Não é uma tabela: é calculado por {@code ResumoDAO} com várias consultas
 * de contagem e exibido por {@code ResumoPanel} (cada perfil mostra só uma parte).
 *
 * <p>Campos que não são óbvios: as contagens de alunos, funcionários e professores consideram só os ATIVOS (e
 * {@code funcionariosAtivos} não inclui o gerente). {@code fichasAtivas} conta fichas com status ATIVA.
 * {@code pagamentosPendentes} e {@code pagamentosAtrasados} somam cobranças de TODOS os meses. {@code receitaMes} é
 * a soma das cobranças PAGAS cuja data de pagamento caiu no mês atual.
 */
public record Resumo(int alunosAtivos, int funcionariosAtivos, int professoresAtivos, int fichasAtivas,
                     int pagamentosPendentes, int pagamentosAtrasados, BigDecimal receitaMes) { }
