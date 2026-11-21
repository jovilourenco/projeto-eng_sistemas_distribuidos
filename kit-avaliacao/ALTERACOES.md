# Alterações em relação ao kit original

A cópia do kit entrou sem alterações no commit `f5fbdd2`. Tudo o que mudou está nos commits seguintes desta pasta; `git diff f5fbdd2 HEAD -- kit-avaliacao` mostra a diferença completa. As decisões estão no ADR-006.

## O que não mudou (exigência do professor)

- A massa de dados (`dados/`).
- Os cenários e perfis (`testes/cenarios.js`).
- As invariantes conferidas (`verificador/`).

## O que mudou e por quê

| Arquivo | Mudança | Motivo |
| :--- | :--- | :--- |
| `adaptadores/grupo_01.json` | Rotas, campos e status do TicketFlow | O nosso contrato usa outros nomes (`/ingressos`, `PAGO`, `CANCELADO`) |
| `carga/` (novo) | Reset e carga dos dados por SQL e Redis, e geração do mapa de ids | As rotas `/_teste` não existem no código de produção |
| `testes/ids.js` (novo) e `testes/lib.js` | Tradução entre os códigos do kit (`E1`, `ARENA-A-01-001`, `U000123`) e os ids inteiros do sistema | O TicketFlow usa ids inteiros |
| `executar.sh` | Chama `carga/` no lugar das rotas `/_teste` e informa o caminho do mapa de ids ao k6 | Consequência das duas linhas acima |

## A fazer

- `verificador/verificar.py`: ler o estado final dos bancos no lugar de `GET /_teste/vendas`, e converter os valores de reais para centavos.
- Fechar o adaptador quando a compra com vários assentos e o pagamento estiverem prontos.
