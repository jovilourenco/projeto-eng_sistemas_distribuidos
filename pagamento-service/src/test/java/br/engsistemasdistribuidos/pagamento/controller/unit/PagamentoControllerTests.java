package br.engsistemasdistribuidos.pagamento.controller.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import br.engsistemasdistribuidos.pagamento.controller.PagamentoController;
import br.engsistemasdistribuidos.pagamento.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.pagamento.dto.response.PagamentoResponse;
import br.engsistemasdistribuidos.pagamento.mapper.PagamentoMapper;
import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.pagamento.service.PagamentoService;

@ExtendWith(MockitoExtension.class)
public class PagamentoControllerTests {

    @Mock
    private PagamentoService pagamentoService;

    @Mock
    private PagamentoMapper pagamentoMapper;

    @InjectMocks
    private PagamentoController controller;

    @Test
    void testCriarPagamentoAprovadoRetorna200() {
        var id = UUID.randomUUID();
        var idCompra = UUID.randomUUID();
        var agora = LocalDateTime.now();
        var valor = new BigDecimal("120.00");
        var chave = idCompra + ":1";

        var request = new PagamentoRequest(idCompra, 1, valor, MetodoPagamento.PIX, SimulacaoPagamento.APROVAR);
        var pagamento = new Pagamento(id, idCompra, 1, valor, "APROVADO", MetodoPagamento.PIX, SimulacaoPagamento.APROVAR, chave, agora);
        var responseEsperado = new PagamentoResponse(id, idCompra, 1, valor, "APROVADO", MetodoPagamento.PIX, chave, agora);

        when(pagamentoService.processarPagamento(idCompra, 1, valor, MetodoPagamento.PIX, SimulacaoPagamento.APROVAR))
                .thenReturn(pagamento);
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseEsperado);

        var response = controller.criarPagamento(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(responseEsperado);

        verify(pagamentoService).processarPagamento(idCompra, 1, valor, MetodoPagamento.PIX, SimulacaoPagamento.APROVAR);
        verify(pagamentoMapper).toResponse(pagamento);
    }

    @Test
    void testCriarPagamentoRecusadoRetorna402() {
        var id = UUID.randomUUID();
        var idCompra = UUID.randomUUID();
        var agora = LocalDateTime.now();
        var valor = new BigDecimal("120.00");
        var chave = idCompra + ":1";

        var request = new PagamentoRequest(idCompra, 1, valor, MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR);
        var pagamento = new Pagamento(id, idCompra, 1, valor, "RECUSADO", MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR, chave, agora);
        var responseEsperado = new PagamentoResponse(id, idCompra, 1, valor, "RECUSADO", MetodoPagamento.CARTAO_CREDITO, chave, agora);

        when(pagamentoService.processarPagamento(idCompra, 1, valor, MetodoPagamento.CARTAO_CREDITO, SimulacaoPagamento.RECUSAR))
                .thenReturn(pagamento);
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseEsperado);

        var response = controller.criarPagamento(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isEqualTo(responseEsperado);
    }
}