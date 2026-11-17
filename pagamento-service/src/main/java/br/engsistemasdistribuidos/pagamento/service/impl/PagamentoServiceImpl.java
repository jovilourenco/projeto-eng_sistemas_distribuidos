package br.engsistemasdistribuidos.pagamento.service.impl;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
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

    public PagamentoServiceImpl(PagamentoRepository pagamentoRepository) {
        this.pagamentoRepository = pagamentoRepository;
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "pagamentoService", fallbackMethod = "pagamentoFallback")
    public Pagamento processarPagamento(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento, String chaveIdempotencia) {
        
        // 1. VALIDAÇÃO DE IDEMPOTÊNCIA
        Optional<Pagamento> pagamentoExistente = pagamentoRepository.findByChaveIdempotencia(chaveIdempotencia);
        if (pagamentoExistente.isPresent()) {
            return pagamentoExistente.get();
        }

        // Simulação de erro proposital para testes da SAGA/Circuit Breaker (se idIngresso for 666)
        if (idIngresso != null && idIngresso == 666L) {
            throw new RuntimeException("Serviço de pagamento externo indisponível ou recusado.");
        }

        String statusSimulado = "APROVADO"; 

        // 2. SALVAR O PAGAMENTO NO BANCO
        Pagamento novoPagamento = new Pagamento(idIngresso, valor, statusSimulado, metodoPagamento, chaveIdempotencia);
        
        return pagamentoRepository.save(novoPagamento);
    }

    /**
     * MÉTODO DE FALLBACK
     * é acionado pelo Resilience4j quando o Circuit Breaker 
     * estiver aberto ou a chamada principal for uma exceção.
     */
    public Pagamento pagamentoFallback(Long idIngresso, BigDecimal valor, MetodoPagamento metodoPagamento, String chaveIdempotencia, Throwable t) {
        // Retorna um objeto de pagamento com status RECUSADO / INDISPONÍVEL de forma controlada
        // Isso evita travar o sistema e responde instantaneamente ao cliente/gateway.
        return new Pagamento(idIngresso, valor, "SERVICO_INDISPONIVEL_FALLBACK", metodoPagamento, chaveIdempotencia);
    }
}