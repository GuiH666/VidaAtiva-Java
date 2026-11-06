package br.com.vidaativa.ui.estilo;

import java.awt.Color;

/**
 * Marca um contêiner que desenha o próprio fundo arredondado (como o {@code Cartao}) em vez de ser opaco.
 * <p>
 * Os campos de texto têm cantos arredondados; para "recortar" os cantos, {@link CampoBorda} pinta a cor do fundo que
 * está atrás do campo. Contêineres não opacos implementam esta interface para dizer que cor é essa.
 */
public interface FundoCampo {
    /** Cor do fundo visível atrás dos filhos deste contêiner. */
    Color fundoParaCampos();
}
