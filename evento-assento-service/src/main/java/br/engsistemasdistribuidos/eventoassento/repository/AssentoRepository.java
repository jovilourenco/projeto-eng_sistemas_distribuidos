package br.engsistemasdistribuidos.eventoassento.repository;

import java.math.BigDecimal;
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

    // ---- Escritas condicionais do gestor (ADR-003, item 6) ----
    // Só alteram o assento enquanto ele estiver DISPONIVEL; retornam o número de linhas alteradas (0 ou 1).
    // Zero linhas quer dizer que uma reserva entrou antes: o gestor recebe 409.

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE evento_assento.assento
               SET tipo_assento = :tipoAssento, valor = :valor, ativo = :ativo
             WHERE id_assento = :idAssento
               AND id_evento = :idEvento
               AND status = 'DISPONIVEL'
            """, nativeQuery = true)
    int atualizarSeDisponivel(@Param("idAssento") int idAssento, @Param("idEvento") int idEvento,
            @Param("tipoAssento") String tipoAssento, @Param("valor") BigDecimal valor,
            @Param("ativo") boolean ativo);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            DELETE FROM evento_assento.assento
             WHERE id_assento = :idAssento
               AND id_evento = :idEvento
               AND status = 'DISPONIVEL'
            """, nativeQuery = true)
    int excluirSeDisponivel(@Param("idAssento") int idAssento, @Param("idEvento") int idEvento);

    // Usada na exclusão do evento: os assentos reservados ou vendidos permanecem e fazem a chave estrangeira bloquear.
    @Modifying(clearAutomatically = true)
    @Query(value = """
            DELETE FROM evento_assento.assento
             WHERE id_evento = :idEvento
               AND status = 'DISPONIVEL'
            """, nativeQuery = true)
    int excluirDisponiveisDoEvento(@Param("idEvento") int idEvento);
}
