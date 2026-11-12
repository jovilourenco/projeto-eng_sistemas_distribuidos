package br.engsistemasdistribuidos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.jayway.jsonpath.JsonPath;

/**
 * Testes de integração dos endpoints de ingressos: aplicação completa + PostgreSQL e Redis reais (Testcontainers).
 * Requer o Docker em execução. Cada teste usa assentos, usuários e chaves próprios, então não dependem uns dos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IngressoIntegrationTest {

    private static final String HEADER_ID = "X-Usuario-Id";
    private static final String HEADER_PERFIL = "X-Usuario-Perfil";
    private static final String HEADER_CHAVE = "Idempotency-Key";
    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1000);

    // Usuário "postgres": a migration do outbox faz GRANT para esse papel, que não existiria com o usuário padrão "test".
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15").withUsername("postgres");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ criação

    @Test
    void reservar_retorna202ComIngressoPendenteTravaAssentoEGravaOutbox() throws Exception {
        int idUsuario = SEQUENCIA.incrementAndGet();
        int idAssento = SEQUENCIA.incrementAndGet();

        ResultActions resultado = reservar(idUsuario, idAssento, "ESTUDANTE", novaChave())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.idEvento").value(1))
                .andExpect(jsonPath("$.idAssento").value(idAssento))
                .andExpect(jsonPath("$.idUsuario").value(idUsuario))
                .andExpect(jsonPath("$.tipoIngresso").value("ESTUDANTE"))
                .andExpect(jsonPath("$.status").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.dataReserva").isNotEmpty())
                .andExpect(jsonPath("$.dataExpiracao").isNotEmpty());

        String idIngresso = extrairId(resultado);
        assertThat(redisTemplate.opsForValue().get("assento:lock:" + idAssento)).isEqualTo(idIngresso);
        assertThat(contarOutbox(idIngresso, "RESERVA_CRIADA")).isEqualTo(1);
    }

    @Test
    void reservar_semIdempotencyKey_retorna400() throws Exception {
        reservar(SEQUENCIA.incrementAndGet(), SEQUENCIA.incrementAndGet(), "INTEIRA", null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservar_mesmaChaveMesmoCorpo_devolveOMesmoIngressoSemDuplicar() throws Exception {
        int idUsuario = SEQUENCIA.incrementAndGet();
        int idAssento = SEQUENCIA.incrementAndGet();
        String chave = novaChave();

        String primeiro = extrairId(reservar(idUsuario, idAssento, "INTEIRA", chave));
        String repetido = extrairId(reservar(idUsuario, idAssento, "INTEIRA", chave));

        assertThat(repetido).isEqualTo(primeiro);
        assertThat(contarIngressosDoUsuario(idUsuario)).isEqualTo(1);
        assertThat(contarOutbox(primeiro, "RESERVA_CRIADA")).isEqualTo(1);
    }

    @Test
    void reservar_mesmaChaveCorpoDiferente_retorna422() throws Exception {
        int idUsuario = SEQUENCIA.incrementAndGet();
        String chave = novaChave();

        reservar(idUsuario, SEQUENCIA.incrementAndGet(), "INTEIRA", chave).andExpect(status().isAccepted());
        reservar(idUsuario, SEQUENCIA.incrementAndGet(), "INTEIRA", chave).andExpect(status().isUnprocessableEntity());

        assertThat(contarIngressosDoUsuario(idUsuario)).isEqualTo(1);
    }

    @Test
    void reservar_mesmaChaveUsuariosDiferentes_saoComprasDistintas() throws Exception {
        String chave = novaChave();

        String deUm = extrairId(reservar(SEQUENCIA.incrementAndGet(), SEQUENCIA.incrementAndGet(), "INTEIRA", chave));
        String deOutro = extrairId(reservar(SEQUENCIA.incrementAndGet(), SEQUENCIA.incrementAndGet(), "INTEIRA", chave));

        assertThat(deOutro).isNotEqualTo(deUm);
    }

    @Test
    void reservar_assentoTravadoPorOutraCompra_retorna409() throws Exception {
        int idAssento = SEQUENCIA.incrementAndGet();
        int idOutroUsuario = SEQUENCIA.incrementAndGet();

        reservar(SEQUENCIA.incrementAndGet(), idAssento, "INTEIRA", novaChave()).andExpect(status().isAccepted());
        reservar(idOutroUsuario, idAssento, "INTEIRA", novaChave()).andExpect(status().isConflict());

        // A tentativa recusada não deixa ingresso no banco.
        assertThat(contarIngressosDoUsuario(idOutroUsuario)).isZero();
    }

    @Test
    void reservar_tipoInvalido_retorna400() throws Exception {
        reservar(SEQUENCIA.incrementAndGet(), SEQUENCIA.incrementAndGet(), "VIP", novaChave())
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservar_gestor_retorna403() throws Exception {
        mockMvc.perform(post("/ingressos")
                .header(HEADER_PERFIL, "GESTOR_EVENTO")
                .header(HEADER_CHAVE, novaChave())
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(SEQUENCIA.incrementAndGet(), "INTEIRA")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ concorrência

    @Test
    void reservar_variosUsuariosSimultaneosNaMesmaCadeira_apenasUmConsegue() throws Exception {
        int idAssento = SEQUENCIA.incrementAndGet();
        List<Integer> statusRecebidos = emParalelo(10, () ->
                reservar(SEQUENCIA.incrementAndGet(), idAssento, "INTEIRA", novaChave())
                        .andReturn().getResponse().getStatus());

        assertThat(statusRecebidos).filteredOn(s -> s == 202).hasSize(1);
        assertThat(statusRecebidos).filteredOn(s -> s == 409).hasSize(9);
        Integer ingressosDaCadeira = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reserva.ingresso WHERE id_assento = ?", Integer.class, idAssento);
        assertThat(ingressosDaCadeira).isEqualTo(1);
    }

    @Test
    void reservar_mesmaRequisicaoRepetidaSimultaneamente_criaUmUnicoIngresso() throws Exception {
        // Ex.: o cliente reenviou o pedido várias vezes. Cada repetição recebe o ingresso (202)
        // ou "em processamento" (409), mas só uma compra é criada.
        int idUsuario = SEQUENCIA.incrementAndGet();
        int idAssento = SEQUENCIA.incrementAndGet();
        String chave = novaChave();

        List<Integer> statusRecebidos = emParalelo(10, () ->
                reservar(idUsuario, idAssento, "INTEIRA", chave).andReturn().getResponse().getStatus());

        assertThat(statusRecebidos).containsOnly(202, 409).contains(202);
        assertThat(contarIngressosDoUsuario(idUsuario)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ consultas

    @Test
    void listarMeus_retornaApenasIngressosDoUsuario() throws Exception {
        int usuarioA = SEQUENCIA.incrementAndGet();
        int usuarioB = SEQUENCIA.incrementAndGet();
        reservar(usuarioA, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()).andExpect(status().isAccepted());
        reservar(usuarioA, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()).andExpect(status().isAccepted());
        reservar(usuarioB, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()).andExpect(status().isAccepted());

        mockMvc.perform(get("/ingressos/meus").header(HEADER_ID, usuarioA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idUsuario").value(usuarioA))
                .andExpect(jsonPath("$[1].idUsuario").value(usuarioA));
    }

    @Test
    void buscarPorId_donoEGestorVeem_outroUsuarioRecebe404() throws Exception {
        int dono = SEQUENCIA.incrementAndGet();
        String idIngresso = extrairId(reservar(dono, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()));

        mockMvc.perform(get("/ingressos/{id}", idIngresso).header(HEADER_ID, dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idIngresso));

        mockMvc.perform(get("/ingressos/{id}", idIngresso).header(HEADER_PERFIL, "GESTOR_EVENTO"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ingressos/{id}", idIngresso).header(HEADER_ID, SEQUENCIA.incrementAndGet()))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscarPorId_inexistente_retorna404() throws Exception {
        mockMvc.perform(get("/ingressos/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ cancelamento

    @Test
    void cancelar_peloDono_cancelaLiberaTravaEGravaOutbox() throws Exception {
        int dono = SEQUENCIA.incrementAndGet();
        int idAssento = SEQUENCIA.incrementAndGet();
        String idIngresso = extrairId(reservar(dono, idAssento, "INTEIRA", novaChave()));

        cancelar(idIngresso, dono, novaChave())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(redisTemplate.hasKey("assento:lock:" + idAssento)).isFalse();
        assertThat(contarOutbox(idIngresso, "RESERVA_CANCELADA")).isEqualTo(1);

        // Com a trava liberada, outro usuário consegue reservar o mesmo assento.
        reservar(SEQUENCIA.incrementAndGet(), idAssento, "INTEIRA", novaChave()).andExpect(status().isAccepted());
    }

    @Test
    void cancelar_mesmaChaveRepetida_retorna200SemAplicarDeNovo() throws Exception {
        int dono = SEQUENCIA.incrementAndGet();
        String idIngresso = extrairId(reservar(dono, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()));
        String chave = novaChave();

        cancelar(idIngresso, dono, chave).andExpect(status().isOk());
        cancelar(idIngresso, dono, chave)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        assertThat(contarOutbox(idIngresso, "RESERVA_CANCELADA")).isEqualTo(1);
    }

    @Test
    void cancelar_jaCanceladoComOutraChave_retorna409() throws Exception {
        int dono = SEQUENCIA.incrementAndGet();
        String idIngresso = extrairId(reservar(dono, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()));

        cancelar(idIngresso, dono, novaChave()).andExpect(status().isOk());
        cancelar(idIngresso, dono, novaChave()).andExpect(status().isConflict());
    }

    @Test
    void cancelar_semIdempotencyKey_retorna400() throws Exception {
        int dono = SEQUENCIA.incrementAndGet();
        String idIngresso = extrairId(reservar(dono, SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()));

        cancelar(idIngresso, dono, null).andExpect(status().isBadRequest());
    }

    @Test
    void cancelar_outroUsuarioRecebe404_gestorConsegueCancelar() throws Exception {
        String idIngresso = extrairId(
                reservar(SEQUENCIA.incrementAndGet(), SEQUENCIA.incrementAndGet(), "INTEIRA", novaChave()));

        cancelar(idIngresso, SEQUENCIA.incrementAndGet(), novaChave()).andExpect(status().isNotFound());

        mockMvc.perform(post("/ingressos/{id}/cancelamento", idIngresso)
                .header(HEADER_PERFIL, "GESTOR_EVENTO")
                .header(HEADER_CHAVE, novaChave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions reservar(int idUsuario, int idAssento, String tipoIngresso, String chave) throws Exception {
        var requisicao = post("/ingressos")
                .header(HEADER_ID, idUsuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(idAssento, tipoIngresso));
        if (chave != null) {
            requisicao.header(HEADER_CHAVE, chave);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions cancelar(String idIngresso, int idUsuario, String chave) throws Exception {
        var requisicao = post("/ingressos/{id}/cancelamento", idIngresso).header(HEADER_ID, idUsuario);
        if (chave != null) {
            requisicao.header(HEADER_CHAVE, chave);
        }
        return mockMvc.perform(requisicao);
    }

    private static String novaChave() {
        return UUID.randomUUID().toString();
    }

    private static String corpo(int idAssento, String tipoIngresso) {
        return "{\"idEvento\":1,\"idAssento\":" + idAssento + ",\"tipoIngresso\":\"" + tipoIngresso + "\"}";
    }

    private static String extrairId(ResultActions resultado) throws Exception {
        String json = resultado.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    // Dispara as tarefas ao mesmo tempo (todas esperam um sinal de largada) e devolve os resultados.
    private static <T> List<T> emParalelo(int quantidade, Callable<T> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            executor.shutdown();
        }
    }

    private int contarIngressosDoUsuario(int idUsuario) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reserva.ingresso WHERE id_usuario = ?", Integer.class, idUsuario);
        return total == null ? 0 : total;
    }

    private int contarOutbox(String idIngresso, String tipoEvento) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reserva.outbox_event WHERE agregado_id = ? AND tipo_evento = ?",
                Integer.class, idIngresso, tipoEvento);
        return total == null ? 0 : total;
    }
}
