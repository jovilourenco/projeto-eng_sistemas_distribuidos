# Kit de avaliação comparativa — projetos de venda de ingressos

Engenharia de Sistemas Distribuídos 2026.2 · UFPB · grupos 01 (TicketFlow), 02 (Ingressos), 07 (eTiquete), 08 (CineSeat) e 09 (Ticket Rush).

O kit aplica **a mesma massa de dados e os mesmos cenários de teste** aos cinco projetos de ingressos e produz uma tabela comparativa (`resultados/comparativo.csv`). Ele mede duas coisas:

- **Correção:** invariantes de negócio que um sistema de ingressos não pode violar, como vender o mesmo assento duas vezes ou cobrar sem entregar o ingresso. São conferidas no estado final do sistema, não só nas respostas HTTP.
- **Desempenho e resiliência:** latência (p95), vazão, taxa de erro e comportamento sob pico, disputa e falha de pagamento.

## O que cada grupo precisa fazer

1. Ligar o kit à API do projeto. O caminho mais curto é seguir o [`CONTRATO.md`](CONTRATO.md), mas **o contrato é uma sugestão**: a equipe pode adaptar o kit à API que já tem (próxima seção).
2. Conferir e completar o adaptador do grupo em `adaptadores/grupo_XX.json`. O campo `pendencias_do_grupo` diz o que falta confirmar.
3. Rodar o kit no próprio ambiente antes da entrega: `./executar.sh adaptadores/grupo_XX.json leve`.

O servidor de referência (`referencia/servidor.py`) implementa o contrato sem adaptações. Ele serve de exemplo do comportamento esperado e passa em todas as invariantes no perfil `leve`. **Não é um sistema distribuído**: usa um único lock em memória e, nos perfis `padrao` e `pesado`, acumula timeouts. Em `resultados/exemplo_referencia_leve/` estão os resultados dele, para conferir o formato das saídas.

## Adaptar o kit à API do seu projeto

O contrato existe para que todos os projetos sejam testados do mesmo jeito, não para impor uma API. Há dois níveis de adaptação:

| Diferença | Onde ajustar | Exemplos |
|---|---|---|
| Nomes | Só no adaptador (`adaptadores/grupo_XX.json`) | Rota `POST /api/orders` em vez de `/reservas`; campo `orderId` em vez de `reserva_id` (aceita caminho com ponto, como `data.orderId`); `201` para sucesso e `422` para conflito; status `PAID` em vez de `confirmada`; token do Keycloak em `cabecalhos` |
| Forma | Numa cópia do kit no repositório do grupo | Compra em mais de um passo (carrinho, depois pedido); pagamento por webhook; rotas de teste em outro formato; carga dos dados por script SQL em vez de `POST /_teste/carga` |

Para adaptar a forma, os pontos de mudança são poucos:

- `testes/lib.js`: as funções `reqReservar`, `reqPagar`, `lerReserva`, `lerPagamento` e `fluxoCompra` montam e interpretam as chamadas.
- `executar.sh`: as duas chamadas `curl` que zeram e carregam os dados antes de cada cenário.
- `verificador/verificar.py`: a função `buscar_vendas`, que lê o estado final. Ela pode consultar o banco do grupo em vez de uma rota HTTP, desde que devolva vendas, cobranças e reservas no formato da seção 2.3 do contrato.

**O que não pode mudar**, para a comparação valer: a massa de dados (`dados/`), os cenários e perfis (`testes/cenarios.js`) e as invariantes (`verificador/`). Quem adaptar o kit versiona a cópia no próprio repositório, numa pasta `kit-avaliacao/`, com um parágrafo no README dizendo o que mudou e por quê.

## Massa de dados

Gerada por `dados/gerar_dados.py` com semente fixa (2026): rodar de novo produz arquivos idênticos.

| Item | Quantidade |
|---|---|
| Locais | 4 (Arena 20.000 lugares, Teatro 1.200, Clube 200, Sala VIP 50) |
| Assentos | 21.450, em 13 setores com preço próprio |
| Eventos | 5 (E1 Arena, E2 e E5 Teatro, E3 Clube, E4 Sala VIP) |
| Usuários | 50.000 |
| Intenções de compra (carga e pico) | 30.000: 70% E1, 20% E2, 10% E3; 1 a 4 assentos lado a lado; pagamento 90% aprovado, 7% recusado, 3% atrasado (3 s) |
| Intenções da corrida | 5.000 pessoas para os 50 assentos do E4 |
| Idempotência, expiração e compensação | 400 compras cada, em assentos separados do E5 |

A demanda é maior que a oferta de propósito: 58 mil pedidos de assento na carga para 21 mil lugares, e 6.555 pedidos para 50 lugares na corrida. Conflitos (409) são o comportamento esperado, não erro.

`dados/carga.json` define `ttl_reserva_segundos = 20` e `atraso_pagamento_ms = 3000`, para o teste de expiração caber em minutos.

## Cenários

