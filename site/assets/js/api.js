/**
 * api.js — Cliente da API da Vida Ativa (usado por login, aluno, pagamento e planos).
 *
 * Expõe o objeto global `Api` com:
 *   Api.entrar(usuario, senha)  -> POST /api/login; guarda o token
 *   Api.get(caminho, opcoes)    -> GET com o token no cabeçalho Authorization
 *   Api.post(caminho, corpo)    -> POST com corpo JSON
 *   Api.logado() / Api.sair()   -> estado de login
 *   Api.moeda() / Api.data()    -> formatadores em português (R$ 129,90 / 24/10/2026)
 *
 * Se a API responder 401 (token vencido), redireciona para login.html?sessao=expirada.
 * O token fica só na sessionStorage: some quando a aba é fechada (seguro em computador compartilhado).
 * O endereço da API vem de config.js (window.VIDA_ATIVA_API).
 */
(function () {
    'use strict';

    var BASE = String(window.VIDA_ATIVA_API || 'http://localhost:8080').replace(/\/+$/, '');
    var CHAVE = 'vidaAtivaToken';
    var MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

    function lerToken() { try { return sessionStorage.getItem(CHAVE); } catch (e) { return null; } }
    function guardarToken(t) { try { sessionStorage.setItem(CHAVE, t); } catch (e) { /* navegador bloqueou o armazenamento */ } }
    function apagarToken() { try { sessionStorage.removeItem(CHAVE); } catch (e) { /* ignora */ } }

    function chamar(metodo, caminho, corpo, opcoes) {
        opcoes = opcoes || {};
        var headers = { 'Accept': 'application/json' };
        var token = lerToken();
        if (token && !opcoes.publico) headers['Authorization'] = 'Bearer ' + token;
        if (corpo !== undefined) headers['Content-Type'] = 'application/json';

        return fetch(BASE + caminho, {
            method: metodo,
            headers: headers,
            body: corpo === undefined ? undefined : JSON.stringify(corpo)
        }).catch(function () {
            throw new Error('Não foi possível conectar à academia. Verifique sua internet ou tente de novo em instantes.');
        }).then(function (resposta) {
            return resposta.text().then(function (texto) {
                var dados = null;
                try { dados = texto ? JSON.parse(texto) : null; } catch (e) { /* resposta sem JSON */ }
                if (resposta.ok) return dados;
                if (resposta.status === 401 && !opcoes.semRedirecionar) {
                    apagarToken();
                    window.location.href = 'login.html?sessao=expirada';
                }
                var erro = new Error((dados && dados.erro) || 'Algo deu errado (código ' + resposta.status + ').');
                erro.status = resposta.status;
                throw erro;
            });
        });
    }

    /** "2026-10-30" -> Date local (sem o erro de fuso horário do new Date("2026-10-30")). */
    function paraData(iso) {
        if (!iso) return null;
        var p = String(iso).split('-');
        return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
    }

    window.Api = {
        base: BASE,
        logado: function () { return !!lerToken(); },

        get: function (caminho, opcoes) { return chamar('GET', caminho, undefined, opcoes); },
        post: function (caminho, corpo, opcoes) { return chamar('POST', caminho, corpo, opcoes); },

        entrar: function (login, senha) {
            return chamar('POST', '/api/login', { login: login, senha: senha }, { publico: true, semRedirecionar: true })
                .then(function (r) { guardarToken(r.token); return r; });
        },
        sair: function () { apagarToken(); window.location.href = 'home.html'; },

        /** 30/10/2026 */
        data: function (iso) {
            var d = paraData(iso);
            if (!d) return '—';
            return ('0' + d.getDate()).slice(-2) + '/' + ('0' + (d.getMonth() + 1)).slice(-2) + '/' + d.getFullYear();
        },
        /** { dia: '30', mes: 'OUT' } */
        diaMes: function (iso) {
            var d = paraData(iso);
            if (!d) return { dia: '—', mes: '' };
            return { dia: ('0' + d.getDate()).slice(-2), mes: MESES[d.getMonth()].slice(0, 3).toUpperCase() };
        },
        /** "2026-10" -> "Outubro 2026" */
        mesAno: function (ref) {
            if (!ref || ref.length !== 7) return ref || '—';
            return MESES[Number(ref.slice(5)) - 1] + ' ' + ref.slice(0, 4);
        },
        /** Dias entre hoje e a data (negativo = já passou). */
        diasAte: function (iso) {
            var d = paraData(iso);
            if (!d) return null;
            var h = new Date();
            var hoje = new Date(h.getFullYear(), h.getMonth(), h.getDate());
            return Math.round((d - hoje) / 86400000);
        },
        paraData: paraData,
        moeda: function (n) {
            return Number(n).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
        },
        numero: function (n, casas) {
            return Number(n).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
        },
        forma: function (f) {
            return { PIX: 'Pix', CARTAO: 'Cartão', BOLETO: 'Boleto', DINHEIRO: 'Dinheiro' }[f] || f || '—';
        }
    };
})();
