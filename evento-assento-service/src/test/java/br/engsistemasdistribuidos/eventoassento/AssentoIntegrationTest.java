package br.engsistemasdistribuidos.eventoassento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

/**
 * Testes de integração dos endpoints de assentos: aplicação completa + PostgreSQL real (Testcontainers).
 * Requer o Docker em execução. Cada teste cria o próprio evento, então não dependem uns dos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AssentoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cadastrarEmLote_criaAssentosDisponiveis() throws Exception {
        int idEvento = criarEvento();

        mockMvc.perform(post("/eventos/{idEvento}/assentos", idEvento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":3,\"tipoAssento\":\"VIP\",\"valor\":250.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].idEvento").value(idEvento))
                .andExpect(jsonPath("$[0].tipoAssento").value("VIP"))
                .andExpect(jsonPath("$[0].status").value("DISPONIVEL"))
                .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    void cadastrarEmLote_quantidadeInvalida_retorna400() throws Exception {
        int idEvento = criarEvento();

        mockMvc.perform(post("/eventos/{idEvento}/assentos", idEvento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":0,\"tipoAssento\":\"VIP\",\"valor\":100.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrarEmLote_eventoInexistente_retorna404() throws Exception {
        mockMvc.perform(post("/eventos/{idEvento}/assentos", 999999)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":1,\"tipoAssento\":\"VIP\",\"valor\":100.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_filtraPorStatus() throws Exception {
        int idEvento = criarEvento();
        List<Integer> ids = criarLote(idEvento, 3);
        marcarComoReservado(ids.get(0));

        mockMvc.perform(get("/eventos/{idEvento}/assentos", idEvento))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/eventos/{idEvento}/assentos", idEvento).param("status", "DISPONIVEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/eventos/{idEvento}/assentos", idEvento).param("status", "RESERVADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void buscarPorId_assentoDeOutroEvento_retorna404() throws Exception {
        int idEventoA = criarEvento();
        int idEventoB = criarEvento();
        int idAssentoDoA = criarLote(idEventoA, 1).get(0);

        mockMvc.perform(get("/eventos/{idEvento}/assentos/{idAssento}", idEventoA, idAssentoDoA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/eventos/{idEvento}/assentos/{idAssento}", idEventoB, idAssentoDoA))
                .andExpect(status().isNotFound());
    }

    @Test
    void atualizar_assentoDisponivel_alteraSoOsCamposInformados() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarLote(idEvento, 1).get(0);

        mockMvc.perform(put("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(300.0))
                .andExpect(jsonPath("$.tipoAssento").value("VIP"))
                .andExpect(jsonPath("$.ativo").value(true));

        mockMvc.perform(put("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.valor").value(300.0));
    }

    @Test
    void atualizar_assentoReservado_retorna409() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarLote(idEvento, 1).get(0);
        marcarComoReservado(idAssento);

        mockMvc.perform(put("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"valor\":1.00}"))
                .andExpect(status().isConflict());
    }

    @Test
    void excluir_assentoDisponivel_retorna204_eDepoisNaoExiste() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarLote(idEvento, 1).get(0);

        mockMvc.perform(delete("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluir_assentoReservado_retorna409() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarLote(idEvento, 1).get(0);
        marcarComoReservado(idAssento);

        mockMvc.perform(delete("/eventos/{idEvento}/assentos/{idAssento}", idEvento, idAssento))
                .andExpect(status().isConflict());
    }

    // ---- Apoio aos testes ----

    private int criarEvento() throws Exception {
        String json = "{\"titulo\":\"Evento de Teste\",\"descricao\":\"Descricao do evento\","
                + "\"dataEvento\":\"2026-12-20T20:00:00\",\"tipoEvento\":{\"id\":4}}";

        MvcResult result = mockMvc.perform(post("/eventos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private List<Integer> criarLote(int idEvento, int quantidade) throws Exception {
        String json = "{\"quantidade\":" + quantidade + ",\"tipoAssento\":\"VIP\",\"valor\":250.00}";

        MvcResult result = mockMvc.perform(post("/eventos/{idEvento}/assentos", idEvento)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
    }

    // Ainda não existe o serviço de reservas: simula direto no banco.
    private void marcarComoReservado(int idAssento) {
        jdbcTemplate.update("UPDATE evento_assento.assento SET status = 'RESERVADO' WHERE id_assento = ?", idAssento);
    }
}
