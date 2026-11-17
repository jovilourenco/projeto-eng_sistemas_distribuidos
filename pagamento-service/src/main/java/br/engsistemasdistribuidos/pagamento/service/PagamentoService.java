package br.engsistemasdistribuidos.pagamento.service;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import java.math.BigDecimal;

public interface PagamentoService {
    
    /**
     * Processa um pagamento simulado garantindo idempotência através da chave fornecida.
     * 
     * @param idIngresso ID do ingresso que está sendo pago
     * @param valor Valor da transação
     * @param chaveIdempotencia Chave única para evitar duplicidade
     * @param metodoPagamento Método de pagamento utilizado (PIX, CARTÃO DE CRÉDITO, BOLETO)
     * @return O pagamento processado (novo ou recuperado se já existia)
     */
    Pagamento processarPagamento(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento, String chaveIdempotencia);
    
}