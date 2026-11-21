package br.engsistemasdistribuidos.reserva.service;

import java.util.List;
import java.util.UUID;

import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;

/** Consulta de ingressos. Criar, pagar e cancelar são operações da reserva ({@link ReservaApiService}). */
public interface IngressoService {

    List<IngressoResponse> listarMeus(UsuarioAutenticado usuario);

    IngressoResponse buscarPorId(UUID idIngresso, UsuarioAutenticado usuario);
}
