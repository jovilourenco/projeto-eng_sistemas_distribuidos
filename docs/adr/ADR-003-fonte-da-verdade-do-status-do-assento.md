# ADR-003: Fonte da verdade do status do assento

- **Status:** Pendente
- **Data:** 2026-11-02
- **Issue:** #17

## Contexto

O principal critério de sucesso do TicketFlow é **nenhum assento vendido em duplicidade com 500 usuários simultâneos disputando os mesmos assentos**. Também se espera latência p95 da reserva abaixo de 300 ms com 200 requisições por segundo.

A proposta do projeto prevê dois armazenamentos envolvidos na disponibilidade de um assento:

- o **Redis**, para a reserva temporária dos assentos;
- o **PostgreSQL do Serviço de Eventos e Assentos**, que guarda cada assento e o seu status (`DISPONIVEL`, `RESERVADO`, `VENDIDO`).

É necessário decidir qual dos dois é a **fonte da verdade**, ou seja, qual prevalece quando divergirem, e **em que ponto a venda em duplicidade é efetivamente impedida**.

Duas decisões anteriores restringem as opções:

- **ADR-001:** o broker entrega mensagens "pelo menos uma vez", e, com consumidores concorrentes, fora de ordem. O mesmo comando pode chegar mais de uma vez ao Serviço de Eventos e Assentos.
- **ADR-002:** a reserva do assento é feita pelo orquestrador da SAGA, no Serviço de Reservas, por meio de comandos assíncronos (`assento.reservar`, `assento.vender`, `assento.liberar`). O orquestrador reenvia comandos sem resposta. Sem um filtro na entrada, cada tentativa de reserva de um mesmo assento iniciaria uma compra própria.

## Análise comparativa

### Onde fica a verdade

| Critério | A. PostgreSQL como verdade, Redis como filtro de entrada | B. Redis como verdade | C. Apenas PostgreSQL |
| :--- | :--- | :--- | :--- |
| **Durabilidade** | Status persistido em banco transacional | Dados em memória; reinício pode apagar reservas em andamento | Status persistido em banco transacional |
| **Garantia contra venda dupla** | No banco, independente do Redis | Depende do Redis estar correto e disponível | No banco |
| **Absorção do pico de disputa** | Redis recusa os concorrentes em milissegundos, antes de criar compras | Redis | Todo o pico chega ao banco; cada tentativa cria uma compra que depois é cancelada |
| **Posse dos dados** | Cada serviço é dono dos próprios dados | O Eventos e Assentos dependeria do Redis do Serviço de Reservas | Cada serviço é dono dos próprios dados |
| **Complexidade** | Dois mecanismos a manter coerentes | Sincronização Redis → banco | Um único mecanismo |

### Como o banco impede a venda dupla

O problema de duas transações lerem o assento como disponível e ambas o reservarem é conhecido como **atualização perdida** (*lost update*). No nível de isolamento padrão do PostgreSQL (READ COMMITTED), adotado pelo projeto, há quatro técnicas clássicas para evitá-lo:

| Técnica | Funcionamento | Comportamento sob muita disputa |
| :--- | :--- | :--- |
| **Escrita condicional atômica** (*compare-and-set*) | `UPDATE ... WHERE status = 'DISPONIVEL'`: verificação e escrita em uma única instrução | A transação perdedora espera a trava de linha da vencedora apenas até ela ser confirmada; então reavalia a condição e recebe "0 linhas alteradas". Como a transação vencedora é curta e não há ida e volta à aplicação entre ler e escrever, a espera é breve |
| **Bloqueio pessimista** (`SELECT ... FOR UPDATE`) | Trava a linha, devolve os dados à aplicação, que valida e então atualiza | A trava fica retida durante a leitura, a validação na aplicação e a escrita, então as transações concorrentes formam uma fila mais longa |
| **Bloqueio otimista com versão** (por exemplo, `@Version` do JPA) | Lê o registro, valida na aplicação e grava apenas se a versão não mudou | Correto, mas exige ler e escrever em duas idas ao banco, e cada perdedora recebe uma exceção a ser tratada |
| **Isolamento SERIALIZABLE** | O banco detecta transações concorrentes conflitantes | Correto, mas as perdedoras são abortadas com erro de serialização e precisam ser repetidas |

