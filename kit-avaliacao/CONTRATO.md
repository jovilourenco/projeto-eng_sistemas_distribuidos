# Contrato de teste — projetos de venda de ingressos

**Este contrato é uma sugestão.** Ele descreve o que o kit espera encontrar por padrão nos projetos de ingressos (grupos 01, 02, 07, 08 e 09). Cada equipe pode adotá-lo como está ou adaptar o kit à API que já tem:

- **Diferenças de nome** (rota, campo, código de status, valores de status) se resolvem no adaptador do grupo (`adaptadores/grupo_XX.json`), sem mexer no código do kit.
- **Diferenças de forma** (outro fluxo de compra, pagamento assíncrono com outro modelo, autenticação por token, rotas de teste com outro formato) podem ser resolvidas alterando os arquivos do kit (`testes/lib.js`, `verificador/verificar.py`) numa cópia do grupo.

Para a comparação continuar justa, quem adaptar o kit mantém três coisas iguais: **a massa de dados** (`dados/`), **os cenários e perfis de carga** (`testes/cenarios.js`) e **as invariantes conferidas** (seção 2.3 e `verificador/`). As alterações ficam versionadas no repositório do grupo, numa pasta `kit-avaliacao/`, com um parágrafo no README explicando o que mudou e por quê.

Nada aqui muda a arquitetura de ninguém: as rotas podem ficar atrás do API Gateway do grupo.

Todas as requisições e respostas são JSON (UTF-8). Valores em dinheiro são **inteiros em centavos**.

## 1. Rotas de negócio

| Operação | Rota padrão | Sucesso | Recusa esperada |
|---|---|---|---|
| Listar assentos de um setor | `GET /eventos/{evento_id}/assentos?setor={setor}` | 200 | 404 evento inexistente |
| Reservar assentos | `POST /reservas` | 201 | 409 assento indisponível |
| Pagar reserva | `POST /reservas/{reserva_id}/pagamento` | 200 | 402 pagamento recusado; 409 reserva expirada ou cancelada |
| Consultar reserva | `GET /reservas/{reserva_id}` | 200 | 404 |

### 1.1 Listar assentos

Resposta:

```json
{ "evento_id": "E1", "setor": "A",
  "assentos": [ { "assento_id": "ARENA-A-01-001", "status": "disponivel" } ] }
```

`status` é um de `disponivel`, `reservado` ou `vendido`.

### 1.2 Reservar

Cabeçalho obrigatório: `Idempotency-Key: <texto>`. Corpo:

```json
{ "evento_id": "E1", "usuario_id": "U000123", "assento_ids": ["ARENA-A-01-001", "ARENA-A-01-002"] }
```

Resposta 201:

```json
{ "reserva_id": "...", "status": "reservada", "valor_total": 30000, "expira_em": "2026-12-01T20:00:30Z" }
```

Regras:

- A reserva é **tudo ou nada**: se um dos assentos estiver indisponível, nenhum é reservado e a resposta é 409.
- A mesma `Idempotency-Key` repetida (inclusive em paralelo) devolve a **mesma** reserva, com 200 ou 201, e não reserva nada a mais.
- A reserva expira depois de `ttl_reserva_segundos` (definido na carga de dados, seção 2.2). Ao expirar, os assentos voltam a `disponivel`.

### 1.3 Pagar

Cabeçalho obrigatório: `Idempotency-Key: <texto>`. Corpo:

```json
{ "metodo": "pix", "simulacao": "aprovar" }
```

`simulacao` controla o provedor de pagamento simulado do grupo:

| Valor | Comportamento esperado do provedor simulado |
|---|---|
| `aprovar` | Aprova na hora |
| `recusar` | Recusa; a reserva é cancelada e os assentos voltam a `disponivel` (compensação) |
| `atrasar` | Responde em `atraso_pagamento_ms` (seção 2.2) e aprova |

Resposta 200: `{ "reserva_id": "...", "status": "confirmada", "valor_cobrado": 30000 }`. Recusa: 402 com `"status": "recusada"`.

A mesma `Idempotency-Key` repetida devolve o mesmo resultado e **não cobra de novo**.

Respostas 202 (processamento assíncrono) são aceitas: nesse caso o kit consulta `GET /reservas/{id}` até o status final (`confirmada`, `recusada`, `expirada` ou `cancelada`), por até 30 s.

## 2. Rotas de teste

Ficam fora do caminho de produção (podem exigir o cabeçalho `X-Chave-Teste`, configurado no adaptador). São usadas só pelo kit.

### 2.1 `POST /_teste/reset`

Apaga reservas, vendas, pagamentos e chaves de idempotência. Responde 200 ou 204.

### 2.2 `POST /_teste/carga`

Recebe o arquivo `dados/carga.json` inteiro e carrega locais, eventos, assentos, preços e usuários. Responde 200 ou 201. O objeto `config` define:

```json
{ "ttl_reserva_segundos": 20, "atraso_pagamento_ms": 3000 }
```

### 2.3 `GET /_teste/vendas?evento_id={id}`

Estado final, para o verificador de invariantes:

```json
{ "evento_id": "E1",
  "vendas": [ { "reserva_id": "...", "assento_id": "...", "usuario_id": "...", "idempotency_key": "..." } ],
  "pagamentos_aprovados": [ { "reserva_id": "...", "valor": 30000, "idempotency_key": "..." } ],
  "reservas": [ { "reserva_id": "...", "status": "confirmada", "assento_ids": ["..."], "idempotency_key": "..." } ] }
```

- `vendas`: uma linha por assento vendido (reserva confirmada).
- `pagamentos_aprovados`: uma linha por cobrança aprovada. Cobrança duplicada aparece como duas linhas.
- `reservas`: todas as reservas criadas no evento, em qualquer status.

## 3. Autenticação

Se o gateway exige token, o adaptador do grupo informa um cabeçalho fixo (`cabecalhos`) válido durante o teste, ou o grupo desliga a autenticação no perfil de teste. O usuário da compra vai sempre no corpo (`usuario_id`).
