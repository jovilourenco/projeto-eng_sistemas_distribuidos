package br.engsistemasdistribuidos.reserva.service;

import java.util.UUID;

import br.engsistemasdistribuidos.reserva.dto.request.PagamentoRequest;
import br.engsistemasdistribuidos.reserva.dto.request.ReservaRequest;
import br.engsistemasdistribuidos.reserva.dto.response.ReservaResponse;
import br.engsistemasdistribuidos.reserva.seguranca.UsuarioAutenticado;

/** Regras de permissão e validação das rotas /reservas. Os efeitos ficam no {@link ReservaService}. */
public interface ReservaApiService {

    /** Resposta da criação e se a reserva foi criada agora (201) ou é uma repetição (200). */
    record Criada(ReservaResponse reserva, boolean nova) {
    }

    Criada criar(ReservaRequest request, String chaveIdempotencia, UsuarioAutenticado usuario);

    ReservaResponse buscarPorId(UUID idReserva, UsuarioAutenticado usuario);

    ReservaResponse pagar(UUID idReserva, PagamentoRequest request, String chaveIdempotencia,
                          UsuarioAutenticado usuario);

    ReservaResponse cancelar(UUID idReserva, String chaveIdempotencia, UsuarioAutenticado usuario);
}
