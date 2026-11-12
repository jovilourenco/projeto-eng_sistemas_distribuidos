package br.engsistemasdistribuidos.reserva.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Registro de que uma operação (ex.: cancelamento) já foi aplicada a uma compra com determinada chave.
// A restrição de unicidade (id_ingresso, operacao, chave) impede aplicar a mesma operação duas vezes.
@Entity
@Table(schema = "reserva", name = "chave_idempotencia")
public class ChaveIdempotencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_ingresso", nullable = false)
    private UUID idIngresso;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false)
    private OperacaoIdempotente operacao;

    @Column(name = "chave", nullable = false)
    private String chave;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    public ChaveIdempotencia() {
    }

    public ChaveIdempotencia(UUID idIngresso, OperacaoIdempotente operacao, String chave) {
        this.idIngresso = idIngresso;
        this.operacao = operacao;
        this.chave = chave;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdIngresso() {
        return idIngresso;
    }

    public OperacaoIdempotente getOperacao() {
        return operacao;
    }

    public String getChave() {
        return chave;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }
}
