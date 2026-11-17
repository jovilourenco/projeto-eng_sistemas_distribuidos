package br.engsistemasdistribuidos.pagamento.service.impl;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.repository.PagamentoRepository;
import br.engsistemasdistribuidos.pagamento.service.PagamentoService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PagamentoServiceImpl implements PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final OutboxEventRepository outboxRepository;

    public PagamentoServiceImpl(PagamentoRepository pagamentoRepository, OutboxEventRepository outboxRepository) {
        this.pagamentoRepository = pagamentoRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "pagamentoService", fallbackMethod = "pagamentoFallback")
    public Pagamento processarPagamento(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento,
            String chaveIdempotencia) {

        Optional<Pagamento> pagamentoExistente = pagamentoRepository.findByChaveIdempotencia(chaveIdempotencia);
        if (pagamentoExistente.isPresent()) {
            return pagamentoExistente.get();
        }

        if (idIngresso != null && idIngresso == 666L) {
            throw new RuntimeException("Serviço de pagamento externo indisponível.");
        }

        // Simulação: valores negativos ou zerados são rejeitados para permitir testar a
        // compensação da SAGA
        String statusSimulado = (valor != null && valor.compareTo(BigDecimal.ZERO) > 0) ? "APROVADO" : "REJEITADO";
        String tipoEvento = "APROVADO".equals(statusSimulado) ? "PAGAMENTO_APROVADO" : "PAGAMENTO_FALHOU";

        Pagamento novoPagamento = new Pagamento(idIngresso, valor, statusSimulado, metodoPagamento, chaveIdempotencia);
        Pagamento pagamentoSalvo = pagamentoRepository.save(novoPagamento);

        String payload = String.format(
                "{\"idPagamento\":\"%s\",\"idIngresso\":%d,\"status\":\"%s\"}",
                pagamentoSalvo.getId(), idIngresso, statusSimulado);

        OutboxEvent outboxEvent = new OutboxEvent(
                "PAGAMENTO",
                pagamentoSalvo.getId().toString(),
                tipoEvento,
                payload);
        outboxRepository.save(outboxEvent);

        return pagamentoSalvo;
    }

    public Pagamento pagamentoFallback(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento,
            String chaveIdempotencia, Throwable t) {
        return new Pagamento(idIngresso, valor, "SERVICO_INDISPONIVEL_FALLBACK", metodoPagamento, chaveIdempotencia);
    }
}