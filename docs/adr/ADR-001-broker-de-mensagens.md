# ADR-001: Broker de mensagens

- **Status:** Pendente
- **Data:** 2026-11-02
- **Issue:** #17

## Contexto

O TicketFlow é composto por microsserviços com bancos de dados independentes (Eventos e Assentos, Reservas, Pagamentos e Notificações). O fluxo de compra atravessa vários desses serviços, e a proposta do projeto definiu que essa coordenação será feita com o padrão **SAGA**, com publicação de mensagens garantida pelo **Transactional Outbox**. Os dois padrões pressupõem comunicação assíncrona por mensagens. Por isso é necessário escolher um broker de mensagens.

A proposta deixou em aberto a escolha entre as duas soluções mais adotadas e estudadas para esse papel: **RabbitMQ** e **Apache Kafka**.

Os requisitos que orientam a escolha vêm dos critérios de sucesso e das demais decisões da proposta:

1. **Nenhum evento perdido** ao derrubar um serviço ou o próprio broker durante a publicação ou o consumo de mensagens.
2. **Compensação em 100% das reservas cujo pagamento falhar**, o que exige que mensagens com falha de processamento não sejam descartadas silenciosamente.
3. **Carga de referência de 200 requisições por segundo.**
4. **Compatibilidade com a stack definida:** Spring Boot e Spring Cloud, Docker Compose para o ambiente local, Testcontainers para os testes de integração e GitHub Actions para a integração contínua.
5. **Equipe de seis pessoas, com prazo de um semestre letivo**, o que torna relevante o custo de aprendizado e de operação de cada ferramenta.

## Análise comparativa

As duas ferramentas resolvem o mesmo problema geral (desacoplar produtores e consumidores), mas partem de modelos diferentes.

- **RabbitMQ** implementa o protocolo AMQP e segue o modelo de **fila de mensagens**. O broker recebe a mensagem, a roteia para uma ou mais filas por meio de *exchanges* e regras de *binding*, entrega cada mensagem a um consumidor e a remove da fila quando recebe a confirmação (*ack*). O broker concentra a inteligência de roteamento e o controle de entrega ("broker inteligente, consumidor simples").
- **Kafka** segue o modelo de **log distribuído**. As mensagens são anexadas a um log particionado e retidas por um período configurável, independentemente de terem sido lidas. Cada grupo de consumidores controla a própria posição de leitura (*offset*). O broker é deliberadamente simples, e a responsabilidade de controlar o progresso fica no consumidor ("broker simples, consumidor inteligente").

| Critério | RabbitMQ | Kafka |
| :--- | :--- | :--- |
| **Modelo** | Fila: a mensagem é removida após a confirmação | Log: a mensagem é retida e pode ser relida |
| **Roteamento** | Flexível no broker: exchanges `direct`, `topic`, `fanout` e `headers` | Por tópico e partição; filtragem fina fica no consumidor |
| **Releitura do histórico** | Não é o caso de uso principal | Nativa: basta voltar o offset |
| **Ordenação** | Garantida por fila, com um único consumidor | Garantida por partição |
| **Garantia de entrega** | Pelo menos uma vez, com *publisher confirms* e *ack* enviado só após o processamento | Pelo menos uma vez por padrão; "exatamente uma vez" apenas entre tópicos Kafka, por meio de transações |
| **Mensagens com falha** | *Dead letter exchange*: o broker encaminha a mensagem rejeitada para outra fila; o número de tentativas é limitado no consumidor | Tópicos de retry e de mensagens mortas mantidos pela aplicação ou por biblioteca cliente (por exemplo, Spring for Apache Kafka) |
| **Vazão** | Menor vazão máxima | Vazão muito superior em grandes volumes; a vantagem aparece em cargas muito acima da prevista |
| **Operação local** | Um container, com painel web de administração | Broker com KRaft (ou ZooKeeper em versões anteriores); mais memória e configuração |
| **Integração com Spring** | Spring AMQP: `RabbitTemplate`, `@RabbitListener` | Spring for Apache Kafka: `KafkaTemplate`, `@KafkaListener` |
| **Testes com Testcontainers** | `RabbitMQContainer` | `KafkaContainer` |
| **Curva de aprendizado** | Conceitos de fila, exchange e binding | Partições, offsets, consumer groups, rebalanceamento e retenção |

