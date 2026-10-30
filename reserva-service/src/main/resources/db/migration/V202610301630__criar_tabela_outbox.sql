CREATE SCHEMA IF NOT EXISTS reserva;

CREATE TABLE IF NOT EXISTS reserva.outbox_event (
    id uuid NOT NULL,
    agregado_tipo varchar(100) NOT NULL,
    agregado_id varchar(100) NOT NULL,
    tipo_evento varchar(150) NOT NULL,
    payload text NOT NULL,
    processado bool NOT NULL DEFAULT FALSE,
    data_criacao timestamp NOT NULL,
    CONSTRAINT outbox_event_pk PRIMARY KEY (id)
);

GRANT SELECT ON reserva.outbox_event TO public;
GRANT INSERT, SELECT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON reserva.outbox_event TO postgres;

COMMENT ON TABLE reserva.outbox_event IS 'Tabela responsável pelo padrão Transactional Outbox para garantir o envio confiável de eventos ao RabbitMQ';
COMMENT ON COLUMN reserva.outbox_event.id IS 'Identificador único do evento (UUID)';
COMMENT ON COLUMN reserva.outbox_event.agregado_tipo IS 'Tipo da entidade do domínio que gerou o evento (Ex: INGRESSO)';
COMMENT ON COLUMN reserva.outbox_event.agregado_id IS 'Identificador (ID) da entidade agregada';
COMMENT ON COLUMN reserva.outbox_event.tipo_evento IS 'Nome do evento de domínio que será publicado (Ex: RESERVA_CRIADA)';
COMMENT ON COLUMN reserva.outbox_event.payload IS 'O corpo da mensagem em formato JSON que será enviado para a fila';
COMMENT ON COLUMN reserva.outbox_event.processado IS 'Flag que indica se o evento já foi lido pelo scheduler e publicado com sucesso no broker (false = pendente, true = enviado)';
COMMENT ON COLUMN reserva.outbox_event.data_criacao IS 'Data e hora exata em que o evento foi registrado junto com a transação principal';