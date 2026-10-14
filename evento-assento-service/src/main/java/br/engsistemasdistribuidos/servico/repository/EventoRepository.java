package br.engsistemasdistribuidos.servico.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.engsistemasdistribuidos.servico.model.Evento;
import br.engsistemasdistribuidos.servico.model.Evento_;

public interface EventoRepository extends JpaSpecificationExecutor<Evento>, JpaRepository<Evento, Integer> {

    // útil para casos de filtro por título.
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