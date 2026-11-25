-- Listagem de assentos por evento e setor (tipo_assento), com filtro opcional de status (issue #55).
CREATE INDEX IF NOT EXISTS assento_evento_tipo_status_idx ON evento_assento.assento (id_evento, tipo_assento, status);
