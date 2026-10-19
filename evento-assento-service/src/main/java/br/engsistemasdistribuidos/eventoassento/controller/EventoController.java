package br.engsistemasdistribuidos.eventoassento.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.engsistemasdistribuidos.eventoassento.dto.request.EventoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.EventoResponse;
import br.engsistemasdistribuidos.eventoassento.service.EventoService;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EventoResponse> cadastrar(@RequestBody EventoRequest request) {
        EventoResponse response = service.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EventoResponse>> listar(@RequestParam(required = false) String titulo) {
        List<EventoResponse> responses = service.listar(titulo);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{idEvento}")
    public ResponseEntity<EventoResponse> buscarPorId(@PathVariable int idEvento) {
        EventoResponse response = service.buscarPorId(idEvento);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{idEvento}")
    public ResponseEntity<EventoResponse> atualizar(
            @PathVariable int idEvento, 
            @RequestBody EventoRequest request) {
        EventoResponse response = service.atualizar(idEvento, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{idEvento}")
    public ResponseEntity<Void> excluir(@PathVariable int idEvento) {
        service.excluir(idEvento);
        return ResponseEntity.noContent().build(); 
    }
}