Em resumo: o Kafka se destaca quando o volume é muito alto e o histórico de eventos precisa ser retido e reprocessado; o RabbitMQ se destaca quando o roteamento é variado, as mensagens representam tarefas ou comandos que devem ser entregues e confirmadas individualmente, e o tratamento de mensagens com falha deve ficar no broker.

## Decisão

Adotar o **RabbitMQ** como broker de mensagens do TicketFlow.

### Justificativa

1. **O tipo de mensagem do TicketFlow é o caso de uso típico de filas.** As mensagens do sistema são comandos ponto a ponto e respostas de uma transação de negócio em andamento: o orquestrador da compra manda o Serviço de Eventos e Assentos reservar, vender ou liberar um assento e recebe a resposta (ADR-002), e o fim da compra é anunciado ao Serviço de Notificações. Cada mensagem precisa ser entregue, processada e confirmada individualmente. Não há requisito de reprocessar o histórico: o estado de cada compra fica persistido nos bancos dos serviços, e não no broker. A principal vantagem do Kafka, a retenção e releitura do log, não seria aproveitada.

2. **Compatibilidade com a SAGA e o Transactional Outbox.** Os dois brokers atendem a esses padrões: ambos oferecem entrega "pelo menos uma vez", e em ambos o consumidor só confirma o progresso depois de concluir a transação local. O RabbitMQ é preferido porque o modelo de fila corresponde diretamente a comandos ponto a ponto com confirmação individual. Se o serviço cair no meio do processamento, a mensagem não confirmada volta para a fila e é reentregue, atendendo ao critério de nenhum evento perdido.

3. **Falhas de compensação não podem ser descartadas.** O critério de compensação em 100% dos casos exige que uma mensagem que falhe repetidamente seja isolada para análise, e não perdida nem reprocessada em laço infinito (padrão *Dead Letter Channel*). No RabbitMQ, o consumidor limita as tentativas e rejeita a mensagem, e o broker a encaminha para a fila de mensagens mortas, declarada como propriedade da fila. No Kafka, o encaminhamento também precisa ser feito pela aplicação, que publica a mensagem em outro tópico.

4. **A carga prevista não exige o Kafka.** A meta de 200 requisições por segundo está cerca de duas ordens de grandeza abaixo do ponto em que a diferença de vazão entre as duas ferramentas se torna relevante. Além disso, o broker não está no caminho síncrono da resposta ao usuário (ADR-002), então não influencia a meta de latência da reserva.

5. **Simplicidade de operação e de aprendizado.** Um único container com painel web reduz o custo do ambiente local em Docker Compose e dos testes com Testcontainers. Os conceitos do RabbitMQ (fila, exchange, binding, ack) são menos numerosos que os do Kafka. Para uma equipe de seis pessoas, com um semestre de prazo, isso libera tempo para os pontos centrais do projeto: SAGA, Outbox, idempotência e Circuit Breaker. O painel de administração também permite observar as filas durante os testes de resiliência e a apresentação.

### Configurações obrigatórias

O critério de nenhum evento perdido só é atendido com as seguintes configurações, que fazem parte desta decisão:

| Configuração | Efeito |
| :--- | :--- |
| Exchanges e filas **duráveis** | Sobrevivem à reinicialização do broker |
| Mensagens **persistentes** (`delivery_mode = 2`) | São gravadas em disco e não se perdem se o broker reiniciar com elas na fila |
| ***Publisher confirms*** no publicador do Outbox | O Outbox só marca uma mensagem como enviada depois que o broker confirma o recebimento; sem confirmação, ela é reenviada |
| ***Ack* após o processamento** nos consumidores (não na entrega) | A mensagem só é confirmada depois que a transação local do consumidor é concluída. No Spring AMQP, isso corresponde ao modo de confirmação `AUTO`, em que o container confirma a mensagem quando o listener termina sem erro |
| **Dead letter exchange** em toda fila de consumo, e número de tentativas limitado no consumidor (retry do Spring AMQP, que rejeita a mensagem sem devolvê-la à fila ao fim das tentativas) | Mensagens que falham repetidamente são isoladas em uma fila de mensagens mortas, monitorada pela equipe, em vez de serem reprocessadas sem fim |

