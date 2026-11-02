package br.engsistemasdistribuidos.eventoassento.mensageria;

import static br.engsistemasdistribuidos.eventoassento.mensageria.MensagensAssento.*;

import java.util.Map;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia do RabbitMQ usada por este serviço (ADR-001, "Configurações obrigatórias"):
 * exchanges e filas duráveis, fila de mensagens mortas e mensagens em JSON.
 * As mensagens publicadas pelo RabbitTemplate são persistentes por padrão.
 */
@Configuration
public class MensageriaConfig {

    @Bean
    public Declarables topologiaAssento() {
        TopicExchange comandos = new TopicExchange(EXCHANGE_COMANDOS, true, false);
        TopicExchange respostas = new TopicExchange(EXCHANGE_RESPOSTAS, true, false);
        // Fanout: a mensagem morta mantém a routing key original, que é o tipo do comando. Assim ela pode
        // ser reprocessada publicando-a de volta no exchange de comandos com a mesma routing key.
        FanoutExchange mensagensMortas = new FanoutExchange(EXCHANGE_MENSAGENS_MORTAS, true, false);

        Queue filaComandos = QueueBuilder.durable(FILA_COMANDOS)
                .deadLetterExchange(EXCHANGE_MENSAGENS_MORTAS)
                .build();
        Queue filaMensagensMortas = QueueBuilder.durable(FILA_MENSAGENS_MORTAS).build();

        Binding reservar = BindingBuilder.bind(filaComandos).to(comandos).with(RESERVAR);
        Binding vender = BindingBuilder.bind(filaComandos).to(comandos).with(VENDER);
        Binding liberar = BindingBuilder.bind(filaComandos).to(comandos).with(LIBERAR);
        Binding mortas = BindingBuilder.bind(filaMensagensMortas).to(mensagensMortas);

        return new Declarables(comandos, respostas, mensagensMortas, filaComandos, filaMensagensMortas,
                reservar, vender, liberar, mortas);
    }

    /**
     * Mensagens malformadas (comando incompleto ou JSON inválido) nunca vão dar certo: vão direto para a
     * fila de mensagens mortas, sem passar pelas tentativas. As demais falhas, como o banco fora do ar,
     * são tentadas o número de vezes configurado em spring.rabbitmq.listener.simple.retry.max-attempts.
     */
    @Bean
    public RabbitRetryTemplateCustomizer naoRepetirMensagensMalformadas(RabbitProperties propriedades) {
        int tentativas = propriedades.getListener().getSimple().getRetry().getMaxAttempts();
        return (alvo, retryTemplate) -> {
            if (alvo == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(new SimpleRetryPolicy(tentativas, Map.of(
                        AmqpRejectAndDontRequeueException.class, false,
                        MessageConversionException.class, false), true, true));
            }
        };
    }

    /**
     * Converte as mensagens para JSON. O tipo de destino é sempre o parâmetro do listener, e não o
     * cabeçalho __TypeId__, para que o publicador (em outro serviço) não precise compartilhar classes Java.
     */
    @Bean
    public MessageConverter conversorJson(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(objectMapper);
        conversor.setAlwaysConvertToInferredType(true);
        return conversor;
    }
}
