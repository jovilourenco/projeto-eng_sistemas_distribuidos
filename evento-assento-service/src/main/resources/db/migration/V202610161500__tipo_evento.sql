CREATE TABLE IF NOT EXISTS evento_assento.tipo_evento (
    id_tipo_evento int4 NOT NULL,
    descricao varchar(100) NOT NULL,
    CONSTRAINT tipo_evento_pk PRIMARY KEY (id_tipo_evento)
);

COMMENT ON TABLE evento_assento.tipo_evento IS 'Contém os tipos de eventos disponíveis na plataforma';
COMMENT ON COLUMN evento_assento.tipo_evento.id_tipo_evento IS 'Identificador do Tipo de Evento';
COMMENT ON COLUMN evento_assento.tipo_evento.descricao IS 'Descrição do Tipo de Evento';

INSERT INTO evento_assento.tipo_evento (id_tipo_evento, descricao) VALUES (1, 'PALESTRA');
INSERT INTO evento_assento.tipo_evento (id_tipo_evento, descricao) VALUES (2, 'CURSO');
INSERT INTO evento_assento.tipo_evento (id_tipo_evento, descricao) VALUES (3, 'SEMINÁRIO');
INSERT INTO evento_assento.tipo_evento (id_tipo_evento, descricao) VALUES (4, 'SHOW');