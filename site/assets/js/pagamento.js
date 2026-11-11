/**
 * pagamento.js — Mensalidades do aluno (pages/pagamento.html): consulta e PAGAMENTO ONLINE (Pix e cartão).
 *
 * Rotas usadas:
 *   GET  /api/aluno/me, /api/aluno/me/pagamentos         (faturas)
 *   GET  /api/aluno/me/pagamento-online                  (o pagamento online está ligado? é demonstração?)
 *   POST /api/aluno/me/pagamentos/AAAA-MM/online         (começa o Pix ou o cartão)
 *   GET  /api/aluno/me/pagamentos/AAAA-MM/online         (situação; consultada de 4 em 4 s enquanto espera)
 *   POST /api/aluno/me/pagamentos/AAAA-MM/online/simular (só no modo demonstração)
 *
 * Princípios de segurança: o site NUNCA decide valor nem se "foi pago": só mostra o que a API devolve (o valor vem do banco
 * e a confirmação vem do processador de pagamentos, conferida pelo servidor). Dados de cartão nunca são digitados aqui:
 * o cartão é pago na página do processador. Todo texto vindo da API entra na tela com textContent (nunca innerHTML).
 *
 * Telas (só uma visível por vez): resumo -> escolha (Pix/cartão) -> Pix | cartão -> resultado.
 * Depende de: config.js e api.js.
 */
