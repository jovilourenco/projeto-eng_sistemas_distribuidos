# ADR-006: Adaptação ao kit de avaliação do professor

- **Status:** Aceito
- **Data:** 2026-11-18
- **Issue:** #47

## Contexto

O professor disponibilizou um kit de testes (carga, pico, corrida, idempotência, expiração e compensação), aplicado aos cinco projetos de venda de ingressos, que confere invariantes no estado final do sistema (nenhum assento vendido duas vezes, nenhuma cobrança duplicada, todo ingresso pago, nenhuma venda perdida). O contrato do kit é uma sugestão: diferenças de nome ficam no adaptador, e diferenças de forma numa cópia do kit versionada no repositório (`kit-avaliacao/`), mantendo iguais a massa de dados, os cenários e as invariantes.

## Decisão

1. A compra aceita de 1 a 4 assentos, tudo ou nada. As rotas do contrato do kit (`POST /reservas`, `POST /reservas/{id}/pagamento`, `GET /reservas/{id}` e `POST /reservas/{id}/cancelamento`) são implementadas pelo `reserva-service`. A disputa por assentos é resolvida de forma síncrona pela trava do Redis (resposta 409), conforme o ADR-003. O API Gateway é o ponto de entrada único.
2. Os identificadores continuam inteiros. A cópia do kit traduz os códigos de texto (E1, ARENA-A-01-001, U000123) por um mapa gerado na carga dos dados.
3. Os valores continuam em reais no sistema. A conversão para centavos ocorre na leitura do estado, na cópia do kit.
4. A autenticação (Keycloak e JWT) permanece no perfil normal. Enquanto o gateway não existe, os serviços usam autenticação simulada por cabeçalhos (`X-Usuario-Id` e `X-Usuario-Perfil`); no perfil de teste, o kit envia esses cabeçalhos em cada requisição (cópia do kit), sem alterar o código de produção.
5. As rotas `/_teste` não entram no código de produção: reset, carga e leitura do estado são feitos por SQL e Redis, na cópia do kit.

## Alternativas consideradas

- **Identificadores de texto:** descartado; exigiria migrar todos os serviços e bancos.
- **Compra de um assento por vez:** descartado; o kit compra de 1 a 4 assentos e confere o valor total cobrado.
- **Manter a autenticação durante o teste:** descartado; o kit não se autentica, e o objetivo é medir o sistema.
- **Rotas `/_teste` no código de produção:** descartado; exporia operações destrutivas.

## Consequências

- Mantemos o modelo e os ids; o custo é uma camada de tradução na cópia do kit.
- O perfil de teste não exercita a segurança, então os testes de 401 e 403 rodam no perfil normal. É um trade-off assumido.
- A compra com vários assentos muda o modelo de reservas e o contrato de mensagens com o serviço de Eventos e Assentos (issue D).
- O prazo de pagamento simulado (3 s no cenário `atrasar`) exige que o cliente HTTP do Reservas tenha timeout acima desse valor.
