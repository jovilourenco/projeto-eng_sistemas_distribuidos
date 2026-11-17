package br.engsistemasdistribuidos.pagamento.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", schema = "pagamento")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agregado_tipo", nullable = false, length = 100)
    private String agregadoTipo;

    @Column(name = "agregado_id", nullable = false, length = 100)
    private String agregadoId;

    @Column(name = "tipo_evento", nullable = false, length = 150)
    private String tipoEvento;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean processado = false;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    public OutboxEvent() {
    }

    public OutboxEvent(String agregadoTipo, String agregadoId, String tipoEvento, String payload) {
        this.agregadoTipo = agregadoTipo;
        this.agregadoId = agregadoId;
        this.tipoEvento = tipoEvento;
        this.payload = payload;
        this.dataCriacao = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAgregadoTipo() {
        return agregadoTipo;
    }

    public void setAgregadoTipo(String agregadoTipo) {
        this.agregadoTipo = agregadoTipo;
    }

    public String getAgregadoId() {
        return agregadoId;
    }

    public void setAgregadoId(String agregadoId) {
        this.agregadoId = agregadoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public boolean isProcessado() {
        return processado;
    }

    public void setProcessado(boolean processado) {
        this.processado = processado;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
}