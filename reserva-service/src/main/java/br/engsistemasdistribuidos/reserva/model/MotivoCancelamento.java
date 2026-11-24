package br.engsistemasdistribuidos.reserva.model;

/** Por que a compra foi para COMPENSANDO/CANCELADA. */
public enum MotivoCancelamento {
    CANCELADA_PELO_USUARIO,
    PRAZO_VENCIDO,
    PAGAMENTO_RECUSADO,
    ASSENTO_INDISPONIVEL
}
