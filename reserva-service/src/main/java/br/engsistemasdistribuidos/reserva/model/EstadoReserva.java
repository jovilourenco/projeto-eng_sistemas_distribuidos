package br.engsistemasdistribuidos.reserva.model;

/** Estado da SAGA de compra (ADR-002, item 3). */
public enum EstadoReserva {
    INICIADA,
    AGUARDANDO_PAGAMENTO,
    PROCESSANDO_PAGAMENTO,
    CONFIRMANDO,
    CONCLUIDA,
    COMPENSANDO,
    CANCELADA;

    /** Status exibido em cada ingresso da compra, conforme a tabela do ADR-002 (item 1). */
    public StatusIngresso statusIngresso() {
        return switch (this) {
            case INICIADA, AGUARDANDO_PAGAMENTO, PROCESSANDO_PAGAMENTO -> StatusIngresso.PENDENTE_PAGAMENTO;
            case CONFIRMANDO, CONCLUIDA -> StatusIngresso.PAGO;
            case COMPENSANDO, CANCELADA -> StatusIngresso.CANCELADO;
        };
    }

    /** Estados em que a compra ainda pode ser paga ou cancelada pelo usuário. */
    public boolean aguardandoUsuario() {
        return this == INICIADA || this == AGUARDANDO_PAGAMENTO;
    }
}
