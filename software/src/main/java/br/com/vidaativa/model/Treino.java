package br.com.vidaativa.model;

import java.util.List;

/**
 * Um treino da ficha (ex.: "Treino A — Peito e tríceps"), com seus exercícios (tabela {@code treinos}).
 *
 * <p>Quem usa: {@code FichaTreinoDAO}, o editor de treinos ({@code TreinoEditor}) e o site do aluno. Assim como
 * {@link Exercicio}, este record vai ao site sem conversão (dentro da ficha), então os nomes dos campos são os do JSON.
 *
 * <p>Campos que não são óbvios: {@code diaSemana} é texto livre (ex.: "Segunda-feira") e pode ser {@code null} (o site
 * mostra "Dia livre"); {@code descricao} também é opcional; {@code id} é 0 enquanto o treino não foi gravado.
 */
public record Treino(int id, String nome, String diaSemana, String descricao, List<Exercicio> exercicios) { }
