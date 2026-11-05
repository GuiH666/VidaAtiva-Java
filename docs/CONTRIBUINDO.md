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
Ele baixa as bibliotecas (se faltar), compila e roda `TestesLogica` e `TestesApi`.

Resultado esperado: `0 falha(s)` nos dois. Rode sempre antes de enviar mudanças.

## Fluxo com Git

- `main` sempre funcionando. Cada tarefa em uma branch: `feature/nome-curto` ou `fix/nome-curto`.
- Commits pequenos, mensagem no imperativo e em português: `Adiciona filtro por status em Pagamentos`.
- Antes de abrir o Pull Request: compile, rode os testes e abra a tela que você mexeu.
- Peça revisão de outra pessoa do grupo antes de juntar na `main`.
- **Nunca** envie `config.properties`, senhas, `lib/`, `out/` ou `target/` (já estão no `.gitignore`).

## Checklist de Pull Request

- [ ] Compila e os dois testes passam.
- [ ] Mexeu no banco? `schema.sql` atualizado **e** migração nova em `database/migrations/`.
- [ ] Mexeu na API? `docs/API.md` e `TestesApi` atualizados.
- [ ] Classe nova tem Javadoc; lógica difícil tem comentário explicando o porquê.
- [ ] Texto de tela em português, claro para quem não conhece o sistema.
- [ ] Nenhuma senha ou dado pessoal real no código ou nos prints.

## Estilo

- Java: 4 espaços, chaves na mesma linha, nomes em português sem acento. `.editorconfig` configura o editor.
- HTML/CSS/JS: veja `site/README.md`.
