package br.engsistemasdistribuidos.reserva.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.engsistemasdistribuidos.reserva.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.reserva.dto.request.ReservaRequest;
import br.engsistemasdistribuidos.reserva.dto.response.ReservaResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.ReservaApiService;

/**
 * Compra de 1 a 4 assentos, no formato do contrato do kit de avaliação (ADR-004). A disputa por assentos é
 * respondida na hora (409) pela trava do Redis; o restante da compra segue pela SAGA (ADR-002), e o cliente
 * acompanha o andamento pelo GET.
 */
@RestController
@RequestMapping("/reservas")
public class ReservaController {

    public static final String HEADER_IDEMPOTENCIA = "Idempotency-Key";

    private final ReservaApiService service;

    public ReservaController(ReservaApiService service) {
        this.service = service;
    }

    // 201 na criação; 200 quando a mesma Idempotency-Key é repetida e devolve a reserva original.
    @PostMapping
    public ResponseEntity<ReservaResponse> reservar(
            @RequestBody(required = false) ReservaRequest request,
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String chaveIdempotencia,
            UsuarioAutenticado usuario) {
        ReservaApiService.Criada criada = service.criar(request, chaveIdempotencia, usuario);
        return ResponseEntity.status(criada.nova() ? HttpStatus.CREATED : HttpStatus.OK).body(criada.reserva());
    }

    @GetMapping("/{idReserva}")
    public ResponseEntity<ReservaResponse> buscarPorId(@PathVariable UUID idReserva, UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.buscarPorId(idReserva, usuario));
    }

    /*
     * O pagamento é processado pelo orquestrador da SAGA. Enquanto ele não termina, a resposta é 202 e o
     * cliente consulta o GET. Uma repetição depois do fim recebe o resultado: 200 confirmada, 402 recusada,
     * 409 expirada ou cancelada.
     */
    @PostMapping("/{idReserva}/pagamento")
    public ResponseEntity<ReservaResponse> pagar(
            @PathVariable UUID idReserva,
            @RequestBody(required = false) PagamentoRequest request,
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String chaveIdempotencia,
            UsuarioAutenticado usuario) {
        ReservaResponse reserva = service.pagar(idReserva, request, chaveIdempotencia, usuario);
        HttpStatus codigo = switch (reserva.status()) {
            case CONFIRMADA -> HttpStatus.OK;
            case RECUSADA -> HttpStatus.PAYMENT_REQUIRED;
            case EXPIRADA, CANCELADA -> HttpStatus.CONFLICT;
            case RESERVADA, PAGAMENTO_EM_PROCESSAMENTO -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(codigo).body(reserva);
    }

    @PostMapping("/{idReserva}/cancelamento")
    public ResponseEntity<ReservaResponse> cancelar(
            @PathVariable UUID idReserva,
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String chaveIdempotencia,
            UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.cancelar(idReserva, chaveIdempotencia, usuario));
    }
}
