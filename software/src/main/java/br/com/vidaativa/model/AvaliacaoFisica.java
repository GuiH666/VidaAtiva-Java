package br.com.vidaativa.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Uma avaliação física feita por um professor em um aluno (tabela {@code avaliacoes_fisicas}).
 *
 * <p>Quem usa: {@code AvaliacaoDAO}, a tela de avaliações do professor e a API do site
 * ({@code GET /api/aluno/me/avaliacoes}). Pelo plano, só o Premium inclui avaliação (veja {@code Precificacao}).
 *
 * <p>Campos que não são óbvios:
 * <ul>
 *   <li>{@code alunoId}/{@code professorId}: chaves das tabelas {@code alunos} e {@code professores} (não de {@code usuarios}).</li>
 *   <li>{@code alunoNome}/{@code professorNome}: não são colunas desta tabela; vêm de um JOIN com {@code usuarios}.</li>
 *   <li>{@code data}: dia em que a avaliação foi feita (coluna {@code data_avaliacao}).</li>
 *   <li>{@code peso} em quilos e {@code altura} em METROS (ex.: 1.78, e não 178).</li>
 *   <li>{@code percentualGordura}: de 0 a 100; é opcional e pode ser {@code null}.</li>
 * </ul>
 * O IMC não é gravado no banco: é calculado por {@link #imc()}.
 */
public record AvaliacaoFisica(int id, int alunoId, int professorId, String alunoNome, String professorNome,
                              LocalDate data, BigDecimal peso, BigDecimal altura,
                              BigDecimal percentualGordura, String observacoes) {
    /** Índice de massa corporal (peso / altura²), ou null se faltar peso ou altura. */
    public BigDecimal imc() {
        if (peso == null || altura == null || altura.signum() <= 0) return null;
        return peso.divide(altura.multiply(altura), 1, RoundingMode.HALF_UP);
    }
}
