package br.engsistemasdistribuidos.pagamento.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento", schema = "pagamento")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_ingresso", nullable = false)
    private Long idIngresso;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 50)
    private String status; // "PENDENTE", "APROVADO", "REJEITADO"

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", nullable = false, length = 50)
    private MetodoPagamento metodoPagamento;

    @Column(name = "chave_idempotencia", nullable = false, unique = true, length = 255)
    private String chaveIdempotencia;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    public Pagamento() {
    }

    public Pagamento(Long idIngresso, BigDecimal valor, String status, MetodoPagamento metodoPagamento,
            String chaveIdempotencia) {
        this.idIngresso = idIngresso;
        this.valor = valor;
        this.status = status;
        this.metodoPagamento = metodoPagamento;
        this.chaveIdempotencia = chaveIdempotencia;
        this.dataCriacao = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getIdIngresso() {
        return idIngresso;
    }

    public void setIdIngresso(Long idIngresso) {
        this.idIngresso = idIngresso;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public MetodoPagamento getMetodoPagamento() {
        return metodoPagamento;
    }

    public void setMetodoPagamento(MetodoPagamento metodoPagamento) {
        this.metodoPagamento = metodoPagamento;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
}