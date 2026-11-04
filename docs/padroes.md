# Padrões Arquiteturais

Para garantir os critérios de sucesso (como evitar venda duplicada de assentos e cobranças duplas), os seguintes padrões serão aplicados:

| Padrão | Onde será aplicado | Qual problema resolve |
| :--- | :--- | :--- |
| **API Gateway Pattern** | `api-gateway` | Centraliza o roteamento das requisições, aplica autenticação (Keycloak), rate limiting (Redis) e evita que o cliente conheça a topologia interna. |
| **SAGA Pattern** | reserva-service (orquestração exclusiva) | Resolve o problema de transações distribuídas, coordenando o fluxo de reserva e pagamento, e disparando a compensação (liberação do assento) caso o pagamento falhe. |
| **Transactional Outbox** | reserva-service | Resolve o problema de "dual-write". Garante que os eventos só sejam enviados ao RabbitMQ se a transação no PostgreSQL for confirmada. A comunicação com o pagamento-service é síncrona via HTTP e não utiliza este padrão. |
| **Circuit Breaker** | `reserva-service` (ao chamar `pagamento-service`) | Isola falhas. Se o serviço de pagamento cair, o Circuit Breaker abre, retornando um erro rápido (< 1s) e evitando o esgotamento de recursos no sistema inteiro. |
| **Chave de Idempotência** | `pagamento-service` e `reserva-service` | Resolve o problema de requisições duplicadas (cliques duplos ou retry de rede). O serviço verifica a chave antes de processar, garantindo que a cobrança ou reserva ocorra apenas uma vez. |
