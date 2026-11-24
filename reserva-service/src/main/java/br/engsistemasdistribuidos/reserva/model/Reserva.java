package br.engsistemasdistribuidos.reserva.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Compra de 1 a 4 assentos (ADR-004) e estado da SAGA que a coordena (ADR-002).
 * idEvento e idUsuario são referências lógicas a dados de outros serviços (sem FK).
 * O id é derivado do usuário + Idempotency-Key, não gerado pelo banco.
 */
@Entity
@Table(schema = "reserva", name = "reserva")
public class Reserva {

    @Id
    @Column(name = "id_reserva")
    private UUID id;

    @Column(name = "id_evento", nullable = false)
    private Integer idEvento;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoReserva estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelamento")
    private MotivoCancelamento motivoCancelamento;

    // Preenchido quando o Serviço de Eventos e Assentos confirma a reserva e informa os preços.
    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    @Column(name = "chave_idempotencia", nullable = false)
    private String chaveIdempotencia;

    // Resumo (SHA-256) do corpo da criação: mesma chave com corpo diferente → 422 (ADR-002).
    @Column(name = "hash_requisicao", nullable = false)
    private String hashRequisicao;

    @Column(name = "data_reserva", nullable = false)
    private LocalDateTime dataReserva;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    // ---- Pedido de pagamento em aberto. Quem o processa é o orquestrador (ADR-002, item 5). ----

    @Column(name = "chave_pagamento")
    private String chavePagamento;

    @Column(name = "hash_pagamento")
    private String hashPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento")
    private MetodoPagamento metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "simulacao_pagamento")
    private SimulacaoPagamento simulacaoPagamento;

    @Column(name = "data_pedido_pagamento")
    private LocalDateTime dataPedidoPagamento;

    // Controle de concorrência otimista: dois gatilhos simultâneos na mesma compra não se sobrescrevem (ADR-002).
    // Integer (e não int): versão null indica ao Spring Data que a reserva é nova, então o save faz INSERT
    // e um id duplicado falha com violação de unicidade, em vez de virar um merge.
    @Version
    @Column(name = "versao", nullable = false)
    private Integer versao;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.PERSIST)
    @OrderBy("idAssento")
    private List<Ingresso> ingressos = new ArrayList<>();

    public StatusReserva getStatus() {
        return StatusReserva.de(estado, motivoCancelamento, isPagamentoSolicitado());
    }

    public boolean isPagamentoSolicitado() {
        return chavePagamento != null;
    }

    public void adicionarIngresso(Ingresso ingresso) {
        ingresso.setReserva(this);
        ingressos.add(ingresso);
    }

    public void registrarPedidoPagamento(String chave, String hash, MetodoPagamento metodo,
                                         SimulacaoPagamento simulacao, LocalDateTime quando) {
        this.chavePagamento = chave;
        this.hashPagamento = hash;
        this.metodoPagamento = metodo;
        this.simulacaoPagamento = simulacao;
        this.dataPedidoPagamento = quando;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getIdEvento() {
        return idEvento;
    }

    public void setIdEvento(Integer idEvento) {
        this.idEvento = idEvento;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }

    public MotivoCancelamento getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public void setMotivoCancelamento(MotivoCancelamento motivoCancelamento) {
        this.motivoCancelamento = motivoCancelamento;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public void setHashRequisicao(String hashRequisicao) {
        this.hashRequisicao = hashRequisicao;
    }

    public LocalDateTime getDataReserva() {
        return dataReserva;
    }

    public void setDataReserva(LocalDateTime dataReserva) {
        this.dataReserva = dataReserva;
    }

    public LocalDateTime getDataExpiracao() {
        return dataExpiracao;
    }

    public void setDataExpiracao(LocalDateTime dataExpiracao) {
        this.dataExpiracao = dataExpiracao;
    }

    public String getChavePagamento() {
        return chavePagamento;
    }

    public String getHashPagamento() {
        return hashPagamento;
    }

    public MetodoPagamento getMetodoPagamento() {
        return metodoPagamento;
    }

    public SimulacaoPagamento getSimulacaoPagamento() {
        return simulacaoPagamento;
    }

    public LocalDateTime getDataPedidoPagamento() {
        return dataPedidoPagamento;
    }

    public Integer getVersao() {
        return versao;
    }

    public void setVersao(Integer versao) {
        this.versao = versao;
    }

    public List<Ingresso> getIngressos() {
        return ingressos;
    }
}
