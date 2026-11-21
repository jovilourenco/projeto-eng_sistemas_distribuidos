package br.engsistemasdistribuidos.reserva.model;

/**
 * Situação da compra exibida ao cliente. É derivada do estado da SAGA e do motivo do cancelamento,
 * e é o campo que o kit de avaliação do professor acompanha (ver o contrato do kit).
 */
public enum StatusReserva {
    RESERVADA,
    PAGAMENTO_EM_PROCESSAMENTO,
    CONFIRMADA,
    RECUSADA,
    EXPIRADA,
    CANCELADA;

    public static StatusReserva de(EstadoReserva estado, MotivoCancelamento motivo, boolean pagamentoSolicitado) {
        return switch (estado) {
            case INICIADA, AGUARDANDO_PAGAMENTO -> pagamentoSolicitado ? PAGAMENTO_EM_PROCESSAMENTO : RESERVADA;
            case PROCESSANDO_PAGAMENTO -> PAGAMENTO_EM_PROCESSAMENTO;
            case CONFIRMANDO, CONCLUIDA -> CONFIRMADA;
            case COMPENSANDO, CANCELADA -> motivo == null ? CANCELADA : switch (motivo) {
                case PAGAMENTO_RECUSADO -> RECUSADA;
                case PRAZO_VENCIDO -> EXPIRADA;
                case CANCELADA_PELO_USUARIO, ASSENTO_INDISPONIVEL -> CANCELADA;
            };
        };
    }
}
