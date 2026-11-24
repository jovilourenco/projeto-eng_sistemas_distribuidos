package br.engsistemasdistribuidos.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
 * Testes de integração das rotas /reservas e /ingressos: aplicação completa + PostgreSQL e Redis reais
 * (Testcontainers). Requer o Docker em execução. Cada teste usa assentos, usuários e chaves próprios,
 * então os testes não dependem uns dos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservaIntegrationTest {

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
    void reservar_quatroAssentos_retorna201TravaTodosEGravaUmComandoNoOutbox() throws Exception {
        int usuario = novoId();
        List<Integer> assentos = novosAssentos(4);

        String id = extrairId(reservar(usuario, invertida(assentos), null, novaChave())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEvento").value(1))
                .andExpect(jsonPath("$.idUsuario").value(usuario))
                .andExpect(jsonPath("$.status").value("RESERVADA"))
                .andExpect(jsonPath("$.estado").value("INICIADA"))
                .andExpect(jsonPath("$.valorTotal").doesNotExist())
                .andExpect(jsonPath("$.pagamentoSolicitado").value(false))
                .andExpect(jsonPath("$.dataExpiracao").isNotEmpty())
                .andExpect(jsonPath("$.ingressos.length()").value(4))
                .andExpect(jsonPath("$.ingressos[*].idAssento").value(org.hamcrest.Matchers.contains(
                        assentos.toArray())))
                .andExpect(jsonPath("$.ingressos[0].tipoIngresso").value("INTEIRA")));

        for (int assento : assentos) {
            assertThat(redisTemplate.opsForValue().get("assento:trava:" + assento)).isEqualTo(id);
        }
        List<String> comandos = comandosNoOutbox(id, "assento.reservar");
        assertThat(comandos).hasSize(1);
        assertThat(JsonPath.<String>read(comandos.get(0), "$.idCompra")).isEqualTo(id);
        assertThat(JsonPath.<Integer>read(comandos.get(0), "$.idEvento")).isEqualTo(1);
        assertThat(JsonPath.<List<Integer>>read(comandos.get(0), "$.idAssentos")).containsExactlyElementsOf(assentos);
        assertThat(JsonPath.<String>read(comandos.get(0), "$.idMensagem")).isNotBlank();
    }

    @Test
    void reservar_semIdempotencyKey_retorna400() throws Exception {
        reservar(novoId(), novosAssentos(1), null, null).andExpect(status().isBadRequest());
    }

    @Test
    void reservar_quantidadeDeAssentosInvalida_retorna400() throws Exception {
        int usuario = novoId();
        reservar(usuario, List.of(), null, novaChave()).andExpect(status().isBadRequest());
        reservar(usuario, novosAssentos(5), null, novaChave()).andExpect(status().isBadRequest());
        int repetido = novoId();
        reservar(usuario, List.of(repetido, repetido), null, novaChave()).andExpect(status().isBadRequest());
        mockMvc.perform(post("/reservas").header(HEADER_ID, usuario).header(HEADER_CHAVE, novaChave())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idAssentos\":[1]}"))
                .andExpect(status().isBadRequest());
        assertThat(contarReservasDoUsuario(usuario)).isZero();
    }

    @Test
    void reservar_travaQueSobrouDaPropriaCompra_naoBloqueiaARepeticao() throws Exception {
        // Ex.: o serviço caiu depois de travar e antes de gravar. A repetição com a mesma chave grava normalmente.
        int usuario = novoId();
        List<Integer> assentos = novosAssentos(2);
        String chave = novaChave();
        String idCompra = br.engsistemasdistribuidos.reserva.service.impl.ReservaServiceImpl
                .derivarIdCompra(usuario, chave).toString();
        redisTemplate.opsForValue().set("assento:trava:" + assentos.get(0), idCompra);

        reservar(usuario, assentos, null, chave).andExpect(status().isCreated());
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(1))).isEqualTo(idCompra);
    }

    @Test
    void erros_vemComAMensagemNoCampoDetail() throws Exception {
        reservar(novoId(), novosAssentos(5), null, novaChave())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Informe de 1 a 4 assentos."));
    }

    @Test
    void reservar_tipoInvalido_retorna400() throws Exception {
        reservar(novoId(), novosAssentos(1), "VIP", novaChave()).andExpect(status().isBadRequest());
    }

    @Test
    void reservar_mesmaChaveMesmoCorpo_devolveAMesmaReservaCom200SemDuplicar() throws Exception {
        int usuario = novoId();
        List<Integer> assentos = novosAssentos(2);
        String chave = novaChave();

        String primeira = extrairId(reservar(usuario, assentos, "ESTUDANTE", chave).andExpect(status().isCreated()));
        // Mesmos assentos em outra ordem: é a mesma requisição.
        String repetida = extrairId(reservar(usuario, invertida(assentos), "ESTUDANTE", chave)
                .andExpect(status().isOk()));

        assertThat(repetida).isEqualTo(primeira);
        assertThat(contarReservasDoUsuario(usuario)).isEqualTo(1);
        assertThat(comandosNoOutbox(primeira, "assento.reservar")).hasSize(1);
    }

    @Test
    void reservar_mesmaChaveCorpoDiferente_retorna422() throws Exception {
        int usuario = novoId();
        String chave = novaChave();

        reservar(usuario, novosAssentos(1), null, chave).andExpect(status().isCreated());
        reservar(usuario, novosAssentos(1), null, chave).andExpect(status().isUnprocessableEntity());

        assertThat(contarReservasDoUsuario(usuario)).isEqualTo(1);
    }

    @Test
    void reservar_mesmaChaveUsuariosDiferentes_saoComprasDistintas() throws Exception {
        String chave = novaChave();

        String a = extrairId(reservar(novoId(), novosAssentos(1), null, chave).andExpect(status().isCreated()));
        String b = extrairId(reservar(novoId(), novosAssentos(1), null, chave).andExpect(status().isCreated()));

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void reservar_umDosQuatroAssentosEmDisputa_retorna409ENaoReservaNemTravaNenhum() throws Exception {
        List<Integer> assentos = novosAssentos(4);
        String dona = extrairId(reservar(novoId(), List.of(assentos.get(2)), null, novaChave())
                .andExpect(status().isCreated()));
        int usuario = novoId();

        reservar(usuario, assentos, null, novaChave()).andExpect(status().isConflict());

        assertThat(contarReservasDoUsuario(usuario)).isZero();
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(0))).isNull();
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(1))).isNull();
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(2))).isEqualTo(dona);
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(3))).isNull();
    }

    @Test
    void reservar_gestor_retorna403() throws Exception {
        mockMvc.perform(post("/reservas")
                        .header(HEADER_PERFIL, "GESTOR_EVENTO")
                        .header(HEADER_CHAVE, novaChave())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(novosAssentos(1), null)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ concorrência

    @Test
    void reservar_corrida500ClientesPor50Assentos_nenhumAssentoEmDuasReservas() throws Exception {
        // Cenário "corrida" do kit: 500 clientes disputam 50 assentos, cada um pedindo de 1 a 4 assentos vizinhos.
        List<Integer> assentos = novosAssentos(50);
        List<Integer> usuarios = new ArrayList<>();
        List<List<Integer>> pedidos = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            int inicio = i % 50;
            int quantidade = 1 + i % 4;
            usuarios.add(novoId());
            pedidos.add(assentos.subList(inicio, Math.min(50, inicio + quantidade)));
        }
        AtomicInteger proximo = new AtomicInteger();

        List<Integer> codigos = emParalelo(500, 32, () -> {
            int i = proximo.getAndIncrement();
            return reservar(usuarios.get(i), pedidos.get(i), null, novaChave()).andReturn().getResponse().getStatus();
        });

        assertThat(codigos).isSubsetOf(201, 409).contains(201, 409);
        // Cada assento está em no máximo uma reserva, e cada reserva criada tem a trava de todos os seus assentos.
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList("""
                SELECT i.id_assento, r.id_reserva::text AS id_reserva
                  FROM reserva.ingresso i JOIN reserva.reserva r ON r.id_reserva = i.id_reserva
                 WHERE i.id_assento = ANY (?)
                """, (Object) assentos.toArray(Integer[]::new));
        List<Integer> reservados = linhas.stream().map(l -> (Integer) l.get("id_assento")).toList();
        assertThat(reservados).doesNotHaveDuplicates();
        for (Map<String, Object> linha : linhas) {
            assertThat(redisTemplate.opsForValue().get("assento:trava:" + linha.get("id_assento")))
                    .isEqualTo(linha.get("id_reserva"));
        }
        Set<Object> reservas = linhas.stream().map(l -> l.get("id_reserva")).collect(Collectors.toSet());
        assertThat(codigos).filteredOn(c -> c == 201).hasSize(reservas.size());
    }

    @Test
    void reservar_mesmaRequisicaoRepetidaSimultaneamente_criaUmaUnicaReserva() throws Exception {
        // Cenário "idempotência" do kit: o cliente reenvia o pedido em paralelo. Todas as repetições recebem a
        // mesma reserva (201 a primeira, 200 as demais), nunca 409, e só uma compra é criada.
        int usuario = novoId();
        List<Integer> assentos = novosAssentos(3);
        String chave = novaChave();

        List<Integer> codigos = emParalelo(10, 10, () ->
                reservar(usuario, assentos, null, chave).andReturn().getResponse().getStatus());

        assertThat(codigos).isSubsetOf(200, 201);
        assertThat(codigos).filteredOn(c -> c == 201).hasSize(1);
        assertThat(contarReservasDoUsuario(usuario)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ pagamento

    @Test
    void pagar_registraOPedidoERetorna202_repeticaoDevolveOMesmoEstado() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(2));
        String chave = novaChave();

        // Valores em minúsculas, como no contrato do kit.
        pagar(id, usuario, "pix", "aprovar", chave)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAGAMENTO_EM_PROCESSAMENTO"))
                .andExpect(jsonPath("$.pagamentoSolicitado").value(true));
        pagar(id, usuario, "pix", "aprovar", chave).andExpect(status().isAccepted());

        Map<String, Object> reserva = lerReserva(id);
        assertThat(reserva.get("chave_pagamento")).isEqualTo(chave);
        assertThat(reserva.get("metodo_pagamento")).isEqualTo("PIX");
        assertThat(reserva.get("simulacao_pagamento")).isEqualTo("APROVAR");
        assertThat(reserva.get("estado")).isEqualTo("INICIADA");
    }

    @Test
    void pagar_mesmaChaveCorpoDiferente_retorna422() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));
        String chave = novaChave();

        pagar(id, usuario, "PIX", "APROVAR", chave).andExpect(status().isAccepted());
        pagar(id, usuario, "PIX", "RECUSAR", chave).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pagar_outraChaveComPedidoEmAberto_retorna202SemSubstituirOPedido() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));
        String primeira = novaChave();

        pagar(id, usuario, "PIX", "APROVAR", primeira).andExpect(status().isAccepted());
        pagar(id, usuario, "BOLETO", "RECUSAR", novaChave()).andExpect(status().isAccepted());

        Map<String, Object> reserva = lerReserva(id);
        assertThat(reserva.get("chave_pagamento")).isEqualTo(primeira);
        assertThat(reserva.get("simulacao_pagamento")).isEqualTo("APROVAR");
    }

    @Test
    void pagar_validacoes_retornam400_403e404() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));

        pagar(id, usuario, "PIX", null, null).andExpect(status().isBadRequest());
        pagar(id, usuario, null, "APROVAR", novaChave()).andExpect(status().isBadRequest());
        pagar(id, usuario, "DINHEIRO", null, novaChave()).andExpect(status().isBadRequest());
        pagar(id, novoId(), "PIX", null, novaChave()).andExpect(status().isNotFound());
        mockMvc.perform(post("/reservas/{id}/pagamento", id)
                        .header(HEADER_PERFIL, "GESTOR_EVENTO").header(HEADER_CHAVE, novaChave())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"metodo\":\"PIX\"}"))
                .andExpect(status().isForbidden());
        assertThat(lerReserva(id).get("chave_pagamento")).isNull();
    }

    @Test
    void pagar_prazoVencido_retorna409() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));
        jdbcTemplate.update("""
                UPDATE reserva.reserva SET data_reserva = now() - interval '20 minutes',
                       data_expiracao = now() - interval '1 second' WHERE id_reserva = ?::uuid
                """, id);

        pagar(id, usuario, "PIX", "APROVAR", novaChave()).andExpect(status().isConflict());
        assertThat(lerReserva(id).get("chave_pagamento")).isNull();
    }

    @Test
    void pagar_reservaCanceladaOuJaPaga_retorna409() throws Exception {
        int usuario = novoId();
        String cancelada = criarReserva(usuario, novosAssentos(1));
        cancelar(cancelada, usuario, novaChave()).andExpect(status().isOk());
        String paga = criarReserva(usuario, novosAssentos(1));
        mudarEstado(paga, "CONCLUIDA", null);

        pagar(cancelada, usuario, "PIX", null, novaChave()).andExpect(status().isConflict());
        pagar(paga, usuario, "PIX", null, novaChave()).andExpect(status().isConflict());
    }

    @Test
    void pagar_repeticaoDepoisDoResultado_devolveOResultadoFinal() throws Exception {
        // O orquestrador (issue #50) leva a compra até o fim; a repetição do pedido recebe o resultado.
        int usuario = novoId();
        String confirmada = criarReserva(usuario, novosAssentos(1));
        String recusada = criarReserva(usuario, novosAssentos(1));
        String expirada = criarReserva(usuario, novosAssentos(1));
        String chave = novaChave();
        pagar(confirmada, usuario, "PIX", "APROVAR", chave).andExpect(status().isAccepted());
        pagar(recusada, usuario, "PIX", "RECUSAR", chave).andExpect(status().isAccepted());
        pagar(expirada, usuario, "PIX", "ATRASAR", chave).andExpect(status().isAccepted());
        mudarEstado(confirmada, "CONCLUIDA", null);
        mudarEstado(recusada, "CANCELADA", "PAGAMENTO_RECUSADO");
        mudarEstado(expirada, "CANCELADA", "PRAZO_VENCIDO");

        pagar(confirmada, usuario, "PIX", "APROVAR", chave)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMADA"));
        pagar(recusada, usuario, "PIX", "RECUSAR", chave)
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.status").value("RECUSADA"));
        pagar(expirada, usuario, "PIX", "ATRASAR", chave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value("EXPIRADA"));
    }

    // ------------------------------------------------------------------ cancelamento

    @Test
    void cancelar_peloDono_vaiParaCompensandoEPedeALiberacaoDosAssentos() throws Exception {
        int usuario = novoId();
        List<Integer> assentos = novosAssentos(2);
        String id = criarReserva(usuario, assentos);

        cancelar(id, usuario, novaChave())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPENSANDO"))
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelamento").value("CANCELADA_PELO_USUARIO"));

        List<String> comandos = comandosNoOutbox(id, "assento.liberar");
        assertThat(comandos).hasSize(1);
        assertThat(JsonPath.<List<Integer>>read(comandos.get(0), "$.idAssentos")).containsExactlyElementsOf(assentos);
        // A trava só sai quando a liberação for confirmada (CANCELADA), pelo orquestrador (ADR-002).
        assertThat(redisTemplate.opsForValue().get("assento:trava:" + assentos.get(0))).isEqualTo(id);
    }

    @Test
    void cancelar_mesmaChaveRepetida_retorna200SemAplicarDeNovo_outraChaveRetorna409() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));
        String chave = novaChave();

        cancelar(id, usuario, chave).andExpect(status().isOk());
        cancelar(id, usuario, chave).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPENSANDO"));
        cancelar(id, usuario, novaChave()).andExpect(status().isConflict());

        assertThat(comandosNoOutbox(id, "assento.liberar")).hasSize(1);
    }

    @Test
    void cancelar_comPagamentoSolicitado_retorna409() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));
        pagar(id, usuario, "PIX", "APROVAR", novaChave()).andExpect(status().isAccepted());

        cancelar(id, usuario, novaChave()).andExpect(status().isConflict());

        assertThat(lerReserva(id).get("estado")).isEqualTo("INICIADA");
        assertThat(comandosNoOutbox(id, "assento.liberar")).isEmpty();
    }

    @Test
    void pagarECancelarAoMesmoTempo_apenasUmVence() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            int usuario = novoId();
            String id = criarReserva(usuario, novosAssentos(1));
            AtomicInteger vez = new AtomicInteger();

            List<Integer> codigos = emParalelo(2, 2, () -> vez.getAndIncrement() == 0
                    ? pagar(id, usuario, "PIX", "APROVAR", novaChave()).andReturn().getResponse().getStatus()
                    : cancelar(id, usuario, novaChave()).andReturn().getResponse().getStatus());

            Map<String, Object> reserva = lerReserva(id);
            boolean pagou = reserva.get("chave_pagamento") != null;
            boolean cancelou = "COMPENSANDO".equals(reserva.get("estado"));
            assertThat(pagou ^ cancelou).as("rodada %d: %s, códigos %s", rodada, reserva, codigos).isTrue();
            assertThat(codigos).contains(409);
        }
    }

    @Test
    void cancelar_semChave_retorna400_outroUsuario404_gestorConsegue() throws Exception {
        int usuario = novoId();
        String id = criarReserva(usuario, novosAssentos(1));

        cancelar(id, usuario, null).andExpect(status().isBadRequest());
        cancelar(id, novoId(), novaChave()).andExpect(status().isNotFound());
        mockMvc.perform(post("/reservas/{id}/cancelamento", id)
                        .header(HEADER_PERFIL, "GESTOR_EVENTO").header(HEADER_CHAVE, novaChave()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPENSANDO"));
    }

    // ------------------------------------------------------------------ consultas

    @Test
    void buscarPorId_donoEGestorVeem_outroUsuarioEInexistenteRecebem404() throws Exception {
        int dono = novoId();
        String id = criarReserva(dono, novosAssentos(2));

        mockMvc.perform(get("/reservas/{id}", id).header(HEADER_ID, dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ingressos.length()").value(2));
        mockMvc.perform(get("/reservas/{id}", id).header(HEADER_PERFIL, "GESTOR_EVENTO"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reservas/{id}", id).header(HEADER_ID, novoId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/reservas/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void ingressos_listaOsDoUsuarioComStatusDerivadoDaReserva() throws Exception {
        int usuario = novoId();
        String ativa = criarReserva(usuario, novosAssentos(2));
        String cancelada = criarReserva(usuario, novosAssentos(1));
        cancelar(cancelada, usuario, novaChave()).andExpect(status().isOk());
        criarReserva(novoId(), novosAssentos(1));

        String json = mockMvc.perform(get("/ingressos/meus").header(HEADER_ID, usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> ingressos = JsonPath.read(json, "$");
        assertThat(ingressos).allSatisfy(i -> assertThat(i.get("idUsuario")).isEqualTo(usuario));
        assertThat(ingressos).filteredOn(i -> ativa.equals(i.get("idReserva")))
                .hasSize(2).allSatisfy(i -> assertThat(i.get("status")).isEqualTo("PENDENTE_PAGAMENTO"));
        assertThat(ingressos).filteredOn(i -> cancelada.equals(i.get("idReserva")))
                .singleElement().satisfies(i -> assertThat(i.get("status")).isEqualTo("CANCELADO"));

        String idIngresso = (String) ingressos.get(0).get("id");
        mockMvc.perform(get("/ingressos/{id}", idIngresso).header(HEADER_ID, usuario)).andExpect(status().isOk());
        mockMvc.perform(get("/ingressos/{id}", idIngresso).header(HEADER_ID, novoId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/ingressos/meus").header(HEADER_PERFIL, "GESTOR_EVENTO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rotaAntigaDeCompraPorIngresso_naoExisteMais() throws Exception {
        mockMvc.perform(post("/ingressos").header(HEADER_CHAVE, novaChave())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions reservar(int usuario, List<Integer> assentos, String tipo, String chave) throws Exception {
        var requisicao = post("/reservas")
                .header(HEADER_ID, usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(assentos, tipo));
        if (chave != null) {
            requisicao.header(HEADER_CHAVE, chave);
        }
        return mockMvc.perform(requisicao);
    }

    private String criarReserva(int usuario, List<Integer> assentos) throws Exception {
        return extrairId(reservar(usuario, assentos, null, novaChave()).andExpect(status().isCreated()));
    }

    private ResultActions pagar(String id, int usuario, String metodo, String simulacao, String chave)
            throws Exception {
        String corpo = "{" + (metodo == null ? "" : "\"metodo\":\"" + metodo + "\"")
                + (simulacao == null ? "" : (metodo == null ? "" : ",") + "\"simulacao\":\"" + simulacao + "\"") + "}";
        var requisicao = post("/reservas/{id}/pagamento", id)
                .header(HEADER_ID, usuario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo);
        if (chave != null) {
            requisicao.header(HEADER_CHAVE, chave);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions cancelar(String id, int usuario, String chave) throws Exception {
        var requisicao = post("/reservas/{id}/cancelamento", id).header(HEADER_ID, usuario);
        if (chave != null) {
            requisicao.header(HEADER_CHAVE, chave);
        }
        return mockMvc.perform(requisicao);
    }

    // Simula o avanço da SAGA, que é responsabilidade do orquestrador (issue #50).
    private void mudarEstado(String id, String estado, String motivo) {
        jdbcTemplate.update("UPDATE reserva.reserva SET estado = ?, motivo_cancelamento = ? WHERE id_reserva = ?::uuid",
                estado, motivo, id);
    }

    private static int novoId() {
        return SEQUENCIA.incrementAndGet();
    }

    private static List<Integer> novosAssentos(int quantidade) {
        List<Integer> assentos = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            assentos.add(novoId());
        }
        return assentos;
    }

    // List.reversed() só existe a partir do Java 21.
    private static List<Integer> invertida(List<Integer> lista) {
        List<Integer> copia = new ArrayList<>(lista);
        java.util.Collections.reverse(copia);
        return copia;
    }

    private static String novaChave() {
        return UUID.randomUUID().toString();
    }

    private static String corpo(List<Integer> assentos, String tipo) {
        String lista = assentos.stream().map(String::valueOf).collect(Collectors.joining(","));
        return "{\"idEvento\":1,\"idAssentos\":[" + lista + "]"
                + (tipo == null ? "" : ",\"tipoIngresso\":\"" + tipo + "\"") + "}";
    }

    private static String extrairId(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    // Dispara as tarefas ao mesmo tempo (todas esperam um sinal de largada) e devolve os resultados.
    private static <T> List<T> emParalelo(int quantidade, int threads, Callable<T> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
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
                resultados.add(futuro.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, Object> lerReserva(String id) {
        return jdbcTemplate.queryForMap("SELECT * FROM reserva.reserva WHERE id_reserva = ?::uuid", id);
    }

    private int contarReservasDoUsuario(int usuario) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reserva.reserva WHERE id_usuario = ?", Integer.class, usuario);
        return total == null ? 0 : total;
    }

    private List<String> comandosNoOutbox(String id, String tipo) {
        return jdbcTemplate.queryForList(
                "SELECT payload FROM reserva.outbox_event WHERE agregado_id = ? AND tipo_evento = ?",
                String.class, id, tipo);
    }
}
