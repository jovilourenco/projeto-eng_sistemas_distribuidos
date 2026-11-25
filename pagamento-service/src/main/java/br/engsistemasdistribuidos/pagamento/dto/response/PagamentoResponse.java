package br.engsistemasdistribuidos.pagamento.dto.response;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponse(
        UUID id,
        UUID idCompra,
        Integer tentativa,
        BigDecimal valor,
        String status,
        MetodoPagamento metodo,
        String chaveIdempotencia,
        LocalDateTime dataCriacao
) {
}