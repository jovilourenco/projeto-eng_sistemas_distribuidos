package br.engsistemasdistribuidos.eventoassento.mensageria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Comando da SAGA recebido do orquestrador. O tipo do comando (reservar, vender ou liberar)
 * vem na routing key da mensagem.
 *
 * O comando vale para todos os assentos da compra, de 1 a {@link MensagensAssento#MAXIMO_ASSENTOS}:
 * ou todos mudam de status, ou nenhum muda (ADR-004, tudo ou nada).
 *
 * @param idMensagem identificador da mensagem, devolvido na resposta para correlação
 * @param idCompra   compra que pede a transição; é ela que passa a deter os assentos
 * @param idEvento   evento ao qual os assentos devem pertencer (obrigatório em assento.reservar)
 * @param idAssentos assentos alvo do comando, sem repetição
 * @param dataHora   momento em que o comando foi emitido
 */
public record ComandoAssento(
        UUID idMensagem,
        UUID idCompra,
        Integer idEvento,
        List<Integer> idAssentos,
        Instant dataHora) {
}
