package br.engsistemasdistribuidos.pagamento.service.unit;

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

import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.scheduler.OutboxRelayScheduler;

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
        evento.setAgregadoTipo("PAGAMENTO");
        evento.setAgregadoId(id.toString());
        evento.setTipoEvento("PAGAMENTO_APROVADO");
        evento.setPayload("{\"status\":\"APROVADO\"}");
        evento.setProcessado(false);
        evento.setDataCriacao(agora);

        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(List.of(evento));
        when(outboxRepository.save(evento)).thenReturn(evento);

        scheduler.processarOutbox();

        assertThat(evento.getId()).isEqualTo(id);
        assertThat(evento.getAgregadoTipo()).isEqualTo("PAGAMENTO");
        assertThat(evento.getAgregadoId()).isEqualTo(id.toString());
        assertThat(evento.getTipoEvento()).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(evento.getPayload()).isEqualTo("{\"status\":\"APROVADO\"}");
        assertThat(evento.getDataCriacao()).isEqualTo(agora);
        assertThat(evento.isProcessado()).isTrue();

        verify(outboxRepository).save(evento);
    }

    @Test
    void testNaoProcessarOutboxQuandoListaVazia() {
        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(Collections.emptyList());

        scheduler.processarOutbox();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void testCapturarErroAoSalvarOutbox() {
        var evento = new OutboxEvent("PAGAMENTO", "1", "PAGAMENTO_APROVADO", "{}");

        when(outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc()).thenReturn(List.of(evento));
        doThrow(new RuntimeException("Erro no banco")).when(outboxRepository).save(evento);

        scheduler.processarOutbox();

        verify(outboxRepository).save(evento);
    }
}