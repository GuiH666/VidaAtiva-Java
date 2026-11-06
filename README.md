# Vida Ativa

Sistema para academia em três partes que usam **um único banco MySQL**:

| Parte | O que é | Quem usa | Pasta |
|---|---|---|---|
| **Software** | Programa desktop em Java (Swing) | Gerente, funcionários e professores | `software/` |
| **API** | Servidor HTTP que sobe junto com o software (porta 8080) | O site | `software/.../api/` |
| **Site** | HTML + CSS + JavaScript | Alunos | `site/` |
| **Banco** | MySQL `vida_ativa` | Software e API | `database/` |

```
Software (equipe) ──JDBC──▶ MySQL ◀──JDBC── API (porta 8080) ◀──fetch── Site (aluno)
```

- **Funcionário (recepção)**: painel do dia (cobranças, atrasos, vencimentos, aniversariantes), cadastro de alunos, pagamentos, histórico de pagamentos de cada aluno e aviso de cobrança por WhatsApp/e-mail (mensagem pronta).
- **Gerente**: acompanha a academia num dashboard (cadastros e recebimentos do mês e do dia, atrasos) e cadastra funcionários e professores. **Funcionário**: cadastra alunos e controla pagamentos.
  **Professor**: monta fichas de treino e registra avaliações físicas.
- **Aluno**: entra no site e vê plano, treino, avaliações e mensalidades.

## Estrutura do repositório

```
VidaAtiva_Java/
├── README.md               Este arquivo
├── docs/                   Documentação (comece por aqui se for novo no projeto)
│   ├── ARQUITETURA.md        Como as partes se conectam, camadas, perfis, fluxos
│   ├── GUIA-DO-CODIGO.md     "Onde mexo para...?" + receitas passo a passo
│   ├── DESIGN.md             Identidade visual, componentes e dashboard do gerente
│   ├── API.md                Rotas do site
│   ├── CONTRIBUINDO.md       Ambiente, testes, Git, checklist de Pull Request
│   ├── OPERACAO.md           Incidentes, metas de serviço, roteiros de atendimento, registro de mudanças
│   └── PROXIMOS-PASSOS.md    Melhorias, problemas conhecidos, o que falta
├── database/               schema.sql · seed.sql · migrations/ · README.md
├── software/               Programa desktop + API (Java 21, Maven)
│   ├── rodar.bat / .sh       Compila e abre o software
│   ├── empacotar.bat         Gera o aplicativo "Vida Ativa.exe" (com ícone e atalho na área de trabalho)
│   ├── testar.bat / .sh      Compila e roda os testes automáticos
│   ├── config.properties.example
│   └── src/main/java/br/com/vidaativa/
│       ├── config/ security/ model/ dao/ service/ api/ ui/ ui/panels/ ui/componentes/ ui/grafico/ ui/estilo/
│       └── (cada pacote tem um package-info.java explicando o papel dele)
└── site/                   Site do aluno (veja site/README.md)
    ├── index.html · pages/ · assets/{css,js,img}/
```

## Como rodar (primeira vez)

Requisitos: **JDK 21+** (`javac -version`) e **MySQL 8**.

1. **Banco**: rode `database/schema.sql` e depois `database/seed.sql` (detalhes em `database/README.md`).
2. **Configuração**: copie `software/config.properties.example` para `software/config.properties` e coloque a senha do seu MySQL em `db.password`.
3. **Software + API**: dois cliques em `software/rodar.bat` (Windows) ou `software/rodar.sh`.
   Também funciona com Maven (`cd software && mvn compile exec:java`) ou pela IDE (classe `br.com.vidaativa.ui.Main`).
   **Aplicativo com ícone na área de trabalho (Windows):** dois cliques em `software/empacotar.bat` (veja `docs/APLICATIVO.md`).
   > Não use o botão "Run Code" sobre o `Main.java` solto: o projeto tem pacotes e não compila assim.
4. **Site**: abra `site/pages/home.html` com a extensão *Live Server* do VS Code (ou `python -m http.server 8000` dentro de `site/`).
   O software precisa estar aberto, porque a API roda nele.

### Usuários de demonstração (senha `123456`)

| Usuário | Perfil | Entra em |
|---|---|---|
| `gerente` | Gerente | Software |
| `funcionario` | Funcionário | Software |
| `professor` | Professor | Software |
| `pablo` | Aluno (plano Plus) | Site |

> **Antes de apresentar ou publicar:** troque essas senhas e defina um `api.secret` próprio em `config.properties`.
> O `config.properties` nunca vai para o Git.

## Testes

`software/testar.bat` (ou `./testar.sh`): roda `TestesLogica` (regras, validação, senha, token, números do dashboard e da recepção, histórico de pagamentos, aviso de cobrança, biblioteca de exercícios) e `TestesApi`
(todas as rotas, com dados em memória, sem precisar de MySQL). Esperado: `0 falha(s)`.

## Regras de negócio (resumo)

- **TotalPass**: Básico = R$ 0 (sem cobrança); Plus = + R$ 29,90; Premium = + R$ 49,90.
- **Ficha de treino** vem nos planos Plus e Premium; **avaliação física** só no Premium.
- **Ficha**: Rascunho, Ativa ou Inativa. O aluno vê só a Ativa; ao ativar uma, as outras do aluno ficam Inativas.
  Cada professor edita e exclui só as próprias fichas.
- **Mensalidade**: uma cobrança por aluno por mês; pendente vencida vira Atrasada; ao registrar pagamento o vencimento avança um mês.

Detalhes e fluxos em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Para quem vai continuar o projeto

1. Leia `docs/ARQUITETURA.md` e `docs/GUIA-DO-CODIGO.md`.
2. Siga `docs/CONTRIBUINDO.md` (branches, testes, checklist).
3. Veja `docs/PROXIMOS-PASSOS.md`: lista do que melhorar (visual dos cadastros, problemas já conhecidos, funcionalidades futuras).
