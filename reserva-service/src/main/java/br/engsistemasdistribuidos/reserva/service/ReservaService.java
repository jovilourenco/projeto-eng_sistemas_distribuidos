package br.engsistemasdistribuidos.reserva.service;

import br.engsistemasdistribuidos.reserva.model.Ingresso;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

/**
 * Efeitos da compra com idempotência (ADR-002, item 2): persistência do ingresso, trava do assento
 * no Redis (ADR-003) e eventos no Outbox. As regras de permissão ficam no {@link IngressoService}.
 */
public interface ReservaService {

    /**
     * Cria o ingresso PENDENTE_PAGAMENTO, ou devolve o já criado com a mesma chave e o mesmo corpo.
     * 409 se o assento estiver em disputa ou a mesma requisição estiver em processamento;
     * 422 se a chave já foi usada com outro corpo.
     */
    Ingresso criarReserva(String chaveIdempotencia, Integer idEvento, Integer idAssento,
                          Integer idUsuario, TipoIngresso tipoIngresso);

    /**
     * Cancela o ingresso, ou devolve o estado atual se o cancelamento com esta chave já foi aplicado.
     * 409 se o estado do ingresso não permitir o cancelamento.
     */
    Ingresso cancelarReserva(Ingresso ingresso, String chaveIdempotencia);
}
