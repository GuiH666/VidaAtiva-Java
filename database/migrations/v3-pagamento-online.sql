-- v3.5: pagamento online (Pix e cartão no site do aluno).
-- Opcional: o software cria esta tabela sozinho na primeira vez que o pagamento online é ligado
-- (pagamento.provedor diferente de "desligado" no config.properties), se o usuário do banco tiver permissão.
-- Rode este arquivo à mão se preferir, ou se o usuário do banco não puder criar tabelas.
USE vida_ativa;

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