## Alternativas consideradas

**Apache Kafka.** Descartado pelos motivos acima. Sua retenção de eventos, sua vazão e sua garantia de "exatamente uma vez" entre tópicos não trazem benefício concreto para os requisitos do TicketFlow. Essa garantia, aliás, não se estende à gravação nos bancos PostgreSQL dos serviços, então a idempotência dos consumidores seria necessária de qualquer forma. Em troca, o Kafka exigiria mais infraestrutura, mais conceitos e a configuração de mensagens mortas fora do broker.

**Redis Streams.** O Redis já faz parte da stack, e o Streams oferece grupos de consumidores e confirmação. Descartado porque o Redis será usado como armazenamento temporário, e não configurado para persistência durável (ADR-003). Usá-lo como broker colocaria o critério de nenhum evento perdido sob a mesma configuração. Também não oferece mensagens mortas nativas.

**Outros brokers (ActiveMQ Artemis, NATS JetStream).** Atenderiam tecnicamente ao modelo de fila, mas têm integração menos difundida com o ecossistema Spring e menos material de apoio para a equipe. Não oferecem vantagem que compense essa diferença.

**Comunicação síncrona por HTTP como mecanismo geral entre os serviços.** Descartada para os passos da SAGA que não têm usuário esperando a resposta. Nesses passos, a chamada síncrona acopla a disponibilidade dos serviços: se o serviço chamado estiver fora, a operação falha imediatamente, e não há como garantir a entrega depois que ele volta. A comunicação com o Serviço de Pagamentos é uma exceção deliberada, porque o usuário aguarda o resultado do pagamento. Essa exceção é justificada no ADR-002.

## Consequências

**Positivas**

- O roteamento por exchanges `topic` permite que cada serviço assine apenas os tipos de mensagem que lhe interessam, sem mudanças nos publicadores.
- Mensagens com falha persistente ficam isoladas em filas de mensagens mortas, onde podem ser inspecionadas e reprocessadas.
- O ambiente local e os testes de integração usam um único container leve.
- O painel de administração dá visibilidade das filas nos testes de resiliência, como derrubar um serviço e ver as mensagens se acumularem e depois serem consumidas.

**Negativas e riscos**

- **A entrega é "pelo menos uma vez".** Uma mensagem pode ser entregue mais de uma vez, por exemplo quando o consumidor processa mas cai antes de confirmar, ou quando o Outbox reenvia. Todo consumidor precisa ser idempotente. A estratégia para o status do assento está no ADR-003, e a do Serviço de Notificações no ADR-002.
- **Não há histórico de mensagens no broker.** Se surgir a necessidade de auditoria ou reprocessamento de eventos passados, ela terá de ser atendida pelos bancos dos serviços (por exemplo, a tabela de outbox) ou pela revisão desta decisão.
- **A ordem só é garantida dentro de uma fila com um único consumidor.** Com consumidores concorrentes, mensagens sobre o mesmo assento podem ser processadas fora de ordem. Os consumidores devem validar o estado atual antes de aplicar uma mudança.
- **Mensagens mortas exigem acompanhamento.** Uma mensagem isolada na fila de mensagens mortas não é processada sozinha. O encaminhamento para essa fila também não tem garantia de entrega: se falhar, a mensagem se perde. Por isso nenhuma decisão depende só dele: a rotina de verificação do orquestrador (ADR-002) reenvia comandos pendentes e dispara expirações, e a reconciliação do ADR-003 recupera assentos presos.
- **O broker é um ponto único de falha.** Do lado de quem publica, o Outbox retém as mensagens no banco até o broker voltar. Do lado das filas, a durabilidade e a persistência preservam as mensagens já entregues. Para ambientes além do local, filas replicadas (*quorum queues*) seriam necessárias.
