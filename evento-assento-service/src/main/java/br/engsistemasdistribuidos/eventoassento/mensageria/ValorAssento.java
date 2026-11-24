package br.engsistemasdistribuidos.eventoassento.mensageria;

import java.math.BigDecimal;

/**
 * Preço de um assento no momento da reserva. É o valor que será cobrado, mesmo que o gestor
 * altere o preço do assento depois (ADR-002, item 6).
 */
public record ValorAssento(Integer idAssento, BigDecimal valor) {
}
