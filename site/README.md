# Site do aluno (front-end)

Site estático (HTML + CSS + JavaScript puro, sem build). Fala com o banco **somente pela API**
do software (`GET/POST http://localhost:8080/api/...`). Não existe área de funcionário aqui:
equipe usa o software desktop.

## Estrutura

```
site/
├── index.html          Redireciona para pages/home.html
├── pages/              Uma página HTML por tela
│   ├── home.html       Apresentação da academia
│   ├── planos.html     Planos e preços (preços vêm da API)
│   ├── login.html      Login do aluno
│   ├── aluno.html      Área do aluno: plano, treino, avaliações, pagamentos
│   ├── pagamento.html  Mensalidades: consulta e pagamento por Pix/cartão (se ligado no software)
│   └── checkout-demo.html  Página que simula o banco/cartão (só modo demonstração)
└── assets/
    ├── css/            base.css (comum) + um CSS por página
    ├── js/             config.js · api.js (cliente da API) + um JS por página
    └── img/            logo.jpeg · logo-nome.png · icone-whatsapp.png
```

## Convenções

- Nomes de arquivo em minúsculas, sem acento (`home.html`, `aluno.js`).
- Cada página tem **um** CSS próprio e, se precisar de lógica, **um** JS próprio com o mesmo nome.
  Nada de `<style>` ou `<script>` com código dentro do HTML.
- Estilo comum (cores, fontes, cabeçalho, botões, rodapé) fica em `assets/css/base.css`.
  Mudou a identidade visual? Comece por lá.
- Ordem dos scripts na página: `config.js` → `api.js` → script da página.
- Dados vindos da API entram na tela com `textContent` (nunca `innerHTML`), para evitar XSS.
- O token de login fica em `sessionStorage` (some ao fechar a aba).

## Como rodar

Sirva a pasta `site/` com um servidor estático (extensão *Live Server* do VS Code, ou
`python -m http.server 8000` dentro de `site/`) e abra `pages/home.html`.
O software precisa estar aberto (a API sobe junto, porta 8080).
O endereço da API fica em `assets/js/config.js`.
