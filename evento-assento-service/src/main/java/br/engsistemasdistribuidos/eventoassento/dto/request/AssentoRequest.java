package br.engsistemasdistribuidos.eventoassento.dto.request;

import java.math.BigDecimal;

// Campos não informados (null) permanecem como estão no assento.
public class AssentoRequest {

    private String tipoAssento;

    private BigDecimal valor;

    private Boolean ativo;

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

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }
}
