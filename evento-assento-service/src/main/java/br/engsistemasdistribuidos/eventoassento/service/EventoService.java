package br.engsistemasdistribuidos.eventoassento.service;

import java.util.List;

import br.engsistemasdistribuidos.eventoassento.dto.request.EventoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.EventoResponse;

public interface EventoService {
    
    EventoResponse criar(EventoRequest request);
    
    List<EventoResponse> listar(String titulo);
    
    EventoResponse buscarPorId(int idEvento);
    
    EventoResponse atualizar(int idEvento, EventoRequest request);
    
    void excluir(int idEvento);
}