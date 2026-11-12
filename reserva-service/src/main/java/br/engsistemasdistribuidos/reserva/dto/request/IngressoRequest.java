package br.engsistemasdistribuidos.reserva.dto.request;

import br.engsistemasdistribuidos.reserva.model.TipoIngresso;

// O idUsuario não vem no corpo: é extraído do usuário autenticado.
public class IngressoRequest {

    private Integer idEvento;

    private Integer idAssento;

    private TipoIngresso tipoIngresso;

    public Integer getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(Integer idEvento) {
        this.idEvento = idEvento;
    }

    public Integer getIdAssento() {
        return idAssento;
    }

    public void setIdAssento(Integer idAssento) {
        this.idAssento = idAssento;
    }

    public TipoIngresso getTipoIngresso() {
        return tipoIngresso;
    }

    public void setTipoIngresso(TipoIngresso tipoIngresso) {
        this.tipoIngresso = tipoIngresso;
    }
}
