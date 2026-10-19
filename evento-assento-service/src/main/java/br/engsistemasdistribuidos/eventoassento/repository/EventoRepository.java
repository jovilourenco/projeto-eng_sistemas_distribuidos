package br.engsistemasdistribuidos.eventoassento.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.engsistemasdistribuidos.eventoassento.model.Evento;
import br.engsistemasdistribuidos.eventoassento.model.Evento_;

public interface EventoRepository extends JpaSpecificationExecutor<Evento>, JpaRepository<Evento, Integer> {

    // Caso a gente precise filtrar por título em algum momento.
    static Specification<Evento> byTitulo(String titulo) {
        return (root, query, builder) -> {
            if (titulo == null || titulo.isBlank()) {
                return builder.conjunction();
            }
            return builder.like(builder.lower(root.get(Evento_.titulo)), "%" + titulo.toLowerCase() + "%");
        };
    }

    static Specification<Evento> byId(int id) {
        return (root, query, builder) -> {
            return builder.equal(root.get(Evento_.id), id);
        };
    }
}