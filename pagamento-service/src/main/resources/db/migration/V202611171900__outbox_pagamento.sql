CREATE TABLE IF NOT EXISTS pagamento.outbox_event (
    id uuid NOT NULL,
    agregado_tipo varchar(100) NOT NULL,
    agregado_id varchar(100) NOT NULL,
    tipo_evento varchar(150) NOT NULL,
    payload text NOT NULL,
    processado bool NOT NULL DEFAULT FALSE,
    data_criacao timestamp NOT NULL,
    CONSTRAINT outbox_event_pagamento_pk PRIMARY KEY (id)
);

COMMENT ON TABLE pagamento.outbox_event IS 'Tabela Transactional Outbox do serviço de pagamentos para publicação confiável de eventos da SAGA';
COMMENT ON COLUMN pagamento.outbox_event.id IS 'Identificador único do evento (UUID)';
COMMENT ON COLUMN pagamento.outbox_event.agregado_tipo IS 'Tipo da entidade agregada (PAGAMENTO)';
COMMENT ON COLUMN pagamento.outbox_event.agregado_id IS 'ID do pagamento relacionado';
COMMENT ON COLUMN pagamento.outbox_event.tipo_evento IS 'Tipo do evento (PAGAMENTO_APROVADO ou PAGAMENTO_FALHOU)';
COMMENT ON COLUMN pagamento.outbox_event.payload IS 'JSON contendo idPagamento, idIngresso e status';
COMMENT ON COLUMN pagamento.outbox_event.processado IS 'Indica se já foi publicado no RabbitMQ';
COMMENT ON COLUMN pagamento.outbox_event.data_criacao IS 'Data e hora de criação do registro';