package br.engsistemasdistribuidos.eventoassento.model;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(schema = "evento_assento", name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "evento_sequence")
    @SequenceGenerator(name = "evento_sequence", sequenceName = "evento_assento.evento_id_evento_seq", allocationSize = 1)
    @Column(name = "id_evento")
    private int id;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "data_evento")
    private LocalDateTime dataEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_evento", nullable = false)
    private TipoEvento tipoEvento;

    @Column(name = "data_cadastro")
    private LocalDateTime dataCadastro;

    @Column(name = "data_atualizacao")
    private LocalDateTime dataAtualizacao;

    @Column(name = "id_usuario_cadastro")
    private Integer idUsuarioCadastro;

    @Column(name = "id_usuario_atualizacao")
    private Integer idUsuarioAtualizacao;

    @Override
    public int hashCode() {
        return Objects.hash(id, titulo, descricao, dataEvento, dataCadastro, idUsuarioCadastro);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Evento other)) {
            return false;
        }
        return id == other.id
                && Objects.equals(titulo, other.titulo)
                && Objects.equals(descricao, other.descricao)
                && Objects.equals(dataEvento, other.dataEvento)
                && Objects.equals(dataCadastro, other.dataCadastro)
                && Objects.equals(dataAtualizacao, other.dataAtualizacao)
                && Objects.equals(idUsuarioCadastro, other.idUsuarioCadastro)
                && Objects.equals(idUsuarioAtualizacao, other.idUsuarioAtualizacao);
    }

    @Override
    public String toString() {
        return "Evento [id=" + id + ", titulo=" + titulo + ", descricao=" + descricao + ", dataEvento=" + dataEvento
                + ", dataCadastro=" + dataCadastro + ", dataAtualizacao=" + dataAtualizacao + ", idUsuarioCadastro="
                + idUsuarioCadastro + ", idUsuarioAtualizacao=" + idUsuarioAtualizacao + "]";
    }
    
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

    public TipoEvento getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(TipoEvento tipoEvento) {
        this.tipoEvento = tipoEvento;
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