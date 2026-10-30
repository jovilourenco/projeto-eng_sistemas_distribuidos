package br.engsistemasdistribuidos.eventoassento.dto.response;

import java.math.BigDecimal;

import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;

public record AssentoResponse(
        int id,
        EventoResponse evento,
        String tipoAssento,
        BigDecimal valor,
        StatusAssento status,
        boolean ativo) {
}
