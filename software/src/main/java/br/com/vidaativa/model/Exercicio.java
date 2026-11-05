package br.com.vidaativa.model;

/**
 * Um exercício dentro de um {@link Treino} (tabela {@code exercicios}), por exemplo "Supino reto", 4 séries de 8-10.
 *
 * <p>Quem usa: {@code FichaTreinoDAO} (lê e grava), o editor de treinos do software ({@code TreinoEditor}) e o site do
 * aluno. Atenção: este record é enviado ao site SEM conversão (vai dentro de {@code Treino} na ficha), então os nomes
 * dos campos são também os nomes do JSON que {@code site/assets/js/aluno.js} lê; renomear um campo quebra o site.
 *
 * <p>Campos que não são óbvios: {@code series} é um número ({@code null} se não informado); {@code repeticoes},
 * {@code carga} e {@code descanso} são TEXTO livre ("8-10", "60 kg", "90 s") justamente para aceitar faixas e unidades.
 * {@code id} é 0 quando o exercício ainda não foi gravado no banco.
 */
public record Exercicio(int id, String nome, String grupoMuscular, Integer series, String repeticoes,
                        String carga, String descanso, String observacoes) { }