| Cenário | O que faz | O que mede |
|---|---|---|
| `carga` | Rampa de chegadas até 120 compras/s (perfil padrão), por ~6,5 min | Vazão, p95 de reserva, pagamento e fluxo completo, taxa de erro |
| `pico` | Salto de 10 para 300 compras/s em 5 s (abertura de vendas) | Se o sistema degrada com controle (429/503) ou com erros e timeouts |
| `corrida` | 500 clientes ao mesmo tempo disputando 50 assentos | Exclusão mútua: nenhum assento vendido duas vezes, e todos os 50 vendidos |
| `idempotencia` | Cada reserva e cada pagamento enviados 5 vezes em paralelo, mais 1 repetição | Uma reserva e uma cobrança por compra; repetição recebe o mesmo resultado |
| `expiracao` | 400 reservas nunca pagas | Liberação dos assentos depois do TTL |
| `compensacao` | 400 pagamentos recusados | Compensação da SAGA: assentos liberados e nenhuma cobrança |

Perfis: `leve` (20% da taxa, para máquinas modestas e para os grupos testarem), `padrao` e `pesado` (2×). Para comparar grupos, **use o mesmo perfil e a mesma máquina para todos**.

`testes/caos.sh` derruba um serviço no meio da carga e o sobe de novo (opcional; usa `caos` no adaptador).

## Invariantes conferidas

Por evento, no estado final retornado por `/_teste/vendas`:

1. Nenhum assento vendido duas vezes.
2. Só assentos que existem foram vendidos, e dentro da capacidade.
3. Nenhuma cobrança duplicada.
4. Toda cobrança aprovada tem ingresso, e todo ingresso vendido foi pago.
5. Valor cobrado igual à soma dos preços dos assentos.
6. Uma reserva por chave de idempotência.
7. Nenhuma venda confirmada ao cliente desaparece do estado final.

Mais as específicas de cada cenário: no máximo 50 vendidos na corrida; no máximo uma reserva e uma cobrança por compra na idempotência; nenhuma reserva aberta e todos os assentos liberados na expiração e na compensação.

O kit foi validado contra o servidor de referência com cinco defeitos injetados (`--bug overbooking`, `idempotencia`, `cobranca`, `compensacao`, `expiracao`): cada defeito fez falhar pelo menos uma invariante, e o servidor correto passou em todas.

## Como rodar

Requisitos: [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) 0.54 ou mais novo, Python 3.10+, curl e bash.

```bash
# 1. sistema do grupo no ar (docker compose up), com as rotas do contrato
# 2. um cenário ou todos
./executar.sh adaptadores/grupo_08.json padrao              # todos os cenários
./executar.sh adaptadores/grupo_08.json leve corrida        # só a corrida
K6=/opt/k6/k6 ./executar.sh adaptadores/grupo_09.json padrao # k6 fora do PATH
```

Para conhecer o kit sem nenhum projeto:

```bash
python3 referencia/servidor.py --porta 8080 &
./executar.sh adaptadores/referencia.json leve
```

Cada cenário começa com `/_teste/reset` e `/_teste/carga`, então a ordem não importa.

## Saídas

| Arquivo | Conteúdo |
|---|---|
| `resultados/<grupo>/<cenario>.resumo.json` | Resumo completo do k6 |
| `resultados/<grupo>/<cenario>.verificacao.json` | Invariantes (ok ou falha, com contagem) e métricas de desempenho |
| `resultados/<grupo>/<cenario>.console.log` | Reservas e vendas confirmadas ao cliente, usadas na invariante 7 |
| `resultados/comparativo.csv` | Uma linha por grupo e cenário, para a tabela comparativa |

Colunas do comparativo: `violacoes`, `invariantes_violadas`, `requisicoes_por_s`, `reservar_p95_ms`, `pagar_p95_ms`, `fluxo_p95_ms`, `listar_p95_ms`, `taxa_erro`, `erros`, `rejeicoes_controladas`, `reservas_ok`, `conflitos`, `pagamentos_aprovados`, `pagamentos_recusados`, `assentos_vendidos`, `vendas_confirmadas_cliente`, `duracao_s`.

## Sugestão de leitura comparativa

- **Correção primeiro.** Uma invariante violada (overbooking, cobrança duplicada, venda perdida) pesa mais que qualquer ganho de latência.
- **Corrida:** o ideal é `assentos_vendidos = 50` com zero erros. Menos de 50 indica reservas presas ou falhas sob disputa.
- **Pico:** `rejeicoes_controladas` (429/503) é degradação aceitável; `erros` (5xx e timeouts) não é.
- **Carga:** compare `fluxo_p95_ms` e `requisicoes_por_s` só entre execuções no mesmo perfil e na mesma máquina.

## Opcional: escalar para um evento de porte nacional

