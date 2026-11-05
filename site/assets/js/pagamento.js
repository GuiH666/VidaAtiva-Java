/**
 * pagamento.js — Mensalidades do aluno (pages/pagamento.html), somente consulta.
 *
 * Rotas usadas: /api/aluno/me e /api/aluno/me/pagamentos.
 * O pagamento em si é registrado pela recepção no software; aqui o aluno só acompanha.
 * Depende de: config.js e api.js.
 */
(function () {
    'use strict';

    if (!Api.logado()) { window.location.replace('login.html'); return; }

    function $(id) { return document.getElementById(id); }
    function texto(id, v) { $(id).textContent = v; }
    function criar(tag, classe, conteudo) {
        var e = document.createElement(tag);
        if (classe) e.className = classe;
        if (conteudo !== undefined) e.textContent = conteudo;
        return e;
    }

    var ROTULO = { PAGO: 'Paga', PENDENTE: 'A vencer', ATRASADO: 'Em atraso' };
    var CLASSE = { PAGO: 'st-pago', PENDENTE: 'st-pendente', ATRASADO: 'st-atrasado' };
    var SITUACAO = { EM_DIA: 'Mensalidade em dia', PENDENTE: 'Mensalidade a vencer', ATRASADO: 'Mensalidade em atraso' };

    Promise.all([Api.get('/api/aluno/me'), Api.get('/api/aluno/me/pagamentos')]).then(function (r) {
        var m = r[0], faturas = r[1];
        texto('nomeTopo', 'Olá, ' + String(m.nome).trim().split(/\s+/)[0]);
        texto('statusTexto', SITUACAO[m.situacaoPagamento] || '');
        if (m.situacaoPagamento === 'ATRASADO') $('statusAluno').classList.add('atrasado');
        if (m.situacaoPagamento === 'PENDENTE') $('statusAluno').classList.add('pendente');

        var valor = Number(m.valorMensalidade);
        texto('valorMensalidade', valor === 0 ? 'Coberto pelo TotalPass' : Api.moeda(valor));
        texto('proximoVencimento', Api.data(m.proximoVencimento));
        texto('formaCadastrada', Api.forma(m.formaPagamento));

        var lista = $('listaFaturas');
        lista.textContent = '';
        if (!faturas.length) {
            lista.appendChild(criar('p', 'vazio-texto', 'Ainda não há mensalidades geradas para você.'));
            return;
        }
        faturas.forEach(function (f) {
            var linha = criar('div', 'fatura');
            var info = criar('span');
            info.appendChild(criar('strong', null, Api.mesAno(f.referencia)));
            var detalhe = f.status === 'PAGO' && f.dataPagamento
                ? 'Paga em ' + Api.data(f.dataPagamento) + (f.formaPagamento ? ' (' + Api.forma(f.formaPagamento) + ')' : '')
                : 'Vencimento em ' + Api.data(f.vencimento);
            info.appendChild(criar('small', null, detalhe));
            linha.appendChild(info);
            linha.appendChild(criar('b', null, Api.moeda(f.valor)));
            linha.appendChild(criar('i', CLASSE[f.status] || '', ROTULO[f.status] || f.status));
            lista.appendChild(linha);
        });
    }).catch(function (erro) {
        var aviso = $('avisoErro');
        aviso.textContent = erro.message;
        aviso.hidden = false;
        texto('statusTexto', 'Indisponível');
    });
})();
