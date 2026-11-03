package br.engsistemasdistribuidos.eventoassento.dto.request;

import java.math.BigDecimal;

public class AssentoLoteRequest {

    private Integer quantidade;

    private String tipoAssento;

    private BigDecimal valor;

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public String getTipoAssento() {
        return tipoAssento;
    }

    public void setTipoAssento(String tipoAssento) {
        this.tipoAssento = tipoAssento;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
