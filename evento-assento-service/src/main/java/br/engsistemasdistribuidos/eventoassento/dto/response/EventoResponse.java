package br.engsistemasdistribuidos.eventoassento.dto.response;

import java.time.LocalDateTime;

public class EventoResponse {

    private int id;
    
    private String titulo;
    
    private String descricao;
    
    private LocalDateTime dataEvento;
    
    private LocalDateTime dataCadastro;
    
    private LocalDateTime dataAtualizacao;
    
    private Integer idUsuarioCadastro;
    
    private Integer idUsuarioAtualizacao;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

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

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public Integer getIdUsuarioCadastro() {
        return idUsuarioCadastro;
    }

    public void setIdUsuarioCadastro(Integer idUsuarioCadastro) {
        this.idUsuarioCadastro = idUsuarioCadastro;
    }

    public Integer getIdUsuarioAtualizacao() {
        return idUsuarioAtualizacao;
    }

    public void setIdUsuarioAtualizacao(Integer idUsuarioAtualizacao) {
        this.idUsuarioAtualizacao = idUsuarioAtualizacao;
    }
}