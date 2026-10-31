# Diagramas de arquitetura do TicketFlow

Relacionado à issue #16.

## Resumo

- Adiciona o diagrama C4 de nível 1 (contexto) em `docs/arquitetura/c4-nivel1.png` e seu fonte editável `c4-nivel1.drawio`.
- Adiciona o diagrama C4 de nível 2 (containers) em `docs/arquitetura/c4-nivel2.png` e seu fonte editável `c4-nivel2.drawio`.
- Referencia os diagramas no README.

Os diagramas representam a arquitetura **proposta**, tomando como referência principal o documento *Proposta para Projeto Final - Eng. Sist. Distribuídos-2.pdf*. Para as tecnologias de infraestrutura, também foram consultados `docker-compose.yml` e `init.sql`. O Serviço de Notificações está previsto na proposta, mas ainda não está implementado neste repositório.

## Questões arquiteturais para revisão

A proposta prevê uma SAGA para coordenar reserva, pagamento e emissão, incluindo a liberação do assento quando o pagamento falhar. Nesta revisão, foi definido que **o Serviço de Reservas é responsável pela compensação e liberação do assento**. Essa responsabilidade aparece no container de Reservas. O diagrama propõe que o resultado do pagamento chegue a Reservas pelo RabbitMQ; a equipe ainda precisa confirmar esse transporte e como a liberação afeta a disponibilidade dos assentos. As demais mensagens foram rotuladas pela ação esperada de cada serviço, sem fixar nomes ou contratos de eventos.

**Perguntas para a equipe:**

1. Quem gerenciará a SAGA: o Serviço de Reservas ou um serviço de orquestração dedicado?
2. O Serviço de Reservas receberá o resultado do pagamento pelo RabbitMQ? Como executará a liberação do assento após uma falha?
3. Como o Serviço de Reservas verificará e atualizará a disponibilidade dos assentos: chamada direta ao Serviço de Eventos e Assentos, mensagens ou outra solução?
4. As publicações e entregas de mensagens mostradas no diagrama correspondem ao fluxo pretendido? Quais serão os eventos e seus contratos para reserva, pagamento, compensação e notificação?
5. Por qual canal o Serviço de Notificações enviará a confirmação e o ingresso ao usuário?

O diagrama mostra as ações esperadas de publicação e consumo via RabbitMQ, mas essas rotas ainda precisam de confirmação. Ele não especifica nomes, contratos de eventos ou o protocolo de solicitação de pagamento.

## Declaração de uso de IA

- **Ferramenta utilizada:** Codex, da OpenAI.
- **Em quais partes atuou:** leitura e síntese dos materiais do repositório; criação dos arquivos draw.io e PNG; atualização das referências no README; redação desta descrição de PR.
- **Como foi orientada:** recebeu o pedido de representar os níveis de contexto e containers, usar os componentes indicados, rotular cada seta e tomar a proposta do projeto como referência principal. Durante a revisão, foi orientada a manter a integração com Keycloak abstrata, mostrar a entrega da notificação ao usuário, registrar a responsabilidade de Reservas pela compensação e descrever nas setas de mensageria as ações esperadas, deixando as rotas propostas para validação da equipe.
- **Avaliação do uso:** os arquivos draw.io foram verificados como XML válido, os PNGs foram abertos e inspecionados visualmente, e `git diff --check` não apontou problemas de formatação. As decisões listadas acima ainda precisam de revisão da equipe.