A abertura de vendas é, por definição, um cenário de **conflito frequente** sobre as mesmas linhas. Nesse cenário, um conflito não pede nova tentativa: significa apenas que o assento está ocupado.

## Decisão

### 1. O PostgreSQL do Serviço de Eventos e Assentos é a fonte da verdade

O status do assento gravado no banco do Serviço de Eventos e Assentos prevalece sobre qualquer outra informação. É ele que a listagem de assentos exibe e que resolve qualquer divergência.

O identificador do assento é único em todo o serviço, e não apenas dentro de um evento.

### 2. O Redis é um filtro de entrada, não um registro

Antes de iniciar uma compra, e depois de confirmar no banco que ela ainda não existe (ADR-002, item 2), o Serviço de Reservas tenta criar no Redis uma trava para o assento, com validade igual ao prazo de pagamento da compra:

```
SET assento:trava:{idAssento} {idCompra} NX PX {prazoDePagamentoEmMs}
```

- Se a trava for criada, a compra é iniciada.
- Se a trava já existir com o identificador da mesma compra, há uma requisição igual em andamento, tratada conforme o ADR-002, item 2.
- Se a trava já existir com outro valor, o usuário recebe imediatamente HTTP 409 e nenhuma compra é criada.

A trava guarda o identificador da compra que a criou. Ela só é removida por essa mesma compra, com uma operação atômica de "comparar e remover" (script Lua), para que uma compra não apague a trava de outra. A trava é removida quando a compra chega a `CANCELADA`, ou quando a criação da compra falha, e expira sozinha nos demais casos.

A trava **não é uma garantia de exclusividade**. Ela pode expirar enquanto a compra ainda está ativa (por exemplo, em `PROCESSANDO_PAGAMENTO`, ADR-002) ou se perder em uma reinicialização do Redis. Uma trava com prazo de validade, sozinha, não é segura em sistemas distribuídos, porque o detentor pode perdê-la sem saber e continuar agindo como se ainda a tivesse. A proteção correta é o armazenamento final recusar escritas de quem não detém mais o direito. No TicketFlow, esse papel é do banco, conforme o item 3.

**Redis indisponível.** Se o Redis não responder, o Serviço de Reservas segue sem o filtro: a compra é iniciada sem a trava, e a disputa pelo assento é decidida pelo banco (item 3). A correção não é afetada, porque a garantia contra venda dupla não depende do Redis. O que se perde, enquanto o Redis estiver fora, é a proteção contra o pico: tentativas concorrentes sobre o mesmo assento criam compras que terminam em `CANCELADA`. A chamada ao Redis tem tempo limite curto, para que a sua indisponibilidade não aumente a latência da reserva.

### 3. Toda mudança de status é uma escrita condicional que registra a compra dona do assento

O assento passa a registrar **qual compra o detém** (`id_compra`) e **desde quando está reservado** (`reservado_em`). Cada comando da SAGA é aplicado com uma única instrução `UPDATE` condicional:

```sql
-- assento.reservar
UPDATE assento SET status = 'RESERVADO', id_compra = :idCompra, reservado_em = now()
 WHERE id_assento = :idAssento
   AND id_evento = :idEvento
   AND ((status = 'DISPONIVEL' AND ativo = true)
        OR (status = 'RESERVADO' AND id_compra = :idCompra));

-- assento.vender
UPDATE assento SET status = 'VENDIDO'
 WHERE id_assento = :idAssento
   AND id_compra = :idCompra
   AND status IN ('RESERVADO', 'VENDIDO');

-- assento.liberar
UPDATE assento SET status = 'DISPONIVEL', id_compra = NULL, reservado_em = NULL
 WHERE id_assento = :idAssento
   AND id_compra = :idCompra
   AND status = 'RESERVADO';
```

A condição com `id_evento` não serve para distinguir assentos, que já têm identificador único, mas para recusar um comando que associe o assento ao evento errado.

A resposta enviada ao orquestrador depende do resultado. Quando a escrita altera 0 linhas, o serviço lê o assento para escolher a resposta:

