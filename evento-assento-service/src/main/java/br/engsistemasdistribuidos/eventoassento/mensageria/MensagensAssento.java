package br.engsistemasdistribuidos.eventoassento.mensageria;

/**
 * Nomes de exchanges, filas e tipos de mensagem da SAGA de compra (ADR-002 e docs/mensageria.md).
 * O tipo da mensagem é a routing key.
 */
public final class MensagensAssento {

    // Comandos enviados pelo orquestrador (Serviço de Reservas) e consumidos por este serviço.
    public static final String EXCHANGE_COMANDOS = "ticketflow.assento";
    public static final String FILA_COMANDOS = "evento-assento.comandos";

    // Mensagens que falharam repetidamente vão para a fila de mensagens mortas (ADR-001).
    public static final String EXCHANGE_MENSAGENS_MORTAS = "ticketflow.assento.dlx";
    public static final String FILA_MENSAGENS_MORTAS = "evento-assento.comandos.dlq";

    // Respostas publicadas por este serviço e consumidas pelo orquestrador.
    public static final String EXCHANGE_RESPOSTAS = "ticketflow.reserva";

    // Comandos
    public static final String RESERVAR = "assento.reservar";
    public static final String VENDER = "assento.vender";
    public static final String LIBERAR = "assento.liberar";

    // Respostas
    public static final String RESERVADO = "assento.reservado";
    public static final String INDISPONIVEL = "assento.indisponivel";
    public static final String VENDIDO = "assento.vendido";
    public static final String VENDA_RECUSADA = "assento.venda_recusada";
    public static final String LIBERADO = "assento.liberado";
    public static final String LIBERACAO_RECUSADA = "assento.liberacao_recusada";

    private MensagensAssento() {
    }
}
