package br.engsistemasdistribuidos.reserva.service.impl;

import br.engsistemasdistribuidos.reserva.model.OutboxEvent;
import br.engsistemasdistribuidos.reserva.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.reserva.service.ReservaService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
public class ReservaServiceImpl implements ReservaService {

    private final StringRedisTemplate redisTemplate;
    private final OutboxEventRepository outboxRepository;

    public ReservaServiceImpl(StringRedisTemplate redisTemplate, OutboxEventRepository outboxRepository) {
        this.redisTemplate = redisTemplate;
        this.outboxRepository = outboxRepository;
    }

    @Override
    @Transactional
    public void criarReserva(Integer idAssento, Integer idUsuario) {
        
        // 1. TENTAR PEGAR O LOCK NO REDIS
        String lockKey = "assento:lock:" + idAssento;
        
        Boolean lockAdquirido = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, idUsuario.toString(), Duration.ofMinutes(10));

        if (Boolean.FALSE.equals(lockAdquirido)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assento já está em processo de reserva por outro usuário.");
        }

        try {
            // 2. SALVAR O INGRESSO NO BANCO 
            // Código de persistência do Ingresso entrará aqui, mas ainda não foi implementado.
            
            String idIngressoSimulado = "999"; 
            String payloadJson = "{\"idIngresso\": " + idIngressoSimulado + ", \"idAssento\": " + idAssento + "}";

            // 3. SALVAR O EVENTO NO OUTBOX
            OutboxEvent outboxEvent = new OutboxEvent(
                    "INGRESSO", 
                    idIngressoSimulado, 
                    "RESERVA_CRIADA", 
                    payloadJson
            );
            
            outboxRepository.save(outboxEvent);

        } catch (Exception e) {
            // Em caso de falha, libera o Lock
            redisTemplate.delete(lockKey);
            throw e;
        }
    }
}