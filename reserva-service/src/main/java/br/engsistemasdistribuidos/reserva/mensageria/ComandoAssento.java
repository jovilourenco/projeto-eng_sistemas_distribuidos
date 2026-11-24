package br.engsistemasdistribuidos.reserva.mensageria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Comando da SAGA enviado ao Serviço de Eventos e Assentos pelo Outbox (contrato em docs/mensageria.md).
 * O tipo do comando (assento.reservar, assento.vender ou assento.liberar) vai na routing key,
 * que é o tipo_evento do registro no Outbox.
 */
public record ComandoAssento(
        UUID idMensagem,
        UUID idCompra,
        Integer idEvento,
        List<Integer> idAssentos,
        Instant dataHora) {

    // Routing keys dos comandos.
    public static final String RESERVAR = "assento.reservar";
    public static final String VENDER = "assento.vender";
    public static final String LIBERAR = "assento.liberar";

    public static ComandoAssento novo(UUID idCompra, Integer idEvento, List<Integer> idAssentos) {
        return new ComandoAssento(UUID.randomUUID(), idCompra, idEvento, idAssentos, Instant.now());
    }
}
