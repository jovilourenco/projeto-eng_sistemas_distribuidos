CREATE TABLE IF NOT EXISTS reserva.ingresso (
    id_ingresso uuid NOT NULL,
    id_evento int4 NOT NULL,
    id_assento int4 NOT NULL,
    id_usuario int4 NOT NULL,
    tipo_ingresso varchar(20) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'PENDENTE_PAGAMENTO',
    data_reserva timestamp NOT NULL,
    data_expiracao timestamp NOT NULL,
    hash_requisicao varchar(64) NOT NULL,
    versao int4 NOT NULL DEFAULT 0,
    CONSTRAINT ingresso_pk PRIMARY KEY (id_ingresso),
    CONSTRAINT ingresso_status_ck CHECK (status IN ('PENDENTE_PAGAMENTO', 'PAGO', 'CANCELADO')),
    CONSTRAINT ingresso_tipo_ck CHECK (tipo_ingresso IN ('INTEIRA', 'ESTUDANTE')),
    CONSTRAINT ingresso_expiracao_ck CHECK (data_expiracao > data_reserva)
);

CREATE INDEX IF NOT EXISTS ingresso_usuario_idx ON reserva.ingresso (id_usuario);

-- Dicionário de Dados
COMMENT ON TABLE reserva.ingresso IS 'Contém os ingressos (reservas) dos usuários. Cada ingresso corresponde a uma compra da SAGA';
COMMENT ON COLUMN reserva.ingresso.id_ingresso IS 'Identificador do Ingresso e da compra na SAGA. UUID derivado do usuário e da Idempotency-Key (ADR-002)';
COMMENT ON COLUMN reserva.ingresso.id_evento IS 'Referência lógica ao Evento (Serviço de Eventos e Assentos)';
COMMENT ON COLUMN reserva.ingresso.id_assento IS 'Referência lógica ao Assento (Serviço de Eventos e Assentos)';
COMMENT ON COLUMN reserva.ingresso.id_usuario IS 'Referência lógica ao Usuário dono do Ingresso (extraído do token)';
COMMENT ON COLUMN reserva.ingresso.tipo_ingresso IS 'Tipo do Ingresso: INTEIRA ou ESTUDANTE';
COMMENT ON COLUMN reserva.ingresso.status IS 'Situação do Ingresso: PENDENTE_PAGAMENTO, PAGO ou CANCELADO';
COMMENT ON COLUMN reserva.ingresso.data_reserva IS 'Data e hora em que a reserva foi solicitada';
COMMENT ON COLUMN reserva.ingresso.data_expiracao IS 'Prazo para pagamento; depois dele a reserva deve ser cancelada';
COMMENT ON COLUMN reserva.ingresso.hash_requisicao IS 'Resumo SHA-256 do corpo da requisição de criação, para detectar a mesma chave com corpo diferente';
COMMENT ON COLUMN reserva.ingresso.versao IS 'Versão do registro para controle de concorrência otimista';
