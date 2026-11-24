-- Compra de 1 a 4 assentos, tudo ou nada (ADR-004).
-- A compra (estado da SAGA, prazo, chave) passa para a tabela reserva; cada assento vira um ingresso dela.

CREATE TABLE IF NOT EXISTS reserva.reserva (
    id_reserva uuid NOT NULL,
    id_evento int4 NOT NULL,
    id_usuario int4 NOT NULL,
    estado varchar(30) NOT NULL DEFAULT 'INICIADA',
    motivo_cancelamento varchar(30) NULL,
    valor_total numeric(10, 2) NULL,
    chave_idempotencia varchar(255) NOT NULL,
    hash_requisicao varchar(64) NOT NULL,
    data_reserva timestamp NOT NULL,
    data_expiracao timestamp NOT NULL,
    chave_pagamento varchar(255) NULL,
    hash_pagamento varchar(64) NULL,
    metodo_pagamento varchar(20) NULL,
    simulacao_pagamento varchar(20) NULL,
    data_pedido_pagamento timestamp NULL,
    versao int4 NOT NULL DEFAULT 0,
    CONSTRAINT reserva_pk PRIMARY KEY (id_reserva),
    CONSTRAINT reserva_estado_ck CHECK (estado IN ('INICIADA', 'AGUARDANDO_PAGAMENTO', 'PROCESSANDO_PAGAMENTO',
        'CONFIRMANDO', 'CONCLUIDA', 'COMPENSANDO', 'CANCELADA')),
    CONSTRAINT reserva_motivo_ck CHECK (motivo_cancelamento IN ('CANCELADA_PELO_USUARIO', 'PRAZO_VENCIDO',
        'PAGAMENTO_RECUSADO', 'ASSENTO_INDISPONIVEL')),
    CONSTRAINT reserva_metodo_ck CHECK (metodo_pagamento IN ('PIX', 'CARTAO_CREDITO', 'BOLETO')),
    CONSTRAINT reserva_simulacao_ck CHECK (simulacao_pagamento IN ('APROVAR', 'RECUSAR', 'ATRASAR')),
    -- O pedido de pagamento é gravado por inteiro ou não existe.
    CONSTRAINT reserva_pagamento_ck CHECK (
        (chave_pagamento IS NULL AND hash_pagamento IS NULL AND metodo_pagamento IS NULL
            AND data_pedido_pagamento IS NULL)
        OR (chave_pagamento IS NOT NULL AND hash_pagamento IS NOT NULL AND metodo_pagamento IS NOT NULL
            AND data_pedido_pagamento IS NOT NULL)),
    CONSTRAINT reserva_motivo_estado_ck CHECK (motivo_cancelamento IS NULL OR estado IN ('COMPENSANDO', 'CANCELADA')),
    CONSTRAINT reserva_expiracao_ck CHECK (data_expiracao > data_reserva)
);

CREATE INDEX IF NOT EXISTS reserva_usuario_idx ON reserva.reserva (id_usuario);

-- Cada ingresso já existente vira uma reserva de um assento, com o mesmo id (o id do ingresso era o id da compra).
-- O resumo da requisição mudou de formato (lista de assentos), então repetir uma compra antiga com a mesma chave
-- responde 422. Só há dados de desenvolvimento nesta tabela, e isso foi aceito.
INSERT INTO reserva.reserva (id_reserva, id_evento, id_usuario, estado, motivo_cancelamento, chave_idempotencia,
                             hash_requisicao, data_reserva, data_expiracao, versao)
SELECT id_ingresso, id_evento, id_usuario,
       CASE status WHEN 'PAGO' THEN 'CONCLUIDA' WHEN 'CANCELADO' THEN 'CANCELADA' ELSE 'INICIADA' END,
       CASE status WHEN 'CANCELADO' THEN 'CANCELADA_PELO_USUARIO' END,
       'migrado:' || id_ingresso, hash_requisicao, data_reserva, data_expiracao, versao
  FROM reserva.ingresso;

ALTER TABLE reserva.ingresso ADD COLUMN id_reserva uuid NULL;
ALTER TABLE reserva.ingresso ADD COLUMN valor numeric(10, 2) NULL;
UPDATE reserva.ingresso SET id_reserva = id_ingresso;
ALTER TABLE reserva.ingresso ALTER COLUMN id_reserva SET NOT NULL;
ALTER TABLE reserva.ingresso
    ADD CONSTRAINT ingresso_reserva_fk FOREIGN KEY (id_reserva) REFERENCES reserva.reserva (id_reserva),
    ADD CONSTRAINT ingresso_reserva_assento_uk UNIQUE (id_reserva, id_assento);

-- Esses dados agora são da compra (tabela reserva).
ALTER TABLE reserva.ingresso
    DROP COLUMN id_evento,
    DROP COLUMN id_usuario,
    DROP COLUMN status,
    DROP COLUMN data_reserva,
    DROP COLUMN data_expiracao,
    DROP COLUMN hash_requisicao,
    DROP COLUMN versao;

