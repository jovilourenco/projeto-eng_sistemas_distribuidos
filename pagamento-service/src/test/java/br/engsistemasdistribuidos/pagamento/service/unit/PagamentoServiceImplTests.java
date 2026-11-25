package br.engsistemasdistribuidos.pagamento.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.repository.PagamentoRepository;
import br.engsistemasdistribuidos.pagamento.service.impl.PagamentoServiceImpl;

@ExtendWith(MockitoExtension.class)
public class PagamentoServiceImplTests {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    private PagamentoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PagamentoServiceImpl(pagamentoRepository, outboxRepository, 50L);
    }

    @Test
    void testProcessarPagamentoAprovado() {
        var idCompra = UUID.randomUUID();
        var tentativa = 1;
        var valor = new BigDecimal("150.00");
        var chave = idCompra + ":" + tentativa;
        var pagamentoSalvo = new Pagamento(
                UUID.randomUUID(), idCompra, tentativa, valor, "APROVADO",
                MetodoPagamento.PIX, SimulacaoPagamento.APROVAR, chave, LocalDateTime.now()
        );

        when(pagamentoRepository.inserirSeNaoExistir(
                any(UUID.class), eq(idCompra), eq(tentativa), eq(valor),
                eq("APROVADO"), eq("PIX"), eq("APROVAR"), eq(chave), any(LocalDateTime.class)))
                .thenReturn(1);
        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.of(pagamentoSalvo));

        var resultado = service.processarPagamento(idCompra, tentativa, valor, MetodoPagamento.PIX, SimulacaoPagamento.APROVAR);

        assertThat(resultado).isSameAs(pagamentoSalvo);
        assertThat(resultado.getStatus()).isEqualTo("APROVADO");

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(captor.getValue().getPayload()).contains(idCompra.toString());
    }

    @Test
    void testProcessarPagamentoRecusado() {
        var idCompra = UUID.randomUUID();
        var tentativa = 1;
        var valor = new BigDecimal("150.00");
        var chave = idCompra + ":" + tentativa;
        var pagamentoSalvo = new Pagamento(
                UUID.randomUUID(), idCompra, tentativa, valor, "RECUSADO",
                MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR, chave, LocalDateTime.now()
        );

        when(pagamentoRepository.inserirSeNaoExistir(
                any(UUID.class), eq(idCompra), eq(tentativa), eq(valor),
                eq("RECUSADO"), eq("CARTAO_CREDITO"), eq("RECUSAR"), eq(chave), any(LocalDateTime.class)))
                .thenReturn(1);
        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.of(pagamentoSalvo));

        var resultado = service.processarPagamento(idCompra, tentativa, valor, MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR);

        assertThat(resultado.getStatus()).isEqualTo("RECUSADO");

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAGAMENTO_FALHOU");
    }

    @Test
    void testProcessarPagamentoComAtrasoEAprovado() {
        var idCompra = UUID.randomUUID();
        var tentativa = 1;
        var valor = new BigDecimal("200.00");
        var chave = idCompra + ":" + tentativa;
        var pagamentoSalvo = new Pagamento(
                UUID.randomUUID(), idCompra, tentativa, valor, "APROVADO",
                MetodoPagamento.PIX, SimulacaoPagamento.ATRASAR, chave, LocalDateTime.now()
        );

        when(pagamentoRepository.inserirSeNaoExistir(
                any(UUID.class), eq(idCompra), eq(tentativa), eq(valor),
                eq("APROVADO"), eq("PIX"), eq("ATRASAR"), eq(chave), any(LocalDateTime.class)))
                .thenReturn(1);
        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.of(pagamentoSalvo));

        long inicio = System.currentTimeMillis();
        var resultado = service.processarPagamento(idCompra, tentativa, valor, MetodoPagamento.PIX, SimulacaoPagamento.ATRASAR);
        long duracao = System.currentTimeMillis() - inicio;

        assertThat(resultado.getStatus()).isEqualTo("APROVADO");
        assertThat(duracao).isGreaterThanOrEqualTo(40L);
    }

    @Test
    void testRetornarPagamentoExistenteSemDuplicarOutboxQuandoConflitoDeChave() {
        var idCompra = UUID.randomUUID();
        var tentativa = 2;
        var valor = new BigDecimal("100.00");
        var chave = idCompra + ":" + tentativa;
        var pagamentoExistente = new Pagamento(
                UUID.randomUUID(), idCompra, tentativa, valor, "APROVADO",
                MetodoPagamento.BOLETO, SimulacaoPagamento.APROVAR, chave, LocalDateTime.now()
        );

        when(pagamentoRepository.inserirSeNaoExistir(
                any(UUID.class), eq(idCompra), eq(tentativa), eq(valor),
                eq("APROVADO"), eq("BOLETO"), eq("APROVAR"), eq(chave), any(LocalDateTime.class)))
                .thenReturn(0);
        when(pagamentoRepository.findByChaveIdempotencia(chave)).thenReturn(Optional.of(pagamentoExistente));

        var resultado = service.processarPagamento(idCompra, tentativa, valor, MetodoPagamento.BOLETO, SimulacaoPagamento.APROVAR);

        assertThat(resultado).isSameAs(pagamentoExistente);
        verifyNoInteractions(outboxRepository);
    }
}