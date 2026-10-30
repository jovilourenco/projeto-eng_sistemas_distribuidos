package br.engsistemasdistribuidos.reserva.repository;

import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    // Eventos não processados, do antigo para o mais novo.
    List<OutboxEvent> findByProcessadoFalseOrderByDataCriacaoAsc();
}