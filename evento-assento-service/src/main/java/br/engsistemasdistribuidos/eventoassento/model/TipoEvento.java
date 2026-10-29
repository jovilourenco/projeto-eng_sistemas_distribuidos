package br.engsistemasdistribuidos.eventoassento.model;

import java.util.Objects;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "evento_assento", name = "tipo_evento")
@Immutable
public class TipoEvento {

    public static final int PALESTRA = 1;
    public static final int CURSO = 2;
    public static final int SEMINARIO = 3;
    public static final int SHOW = 4;

    @Id
    @Column(name = "id_tipo_evento")
    private int id;

    @Column(name = "descricao")
    private String descricao;

    public TipoEvento() {
    }

    public TipoEvento(int id) {
        this.id = id;
    }

    public boolean isShow() {
        return this.id == SHOW;
    }

    public boolean isPalestra() {
        return this.id == PALESTRA;
    }

    public boolean isCurso() {
        return this.id == CURSO;
    }

    public boolean isSeminario() {
        return this.id == SEMINARIO;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof TipoEvento))
            return false;
        TipoEvento other = (TipoEvento) obj;
        return id == other.id;
    }
}