# Registros de Decisões Arquiteturais (ADRs)

Cada ADR registra uma decisão técnica relevante do TicketFlow: o contexto que exigiu a decisão, o que foi decidido, as alternativas avaliadas e as consequências da escolha.

Um ADR fica "Pendente" enquanto está em revisão no PR e passa a "Aceito" após a aprovação. Um ADR aceito não é editado. Se a decisão mudar, um novo ADR é criado com status "Aceito", e o antigo passa a "Substituído por ADR-00X".

| ADR | Título | Status |
| :--- | :--- | :--- |
| [ADR-001](ADR-001-broker-de-mensagens.md) | Broker de mensagens | Pendente |
| [ADR-002](ADR-002-saga-orquestrada-pelo-servico-de-reservas.md) | Coordenação da SAGA de compra | Pendente |
| [ADR-003](ADR-003-fonte-da-verdade-do-status-do-assento.md) | Fonte da verdade do status do assento | Pendente |

## Próximos ADRs

Decisões já identificadas como necessárias, a registrar antes da implementação das partes correspondentes:

| ADR | Tema | Decisões em aberto |
| :--- | :--- | :--- |
| ADR-004 | Transactional Outbox | Publicação por consulta periódica à tabela ou por captura de mudanças (CDC); frequência da publicação; marcação como enviada apenas após *publisher confirm* (ADR-001); ordem de publicação; limpeza das mensagens já enviadas; concorrência entre várias instâncias do publicador |
| ADR-005 | API Gateway e segurança | Onde o token JWT emitido pelo Keycloak é validado (só no gateway ou também em cada serviço); papéis de usuário comum e gestor de eventos; algoritmo e chave do *rate limiting* (por usuário ou por IP); uso da mesma instância de Redis para *rate limiting* e para as travas de assento |

## Modelo

```markdown
# ADR-00X: Título

- **Status:** Pendente | Aceito | Substituído por ADR-00Y
- **Data:** aaaa-mm-dd
- **Issue:** #N

## Contexto
## Decisão
## Alternativas consideradas
## Consequências
```
