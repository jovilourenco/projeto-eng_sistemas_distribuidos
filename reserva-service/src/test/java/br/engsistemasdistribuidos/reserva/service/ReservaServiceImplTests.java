package br.engsistemasdistribuidos.reserva.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.service.impl.ReservaServiceImpl;

@ExtendWith(MockitoExtension.class)
public class ReservaServiceImplTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OutboxEventRepository outboxRepository;

    @InjectMocks
    private ReservaServiceImpl service;

    @Test
    void testCriarReserva() {
        var idAssento = 10;
        var idUsuario = 20;
        var lockKey = "assento:lock:10";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), eq("20"), any(Duration.class))).thenReturn(true);
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.criarReserva(idAssento, idUsuario);

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        var eventoSalvo = captor.getValue();
        assertThat(eventoSalvo).isNotNull();
        assertThat(eventoSalvo.getAgregadoTipo()).isEqualTo("INGRESSO");
        assertThat(eventoSalvo.getTipoEvento()).isEqualTo("RESERVA_CRIADA");
        assertThat(eventoSalvo.getPayload()).contains("\"idAssento\": 10");
        assertThat(eventoSalvo.isProcessado()).isFalse();
        assertThat(eventoSalvo.getDataCriacao()).isNotNull();

        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void testNaoPermitirCriarReservaAssentoJaBloqueado() {
        var idAssento = 10;
        var idUsuario = 20;
        var lockKey = "assento:lock:10";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), eq("20"), any(Duration.class))).thenReturn(false);

        var exception = assertThrows(ResponseStatusException.class, () -> service.criarReserva(idAssento, idUsuario));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        verify(outboxRepository, never()).save(any());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void testLiberarLockQuandoOcorrerErroAoSalvarOutbox() {
        var idAssento = 10;
        var idUsuario = 20;
        var lockKey = "assento:lock:10";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(lockKey), eq("20"), any(Duration.class))).thenReturn(true);
        when(outboxRepository.save(any(OutboxEvent.class))).thenThrow(new RuntimeException("Erro no banco de dados"));

        assertThrows(RuntimeException.class, () -> service.criarReserva(idAssento, idUsuario));

        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(redisTemplate).delete(lockKey);
    }
}