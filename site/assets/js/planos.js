/**
 * planos.js — Página de planos (pages/planos.html).
 *
 * Os preços vêm da API (GET /api/planos), ou seja, do mesmo banco que o software
 * da academia. Cada preço na página tem o atributo data-plano-preco="NomeDoPlano".
 * Se a API estiver fora do ar, ficam os valores que já estão escritos no HTML.
 *
 * Depende de: config.js e api.js.
 */
Api.get('/api/planos', { publico: true, semRedirecionar: true }).then(function (planos) {
    planos.forEach(function (plano) {
        var alvo = document.querySelector('[data-plano-preco="' + plano.nome + '"]');
        if (alvo) alvo.textContent = Api.moeda(plano.valorMensal);
    });
}).catch(function () { /* mantém os valores estáticos */ });
