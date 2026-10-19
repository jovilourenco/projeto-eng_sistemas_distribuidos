# Projeto Final: Engenharia de Sistemas Distribuídos

## Equipe (5 Integrantes)
1. Herick de Lima Carneiro
2. João Lucas Félix de Macena
4. João Victor Lourenço da Silva
5. Leandeson Pinheiro Santos de Araújo
6. Luana de Méro Omena
7. Luerson de Albuquerque Silva Filho


## Tema Escolhido

## Estrutura do Projeto (Microsserviços)
Cada serviço contará com seu próprio schema de banco de dados, com migrações gerenciadas via **Flyway**, e mapeamento de objetos utilizando **MapStruct**. A orquestração do ambiente local será feita via **Docker Compose**.

- `/api-gateway`: Ponto de entrada único para o sistema.
- `/servico-dominio-principal`: Microsserviço responsável pelo core business.

## Executando o Ambiente Local
Para subir a infraestrutura de dependências (bancos de dados, message brokers, etc):
\`\`\`bash
docker-compose up -d
\`\`\`

---

## Declaração Obrigatória: Ferramentas de IA utilizadas

* **Ferramentas utilizadas:**
* **Em quais partes do trabalho a IA atuou:**
* **Como a IA foi orientada:**
* **Avaliação do uso da IA:**
