package br.engsistemasdistribuidos.eventoassento.mensageria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resposta publicada ao orquestrador depois de aplicar um comando. O tipo também é usado como routing key.
 *
 * @param idMensagem identificador desta resposta
 * @param idComando  identificador do comando que originou a resposta
 * @param idCompra   compra que enviou o comando
 * @param idAssentos assentos do comando, na mesma ordem em que vieram
 * @param tipo       resultado, por exemplo assento.reservado ou assento.indisponivel
 * @param valores    preço de cada assento no momento da reserva; preenchido apenas em assento.reservado
 * @param dataHora   momento em que a resposta foi emitida
 */
public record RespostaAssento(
        UUID idMensagem,
        UUID idComando,
        UUID idCompra,
        List<Integer> idAssentos,
        String tipo,
        List<ValorAssento> valores,
        Instant dataHora) {

    public static RespostaAssento para(ComandoAssento comando, String tipo, List<ValorAssento> valores) {
        return new RespostaAssento(UUID.randomUUID(), comando.idMensagem(), comando.idCompra(),
                comando.idAssentos(), tipo, valores, Instant.now());
    }

    public static RespostaAssento para(ComandoAssento comando, String tipo) {
        return para(comando, tipo, null);
    }
}