-- As chaves de idempotência passam a ser da compra.
ALTER TABLE reserva.chave_idempotencia DROP CONSTRAINT chave_idempotencia_ingresso_fk;
ALTER TABLE reserva.chave_idempotencia DROP CONSTRAINT chave_idempotencia_uk;
ALTER TABLE reserva.chave_idempotencia RENAME COLUMN id_ingresso TO id_reserva;
ALTER TABLE reserva.chave_idempotencia
    ADD CONSTRAINT chave_idempotencia_reserva_fk FOREIGN KEY (id_reserva) REFERENCES reserva.reserva (id_reserva),
    ADD CONSTRAINT chave_idempotencia_uk UNIQUE (id_reserva, operacao, chave);

-- Eventos do formato antigo (um assento, agregado INGRESSO) que ainda não foram publicados não seriam entendidos
-- pelo Serviço de Eventos e Assentos, que agora recebe comandos com lista de assentos. Ficam marcados como processados.
UPDATE reserva.outbox_event SET processado = true WHERE agregado_tipo = 'INGRESSO' AND processado = false;

-- Dicionário de Dados
COMMENT ON TABLE reserva.reserva IS 'Compras de 1 a 4 assentos. Cada reserva é uma SAGA de compra coordenada por este serviço (ADR-002, ADR-004)';
COMMENT ON COLUMN reserva.reserva.id_reserva IS 'Identificador da compra na SAGA. UUID derivado do usuário e da Idempotency-Key (ADR-002)';
COMMENT ON COLUMN reserva.reserva.id_evento IS 'Referência lógica ao Evento (Serviço de Eventos e Assentos)';
COMMENT ON COLUMN reserva.reserva.id_usuario IS 'Referência lógica ao Usuário dono da compra';
COMMENT ON COLUMN reserva.reserva.estado IS 'Estado da SAGA (ADR-002): INICIADA, AGUARDANDO_PAGAMENTO, PROCESSANDO_PAGAMENTO, CONFIRMANDO, CONCLUIDA, COMPENSANDO ou CANCELADA';
COMMENT ON COLUMN reserva.reserva.motivo_cancelamento IS 'Por que a compra foi para COMPENSANDO/CANCELADA; nulo nos demais estados';
COMMENT ON COLUMN reserva.reserva.valor_total IS 'Soma dos valores dos ingressos, informados pelo Serviço de Eventos e Assentos na reserva; nulo até lá';
COMMENT ON COLUMN reserva.reserva.chave_idempotencia IS 'Idempotency-Key enviada na criação da compra';
COMMENT ON COLUMN reserva.reserva.hash_requisicao IS 'Resumo SHA-256 do corpo da criação, para detectar a mesma chave com corpo diferente';
COMMENT ON COLUMN reserva.reserva.data_reserva IS 'Data e hora em que a compra foi criada';
COMMENT ON COLUMN reserva.reserva.data_expiracao IS 'Prazo para pagamento; depois dele a compra é cancelada e os assentos liberados';
COMMENT ON COLUMN reserva.reserva.chave_pagamento IS 'Idempotency-Key do pedido de pagamento em aberto; nula se não houver pedido';
COMMENT ON COLUMN reserva.reserva.hash_pagamento IS 'Resumo SHA-256 do corpo do pedido de pagamento';
COMMENT ON COLUMN reserva.reserva.metodo_pagamento IS 'Método do pedido de pagamento: PIX, CARTAO_CREDITO ou BOLETO';
COMMENT ON COLUMN reserva.reserva.simulacao_pagamento IS 'Comportamento pedido ao provedor simulado: APROVAR, RECUSAR ou ATRASAR; nulo se não informado';
COMMENT ON COLUMN reserva.reserva.data_pedido_pagamento IS 'Quando o pagamento foi solicitado';
COMMENT ON COLUMN reserva.reserva.versao IS 'Versão do registro para controle de concorrência otimista';
COMMENT ON TABLE reserva.ingresso IS 'Um ingresso por assento de uma reserva';
COMMENT ON COLUMN reserva.ingresso.id_ingresso IS 'Identificador do Ingresso, derivado da reserva e do assento';
COMMENT ON COLUMN reserva.ingresso.id_reserva IS 'Reserva (compra) à qual o ingresso pertence';
COMMENT ON COLUMN reserva.ingresso.valor IS 'Valor do assento no momento da reserva; nulo até o Serviço de Eventos e Assentos confirmar a reserva';
COMMENT ON TABLE reserva.chave_idempotencia IS 'Chaves de idempotência do cancelamento (ADR-002). A criação usa o próprio id da reserva, e o pedido de pagamento fica na reserva (chave_pagamento)';
COMMENT ON COLUMN reserva.chave_idempotencia.operacao IS 'Operação protegida pela chave: CANCELAMENTO';
COMMENT ON COLUMN reserva.outbox_event.agregado_tipo IS 'Tipo da entidade do domínio que gerou o evento (Ex: RESERVA)';
COMMENT ON COLUMN reserva.outbox_event.tipo_evento IS 'Routing key da mensagem a publicar (Ex: assento.reservar, assento.liberar)';
COMMENT ON COLUMN reserva.chave_idempotencia.id_reserva IS 'Reserva (compra) sobre a qual a operação foi aplicada';
