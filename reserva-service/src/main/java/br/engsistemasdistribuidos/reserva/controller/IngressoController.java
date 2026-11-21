package br.engsistemasdistribuidos.reserva.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.engsistemasdistribuidos.reserva.dto.request.IngressoRequest;
import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.IngressoService;

// Contrato definido no ADR-002 (item 2). Sem PUT/DELETE: o ingresso muda apenas por eventos
// do sistema ou pelo cancelamento explícito.
@RestController
@RequestMapping("/ingressos")
public class IngressoController {

    public static final String HEADER_IDEMPOTENCIA = "Idempotency-Key";

    private final IngressoService service;

    public IngressoController(IngressoService service) {
        this.service = service;
    }

    // 202: a reserva é assíncrona; o usuário acompanha o andamento consultando o ingresso.
    // Uma repetição com a mesma chave devolve o mesmo ingresso, também com 202.
    @PostMapping
    public ResponseEntity<IngressoResponse> reservar(
            @RequestBody IngressoRequest request,
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String chaveIdempotencia,
            UsuarioAutenticado usuario) {
        return ResponseEntity.accepted().body(service.criar(request, chaveIdempotencia, usuario));
    }

    @GetMapping("/meus")
    public ResponseEntity<List<IngressoResponse>> listarMeus(UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.listarMeus(usuario));
    }

    @GetMapping("/{idIngresso}")
    public ResponseEntity<IngressoResponse> buscarPorId(
            @PathVariable UUID idIngresso,
            UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.buscarPorId(idIngresso, usuario));
    }

    @PostMapping("/{idIngresso}/cancelamento")
    public ResponseEntity<IngressoResponse> cancelar(
            @PathVariable UUID idIngresso,
            @RequestHeader(name = HEADER_IDEMPOTENCIA, required = false) String chaveIdempotencia,
            UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.cancelar(idIngresso, chaveIdempotencia, usuario));
    }
}
