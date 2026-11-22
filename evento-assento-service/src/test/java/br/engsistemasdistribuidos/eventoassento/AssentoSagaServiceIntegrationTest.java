package br.engsistemasdistribuidos.eventoassento;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.engsistemasdistribuidos.eventoassento.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.RespostaAssento;
import br.engsistemasdistribuidos.eventoassento.model.Assento;
import br.engsistemasdistribuidos.eventoassento.repository.AssentoRepository;
import br.engsistemasdistribuidos.eventoassento.service.AssentoSagaService;

/**
 * Regras das transições de status da SAGA (ADR-003): cada comando, a idempotência, comandos de outra
 * compra, comandos fora de ordem e a disputa simultânea pelo mesmo assento.
 * Cada teste cria o próprio evento e assento, então os testes não dependem uns dos outros.
 */
class AssentoSagaServiceIntegrationTest extends IntegracaoComContainers {

    @Autowired
    private AssentoSagaService service;

    @Autowired
    private AssentoRepository repository;

    // ---- assento.reservar ----

    @Test
    void reservar_assentoDisponivel_reservaParaACompraEInformaOValor() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        RespostaAssento resposta = service.reservar(comando(compra, idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(RESERVADO);
        assertThat(resposta.valor()).isEqualByComparingTo(VALOR_PADRAO);
        Map<String, Object> assento = lerAssento(idAssento);
        assertThat(assento.get("status")).isEqualTo("RESERVADO");
        assertThat(assento.get("id_compra")).isEqualTo(compra);
        assertThat(assento.get("reservado_em")).isNotNull();
    }

    @Test
    void reservar_repetidoPelaMesmaCompra_respondeReservadoDeNovo() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        service.reservar(comando(compra, idEvento, idAssento));
        RespostaAssento repetida = service.reservar(comando(compra, idEvento, idAssento));

        assertThat(repetida.tipo()).isEqualTo(RESERVADO);
        assertThat(lerAssento(idAssento).get("id_compra")).isEqualTo(compra);
    }

    @Test
    void reservar_assentoDeOutraCompra_respondeIndisponivelEMantemODono() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID dona = UUID.randomUUID();
        service.reservar(comando(dona, idEvento, idAssento));

        RespostaAssento resposta = service.reservar(comando(UUID.randomUUID(), idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(INDISPONIVEL);
        assertThat(lerAssento(idAssento).get("id_compra")).isEqualTo(dona);
    }

    @Test
    void reservar_assentoInativo_respondeIndisponivel() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        jdbcTemplate.update("UPDATE evento_assento.assento SET ativo = false WHERE id_assento = ?", idAssento);

        RespostaAssento resposta = service.reservar(comando(UUID.randomUUID(), idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(INDISPONIVEL);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void reservar_eventoErrado_respondeIndisponivel() {
        int idEvento = criarEvento();
        int outroEvento = criarEvento();
        int idAssento = criarAssento(idEvento);

        RespostaAssento resposta = service.reservar(comando(UUID.randomUUID(), outroEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(INDISPONIVEL);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void reservar_assentoInexistente_respondeIndisponivel() {
        int idEvento = criarEvento();

        RespostaAssento resposta = service.reservar(comando(UUID.randomUUID(), idEvento, 999_999));

        assertThat(resposta.tipo()).isEqualTo(INDISPONIVEL);
    }

    // ---- assento.vender ----

    @Test
    void vender_assentoReservadoPelaCompra_vendeEERepetirRespondeVendido() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, idAssento));

        RespostaAssento primeira = service.vender(comando(compra, idEvento, idAssento));
        RespostaAssento repetida = service.vender(comando(compra, idEvento, idAssento));

        assertThat(primeira.tipo()).isEqualTo(VENDIDO);
        assertThat(repetida.tipo()).isEqualTo(VENDIDO);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("VENDIDO");
    }

    @Test
    void vender_assentoDeOutraCompra_respondeVendaRecusada() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        service.reservar(comando(UUID.randomUUID(), idEvento, idAssento));

        RespostaAssento resposta = service.vender(comando(UUID.randomUUID(), idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(VENDA_RECUSADA);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("RESERVADO");
    }

    @Test
    void vender_assentoSemReserva_respondeVendaRecusada() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);

        RespostaAssento resposta = service.vender(comando(UUID.randomUUID(), idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(VENDA_RECUSADA);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    // ---- assento.liberar ----

    @Test
    void liberar_assentoReservadoPelaCompra_voltaParaDisponivelELimpaODono() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, idAssento));

        RespostaAssento resposta = service.liberar(comando(compra, idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(LIBERADO);
        Map<String, Object> assento = lerAssento(idAssento);
        assertThat(assento.get("status")).isEqualTo("DISPONIVEL");
        assertThat(assento.get("id_compra")).isNull();
        assertThat(assento.get("reservado_em")).isNull();
    }

    @Test
    void liberar_repetido_respondeLiberadoDeNovo() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, idAssento));
        service.liberar(comando(compra, idEvento, idAssento));

        RespostaAssento repetida = service.liberar(comando(compra, idEvento, idAssento));

        assertThat(repetida.tipo()).isEqualTo(LIBERADO);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void liberar_atrasadoDeOutraCompra_naoLiberaOAssentoDoDonoAtual() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID antiga = UUID.randomUUID();
        UUID atual = UUID.randomUUID();
        service.reservar(comando(antiga, idEvento, idAssento));
        service.liberar(comando(antiga, idEvento, idAssento));
        service.reservar(comando(atual, idEvento, idAssento));

        // Um liberar repetido da compra antiga chega depois que outra compra reservou o assento.
        RespostaAssento resposta = service.liberar(comando(antiga, idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(LIBERADO);
        Map<String, Object> assento = lerAssento(idAssento);
        assertThat(assento.get("status")).isEqualTo("RESERVADO");
        assertThat(assento.get("id_compra")).isEqualTo(atual);
    }

    @Test
    void liberar_assentoJaVendidoParaACompra_respondeLiberacaoRecusadaEMantemAVenda() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, idAssento));
        service.vender(comando(compra, idEvento, idAssento));

        RespostaAssento resposta = service.liberar(comando(compra, idEvento, idAssento));

        assertThat(resposta.tipo()).isEqualTo(LIBERACAO_RECUSADA);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("VENDIDO");
    }

