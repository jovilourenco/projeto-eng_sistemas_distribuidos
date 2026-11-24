package br.engsistemasdistribuidos.reserva.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.engsistemasdistribuidos.reserva.model.EstadoReserva;
import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.MotivoCancelamento;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.StatusReserva;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

/**
 * @param status     situação para o cliente (RESERVADA, PAGAMENTO_EM_PROCESSAMENTO, CONFIRMADA, RECUSADA,
 *                   EXPIRADA ou CANCELADA)
 * @param estado     estado interno da SAGA (ADR-002), útil para acompanhar o andamento
 * @param valorTotal soma dos ingressos; nulo até o Serviço de Eventos e Assentos confirmar a reserva
 */
public record ReservaResponse(
        UUID id,
        Integer idEvento,
        Integer idUsuario,
        StatusReserva status,
        EstadoReserva estado,
        MotivoCancelamento motivoCancelamento,
        BigDecimal valorTotal,
        LocalDateTime dataReserva,
        LocalDateTime dataExpiracao,
        boolean pagamentoSolicitado,
        List<IngressoDaReserva> ingressos) {

    public record IngressoDaReserva(UUID id, Integer idAssento, TipoIngresso tipoIngresso, BigDecimal valor) {

        static IngressoDaReserva de(Ingresso ingresso) {
            return new IngressoDaReserva(ingresso.getId(), ingresso.getIdAssento(), ingresso.getTipoIngresso(),
                    ingresso.getValor());
        }
    }

    public static ReservaResponse de(Reserva reserva) {
        return new ReservaResponse(reserva.getId(), reserva.getIdEvento(), reserva.getIdUsuario(),
                reserva.getStatus(), reserva.getEstado(), reserva.getMotivoCancelamento(), reserva.getValorTotal(),
                reserva.getDataReserva(), reserva.getDataExpiracao(), reserva.isPagamentoSolicitado(),
                reserva.getIngressos().stream().map(IngressoDaReserva::de).toList());
    }
}
