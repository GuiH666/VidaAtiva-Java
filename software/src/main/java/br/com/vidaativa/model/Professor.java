package br.com.vidaativa.model;

/**
 * Professor (instrutor) da academia. Junta {@code professores} (especialidade, ativo) com {@code usuarios}
 * (nome, e-mail e username).
 *
 * <p>Quem usa: {@code ProfessorDAO}, as telas de equipe e os formulários de ficha de treino e de avaliação física, que
 * gravam o professor responsável.
 *
 * <p>Campos que não são óbvios: {@code id} é da tabela {@code professores} (é ele que {@code FichaTreino.professorId} e
 * {@code AvaliacaoFisica.professorId} guardam) e {@code usuarioId} é do login em {@code usuarios}.
 */
public record Professor(int id, int usuarioId, String nome, String email, String username,
                        String especialidade, boolean ativo) { }