A escala oficial da avaliação é a desta página: um grande evento local, que roda num notebook. Esta seção é para quem quiser ir além, como trabalho extra ou para experimentar a arquitetura do grupo. Como referência, a venda dos shows da Taylor Swift no Brasil, em 2023, teve fila virtual de mais de 2 milhões de pessoas e esgotou em 20 a 30 minutos ([Gazeta SP](https://www.gazetasp.com.br/entretenimento/shows-de-taylor-swift-no-brasil-tem-fila-virtual-de-mais-de-2-milhoes/), [Mais Goiás](https://www.maisgoias.com.br/entretenimento/ingressos-para-shows-de-taylor-swift-esgotam-em-20-minutos-fila-virtual-chegou-a-mais-de-2-milhoes-de-pessoas/)). Dois milhões de pessoas em 20 minutos dão, em média, cerca de 1.700 chegadas por segundo; na abertura das vendas, o pico é bem maior.

| | Escala oficial | Exemplo de escala nacional |
|---|---|---|
| Usuários | 50 mil | 2 milhões |
| Lugares | 21.450 | cerca de 346 mil (5 estádios de 65 mil a mais) |
| Intenções de compra | 30 mil | 1 milhão |
| Pico de chegadas | 300 compras/s | 6.000 a 30.000 compras/s |

**1. Gerar uma massa maior.** O gerador aceita parâmetros de escala; sem eles, produz a massa oficial, idêntica à distribuída.

```bash
python3 dados/gerar_dados.py --saida dados_nacional --usuarios 2000000 --intencoes 1000000 --estadios 5
```

Com estádios extras, 80% da demanda vai para eles (eventos `N1` a `N5`). Os arquivos passam de 100 MB: se o envio por `POST /_teste/carga` ficar pesado, carregue os dados por script direto no banco (veja "Adaptar o kit").

**2. Aumentar a taxa.** `FATOR` multiplica as taxas e os VUs de todos os perfis:

```bash
DADOS=$PWD/dados_nacional FATOR=20 ./executar.sh adaptadores/grupo_XX.json padrao carga pico
```

Com `FATOR=20`, a carga sobe a 2.400 compras/s e o pico a 6.000 compras/s. Cada compra faz duas ou três requisições. Escale só `carga` e `pico`: corrida, idempotência, expiração e compensação testam correção e já são decisivos na escala oficial.

**3. Distribuir o gerador de carga.** Uma máquina com k6 sustenta alguns milhares de requisições por segundo. Acima disso, divida o teste entre máquinas com segmentos de execução: cada uma roda uma fatia das chegadas, e o roteiro não se repete entre elas.

```bash
# máquina 1 de 4 (nas outras, troque o primeiro segmento por 1/4:2/4, 2/4:3/4 e 3/4:1)
k6 run --execution-segment "0:1/4" --execution-segment-sequence "0,1/4,2/4,3/4,1" \
  -e CENARIO=carga -e FATOR=20 -e DADOS=$PWD/dados_nacional -e ADAPTADOR=$PWD/adaptadores/grupo_XX.json \
  -e SAIDA=$PWD/resultados/grupo_XX/parte1 --console-output resultados/grupo_XX/parte1/carga.console.log \
  testes/cenarios.js
```

Depois, junte os logs (`cat resultados/grupo_XX/parte*/carga.console.log > resultados/grupo_XX/carga.console.log`) e rode o verificador uma vez. As invariantes usam o estado final do sistema, então valem com qualquer número de máquinas. As métricas de latência, porém, ficam uma por máquina; para um resumo único, use o [k6-operator](https://grafana.com/docs/k6/latest/testing-guides/running-distributed-tests/) num cluster Kubernetes. Cada máquina carrega o roteiro inteiro na memória: com 1 milhão de intenções, reserve alguns GB por instância.

**4. Rodar o sistema fora do notebook.** Nessa escala, o gargalo deixa de ser o código e passa a ser a infraestrutura: réplicas dos serviços (`docker compose up --scale` ou Kubernetes), banco particionado por evento, broker em cluster. Isso exige máquinas na nuvem e tem custo; combine antes com o professor.

**5. O que observar.** Ninguém atende 30.000 compras/s com um sistema de turma. Nessa escala, o que diferencia uma boa arquitetura é:

- **Controle de admissão:** o excesso recebe fila virtual ou 429/503 (coluna `rejeicoes_controladas`), não erros nem timeouts.
- **Escalabilidade horizontal:** rodar a mesma carga com 1, 2 e 4 réplicas e ver se a vazão cresce quase na mesma proporção. Um lock central ou um banco único aparecem como uma curva que achata.
- **Correção mantida:** nenhuma das invariantes pode falhar, em escala nenhuma.

## Estrutura

```
CONTRATO.md            rotas e formatos que cada grupo expõe
executar.sh            roda os cenários, verifica e consolida
adaptadores/           um JSON por grupo (URL, rotas, campos, cabeçalhos)
dados/                 gerador (com parâmetros de escala) e massa de dados oficial
testes/cenarios.js     cenários k6
testes/lib.js          cliente do contrato, guiado pelo adaptador
testes/caos.sh         derrubada de serviço durante a carga (opcional)
verificador/           verificar.py (invariantes) e consolidar.py (CSV)
referencia/servidor.py servidor de referência, com defeitos injetáveis
```
