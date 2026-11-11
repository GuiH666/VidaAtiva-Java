# Pagamento online (Pix e cartão no site do aluno)

O aluno paga a mensalidade pela **Área do aluno → Mensalidades** e o pagamento é contabilizado sozinho no software
(a cobrança vira **Paga**, o próximo vencimento avança e o recebimento entra nos números do dashboard).

> **Estado desta entrega:** a parte do site, a API, as regras e a tela do software estão prontas e testadas
> (`TestesPagamentoOnline`, 125 verificações). A integração com o **Mercado Pago real nunca foi executada**: faltam a conta
> da academia e um pagamento de teste (passo a passo na seção 3). Enquanto isso, o modo **demonstração** mostra o fluxo inteiro.

## 1. Como funciona

```
Site (aluno) ──▶ API ──▶ Mercado Pago ──▶ banco do aluno / página do cartão
                  │            │
                  │            └─ aviso (webhook) ─┐
                  ▼                                ▼
               MySQL  ◀── baixa da cobrança ◀── ServicoPagamentoOnline ◀── conferência a cada 30 s
                  ▲
        Software (recepção) mostra "Pix · online" e avisa quando chega pagamento
```

1. O aluno toca em **Pagar** numa fatura em aberto e escolhe **Pix** ou **Cartão**.
2. **Pix:** a tela mostra QR Code e "copia e cola" e fica conferindo sozinha (4 em 4 s).
   **Cartão:** o aluno vai para a página segura do Mercado Pago, paga lá e volta ao site.
3. A confirmação pode chegar por três caminhos (o primeiro que chegar vale; os outros não repetem a baixa):
   o aluno consultando, o aviso do Mercado Pago (webhook) ou a conferência periódica da própria API (a cada 30 s).
   A conferência periódica garante que funcione **mesmo sem endereço público** para o webhook.
4. Confirmado e com valor igual ao cobrado, o sistema dá baixa pela mesma rotina da recepção (`PagamentoDAO.registrarPagamento`).

Telas: `docs/img/pagamento-online-*.png` (site) e `docs/img/software-*.png` (software).

## 2. Segurança (o que o código garante)

- **Dados do cartão nunca passam pelo nosso sistema.** O cartão é digitado na página do Mercado Pago.
- **O valor vem sempre do banco**, nunca do navegador. Um `valor` enviado pelo cliente é ignorado.
- **O aluno só alcança as próprias cobranças**: a mensalidade é achada por (aluno do token de login, referência AAAA-MM).
- **Webhook não é confiado**: a assinatura (`x-signature`, HMAC-SHA256) é conferida e, mesmo assim, o sistema **pergunta de novo** ao Mercado Pago
  qual é a situação do pagamento. Aviso forjado ou repetido não baixa nada.
- **Só baixa se o gateway disser "aprovado" e o valor recebido for exatamente o da cobrança.**
- **Baixa atômica**: só uma thread/requisição consegue mudar a transação para APROVADA, então a cobrança nunca é paga duas vezes (teste com 12 threads × 20 rodadas). Se o dinheiro entrar numa transação já marcada como recusada/falha, ela vira *precisa de conferência* (nunca é descartada em silêncio).
- No Pix, o aviso só vale se for do **mesmo pagamento** criado para aquela transação.
- Limite de 8 pagamentos novos por aluno a cada 10 minutos; endereço de retorno do cartão é validado (`pagamento.url_site`).
- O token do Mercado Pago não aparece em log nem em resposta (teste de não vazamento). Prefira a variável `VIDAATIVA_MP_ACCESS_TOKEN`.
- Rota de simulação existe **só no modo demonstração** (404 no modo real).

## 3. Como ligar

Em `software/config.properties` (modelo em `config.properties.example`):

| Chave | Valor |
|---|---|
| `pagamento.provedor` | `desligado` (padrão: só a recepção recebe) · `simulado` (demonstração) · `mercadopago` (real) |
| `pagamento.url_site` | endereço do site dos alunos (trava o retorno do cartão) |
| `pagamento.mp.access_token` | token da conta Mercado Pago da academia (**secreto**). Comece pelo de **teste** |
| `pagamento.mp.webhook_secret` | segredo da assinatura (painel do Mercado Pago → Webhooks). **Obrigatório** quando `url_api_publica` está preenchida (senão o pagamento online não liga) |
| `pagamento.url_api_publica` | endereço **https público** da API, para o aviso na hora. Vazio = só a conferência de 30 s |
| `pagamento.pix.minutos` / `pagamento.cartao.minutos` | validade do Pix (30) e da página do cartão (120; máximo 2820 = 47 h) |

A tabela `transacoes_pagamento` é criada sozinha na primeira vez (ou rode `database/migrations/v3-pagamento-online.sql`).

