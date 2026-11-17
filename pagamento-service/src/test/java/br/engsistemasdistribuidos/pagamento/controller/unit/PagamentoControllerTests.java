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
    void testCriarPagamento() {
        var id = UUID.randomUUID();
        var agora = LocalDateTime.now();
        var chave = "idem-123";

        var request = new PagamentoRequest();
        request.setIdIngresso(10L);
        request.setValor(new BigDecimal("120.00"));
        request.setMetodoPagamento(MetodoPagamento.PIX);

        var pagamento = new Pagamento(10L, new BigDecimal("120.00"), "APROVADO", MetodoPagamento.PIX, chave);
        pagamento.setId(id);

        var responseEsperado = new PagamentoResponse();
        responseEsperado.setId(id);
        responseEsperado.setIdIngresso(10L);
        responseEsperado.setValor(new BigDecimal("120.00"));
        responseEsperado.setStatus("APROVADO");
        responseEsperado.setMetodoPagamento(MetodoPagamento.PIX);
        responseEsperado.setChaveIdempotencia(chave);
        responseEsperado.setDataCriacao(agora);

        when(pagamentoService.processarPagamento(10L, new BigDecimal("120.00"), MetodoPagamento.PIX, chave))
                .thenReturn(pagamento);
        when(pagamentoMapper.toResponse(pagamento)).thenReturn(responseEsperado);

        var response = controller.criarPagamento(request, chave);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(id);
        assertThat(response.getBody().getIdIngresso()).isEqualTo(10L);
        assertThat(response.getBody().getValor()).isEqualByComparingTo("120.00");
        assertThat(response.getBody().getStatus()).isEqualTo("APROVADO");
        assertThat(response.getBody().getMetodoPagamento()).isEqualTo(MetodoPagamento.PIX);
        assertThat(response.getBody().getChaveIdempotencia()).isEqualTo(chave);
        assertThat(response.getBody().getDataCriacao()).isEqualTo(agora);

        verify(pagamentoService).processarPagamento(10L, new BigDecimal("120.00"), MetodoPagamento.PIX, chave);
        verify(pagamentoMapper).toResponse(pagamento);
    }
}