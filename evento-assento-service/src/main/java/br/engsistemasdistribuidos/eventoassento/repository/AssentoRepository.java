package br.engsistemasdistribuidos.eventoassento.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.engsistemasdistribuidos.eventoassento.model.Assento;
import br.engsistemasdistribuidos.eventoassento.model.Assento_;
import br.engsistemasdistribuidos.eventoassento.model.Evento_;
import br.engsistemasdistribuidos.eventoassento.model.StatusAssento;

public interface AssentoRepository extends JpaSpecificationExecutor<Assento>, JpaRepository<Assento, Integer> {

    static Specification<Assento> byId(int id) {
        return (root, query, builder) -> builder.equal(root.get(Assento_.id), id);
    }

    static Specification<Assento> byEvento(int idEvento) {
        return (root, query, builder) -> builder.equal(root.get(Assento_.evento).get(Evento_.id), idEvento);
    }

    // Filtro opcional: sem status, devolve todos.
    static Specification<Assento> byStatus(StatusAssento status) {
        return (root, query, builder) -> {
            if (status == null) {
                return builder.conjunction();
            }
            return builder.equal(root.get(Assento_.status), status);
        };
    }
}
