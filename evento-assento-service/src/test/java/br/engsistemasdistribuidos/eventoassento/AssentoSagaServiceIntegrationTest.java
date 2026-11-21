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
 * compra, comandos fora de ordem, a disputa simultânea pelo mesmo assento e o tudo ou nada dos
 * comandos com vários assentos (ADR-004).
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
        assertThat(resposta.idAssentos()).containsExactly(idAssento);
        assertThat(resposta.valores()).singleElement().satisfies(v -> {
            assertThat(v.idAssento()).isEqualTo(idAssento);
            assertThat(v.valor()).isEqualByComparingTo(VALOR_PADRAO);
        });
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

    // ---- Vários assentos: tudo ou nada (ADR-004) ----

    @Test
    void reservar_quatroAssentosDisponiveis_reservaTodosEInformaOValorDeCada() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        int c = criarAssento(idEvento);
        int d = criarAssento(idEvento);
        jdbcTemplate.update("UPDATE evento_assento.assento SET valor = 100.00 WHERE id_assento = ?", c);
        UUID compra = UUID.randomUUID();

        // Fora de ordem de propósito: a resposta mantém a ordem do comando.
        RespostaAssento resposta = service.reservar(comando(compra, idEvento, d, a, c, b));

        assertThat(resposta.tipo()).isEqualTo(RESERVADO);
        assertThat(resposta.idAssentos()).containsExactly(d, a, c, b);
        assertThat(resposta.valores()).extracting(v -> v.idAssento()).containsExactly(d, a, c, b);
        assertThat(resposta.valores().get(2).valor()).isEqualByComparingTo("100.00");
        for (int id : List.of(a, b, c, d)) {
            assertThat(lerAssento(id).get("status")).isEqualTo("RESERVADO");
            assertThat(lerAssento(id).get("id_compra")).isEqualTo(compra);
        }
    }

    @Test
    void reservar_umDosQuatroIndisponivel_naoReservaNenhum() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        int c = criarAssento(idEvento);
        int d = criarAssento(idEvento);
        UUID dona = UUID.randomUUID();
        service.reservar(comando(dona, idEvento, c));

        RespostaAssento resposta = service.reservar(comando(UUID.randomUUID(), idEvento, a, b, c, d));

        assertThat(resposta.tipo()).isEqualTo(INDISPONIVEL);
        assertThat(resposta.valores()).isNull();
        // a e b vêm antes de c na ordem de aplicação: foram alterados e precisam ter sido desfeitos.
        for (int id : List.of(a, b, d)) {
            assertThat(lerAssento(id).get("status")).isEqualTo("DISPONIVEL");
            assertThat(lerAssento(id).get("id_compra")).isNull();
        }
        assertThat(lerAssento(c).get("id_compra")).isEqualTo(dona);
    }

    @Test
    void vender_umDosAssentosNaoEDaCompra_naoVendeNenhum() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, a));

        RespostaAssento resposta = service.vender(comando(compra, idEvento, a, b));

        assertThat(resposta.tipo()).isEqualTo(VENDA_RECUSADA);
        assertThat(lerAssento(a).get("status")).isEqualTo("RESERVADO");
        assertThat(lerAssento(b).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void venderELiberar_variosAssentos_aplicamATodos() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        int c = criarAssento(idEvento);
        int d = criarAssento(idEvento);
        UUID vendida = UUID.randomUUID();
        UUID liberada = UUID.randomUUID();
        service.reservar(comando(vendida, idEvento, a, b));
        service.reservar(comando(liberada, idEvento, c, d));

        assertThat(service.vender(comando(vendida, idEvento, a, b)).tipo()).isEqualTo(VENDIDO);
        assertThat(service.liberar(comando(liberada, idEvento, c, d)).tipo()).isEqualTo(LIBERADO);

        assertThat(lerAssento(a).get("status")).isEqualTo("VENDIDO");
        assertThat(lerAssento(b).get("status")).isEqualTo("VENDIDO");
        assertThat(lerAssento(c).get("status")).isEqualTo("DISPONIVEL");
        assertThat(lerAssento(d).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void liberar_umDosAssentosJaVendidoParaACompra_naoLiberaNenhum() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, a, b));
        service.vender(comando(compra, idEvento, b));

        RespostaAssento resposta = service.liberar(comando(compra, idEvento, a, b));

        assertThat(resposta.tipo()).isEqualTo(LIBERACAO_RECUSADA);
        assertThat(lerAssento(a).get("status")).isEqualTo("RESERVADO");
        assertThat(lerAssento(b).get("status")).isEqualTo("VENDIDO");
    }

    @Test
    void liberar_umAssentoJaLiberadoEOutroReservado_respondeLiberadoEDeixaOsDoisDisponiveis() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();
        service.reservar(comando(compra, idEvento, a, b));
        service.liberar(comando(compra, idEvento, a));

        RespostaAssento resposta = service.liberar(comando(compra, idEvento, a, b));

        assertThat(resposta.tipo()).isEqualTo(LIBERADO);
        assertThat(lerAssento(a).get("status")).isEqualTo("DISPONIVEL");
        assertThat(lerAssento(b).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void reservarEVender_repetidosComVariosAssentos_respondemOMesmoResultado() {
        int idEvento = criarEvento();
        int a = criarAssento(idEvento);
        int b = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        RespostaAssento primeira = service.reservar(comando(compra, idEvento, a, b));
        RespostaAssento repetida = service.reservar(comando(compra, idEvento, a, b));
        assertThat(repetida.tipo()).isEqualTo(RESERVADO);
        assertThat(repetida.valores()).isEqualTo(primeira.valores());

        assertThat(service.vender(comando(compra, idEvento, a, b)).tipo()).isEqualTo(VENDIDO);
        assertThat(service.vender(comando(compra, idEvento, a, b)).tipo()).isEqualTo(VENDIDO);
        assertThat(lerAssento(a).get("status")).isEqualTo("VENDIDO");
        assertThat(lerAssento(b).get("status")).isEqualTo("VENDIDO");
    }

    @Test
    void reservar_comprasSimultaneasComAssentosCruzados_semDeadlockENenhumAssentoEmDuasCompras() throws Exception {
        int idEvento = criarEvento();
        List<Integer> assentos = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            assentos.add(criarAssento(idEvento));
        }
        int compras = 60;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<RespostaAssento>> resultados = new ArrayList<>();

        try {
            for (int i = 0; i < compras; i++) {
                // Pares sobrepostos em ordens opostas: (0,1), (2,1), (1,2), (3,2)... para forçar a disputa cruzada.
                int x = assentos.get(i % 6);
                int y = assentos.get((i + 1) % 6);
                List<Integer> par = i % 2 == 0 ? List.of(x, y) : List.of(y, x);
                Callable<RespostaAssento> tentativa = () -> {
                    largada.await();
                    return service.reservar(new ComandoAssento(UUID.randomUUID(), UUID.randomUUID(), idEvento,
                            par, Instant.now()));
                };
                resultados.add(executor.submit(tentativa));
            }
            largada.countDown();

            List<RespostaAssento> vencedoras = new ArrayList<>();
            for (Future<RespostaAssento> resultado : resultados) {
                RespostaAssento resposta = resultado.get(30, TimeUnit.SECONDS);
                if (RESERVADO.equals(resposta.tipo())) {
                    vencedoras.add(resposta);
                }
            }

            // Cada assento reservado pertence a exatamente uma compra vencedora, e cada vencedora tem os dois.
            assertThat(vencedoras).isNotEmpty();
            List<Integer> reservados = vencedoras.stream().flatMap(r -> r.idAssentos().stream()).toList();
            assertThat(reservados).doesNotHaveDuplicates();
            for (RespostaAssento vencedora : vencedoras) {
                for (Integer id : vencedora.idAssentos()) {
                    assertThat(lerAssento(id).get("id_compra")).isEqualTo(vencedora.idCompra());
                }
            }
        } finally {
            executor.shutdownNow();
        }
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

    private ComandoAssento comando(UUID idCompra, int idEvento, Integer... idAssentos) {
        return new ComandoAssento(UUID.randomUUID(), idCompra, idEvento, List.of(idAssentos), Instant.now());
    }
}
