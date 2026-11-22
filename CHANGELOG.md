# Histórico de mudanças

Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/). Datas no formato AAAA-MM-DD.

## [Não lançado] — v3.6

### Adicionado
- Limite de tentativas de "senha atual" na troca de senha da API: 5 erros em 10 minutos por aluno resultam em `429` (RFC-002).
- `TestesDao`: testes das regras dos DAOs que não dependem do banco.
- `LICENSE` (MIT) e este `CHANGELOG.md`.

### Corrigido
- `PagamentoDAO.inserirMensalidade`: só a violação de duplicidade (código 1062) vira "já existe"; chave estrangeira inexistente deixou de sumir (RFC-003).
- `PagamentoDAO.cancelar`: mensagens diferentes para cobrança inexistente e cobrança já paga (RFC-003).
- `FuncionarioDAO.definirAtivo`: o gerente não é desativado nem por engano (RFC-003).

### Pendente
- Testar os DAOs e o pagamento no MySQL e no Mercado Pago reais. Veja `docs/PROXIMOS-PASSOS.md`.

## [3.5] — 2026-11-11 (branch `pagamento-online`)

### Adicionado
- Pagamento da mensalidade por Pix e cartão no site do aluno, com gateway de demonstração e adaptador do Mercado Pago
  (webhook com assinatura, reconsulta do pagamento, baixa única, conferência de divergências).
- Marca "Pix · online" e conferência dos pagamentos online no software.
- Testes do pagamento online (`TestesPagamentoOnline`) e GitHub Actions (`testes.yml`).
- `empacotar.bat` avisa quando o Vida Ativa está aberto.

### Corrigido
- Erro `Incorrect string value` ao salvar ficha: conexão força `utf8mb4` e há migração para o banco.
- `empacotar.bat`: lista de módulos da JVM completa (sem `java.desktop` o aplicativo não abria).

### Removido
- Telas antigas sem uso (`FichaDialog`, `TreinoEditor`).

## [3.4] — 2026-11-06 (branch `main`)

- Histórico de pagamentos e aviso de cobrança (WhatsApp/e-mail), dashboards do gerente e da recepção,
  novo visual, aplicativo de área de trabalho (`empacotar.bat`).

## Primeira versão — 2026-11-05

- Primeira versão do sistema (commit `d042c4e`).
