package br.engsistemasdistribuidos.reserva.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;
import br.engsistemasdistribuidos.reserva.service.IngressoService;

// Somente leitura: os ingressos são criados, pagos e cancelados por meio da reserva (/reservas).
@RestController
@RequestMapping("/ingressos")
public class IngressoController {

    private final IngressoService service;

    public IngressoController(IngressoService service) {
        this.service = service;
    }

    @GetMapping("/meus")
    public ResponseEntity<List<IngressoResponse>> listarMeus(UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.listarMeus(usuario));
    }

    @GetMapping("/{idIngresso}")
    public ResponseEntity<IngressoResponse> buscarPorId(@PathVariable UUID idIngresso, UsuarioAutenticado usuario) {
        return ResponseEntity.ok(service.buscarPorId(idIngresso, usuario));
    }
}
