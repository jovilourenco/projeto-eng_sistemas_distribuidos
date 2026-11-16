CREATE TABLE IF NOT EXISTS reserva.chave_idempotencia (
    id uuid NOT NULL,
    id_ingresso uuid NOT NULL,
    operacao varchar(30) NOT NULL,
    chave varchar(255) NOT NULL,
    data_criacao timestamp NOT NULL,
    CONSTRAINT chave_idempotencia_pk PRIMARY KEY (id),
    CONSTRAINT chave_idempotencia_ingresso_fk FOREIGN KEY (id_ingresso) REFERENCES reserva.ingresso (id_ingresso),
    CONSTRAINT chave_idempotencia_operacao_ck CHECK (operacao IN ('CANCELAMENTO')),
    CONSTRAINT chave_idempotencia_uk UNIQUE (id_ingresso, operacao, chave)
);

-- Dicionário de Dados
COMMENT ON TABLE reserva.chave_idempotencia IS 'Chaves de idempotência das operações sobre uma compra existente (ADR-002). A criação usa o próprio id do ingresso';
COMMENT ON COLUMN reserva.chave_idempotencia.id IS 'Identificador do registro';
COMMENT ON COLUMN reserva.chave_idempotencia.id_ingresso IS 'Ingresso (compra) sobre o qual a operação foi aplicada';
COMMENT ON COLUMN reserva.chave_idempotencia.operacao IS 'Operação protegida pela chave: CANCELAMENTO (futuramente PAGAMENTO)';
COMMENT ON COLUMN reserva.chave_idempotencia.chave IS 'Valor do cabeçalho Idempotency-Key enviado pelo cliente';
COMMENT ON COLUMN reserva.chave_idempotencia.data_criacao IS 'Quando a operação foi aplicada';
