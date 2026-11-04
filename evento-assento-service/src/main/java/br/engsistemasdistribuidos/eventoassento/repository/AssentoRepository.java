package br.engsistemasdistribuidos.eventoassento.repository;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ---- Escritas condicionais da SAGA (ADR-003, item 3) ----
    // Cada transição é uma única instrução: o banco verifica o estado esperado e o dono (id_compra)
    // e altera a linha de forma atômica. Retornam o número de linhas alteradas (0 ou 1).
    // A condição com id_compra torna cada comando idempotente: repetir o comando da mesma compra
    // produz o mesmo resultado, e um comando de outra compra nunca altera o assento.

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE evento_assento.assento
               SET status = 'RESERVADO', id_compra = :idCompra, reservado_em = now()
             WHERE id_assento = :idAssento
               AND id_evento = :idEvento
               AND ((status = 'DISPONIVEL' AND ativo = true)
                    OR (status = 'RESERVADO' AND id_compra = :idCompra))
            """, nativeQuery = true)
    int reservar(@Param("idAssento") int idAssento, @Param("idEvento") int idEvento,
            @Param("idCompra") UUID idCompra);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE evento_assento.assento
               SET status = 'VENDIDO'
             WHERE id_assento = :idAssento
               AND id_compra = :idCompra
               AND status IN ('RESERVADO', 'VENDIDO')
            """, nativeQuery = true)
    int vender(@Param("idAssento") int idAssento, @Param("idCompra") UUID idCompra);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE evento_assento.assento
               SET status = 'DISPONIVEL', id_compra = NULL, reservado_em = NULL
             WHERE id_assento = :idAssento
               AND id_compra = :idCompra
               AND status = 'RESERVADO'
            """, nativeQuery = true)
    int liberar(@Param("idAssento") int idAssento, @Param("idCompra") UUID idCompra);
}
