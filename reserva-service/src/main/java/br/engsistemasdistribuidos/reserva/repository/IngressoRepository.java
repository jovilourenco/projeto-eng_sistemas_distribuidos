package br.engsistemasdistribuidos.reserva.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.engsistemasdistribuidos.reserva.model.Ingresso;

public interface IngressoRepository extends JpaRepository<Ingresso, UUID> {

    // Mais recentes primeiro. A reserva vem na mesma consulta (status, evento e prazo são dela).
    @Query("""
            SELECT i FROM Ingresso i JOIN FETCH i.reserva r
             WHERE r.idUsuario = :idUsuario
             ORDER BY r.dataReserva DESC, i.idAssento
            """)
    List<Ingresso> listarDoUsuario(@Param("idUsuario") Integer idUsuario);

    @Query("SELECT i FROM Ingresso i JOIN FETCH i.reserva WHERE i.id = :id")
    Optional<Ingresso> buscarComReserva(@Param("id") UUID id);
}
