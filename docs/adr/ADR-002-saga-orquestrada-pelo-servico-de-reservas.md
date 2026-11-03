# ADR-002: Coordenação da SAGA de compra

- **Status:** Pendente
- **Data:** 2026-11-02
- **Issue:** #17

## Contexto

Uma compra no TicketFlow altera dados de três serviços, cada um com o seu banco:

- **Reservas:** cria o ingresso e controla o andamento da compra.
- **Pagamentos:** registra a cobrança simulada.
- **Eventos e Assentos:** altera o status do assento (`DISPONIVEL`, `RESERVADO`, `VENDIDO`).

Como não existe uma transação de banco que abranja os três serviços, a proposta do projeto adotou o padrão **SAGA**. A compra é dividida em uma sequência de transações locais. Quando um passo falha, **transações de compensação** desfazem o efeito dos passos anteriores; neste caso, a compensação libera o assento.

Os requisitos da proposta que pesam nesta decisão são:

1. **Compensação (liberação do assento) em 100% das reservas cujo pagamento falhar.**
2. **Liberação automática de reservas não pagas em até 10 minutos.**
3. **Nenhuma cobrança duplicada** em requisições repetidas com a mesma chave de idempotência.
4. **Todas as operações de escrita com chave de idempotência.**
5. **Resposta de erro controlada em menos de 1 segundo** quando o Serviço de Pagamentos estiver indisponível, com Circuit Breaker.
6. **Latência p95 da reserva abaixo de 300 ms** com 200 requisições por segundo.
7. Comunicação assíncrona por RabbitMQ (ADR-001).

Há duas formas de coordenar uma SAGA:

- **Coreografia:** não há coordenador. Cada serviço publica eventos sobre o que fez e reage aos eventos dos outros.
- **Orquestração:** um componente orquestrador guarda o estado da SAGA e envia comandos a cada participante, decidindo o próximo passo a partir das respostas (padrão *Process Manager*).

## Análise comparativa

| Critério | Coreografia | Orquestração |
| :--- | :--- | :--- |
| **Onde fica a lógica do fluxo** | Distribuída entre os participantes | Concentrada no orquestrador |
| **Estado de uma compra** | Implícito; reconstruído juntando eventos de vários serviços | Explícito, persistido em um único lugar |
| **Garantia de compensação** | Cada serviço precisa tratar as falhas que lhe dizem respeito; casos podem ficar sem dono | Decidida em um único ponto, que conhece todos os caminhos de falha |
| **Acoplamento** | Cada participante conhece os eventos dos outros, com risco de dependências cíclicas | Participantes conhecem apenas os próprios comandos; só o orquestrador conhece o fluxo |
| **Testabilidade** | Exige subir vários serviços para verificar um fluxo | A máquina de estados pode ser testada isoladamente |
| **Adequação** | Fluxos curtos, com poucos participantes e poucas falhas | Fluxos com várias etapas, prazos e caminhos de compensação |
| **Risco principal** | Fluxo difícil de entender e depurar | Orquestrador concentrar lógica de negócio que pertence aos participantes |

O fluxo do TicketFlow tem prazo, pelo menos quatro caminhos que levam à compensação (pagamento recusado, expiração, cancelamento pelo usuário e assento indisponível) e um critério explícito de compensação em 100% dos casos. Esse é o perfil em que a orquestração se mostra mais adequada.

## Decisão

### 1. SAGA orquestrada, com o orquestrador dentro do Serviço de Reservas

O orquestrador é um **componente do Serviço de Reservas**, e não um serviço separado. Ele fica em um módulo próprio, isolado das regras de reserva, e é o único ponto que conhece o fluxo completo da compra.

Cada compra corresponde a um ingresso, e os dois compartilham o mesmo identificador. O estado da SAGA é gravado no banco do Serviço de Reservas, **na mesma transação** que o ingresso. Assim o estado da compra existe em um só lugar. O status exibido ao usuário no ingresso é derivado do estado da SAGA:

