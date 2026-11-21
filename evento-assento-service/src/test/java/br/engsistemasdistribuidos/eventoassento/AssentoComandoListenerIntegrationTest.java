package br.engsistemasdistribuidos.eventoassento;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;

import br.engsistemasdistribuidos.eventoassento.mensageria.ComandoAssento;
import br.engsistemasdistribuidos.eventoassento.mensageria.RespostaAssento;

/**
 * Fluxo completo pelo RabbitMQ: o comando é publicado no exchange de comandos, o listener aplica a
 * transição e a resposta chega ao exchange de respostas. Comandos inválidos vão para a fila de
 * mensagens mortas.
 */
class AssentoComandoListenerIntegrationTest extends IntegracaoComContainers {

    private static final String FILA_RESPOSTAS_TESTE = "teste.respostas";
    private static final long ESPERA_MS = 15_000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void escutarRespostas() {
        // Fila do teste ligada ao exchange de respostas, no papel do orquestrador.
        Queue fila = new Queue(FILA_RESPOSTAS_TESTE, false);
        amqpAdmin.declareQueue(fila);
        amqpAdmin.declareBinding(BindingBuilder.bind(fila)
                .to(new TopicExchange(EXCHANGE_RESPOSTAS)).with("assento.#"));
        amqpAdmin.purgeQueue(FILA_RESPOSTAS_TESTE, false);
    }

