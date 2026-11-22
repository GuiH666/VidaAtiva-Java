/**
 * checkout-demo.js — Página que imita o pagamento com cartão (pages/checkout-demo.html).
 *
 * Só existe para a DEMONSTRAÇÃO (pagamento.provedor=simulado no software): no pagamento de verdade, o aluno é levado à página
 * do processador de pagamentos (Mercado Pago). Aqui NÃO há campo de cartão. Os botões chamam a rota de simulação da API,
 * que só existe no modo de demonstração (em modo real ela responde 404).
 * Depende de: config.js e api.js. O mês da mensalidade vem da sessionStorage, gravada por pagamento.js.
 */
(function () {
    'use strict';

    if (!Api.logado()) { window.location.replace('login.html'); return; }

    function $(id) { return document.getElementById(id); }
    var referencia = null;
    try { referencia = sessionStorage.getItem('vaPagamentoEmCurso'); } catch (e) { /* sem armazenamento */ }
    var VALIDA = /^\d{4}-(0[1-9]|1[0-2])$/;

    function erro(msg) { var a = $('avisoErro'); a.textContent = msg; a.hidden = false; }
    function voltar() { window.location.href = 'pagamento.html'; }

    if (!referencia || !VALIDA.test(referencia)) {
        erro('Não encontrei o pagamento em andamento. Volte para as faturas e escolha Pagar de novo.');
        $('btnAprovar').disabled = true;
        $('btnRecusar').disabled = true;
    } else {
        $('demoMes').textContent = 'Mensalidade de ' + Api.mesAno(referencia);
        Api.get('/api/aluno/me/pagamentos/' + referencia + '/online').then(function (d) {
            if (d && d.valor != null) $('demoValor').textContent = Api.moeda(d.valor);
        }).catch(function (e) { erro(e.message); });
    }

    function simular(aprovar) {
        $('btnAprovar').disabled = true;
        $('btnRecusar').disabled = true;
        Api.post('/api/aluno/me/pagamentos/' + referencia + '/online/simular', { aprovar: aprovar })
            .then(voltar)
            .catch(function (e) {
                erro(e.status === 404 ? 'A demonstração não está ligada neste servidor.' : e.message);
                $('btnAprovar').disabled = false;
                $('btnRecusar').disabled = false;
            });
    }

    $('btnAprovar').addEventListener('click', function () { simular(true); });
    $('btnRecusar').addEventListener('click', function () { simular(false); });
    $('btnVoltar').addEventListener('click', voltar);
})();
