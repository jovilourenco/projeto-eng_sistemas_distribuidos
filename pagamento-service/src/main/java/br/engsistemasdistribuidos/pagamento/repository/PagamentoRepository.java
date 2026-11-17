package br.engsistemasdistribuidos.pagamento.repository;

import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    
    // Busca um pagamento existente através da chave de idempotência
    Optional<Pagamento> findByChaveIdempotencia(String chaveIdempotencia);
}