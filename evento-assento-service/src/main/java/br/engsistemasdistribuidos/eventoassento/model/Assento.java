package br.engsistemasdistribuidos.eventoassento.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(schema = "evento_assento", name = "assento")
public class Assento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assento_sequence")
    @SequenceGenerator(name = "assento_sequence", sequenceName = "evento_assento.assento_id_assento_seq", allocationSize = 1)
    @Column(name = "id_assento")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", nullable = false)
    private Evento evento;

    @Column(name = "tipo_assento")
    private String tipoAssento;

    @Column(name = "valor")
    private BigDecimal valor;

    // status e idCompra só mudam pelas escritas condicionais da SAGA (AssentoRepository).
    // updatable = false impede que um save() do JPA desfaça uma reserva feita em paralelo (ADR-003).
    // Impedir que o gestor altere ou exclua um assento recém-reservado é a issue #39 (ADR-003, item 6).
    @Enumerated(EnumType.STRING)
    @Column(name = "status", updatable = false)
    private StatusAssento status;

    @Column(name = "ativo")
    private boolean ativo;

    @Column(name = "id_compra", insertable = false, updatable = false)
    private UUID idCompra;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Evento getEvento() {
        return evento;
    }

    public void setEvento(Evento evento) {
        this.evento = evento;
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

    public UUID getIdCompra() {
        return idCompra;
    }
}