(function () {
    'use strict';

    if (!Api.logado()) { window.location.replace('login.html'); return; }

    var CHAVE_EM_CURSO = 'vaPagamentoEmCurso';   // mês (AAAA-MM) do pagamento em andamento; usado ao voltar da página do cartão
    var INTERVALO_CONSULTA_MS = 4000;
    var MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;
    var BASE64_VALIDO = /^[A-Za-z0-9+\/=]+$/;

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
    var TELAS = ['vResumo', 'vEscolha', 'vPix', 'vCartao', 'vResultado'];

    var opcoes = { ativo: false, demonstracao: false };
    var fatura = null;            // { referencia, valor } da fatura em pagamento
    var consulta = null;          // temporizador da consulta periódica
    var contagem = null;          // temporizador da contagem regressiva do Pix
    var fimMs = 0;                // instante (do relógio do navegador) em que o Pix expira, calculado a partir dos segundos do servidor
    var tentativas = 0;           // consultas seguidas que falharam (queda de rede)

    // ------------------------------------------------------------------ telas

    function mostrar(tela) {
        TELAS.forEach(function (t) { $(t).hidden = t !== tela; });
        $('avisoDemo').hidden = !opcoes.demonstracao;
    }

    function parar() {
        if (consulta) { clearInterval(consulta); consulta = null; }
        if (contagem) { clearInterval(contagem); contagem = null; }
    }

    function erroGeral(mensagem) {
        var aviso = $('avisoErro');
        aviso.textContent = mensagem;
        aviso.hidden = false;
    }

    function limparEmCurso() { try { sessionStorage.removeItem(CHAVE_EM_CURSO); } catch (e) { /* ignora */ } }
    function guardarEmCurso(ref) { try { sessionStorage.setItem(CHAVE_EM_CURSO, ref); } catch (e) { /* ignora */ } }
    function lerEmCurso() {
        try { var v = sessionStorage.getItem(CHAVE_EM_CURSO); return v && MES_VALIDO.test(v) ? v : null; } catch (e) { return null; }
    }

    // ------------------------------------------------------------------ faturas

    function carregar() {
        return Promise.all([Api.get('/api/aluno/me'), Api.get('/api/aluno/me/pagamentos')]).then(function (r) {
            var m = r[0], faturas = r[1];
            texto('nomeTopo', 'Olá, ' + String(m.nome).trim().split(/\s+/)[0]);
            texto('statusTexto', SITUACAO[m.situacaoPagamento] || '');
            var st = $('statusAluno');
            st.classList.toggle('atrasado', m.situacaoPagamento === 'ATRASADO');
            st.classList.toggle('pendente', m.situacaoPagamento === 'PENDENTE');

            var valor = Number(m.valorMensalidade);
            texto('valorMensalidade', valor === 0 ? 'Coberto pelo TotalPass' : Api.moeda(valor));
            texto('proximoVencimento', Api.data(m.proximoVencimento));
            texto('formaCadastrada', Api.forma(m.formaPagamento));
            desenharFaturas(faturas);
            return faturas;
        });
    }

    function desenharFaturas(faturas) {
        var lista = $('listaFaturas');
        lista.textContent = '';
        if (!faturas.length) {
            lista.appendChild(criar('p', 'vazio-texto', 'Ainda não há mensalidades geradas para você.'));
            return;
        }
        faturas.forEach(function (f) {
            var podePagar = opcoes.ativo && f.status !== 'PAGO' && Number(f.valor) > 0 && MES_VALIDO.test(String(f.referencia));
            var linha = criar('div', podePagar ? 'fatura com-acao' : 'fatura');
            var info = criar('span');
            info.appendChild(criar('strong', null, Api.mesAno(f.referencia)));
            var detalhe = f.status === 'PAGO' && f.dataPagamento
                ? 'Paga em ' + Api.data(f.dataPagamento) + (f.formaPagamento ? ' (' + Api.forma(f.formaPagamento) + ')' : '')
                : 'Vencimento em ' + Api.data(f.vencimento);
            info.appendChild(criar('small', null, detalhe));
            linha.appendChild(info);
            linha.appendChild(criar('b', null, Api.moeda(f.valor)));
            linha.appendChild(criar('i', CLASSE[f.status] || '', ROTULO[f.status] || f.status));
            if (podePagar) {
                var botao = criar('button', 'btn-pagar', 'Pagar');
                botao.type = 'button';
                botao.setAttribute('aria-label', 'Pagar a mensalidade de ' + Api.mesAno(f.referencia));
                botao.addEventListener('click', function () { escolher(f); });
                linha.appendChild(botao);
            }
            lista.appendChild(linha);
        });
    }

    // ------------------------------------------------------------------ fluxo de pagamento

    function caminho(ref) { return '/api/aluno/me/pagamentos/' + ref + '/online'; }

    function escolher(f) {
        parar();
        fatura = { referencia: f.referencia, valor: f.valor };
        texto('escolhaMes', 'Mensalidade de ' + Api.mesAno(f.referencia));
        texto('escolhaValor', Api.moeda(f.valor));
        $('btnPix').disabled = false;
        $('btnCartao').disabled = false;
        mostrar('vEscolha');
    }

    function iniciar(metodo) {
        $('btnPix').disabled = true;
        $('btnCartao').disabled = true;
        // "retorno" é a própria página (sem ?consulta): para onde o processador devolve o aluno depois do cartão.
        var retorno = window.location.href.replace(/[?#].*$/, '');
        guardarEmCurso(fatura.referencia);
        Api.post(caminho(fatura.referencia), { metodo: metodo, retorno: retorno }).then(mostrarDetalhe).catch(function (e) {
            limparEmCurso();
            $('btnPix').disabled = false;
            $('btnCartao').disabled = false;
            erroGeral(e.message);
        });
    }

    /** Decide a tela a partir do que a API devolveu. */
    function mostrarDetalhe(d) {
        $('avisoErro').hidden = true;
        if (!d || d.status === 'NENHUMA') { mostrar('vResumo'); return; }
        if (!fatura) fatura = { referencia: d.referencia, valor: d.valor };
        if (d.status === 'AGUARDANDO') {
            if (d.metodo === 'PIX') mostrarPix(d); else mostrarCartao(d);
            iniciarConsulta();
            return;
        }
        parar();
        limparEmCurso();
        if (d.status === 'APROVADA') resultado('ok', 'Pagamento confirmado!', d.mensagem || 'Obrigado!');
        else if (d.status === 'EM_ANALISE') resultado('info', 'Pagamento em análise', d.mensagem || '');
        else resultado('erro', d.status === 'EXPIRADA' ? 'Prazo encerrado' : 'Pagamento não concluído', d.mensagem || '');
        // Atualiza a lista: uma fatura recém paga passa para "Paga".
        carregar().catch(function () { /* mantém a lista antiga */ });
    }

    function mostrarPix(d) {
        texto('pixMes', 'Mensalidade de ' + Api.mesAno(fatura.referencia));
        texto('pixValor', Api.moeda(d.valor));
        var codigo = String(d.pixCopiaCola || '');
        $('pixCodigo').value = codigo;
        var img = $('pixQr');
        var temImagem = typeof d.pixQrBase64 === 'string' && d.pixQrBase64.length > 20 && BASE64_VALIDO.test(d.pixQrBase64);
        if (temImagem) img.src = 'data:image/png;base64,' + d.pixQrBase64;
        img.hidden = !temImagem;
        $('pixQrVazio').hidden = temImagem;
        $('btnSimularPix').hidden = !d.demonstracao;
        $('btnCopiar').textContent = 'Copiar';
        fimMs = Date.now() + Number(d.segundosRestantes || 0) * 1000;
        atualizarContagem();
        if (contagem) clearInterval(contagem);
        contagem = setInterval(atualizarContagem, 1000);
        mostrar('vPix');
    }

    function atualizarContagem() {
        var s = Math.max(0, Math.round((fimMs - Date.now()) / 1000));
        var mm = ('0' + Math.floor(s / 60)).slice(-2), ss = ('0' + (s % 60)).slice(-2);
        texto('pixEspera', s > 0 ? 'Aguardando o pagamento… expira em ' + mm + ':' + ss : 'O prazo acabou. Confirmando…');
        if (s === 0 && contagem) { clearInterval(contagem); contagem = null; consultarAgora(); }
    }

    function mostrarCartao(d) {
        texto('cartaoMes', 'Mensalidade de ' + Api.mesAno(fatura.referencia));
        texto('cartaoValor', Api.moeda(d.valor));
        var botao = $('btnIrCartao');
        var url = enderecoSeguro(d.urlCheckout, d.demonstracao);
        if (url) { botao.href = url; botao.removeAttribute('aria-disabled'); }
        else { botao.removeAttribute('href'); botao.setAttribute('aria-disabled', 'true'); }
        mostrar('vCartao');
    }

    /** Só segue links http(s); em produção, só https. Evita que uma resposta estranha mande o aluno para "javascript:" ou similares. */
    function enderecoSeguro(url, demonstracao) {
        try {
            var u = new URL(String(url));
            if (u.protocol === 'https:' || demonstracao && (u.protocol === 'http:' || u.protocol === 'file:')) return u.href;
        } catch (e) { /* URL inválida */ }
        return null;
    }

    function resultado(tipo, titulo, mensagem) {
        var box = $('resultadoBox');
        box.className = 'resultado resultado-' + tipo;
        texto('resultadoIcone', tipo === 'ok' ? '✓' : tipo === 'info' ? 'i' : '!');
        texto('resultadoTitulo', titulo);
        texto('resultadoTexto', mensagem);
        $('btnResultadoTentar').hidden = tipo !== 'erro';
        mostrar('vResultado');
    }

    // ------------------------------------------------------------------ consulta periódica

    function iniciarConsulta() {
        if (consulta) clearInterval(consulta);
        tentativas = 0;
        consulta = setInterval(function () { if (!document.hidden) consultarAgora(); }, INTERVALO_CONSULTA_MS);
    }

    function consultarAgora() {
        if (!fatura) return;
        Api.get(caminho(fatura.referencia)).then(function (d) {
            tentativas = 0;
            // Mesma situação: só atualiza a contagem; mudou: troca de tela.
            if (d.status === 'AGUARDANDO') { fimMs = Date.now() + Number(d.segundosRestantes || 0) * 1000; return; }
            mostrarDetalhe(d);
        }).catch(function () {
            tentativas++;
            if (tentativas >= 5) { parar(); erroGeral('Perdi a conexão ao acompanhar o pagamento. Se você já pagou, a fatura será atualizada em instantes; recarregue a página para conferir.'); }
        });
    }

    // ------------------------------------------------------------------ botões

    $('btnPix').addEventListener('click', function () { iniciar('PIX'); });
    $('btnCartao').addEventListener('click', function () { iniciar('CARTAO'); });
    $('btnEscolhaVoltar').addEventListener('click', function () { mostrar('vResumo'); });
    function voltarAsFaturas() { parar(); limparEmCurso(); fatura = null; $('avisoErro').hidden = true; mostrar('vResumo'); }
    $('btnPixVoltar').addEventListener('click', voltarAsFaturas);
    $('btnCartaoVoltar').addEventListener('click', voltarAsFaturas);
    $('btnResultadoOk').addEventListener('click', voltarAsFaturas);
    $('btnResultadoTentar').addEventListener('click', function () {
        var f = fatura;
        parar(); limparEmCurso();
        if (f) escolher(f); else mostrar('vResumo');
    });
    // Ao tocar em "Ir para o pagamento seguro" o aluno sai da página: guarda o mês para retomar quando ele voltar.
    $('btnIrCartao').addEventListener('click', function (e) {
        if ($('btnIrCartao').getAttribute('aria-disabled') === 'true') { e.preventDefault(); return; }
        if (fatura) guardarEmCurso(fatura.referencia);
    });
    $('btnCopiar').addEventListener('click', function () {
        var campo = $('pixCodigo');
        var feito = function () { $('btnCopiar').textContent = 'Copiado!'; setTimeout(function () { $('btnCopiar').textContent = 'Copiar'; }, 2000); };
        if (navigator.clipboard && window.isSecureContext) {
            navigator.clipboard.writeText(campo.value).then(feito).catch(function () { copiarAntigo(campo, feito); });
        } else {
            copiarAntigo(campo, feito);
        }
    });
    function copiarAntigo(campo, feito) {
        campo.focus();
        campo.select();
        try { if (document.execCommand('copy')) feito(); } catch (e) { /* o aluno ainda pode copiar à mão: o campo está selecionado */ }
    }
    $('btnSimularPix').addEventListener('click', function () {
        $('btnSimularPix').disabled = true;
        Api.post(caminho(fatura.referencia) + '/simular', { aprovar: true }).then(mostrarDetalhe).catch(function (e) {
            $('btnSimularPix').disabled = false;
            erroGeral(e.message);
        });
    });

    // ------------------------------------------------------------------ início

    Api.get('/api/aluno/me/pagamento-online').catch(function () { return { ativo: false }; }).then(function (o) {
        opcoes = o && o.ativo ? o : { ativo: false, demonstracao: false };
        if (opcoes.ativo) {
            texto('resumoSub', 'Pague online com Pix ou cartão, ou na recepção.');
            texto('instrucoes', 'Toque em Pagar na fatura que deseja quitar. A confirmação é automática: assim que o pagamento for aprovado, a fatura passa para Paga. Você também pode pagar na recepção (Pix, cartão, boleto ou dinheiro).');
        }
        return carregar();
    }).then(function () {
        mostrar('vResumo');
        // Voltou da página do cartão (ou recarregou no meio de um pagamento): retoma de onde parou.
        var emCurso = lerEmCurso();
        if (opcoes.ativo && emCurso) {
            fatura = { referencia: emCurso, valor: 0 };
            Api.get(caminho(emCurso)).then(mostrarDetalhe).catch(function () { limparEmCurso(); });
        }
        if (window.location.search && window.history.replaceState) window.history.replaceState(null, '', window.location.pathname);
    }).catch(function (erro) {
        erroGeral(erro.message);
        texto('statusTexto', 'Indisponível');
    });
})();
