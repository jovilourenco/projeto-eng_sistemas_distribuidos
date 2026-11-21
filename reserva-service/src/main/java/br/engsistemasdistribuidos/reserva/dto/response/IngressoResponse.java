package br.engsistemasdistribuidos.reserva.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.StatusIngresso;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

// Evento, usuário, status e datas vêm da reserva à qual o ingresso pertence.
public record IngressoResponse(
        UUID id,
        UUID idReserva,
        Integer idEvento,
        Integer idAssento,
        Integer idUsuario,
        TipoIngresso tipoIngresso,
        BigDecimal valor,
        StatusIngresso status,
        LocalDateTime dataReserva,
        LocalDateTime dataExpiracao) {

    public static IngressoResponse de(Ingresso ingresso) {
        Reserva reserva = ingresso.getReserva();
        return new IngressoResponse(ingresso.getId(), reserva.getId(), reserva.getIdEvento(), ingresso.getIdAssento(),
                reserva.getIdUsuario(), ingresso.getTipoIngresso(), ingresso.getValor(), ingresso.getStatus(),
                reserva.getDataReserva(), reserva.getDataExpiracao());
    }

    public static List<IngressoResponse> de(List<Ingresso> ingressos) {
        return ingressos.stream().map(IngressoResponse::de).toList();
    }
}
