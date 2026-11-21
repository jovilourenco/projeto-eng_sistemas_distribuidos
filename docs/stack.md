# Stack Tecnológica

A stack foi definida visando a construção de uma arquitetura de microsserviços resiliente, escalável e segura para o TicketFlow, focando nos requisitos de alta concorrência e integridade de dados.

| Tecnologia | Por que foi escolhida? |
| :--- | :--- |
| **Spring Boot** | Facilita o desenvolvimento de microsserviços com configuração automática, servidor embutido e ecossistema maduro para APIs REST. |
| **Spring Cloud Gateway** | Implementa o padrão API Gateway, atuando como ponto único de entrada para roteamento, autenticação, autorização e rate limiting. |
| **Resilience4j** | Biblioteca utilizada para implementar o padrão Circuit Breaker, permitindo isolar falhas e evitar a propagação de erros em cascata. |
| **PostgreSQL** | Banco relacional escolhido para garantir propriedades ACID nas transações de cada serviço, fundamental para a consistência e para o padrão Transactional Outbox. |
| **Redis** | Banco em memória escolhido para alta performance, sendo utilizado para gerenciar a reserva temporária dos assentos (com TTL) e o rate limiting. |
| **RabbitMQ** | Broker de mensagens utilizado para a comunicação assíncrona entre os serviços, viabilizando o Transactional Outbox. |
| **Keycloak** | Solução de gerenciamento de identidade utilizada para autenticação baseada em OAuth 2.0 e JWT, garantindo a segurança do sistema. |
| **Docker Compose** | Ferramenta para orquestração do ambiente local, permitindo subir toda a infraestrutura e os microsserviços de forma padronizada e isolada. |
| **GitHub Actions** | Plataforma de CI/CD escolhida para automatizar a integração contínua, rodando testes e garantindo a qualidade do código a cada Pull Request. |
| **Flyway** | Ferramenta escolhida para gerenciar as migrações e o versionamento dos schemas de banco de dados de cada microsserviço. |
| **MapStruct** | Utilizado para o mapeamento de objetos (ex: DTOs para Entidades) de forma performática e segura em tempo de compilação. |