| Estado da SAGA | Status do ingresso |
| :--- | :--- |
| `INICIADA`, `AGUARDANDO_PAGAMENTO`, `PROCESSANDO_PAGAMENTO` | `PENDENTE_PAGAMENTO` |
| `CONFIRMANDO`, `CONCLUIDA` | `PAGO` |
| `COMPENSANDO`, `CANCELADA` | `CANCELADO` |

Cada compra tem um **prazo de pagamento** de **9 minutos**, contado a partir da criação. O prazo é menor que os 10 minutos do critério de sucesso porque a liberação do assento acontece depois do vencimento: a expiração precisa ser detectada pela rotina agendada, que roda a cada 30 segundos, e o comando de liberação precisa ser publicado e processado, o que leva segundos. Com os serviços em funcionamento, o assento é liberado em até 9 minutos e meio mais esse tempo de processamento, dentro dos 10 minutos. O mesmo prazo é usado como validade da trava do assento no Redis (ADR-003).

### 2. Contrato com o usuário

A reserva é assíncrona: a resposta ao usuário não espera o Serviço de Eventos e Assentos. O usuário acompanha o andamento consultando o ingresso.

| Operação | Resposta |
| :--- | :--- |
| `POST /ingressos` (corpo: evento, assento, tipo de ingresso) | **202** com o identificador do ingresso, o status e o prazo de pagamento. **409** se o assento estiver em disputa (ADR-003). |
| `GET /ingressos/{id}` | **200** com o status atual. É a forma de o usuário acompanhar a reserva. |
| `POST /ingressos/{id}/pagamento` | **200** com o resultado (aprovado ou recusado). **202** com status "processando" quando o resultado é desconhecido. **503** quando a requisição não chegou a ser processada pelo Serviço de Pagamentos. **409** se o estado da compra não permitir o pagamento. |
| `POST /ingressos/{id}/cancelamento` | **200** com o ingresso cancelado. **409** se o estado da compra não permitir o cancelamento. |

A meta de **p95 abaixo de 300 ms** se refere ao `POST /ingressos`, que executa apenas uma consulta e uma transação local no banco (compra e mensagem no outbox) e a trava no Redis. Com o Serviço de Pagamentos indisponível, o `POST /ingressos/{id}/pagamento` responde em menos de 1 segundo com 503 ou com 202 "processando"; as duas são respostas controladas (item 5).

**Chave de idempotência nas escritas.** As três operações de escrita exigem o cabeçalho `Idempotency-Key`, gerado pelo cliente. Junto com cada chave é guardado um resumo (*hash*) do corpo da requisição. Uma requisição com a mesma chave e corpo diferente recebe **422**, em vez de ser confundida com a original.

- **Criação:** o identificador da compra é derivado de forma determinística do usuário e da chave (UUID baseado em nome). O Serviço de Reservas executa os passos nesta ordem:
  1. procura a compra pelo identificador derivado no banco; se ela existe, compara o resumo do corpo: se for igual, devolve a compra, seja qual for o seu estado, e se for diferente, responde 422. Em nenhum dos casos o Redis é acessado;
  2. tenta criar a trava do assento no Redis (ADR-003). Se a trava já pertence ao mesmo identificador, há outra requisição igual em andamento: o serviço consulta o banco de novo e, se a compra ainda não existir, responde **409** "requisição em processamento", para o cliente repetir. Se a trava pertence a outra compra, responde **409** "assento em disputa";
  3. grava a compra e a mensagem no outbox em uma transação. Uma violação da restrição de unicidade do identificador indica que outra requisição com a mesma chave gravou primeiro: a compra existente é devolvida se o resumo do corpo for igual, ou a resposta é 422 se for diferente. Sempre que esta requisição não grava a compra, a trava que ela criou é removida (comparar e remover).
- **Pagamento e cancelamento:** cada chave é registrada com restrição de unicidade, associada à compra e à operação, **na mesma transação** que produz o efeito da operação (a criação da tentativa de pagamento ou a transição para `COMPENSANDO`). Uma requisição repetida não aplica a operação de novo e recebe o estado atual da compra. Quando a tentativa de pagamento termina sem cobrança e a compra volta para `AGUARDANDO_PAGAMENTO`, seja por um resultado "sem cobrança" ou por anulação (item 5), o registro da chave é removido na mesma transação, para que o cliente possa repetir o pagamento com a mesma chave.

