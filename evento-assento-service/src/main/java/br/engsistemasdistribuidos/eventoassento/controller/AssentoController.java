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

import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoLoteRequest;
import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.AssentoResponse;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;
import br.engsistemasdistribuidos.eventoassento.service.AssentoService;

@RestController
@RequestMapping("/eventos/{idEvento}/assentos")
public class AssentoController {

    private final AssentoService service;

    public AssentoController(AssentoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<List<AssentoResponse>> cadastrarEmLote(
            @PathVariable int idEvento,
            @RequestBody AssentoLoteRequest request) {
        List<AssentoResponse> responses = service.criarEmLote(idEvento, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping
    public ResponseEntity<List<AssentoResponse>> listar(
            @PathVariable int idEvento,
            @RequestParam(required = false) StatusAssento status) {
        return ResponseEntity.ok(service.listar(idEvento, status));
    }

    @GetMapping("/{idAssento}")
    public ResponseEntity<AssentoResponse> buscarPorId(
            @PathVariable int idEvento,
            @PathVariable int idAssento) {
        return ResponseEntity.ok(service.buscarPorId(idEvento, idAssento));
    }

    @PutMapping("/{idAssento}")
    public ResponseEntity<AssentoResponse> atualizar(
            @PathVariable int idEvento,
            @PathVariable int idAssento,
            @RequestBody AssentoRequest request) {
        return ResponseEntity.ok(service.atualizar(idEvento, idAssento, request));
    }

    @DeleteMapping("/{idAssento}")
    public ResponseEntity<Void> excluir(
            @PathVariable int idEvento,
            @PathVariable int idAssento) {
        service.excluir(idEvento, idAssento);
        return ResponseEntity.noContent().build();
    }
}