| Comando | 1 linha alterada | 0 linhas alteradas |
| :--- | :--- | :--- |
| `assento.reservar` | `assento.reservado`, com o valor do assento | `assento.indisponivel`: outra compra detém o assento, ou ele está inativo, não existe ou não pertence ao evento informado |
| `assento.vender` | `assento.vendido` | `assento.venda_recusada`: a compra não detém o assento. Indica violação das garantias da SAGA e leva a compra para intervenção manual (ADR-002) |
| `assento.liberar` | `assento.liberado` | Se o assento está `VENDIDO` para a própria compra: `assento.liberacao_recusada`, que também indica violação e leva a compra para intervenção manual. Nos demais casos: `assento.liberado`, porque a compra já não detém o assento e não há o que liberar |

### 4. A idempotência vem do registro do dono

Como cada condição inclui `id_compra`, aplicar o mesmo comando duas vezes produz o mesmo resultado e a mesma resposta:

- um `assento.reservar` repetido encontra o assento já `RESERVADO` pela própria compra e responde `assento.reservado` de novo, em vez de responder `assento.indisponivel` por engano;
- um `assento.vender` repetido encontra o assento já `VENDIDO` pela própria compra;
- um `assento.liberar` repetido, ou atrasado, nunca libera um assento que, nesse meio-tempo, foi reservado por outra compra, porque o `id_compra` não confere.

Com isso, o Serviço de Eventos e Assentos não precisa de uma tabela de mensagens já processadas para os comandos da SAGA. O próprio estado do assento basta para reconhecer a repetição.

Comandos fora de ordem também ficam cobertos. Se um `assento.liberar` for processado antes de um `assento.reservar` mais antigo da mesma compra, o assento termina reservado para uma compra cancelada. O orquestrador detecta a resposta `assento.reservado` atrasada e reenvia `assento.liberar` (ADR-002).

### 5. Reconciliação de reservas antigas

Para cobrir o caso em que um `assento.liberar` se perde (por exemplo, isolado na fila de mensagens mortas, ADR-001), o Serviço de Eventos e Assentos executa, a cada 5 minutos, uma rotina que procura assentos `RESERVADO` há mais de 15 minutos (`reservado_em`). Esse limite é bem maior que o prazo de pagamento. Para cada um, publica o pedido `assento.verificar`, com o assento e a compra dona, ao orquestrador:

- se a compra estiver `CANCELADA`, o orquestrador reenvia `assento.liberar` (ADR-002);
- se a compra ainda estiver ativa (por exemplo, com pagamento de resultado desconhecido), o pedido é ignorado, e o assento continua reservado;
- se a compra não existir, o que só é possível após uma restauração de backup, o orquestrador envia `assento.liberar` e registra um alerta.

Assim nenhum assento fica reservado indefinidamente para uma compra que já terminou.

### 6. Alterações feitas pelo gestor de eventos

O status só é alterado pelos comandos da SAGA. Para que o gestor não invalide uma compra em andamento, as operações do gestor também são escritas condicionais, e não "verificar e depois agir":

```sql
-- inativar, alterar ou excluir um assento: só se estiver DISPONIVEL
UPDATE assento SET ativo = false WHERE id_assento = :idAssento AND status = 'DISPONIVEL';
DELETE FROM assento WHERE id_assento = :idAssento AND status = 'DISPONIVEL';
```

- Se a escrita alterar 0 linhas, o gestor recebe HTTP 409.
- A exclusão de um evento remove apenas os assentos `DISPONIVEL` e, na mesma transação, o evento. Se restar algum assento reservado ou vendido, a chave estrangeira impede a exclusão do evento, a transação é desfeita e o gestor recebe HTTP 409. Para isso, a chave estrangeira de `assento` para `evento` não pode usar exclusão em cascata (`ON DELETE CASCADE`).
- Um assento inativado deixa de poder ser reservado, porque `assento.reservar` exige `ativo = true`.

Essas regras garantem que um assento `RESERVADO` continue disponível para a venda da compra que o detém. Por isso `assento.venda_recusada` só ocorre se alguma garantia for violada. Alterações de preço também não afetam compras em andamento, porque o valor cobrado é o informado na reserva (ADR-002).

## Alternativas consideradas

**Redis como fonte da verdade (opção B).** Descartada. O Redis é usado aqui como armazenamento em memória de dados com prazo de validade, e não como banco durável. Torná-lo a referência do status faria a garantia contra venda dupla depender da sua disponibilidade e da sua configuração de persistência. Também obrigaria o Serviço de Eventos e Assentos a consultar um armazenamento pertencente ao Serviço de Reservas, violando o princípio de que cada serviço é dono dos próprios dados.

