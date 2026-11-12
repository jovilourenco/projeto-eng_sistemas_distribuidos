package br.engsistemasdistribuidos.reserva.model;

// Operações sobre uma compra existente protegidas por Idempotency-Key (ADR-002). PAGAMENTO virá depois.
public enum OperacaoIdempotente {
    CANCELAMENTO
}
