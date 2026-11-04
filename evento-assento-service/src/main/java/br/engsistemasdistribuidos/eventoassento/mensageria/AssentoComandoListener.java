package br.engsistemasdistribuidos.eventoassento.mensageria;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import br.engsistemasdistribuidos.eventoassento.service.AssentoSagaService;

/**
 * Consome os comandos da SAGA enviados pelo orquestrador (Serviço de Reservas) e publica a resposta.
 *
 * A resposta é publicada depois que a transação do comando é confirmada. Se o serviço cair entre a
 * confirmação e a publicação, a resposta se perde, mas o orquestrador reenvia o comando pendente e,
 * como os comandos são idempotentes, a mesma resposta é gerada de novo (ADR-002, ADR-003).
 * O mesmo vale se a resposta for descartada pelo broker ou se a publicação lançar exceção: neste último
 * caso, o comando ainda é reprocessado aqui, também de forma idempotente.
 */
@Component
public class AssentoComandoListener {

    private final AssentoSagaService service;
    private final RabbitTemplate rabbitTemplate;

    public AssentoComandoListener(AssentoSagaService service, RabbitTemplate rabbitTemplate) {
        this.service = service;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = FILA_COMANDOS)
    public void receber(ComandoAssento comando, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String tipo) {
        validar(comando, tipo);

        RespostaAssento resposta = switch (tipo) {
            case RESERVAR -> service.reservar(comando);
            case VENDER -> service.vender(comando);
            case LIBERAR -> service.liberar(comando);
            default -> throw new AmqpRejectAndDontRequeueException("Tipo de comando desconhecido: " + tipo);
        };

        rabbitTemplate.convertAndSend(EXCHANGE_RESPOSTAS, resposta.tipo(), resposta);
    }

    // Um comando malformado nunca vai ser aplicado: esgotadas as tentativas, vai para a fila de mensagens mortas.
    private void validar(ComandoAssento comando, String tipo) {
        if (comando == null || comando.idMensagem() == null || comando.idCompra() == null
                || comando.idAssento() == null) {
            throw new AmqpRejectAndDontRequeueException("Comando incompleto: " + comando);
        }
        if (RESERVAR.equals(tipo) && comando.idEvento() == null) {
            throw new AmqpRejectAndDontRequeueException("Comando assento.reservar sem idEvento: " + comando);
        }
    }
}
