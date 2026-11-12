package br.engsistemasdistribuidos.reserva.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.engsistemasdistribuidos.reserva.model.StatusIngresso;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

public record IngressoResponse(
        UUID id,
        Integer idEvento,
        Integer idAssento,
        Integer idUsuario,
        TipoIngresso tipoIngresso,
        StatusIngresso status,
        LocalDateTime dataReserva,
        LocalDateTime dataExpiracao) {
}
