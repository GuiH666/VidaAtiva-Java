package br.com.vidaativa.service;

import java.util.*;

/**
 * Catálogo de exercícios comuns de academia, com valores sugeridos de séries, repetições e descanso, e alguns modelos
 * de treino prontos. Serve para o professor montar uma ficha clicando, em vez de digitar tudo.
 * <p>
 * Não grava nada no banco: ao adicionar um exercício à ficha, os valores sugeridos são copiados para a linha, que o
 * professor ajusta (carga, observações...). Para incluir novos exercícios no catálogo, acrescente uma linha em
 * {@link #LISTA}.
 */
public final class BibliotecaExercicios {
    /** Um exercício do catálogo com a prescrição sugerida. */
    public record Modelo(String nome, String grupo, int series, String repeticoes, String descanso) {
        /** Texto curto para mostrar na lista, ex.: "Peito · 4 × 8-12". */
        public String resumo() { return grupo + " · " + series + " × " + repeticoes; }
    }

    /** Um treino de um modelo pronto (nome, dia sugerido e os nomes dos exercícios do catálogo). */
    public record TreinoModelo(String nome, String dia, List<String> exercicios) { }

    /** Um modelo de ficha completo (vários treinos). */
    public record ModeloFicha(String nome, String descricao, int diasPorSemana, List<TreinoModelo> treinos) { }

    /** Grupos musculares na ordem em que aparecem nos filtros. */
    public static final List<String> GRUPOS = List.of("Peito", "Costas", "Ombros", "Bíceps", "Tríceps", "Pernas", "Glúteos", "Abdômen", "Cardio");

    private static Modelo m(String nome, String grupo, int series, String reps, String descanso) {
        return new Modelo(nome, grupo, series, reps, descanso);
    }

    /** Todos os exercícios do catálogo. */
    public static final List<Modelo> LISTA = List.of(
            m("Supino reto com barra", "Peito", 4, "8-12", "90 s"),
            m("Supino inclinado com halteres", "Peito", 3, "8-12", "90 s"),
            m("Supino declinado", "Peito", 3, "8-12", "90 s"),
            m("Crucifixo com halteres", "Peito", 3, "10-12", "60 s"),
            m("Crossover na polia", "Peito", 3, "12-15", "60 s"),
            m("Peck deck (voador)", "Peito", 3, "12-15", "60 s"),
            m("Flexão de braços", "Peito", 3, "10-15", "60 s"),

            m("Puxada frontal", "Costas", 4, "8-12", "90 s"),
            m("Remada curvada com barra", "Costas", 4, "8-10", "90 s"),
            m("Remada baixa (triângulo)", "Costas", 3, "10-12", "60 s"),
            m("Remada unilateral com halter", "Costas", 3, "10-12", "60 s"),
            m("Barra fixa", "Costas", 3, "6-10", "90 s"),
            m("Pulldown com corda", "Costas", 3, "12-15", "60 s"),
            m("Levantamento terra", "Costas", 4, "6-8", "120 s"),

            m("Desenvolvimento com halteres", "Ombros", 4, "8-12", "90 s"),
            m("Elevação lateral", "Ombros", 3, "12-15", "45 s"),
            m("Elevação frontal", "Ombros", 3, "12", "45 s"),
            m("Remada alta", "Ombros", 3, "10-12", "60 s"),
            m("Crucifixo inverso", "Ombros", 3, "12-15", "45 s"),

            m("Rosca direta com barra", "Bíceps", 3, "8-12", "60 s"),
            m("Rosca alternada com halteres", "Bíceps", 3, "10-12", "60 s"),
            m("Rosca martelo", "Bíceps", 3, "10-12", "60 s"),
            m("Rosca scott", "Bíceps", 3, "10-12", "60 s"),
            m("Rosca concentrada", "Bíceps", 3, "12", "45 s"),

            m("Tríceps na polia (corda)", "Tríceps", 3, "10-15", "60 s"),
            m("Tríceps testa", "Tríceps", 3, "10-12", "60 s"),
            m("Tríceps francês", "Tríceps", 3, "10-12", "60 s"),
            m("Mergulho no banco", "Tríceps", 3, "10-15", "60 s"),
            m("Tríceps barra reta na polia", "Tríceps", 3, "10-12", "60 s"),

            m("Agachamento livre", "Pernas", 4, "8-12", "90 s"),
            m("Leg press 45°", "Pernas", 4, "10-12", "90 s"),
            m("Cadeira extensora", "Pernas", 3, "12-15", "60 s"),
            m("Mesa flexora", "Pernas", 3, "12-15", "60 s"),
            m("Afundo (avanço)", "Pernas", 3, "10-12", "60 s"),
            m("Agachamento búlgaro", "Pernas", 3, "8-12", "90 s"),
            m("Stiff", "Pernas", 3, "10-12", "90 s"),
            m("Panturrilha em pé", "Pernas", 4, "15-20", "45 s"),
            m("Panturrilha sentado", "Pernas", 4, "15-20", "45 s"),
            m("Cadeira adutora", "Pernas", 3, "12-15", "45 s"),
            m("Cadeira abdutora", "Pernas", 3, "12-15", "45 s"),

            m("Elevação pélvica", "Glúteos", 4, "10-12", "90 s"),
            m("Glúteo na polia (coice)", "Glúteos", 3, "12-15", "60 s"),
            m("Agachamento sumô", "Glúteos", 3, "10-12", "60 s"),
            m("Abdução de quadril", "Glúteos", 3, "15", "45 s"),

            m("Abdominal crunch", "Abdômen", 3, "15-20", "45 s"),
            m("Prancha", "Abdômen", 3, "30-45 s", "45 s"),
            m("Elevação de pernas (infra)", "Abdômen", 3, "12-15", "45 s"),
            m("Abdominal na polia", "Abdômen", 3, "12-15", "45 s"),
            m("Prancha lateral", "Abdômen", 3, "20-30 s", "45 s"),
            m("Abdominal bicicleta", "Abdômen", 3, "20", "45 s"),

            m("Esteira", "Cardio", 1, "20 min", "—"),
            m("Bicicleta ergométrica", "Cardio", 1, "20 min", "—"),
            m("Elíptico", "Cardio", 1, "20 min", "—"),
            m("Remo ergométrico", "Cardio", 1, "10 min", "—"),
            m("Pular corda", "Cardio", 3, "2 min", "60 s"));

