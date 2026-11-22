package br.engsistemasdistribuidos.eventoassento.mensageria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta publicada ao orquestrador depois de aplicar um comando. O tipo também é usado como routing key.
 *
 * @param idMensagem identificador desta resposta
 * @param idComando  identificador do comando que originou a resposta
 * @param idCompra   compra que enviou o comando
 * @param idAssento  assento alvo do comando
 * @param tipo       resultado, por exemplo assento.reservado ou assento.indisponivel
 * @param valor      valor do assento no momento da reserva; preenchido apenas em assento.reservado
 * @param dataHora   momento em que a resposta foi emitida
 */
public record RespostaAssento(
        UUID idMensagem,
        UUID idComando,
        UUID idCompra,
        Integer idAssento,
        String tipo,
        BigDecimal valor,
        Instant dataHora) {

    public static RespostaAssento para(ComandoAssento comando, String tipo, BigDecimal valor) {
        return new RespostaAssento(UUID.randomUUID(), comando.idMensagem(), comando.idCompra(),
                comando.idAssento(), tipo, valor, Instant.now());
    }

    public static RespostaAssento para(ComandoAssento comando, String tipo) {
        return para(comando, tipo, null);
    }
}
