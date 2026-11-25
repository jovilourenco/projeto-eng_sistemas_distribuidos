CREATE SCHEMA IF NOT EXISTS pagamento;

CREATE TABLE IF NOT EXISTS pagamento.pagamento (
    id uuid NOT NULL,
    id_compra uuid NOT NULL,
    tentativa int4 NOT NULL,
    valor numeric(10, 2) NOT NULL,
    status varchar(50) NOT NULL,
    metodo_pagamento varchar(50) NOT NULL,
    simulacao varchar(50) NOT NULL,
    chave_idempotencia varchar(255) NOT NULL,
    data_criacao timestamp NOT NULL,
    CONSTRAINT pagamento_pk PRIMARY KEY (id),
    CONSTRAINT uk_chave_idempotencia UNIQUE (chave_idempotencia)
);

COMMENT ON TABLE pagamento.pagamento IS 'Tabela responsável por registrar os pagamentos simulados com garantia de idempotência';
COMMENT ON COLUMN pagamento.pagamento.id IS 'Identificador único do pagamento (UUID)';
COMMENT ON COLUMN pagamento.pagamento.id_compra IS 'Identificador da compra/reserva associada (UUID)';
COMMENT ON COLUMN pagamento.pagamento.tentativa IS 'Número da tentativa de pagamento para a compra';
COMMENT ON COLUMN pagamento.pagamento.valor IS 'Valor total da compra em reais';
COMMENT ON COLUMN pagamento.pagamento.status IS 'Resultado do pagamento (APROVADO ou RECUSADO)';
COMMENT ON COLUMN pagamento.pagamento.metodo_pagamento IS 'Método utilizado (PIX, CARTAO_CREDITO, BOLETO)';
COMMENT ON COLUMN pagamento.pagamento.simulacao IS 'Comportamento solicitado ao simulador (APROVAR, RECUSAR, ATRASAR)';
COMMENT ON COLUMN pagamento.pagamento.chave_idempotencia IS 'Chave única composta por idCompra:tentativa';
COMMENT ON COLUMN pagamento.pagamento.data_criacao IS 'Data e hora de registro do pagamento';