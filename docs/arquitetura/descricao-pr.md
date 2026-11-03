# Diagramas de arquitetura do TicketFlow

Relacionado à issue #16.

## Resumo

- Adiciona o diagrama C4 de nível 1 (contexto) em `docs/arquitetura/c4-nivel1.png` e seu arquivo-fonte editável, `c4-nivel1.drawio`.
- Adiciona o diagrama C4 de nível 2 (containers) em `docs/arquitetura/c4-nivel2.png` e seu arquivo-fonte editável, `c4-nivel2.drawio`.
- Adiciona referências aos diagramas no README.

Os diagramas representam a arquitetura **proposta**. A principal referência foi o documento *Proposta para Projeto Final - Eng. Sist. Distribuídos-2.pdf*. Para representar a infraestrutura, também foram consultados `docker-compose.yml` e `init.sql`. O Serviço de Notificações está previsto na proposta, mas ainda não foi implementado neste repositório.

## Decisões arquiteturais refletidas no nível 2

As decisões comunicadas na revisão dos ADR-002 e ADR-003 esclarecem as dúvidas anteriores:

1. **Orquestração da SAGA:** fica internamente no Serviço de Reservas.
2. **Pagamento e compensação:** Reservas solicita o pagamento por HTTP, com Circuit Breaker, e recebe o resultado de forma síncrona. Em caso de falha ou recusa, publica `assento.liberar` no RabbitMQ para o Serviço de Eventos e Assentos.
3. **Disponibilidade:** Reservas cria uma trava temporária no Redis com `SET NX PX`. O Serviço de Eventos e Assentos consome os comandos `assento.*` do RabbitMQ e persiste os estados finais dos assentos no PostgreSQL.
4. **Mensagens da SAGA:** os comandos são `assento.reservar`, `assento.vender` e `assento.liberar`; as respostas são `assento.reservado`, `assento.indisponivel`, `assento.vendido` e `assento.liberado`.
5. **Notificações:** após a conclusão da compra, Reservas publica `compra.concluida`. O Serviço de Notificações consome esse evento do RabbitMQ para enviar e-mail e ingresso de forma assíncrona.

O diagrama identifica as mensagens por nome, mas não define seus esquemas nem detalhes de implementação.

## Declaração de uso de IA

- **Ferramenta utilizada:** Codex, da OpenAI. O modelo GPT-6 Sol foi utilizado na estilização visual dos diagramas.
- **Em quais partes atuou:** leitura e síntese dos materiais do repositório; criação dos arquivos draw.io e exportação dos PNGs; atualização das referências no README; revisão desta descrição de PR; e integração com o GitHub para criar o PR, que foi posteriormente revisado.
- **Como foi orientada:** recebeu o pedido de representar os níveis de contexto e containers, incluir os componentes indicados, rotular cada seta e usar a proposta do projeto como referência principal. Durante a revisão, foi orientada a manter abstrata a integração com o Keycloak, mostrar a entrega da notificação ao usuário, atribuir ao Serviço de Reservas a compensação e descrever nas setas de mensageria as ações esperadas. As rotas foram posteriormente ajustadas conforme as decisões comunicadas para os ADRs.
- **Verificações técnicas:** os arquivos draw.io foram validados como XML, os PNGs foram abertos e inspecionados visualmente, e `git diff --check` não apontou problemas de formatação.
- **Revisão humana:** revisei a lógica, as conexões, os nomes e as inferências do diagrama à luz do PDF da proposta. Corrigi pontos que o modelo havia interpretado de forma inadequada. As decisões arquiteturais foram esclarecidas pela equipe e incorporadas ao nível 2.