**Primeiro teste com o Mercado Pago (obrigatório antes de usar de verdade):**
1. Crie a conta da academia e pegue as credenciais de **teste** (access token).
2. `pagamento.provedor=mercadopago` + token de teste; reabra o software.
3. No site, pague uma mensalidade de teste por Pix e por cartão (cartões de teste do Mercado Pago). Confira que a fatura vira Paga e que aparece "Pix · online" no software.
4. Só então troque para as credenciais de produção.

**Modo demonstração:** `pagamento.provedor=simulado`. A página `checkout-demo.html` finge ser o banco/Mercado Pago.
**Atenção: no modo demonstração a cobrança é marcada como PAGA de verdade no banco**, sem cobrar nada. Use só com banco de teste.

## 4. O que a recepção vê no software

- **Pagamentos → histórico do aluno:** a coluna *Forma* mostra `Pix · online` / `Cartão · online` (ou `· demo`) para o que o aluno pagou pelo site.
- **Faixa verde** na tela Pagamentos quando chega pagamento novo (a lista se atualiza sozinha, a cada 15 s enquanto a tela está aberta).
- **Faixa amarela "precisa de conferência"** + janela **Pagamentos online para conferir**, para dois casos em que o sistema **não baixa sozinho**:
  - *Cobrança já estava paga/cancelada* (ex.: a recepção recebeu antes, ou o aluno pagou Pix e depois cartão): o dinheiro entrou duas vezes. O software **não devolve dinheiro sozinho**: devolva pelo painel do Mercado Pago e clique em **Marcar como conferido**.
  - *Valor diferente do cobrado*: nada foi baixado; confira no painel e, se estiver certo, use **Registrar pagamento**.

## 5. Estados da transação (`transacoes_pagamento.status`)

| Status | Significa |
|---|---|
| CRIADA / AGUARDANDO | iniciada; esperando o aluno pagar |
| APROVADA | pago, valor correto, **baixa dada** |
| RECUSADA / EXPIRADA / FALHOU | não pagou (cartão recusado, tempo esgotado, erro ao falar com o gateway; nada é cobrado) |
| APROVADA_SEM_BAIXA | pagou, mas a cobrança já estava paga/cancelada → conferir/devolver |
| DIVERGENTE | pagou valor diferente do cobrado → conferir |
| CONFERIDA | a recepção resolveu um dos dois casos acima |

Pagamento aprovado **depois** de a transação expirar ainda é aceito (o dinheiro entrou).

## 6. Limites conhecidos

- Integração real com o Mercado Pago **não testada** contra o serviço (só contra um servidor falso que imita a API).
- DAOs/SQL (`TransacaoDAO`, novas consultas de `PagamentoDAO`) **não foram executados contra um MySQL real**.
- Sem endereço público para o webhook, a confirmação depende da conferência de 30 s (o aluno espera até ~30 s depois de pagar).
- Se o aluno gerar um Pix, não pagar e trocar para cartão, e depois pagar os dois, o segundo cai em "precisa de conferência" (devolução manual).
- O QR Code da demonstração é ilustrativo (não é escaneável).
- Reembolso, parcelamento e estorno **não** são feitos pelo sistema, e **estorno/chargeback depois de pago não é detectado**: a cobrança continua Paga. Confira o painel do Mercado Pago periodicamente.
- `pagamento.mp.url_base` precisa ser https (o software recusa ligar se não for). Sem `pagamento.url_site`, o retorno do cartão aceita qualquer endereço: preencha.
- Dois cliques simultâneos em *Pagar* podem gerar dois Pix; só um deles será baixado, o outro cai em conferência.
- Empacotamento: `empacotar.bat` inclui os módulos Java `java.net.http` e `jdk.crypto.ec` (HTTPS); gerar o `.exe` e testar no Windows continua pendente.

## 7. Onde está o código

| O quê | Onde |
|---|---|
| Regras (fluxo, proteções, baixa) | `online/ServicoPagamentoOnline.java` |
| Gateways | `online/GatewayPagamento.java`, `SimuladoGateway.java`, `MercadoPagoGateway.java` |
| Escolha do provedor | `online/PagamentoOnline.java` (`daConfiguracao`) |
| Banco | `dao/TransacaoDAO.java`, `database/schema.sql`, `database/migrations/v3-pagamento-online.sql` |
| Rotas | `api/ApiServer.java` (veja `docs/API.md`) |
| Site | `site/pages/pagamento.html`, `assets/js/pagamento.js`, `checkout-demo.html` |
| Software | `ui/panels/PagamentosPanel.java` (faixa e verificação), `ConferenciaOnlineDialog.java`, `HistoricoAlunoPanel.java` |
| Testes | `src/test/java/.../TestesPagamentoOnline.java` |
