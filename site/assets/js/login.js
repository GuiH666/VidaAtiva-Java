/**
 * login.js — Tela de login do aluno (pages/login.html).
 *
 * Fluxo:
 *   1. Se o aluno já está logado, vai direto para a área do aluno.
 *   2. No envio do formulário, chama Api.entrar() (POST /api/login).
 *   3. Sucesso -> aluno.html. Erro -> mostra a mensagem da API no aviso.
 *
 * Depende de: config.js e api.js (carregados antes, na página).
 */
(function () {
    'use strict';

    if (Api.logado()) { window.location.replace('aluno.html'); return; }

    var aviso = document.getElementById('avisoLogin');
    var botao = document.getElementById('btnEntrar');

    function mostrar(texto) { aviso.textContent = texto; aviso.hidden = false; }

    // api.js redireciona para cá com ?sessao=expirada quando o token vence.
    if (new URLSearchParams(window.location.search).get('sessao') === 'expirada') {
        mostrar('Sua sessão expirou. Entre novamente.');
    }

    document.getElementById('formLogin').addEventListener('submit', function (evento) {
        evento.preventDefault();
        var usuario = document.getElementById('usuario').value.trim();
        var senha = document.getElementById('senha').value;
        if (!usuario || !senha) { mostrar('Informe o usuário e a senha.'); return; }

        aviso.hidden = true;
        botao.disabled = true;
        botao.textContent = 'Entrando...';
        Api.entrar(usuario, senha).then(function () {
            window.location.href = 'aluno.html';
        }).catch(function (erro) {
            mostrar(erro.message);
            document.getElementById('senha').value = '';
            botao.disabled = false;
            botao.textContent = 'Entrar';
        });
    });
})();
