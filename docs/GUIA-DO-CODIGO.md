# Guia do código: "onde mexo para...?"

Leia antes `ARQUITETURA.md`. Caminhos do Java são relativos a `software/src/main/java/br/com/vidaativa/`.

## Mapa rápido

| Quero mudar... | Arquivo |
|---|---|
| Cores, fontes e botões do software (tema escuro) | `ui/Theme.java` (cores/fontes) e `ui/estilo/*` (botão, campo, combo, rolagem) |
| Ícones | `ui/Icones.java` (desenhados em código, sem arquivos de imagem) |
| Componentes prontos (cartão, indicador, pílulas, grades) | `ui/componentes/*` — veja `docs/DESIGN.md` |
| Gráficos (barras e rosca) | `ui/grafico/*` |
| Mensagens de erro/aviso, tabelas, formatação de data e moeda | `ui/UI.java` |
| Layout padrão de formulários (rótulo + campo, seções, ajuda) | `ui/Formulario.java` |
| Janela de login do software | `ui/LoginFrame.java` |
| Menu lateral e quais telas cada perfil vê | `ui/DashboardFrame.java` (`montarMenu`) |
| Tela de alunos (cadastro) | `ui/panels/AlunosPanel.java` |
| Cadastro de funcionários e professores | `ui/panels/PessoalPanel.java` (genérico) e `EquipePanel.java` |
| Tela e geração de mensalidades | `ui/panels/PagamentosPanel.java` |
| Lista de fichas de treino | `ui/panels/FichasPanel.java` |
| Editor de ficha de treino (tela cheia) | `ui/panels/FichaEditor.java` (dados + treinos), `TreinoPanel.java` (exercícios de um treino) e `BibliotecaPanel.java` |
| Exercícios e modelos de ficha prontos | `service/BibliotecaExercicios.java` |
| Avaliações físicas | `ui/panels/AvaliacoesPanel.java` |
| Painel do gerente (dashboard) | `ui/panels/GerentePainel.java` |
| Painel da recepção (funcionário) | `ui/panels/FuncionarioPainel.java` |
| Histórico de pagamentos de um aluno | `ui/panels/HistoricoAlunoPanel.java` (tela) + `service/HistoricoPagamentos.java` (resumo e filtros, testado) + `PagamentoDAO.historicoDoAluno` |
| Aplicativo com ícone (empacotar, atalho, ícone das janelas) | `software/empacotar.bat`, `software/empacotamento/`, `ui/IconeApp.java`, `docs/APLICATIVO.md` |
| Aviso de cobrança (WhatsApp/e-mail) | `ui/panels/AvisoCobrancaDialog.java` (janela) + `service/MensagemCobranca.java` (texto e links, testado) + botão "Avisar aluno" em `PagamentosPanel` |
| Coluna "Financeiro" da lista de alunos | `AlunosPanel` + `HistoricoPagamentos.situacaoFinanceira` |
| Números do dashboard (fórmulas) | `service/Indicadores.java` (testado) + `dao/DashboardDAO.java` (só leitura do banco) |
| Cards do painel inicial do professor | `ui/panels/ResumoPanel.java` + `dao/ResumoDAO.java` |
| Preço da mensalidade por plano/TotalPass | `service/Precificacao.java` |
| Validação de documento (CPF/RG), e-mail, telefone, usuário e senha | `service/Validacao.java` |
| Qualquer SQL | `dao/*.java` |
| Estrutura do banco | `database/schema.sql` (+ migração) |
| Rotas que o site usa | `api/ApiServer.java` |
| Endereço/porta/senha do banco | `software/config.properties` |
| Textos e visual das páginas do site | `site/pages/*.html` e `site/assets/css/*.css` |
| Como o site chama a API | `site/assets/js/api.js` |

## Receitas

### Adicionar um campo ao aluno (ex.: "contato de emergência")

1. **Banco**: coluna em `database/schema.sql` (tabela `alunos`) e uma migração nova em `database/migrations/`.
2. **Model**: campo novo no record `model/Aluno.java`.
3. **DAO**: inclua a coluna nos SQL de `dao/AlunoDAO.java` (SELECT, INSERT, UPDATE) e no método que monta o `Aluno` a partir do `ResultSet`.
   Todo lugar que cria `new Aluno(...)` precisa do novo argumento (o compilador aponta onde).
