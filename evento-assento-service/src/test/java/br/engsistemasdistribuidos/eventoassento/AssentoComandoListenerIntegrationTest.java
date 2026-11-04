package br.engsistemasdistribuidos.eventoassento;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        ComandoAssento comando = new ComandoAssento(UUID.randomUUID(), UUID.randomUUID(), idEvento, idAssento,
                Instant.now());

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR, comando);

        RespostaAssento resposta = aguardarResposta(comando.idMensagem());
        assertThat(resposta.tipo()).isEqualTo(RESERVADO);
        assertThat(resposta.idCompra()).isEqualTo(comando.idCompra());
        assertThat(resposta.valor()).isEqualByComparingTo(VALOR_PADRAO);
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
    void comandoSemIdCompra_vaiParaAFilaDeMensagensMortas() {
        int idEvento = criarEvento();
        int idAssento = criarAssento(idEvento);
        UUID idMensagem = UUID.randomUUID();
        ComandoAssento incompleto = new ComandoAssento(idMensagem, null, idEvento, idAssento, Instant.now());

        rabbitTemplate.convertAndSend(EXCHANGE_COMANDOS, RESERVAR, incompleto);

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
        ComandoAssento comando = new ComandoAssento(UUID.randomUUID(), compra, idEvento, idAssento, Instant.now());
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

    // Lê a fila de mensagens mortas até encontrar uma mensagem que contenha o marcador.
    private boolean aguardarMensagemMorta(String marcador) {
        long limite = System.currentTimeMillis() + ESPERA_MS;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = rabbitTemplate.receive(FILA_MENSAGENS_MORTAS, 1_000);
            if (mensagem != null && new String(mensagem.getBody(), StandardCharsets.UTF_8).contains(marcador)) {
                return true;
            }
        }
        return false;
    }
}
