CREATE SCHEMA IF NOT EXISTS evento_assento;

CREATE TABLE IF NOT EXISTS evento_assento.evento (
    id_evento serial4 NOT NULL,
    titulo varchar(150) NOT NULL,
    descricao varchar(500) NOT NULL,
    data_evento timestamp NOT NULL,
    data_cadastro timestamp,
    id_usuario_cadastro int4,
    data_atualizacao timestamp,
    id_usuario_atualizacao int4,
    CONSTRAINT evento_pk PRIMARY KEY (id_evento)
);

-- Dicionário de Dados
COMMENT ON TABLE evento_assento.evento IS 'Contém os eventos cadastrados na plataforma TicketFlow';
COMMENT ON COLUMN evento_assento.evento.id_evento IS 'Identificador do Evento';
COMMENT ON COLUMN evento_assento.evento.titulo IS 'Título do Evento';
COMMENT ON COLUMN evento_assento.evento.descricao IS 'Descrição detalhada do Evento';
COMMENT ON COLUMN evento_assento.evento.data_evento IS 'A data e hora em que o Evento ocorrerá';
COMMENT ON COLUMN evento_assento.evento.data_cadastro IS 'A data e hora de cadastro do Evento';
COMMENT ON COLUMN evento_assento.evento.id_usuario_cadastro IS 'Identificador do usuário (gestor) que realizou o cadastro do Evento';
COMMENT ON COLUMN evento_assento.evento.data_atualizacao IS 'A data e hora da última atualização do Evento';
COMMENT ON COLUMN evento_assento.evento.id_usuario_atualizacao IS 'Identificador do último usuário que atualizou o Evento';