    @Test
    void liberar_processadoAntesDoReservar_reservarAindaAplicaEOrquestradorCorrige() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        // Fora de ordem: o liberar chega antes do reservar da mesma compra.
        RespostaAssento liberar = service.liberar(comando(compra, idEvento, idAssento));
        RespostaAssento reservar = service.reservar(comando(compra, idEvento, idAssento));

        // As duas respostas chegam ao orquestrador, que reenvia o liberar ao ver o reservado atrasado (ADR-002).
        assertThat(liberar.tipo()).isEqualTo(LIBERADO);
        assertThat(reservar.tipo()).isEqualTo(RESERVADO);
        assertThat(service.liberar(comando(compra, idEvento, idAssento)).tipo()).isEqualTo(LIBERADO);
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    // ---- Concorrência e proteção contra sobrescrita ----

    @Test
    void reservar_disputaSimultaneaPorUmAssento_apenasUmaCompraReserva() throws Exception {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        int compras = 50;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<RespostaAssento>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < compras; i++) {
                Callable<RespostaAssento> tentativa = () -> {
                    largada.await();
                    return service.reservar(comando(UUID.randomUUID(), idEvento, idAssento));
                };
                resultados.add(executor.submit(tentativa));
            }
            largada.countDown();

            List<RespostaAssento> respostas = new ArrayList<>();
            for (Future<RespostaAssento> resultado : resultados) {
                respostas.add(resultado.get(30, TimeUnit.SECONDS));
            }

            List<RespostaAssento> vencedoras = respostas.stream()
                    .filter(r -> RESERVADO.equals(r.tipo())).toList();
            assertThat(vencedoras).hasSize(1);
            assertThat(respostas).filteredOn(r -> INDISPONIVEL.equals(r.tipo())).hasSize(compras - 1);
            assertThat(lerAssento(idAssento).get("id_compra")).isEqualTo(vencedoras.get(0).idCompra());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void salvarAssentoPeloJpa_naoDesfazUmaReservaFeitaEmParalelo() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        // O gestor carrega o assento ainda DISPONIVEL...
        Assento carregadoPeloGestor = repository.findById(idAssento).orElseThrow();
        // ...a SAGA reserva o assento nesse meio-tempo...
        service.reservar(comando(compra, idEvento, idAssento));
        // ...e um save() com a cópia antiga, ainda DISPONIVEL, não pode desfazer a reserva.
        // (Impedir a própria alteração de um assento reservado é a issue #39.)
        repository.save(carregadoPeloGestor);

        Map<String, Object> assento = lerAssento(idAssento);
        assertThat(assento.get("status")).isEqualTo("RESERVADO");
        assertThat(assento.get("id_compra")).isEqualTo(compra);
    }

    // ---- Apoio aos testes ----

    private ComandoAssento comando(UUID idCompra, int idEvento, int idAssento) {
        return new ComandoAssento(UUID.randomUUID(), idCompra, idEvento, idAssento, Instant.now());
    }
}
