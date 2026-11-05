package br.com.vidaativa.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Ficha de treino de um aluno, montada por um professor (tabela {@code fichas_treino}). Uma ficha tem vários
 * {@link Treino} (A, B, C...) e cada treino tem vários {@link Exercicio}.
 *
 * <p>Quem usa: {@code FichaTreinoDAO}, as telas de fichas do professor e a API do site, que mostra ao aluno apenas
 * as fichas ATIVAS dele ({@code GET /api/aluno/me/fichas}). Pelo plano, só Plus e Premium incluem ficha
 * (veja {@code Precificacao}); essa checagem fica na tela, não no DAO.
 *
 * <p>Campos que não são óbvios:
 * <ul>
 *   <li>{@code status}: {@code RASCUNHO} (só o professor vê) | {@code ATIVA} (o aluno vê no site) | {@code INATIVA} (histórico).</li>
 *   <li>{@code alunoNome}/{@code professorNome}: vêm de um JOIN com {@code usuarios}; não são colunas desta tabela.</li>
 *   <li>{@code frequenciaSemanal}: quantas vezes por semana treinar ({@code null} se não informado).</li>
 *   <li>{@code dataRevisao}: data prevista para o professor revisar a ficha.</li>
 *   <li>{@code treinos}: lista carregada junto com a ficha (as tabelas {@code treinos} e {@code exercicios} caem em
 *       cascata se a ficha for apagada).</li>
 * </ul>
 */
public record FichaTreino(int id, int alunoId, int professorId, String alunoNome, String professorNome,
                          String nome, String objetivo, Integer frequenciaSemanal, LocalDate dataInicio,
                          LocalDate dataRevisao, String observacoes, String status, List<Treino> treinos) { }
