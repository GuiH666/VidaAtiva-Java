# API do site

Base: `http://localhost:8080` (configurável em `site/assets/js/config.js` e `software/config.properties`).
Todas as respostas são JSON UTF-8. Erros: `{"erro": "mensagem"}`.
Rotas com **login** exigem o cabeçalho `Authorization: Bearer <token>` (token recebido em `/api/login`, vale 8 h).

| Método | Caminho | Login | Corpo da requisição | Resposta |
|---|---|:-:|---|---|
| GET | `/api/status` | não | – | `{sistema, status}` |
| GET | `/api/planos` | não | – | lista de planos `{id, nome, valorMensal, descricao}` |
| POST | `/api/login` | não | `{login, senha}` | `{token, expiraEmSegundos, nome}` (só aluno entra) |
| GET | `/api/aluno/me` | sim | – | perfil do aluno: plano, vencimento, situação |
| GET | `/api/aluno/me/fichas` | sim | – | fichas **ativas**, com treinos e exercícios |
| GET | `/api/aluno/me/pagamentos` | sim | – | mensalidades (canceladas não aparecem) |
| GET | `/api/aluno/me/avaliacoes` | sim | – | avaliações físicas com IMC |
| POST | `/api/aluno/me/senha` | sim | `{senhaAtual, novaSenha}` | `{ok: true}` |
| GET | `/api/aluno/me/pagamento-online` | sim | – | `{ativo, provedor, demonstracao, metodos}` (404 nas rotas abaixo se desligado) |
| POST | `/api/aluno/me/pagamentos/AAAA-MM/online` | sim | `{metodo: "PIX"\|"CARTAO", retorno}` | inicia ou retoma o pagamento: Pix (QR e copia-e-cola) ou endereço do checkout do cartão |
| GET | `/api/aluno/me/pagamentos/AAAA-MM/online` | sim | – | situação (`AGUARDANDO`, `APROVADA`, `RECUSADA`, `EXPIRADA`, `EM_ANALISE`...) e mensagem |
| POST | `/api/aluno/me/pagamentos/AAAA-MM/online/simular` | sim | `{aprovar: true\|false}` | **só no modo demonstração** (404 no modo real) |
| POST | `/api/pagamentos/webhook/mercadopago` | não | aviso do Mercado Pago | `200`; a assinatura é conferida e o pagamento é reconsultado no Mercado Pago |

O valor cobrado **nunca** vem do cliente: um campo `valor` enviado é ignorado. Detalhes em `PAGAMENTO-ONLINE.md`.

## Códigos HTTP

| Código | Quando |
|---|---|
| 204 | Pré-voo CORS (OPTIONS) |
| 400 | JSON inválido, campo faltando, senha nova inválida |
| 401 | Sem login, token inválido/expirado, credenciais erradas |
| 404 | Rota inexistente |
| 405 | Método HTTP errado para a rota |
| 413 | Corpo maior que 8 KB (o aviso do Mercado Pago tem limite próprio, também pequeno) |
| 429 | Mais de 5 erros de login em 10 min (mesmo IP), ou mais de 8 pagamentos online novos em 10 min (mesmo aluno) |
| 500 | Erro inesperado (detalhes só no console do software) |

## Testando sem o site

Postman/curl:

```bash
curl http://localhost:8080/api/status
curl -X POST http://localhost:8080/api/login -H "Content-Type: application/json" \
     -d '{"login":"pablo","senha":"123456"}'
curl http://localhost:8080/api/aluno/me -H "Authorization: Bearer <token>"
```

## Regras para criar rota nova

1. A rota deve descobrir o aluno **pelo token**, nunca por um id enviado pelo cliente.
2. Devolva um record próprio (como `PerfilAluno`), nunca o `model` inteiro: o model pode ter documento e ids internos.
3. Acrescente o método em `api/PortalDados` e implemente em `api/DadosBanco` (e na versão falsa do `TestesApi`).
4. Acrescente um `case` em `ApiServer.rotear` e escreva o teste em `TestesApi`.
5. Documente aqui.
