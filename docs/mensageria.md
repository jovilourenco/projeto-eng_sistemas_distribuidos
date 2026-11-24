# Contrato de mensagens: comandos de assento da SAGA

Este documento descreve as mensagens trocadas entre o orquestrador da SAGA de compra (Serviço de Reservas) e o Serviço de Eventos e Assentos. As decisões que o justificam estão nos ADRs 001, 002, 003 e 004, em `docs/adr/`.

## Topologia no RabbitMQ

| Elemento | Nome | Tipo | Quem declara |
| :--- | :--- | :--- | :--- |
| Exchange de comandos | `ticketflow.assento` | topic, durável | Eventos e Assentos |
| Fila de comandos | `evento-assento.comandos` | durável | Eventos e Assentos |
| Exchange de mensagens mortas | `ticketflow.assento.dlx` | fanout, durável | Eventos e Assentos |
| Fila de mensagens mortas | `evento-assento.comandos.dlq` | durável | Eventos e Assentos |
| Exchange de respostas | `ticketflow.reserva` | topic, durável | Eventos e Assentos (o Serviço de Reservas também pode declarar, com os mesmos parâmetros) |

O **tipo da mensagem é a routing key**. Todas as mensagens são JSON (`content-type: application/json`) e persistentes. Quem publica o texto JSON já pronto (como o relay do Outbox) precisa informar esse `content-type`: sem ele, o consumidor não converte a mensagem, e ela vai para a fila de mensagens mortas.

## Comandos (Reservas → Eventos e Assentos)

Publicados no exchange `ticketflow.assento`. Cada comando vale para **todos os assentos de uma compra (de 1 a 4)**, com a regra do tudo ou nada (ADR-004): ou todos os assentos mudam de status, ou nenhum muda.

| Routing key | Transição | Respostas possíveis |
| :--- | :--- | :--- |
| `assento.reservar` | `DISPONIVEL` → `RESERVADO` | `assento.reservado`, `assento.indisponivel` |
| `assento.vender` | `RESERVADO` → `VENDIDO` | `assento.vendido`, `assento.venda_recusada` |
| `assento.liberar` | `RESERVADO` → `DISPONIVEL` | `assento.liberado`, `assento.liberacao_recusada` |

```json
{
  "idMensagem": "5f0c2a8e-8d1b-4a5e-9b7a-3c2d1e0f9a11",
  "idCompra": "0b6f7c55-3c1e-5a8e-9d4f-6a2b1c3d4e5f",
  "idEvento": 1,
  "idAssentos": [7, 8],
  "dataHora": "2026-11-02T14:30:00Z"
}
```

| Campo | Obrigatório | Descrição |
| :--- | :--- | :--- |
| `idMensagem` | sim | Identificador do comando. É devolvido na resposta, no campo `idComando`. |
| `idCompra` | sim | Compra que pede a transição. É ela que passa a deter os assentos. |
| `idEvento` | em `assento.reservar` | Evento ao qual os assentos devem pertencer. |
| `idAssentos` | sim | De 1 a 4 assentos distintos, alvo do comando. |
| `dataHora` | não | Momento em que o comando foi emitido (ISO-8601, UTC). |

Um comando sem os campos obrigatórios, com mais de 4 assentos ou com assento repetido, ou que não seja JSON válido, nunca é aplicado e vai diretamente para a fila de mensagens mortas, sem novas tentativas.

Os assentos são alterados em ordem crescente de id, numa única transação. Duas compras que disputam assentos em comum travam as linhas na mesma ordem, e por isso uma espera a outra, sem deadlock.

## Respostas (Eventos e Assentos → Reservas)

Publicadas no exchange `ticketflow.reserva`, com a routing key igual ao campo `tipo`. Para receber todas, o Serviço de Reservas liga a sua fila com o padrão `assento.#`. `idAssentos` repete os assentos do comando, na mesma ordem.

```json
{
  "idMensagem": "c3e8b1f2-4a6d-4e2b-9f1a-7d5c3b2a1e09",
  "idComando": "5f0c2a8e-8d1b-4a5e-9b7a-3c2d1e0f9a11",
  "idCompra": "0b6f7c55-3c1e-5a8e-9d4f-6a2b1c3d4e5f",
  "idAssentos": [7, 8],
  "tipo": "assento.reservado",
  "valores": [
    { "idAssento": 7, "valor": 250.00 },
    { "idAssento": 8, "valor": 180.00 }
  ],
  "dataHora": "2026-11-02T14:30:00.120Z"
}
```

| Tipo | Significado |
| :--- | :--- |
| `assento.reservado` | Todos os assentos estão reservados para a compra. `valores` traz o preço de cada assento no momento da reserva; a soma é o valor a cobrar. Nas demais respostas, `valores` vem `null`. |
| `assento.indisponivel` | Pelo menos um assento é de outra compra, está inativo, não existe ou não pertence ao evento informado. Nenhum assento foi reservado. |
| `assento.vendido` | Todos os assentos estão vendidos para a compra. |
| `assento.venda_recusada` | A compra não detém pelo menos um dos assentos, e nenhum foi vendido. Indica violação das garantias da SAGA: a compra deve ir para intervenção manual. |
| `assento.liberado` | Nenhum dos assentos está mais reservado para a compra, seja porque foram liberados agora, seja porque a compra já não os detinha. |
| `assento.liberacao_recusada` | Pelo menos um assento já foi vendido para a própria compra, e nenhum foi liberado. Indica violação das garantias da SAGA: a compra deve ir para intervenção manual. |

## Garantias e responsabilidades

- **Os comandos são idempotentes.** Repetir um comando da mesma compra produz a mesma resposta. Por isso o orquestrador pode reenviar qualquer comando sem resposta.
- **A resposta é publicada logo depois que a transação do comando é confirmada.** Se ela se perder, por exemplo com uma queda do serviço entre a confirmação e a publicação, o reenvio do comando pelo orquestrador gera a mesma resposta de novo.
- **O valor a cobrar é o da primeira resposta `assento.reservado`.** Uma resposta repetida (por reenvio do comando) lê o preço atual dos assentos; o orquestrador grava os `valores` da primeira e ignora os das seguintes.
- **Respostas podem chegar repetidas ou fora de ordem.** O orquestrador deve aplicá-las conforme a sua máquina de estados (ADR-002) e reenviar `assento.liberar` se receber `assento.reservado` de uma compra em compensação ou cancelada.
- **O consumidor deve usar o próprio tipo de destino** e ignorar o cabeçalho `__TypeId__`, que contém nomes de classes Java do serviço publicador.
- **Tentativas e mensagens mortas:** um comando cujo processamento falha (por exemplo, com o banco fora do ar) é tentado até 3 vezes, com intervalo crescente, e então vai para `evento-assento.comandos.dlq`. A mensagem morta mantém a routing key original. Para reprocessá-la, basta publicá-la de novo no exchange `ticketflow.assento` com a mesma routing key; como os comandos são idempotentes, isso é seguro. De qualquer forma, o orquestrador reenvia os comandos que ficaram sem resposta.