**Recusar reservas quando o Redis estiver indisponível.** Descartada. Bloquearia todas as vendas por causa de um componente que não é responsável pela correção, enquanto o banco, que é a fonte da verdade, continua disponível.

**Apenas PostgreSQL, sem Redis (opção C).** Descartada como mecanismo único. Seria correta quanto à venda dupla, mas, com a reserva assíncrona do ADR-002, cada uma das tentativas simultâneas sobre o mesmo assento iniciaria uma compra, enviaria um comando e seria cancelada em seguida. Isso gera carga e mensagens desnecessárias no momento de pico e atrasa a resposta ao usuário. Também abandonaria a reserva temporária em Redis prevista na proposta.

**Bloqueio pessimista.** Descartado. A trava de linha fica retida enquanto a aplicação valida o status, o que alonga a fila de transações concorrentes sobre o mesmo assento no pico da abertura de vendas.

**Bloqueio otimista com coluna de versão.** Correto, e a escrita condicional adotada é, ela própria, uma forma de controle otimista. A diferença é que a coluna de versão exige ler e depois gravar, em duas idas ao banco, e trata o conflito como exceção, enquanto a escrita condicional resolve tudo em uma instrução e expressa a regra (quem é o dono e qual é o estado esperado) na própria condição.

**Isolamento SERIALIZABLE.** Descartado. Garante a correção, mas sob disputa intensa aborta muitas transações com erro de serialização, que precisam ser tratadas e repetidas. A escrita condicional em READ COMMITTED atinge o mesmo resultado para este caso sem abortos.

**Restrição de unicidade em uma tabela de vendas** (uma linha por assento vendido, com `UNIQUE` no assento). Impede a venda dupla de forma declarativa, mas não modela a reserva temporária nem a liberação: seria preciso inserir e apagar linhas para reservar e liberar, e o status do assento ficaria dividido entre duas tabelas.

**Tabela de mensagens processadas (padrão *Inbox*).** Descartada para os comandos da SAGA, porque o registro de `id_compra` já torna cada transição idempotente sem armazenamento adicional. Continua sendo a opção para consumidores cujas operações não tenham um estado natural que permita reconhecer a repetição, como o Serviço de Notificações (ADR-002).

## Consequências

**Positivas**

- A garantia contra venda dupla não depende do Redis. Mesmo com a trava expirada ou perdida, o banco recusa a segunda reserva, e a compra concorrente termina em `CANCELADA`.
- O pico da abertura de vendas é absorvido pelo Redis, que recusa os concorrentes antes de qualquer compra ser criada. Isso contribui para a meta de latência.
- Comandos repetidos, atrasados ou fora de ordem não corrompem o status e não liberam o assento de outra compra.
- A reconciliação garante que nenhum assento fique reservado indefinidamente para uma compra encerrada.
- O critério de nenhuma venda dupla pode ser testado diretamente, disparando reservas simultâneas sobre o mesmo assento e verificando que apenas uma transição é aplicada.

**Negativas e riscos**

- **Consistência eventual na listagem:** entre a criação da trava no Redis e a aplicação de `assento.reservar` no banco, a listagem ainda mostra o assento como `DISPONIVEL`. Um usuário que tente reservá-lo nesse intervalo recebe 409 do Serviço de Reservas.
- **Trava órfã:** se o Serviço de Reservas cair entre criar a trava e gravar a compra, a trava fica sem compra correspondente. O assento continua `DISPONIVEL` no banco, mas novas tentativas recebem 409 até a trava expirar. O efeito é limitado ao prazo de pagamento e não compromete a consistência.
- **Redis fora do ar:** enquanto o Redis estiver indisponível, o sistema funciona como na opção C, com mais compras canceladas e mais carga no banco durante a disputa, mas sem venda dupla.
- A tabela `assento` passa a ter as colunas `id_compra` e `reservado_em`. A primeira referencia uma compra de outro serviço sem chave estrangeira, como toda referência entre serviços.
- Parte da regra de transição fica expressa nas condições SQL. As instruções devem ficar concentradas em um único repositório e ser cobertas por testes de integração.
- A trava no Redis e o status no banco podem divergir temporariamente, por exemplo com uma trava expirada e o assento ainda `RESERVADO`. Isso é esperado, e o banco prevalece.
