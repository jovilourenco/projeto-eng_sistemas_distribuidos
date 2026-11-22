-- Colunas usadas pela SAGA de compra (ADR-003): a compra que detém o assento e desde quando ele está reservado.
ALTER TABLE evento_assento.assento ADD COLUMN IF NOT EXISTS id_compra uuid;
ALTER TABLE evento_assento.assento ADD COLUMN IF NOT EXISTS reservado_em timestamptz;

-- Dicionário de Dados
COMMENT ON COLUMN evento_assento.assento.id_compra IS 'Identificador da compra (Serviço de Reservas) que detém o Assento enquanto RESERVADO ou VENDIDO. Referência lógica, sem chave estrangeira';
COMMENT ON COLUMN evento_assento.assento.reservado_em IS 'Data e hora em que o Assento foi reservado pela compra atual';
