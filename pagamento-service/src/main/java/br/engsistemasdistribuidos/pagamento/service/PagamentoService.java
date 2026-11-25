package br.engsistemasdistribuidos.pagamento.service;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;

import java.math.BigDecimal;
import java.util.UUID;

public interface PagamentoService {
    
    Pagamento processarPagamento(
            UUID idCompra,
            Integer tentativa,
            BigDecimal valor,
            MetodoPagamento metodo,
            SimulacaoPagamento simulacao
    );
}