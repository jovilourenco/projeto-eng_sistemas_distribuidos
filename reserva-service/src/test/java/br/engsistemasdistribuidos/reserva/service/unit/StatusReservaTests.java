package br.engsistemasdistribuidos.reserva.service.unit;

import static br.engsistemasdistribuidos.reserva.model.StatusReserva.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.engsistemasdistribuidos.reserva.model.EstadoReserva;
import br.engsistemasdistribuidos.reserva.model.MotivoCancelamento;
import br.engsistemasdistribuidos.reserva.model.StatusIngresso;
import br.engsistemasdistribuidos.reserva.model.StatusReserva;

// Tradução do estado da SAGA para o que o cliente e o kit de avaliação enxergam (ADR-002 e ADR-004).
class StatusReservaTests {

    @Test
    void statusDaReserva_derivadoDoEstadoDoMotivoEDoPedidoDePagamento() {
        assertThat(StatusReserva.de(EstadoReserva.INICIADA, null, false)).isEqualTo(RESERVADA);
        assertThat(StatusReserva.de(EstadoReserva.AGUARDANDO_PAGAMENTO, null, false)).isEqualTo(RESERVADA);
        assertThat(StatusReserva.de(EstadoReserva.AGUARDANDO_PAGAMENTO, null, true))
                .isEqualTo(PAGAMENTO_EM_PROCESSAMENTO);
        assertThat(StatusReserva.de(EstadoReserva.PROCESSANDO_PAGAMENTO, null, true))
                .isEqualTo(PAGAMENTO_EM_PROCESSAMENTO);
        assertThat(StatusReserva.de(EstadoReserva.CONFIRMANDO, null, true)).isEqualTo(CONFIRMADA);
        assertThat(StatusReserva.de(EstadoReserva.CONCLUIDA, null, true)).isEqualTo(CONFIRMADA);
        assertThat(StatusReserva.de(EstadoReserva.COMPENSANDO, MotivoCancelamento.PAGAMENTO_RECUSADO, true))
                .isEqualTo(RECUSADA);
        assertThat(StatusReserva.de(EstadoReserva.CANCELADA, MotivoCancelamento.PRAZO_VENCIDO, false))
                .isEqualTo(EXPIRADA);
        assertThat(StatusReserva.de(EstadoReserva.CANCELADA, MotivoCancelamento.CANCELADA_PELO_USUARIO, false))
                .isEqualTo(CANCELADA);
        assertThat(StatusReserva.de(EstadoReserva.CANCELADA, MotivoCancelamento.ASSENTO_INDISPONIVEL, false))
                .isEqualTo(CANCELADA);
        assertThat(StatusReserva.de(EstadoReserva.CANCELADA, null, false)).isEqualTo(CANCELADA);
    }

    @Test
    void statusDoIngresso_segueATabelaDoAdr002() {
        assertThat(EstadoReserva.INICIADA.statusIngresso()).isEqualTo(StatusIngresso.PENDENTE_PAGAMENTO);
        assertThat(EstadoReserva.PROCESSANDO_PAGAMENTO.statusIngresso()).isEqualTo(StatusIngresso.PENDENTE_PAGAMENTO);
        assertThat(EstadoReserva.CONFIRMANDO.statusIngresso()).isEqualTo(StatusIngresso.PAGO);
        assertThat(EstadoReserva.CONCLUIDA.statusIngresso()).isEqualTo(StatusIngresso.PAGO);
        assertThat(EstadoReserva.COMPENSANDO.statusIngresso()).isEqualTo(StatusIngresso.CANCELADO);
        assertThat(EstadoReserva.CANCELADA.statusIngresso()).isEqualTo(StatusIngresso.CANCELADO);
    }
}
