package br.engsistemasdistribuidos.pagamento.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.repository.PagamentoRepository;
import br.engsistemasdistribuidos.pagamento.service.impl.PagamentoServiceImpl;

@ExtendWith(MockitoExtension.class)
public class PagamentoServiceImplTests {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @InjectMocks
    private PagamentoServiceImpl service;

    @Test
    void testProcessarPagamentoAprovado() {
        var idIngresso = 10L;
        var valor = new BigDecimal("150.00");
        var metodo = MetodoPagamento.PIX;
        var chave = "idem-key-123";
        var idGerado = UUID.randomUUID();

        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.empty());
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            p.setId(idGerado);
            return p;
        });
        when(outboxRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resultado = service.processarPagamento(idIngresso, valor, metodo, chave);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(idGerado);
        assertThat(resultado.getIdIngresso()).isEqualTo(idIngresso);
        assertThat(resultado.getValor()).isEqualByComparingTo(valor);
        assertThat(resultado.getMetodoPagamento()).isEqualTo(metodo);
        assertThat(resultado.getStatus()).isEqualTo("APROVADO");
        assertThat(resultado.getChaveIdempotencia()).isEqualTo(chave);
        assertThat(resultado.getDataCriacao()).isNotNull();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());

        var eventoSalvo = captor.getValue();
        assertThat(eventoSalvo.getAgregadoTipo()).isEqualTo("PAGAMENTO");
        assertThat(eventoSalvo.getAgregadoId()).isEqualTo(idGerado.toString());
        assertThat(eventoSalvo.getTipoEvento()).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(eventoSalvo.getPayload()).contains("\"status\":\"APROVADO\"");
    }

    @Test
    void testProcessarPagamentoRejeitadoQuandoValorZeroOuNegativo() {
        var idIngresso = 10L;
        var valor = BigDecimal.ZERO;
        var metodo = MetodoPagamento.CARTAO_CREDITO;
        var chave = "idem-key-456";
        var idGerado = UUID.randomUUID();

        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.empty());
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento p = invocation.getArgument(0);
            p.setId(idGerado);
            return p;
        });

        var resultado = service.processarPagamento(idIngresso, valor, metodo, chave);

        assertThat(resultado.getStatus()).isEqualTo("REJEITADO");

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAGAMENTO_FALHOU");
    }

    @Test
    void testRetornarPagamentoExistenteQuandoChaveIdempotenciaJaProcessada() {
        var chave = "idem-key-duplicada";
        var pagamentoExistente = new Pagamento();
        pagamentoExistente.setId(UUID.randomUUID());
        pagamentoExistente.setIdIngresso(10L);
        pagamentoExistente.setValor(new BigDecimal("100.00"));
        pagamentoExistente.setStatus("APROVADO");
        pagamentoExistente.setMetodoPagamento(MetodoPagamento.BOLETO);
        pagamentoExistente.setChaveIdempotencia(chave);
        pagamentoExistente.setDataCriacao(LocalDateTime.now());

        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.of(pagamentoExistente));

        var resultado = service.processarPagamento(10L, new BigDecimal("100.00"), MetodoPagamento.BOLETO, chave);

        assertThat(resultado).isSameAs(pagamentoExistente);
        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void testLancarExcecaoQuandoServicoIndisponivelSimulado() {
        var chave = "idem-key-erro";

        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.processarPagamento(666L, new BigDecimal("100.00"), MetodoPagamento.PIX, chave));

        verify(pagamentoRepository, never()).save(any());
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void testPagamentoFallback() {
        var erro = new RuntimeException("Falha externa");

        var resultado = service.pagamentoFallback(15L, new BigDecimal("80.00"), MetodoPagamento.PIX, "idem-fallback",
                erro);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getIdIngresso()).isEqualTo(15L);
        assertThat(resultado.getStatus()).isEqualTo("SERVICO_INDISPONIVEL_FALLBACK");
        assertThat(resultado.getMetodoPagamento()).isEqualTo(MetodoPagamento.PIX);
    }
}