-- =====================================================================
-- Migração: banco da versão 1  ->  versão 2
-- Use apenas se você já criou o banco com o schema.sql antigo.
-- Pode rodar quantas vezes quiser: o que já existe é ignorado (sem erro),
-- e funciona com o "Safe Updates" ligado do MySQL Workbench.
-- Seus dados são mantidos. Depois rode o seed.sql para ganhar os dados de demonstração.
-- =====================================================================
USE vida_ativa;
SET NAMES utf8mb4;
SET SQL_SAFE_UPDATES = 0;

DROP PROCEDURE IF EXISTS va_migrar_v2;
DELIMITER $$
CREATE PROCEDURE va_migrar_v2()
BEGIN
    -- Hash PBKDF2 é maior que 64 caracteres (senhas antigas continuam valendo
    -- e são convertidas automaticamente no primeiro login).
    ALTER TABLE usuarios MODIFY senha_hash VARCHAR(255) NOT NULL;

    -- "Cliente desde" do aluno
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alunos' AND COLUMN_NAME = 'data_inicio') THEN
        ALTER TABLE alunos ADD COLUMN data_inicio DATE NULL AFTER data_nascimento;
    END IF;
    UPDATE alunos SET data_inicio = CURDATE() WHERE data_inicio IS NULL;

    -- Mês de referência da mensalidade (AAAA-MM)
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pagamentos' AND COLUMN_NAME = 'referencia') THEN
        ALTER TABLE pagamentos ADD COLUMN referencia VARCHAR(7) NULL AFTER vencimento;
    END IF;
    UPDATE pagamentos
       SET referencia = DATE_FORMAT(COALESCE(vencimento, data_pagamento, CURDATE()), '%Y-%m')
     WHERE referencia IS NULL;

    -- Uma cobrança por aluno por mês.
    -- Se der "Duplicate entry", há dois pagamentos do mesmo aluno no mesmo mês:
    -- ajuste a coluna referencia de um deles e rode de novo.
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pagamentos' AND INDEX_NAME = 'uq_pag_aluno_ref') THEN
        ALTER TABLE pagamentos ADD UNIQUE KEY uq_pag_aluno_ref (aluno_id, referencia);
    END IF;

    -- Dia da semana de cada treino da ficha
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'treinos' AND COLUMN_NAME = 'dia_semana') THEN
        ALTER TABLE treinos ADD COLUMN dia_semana VARCHAR(20) NULL AFTER nome;
    END IF;

    -- Índices de consulta
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'fichas_treino' AND INDEX_NAME = 'idx_ficha_aluno') THEN
        ALTER TABLE fichas_treino ADD INDEX idx_ficha_aluno (aluno_id, status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'avaliacoes_fisicas' AND INDEX_NAME = 'idx_av_aluno') THEN
        ALTER TABLE avaliacoes_fisicas ADD INDEX idx_av_aluno (aluno_id, data_avaliacao);
    END IF;
END$$
DELIMITER ;

CALL va_migrar_v2();
DROP PROCEDURE va_migrar_v2;
SET SQL_SAFE_UPDATES = 1;

-- Conferência: deve devolver 0 nas duas linhas.
SELECT 'alunos sem data_inicio' AS verificacao, COUNT(*) AS qtd FROM alunos WHERE data_inicio IS NULL
UNION ALL
SELECT 'pagamentos sem referencia', COUNT(*) FROM pagamentos WHERE referencia IS NULL;
