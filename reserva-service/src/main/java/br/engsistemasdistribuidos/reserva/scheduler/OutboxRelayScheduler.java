package br.engsistemasdistribuidos.reserva.scheduler;

import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private final OutboxEventRepository outboxRepository;
    // Futuramente você vai injetar o RabbitTemplate aqui!

    public OutboxRelayScheduler(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // Roda a cada 5000 milissegundos (5 segundos)
    @Scheduled(fixedDelay = 5000)
    public void processarOutbox() {
        List<OutboxEvent> eventosPendentes = outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc();

        if (eventosPendentes.isEmpty()) {
            return; // Nada a fazer
        }

        log.info("Encontrados {} eventos no Outbox para processar.", eventosPendentes.size());

        for (OutboxEvent evento : eventosPendentes) {
            try {
                // 1. Enviar para o RabbitMQ
                // rabbitTemplate.convertAndSend("ticketflow.exchange", evento.getTipoEvento(), evento.getPayload());
                
                log.info("Simulando envio ao RabbitMQ do evento: {}", evento.getTipoEvento());

                // 2. Marcar como processado
                evento.setProcessado(true);
                outboxRepository.save(evento);
                
                log.info("Evento {} processado com sucesso.", evento.getId());

            } catch (Exception e) {
                // Se der erro (ex: RabbitMQ caiu), ele cai no catch, a flag 'processado' continua FALSE,
                // e daqui a 5 segundos ele vai tentar enviar de novo! Resiliência total!
                log.error("Erro ao enviar evento {}", evento.getId(), e);
            }
        }
    }
}
