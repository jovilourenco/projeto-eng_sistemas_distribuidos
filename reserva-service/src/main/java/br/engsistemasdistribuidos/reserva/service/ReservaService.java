package br.engsistemasdistribuidos.reserva.service;

import java.util.List;

import br.engsistemasdistribuidos.reserva.model.MetodoPagamento;
import br.engsistemasdistribuidos.reserva.model.Reserva;
import br.engsistemasdistribuidos.reserva.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

/**
 * Efeitos da compra com idempotência (ADR-002, item 2): persistência da reserva, trava dos assentos
 * no Redis (ADR-003) e comandos no Outbox. As regras de permissão e validação ficam no
 * {@link ReservaApiService}.
 */
public interface ReservaService {

    /** Resultado da criação: a reserva e se ela foi criada agora (201) ou já existia com a mesma chave (200). */
    record Criacao(Reserva reserva, boolean nova) {
    }

    /**
     * Cria a reserva INICIADA com um ingresso por assento, ou devolve a já criada com a mesma chave e o mesmo corpo.
     * 409 se algum assento estiver em disputa;
     * 422 se a chave já foi usada com outro corpo.
     */
    Criacao criarReserva(String chaveIdempotencia, Integer idEvento, List<Integer> idAssentos,
                         Integer idUsuario, TipoIngresso tipoIngresso);

    /**
     * Registra o pedido de pagamento, que o orquestrador processa. Repetir com a mesma chave não registra de novo.
     * 409 se a compra já foi paga, cancelada ou passou do prazo; 422 se a chave já foi usada com outro corpo.
     */
    Reserva solicitarPagamento(Reserva reserva, String chaveIdempotencia, MetodoPagamento metodo,
                               SimulacaoPagamento simulacao);

    /**
     * Cancela a compra (vai para COMPENSANDO e pede a liberação dos assentos), ou devolve o estado atual
     * se o cancelamento com esta chave já foi aplicado. 409 se o estado não permitir o cancelamento.
     */
    Reserva cancelarReserva(Reserva reserva, String chaveIdempotencia);
}
