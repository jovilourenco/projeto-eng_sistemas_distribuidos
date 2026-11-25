package br.engsistemasdistribuidos.eventoassento.dto.response;

import java.math.BigDecimal;

import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;

// Versão enxuta para listagens grandes (um setor pode ter milhares de assentos): sem repetir o Evento em cada linha.
public record AssentoResumoResponse(int id, String tipoAssento, BigDecimal valor, StatusAssento status, boolean ativo) {
}