### 3. Máquina de estados explícita

```
 INICIADA ───── assento indisponível ──────────────────────────────┐
   │    │                                                          │
   │    └────── expirou ou cancelado ─────────────┐                │
   │ assento                                      │                │
   │ reservado                                    │                │
   ▼                                              ▼                │
 AGUARDANDO_PAGAMENTO ── expirou ou cancelado ─► COMPENSANDO       │
   │            ▲                                 ▲    │           │
   │ usuário    │ sem cobrança,                   │    │ assento   │
   │ paga       │ ou anulada                      │    │ liberado  │
   ▼            │ no prazo                        │    ▼           │
 PROCESSANDO_PAGAMENTO ── recusado, ou ───────────┘   CANCELADA ◄──┘
   │                      anulado fora do prazo
   │ pagamento aprovado
   ▼
 CONFIRMANDO ── assento vendido ──► CONCLUIDA
```

| Estado | Ação ao entrar | Gatilho | Próximo estado |
| :--- | :--- | :--- | :--- |
| `INICIADA` | Cria o ingresso, define o prazo de pagamento e envia o comando `assento.reservar` | Resposta `assento.reservado` | `AGUARDANDO_PAGAMENTO` |
| | | Resposta `assento.indisponivel` | `CANCELADA` (nada a compensar) |
| | | Prazo vencido ou cancelamento pelo usuário | `COMPENSANDO` |
| `AGUARDANDO_PAGAMENTO` | — | Usuário solicita o pagamento dentro do prazo | `PROCESSANDO_PAGAMENTO` (nova tentativa) |
| | | Prazo vencido ou cancelamento pelo usuário | `COMPENSANDO` |
| `PROCESSANDO_PAGAMENTO` | Cria uma tentativa marcada como de resultado desconhecido e faz a única chamada de cobrança da tentativa | Pagamento aprovado | `CONFIRMANDO` |
| | | Pagamento recusado | `COMPENSANDO` |
| | | Chamada sem cobrança | `AGUARDANDO_PAGAMENTO` (a tentativa é desfeita) |
| | | Resultado desconhecido | Permanece |
| | | Tentativa anulada, dentro do prazo | `AGUARDANDO_PAGAMENTO` |
| | | Tentativa anulada, fora do prazo | `COMPENSANDO` |
| | | Nova solicitação do usuário | Permanece e responde 202 "processando", sem nova chamada |
| `CONFIRMANDO` | Envia o comando `assento.vender` | Resposta `assento.vendido` | `CONCLUIDA` |
| | | Resposta `assento.venda_recusada` | Permanece e é sinalizada para intervenção manual |
| `CONCLUIDA` | Publica o evento `compra.concluida` para o Serviço de Notificações | — | Final |
| `COMPENSANDO` | Envia o comando `assento.liberar` | Resposta `assento.liberado` | `CANCELADA` |
| | | Resposta `assento.liberacao_recusada` | Permanece e é sinalizada para intervenção manual |
| | | Resposta `assento.reservado` atrasada | Permanece e reenvia `assento.liberar` |
| `CANCELADA` | Remove a trava do assento no Redis (ADR-003) | Resposta `assento.reservado` atrasada | Permanece e reenvia `assento.liberar` |
| | | Pedido de verificação `assento.verificar` (ADR-003) | Permanece e reenvia `assento.liberar` |

### 4. Regras gerais da máquina