    /** Modelos de ficha prontos: o professor escolhe um e depois ajusta os exercícios e as cargas. */
    public static final List<ModeloFicha> MODELOS = List.of(
            new ModeloFicha("Full body (A/B/C)", "Corpo inteiro em 3 dias alternados. Bom para iniciantes.", 3, List.of(
                    new TreinoModelo("Treino A", "Segunda-feira", List.of("Agachamento livre", "Supino reto com barra", "Remada baixa (triângulo)", "Desenvolvimento com halteres", "Prancha")),
                    new TreinoModelo("Treino B", "Quarta-feira", List.of("Leg press 45°", "Puxada frontal", "Supino inclinado com halteres", "Rosca direta com barra", "Tríceps na polia (corda)")),
                    new TreinoModelo("Treino C", "Sexta-feira", List.of("Stiff", "Remada curvada com barra", "Elevação lateral", "Afundo (avanço)", "Abdominal crunch")))),
            new ModeloFicha("ABC Hipertrofia", "Peito e tríceps, costas e bíceps, pernas e ombros.", 3, List.of(
                    new TreinoModelo("Treino A — Peito e tríceps", "Segunda-feira", List.of("Supino reto com barra", "Supino inclinado com halteres", "Crucifixo com halteres", "Tríceps testa", "Tríceps na polia (corda)")),
                    new TreinoModelo("Treino B — Costas e bíceps", "Quarta-feira", List.of("Puxada frontal", "Remada curvada com barra", "Remada unilateral com halter", "Rosca direta com barra", "Rosca martelo")),
                    new TreinoModelo("Treino C — Pernas e ombros", "Sexta-feira", List.of("Agachamento livre", "Leg press 45°", "Mesa flexora", "Desenvolvimento com halteres", "Elevação lateral", "Panturrilha em pé")))),
            new ModeloFicha("Emagrecimento (circuito + cardio)", "Circuito de corpo inteiro com cardio no final, 3 vezes por semana.", 3, List.of(
                    new TreinoModelo("Treino A — Circuito", "Segunda-feira", List.of("Agachamento livre", "Flexão de braços", "Remada baixa (triângulo)", "Afundo (avanço)", "Prancha", "Esteira")),
                    new TreinoModelo("Treino B — Circuito", "Quarta-feira", List.of("Leg press 45°", "Supino inclinado com halteres", "Puxada frontal", "Elevação pélvica", "Abdominal bicicleta", "Bicicleta ergométrica")),
                    new TreinoModelo("Treino C — Circuito", "Sexta-feira", List.of("Stiff", "Desenvolvimento com halteres", "Remada unilateral com halter", "Agachamento sumô", "Prancha lateral", "Elíptico")))));

    private BibliotecaExercicios() { }

    /** Procura no catálogo pelo nome exato (sem diferenciar maiúsculas); vazio se não existir. */
    public static Optional<Modelo> buscar(String nome) {
        return LISTA.stream().filter(e -> e.nome().equalsIgnoreCase(nome)).findFirst();
    }

    /**
     * Filtra o catálogo.
     *
     * @param grupo grupo muscular ou {@code null}/vazio para todos
     * @param termo texto digitado na busca (ignora acentos e maiúsculas); vazio para todos
     */
    public static List<Modelo> filtrar(String grupo, String termo) {
        String t = normalizar(termo);
        List<Modelo> r = new ArrayList<>();
        for (Modelo e : LISTA) {
            if (grupo != null && !grupo.isBlank() && !e.grupo().equals(grupo)) continue;
            if (!t.isEmpty() && !normalizar(e.nome()).contains(t) && !normalizar(e.grupo()).contains(t)) continue;
            r.add(e);
        }
        return r;
    }

    /** Minúsculas e sem acentos: "Bíceps" e "biceps" ficam iguais na busca. */
    static String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
