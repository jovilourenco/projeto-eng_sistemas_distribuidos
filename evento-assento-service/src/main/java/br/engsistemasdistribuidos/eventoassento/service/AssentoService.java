package br.engsistemasdistribuidos.eventoassento.service;

import java.util.List;

import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoLoteRequest;
import br.engsistemasdistribuidos.eventoassento.dto.request.AssentoRequest;
import br.engsistemasdistribuidos.eventoassento.dto.response.AssentoResponse;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;

public interface AssentoService {

    List<AssentoResponse> criarEmLote(int idEvento, AssentoLoteRequest request);

    List<AssentoResponse> listar(int idEvento, StatusAssento status);

    AssentoResponse buscarPorId(int idEvento, int idAssento);

    AssentoResponse atualizar(int idEvento, int idAssento, AssentoRequest request);

    void excluir(int idEvento, int idAssento);
}