1. **Transição inexistente é rejeitada.** Um gatilho que não consta da tabela para o estado atual não altera o estado. Se o gatilho for uma mensagem, ela é descartada e registrada em log. Se for uma ação do usuário, ele recebe HTTP 409 com o estado atual da compra. Exemplos: cancelar uma compra em `PROCESSANDO_PAGAMENTO`, ou pedir o pagamento de uma compra já `CONCLUIDA`.
2. **Gatilhos simultâneos.** A mudança de estado usa controle de concorrência otimista (versão do registro). Se dois gatilhos chegam ao mesmo tempo para a mesma compra, apenas um é aplicado. O perdedor é reavaliado contra o novo estado: uma mensagem é reentregue pelo broker; uma ação do usuário recebe HTTP 409.
3. **Resposta atrasada depois da compensação.** Com consumidores concorrentes, o comando `assento.liberar` pode ser processado antes de um `assento.reservar` enviado antes dele. Nesse caso, o assento fica reservado para uma compra em compensação ou já cancelada. Por isso, uma resposta `assento.reservado` que chega em `COMPENSANDO` ou `CANCELADA` faz o orquestrador reenviar `assento.liberar`. Como o comando de liberar só age sobre o assento da própria compra (ADR-003), o reenvio é seguro. Se esse reenvio se perder, a reconciliação do ADR-003 detecta o assento e pede uma verificação (`assento.verificar`), que leva a um novo `assento.liberar`.
4. **O pagamento aprovado é o ponto sem volta (transação pivô).** Antes dele, todos os passos podem ser compensados. Depois dele, o passo seguinte (`assento.vender`) não tem compensação: ele é reenviado até ser confirmado. Isso é consistente porque o assento está reservado para aquela compra desde `AGUARDANDO_PAGAMENTO`, e nem a expiração nem o cancelamento são aceitos em `PROCESSANDO_PAGAMENTO`: dali, a compra só segue para `COMPENSANDO` quando o Serviço de Pagamentos confirma que não houve cobrança (recusa ou anulação).
5. **Violações de invariante vão para intervenção manual.** As respostas `assento.venda_recusada` e `assento.liberacao_recusada` só ocorrem se as garantias do ADR-003 forem violadas. A compra é sinalizada para tratamento manual, em vez de seguir automaticamente para um estado possivelmente errado.
6. **Verificação de uma compra desconhecida.** Um pedido `assento.verificar` (ADR-003) para um identificador de compra que não existe no banco do Serviço de Reservas, situação possível apenas após uma restauração de backup, faz o orquestrador enviar `assento.liberar` e registrar um alerta. Sem compra, não há quem detenha o assento.
7. **Cancelamento depois do pagamento está fora do escopo.** O TicketFlow não implementa reembolso. Uma compra em `CONFIRMANDO` ou `CONCLUIDA` não pode ser cancelada pelo usuário.

### 5. Chamada ao Serviço de Pagamentos

**Uma tentativa, uma chamada.** Cada passagem de `AGUARDANDO_PAGAMENTO` para `PROCESSANDO_PAGAMENTO` cria uma **tentativa de pagamento**, numerada. A chave de idempotência enviada ao Serviço de Pagamentos é o identificador da compra combinado com o número da tentativa. Cada tentativa faz **exatamente uma chamada de cobrança**:

- um novo pedido de pagamento do usuário, com a compra em `PROCESSANDO_PAGAMENTO`, não gera outra chamada e recebe o estado atual ("processando");
- o cliente HTTP não faz novas tentativas automáticas;
- a rotina de verificação nunca repete a cobrança: ela só consulta ou anula (abaixo).

Só existe uma tentativa em aberto por compra, porque uma nova tentativa só nasce em `AGUARDANDO_PAGAMENTO`, e a compra só volta para esse estado quando a tentativa anterior certamente não gerou cobrança. Todo resultado, seja da chamada ou da operação resolver, é aplicado junto com o número da tentativa a que se refere: se não for a tentativa em aberto da compra, o resultado é descartado. Assim, um resultado atrasado de uma tentativa antiga nunca é aplicado sobre a seguinte.

**Classificação do resultado da chamada.** Quem envia uma requisição e não recebe resposta não consegue distinguir se ela se perdeu na ida, se o servidor falhou antes de processá-la ou se ela foi processada e a resposta se perdeu na volta. Por isso, cada resultado possível é classificado:

