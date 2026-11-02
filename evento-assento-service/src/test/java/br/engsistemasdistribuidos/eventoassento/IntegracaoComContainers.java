package br.engsistemasdistribuidos.eventoassento;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base dos testes de integração: aplicação completa com PostgreSQL e RabbitMQ reais (Testcontainers).
 * Requer o Docker em execução.
 *
 * Os containers são iniciados uma única vez e compartilhados por todas as classes de teste, junto com
 * o mesmo contexto do Spring. Assim os testes não reiniciam containers nem disputam a mesma fila com
 * dois contextos diferentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracaoComContainers {

    protected static final String VALOR_PADRAO = "250.00";

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @ServiceConnection
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    static {
        postgres.start();
        rabbitmq.start();
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // ---- Apoio aos testes: dados criados direto no banco ----

    protected int criarEvento() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO evento_assento.evento (titulo, descricao, data_evento, id_tipo_evento)
                VALUES ('Evento de Teste', 'Descricao do evento', '2026-12-20 20:00:00', 4)
                RETURNING id_evento
                """, Integer.class);
    }

    /** Cria um assento DISPONIVEL e ativo, com o valor padrão. */
    protected int criarAssento(int idEvento) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO evento_assento.assento (id_evento, tipo_assento, valor)
                VALUES (?, 'VIP', ?)
                RETURNING id_assento
                """, Integer.class, idEvento, new BigDecimal(VALOR_PADRAO));
    }

    protected Map<String, Object> lerAssento(int idAssento) {
        return jdbcTemplate.queryForMap(
                "SELECT status, id_compra, reservado_em, valor FROM evento_assento.assento WHERE id_assento = ?",
                idAssento);
    }
}
