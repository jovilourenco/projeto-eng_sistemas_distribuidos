package br.engsistemasdistribuidos.pagamento.repository;

import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByChaveIdempotencia(String chaveIdempotencia);

    @Modifying
    @Query(value = """
            INSERT INTO pagamento.pagamento (
                id, id_compra, tentativa, valor, status,
                metodo_pagamento, simulacao, chave_idempotencia, data_criacao
            ) VALUES (
                :id, :idCompra, :tentativa, :valor, :status,
                :metodoPagamento, :simulacao, :chaveIdempotencia, :dataCriacao
            )
            ON CONFLICT (chave_idempotencia) DO NOTHING
            """, nativeQuery = true)
    int inserirSeNaoExistir(
            @Param("id") UUID id,
            @Param("idCompra") UUID idCompra,
            @Param("tentativa") Integer tentativa,
            @Param("valor") BigDecimal valor,
            @Param("status") String status,
            @Param("metodoPagamento") String metodoPagamento,
            @Param("simulacao") String simulacao,
            @Param("chaveIdempotencia") String chaveIdempotencia,
            @Param("dataCriacao") LocalDateTime dataCriacao
    );
}