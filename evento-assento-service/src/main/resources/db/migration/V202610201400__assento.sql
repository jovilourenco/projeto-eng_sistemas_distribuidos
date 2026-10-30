CREATE TABLE IF NOT EXISTS evento_assento.assento (
    id_assento serial4 NOT NULL,
    id_evento int4 NOT NULL,
    tipo_assento varchar(50) NOT NULL,
    valor numeric(10,2) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DISPONIVEL',
    ativo boolean NOT NULL DEFAULT true,
    CONSTRAINT assento_pk PRIMARY KEY (id_assento),
    CONSTRAINT assento_evento_fk FOREIGN KEY (id_evento) REFERENCES evento_assento.evento (id_evento),
    CONSTRAINT assento_status_ck CHECK (status IN ('DISPONIVEL', 'RESERVADO', 'VENDIDO')),
    CONSTRAINT assento_valor_ck CHECK (valor >= 0)
);

CREATE INDEX IF NOT EXISTS assento_evento_idx ON evento_assento.assento (id_evento);

-- Dicionário de Dados
COMMENT ON TABLE evento_assento.assento IS 'Contém os assentos cadastrados para cada Evento';
COMMENT ON COLUMN evento_assento.assento.id_assento IS 'Identificador do Assento';
COMMENT ON COLUMN evento_assento.assento.id_evento IS 'Identificador do Evento ao qual o Assento pertence';
COMMENT ON COLUMN evento_assento.assento.tipo_assento IS 'Tipo ou categoria do Assento (ex: VIP, PISTA)';
COMMENT ON COLUMN evento_assento.assento.valor IS 'Valor base do Assento';
COMMENT ON COLUMN evento_assento.assento.status IS 'Situação do Assento: DISPONIVEL, RESERVADO ou VENDIDO';
COMMENT ON COLUMN evento_assento.assento.ativo IS 'Indica se o Assento está ativo (false = inativado)';
