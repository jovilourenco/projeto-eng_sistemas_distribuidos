# Contrato de mensagens: comandos de assento da SAGA

Este documento descreve as mensagens trocadas entre o orquestrador da SAGA de compra (Serviço de Reservas) e o Serviço de Eventos e Assentos. As decisões que o justificam estão nos ADRs 001, 002 e 003, em `docs/adr/`.

## Topologia no RabbitMQ

| Elemento | Nome | Tipo | Quem declara |
| :--- | :--- | :--- | :--- |
| Exchange de comandos | `ticketflow.assento` | topic, durável | Eventos e Assentos |
| Fila de comandos | `evento-assento.comandos` | durável | Eventos e Assentos |
| Exchange de mensagens mortas | `ticketflow.assento.dlx` | fanout, durável | Eventos e Assentos |
| Fila de mensagens mortas | `evento-assento.comandos.dlq` | durável | Eventos e Assentos |
| Exchange de respostas | `ticketflow.reserva` | topic, durável | Eventos e Assentos (o Serviço de Reservas também pode declarar, com os mesmos parâmetros) |

O **tipo da mensagem é a routing key**. Todas as mensagens são JSON (`content-type: application/json`) e persistentes.

## Comandos (Reservas → Eventos e Assentos)

Publicados no exchange `ticketflow.assento`.

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
  "idAssento": 7,
  "dataHora": "2026-11-02T14:30:00Z"
}
```

| Campo | Obrigatório | Descrição |
| :--- | :--- | :--- |
| `idMensagem` | sim | Identificador do comando. É devolvido na resposta, no campo `idComando`. |
| `idCompra` | sim | Compra que pede a transição. É ela que passa a deter o assento. |
| `idEvento` | em `assento.reservar` | Evento ao qual o assento deve pertencer. |
| `idAssento` | sim | Assento alvo do comando. |
| `dataHora` | não | Momento em que o comando foi emitido (ISO-8601, UTC). |

Um comando sem os campos obrigatórios, ou que não seja JSON válido, nunca é aplicado e vai diretamente para a fila de mensagens mortas, sem novas tentativas.

## Respostas (Eventos e Assentos → Reservas)

Publicadas no exchange `ticketflow.reserva`, com a routing key igual ao campo `tipo`. Para receber todas, o Serviço de Reservas liga a sua fila com o padrão `assento.#`.

```json
{
  "idMensagem": "c3e8b1f2-4a6d-4e2b-9f1a-7d5c3b2a1e09",
  "idComando": "5f0c2a8e-8d1b-4a5e-9b7a-3c2d1e0f9a11",
  "idCompra": "0b6f7c55-3c1e-5a8e-9d4f-6a2b1c3d4e5f",
  "idAssento": 7,
  "tipo": "assento.reservado",
  "valor": 250.00,
  "dataHora": "2026-11-02T14:30:00.120Z"
}
```

| Tipo | Significado |
| :--- | :--- |
| `assento.reservado` | O assento está reservado para a compra. `valor` traz o preço do assento no momento da reserva, que é o valor a cobrar. Nas demais respostas, `valor` vem `null`. |
| `assento.indisponivel` | Outra compra detém o assento, ou ele está inativo, não existe ou não pertence ao evento informado. |
| `assento.vendido` | O assento está vendido para a compra. |
| `assento.venda_recusada` | A compra não detém o assento. Indica violação das garantias da SAGA: a compra deve ir para intervenção manual. |
| `assento.liberado` | O assento não está mais reservado para a compra, seja porque foi liberado agora, seja porque a compra já não o detinha. |
| `assento.liberacao_recusada` | O assento já foi vendido para a própria compra. Indica violação das garantias da SAGA: a compra deve ir para intervenção manual. |

## Garantias e responsabilidades

- **Os comandos são idempotentes.** Repetir um comando da mesma compra produz a mesma resposta. Por isso o orquestrador pode reenviar qualquer comando sem resposta.
- **A resposta é publicada logo depois que a transação do comando é confirmada.** Se ela se perder, por exemplo com uma queda do serviço entre a confirmação e a publicação, o reenvio do comando pelo orquestrador gera a mesma resposta de novo.
- **Respostas podem chegar repetidas ou fora de ordem.** O orquestrador deve aplicá-las conforme a sua máquina de estados (ADR-002) e reenviar `assento.liberar` se receber `assento.reservado` de uma compra em compensação ou cancelada.
- **O consumidor deve usar o próprio tipo de destino** e ignorar o cabeçalho `__TypeId__`, que contém nomes de classes Java do serviço publicador.
- **Tentativas e mensagens mortas:** um comando cujo processamento falha (por exemplo, com o banco fora do ar) é tentado até 3 vezes, com intervalo crescente, e então vai para `evento-assento.comandos.dlq`. A mensagem morta mantém a routing key original. Para reprocessá-la, basta publicá-la de novo no exchange `ticketflow.assento` com a mesma routing key; como os comandos são idempotentes, isso é seguro. De qualquer forma, o orquestrador reenvia os comandos que ficaram sem resposta.
