package br.engsistemasdistribuidos.reserva.model;

// Operações sobre uma reserva existente protegidas por Idempotency-Key em tabela própria (ADR-002).
// O pedido de pagamento guarda a própria chave na reserva (chave_pagamento).
public enum OperacaoIdempotente {
    CANCELAMENTO
}
