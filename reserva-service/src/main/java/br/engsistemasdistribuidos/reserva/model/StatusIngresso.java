package br.engsistemasdistribuidos.reserva.model;

// Status exibido ao usuário. Derivado do estado da SAGA (ADR-002).
public enum StatusIngresso {
    PENDENTE_PAGAMENTO,
    PAGO,
    CANCELADO
}
