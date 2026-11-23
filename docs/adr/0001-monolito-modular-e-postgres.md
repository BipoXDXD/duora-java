# 0001 — Monólito modular com PostgreSQL 18

- **Estado:** aceita
- **Data:** 2026-11-23
- **Etapa:** 2a

## Contexto

Até a Etapa 1, o Duora guardava tudo em memória: salas anônimas com expiração, sem conta e sem nada que precisasse sobreviver a um reinício. A Etapa 2 traz contas, sessões, links mágicos, aceites versionados, registros de acesso exigidos pelo Marco Civil e e-mails transacionais. Esses dados precisam de durabilidade, de invariantes garantidas pelo próprio banco (e-mail único e normalizado, token de uso único, chave de idempotência) e de migrações versionadas que andem junto com o código.

O projeto tem um desenvolvedor, roda num único servidor no piloto e precisa ser barato de operar. As próximas etapas (salas com conta, mensagens, denúncias, retenção) vão cruzar os mesmos dados.

## Decisão

- **Um deployable:** um único jar Spring Boot. Os módulos são pacotes de primeiro nível (`room`, `conta`, `notificacao`, `retencao`…) cuja raiz é a API pública; `dominio/`, `web/` e `infra/` ficam por dentro. A fronteira é travada por testes de arquitetura da ArchUnit (`ArquiteturaTest`), não por módulos Maven separados.
- **PostgreSQL 18 com Flyway:** o esquema nasce e muda só por migrações `V<n>__*.sql` em `src/main/resources/db/migration`, aplicadas no boot, sem `out-of-order`. No Spring Boot 4 o Flyway entra pelo `spring-boot-starter-flyway` (só a `flyway-core` no classpath não roda as migrações, e nenhum erro aparece), mais o `flyway-database-postgresql`.
- **Spring Data JDBC e `JdbcClient`, sem JPA:** SQL explícito, sem sessão de persistência nem carregamento preguiçoso. As invariantes de concorrência (consumo de token, renovação de sessão) são `UPDATE` condicionais escritos à mão.
- **Testcontainers:** os testes rodam contra o mesmo Postgres 18 da produção, um contêiner por JVM de teste (URL `jdbc:tc:` com `TC_DAEMON`), sem banco em memória que aceite SQL que o Postgres recusa. Em dev, o `spring-boot-docker-compose` sobe o `compose.yaml`.
- **Outbox no lugar de broker:** e-mails e outros efeitos externos vão para uma tabela `outbox` na mesma transação do fato, e um despachante agendado os entrega com retry. Sem Kafka, RabbitMQ ou SQS no piloto.
- **Pool com limite de transação ociosa:** toda conexão nasce com `idle_in_transaction_session_timeout = 30s`, para uma transação esquecida não segurar bloqueios.

## Consequências

- Docker passa a ser obrigatório para `./mvnw verify`, para o e2e e para `./mvnw spring-boot:run`. A CI baixa a imagem `postgres:18` antes do Maven; localmente, as sessões paralelas serializam as suítes com Docker numa trava da máquina.
- Uma única transação local cobre o fato e o e-mail (sem dual write), e o banco é a fonte da verdade de tudo que precisa durar.
- Extrair um módulo para serviço próprio continua possível porque a API pública de cada módulo já é a única porta de entrada; até lá, não há rede entre módulos.
- Os testes com banco dividem um contexto Spring (`BancoDeTeste`) com pool pequeno e `TRUNCATE` antes de cada teste; o e2e compartilha o banco entre contextos sem limpar e precisa de dados únicos por teste.
- A versão do Flyway acompanha o BOM do Spring Boot; se um Flyway novo não reconhecer a versão do Postgres, o boot avisa no log e a versão é fixada no `pom.xml`.
