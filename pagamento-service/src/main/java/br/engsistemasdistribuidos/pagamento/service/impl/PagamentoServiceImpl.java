package br.engsistemasdistribuidos.pagamento.service.impl;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.OutboxEvent;
import br.engsistemasdistribuidos.pagamento.model.Pagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;
import br.engsistemasdistribuidos.pagamento.repository.OutboxEventRepository;
import br.engsistemasdistribuidos.pagamento.repository.PagamentoRepository;
import br.engsistemasdistribuidos.pagamento.service.PagamentoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PagamentoServiceImpl implements PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final OutboxEventRepository outboxRepository;
    private final long atrasoMs;

    public PagamentoServiceImpl(
            PagamentoRepository pagamentoRepository,
            OutboxEventRepository outboxRepository,
            @Value("${pagamento.simulacao.atraso-ms:3000}") long atrasoMs) {
        this.pagamentoRepository = pagamentoRepository;
        this.outboxRepository = outboxRepository;
        this.atrasoMs = atrasoMs;
    }

    @Override
    @Transactional
    public Pagamento processarPagamento(
            UUID idCompra,
            Integer tentativa,
            BigDecimal valor,
            MetodoPagamento metodo,
            SimulacaoPagamento simulacao) {

        if (simulacao == SimulacaoPagamento.ATRASAR && atrasoMs > 0) {
            simularAtraso(atrasoMs);
        }

        String chaveIdempotencia = idCompra + ":" + tentativa;
        String status = (simulacao == SimulacaoPagamento.RECUSAR) ? "RECUSADO" : "APROVADO";
        UUID novoId = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();

        int inseridos = pagamentoRepository.inserirSeNaoExistir(
                novoId,
                idCompra,
                tentativa,
                valor,
                status,
                metodo.name(),
                simulacao.name(),
                chaveIdempotencia,
                agora
        );

        if (inseridos == 1) {
            String tipoEvento = "APROVADO".equals(status) ? "PAGAMENTO_APROVADO" : "PAGAMENTO_FALHOU";
            String payload = String.format(
                    "{\"idPagamento\":\"%s\",\"idCompra\":\"%s\",\"tentativa\":%d,\"status\":\"%s\"}",
                    novoId, idCompra, tentativa, status
            );

            OutboxEvent outboxEvent = new OutboxEvent(
                    "PAGAMENTO",
                    novoId.toString(),
                    tipoEvento,
                    payload
            );
            outboxRepository.save(outboxEvent);
        }

        return pagamentoRepository.findByChaveIdempotencia(chaveIdempotencia)
                .orElseThrow(() -> new IllegalStateException(
                        "Pagamento não encontrado após inserção idempotente: " + chaveIdempotencia
                ));
    }

    private void simularAtraso(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execução interrompida durante simulação de atraso", e);
        }
    }
}