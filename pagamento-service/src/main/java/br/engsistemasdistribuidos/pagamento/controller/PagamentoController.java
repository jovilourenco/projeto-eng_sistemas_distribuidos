package br.engsistemasdistribuidos.pagamento.controller;

import br.engsistemasdistribuidos.pagamento.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.pagamento.dto.response.PagamentoResponse;
import br.engsistemasdistribuidos.pagamento.mapper.PagamentoMapper;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;
    private final PagamentoMapper pagamentoMapper;

    public PagamentoController(PagamentoService pagamentoService, PagamentoMapper pagamentoMapper) {
        this.pagamentoService = pagamentoService;
        this.pagamentoMapper = pagamentoMapper;
    }

    @PostMapping
    public ResponseEntity<PagamentoResponse> criarPagamento(
            @Valid @RequestBody PagamentoRequest request,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia) {

        Pagamento pagamento = pagamentoService.processarPagamento(
                request.getIdIngresso(),
                request.getValor(),
                request.getMetodoPagamento(),
                chaveIdempotencia
        );

        PagamentoResponse response = pagamentoMapper.toResponse(pagamento);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}