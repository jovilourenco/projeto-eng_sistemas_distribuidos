package br.engsistemasdistribuidos.pagamento.scheduler;

import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private final OutboxEventRepository outboxRepository;

    public OutboxRelayScheduler(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelay = 5000)
    public void processarOutbox() {
        List<OutboxEvent> eventosPendentes = outboxRepository.findByProcessadoFalseOrderByDataCriacaoAsc();

        if (eventosPendentes.isEmpty()) {
            return;
        }

        for (OutboxEvent evento : eventosPendentes) {
            try {
                log.info("Publicando evento de pagamento no broker: {} - Payload: {}", evento.getTipoEvento(), evento.getPayload());

                evento.setProcessado(true);
                outboxRepository.save(evento);
            } catch (Exception e) {
                log.error("Erro ao publicar evento de pagamento {}", evento.getId(), e);
            }
        }
    }
}