| Resultado da chamada | Classificação | Transição | Resposta ao usuário |
| :--- | :--- | :--- | :--- |
| Resposta `APROVADO` | Cobrado | `CONFIRMANDO` | 200 |
| Resposta `RECUSADO` | Sem cobrança, definitivo | `COMPENSANDO` | 200 com a recusa |
| Resposta `ANULADA`, circuito aberto, conexão recusada, tempo esgotado ao conectar, ou qualquer resposta 4xx | Sem cobrança: a requisição não chegou ou foi rejeitada antes de cobrar | `AGUARDANDO_PAGAMENTO`; a tentativa é desfeita e o usuário pode tentar de novo | 503 |
| Tempo esgotado após conectar, conexão interrompida após o envio, resposta 5xx, ou qualquer outro erro | **Resultado desconhecido**: a cobrança pode ter acontecido | Permanece em `PROCESSANDO_PAGAMENTO` | 202 "processando" |

A tentativa nasce marcada como de resultado desconhecido, na mesma transação que coloca a compra em `PROCESSANDO_PAGAMENTO`, antes da chamada. Como só existe uma chamada por tentativa, só ela pode desfazer a marca, e só ao receber um resultado "sem cobrança". Se o Serviço de Reservas cair durante a chamada, a marca permanece, e o assento não pode ser liberado enquanto a dúvida não for esclarecida.

**Recuperação por "consultar ou anular".** Uma tentativa de resultado desconhecido é esclarecida pela rotina de verificação (item 7), que chama a operação **resolver** do Serviço de Pagamentos com a chave da tentativa:

- se existe cobrança com essa chave, o Serviço de Pagamentos devolve o resultado dela, e a compra segue a transição correspondente;
- se não existe, o Serviço de Pagamentos registra a chave como **anulada** e responde `ANULADA`. A partir daí, qualquer cobrança com essa chave é recusada com `ANULADA`, inclusive a requisição original, se chegar atrasada. A compra volta para `AGUARDANDO_PAGAMENTO` se ainda estiver no prazo, para que o usuário possa tentar de novo com uma nova tentativa, ou vai para `COMPENSANDO` se o prazo já venceu.

A rotina só resolve tentativas criadas há mais tempo que o prazo da chamada somado a uma margem de 30 segundos, para não disputar com a chamada ainda em andamento. Se o Serviço de Pagamentos estiver fora, a rotina tenta de novo no ciclo seguinte. Nesse intervalo o assento continua reservado: é preferível segurá-lo a liberar o lugar de alguém que pode ter sido cobrado.

**Requisitos impostos ao Serviço de Pagamentos.** O desenho acima depende de três garantias, que fazem parte desta decisão:

1. **Cobrança em uma única transação local.** Como o pagamento é simulado, registrar a cobrança é uma única transação no banco do Serviço de Pagamentos, sem estado intermediário visível. A chave de idempotência tem restrição de unicidade: uma requisição repetida devolve o resultado da original, sem cobrar de novo, e duas requisições simultâneas com a mesma chave são serializadas pelo banco.
2. **Respostas explícitas.** O resultado é sempre `APROVADO`, `RECUSADO` ou `ANULADA`. Respostas 4xx só são usadas quando a requisição é rejeitada sem nenhuma cobrança.
3. **Resolver atômico.** A operação resolver ou devolve a cobrança existente, ou registra a chave como anulada, nunca as duas coisas. A restrição de unicidade da chave serializa a cobrança e a anulação.

**Tempos e Circuit Breaker.** A chamada tem tempo limite de conexão de 200 ms e prazo total de 700 ms. Somado ao trabalho local, o usuário recebe a resposta em menos de 1 segundo mesmo com o Serviço de Pagamentos lento ou fora do ar. O Circuit Breaker (Resilience4j) abre quando pelo menos 50% das últimas 20 chamadas falham, permanece aberto por 10 segundos e então libera 3 chamadas de teste. Esses valores são o ponto de partida e serão ajustados nos testes de carga e de resiliência.

### 6. Comunicação com os participantes

