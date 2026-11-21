package br.engsistemasdistribuidos.reserva.dto.request;

import java.util.List;

import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

/**
 * Corpo do POST /reservas. O usuário é o autenticado. O usuário no corpo, para o perfil de teste do kit
 * (ADR-004, decisão 4), fica para a issue #56.
 *
 * @param idEvento     evento dos assentos
 * @param idAssentos   de 1 a 4 assentos distintos, reservados juntos (tudo ou nada)
 * @param tipoIngresso tipo dos ingressos; INTEIRA se não informado
 */
public record ReservaRequest(Integer idEvento, List<Integer> idAssentos, TipoIngresso tipoIngresso) {
}
