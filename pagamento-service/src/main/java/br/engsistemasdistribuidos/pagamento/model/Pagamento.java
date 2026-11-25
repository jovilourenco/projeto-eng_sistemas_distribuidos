package br.engsistemasdistribuidos.pagamento.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento", schema = "pagamento")
public class Pagamento {

    @Id
    private UUID id;

    @Column(name = "id_compra", nullable = false)
    private UUID idCompra;

    @Column(nullable = false)
    private Integer tentativa;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 50)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", nullable = false, length = 50)
    private MetodoPagamento metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SimulacaoPagamento simulacao;

    @Column(name = "chave_idempotencia", nullable = false, unique = true, length = 255)
    private String chaveIdempotencia;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    public Pagamento() {
    }

    public Pagamento(UUID id, UUID idCompra, Integer tentativa, BigDecimal valor, String status,
            MetodoPagamento metodoPagamento, SimulacaoPagamento simulacao, String chaveIdempotencia,
            LocalDateTime dataCriacao) {
        this.id = id;
        this.idCompra = idCompra;
        this.tentativa = tentativa;
        this.valor = valor;
        this.status = status;
        this.metodoPagamento = metodoPagamento;
        this.simulacao = simulacao;
        this.chaveIdempotencia = chaveIdempotencia;
        this.dataCriacao = dataCriacao;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIdCompra() {
        return idCompra;
    }

    public void setIdCompra(UUID idCompra) {
        this.idCompra = idCompra;
    }

    public Integer getTentativa() {
        return tentativa;
    }

    public void setTentativa(Integer tentativa) {
        this.tentativa = tentativa;
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

    public SimulacaoPagamento getSimulacao() {
        return simulacao;
    }

    public void setSimulacao(SimulacaoPagamento simulacao) {
        this.simulacao = simulacao;
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