# Banco de dados (MySQL 8)

Um único banco, `vida_ativa`. O software grava nele por JDBC e o site lê pela API.

| Arquivo | Para quê |
|---|---|
| `schema.sql` | Cria o banco e todas as tabelas (versão atual). Para instalação nova. |
| `seed.sql` | Dados de demonstração (usuários, um aluno com plano, pagamentos, ficha e avaliação). Pode rodar mais de uma vez. |
| `migrations/v1-para-v2.sql` | Atualiza um banco criado com o schema antigo. Pode rodar mais de uma vez. |
| `migrations/v3-1-utf8mb4.sql` | Converte banco e tabelas para utf8mb4. Rode se aparecer "Incorrect string value" ao salvar (acentos/travessão). Pode rodar mais de uma vez. |
| `migrations/v3-pagamento-online.sql` | Cria a tabela `transacoes_pagamento` (pagamento online). Pode rodar mais de uma vez. |

## Instalar do zero

```bash
mysql -u root -p --default-character-set=utf8mb4 < database/schema.sql
mysql -u root -p --default-character-set=utf8mb4 < database/seed.sql
```

No MySQL Workbench: abra o arquivo e execute o script inteiro (raio, sem selecionar trecho).

## Tabelas

```
usuarios ──┬── funcionarios       (gerente e funcionários)
           ├── professores
           └── alunos ──┬── pagamentos          (uma cobrança por aluno por mês)
                        │            └── transacoes_pagamento  (tentativas de pagamento online: Pix/cartão)
                        ├── fichas_treino ── treinos ── exercicios
                        └── avaliacoes_fisicas
planos ───── alunos
```

- **usuarios**: login de todo mundo (`username`, `senha_hash`, `perfil` = GERENTE, FUNCIONARIO, PROFESSOR ou ALUNO).
  Cada funcionário, professor e aluno tem uma linha aqui **e** uma linha na tabela do seu tipo.
- **planos**: Básico, Plus, Premium (`valor_mensal`).
- **alunos**: `plano_id`, `tipo_cliente` (CONVENCIONAL/TOTALPASS), `forma_pagamento`, `vencimento` (próximo vencimento), `data_inicio`.
- **pagamentos**: `referencia` (mês AAAA-MM), `status` (PENDENTE, PAGO, ATRASADO, CANCELADO).
  Chave única `(aluno_id, referencia)` impede duas cobranças do mesmo mês.
- **transacoes_pagamento**: cada tentativa de pagamento online (`pagamento_id`, `provedor`, `metodo` PIX/CARTAO, `status`, `valor`). Criada sozinha pelo software
  quando o pagamento online é ligado, ou por `migrations/v3-pagamento-online.sql`. Veja `docs/PAGAMENTO-ONLINE.md`.
- **fichas_treino**: `status` (RASCUNHO, ATIVA, INATIVA); só a ATIVA aparece para o aluno no site.
- **treinos** e **exercicios**: apagados junto com a ficha (`ON DELETE CASCADE`).

## Regras para mudar o banco

1. Mudou uma tabela? Atualize o `schema.sql` **e** crie uma migração nova em `migrations/` (nome `vX-para-vY.sql`)
   que funcione em um banco já existente e possa rodar mais de uma vez.
2. Ajuste os DAOs em `software/src/main/java/br/com/vidaativa/dao/` e os `model/` correspondentes.
3. Rode `TestesLogica` e `TestesApi` (veja `docs/CONTRIBUINDO.md`).
