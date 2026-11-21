package br.engsistemasdistribuidos.reserva.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import br.engsistemasdistribuidos.reserva.model.Reserva;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    // A reserva quase sempre é usada com os ingressos: carregá-los na mesma consulta evita uma segunda ida ao banco.
    @EntityGraph(attributePaths = "ingressos")
    Optional<Reserva> findComIngressosById(UUID id);
}