    @Test
    void comandoReservar_publicadoNoExchange_reservaEPublicaAResposta() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        ComandoAssento comando = new ComandoAssento(UUID.randomUUID(), UUID.randomUUID(), idEvento,
                List.of(idAssento), Instant.now());

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR, comando);

        RespostaAssento resposta = aguardarResposta(comando.idMensagem());
        assertThat(resposta.tipo()).isEqualTo(RESERVADO);
        assertThat(resposta.idCompra()).isEqualTo(comando.idCompra());
        assertThat(resposta.valores()).singleElement()
                .satisfies(v -> assertThat(v.valor()).isEqualByComparingTo(VALOR_PADRAO));
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("RESERVADO");
    }

    @Test
    void fluxoCompleto_reservarVenderLiberar_respondeCadaPasso() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID compra = UUID.randomUUID();

        assertThat(enviarEAguardar(RESERVAR, compra, idEvento, idAssento).tipo()).isEqualTo(RESERVADO);
        assertThat(enviarEAguardar(VENDER, compra, idEvento, idAssento).tipo()).isEqualTo(VENDIDO);
        assertThat(enviarEAguardar(LIBERAR, compra, idEvento, idAssento).tipo()).isEqualTo(LIBERACAO_RECUSADA);
    }

    @Test
    void comandoEmJsonComoOReservasGrava_umAssentoOcupado_respondeIndisponivelSemIrParaADlq() {
        // JSON cru, no formato que o Outbox do Serviço de Reservas grava (dataHora em texto ISO-8601).
        int idEvento = criarEvento();
        int livre = criarAssento(idEvento);
        int ocupado = criarAssento(idEvento);
        enviarEAguardar(RESERVAR, UUID.randomUUID(), idEvento, ocupado);
        UUID idMensagem = UUID.randomUUID();
        String json = """
                {"idMensagem":"%s","idCompra":"%s","idEvento":%d,"idAssentos":[%d,%d],
                 "dataHora":"2026-11-21T14:30:00.123456Z"}
                """.formatted(idMensagem, UUID.randomUUID(), idEvento, livre, ocupado);
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        rabbitTemplate.send(EXCHANGE_COMANDOS, RESERVAR,
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(propriedades).build());

        Message resposta = aguardarRespostaCrua(idMensagem);
        String corpo = new String(resposta.getBody(), StandardCharsets.UTF_8);
        assertThat(resposta.getMessageProperties().getReceivedRoutingKey()).isEqualTo(INDISPONIVEL);
        assertThat(corpo).contains("\"tipo\":\"assento.indisponivel\"")
                .contains("\"idAssentos\":[" + livre + "," + ocupado + "]")
                .contains("\"valores\":null");
        assertThat(lerAssento(livre).get("status")).isEqualTo("DISPONIVEL");
        assertThat(aguardarMensagemMorta(idMensagem.toString(), 2_000)).isFalse();
    }

    @Test
    void comandoSemIdCompra_vaiParaAFilaDeMensagensMortas() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID idMensagem = UUID.randomUUID();
        ComandoAssento incompleto = new ComandoAssento(idMensagem, null, idEvento, List.of(idAssento),
                Instant.now());

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR, incompleto);

        assertThat(aguardarMensagemMorta(idMensagem.toString())).isTrue();
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void comandoComMaisDeQuatroAssentos_vaiParaAFilaDeMensagensMortasSemAlterarNada() {
        int idEvento = criarEvento();
        List<Integer> assentos = List.of(criarAssento(idEvento), criarAssento(idEvento), criarAssento(idEvento),
                criarAssento(idEvento), criarAssento(idEvento));
        UUID idMensagem = UUID.randomUUID();

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR,
                new ComandoAssento(idMensagem, UUID.randomUUID(), idEvento, assentos, Instant.now()));

        assertThat(aguardarMensagemMorta(idMensagem.toString())).isTrue();
        assentos.forEach(id -> assertThat(lerAssento(id).get("status")).isEqualTo("DISPONIVEL"));
    }

    @Test
    void comandoComAssentoRepetido_vaiParaAFilaDeMensagensMortas() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID idMensagem = UUID.randomUUID();

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR,
                new ComandoAssento(idMensagem, UUID.randomUUID(), idEvento, List.of(idAssento, idAssento),
                        Instant.now()));

        assertThat(aguardarMensagemMorta(idMensagem.toString())).isTrue();
        assertThat(lerAssento(idAssento).get("status")).isEqualTo("DISPONIVEL");
    }

    @Test
    void mensagemQueNaoEJson_vaiParaAFilaDeMensagensMortas() {
        String marcador = "nao-e-json-" + UUID.randomUUID();
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message invalida = MessageBuilder.withBody(marcador.getBytes(StandardCharsets.UTF_8))
                .andProperties(propriedades).build();

        rabbitTemplate.send(EXCHANGE_COMANDOS, RESERVAR, invalida);

        assertThat(aguardarMensagemMorta(marcador)).isTrue();
    }

    // ---- Apoio aos testes ----

    private RespostaAssento enviarEAguardar(String tipo, UUID compra, int idEvento, int idAssento) {
        ComandoAssento comando = new ComandoAssento(UUID.randomUUID(), compra, idEvento, List.of(idAssento),
                Instant.now());
        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, tipo, comando);
        return aguardarResposta(comando.idMensagem());
    }

    // Lê a fila de respostas até encontrar a resposta do comando informado.
    private RespostaAssento aguardarResposta(UUID idComando) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            RespostaAssento resposta = rabbitTemplate.receiveAndConvert(FILA_RESPOSTAS_TESTE, 1_000,
                    new ParameterizedTypeReference<RespostaAssento>() {
                    });
            if (resposta != null && idComando.equals(resposta.idComando())) {
                return resposta;
            }
        }
        throw new AssertionError("Nenhuma resposta recebida para o comando " + idComando);
    }

    // Lê a fila de respostas, sem converter, até encontrar a resposta do comando informado.
    private Message aguardarRespostaCrua(UUID idComando) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = rabbitTemplate.receive(FILA_RESPOSTAS_TESTE, 1_000);
            if (mensagem != null && new String(mensagem.getBody(), StandardCharsets.UTF_8)
                    .contains("\"idComando\":\"" + idComando + "\"")) {
                return mensagem;
            }
        }
        throw new AssertionError("Nenhuma resposta recebida para o comando " + idComando);
    }

    private boolean aguardarMensagemMorta(String marcador) {
        return aguardarMensagemMorta(marcador, ESPERA_MS);
    }

    // Lê a fila de mensagens mortas até encontrar uma mensagem que contenha o marcador.
    private boolean aguardarMensagemMorta(String marcador, long esperaMs) {
        long limite = System.currentTimeMillis() + esperaMs;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = rabbitTemplate.receive(FILA_MENSAGENS_MORTAS, 1_000);
            if (mensagem != null && new String(mensagem.getBody(), StandardCharsets.UTF_8).contains(marcador)) {
                return true;
            }
        }
        return false;
    }
}
