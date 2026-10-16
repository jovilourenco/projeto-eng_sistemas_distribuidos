package br.engsistemasdistribuidos.eventoassento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.engsistemasdistribuidos.eventoassento.model.TipoEvento;

@Repository
public interface TipoEventoRepository extends JpaRepository<TipoEvento, Integer> {
    
}