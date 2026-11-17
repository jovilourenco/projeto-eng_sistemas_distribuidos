package br.engsistemasdistribuidos.pagamento.dto.request;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PagamentoRequest {

    @NotNull(message = "O ID do ingresso é obrigatório")
    private Long idIngresso;

    @NotNull(message = "O valor do pagamento é obrigatório")
    private BigDecimal valor;

    @NotNull(message = "O método de pagamento é obrigatório")
    private MetodoPagamento metodoPagamento;

    public PagamentoRequest() {
    }

    public PagamentoRequest(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento) {
        this.idIngresso = idIngresso;
        this.valor = valor;
        this.metodoPagamento = metodoPagamento;
    }

    public Long getIdIngresso() {
        return idIngresso;
    }

    public void setIdIngresso(Long idIngresso) {
        this.idIngresso = idIngresso;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public MetodoPagamento getMetodoPagamento() {
        return metodoPagamento;
    }

    public void setMetodoPagamento(MetodoPagamento metodoPagamento) {
        this.metodoPagamento = metodoPagamento;
    }
}