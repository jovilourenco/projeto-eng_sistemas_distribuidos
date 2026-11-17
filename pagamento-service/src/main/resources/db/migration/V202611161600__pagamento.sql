-- Garante a criação do schema de pagamentos
CREATE SCHEMA IF NOT EXISTS pagamento;

-- Criação da tabela de pagamentos simulados com suporte a Idempotência
CREATE TABLE IF NOT EXISTS pagamento.pagamento (
    id uuid NOT NULL,
    id_ingresso int8 NOT NULL,
    valor numeric(10, 2) NOT NULL,
    status varchar(50) NOT NULL, -- PENDENTE, APROVADO, REJEITADO
    metodo_pagamento varchar(50) NOT NULL, -- PIX, CARTAO_CREDITO, BOLETO
    chave_idempotencia varchar(255) NOT NULL,
    data_criacao timestamp NOT NULL,
    CONSTRAINT pagamento_pk PRIMARY KEY (id),
    CONSTRAINT uk_chave_idempotencia UNIQUE (chave_idempotencia)
);

COMMENT ON TABLE pagamento.pagamento IS 'Tabela responsável por registrar os pagamentos simulados com garantia de idempotência';
COMMENT ON COLUMN pagamento.pagamento.id IS 'Identificador único do pagamento (UUID)';
COMMENT ON COLUMN pagamento.pagamento.id_ingresso IS 'Identificador do ingresso associado a este pagamento';
COMMENT ON COLUMN pagamento.pagamento.valor IS 'Valor monetário da transação';
COMMENT ON COLUMN pagamento.pagamento.status IS 'Estado atual da transação (PENDENTE, APROVADO, REJEITADO)';
COMMENT ON COLUMN pagamento.pagamento.metodo_pagamento IS 'Método utilizado para realizar o pagamento (PIX, CARTAO_CREDITO, BOLETO)';
COMMENT ON COLUMN pagamento.pagamento.chave_idempotencia IS 'Chave única enviada pelo cliente para evitar cobranças duplicadas em tentativas repetidas';
COMMENT ON COLUMN pagamento.pagamento.data_criacao IS 'Data e hora em que a solicitação de pagamento foi registrada';