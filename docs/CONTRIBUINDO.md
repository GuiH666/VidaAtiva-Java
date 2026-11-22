# Como contribuir

## Preparar o ambiente

1. JDK 21 ou superior (`javac -version`), MySQL 8 e VS Code (ou outra IDE).
2. Banco: veja `database/README.md`.
3. `software/config.properties`: copie de `config.properties.example` e coloque a senha do seu MySQL.
4. Abra o software: dois cliques em `software/rodar.bat` (Windows) ou `software/rodar.sh`.
   Não use o botão "Run Code" do VS Code sobre um arquivo solto: o projeto tem pacotes.
5. Site: abra `site/pages/home.html` com a extensão Live Server (o software precisa estar aberto).

## Rodar os testes

Dentro de `software/`: dois cliques em `testar.bat` (Windows) ou `./testar.sh` (Linux/macOS).
Ele baixa as bibliotecas (se faltar), compila e roda `TestesLogica`, `TestesApi`, `TestesPagamentoOnline` e `TestesDao`.

Resultado esperado: `0 falha(s)` em todos. Rode sempre antes de enviar mudanças.

## Fluxo com Git

- `main` sempre funcionando. Cada tarefa em uma branch: `feature/nome-curto` ou `fix/nome-curto`.
- Commits pequenos, **um assunto por commit**. Mensagem no formato `tipo(área): o que mudou`, em português e no imperativo:

  | Tipo | Quando usar | Exemplo |
  |---|---|---|
  | `feat` | funcionalidade nova | `feat(api): limita tentativas de troca de senha` |
  | `fix` | correção de erro | `fix(dao): só duplicidade vira "já existe"` |
  | `test` | só testes | `test: cobre cancelamento de cobrança paga` |
  | `docs` | só documentação | `docs: explica o modo demonstração` |
  | `refactor` | muda o código sem mudar o comportamento | `refactor(ui): separa o painel de fichas` |
  | `chore` / `ci` | arquivos de apoio, scripts, GitHub Actions | `ci: compila o programa inteiro` |

  Evite mensagens como "Atualização", "Correções" ou "Melhorias": daqui a um mês ninguém sabe o que mudou.
- Antes de abrir o Pull Request: compile, rode os testes e abra a tela que você mexeu.
- Peça revisão de outra pessoa do grupo antes de juntar na `main`.
- Junte pelo Pull Request do GitHub (botão **Merge pull request**), não por `git merge` na `main` local. Assim a revisão fica registrada.
- Conflito (por exemplo no `README.md`): `git fetch`, `git merge origin/main` na **sua branch**, resolva os arquivos marcados com `<<<<<<<`, rode os testes e só então dê o push.
- Branch já juntada: apague (`git branch -d nome` e `git push origin --delete nome`).
- Versões: depois de juntar, marque com tag (`git tag -a v3.5.0 -m "Pagamento online"` e `git push origin v3.5.0`) e anote no `CHANGELOG.md`.
- **Nunca** envie `config.properties`, senhas, `lib/`, `out/` ou `target/` (já estão no `.gitignore`).

## Checklist de Pull Request

- [ ] Compila e todos os testes passam.
- [ ] Mexeu no banco? `schema.sql` atualizado **e** migração nova em `database/migrations/`.
- [ ] Mexeu na API? `docs/API.md` e `TestesApi` atualizados.
- [ ] Classe nova tem Javadoc; lógica difícil tem comentário explicando o porquê.
- [ ] Texto de tela em português, claro para quem não conhece o sistema.
- [ ] Nenhuma senha ou dado pessoal real no código ou nos prints.

## Estilo

- Java: 4 espaços, chaves na mesma linha, nomes em português sem acento. `.editorconfig` configura o editor.
- HTML/CSS/JS: veja `site/README.md`.
