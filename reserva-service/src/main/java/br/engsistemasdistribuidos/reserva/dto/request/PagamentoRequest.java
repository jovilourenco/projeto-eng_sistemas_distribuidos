package br.engsistemasdistribuidos.reserva.dto.request;

import br.engsistemasdistribuidos.reserva.model.MetodoPagamento;
import br.engsistemasdistribuidos.reserva.model.SimulacaoPagamento;

/**
 * Corpo do POST /reservas/{id}/pagamento.
 *
 * @param metodo    PIX, CARTAO_CREDITO ou BOLETO
 * @param simulacao comportamento do provedor simulado (APROVAR, RECUSAR ou ATRASAR); opcional
 */
public record PagamentoRequest(MetodoPagamento metodo, SimulacaoPagamento simulacao) {
}
