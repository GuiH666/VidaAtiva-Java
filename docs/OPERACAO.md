# Operação do serviço (visão ITIL)

Como o grupo opera e muda o Vida Ativa. Complementa `ARQUITETURA.md` (como é feito) e `CONTRIBUINDO.md` (como contribuir).
Metas marcadas como **proposta** ainda não foram medidas: servem de referência para a homologação com o PO.

## 1. Serviços e consumidores

| Serviço | Quem usa | Onde roda |
|---|---|---|
| Cadastro e cobrança (software) | Gerente, funcionário, professor | Computador da recepção (`software/`) |
| Consulta do aluno (site + API) | Aluno | Site em `site/`, API na porta 8080 (sobe junto com o software) |

Fluxo crítico acompanhado: **cadastro do aluno (CPF único) → mensalidade → consulta no site**.

## 2. Classificação de incidentes

| Prioridade | Quando | Exemplos no projeto | Meta de resposta (proposta) |
|---|---|---|---|
| P1 | Ninguém consegue usar o serviço | Banco fora do ar; software não abre; API parada e todos os alunos sem acesso ao site | Reconhecer em 30 min no horário de funcionamento |
| P2 | Um usuário ou uma função fica sem uso | Um aluno não consegue entrar; cadastro recusado sem motivo claro; cobrança errada | Reconhecer em 4 h úteis |
| P3 | Incômodo sem bloquear o trabalho | Texto confuso, alinhamento, mensagem de erro pouco clara | Entra na fila da próxima sprint |

Quem abre: a recepção (ou o professor/aluno, por ela). Quem trata: o grupo de desenvolvimento. O registro fica numa tabela simples
(data, quem relatou, o que aconteceu, prioridade, causa, solução). Incidente que se repete vira **problema** e entra em `PROXIMOS-PASSOS.md`.

## 3. Metas de nível de serviço (propostas)

| Indicador | Meta | Como medir |
|---|---|---|
| API respondendo no horário de funcionamento | 99% | `GET /api/status` (retorna `online`) a cada 5 min |
| Login do aluno | resposta em até 2 s | cronômetro durante a homologação com o PO |
| Cadastro com CPF inválido recusado | 100% dos casos | `TestesLogica` (falha a build se regredir) |
| Mensalidade sem duplicidade | 0 duplicadas por aluno/mês | chave `UNIQUE(aluno_id, referencia)` no banco |

## 4. Roteiros de atendimento (runbook)

**Aluno diz que não consegue entrar no site**
1. Abra `http://localhost:8080/api/status`. Se não responder: o software precisa estar aberto (a API sobe junto) e `api.autostart` deve ser `true`.
2. Se responde `online`, tente o login. Mensagem de usuário/senha inválidos: no software, em **Alunos → Editar**, defina uma **nova senha** e avise o aluno.
3. Se a mensagem for de muitas tentativas (HTTP 429): são 5 falhas em 10 minutos por IP. Aguarde 10 minutos.
4. Persistindo: registre como P2 com o horário e a mensagem exibida.

**Cadastro de aluno recusado**
- "CPF inválido. Confira os 11 dígitos.": digitação errada do CPF; confirme com o documento do aluno.
- "Já existe um aluno cadastrado com esse documento.": o aluno já tem cadastro. Procure-o em **Alunos** (inclusive em "Mostrar inativos") e reative.

**Mensalidade com problema**
- Cobrança errada e ainda não paga: em **Pagamentos**, cancele a cobrança e gere novamente. Cobrança paga não pode ser cancelada.
- Gerar duas vezes no mês não duplica (uma cobrança por aluno por mês).

**Aluno pagou online e a mensalidade não ficou paga**
1. Veja se o software está aberto (a API e a conferência de 30 s rodam dentro dele). Sem webhook público, o aluno pode esperar até ~30 s.
2. Em **Pagamentos** aparece a faixa amarela se o pagamento precisa de conferência (cobrança já paga/cancelada ou valor diferente). Abra *Conferir*.
3. Se nada aparece e passou de 5 minutos: confira no painel do Mercado Pago se o pagamento foi aprovado; se sim, use **Registrar pagamento** e registre como P2.
4. Dinheiro cobrado em duplicidade: devolva pelo painel do Mercado Pago e marque como conferido (o software não devolve sozinho).

**Erro "Incorrect string value ... for column 'nome'" ao salvar (ficha, aluno...)**
1. O banco foi criado com outro conjunto de caracteres (não utf8mb4), então não aceita travessão (—), emojis etc.
2. Rode `database/migrations/v3-1-utf8mb4.sql` (faça backup antes). Ele converte banco e tabelas sem perder dados.
3. Reabra o software. Se continuar, envie o resultado de `SHOW CREATE TABLE treinos;` e `SHOW VARIABLES LIKE 'character_set%';`.

**API ou banco fora do ar (P1)**
1. Confira se o MySQL está rodando e se `config.properties` tem usuário e senha corretos.
2. Reabra o software; a API sobe junto. Porta 8080 ocupada: ajuste `api.port` e o endereço em `site/assets/js/config.js`.

## 5. Registro de mudanças (Change Enablement)

Toda mudança passa por: necessidade → teste automático → revisão por outro integrante → homologação com o PO na Sprint Review → registro abaixo.

| ID | Data | Mudança | Risco | Teste / evidência | Decisão |
|---|---|---|---|---|---|
| RFC-001 | 04/11/2026 | Validação dos dígitos do CPF e gravação com máscara padrão (`Validacao.documento`, `AlunoDAO`, `seed.sql`) | Médio: pode recusar cadastros antes aceitos | 6 testes novos falhavam antes (45 ok / 6 falhas) e passam depois (51 ok / 0 falha) | Implementada; pendente de revisão por outro integrante e de homologação do PO |

Cuidados da RFC-001 ao atualizar um banco que já tem dados:
- O aluno de demonstração tinha CPF `000.000.000-00` (inválido). Rodar o `seed.sql` de novo troca para `529.982.247-25`.
- Alunos reais gravados **sem máscara** antes desta versão não colidem com o mesmo CPF digitado com máscara. Para padronizar, o gerente
  pode abrir e salvar o cadastro (a tela grava no formato novo) ou rodar uma correção de SQL após conferir os dados.

## 6. Melhoria contínua

Lista viva em `PROXIMOS-PASSOS.md`. Em cada Sprint Review o grupo escolhe 1 a 2 itens dela para entrar na próxima sprint.
