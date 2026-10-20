package br.engsistemasdistribuidos.eventoassento.dto.response;

import java.math.BigDecimal;

import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;

public class AssentoResponse {

    private int id;

    private int idEvento;

    private String tipoAssento;

    private BigDecimal valor;

    private StatusAssento status;

    private boolean ativo;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(int idEvento) {
        this.idEvento = idEvento;
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

    public StatusAssento getStatus() {
        return status;
    }

    public void setStatus(StatusAssento status) {
        this.status = status;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
