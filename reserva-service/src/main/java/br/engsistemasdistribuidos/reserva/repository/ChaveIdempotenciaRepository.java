package br.engsistemasdistribuidos.reserva.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.engsistemasdistribuidos.reserva.model.ChaveIdempotencia;
import br.engsistemasdistribuidos.reserva.model.OperacaoIdempotente;

public interface ChaveIdempotenciaRepository extends JpaRepository<ChaveIdempotencia, UUID> {

    boolean existsByIdIngressoAndOperacaoAndChave(UUID idIngresso, OperacaoIdempotente operacao, String chave);
}
