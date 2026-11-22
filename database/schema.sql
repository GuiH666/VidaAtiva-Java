-- =====================================================================
-- Vida Ativa — banco de dados central (versão 2)
-- Usado pelo software (Java Swing) e pela API que alimenta o site do aluno.
--
-- Primeira instalação:  execute este arquivo e depois o seed.sql
-- Já tem o banco da versão 1?  NÃO rode este arquivo: rode migrations/v1-para-v2.sql
-- =====================================================================
CREATE DATABASE IF NOT EXISTS vida_ativa CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vida_ativa;
SET NAMES utf8mb4;

-- Login de TODOS os perfis (gerente, funcionário, professor e aluno).
-- senha_hash guarda PBKDF2 com salt (ex.: pbkdf2$120000$salt$hash) — nunca a senha em texto.
CREATE TABLE IF NOT EXISTS usuarios (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    username VARCHAR(60) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    perfil ENUM('GERENTE','FUNCIONARIO','PROFESSOR','ALUNO') NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS planos (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nome VARCHAR(50) NOT NULL UNIQUE,
    valor_mensal DECIMAL(10,2) NOT NULL,
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Gerente também tem uma linha aqui (cargo "Gerente da Academia").
CREATE TABLE IF NOT EXISTS funcionarios (
    id INT PRIMARY KEY AUTO_INCREMENT,
    usuario_id INT NOT NULL UNIQUE,
    cargo VARCHAR(100) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_func_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE IF NOT EXISTS professores (
    id INT PRIMARY KEY AUTO_INCREMENT,
    usuario_id INT NOT NULL UNIQUE,
    especialidade VARCHAR(120),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_prof_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- data_inicio = "cliente desde"; vencimento = data do próximo vencimento da mensalidade.
CREATE TABLE IF NOT EXISTS alunos (
    id INT PRIMARY KEY AUTO_INCREMENT,
    usuario_id INT NOT NULL UNIQUE,
    documento VARCHAR(30) NOT NULL UNIQUE,
    telefone VARCHAR(30),
    data_nascimento DATE,
    data_inicio DATE,
    plano_id INT NOT NULL,
    tipo_cliente ENUM('CONVENCIONAL','TOTALPASS') NOT NULL DEFAULT 'CONVENCIONAL',
    forma_pagamento ENUM('PIX','CARTAO','BOLETO','DINHEIRO') NOT NULL,
    vencimento DATE,
    observacoes TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_aluno_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_aluno_plano FOREIGN KEY (plano_id) REFERENCES planos(id)
);

-- referencia = mês da mensalidade (AAAA-MM). Um aluno só tem uma cobrança por mês.
CREATE TABLE IF NOT EXISTS pagamentos (
    id INT PRIMARY KEY AUTO_INCREMENT,
    aluno_id INT NOT NULL,
    data_pagamento DATE,
    valor DECIMAL(10,2) NOT NULL,
    forma_pagamento VARCHAR(30) NOT NULL,
    status ENUM('PENDENTE','PAGO','ATRASADO','CANCELADO') NOT NULL DEFAULT 'PENDENTE',
    vencimento DATE,
    referencia VARCHAR(7),
    CONSTRAINT fk_pag_aluno FOREIGN KEY (aluno_id) REFERENCES alunos(id),
    UNIQUE KEY uq_pag_aluno_ref (aluno_id, referencia)
);

-- Pagamento online (Pix/cartão pelo site). Veja database/migrations/v3-pagamento-online.sql.
-- Uma linha por TENTATIVA de pagamento online de uma cobrança. O dinheiro só conta quando status = 'APROVADA'
-- (nesse momento a cobrança em "pagamentos" vira PAGO). Estados: CRIADA, AGUARDANDO, APROVADA, RECUSADA, EXPIRADA, FALHOU,
-- APROVADA_SEM_BAIXA (pagou mas a cobrança já estava paga/cancelada: conferir e devolver) e DIVERGENTE (valor diferente).
CREATE TABLE IF NOT EXISTS transacoes_pagamento (
    id INT PRIMARY KEY AUTO_INCREMENT,
    pagamento_id INT NOT NULL,
    aluno_id INT NOT NULL,
    provedor VARCHAR(20) NOT NULL,
    metodo ENUM('PIX','CARTAO') NOT NULL,
    id_externo VARCHAR(80),
    status VARCHAR(24) NOT NULL DEFAULT 'CRIADA',
    valor DECIMAL(10,2) NOT NULL,
    pix_copia_cola TEXT,
    pix_qr_base64 MEDIUMTEXT,
    url_checkout VARCHAR(600),
    expira_em DATETIME,
    criado_em DATETIME NOT NULL,
    atualizado_em DATETIME NOT NULL,
    observacao VARCHAR(300),
    CONSTRAINT fk_trans_pag FOREIGN KEY (pagamento_id) REFERENCES pagamentos(id),
    CONSTRAINT fk_trans_aluno FOREIGN KEY (aluno_id) REFERENCES alunos(id),
    KEY idx_trans_pag (pagamento_id),
    KEY idx_trans_status (status),
    UNIQUE KEY uq_trans_ext (provedor, id_externo)
);

-- O aluno só enxerga no site as fichas com status ATIVA (RASCUNHO fica só para o professor).
CREATE TABLE IF NOT EXISTS fichas_treino (
    id INT PRIMARY KEY AUTO_INCREMENT,
    aluno_id INT NOT NULL,
    professor_id INT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    objetivo VARCHAR(120),
    frequencia_semanal INT,
    data_inicio DATE,
    data_revisao DATE,
    observacoes TEXT,
    status ENUM('RASCUNHO','ATIVA','INATIVA') NOT NULL DEFAULT 'RASCUNHO',
    CONSTRAINT fk_ficha_aluno FOREIGN KEY (aluno_id) REFERENCES alunos(id),
    CONSTRAINT fk_ficha_prof FOREIGN KEY (professor_id) REFERENCES professores(id),
    INDEX idx_ficha_aluno (aluno_id, status)
);

CREATE TABLE IF NOT EXISTS treinos (
    id INT PRIMARY KEY AUTO_INCREMENT,
    ficha_id INT NOT NULL,
    nome VARCHAR(80) NOT NULL,
    dia_semana VARCHAR(20),
    descricao VARCHAR(255),
    CONSTRAINT fk_treino_ficha FOREIGN KEY (ficha_id) REFERENCES fichas_treino(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS exercicios (
    id INT PRIMARY KEY AUTO_INCREMENT,
    treino_id INT NOT NULL,
    nome VARCHAR(120) NOT NULL,
    grupo_muscular VARCHAR(80),
    series INT,
    repeticoes VARCHAR(30),
    carga VARCHAR(30),
    descanso VARCHAR(30),
    observacoes VARCHAR(255),
    CONSTRAINT fk_ex_treino FOREIGN KEY (treino_id) REFERENCES treinos(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS avaliacoes_fisicas (
    id INT PRIMARY KEY AUTO_INCREMENT,
    aluno_id INT NOT NULL,
    professor_id INT NOT NULL,
    data_avaliacao DATE NOT NULL,
    peso DECIMAL(6,2),
    altura DECIMAL(4,2),
    percentual_gordura DECIMAL(5,2),
    observacoes TEXT,
    CONSTRAINT fk_av_aluno FOREIGN KEY (aluno_id) REFERENCES alunos(id),
    CONSTRAINT fk_av_prof FOREIGN KEY (professor_id) REFERENCES professores(id),
    INDEX idx_av_aluno (aluno_id, data_avaliacao)
);

INSERT INTO planos(nome,valor_mensal,descricao) VALUES
('Básico',89.90,'Acesso livre; biometria/QR Code; sem ficha de treino.'),
('Plus',129.90,'Acesso livre; instrutores; ficha de treino personalizada.'),
('Premium',199.90,'Acesso completo; ficha avançada; avaliação física; acompanhamento com professor.')
ON DUPLICATE KEY UPDATE valor_mensal=VALUES(valor_mensal), descricao=VALUES(descricao);
