package br.engsistemasdistribuidos.eventoassento.dto.request;

import java.time.LocalDateTime;

import br.engsistemasdistribuidos.eventoassento.model.TipoEvento;

public class EventoRequest {

    private String titulo;
    
    private String descricao;
    
    private LocalDateTime dataEvento;

    private TipoEvento tipoEvento;

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDateTime getDataEvento() {
        return dataEvento;
    }

    public void setDataEvento(LocalDateTime dataEvento) {
        this.dataEvento = dataEvento;
    }

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEvento tipoEvento) {
        this.tipoEvento = tipoEvento;
    }
}