-- =====================================================================
-- Dados de demonstração — pode rodar mais de uma vez sem duplicar.
-- Senha de TODOS os usuários abaixo: 123456   (troque antes de usar de verdade!)
--   gerente / funcionario / professor  -> entram no SOFTWARE
--   pablo (aluno)                      -> entra no SITE
-- =====================================================================
USE vida_ativa;
SET NAMES utf8mb4;

-- hash PBKDF2 (com salt) da senha 123456
SET @senha := 'pbkdf2$120000$bVfzWX/sFvm1HfpeGf7Nlg==$WK+ITfb3bP00cYF9Ua7rkvj3RglDWZvmD5tuX6oBnxc=';

INSERT INTO usuarios(nome,email,username,senha_hash,perfil,ativo) VALUES
('Gerente Demonstração','gerente@vidaativa.local','gerente',@senha,'GERENTE',1),
('Funcionário Demonstração','funcionario@vidaativa.local','funcionario',@senha,'FUNCIONARIO',1),
('Professor Demonstração','professor@vidaativa.local','professor',@senha,'PROFESSOR',1),
('Pablo Henrique','pablo@email.com','pablo',@senha,'ALUNO',1)
ON DUPLICATE KEY UPDATE nome=VALUES(nome);

SET @gid := (SELECT id FROM usuarios WHERE username='gerente');
SET @fid := (SELECT id FROM usuarios WHERE username='funcionario');
SET @pid := (SELECT id FROM usuarios WHERE username='professor');
SET @aid := (SELECT id FROM usuarios WHERE username='pablo');

INSERT INTO funcionarios(usuario_id,cargo,ativo) VALUES
(@gid,'Gerente da Academia',1),
(@fid,'Recepcionista',1)
ON DUPLICATE KEY UPDATE cargo=VALUES(cargo);

INSERT INTO professores(usuario_id,especialidade,ativo) VALUES
(@pid,'Musculação',1)
ON DUPLICATE KEY UPDATE especialidade=VALUES(especialidade);

-- Aluno de demonstração (plano Plus, paga por Pix)
SET @plus := (SELECT id FROM planos WHERE nome='Plus');
INSERT INTO alunos(usuario_id,documento,telefone,data_nascimento,data_inicio,plano_id,tipo_cliente,forma_pagamento,vencimento,observacoes,ativo)
VALUES(@aid,'529.982.247-25','(11) 99999-9999','2000-05-10',DATE_SUB(CURDATE(), INTERVAL 3 MONTH),@plus,'CONVENCIONAL','PIX',DATE_ADD(CURDATE(), INTERVAL 10 DAY),'Aluno de demonstração.',1)
ON DUPLICATE KEY UPDATE telefone=VALUES(telefone), documento=VALUES(documento);

SET @aluno := (SELECT id FROM alunos WHERE usuario_id=@aid);
SET @prof  := (SELECT id FROM professores WHERE usuario_id=@pid);

-- Mensalidades: duas pagas e a do mês em aberto
INSERT INTO pagamentos(aluno_id,data_pagamento,valor,forma_pagamento,status,vencimento,referencia) VALUES
(@aluno, DATE_SUB(CURDATE(), INTERVAL 50 DAY), 129.90, 'PIX', 'PAGO',     DATE_SUB(CURDATE(), INTERVAL 50 DAY), DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH),'%Y-%m')),
(@aluno, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 129.90, 'PIX', 'PAGO',     DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH),'%Y-%m')),
(@aluno, NULL,                                 129.90, 'PIX', 'PENDENTE', DATE_ADD(CURDATE(), INTERVAL 10 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'))
ON DUPLICATE KEY UPDATE valor=VALUES(valor);

-- Ficha de treino ATIVA com dois treinos
INSERT INTO fichas_treino(aluno_id,professor_id,nome,objetivo,frequencia_semanal,data_inicio,data_revisao,observacoes,status)
SELECT @aluno,@prof,'Hipertrofia A/B','Hipertrofia',4,DATE_SUB(CURDATE(), INTERVAL 20 DAY),DATE_ADD(CURDATE(), INTERVAL 40 DAY),'Ficha de demonstração. Hidrate-se bem durante o treino.','ATIVA'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM fichas_treino WHERE aluno_id=@aluno AND nome='Hipertrofia A/B');
SET @ficha := (SELECT id FROM fichas_treino WHERE aluno_id=@aluno AND nome='Hipertrofia A/B' LIMIT 1);

INSERT INTO treinos(ficha_id,nome,dia_semana)
SELECT @ficha,'Treino A — Peito e tríceps','Segunda-feira' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM treinos WHERE ficha_id=@ficha AND nome='Treino A — Peito e tríceps');
INSERT INTO treinos(ficha_id,nome,dia_semana)
SELECT @ficha,'Treino B — Costas e bíceps','Quarta-feira' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM treinos WHERE ficha_id=@ficha AND nome='Treino B — Costas e bíceps');
SET @ta := (SELECT id FROM treinos WHERE ficha_id=@ficha AND nome='Treino A — Peito e tríceps' LIMIT 1);
SET @tb := (SELECT id FROM treinos WHERE ficha_id=@ficha AND nome='Treino B — Costas e bíceps' LIMIT 1);

INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @ta,'Supino reto','Peito',4,'8-10','60 kg','90 s','Cadência controlada' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@ta AND nome='Supino reto');
INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @ta,'Supino inclinado','Peito',3,'10-12','22 kg','75 s','Com halteres' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@ta AND nome='Supino inclinado');
INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @ta,'Tríceps corda','Tríceps',3,'12-15','25 kg','60 s','Sem roubar no movimento' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@ta AND nome='Tríceps corda');
INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @tb,'Puxada frontal','Costas',4,'10','50 kg','90 s','Amplitude completa' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@tb AND nome='Puxada frontal');
INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @tb,'Remada baixa','Costas',3,'10-12','45 kg','75 s',NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@tb AND nome='Remada baixa');
INSERT INTO exercicios(treino_id,nome,grupo_muscular,series,repeticoes,carga,descanso,observacoes)
SELECT @tb,'Rosca direta','Bíceps',3,'10-12','20 kg','60 s',NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM exercicios WHERE treino_id=@tb AND nome='Rosca direta');

-- Uma avaliação física
INSERT INTO avaliacoes_fisicas(aluno_id,professor_id,data_avaliacao,peso,altura,percentual_gordura,observacoes)
SELECT @aluno,@prof,DATE_SUB(CURDATE(), INTERVAL 15 DAY),78.50,1.78,18.40,'Avaliação inicial de demonstração.' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM avaliacoes_fisicas WHERE aluno_id=@aluno AND observacoes='Avaliação inicial de demonstração.');
