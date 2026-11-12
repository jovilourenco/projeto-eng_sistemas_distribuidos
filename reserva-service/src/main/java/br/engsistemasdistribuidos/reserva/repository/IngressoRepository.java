package br.engsistemasdistribuidos.reserva.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.engsistemasdistribuidos.reserva.model.Ingresso;

public interface IngressoRepository extends JpaRepository<Ingresso, UUID> {

    // Mais recentes primeiro.
    List<Ingresso> findByIdUsuarioOrderByDataReservaDesc(Integer idUsuario);
}
