package br.engsistemasdistribuidos.pagamento.controller;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.service.PagamentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

    private final PagamentoService pagamentoService;

    public PagamentoController(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @PostMapping
    public ResponseEntity<Pagamento> criarPagamento(
            @RequestParam Long idIngresso,
            @RequestParam BigDecimal valor,
            @RequestParam MetodoPagamento metodoPagamento,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia) {

        Pagamento pagamento = pagamentoService.processarPagamento(idIngresso, valor, metodoPagamento,
                chaveIdempotencia);

        return ResponseEntity.status(HttpStatus.CREATED).body(pagamento);
    }
}