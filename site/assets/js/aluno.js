/**
 * aluno.js — Área do aluno (pages/aluno.html).
 *
 * Busca na API (com o token do login) os dados do aluno, a ficha ativa, as avaliações e
 * as mensalidades, e preenche a página. Também controla os modais (treino completo,
 * meus dados, trocar senha). Sem login, volta para login.html.
 *
 * Rotas usadas: /api/aluno/me, /me/fichas, /me/pagamentos, /me/avaliacoes, /me/senha.
 * Depende de: config.js e api.js.
 */
(function () {
    'use strict';

    if (!Api.logado()) { window.location.replace('login.html'); return; }

    function $(id) { return document.getElementById(id); }
    function texto(id, valor) { var e = $(id); if (e) e.textContent = valor; }
    function criar(tag, classe, conteudo) {
        var e = document.createElement(tag);
        if (classe) e.className = classe;
        if (conteudo !== undefined) e.textContent = conteudo;
        return e;
    }
    function esvaziar(e) { while (e.firstChild) e.removeChild(e.firstChild); }
    function primeiroNome(nome) { return String(nome || '').trim().split(/\s+/)[0] || ''; }

    var SITUACAO = {
        EM_DIA: { chip: 'Em dia', status: 'Mensalidade em dia', classe: '' },
        PENDENTE: { chip: 'A vencer', status: 'Mensalidade a vencer', classe: 'pendente' },
        ATRASADO: { chip: 'Em atraso', status: 'Mensalidade em atraso', classe: 'atrasado' }
    };

    // ---------------------------------------------------------------- preenchimento

    function plano(m) {
        texto('nomeAluno', primeiroNome(m.nome));
        texto('nomeTopo', 'Olá, ' + primeiroNome(m.nome));
        texto('nomePlano', 'Plano ' + m.plano.nome);
        texto('dataInicio', Api.data(m.clienteDesde));
        texto('dataFim', Api.data(m.proximoVencimento));

        var dias = Api.diasAte(m.proximoVencimento);
        if (dias === null) {
            texto('diasRestantes', '—');
        } else if (dias < 0) {
            texto('diasRestantes', '0');
            texto('rotuloDias', 'vencida há ' + Math.abs(dias) + (Math.abs(dias) === 1 ? ' dia' : ' dias'));
        } else {
            texto('diasRestantes', String(dias));
            texto('rotuloDias', dias === 1 ? 'dia até o vencimento' : 'dias até o vencimento');
        }

        // barra: andamento do ciclo mensal atual (um mês antes do vencimento até o vencimento)
        var fim = Api.paraData(m.proximoVencimento);
        if (fim) {
            var inicio = new Date(fim.getTime());
            inicio.setMonth(inicio.getMonth() - 1);
            var pct = (Date.now() - inicio.getTime()) / (fim.getTime() - inicio.getTime()) * 100;
            $('barraPlano').style.width = Math.max(0, Math.min(100, pct)) + '%';
        }

        var s = SITUACAO[m.situacaoPagamento] || SITUACAO.EM_DIA;
        texto('statusTexto', s.status);
        if (s.classe) $('statusAluno').classList.add(s.classe);
        texto('chipPagamento', s.chip);
        if (s.classe) $('chipPagamento').classList.add(s.classe);

        texto('pagVencimento', Api.data(m.proximoVencimento));
        var valor = Number(m.valorMensalidade);
        texto('pagValor', valor === 0 ? 'Coberto pelo TotalPass' : Api.moeda(valor));
        texto('pagForma', Api.forma(m.formaPagamento));

        texto('dadoNome', m.nome);
        texto('dadoEmail', m.email || '—');
        texto('dadoTelefone', m.telefone || '—');
        texto('dadoPlano', m.plano.nome);
        texto('dadoTipo', m.tipoCliente === 'TOTALPASS' ? 'TotalPass' : 'Convencional');
        texto('dadoDesde', Api.data(m.clienteDesde));
    }

    function treino(fichas) {
        var ficha = fichas && fichas[0];
        var lista = $('treinoLista');
        esvaziar(lista);

        if (!ficha) {
            lista.appendChild(criar('p', 'vazio-texto', 'Você ainda não tem uma ficha de treino ativa. Fale com o seu professor.'));
            texto('treinoStatus', 'Sem ficha');
            return;
        }

        texto('treinoStatus', 'Atualizado');
        texto('treinoSubtitulo', ficha.nome + (ficha.objetivo ? ' · ' + ficha.objetivo : ''));
        ficha.treinos.forEach(function (t, i) {
            var item = criar('div');
            item.appendChild(criar('span', null, ('0' + (i + 1)).slice(-2)));
            item.appendChild(criar('strong', null, t.nome));
            item.appendChild(criar('small', null, t.diaSemana || 'Dia livre'));
            lista.appendChild(item);
        });

        texto('tituloTreino', ficha.nome);
        var completo = $('treinoCompleto');
        esvaziar(completo);
        ficha.treinos.forEach(function (t) {
            var bloco = criar('div');
            bloco.appendChild(criar('strong', null, t.nome + (t.diaSemana ? ' — ' + t.diaSemana : '')));
            if (!t.exercicios.length) bloco.appendChild(criar('span', null, 'Nenhum exercício cadastrado neste treino.'));
            t.exercicios.forEach(function (e) {
                var partes = [e.nome];
                if (e.series || e.repeticoes) partes.push((e.series || '') + 'x' + (e.repeticoes || ''));
                if (e.carga) partes.push(e.carga);
                if (e.descanso) partes.push('Pausa de ' + e.descanso);
                var linha = partes.join(' · ');
                if (e.observacoes) linha += ' — ' + e.observacoes;
                bloco.appendChild(criar('span', null, linha));
            });
            completo.appendChild(bloco);
        });
        if (ficha.observacoes) {
            var obs = criar('div', 'treino-recomendacao');
            obs.appendChild(criar('strong', null, 'Recomendações do professor'));
            obs.appendChild(criar('span', null, ficha.observacoes));
            completo.appendChild(obs);
        }
        if (ficha.professor) texto('treinoIntro', 'Ficha elaborada por ' + ficha.professor + '. Siga a ordem dos exercícios e respeite os intervalos.');
        $('btnTreino').disabled = false;

        if (ficha.dataRevisao) {
            var d = Api.diaMes(ficha.dataRevisao);
            texto('revisaoDia', d.dia);
            texto('revisaoMes', d.mes);
            texto('revisaoTitulo', 'Revisão da ficha');
            var faltam = Api.diasAte(ficha.dataRevisao);
            texto('revisaoDetalhe', faltam < 0 ? 'Revisão atrasada — fale com o professor'
                : faltam === 0 ? 'É hoje!' : 'Faltam ' + faltam + (faltam === 1 ? ' dia' : ' dias'));
        } else {
            texto('revisaoTitulo', ficha.nome);
            texto('revisaoDetalhe', 'Sem data de revisão definida.');
        }
    }

    function avaliacoes(lista) {
        var alvo = $('listaAvaliacoes');
        esvaziar(alvo);
        if (!lista || !lista.length) {
            alvo.appendChild(criar('p', 'vazio-texto', 'Você ainda não tem avaliações físicas registradas.'));
            return;
        }

        var ultima = lista[0];
        texto('imcValor', ultima.imc === null ? '—' : Api.numero(ultima.imc, 1));
        texto('avaliacaoResumo', 'Última avaliação em ' + Api.data(ultima.data) + '.');

        lista.slice(0, 4).forEach(function (a) {
            var linha = criar('div', 'aula');
            var d = Api.diaMes(a.data);
            var dia = criar('div', 'dia-aula');
            dia.appendChild(criar('strong', null, d.dia));
            dia.appendChild(criar('span', null, d.mes));
            var info = criar('div', 'info-aula');
            var resumo = Api.numero(a.peso, 1) + ' kg';
            if (a.percentualGordura !== null) resumo += ' · ' + Api.numero(a.percentualGordura, 1) + '% de gordura';
            info.appendChild(criar('strong', null, resumo));
            var detalhe = [];
            if (a.imc !== null) detalhe.push('IMC ' + Api.numero(a.imc, 1));
            detalhe.push('Altura ' + Api.numero(a.altura, 2) + ' m');
            if (a.professor) detalhe.push('Prof. ' + a.professor);
            info.appendChild(criar('span', null, detalhe.join(' · ')));
            linha.appendChild(dia);
            linha.appendChild(info);
            linha.appendChild(criar('span', 'confirmada', 'Registrada'));
            alvo.appendChild(linha);
        });
    }

    // ---------------------------------------------------------------- carregar

    function falha(erro) {
        var aviso = $('avisoErro');
        aviso.textContent = erro.message;
        aviso.hidden = false;
        texto('statusTexto', 'Indisponível');
    }

    Promise.all([
        Api.get('/api/aluno/me'),
        Api.get('/api/aluno/me/fichas'),
        Api.get('/api/aluno/me/avaliacoes')
    ]).then(function (r) {
        plano(r[0]);
        treino(r[1]);
        avaliacoes(r[2]);
    }).catch(falha);

    // ---------------------------------------------------------------- modais e botões

    function alternarModal(id, aberto) {
        $(id).hidden = !aberto;
        document.body.classList.toggle('modal-aberto', aberto);
    }

    $('btnSair').addEventListener('click', Api.sair);
    $('btnTreino').addEventListener('click', function () { alternarModal('modalTreino', true); });
    $('btnDados').addEventListener('click', function () { alternarModal('modalDados', true); });
    $('btnSenha').addEventListener('click', function () {
        $('formSenha').reset();
        texto('msgSenha', '');
        alternarModal('modalSenha', true);
    });

    Array.prototype.forEach.call(document.querySelectorAll('[data-fechar]'), function (botao) {
        botao.addEventListener('click', function () { alternarModal(this.getAttribute('data-fechar'), false); });
    });
    Array.prototype.forEach.call(document.querySelectorAll('.modal-backdrop'), function (fundo) {
        fundo.addEventListener('click', function (e) { if (e.target === fundo) alternarModal(fundo.id, false); });
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape') return;
        Array.prototype.forEach.call(document.querySelectorAll('.modal-backdrop'), function (m) { if (!m.hidden) alternarModal(m.id, false); });
    });

    $('formSenha').addEventListener('submit', function (e) {
        e.preventDefault();
        var msg = $('msgSenha');
        var atual = $('senhaAtual').value;
        var nova = $('novaSenha').value;
        msg.className = 'mensagem-form erro';
        if (!atual) { msg.textContent = 'Informe a senha atual.'; return; }
        if (nova.length < 6) { msg.textContent = 'A nova senha precisa ter pelo menos 6 caracteres.'; return; }
        if (nova !== $('novaSenha2').value) { msg.textContent = 'As duas senhas novas não são iguais.'; return; }

        var botao = $('btnSalvarSenha');
        botao.disabled = true;
        Api.post('/api/aluno/me/senha', { senhaAtual: atual, novaSenha: nova }).then(function () {
            msg.className = 'mensagem-form ok';
            msg.textContent = 'Senha alterada com sucesso!';
            $('formSenha').reset();
        }).catch(function (erro) {
            msg.textContent = erro.message;
        }).then(function () { botao.disabled = false; });
    });
})();