4. **Tela**: campo em `ui/panels/AlunosPanel.abrirDialogo` (crie o `JTextField`, adicione ao `Formulario`, leia no momento de salvar).
5. **Validação** (se precisar): método em `service/Validacao.java` e teste em `TestesLogica`.
6. **Site** (se o aluno deve ver): acrescente no record `PerfilAluno` em `api/ApiServer.java`, no `aluno.html` e preencha em `aluno.js`.
7. Rode os testes (`docs/CONTRIBUINDO.md`).

### Criar uma tela nova no software

1. Crie `ui/panels/MinhaTelaPanel.java` estendendo `PainelBase` (título + subtítulo no construtor; implemente `recarregar()`).
2. Use `UI.tabela(...)`, `Theme.primary(...)`, `Formulario` e `DialogoForm.abrir(...)` para manter o padrão visual. Para indicadores use `KpiCard`, para blocos `Cartao`, para escolhas curtas `Opcoes` e para telas que mudam de largura `GradeFluida`/`PilhaVertical` (`ui/componentes`).
3. Busque dados por um DAO (crie um se for assunto novo).
4. Registre no menu em `DashboardFrame.montarMenu`, só para os perfis que podem ver.

### Criar uma página nova no site

1. `site/pages/minha-pagina.html`, copiando o cabeçalho/rodapé de uma página existente.
2. CSS em `site/assets/css/minha-pagina.css` (começando com `@import url('base.css');`).
3. JS em `site/assets/js/minha-pagina.js`. Ordem dos scripts: `config.js`, `api.js`, o seu.
4. Páginas que exigem login começam com `if (!Api.logado()) { window.location.replace('login.html'); return; }`.

### Criar uma rota de API

Veja o fim de `docs/API.md`.

### Mudar o preço de um plano

Os valores base ficam na tabela `planos` (banco). O **upgrade TotalPass** (R$ 29,90 / R$ 49,90) está em
`service/Precificacao.java`. A página `site/pages/planos.html` mostra preços vindos da API, mas deixa valores
escritos como reserva caso a API esteja fora do ar; atualize-os também.

## Padrões usados (siga-os para o código continuar uniforme)

- **Nomes em português**, sem acento em identificadores (`mensalidade`, `dataNascimento`).
- **Erros para o usuário**: lance `IllegalArgumentException` (dado inválido) ou `IllegalStateException` (regra violada)
  com mensagem em português. `UI.erro(...)` mostra como aviso.
  Erro técnico (`SQLException`) aparece como erro e é registrado no console.
- **Telas não montam SQL.** Chamam um DAO.
- **DAOs usam `try-with-resources` e `PreparedStatement`** (nunca concatenar texto do usuário no SQL).
  Operações com várias tabelas usam transação (`setAutoCommit(false)`).
- **Formulários**: use `Formulario` + `DialogoForm.abrir`; o bloco que roda ao clicar em Salvar lança exceção se algo estiver errado
  e o diálogo mostra a mensagem sem fechar.
- **Comentários** explicam o **porquê**, em português. Toda classe nova tem Javadoc de classe.

## Testes

`software/src/test/java/br/com/vidaativa/`:
- `TestesLogica`: preços, validações, senha, token. Sem banco.
- `TestesApi`: sobe a API numa porta aleatória com dados em memória e testa as rotas. Sem banco.

As telas e os DAOs ainda não têm teste automático: teste-os abrindo o software com o banco de demonstração.

### Mudar uma definição do dashboard (ex.: o que conta como "em atraso")

1. A fórmula está em `service/Indicadores.calcular` e as definições, em português, no Javadoc da classe.
2. Ajuste o cálculo e o teste correspondente em `TestesLogica.painelGerente` (os dados do teste são pequenos e comentados).
3. O painel (`GerentePainel`) só desenha o resultado; não calcula nada.

### Adicionar um exercício à biblioteca da ficha

Inclua uma linha em `service/BibliotecaExercicios.LISTA` (nome, grupo, séries, repetições, descanso). Se for usar em um
modelo de ficha, o nome precisa ser igual (o teste `biblioteca()` confere).
