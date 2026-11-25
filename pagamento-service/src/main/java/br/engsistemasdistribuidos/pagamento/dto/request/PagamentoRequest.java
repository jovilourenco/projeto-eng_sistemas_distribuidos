package br.engsistemasdistribuidos.pagamento.dto.request;

import br.engsistemasdistribuidos.pagamento.model.MetodoPagamento;
import br.engsistemasdistribuidos.pagamento.model.SimulacaoPagamento;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoRequest(
        @NotNull(message = "O ID da compra é obrigatório") UUID idCompra,
        @NotNull(message = "O número da tentativa é obrigatório") @Min(value = 1, message = "A tentativa deve ser maior ou igual a 1") Integer tentativa,
        @NotNull(message = "O valor do pagamento é obrigatório") BigDecimal valor,
        @NotNull(message = "O método de pagamento é obrigatório") MetodoPagamento metodo,
        @NotNull(message = "A simulação de pagamento é obrigatória") SimulacaoPagamento simulacao
) {
}