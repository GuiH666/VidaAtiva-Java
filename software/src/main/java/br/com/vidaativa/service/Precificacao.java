package br.com.vidaativa.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Regra de cobrança da mensalidade e do que cada plano inclui. É a ÚNICA classe que conhece essas regras: telas, DAOs
 * e API chamam aqui em vez de repetir a conta.
 *
 * <p>Quem usa: {@code PagamentoDAO} (valor das cobranças geradas), {@code ApiServer} (valor da mensalidade no perfil do
 * aluno), {@code AlunosPanel} (mostra o valor ao cadastrar), {@code FichaDialog} e {@code AvaliacoesPanel} (bloqueiam
 * ficha/avaliação em plano que não inclui).
 *
 * <p>Valor da mensalidade ({@link #valorMensalidade}):
 * <ul>
 *   <li>Cliente CONVENCIONAL (ou qualquer tipo diferente de TOTALPASS, inclusive nulo): paga o valor do plano, sem alteração.</li>
 *   <li>Cliente TOTALPASS, plano Básico: R$ 0,00 (coberto pelo TotalPass; {@code PagamentoDAO} não gera cobrança de valor zero).</li>
 *   <li>Cliente TOTALPASS, plano Plus: paga só o upgrade, R$ 29,90.</li>
 *   <li>Cliente TOTALPASS, plano Premium: paga só o upgrade, R$ 49,90.</li>
 *   <li>Cliente TOTALPASS com plano de outro nome: paga o valor do plano (nenhuma regra especial).</li>
 * </ul>
 * Os valores de upgrade são os mesmos exibidos na página de Planos do site ({@code planos.html}); se mudar aqui, mude lá.
 *
 * <p>O que cada plano inclui (vale para os dois tipos de cliente):
 * <ul>
 *   <li>Ficha de treino personalizada ({@link #incluiFicha}): Plus e Premium. O Básico não inclui.</li>
 *   <li>Avaliação física ({@link #incluiAvaliacao}): somente Premium.</li>
 * </ul>
 *
 * <p>Atenção: o plano é identificado pelo NOME, sem diferenciar maiúsculas/minúsculas nem acentos ("Básico" = "basico").
 * Se um plano for renomeado no banco, as regras acima deixam de reconhecê-lo (o TotalPass passaria a pagar o valor cheio
 * e o plano deixaria de incluir ficha/avaliação).
 */
public final class Precificacao {
    // Valores que o cliente TotalPass paga POR CIMA do que o TotalPass já cobre (não é o preço do plano).
    // Construídos a partir de String para o BigDecimal ser exato (new BigDecimal(29.90) herdaria o erro do double).
    public static final BigDecimal UPGRADE_TOTALPASS_PLUS = new BigDecimal("29.90");
    public static final BigDecimal UPGRADE_TOTALPASS_PREMIUM = new BigDecimal("49.90");

    private Precificacao() { }

    /**
     * Calcula quanto o aluno paga por mês, seguindo as regras do Javadoc da classe.
     *
     * @param nomePlano nome do plano ("Básico", "Plus" ou "Premium"; acentos e maiúsculas não importam)
     * @param valorPlano valor de tabela do plano, devolvido quando não há regra especial
     * @param tipoCliente {@code CONVENCIONAL} ou {@code TOTALPASS}
     * @return valor da mensalidade; pode ser {@link BigDecimal#ZERO} (TotalPass no Básico)
     */
    public static BigDecimal valorMensalidade(String nomePlano, BigDecimal valorPlano, String tipoCliente) {
        // Só TOTALPASS tem regra especial; qualquer outra coisa (inclusive null) paga o valor do plano.
        if (!"TOTALPASS".equalsIgnoreCase(tipoCliente)) return valorPlano;
        return switch (normalizar(nomePlano)) {
            case "basico" -> BigDecimal.ZERO;
            case "plus" -> UPGRADE_TOTALPASS_PLUS;
            case "premium" -> UPGRADE_TOTALPASS_PREMIUM;
            // Plano com outro nome: sem regra de upgrade, cobra o valor do plano.
            default -> valorPlano;
        };
    }

    /** O plano inclui ficha de treino personalizada? (Plus e Premium) */
    public static boolean incluiFicha(String nomePlano) {
        String n = normalizar(nomePlano);
        return n.equals("plus") || n.equals("premium");
    }

    /** O plano inclui avaliação física? (somente Premium) */
    public static boolean incluiAvaliacao(String nomePlano) {
        return normalizar(nomePlano).equals("premium");
    }

    /**
     * Padroniza o nome do plano para comparação: sem acentos, sem espaços nas pontas e em minúsculas ("Básico" vira "basico").
     * Nome nulo vira texto vazio (não casa com nenhum plano).
     */
    private static String normalizar(String s) {
        if (s == null) return "";
        // NFD separa a letra do acento (á vira a + ´); o replaceAll("\\p{M}") apaga as marcas de acento que sobraram.
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }
}
