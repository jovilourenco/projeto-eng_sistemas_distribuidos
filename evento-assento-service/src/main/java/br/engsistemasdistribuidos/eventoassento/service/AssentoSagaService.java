package br.engsistemasdistribuidos.eventoassento.service;

import br.engsistemasdistribuidos.eventoassento.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.RespostaAssento;

/**
 * Aplica os comandos da SAGA de compra sobre o status dos assentos (ADR-003).
 * Cada método é idempotente: repetir o mesmo comando produz a mesma resposta.
 */
public interface AssentoSagaService {

    /** DISPONIVEL → RESERVADO. Responde assento.reservado (com o valor) ou assento.indisponivel. */
    RespostaAssento reservar(ComandoAssento comando);

    /** RESERVADO → VENDIDO. Responde assento.vendido ou assento.venda_recusada. */
    RespostaAssento vender(ComandoAssento comando);

    /** RESERVADO → DISPONIVEL. Responde assento.liberado ou assento.liberacao_recusada. */
    RespostaAssento liberar(ComandoAssento comando);
}
