package br.engsistemasdistribuidos.reserva.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// Um ingresso por assento da reserva. idAssento é referência lógica ao Serviço de Eventos e Assentos (sem FK).
// O status do ingresso não é gravado: ele é derivado do estado da compra (ADR-002, item 1).
@Entity
@Table(schema = "reserva", name = "ingresso")
public class Ingresso {

    @Id
    @Column(name = "id_ingresso")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_reserva", nullable = false)
    private Reserva reserva;

    @Column(name = "id_assento", nullable = false)
    private Integer idAssento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingresso", nullable = false)
    private TipoIngresso tipoIngresso;

    // Preço do assento no momento da reserva, informado pelo Serviço de Eventos e Assentos; nulo até lá.
    @Column(name = "valor")
    private BigDecimal valor;

    public StatusIngresso getStatus() {
        return reserva.getEstado().statusIngresso();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public void setReserva(Reserva reserva) {
        this.reserva = reserva;
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

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }
}
