package br.engsistemasdistribuidos.reserva.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

// idEvento, idAssento e idUsuario são referências lógicas a dados de outros serviços (sem FK).
// O id é o identificador da compra na SAGA: derivado do usuário + Idempotency-Key (ADR-002), não gerado pelo banco.
@Entity
@Table(schema = "reserva", name = "ingresso")
public class Ingresso {

    @Id
    @Column(name = "id_ingresso")
    private UUID id;

    @Column(name = "id_evento", nullable = false)
    private Integer idEvento;

    @Column(name = "id_assento", nullable = false)
    private Integer idAssento;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ingresso", nullable = false)
    private TipoIngresso tipoIngresso;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusIngresso status;

    @Column(name = "data_reserva", nullable = false)
    private LocalDateTime dataReserva;

    @Column(name = "data_expiracao", nullable = false)
    private LocalDateTime dataExpiracao;

    // Resumo (SHA-256) do corpo da requisição de criação: mesma chave com corpo diferente → 422 (ADR-002).
    @Column(name = "hash_requisicao", nullable = false)
    private String hashRequisicao;

    // Controle de concorrência otimista: dois gatilhos simultâneos na mesma compra não se sobrescrevem (ADR-002).
    // Integer (e não int): versão null indica ao Spring Data que o ingresso é novo, então o save faz INSERT.
    // Com id atribuído e versão primitiva, ele tentaria um merge e não detectaria a chave duplicada.
    @Version
    @Column(name = "versao", nullable = false)
    private Integer versao;

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

    public Integer getIdAssento() {
        return idAssento;
    }

    public void setIdAssento(Integer idAssento) {
        this.idAssento = idAssento;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public TipoIngresso getTipoIngresso() {
        return tipoIngresso;
    }

    public void setTipoIngresso(TipoIngresso tipoIngresso) {
        this.tipoIngresso = tipoIngresso;
    }

    public StatusIngresso getStatus() {
        return status;
    }

    public void setStatus(StatusIngresso status) {
        this.status = status;
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

    public String getHashRequisicao() {
        return hashRequisicao;
    }

    public void setHashRequisicao(String hashRequisicao) {
        this.hashRequisicao = hashRequisicao;
    }

    public Integer getVersao() {
        return versao;
    }

    public void setVersao(Integer versao) {
        this.versao = versao;
    }
}