| Participante | Forma | Motivo |
| :--- | :--- | :--- |
| **Eventos e Assentos** | Comandos e respostas assíncronos pelo RabbitMQ. Os comandos são publicados pelo Transactional Outbox; as respostas são publicadas logo depois que a transação do comando é confirmada | Não há usuário esperando a resposta. A fila absorve indisponibilidades temporárias. As respostas dispensam o Outbox: se uma delas se perder, o orquestrador reenvia o comando pendente (item 7) e, como os comandos são idempotentes (ADR-003), a mesma resposta é gerada de novo. |
| **Pagamentos** | Chamada HTTP síncrona com Circuit Breaker e chave de idempotência | O usuário espera o resultado do pagamento. O critério de erro controlado em menos de 1 segundo só se aplica a uma chamada síncrona. Esta é a exceção, prevista no ADR-001, ao uso de mensagens entre serviços. |
| **Notificações** | Evento assíncrono pelo RabbitMQ (`compra.concluida`), publicado pelo Transactional Outbox | Notificar é consequência da compra, não um passo dela. Uma falha no envio não deve desfazer a compra. |

Os comandos para Eventos e Assentos são nomeados no imperativo (`assento.reservar`), e as respostas no particípio (`assento.reservado`). Assim fica explícito que o orquestrador **manda** e o participante **informa** o resultado. Os participantes não tomam decisões sobre o fluxo da compra.

A resposta `assento.reservado` informa o valor do assento no momento da reserva. Esse valor é gravado na compra e é o valor cobrado no pagamento, mesmo que o gestor altere o preço do assento depois.

O Serviço de Notificações registra o identificador de cada compra já notificada (padrão *Inbox*). Um `compra.concluida` reentregue é reconhecido e não gera um segundo envio ao usuário.

### 7. Expiração e verificação de pendências

Uma única rotina agendada, executada a cada 30 segundos, cuida de tudo o que depende de tempo ou de mensagens sem resposta:

- dispara a expiração das compras em `INICIADA` ou `AGUARDANDO_PAGAMENTO` com prazo vencido, inclusive as que voltaram de `PROCESSANDO_PAGAMENTO` depois do prazo;
- reenvia o comando pendente das compras paradas em `INICIADA`, `CONFIRMANDO` ou `COMPENSANDO`;
- resolve as tentativas de pagamento de resultado desconhecido, conforme o item 5.

Se duas execuções da rotina tentarem expirar a mesma compra, só a primeira tem efeito; a segunda encontra a compra em `COMPENSANDO`, e a transição é rejeitada.

## Alternativas consideradas

**SAGA coreografada.** Descartada. A regra "quando liberar o assento" ficaria distribuída: o Eventos e Assentos reagiria ao pagamento recusado, enquanto a expiração e o cancelamento seriam tratados pelo Serviço de Reservas. Um caso de falha sem dono, ou tratado por dois serviços ao mesmo tempo, compromete o critério de compensação em 100% dos casos. Além disso, o Eventos e Assentos passaria a depender do formato das mensagens do Serviço de Pagamentos, e o estado de uma compra só poderia ser reconstruído juntando eventos de vários serviços.

**Orquestrador como serviço independente.** Descartado. Exigiria mais um serviço, banco e implantação para coordenar um único fluxo. E o estado da compra ficaria duplicado: a SAGA no orquestrador e o ingresso no Serviço de Reservas, que precisariam ser mantidos consistentes entre si, recriando o problema de consistência distribuída que a SAGA deveria resolver.

**Motores e frameworks de SAGA (Temporal, Camunda, Axon).** Descartados. Oferecem orquestração durável pronta, mas exigem servidores próprios ou adotam um modelo de programação inteiro, o que pesa mais que o problema: a máquina de estados da compra tem oito estados e pode ser implementada e testada diretamente. Bibliotecas de máquina de estados, como o Spring Statemachine, podem ser usadas na implementação sem alterar esta decisão.

**Comunicação com Pagamentos por mensagens.** Descartada. Tornaria o fluxo inteiramente assíncrono e uniforme, mas o usuário deixaria de receber o resultado do pagamento na mesma requisição. O critério de erro controlado em menos de 1 segundo e o Circuit Breaker previstos na proposta perderiam o sentido, porque a fila já absorveria a indisponibilidade.

