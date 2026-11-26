package br.engsistemasdistribuidos.eventoassento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Operações do gestor como escritas condicionais (ADR-003, item 6): o gestor nunca altera nem exclui um assento
 * depois que uma compra o reservou, e excluir um evento é tudo ou nada. PostgreSQL real (Testcontainers).
 */
class OperacoesGestorIntegrationTest extends IntegracaoComContainers {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void excluirEvento_semAssentosReservados_retorna204_eRemoveEventoEAssentos() throws Exception {
        int idEvento = criarEvento();
        criarAssento(idEvento);
        criarAssento(idEvento);

        mockMvc.perform(delete("/eventos/{idEvento}", idEvento))
                .andExpect(status().isNoContent());

        assertEquals(0, contarAssentos(idEvento));
        assertEquals(0, contarEventos(idEvento));
    }

    @Test
    void excluirEvento_comAssentoReservado_retorna409_eNadaMuda() throws Exception {
        int idEvento = criarEvento();
        criarAssento(idEvento);
        int reservado = criarAssento(idEvento);
        criarAssento(idEvento);
        assertEquals(1, reservarNoBanco(reservado, idEvento));

        mockMvc.perform(delete("/eventos/{idEvento}", idEvento))
                .andExpect(status().isConflict());

        // Tudo ou nada: os assentos disponíveis também voltaram, e o evento continua lá.
        assertEquals(3, contarAssentos(idEvento));
        assertEquals(1, contarEventos(idEvento));
    }

    @Test
    void excluirEvento_inexistente_retorna404() throws Exception {
        mockMvc.perform(delete("/eventos/{idEvento}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirAssento_deOutroEvento_retorna404_eNaoApaga() throws Exception {
        int eventoA = criarEvento();
        int eventoB = criarEvento();
        int assentoDoA = criarAssento(eventoA);

        mockMvc.perform(delete("/eventos/{idEvento}/assentos/{idAssento}", eventoB, assentoDoA))
                .andExpect(status().isNotFound());

        assertEquals(1, contarAssentos(eventoA));
    }

    @Test
    void atualizarAssentoReservado_retorna409_eNaoAlteraNada() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        assertEquals(1, reservarNoBanco(idAssento, idEvento));

        mockMvc.perform(put("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":1.00,\"ativo\":false}"))
                .andExpect(status().isConflict());

        assertEquals(0, new BigDecimal(VALOR_PADRAO).compareTo((BigDecimal) lerAssento(idAssento).get("valor")));
        assertEquals("RESERVADO", lerAssento(idAssento).get("status"));
        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT ativo FROM evento_assento.assento WHERE id_assento = ?", Boolean.class, idAssento));
    }

    /**
     * Uma reserva (o mesmo UPDATE condicional da SAGA) e uma exclusão do gestor disputam o mesmo assento ao mesmo
     * tempo. Em cada rodada só um vence: ou o assento ficou reservado (e a exclusão recebeu 409), ou foi apagado
     * (e a reserva não alterou nenhuma linha). Nunca os dois, nunca nenhum.
     */
    @Test
    void corrida_reservaContraExclusao_apenasUmaVence() throws Exception {
        final int rodadas = 20;
        int idEvento = criarEvento();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < rodadas; i++) {
                final int idAssento = criarAssento(idEvento);
                CyclicBarrier largada = new CyclicBarrier(2);

                Future<Integer> reserva = pool.submit(() -> {
                    largada.await();
                    return reservarNoBanco(idAssento, idEvento);
                });
                Future<Integer> exclusao = pool.submit(() -> {
                    largada.await();
                    return mockMvc.perform(delete("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento))
                            .andReturn().getResponse().getStatus();
                });

                boolean reservou = reserva.get() == 1;
                int statusExclusao = exclusao.get();
                boolean excluiu = statusExclusao == 204;

                assertTrue(reservou ^ excluiu,
                        "rodada " + i + ": deveria haver exatamente um vencedor (reservou=" + reservou
                                + ", status da exclusão=" + statusExclusao + ")");
                if (reservou) {
                    assertEquals(409, statusExclusao, "rodada " + i);
                    assertEquals("RESERVADO", lerAssento(idAssento).get("status"), "rodada " + i);
                } else {
                    assertEquals(0, contarAssento(idAssento), "rodada " + i + ": o assento deveria ter sido apagado");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- Apoio ----

    // Mesma condição do reservar da SAGA (AssentoRepository.reservar), executada direto no banco.
    private int reservarNoBanco(int idAssento, int idEvento) {
        return jdbcTemplate.update("""
                UPDATE evento_assento.assento
                   SET status = 'RESERVADO', id_compra = CAST(? AS uuid), reservado_em = now()
                 WHERE id_assento = ? AND id_evento = ? AND status = 'DISPONIVEL' AND ativo = true
                """, UUID.randomUUID().toString(), idAssento, idEvento);
    }

    private int contarAssentos(int idEvento) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_assento.assento WHERE id_evento = ?", Integer.class, idEvento);
    }

    private int contarAssento(int idAssento) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_assento.assento WHERE id_assento = ?", Integer.class, idAssento);
    }

    private int contarEventos(int idEvento) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_assento.evento WHERE id_evento = ?", Integer.class, idEvento);
    }
}
