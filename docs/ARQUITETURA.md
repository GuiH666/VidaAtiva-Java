# Arquitetura

## Visão geral

```
 EQUIPE                                              ALUNO
┌──────────────────────────────┐                ┌──────────────────────┐
│ Software desktop (Java Swing)│                │ Site (HTML/CSS/JS)   │
│  ui/  ──▶ dao/ ──▶ MySQL     │                │  pages/ + assets/    │
│  api/ (HTTP, porta 8080) ◀───┼──── fetch ─────┤  assets/js/api.js    │
│        └──▶ dao/ ──▶ MySQL   │    (JSON)      └──────────────────────┘
└──────────────────────────────┘
```

- **Um banco só** (`vida_ativa`). O software grava; o site lê pela API. Não há segundo banco nem sincronização.
- **A API roda dentro do software** (mesmo programa, outra thread). Ao abrir o software, a API sobe na porta 8080.
  Se o software estiver fechado, o site não consegue logar. Em produção a API poderia rodar sozinha
  (`br.com.vidaativa.api.ApiServer` tem `main`).
- **O site nunca fala com o banco.** Só chama `/api/...`.
- **O aluno não usa o software, e a equipe não usa o site.**

## Camadas do software (`software/src/main/java/br/com/vidaativa`)

```
ui/ (telas)  ─┐
              ├──▶ service/ (regras)  ──▶ dao/ (SQL) ──▶ MySQL
api/ (rotas) ─┘                              │
                                             └── model/ (records com os dados)
config/ (config.properties, conexão)   security/ (senha, token)
```

| Pacote | Responsabilidade | Regra de ouro |
|---|---|---|
| `model` | Records simples que representam linhas do banco. | Só dados, nada de lógica de tela ou SQL. |
| `dao` | Todo o SQL do sistema. Um DAO por assunto. | **Só aqui se escreve SQL.** |
| `service` | Regras que não dependem de tela nem de banco: validação, cálculo de mensalidade, login. | Fácil de testar; sem Swing, sem JDBC. |
| `security` | Hash de senha (PBKDF2) e token de login (HMAC). | Nunca guardar senha em texto puro. |
| `config` | Leitura do `config.properties` e conexão JDBC. | Nenhuma senha no código. |
| `api` | Servidor HTTP e rotas do site. | Sempre descobrir o aluno pelo token, nunca por id vindo do cliente. |
| `ui` / `ui.panels` | Janelas e telas Swing. | Tela chama DAO/service; não monta SQL. |

## Perfis e permissões

O menu lateral é montado em `ui/DashboardFrame.montarMenu` conforme o perfil de quem entrou.

| Perfil | Painel | Equipe | Alunos | Pagamentos | Fichas | Avaliações |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Gerente | sim (dashboard) | sim | sim (edita) | sim | – | – |
| Funcionário | sim | – | sim (edita) | sim | – | – |
| Professor | sim | – | só consulta | – | sim | sim |
| Aluno | só no site | | | | | |

> Atenção: hoje a barreira entre perfis é só o menu. Os painéis não conferem o perfil por conta própria.
> Se algum dia houver outra forma de abrir um painel, confira o perfil dentro dele também.

## Fluxos principais

**Aluno novo → aparece no site**
1. Funcionário cadastra o aluno em *Alunos* (`AlunosPanel` → `AlunoDAO.cadastrar`): cria o usuário (login/senha) e o aluno numa transação.
2. O aluno abre o site, entra em `login.html` → `POST /api/login` → recebe um token.
3. `aluno.html` chama `/api/aluno/me`, `/me/fichas`, `/me/pagamentos`, `/me/avaliacoes` com o token.

**Ficha de treino**
1. Professor cria a ficha em *Fichas* (`FichasPanel` abre o `FichaEditor` em tela cheia: dados, treinos e biblioteca de exercícios) e salva como **Ativa**.
2. `FichaTreinoDAO.salvar` grava ficha + treinos + exercícios numa transação e deixa as outras fichas ativas do aluno como **Inativas**.
3. O site mostra só a ficha ativa (`/api/aluno/me/fichas`).

**Mensalidade**
1. Funcionário clica *Gerar mensalidades do mês* → `PagamentoDAO.gerarMensalidadesDoMes` cria uma cobrança por aluno ativo
   (valor de `Precificacao.valorMensalidade`; TotalPass Básico não gera cobrança).
2. Pendente com vencimento passado vira Atrasado sozinho (`PagamentoDAO.marcarAtrasados`, chamado ao listar).
3. *Registrar pagamento* marca como Pago e avança o vencimento do aluno em um mês.
4. O aluno só consulta no site (`pagamento.html`).

## Dashboard do gerente

`GerentePainel` pede os dados uma vez ao `DashboardDAO` (alunos, cobranças e resumo) e chama `Indicadores.calcular` para o
mês escolhido; trocar de mês recalcula em memória, sem novo acesso ao banco. As definições de cada número (cadastro,
recebido, a receber, em atraso, adimplência) estão no Javadoc de `service/Indicadores`.

## Painel da recepção e histórico do aluno

- O funcionário abre no `FuncionarioPainel` (o que cobrar hoje, vencimentos, alunos sem mensalidade, aniversariantes). Os números vêm
  do mesmo `Indicadores` do dashboard do gerente.
- Em *Pagamentos* (gerente e funcionário), selecionar uma cobrança mostra à direita o `HistoricoAlunoPanel`: só as cobranças daquele aluno
  (`PagamentoDAO.historicoDoAluno`), com resumo e filtros por situação e ano (`HistoricoPagamentos`).
- Em *Pagamentos*, o botão "Avisar aluno" abre `AvisoCobrancaDialog` para a cobrança selecionada (só pendente/atrasada). O texto vem de
  `MensagemCobranca`; os botões abrem `wa.me` ou `mailto:` pelo `Desktop.browse`. Nada é enviado pelo software nem gravado no banco.
- Em *Alunos* (gerente e funcionário), a coluna Financeiro e o filtro "Só em atraso" usam `HistoricoPagamentos.situacaoFinanceira`.

## Segurança em resumo

- Senha: PBKDF2-HMAC-SHA256 com salt (`security/PasswordUtil`). Hash antigo (SHA-256) é aceito e regravado no primeiro login.
- Login do site: token HMAC de 8 h (`security/TokenService`), enviado em `Authorization: Bearer`.
- API: limite de 5 erros de login em 10 min por IP (429), corpo máximo de 8 KB, CORS configurável (`api.cors.origin`).
- Aluno nunca recebe documento, ids internos ou usuário de outra pessoa (records próprios em `ApiServer`).
- Site: dados entram na tela com `textContent` (evita XSS).
- Segredos ficam em `software/config.properties` (fora do Git).