**Recuperar o resultado desconhecido repetindo a cobrança.** Descartada. Se a requisição original não chegou ao Serviço de Pagamentos, a repetição faria uma cobrança nova, possivelmente muito depois do prazo e sem o usuário presente. Também não haveria saída para uma tentativa que o Serviço de Pagamentos desconhece: o assento ficaria reservado até alguém pagar. A operação "consultar ou anular" encerra a dúvida nos dois sentidos.

**Idempotência das escritas apenas pela trava do Redis.** Descartada. Reconheceria uma criação repetida enquanto a trava existisse, mas não cobriria pagamento e cancelamento, e deixaria a idempotência depender de um armazenamento que pode falhar (ADR-003).

**Expiração por mensagem atrasada no RabbitMQ** (fila com TTL e dead letter exchange). Descartada. Daria precisão de segundos ao disparo da expiração, mas não dispensaria a rotina agendada, que continuaria necessária para os casos em que a mensagem é descartada ou se perde. Com prazo de 9 minutos e rotina a cada 30 segundos, a rotina sozinha já cumpre o critério de 10 minutos. A mensagem atrasada acrescentaria uma fila, uma mensagem no outbox por compra e o tratamento de dois disparos para a mesma compra, em troca de no máximo 30 segundos de precisão.

**Notificação de expiração pelo Redis (*keyspace notifications*).** Descartada. O Redis não garante a entrega dessas notificações: se o serviço estiver fora no momento em que a chave expira, o aviso se perde.

## Consequências

**Positivas**

- O estado de cada compra pode ser consultado em um único registro, e cada transição fica rastreável.
- Todos os caminhos de compensação passam pelo mesmo ponto, e a máquina de estados pode ser testada sem subir os outros serviços. Isso dá base para demonstrar o critério de compensação em 100% dos casos.
- Nenhuma combinação de falhas leva a cobrança duplicada nem a liberar o assento de um cliente cobrado: toda dúvida sobre o pagamento é resolvida por "consultar ou anular" antes de qualquer compensação.
- O Eventos e Assentos e o Pagamentos ficam simples: executam um comando e informam o resultado.
- Gatilhos duplicados, atrasados ou simultâneos (execuções simultâneas da rotina, mensagens repetidas, cliques repetidos) são neutralizados pela própria máquina de estados e pelas chaves de idempotência.

**Negativas e riscos**

- O Serviço de Reservas concentra a coordenação e se torna o componente mais crítico do fluxo. O orquestrador deve conter apenas a sequência da compra, deixando as regras de cada domínio nos respectivos serviços.
- O fluxo é híbrido: síncrono com o Serviço de Pagamentos e assíncrono com os demais. A equipe precisa tratar falhas dos dois tipos.
- O Serviço de Pagamentos ganha responsabilidades além da cobrança simulada: unicidade da chave, respostas explícitas e a operação resolver.
- Uma tentativa de resultado desconhecido mantém o assento reservado até o Serviço de Pagamentos responder, possivelmente além do prazo, e portanto além dos 10 minutos do critério de sucesso. É uma exceção consciente: o critério trata de reservas não pagas, e uma reserva cujo pagamento pode ter sido feito não pode ser liberada antes de a dúvida ser esclarecida. Quando o serviço está fora e a requisição não chegou a ele, o assento não fica preso, porque a compra volta para `AGUARDANDO_PAGAMENTO` e a expiração continua valendo.
- Enquanto uma tentativa tem resultado desconhecido, o usuário não pode repetir o pagamento: ele aguarda o esclarecimento pela rotina, o que leva até cerca de 1 minuto com o Serviço de Pagamentos no ar.
- Os tempos limite e os parâmetros do Circuit Breaker são estimativas iniciais e precisam ser validados nos testes.
- As respostas `assento.venda_recusada` e `assento.liberacao_recusada` exigem intervenção manual. Elas só ocorrem se as garantias do ADR-003 forem violadas.
- Reembolso e cancelamento depois do pagamento ficam fora do escopo.
- Os participantes devem ser idempotentes, porque a entrega é "pelo menos uma vez" (ADR-001) e o orquestrador reenvia comandos pendentes. A forma de garantir isso no status do assento é tratada no ADR-003.
