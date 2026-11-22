package br.engsistemasdistribuidos.eventoassento.mensageria;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando da SAGA recebido do orquestrador. O tipo do comando (reservar, vender ou liberar)
 * vem na routing key da mensagem.
 *
 * @param idMensagem identificador da mensagem, devolvido na resposta para correlação
 * @param idCompra   compra que pede a transição; é ela que passa a deter o assento
 * @param idEvento   evento ao qual o assento deve pertencer (obrigatório em assento.reservar)
 * @param idAssento  assento alvo do comando
 * @param dataHora   momento em que o comando foi emitido
 */
public record ComandoAssento(
        UUID idMensagem,
        UUID idCompra,
        Integer idEvento,
        Integer idAssento,
        Instant dataHora) {
}
