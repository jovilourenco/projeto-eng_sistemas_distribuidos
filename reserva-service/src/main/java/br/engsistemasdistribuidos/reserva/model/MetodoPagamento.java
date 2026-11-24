package br.engsistemasdistribuidos.reserva.model;

// Métodos aceitos no pedido de pagamento. O Serviço de Pagamentos deve aceitar os mesmos valores.
public enum MetodoPagamento {
    PIX,
    CARTAO_CREDITO,
    BOLETO
}
