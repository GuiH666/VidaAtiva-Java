-- Vida Ativa v3.1: corrige o erro "Incorrect string value: '\xE2\x80\x94 ...' for column 'nome'"
-- (acontece ao salvar uma ficha de treino quando o banco/tabelas foram criados com outro conjunto de caracteres que não utf8mb4).
--
-- Converte o banco e todas as tabelas para utf8mb4 SEM perder dados. Pode rodar mais de uma vez.
-- Faça uma cópia antes se tiver dados importantes (mysqldump -u root -p vida_ativa > backup.sql).
--
-- Rode assim (o --default-character-set é importante):
--   mysql -u root -p --default-character-set=utf8mb4 vida_ativa < database/migrations/v3-1-utf8mb4.sql
-- No MySQL Workbench: abra este arquivo e execute o script inteiro.
--
-- Para conferir ANTES (opcional): SHOW CREATE TABLE treinos;   <- procure CHARSET=utf8mb4
--                                 SHOW VARIABLES LIKE 'character_set%';

SET NAMES utf8mb4;
USE vida_ativa;

ALTER DATABASE vida_ativa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE usuarios           CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE planos             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE funcionarios       CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE professores        CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE alunos             CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE pagamentos         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE fichas_treino      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE treinos            CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE exercicios         CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE avaliacoes_fisicas CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Se você usa o pagamento online (v3.5), rode também:
-- ALTER TABLE transacoes_pagamento CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
