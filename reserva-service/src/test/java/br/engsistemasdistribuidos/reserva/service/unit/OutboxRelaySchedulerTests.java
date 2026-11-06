package br.engsistemasdistribuidos.reserva.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.scheduler.OutboxRelayScheduler;

@ExtendWith(MockitoExtension.class)
public class OutboxRelaySchedulerTests {

    @Mock
    private OutboxEventRepository outboxRepository;

    @InjectMocks
    private OutboxRelayScheduler scheduler;

    @Test
    void testProcessarOutboxComSucesso() {
        var id = UUID.randomUUID();
        var agora = LocalDateTime.now();

        var evento = new OutboxEvent();
        evento.setId(id);
        evento.setAgregadoTipo("INGRESSO");
        evento.setAgregadoId("999");
        evento.setTipoEvento("RESERVA_CRIADA");
        evento.setPayload("{\"idIngresso\": 999}");
        evento.setProcessado(false);
        evento.setDataCriacao(agora);

        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(List.of(evento));
        when(outboxRepository.save(evento)).thenReturn(evento);

        scheduler.processarOutbox();

        assertThat(evento.getId()).isEqualTo(id);
        assertThat(evento.getAgregadoTipo()).isEqualTo("INGRESSO");
        assertThat(evento.getAgregadoId()).isEqualTo("999");
        assertThat(evento.getTipoEvento()).isEqualTo("RESERVA_CRIADA");
        assertThat(evento.getPayload()).isEqualTo("{\"idIngresso\": 999}");
        assertThat(evento.getDataCriacao()).isEqualTo(agora);
        assertThat(evento.isProcessado()).isTrue();

        verify(outboxRepository).findByProcessadoFalseOrderByDataCriacaoAsc();
        verify(outboxRepository).save(evento);
    }

    @Test
    void testNaoProcessarOutboxQuandoListaEstiverVazia() {
        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(Collections.emptyList());

        scheduler.processarOutbox();

        verify(outboxRepository).findByProcessadoFalseOrderByDataCriacaoAsc();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void testCapturarErroQuandoFalharAoSalvarEventoNoOutbox() {
        var evento = new OutboxEvent("INGRESSO", "999", "RESERVA_CRIADA", "{\"idIngresso\": 999}");
        evento.setId(UUID.randomUUID());

        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(List.of(evento));
        doThrow(new RuntimeException("Falha ao atualizar outbox")).when(outboxRepository).save(evento);

        scheduler.processarOutbox();

        verify(outboxRepository).findByProcessadoFalseOrderByDataCriacaoAsc();
        verify(outboxRepository).save(evento);
    }
}
