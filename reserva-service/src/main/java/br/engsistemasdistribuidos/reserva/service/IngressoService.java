package br.engsistemasdistribuidos.reserva.service;

import java.util.List;
import java.util.UUID;

import br.engsistemasdistribuidos.reserva.dto.request.IngressoRequest;
import br.engsistemasdistribuidos.reserva.dto.response.IngressoResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;

public interface IngressoService {

    IngressoResponse criar(IngressoRequest request, String chaveIdempotencia, UsuarioAutenticado usuario);

    List<IngressoResponse> listarMeus(UsuarioAutenticado usuario);

    IngressoResponse buscarPorId(UUID idIngresso, UsuarioAutenticado usuario);

    IngressoResponse cancelar(UUID idIngresso, String chaveIdempotencia, UsuarioAutenticado usuario);
}
