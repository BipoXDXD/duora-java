# Etapa 2 — Persistência e contas: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` ou `mattpocock-skills:tdd`.

**Objetivo:** uma pessoa cria conta pelo celular com e-mail, link mágico, data de nascimento e aceite dos termos; um menor é recusado, uma rajada de cadastros é barrada, e o terraço anônimo continua funcionando.

**Arquitetura:** monólito modular com PostgreSQL 18 e Flyway (ADR 0001, retroativo, escrito aqui).
- **Núcleo compartilhado `nucleo/`:** Java puro com erros de domínio sem HTTP e ids UUID v7.
- **`web/`:** adapters HTTP e infraestrutura transversal: filtros, tradução de erro para HTTP, correlation ID, limite de taxa, idempotência.
- **`conta/`** em três camadas:
  - a raiz é a API pública e os casos de uso;
  - `conta/dominio/` fica sem Spring web, JDBC nem Jackson;
  - `conta/web/` e `conta/infra/` são os adapters.
- **E-mail:** sai por transactional outbox (`notificacao/`), sem dual write.
- **Fronteiras:** a ArchUnit as trava no `verify` desde a primeira task.
- **Acesso:** um interceptor nega por padrão toda `/api/**` sem sessão, exceto uma allowlist que inclui o terraço anônimo.
- **Front:** o React da 1a ganha as telas Entrar, Confirmar entrada, Cadastro e Minha conta.

**Stack:** Java 25, Spring Boot 4.2.0 (webmvc, data-jdbc, flyway, docker-compose, actuator só com `health`), PostgreSQL 18, Testcontainers (`postgres:18`), ArchUnit (JUnit 5), Bucket4j core + Caffeine, `RestClient` (Resend, Turnstile siteverify), log estruturado do Spring Boot, React 19 + TypeScript + Tailwind 4 (Vite), Vitest + Testing Library, Playwright Java.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 2, "Escolhas minhas": 18+, login, LGPD). **Contratos e princípios de engenharia (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Conferir pelo Context7 (com `mattpocock-skills:research` quando houver dúvida) antes da task que os usa: Spring Boot 4.2 (starters JDBC/Flyway/docker-compose/testcontainers, `logging.structured.format.console`), Testcontainers 2.x (artefatos), ArchUnit (`slices().should().beFreeOfCycles()`), Bucket4j 8.x (`bucket4j_jdk17-core`, `TimeMeter`), Flyway (`flyway-database-postgresql`), Resend `POST /emails` (`Idempotency-Key`), Turnstile siteverify.

**Pré-requisitos:** Etapa 1c concluída. A Etapa 9 Task 1 (documentos legais) e a Etapa 9 Task 2 (núcleo de retenção) rodam em paralelo a esta etapa e entram antes das Tasks 8 e 9 daqui.

## Restrições globais (Global Constraints)
- Tudo do arquivo de contratos, inclusive "Princípios de engenharia", vale aqui; em conflito, o contrato vence e o plano é corrigido.
- **Camadas por módulo** (vale para `conta/`, `notificacao/` e os módulos das próximas etapas):
  - a raiz do pacote é a API pública e os casos de uso. Pode usar `@Service`/`@Transactional`, mas nunca `org.springframework.web..`, `..jdbc..`, `..data..`, `jakarta.servlet..` nem Jackson;
  - `dominio/` é Java puro (tipos que nascem válidos, regras e portas);
  - `web/` tem controllers, filtros, interceptors e cookies;
  - `infra/` tem os adapters de JDBC e de serviços externos.
  - Outro módulo só usa a raiz. Classes são package-private por padrão.
- **Portas só em borda real:** `Clock`, `EnvioDeEmail`, `VerificacaoHumana`, `Outbox` e os repositórios (`RepositorioDeContas`, `RepositorioDeSessoes`, `RepositorioDeLinks`, `RepositorioDeBloqueios`, `RepositorioDeAceites`). Nada de interface 1:1 por reflexo.
- **Flyway:** esta etapa usa só V1–V19, com números fixos por task (ver "Ordem e paralelismo"). Dentro de uma onda, o merge segue a ordem do número da migração; uma migração menor nunca entra depois de uma maior. `spring.flyway.out-of-order` fica `false`.
- **Tempo e ids:** todo instante vem do `Clock` injetado; todo id vem de `GeradorDeIds` (UUID v7). Idade no fuso `America/Sao_Paulo`. Sem `static` mutável; injeção sempre pelo construtor (inclusive `RandomGenerator` para jitter).
- **Invariantes no banco:**
  - e-mail único e normalizado (`unique` + `check (email = lower(email))`);
  - token de link e de sessão `unique`, com consumo por `UPDATE` condicional (sem read-modify-write);
  - chave HMAC do e-mail bloqueado como PK;
  - aceite com PK `(conta_id, documento, versao)`;
  - chave de idempotência com PK `(escopo, chave)`.
- **Segredos aleatórios:** tokens de 32 bytes de `SecureRandom` em base64url; o banco guarda só o SHA-256 (`bytea`). O e-mail de bloqueio vira HMAC-SHA256 com `DUORA_SEGREDO_EMAIL` (e-mail tem pouca entropia).
- **Link mágico:** uso único, vale 15 min, um pedido novo invalida os anteriores do mesmo e-mail. O token vai no fragmento (`/entrar/confirmar#t=<token>`) e só é consumido por `POST`.
- **Sessão:** cookie `duora_sessao`, `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=2592000` (30 dias). A renovação deslizante é gravada no máximo a cada 24 h, por `UPDATE` condicional. Cada login abre uma sessão nova e encerra a anterior do mesmo navegador. O logout apaga a linha.
- **Sem dual write:** o e-mail do link (e o aviso de bloqueio) é gravado na `outbox` na mesma transação do link. Um despachante envia com retry, backoff exponencial (2 s × 2ⁿ, teto de 15 min, jitter de ±20 %) e `Idempotency-Key` = id da mensagem no Resend. Depois de 10 falhas, a mensagem fica marcada como falha e gera log ERROR.
- **Idempotência:** `POST /api/conta/entrar` aceita o cabeçalho `Idempotency-Key` (`^[A-Za-z0-9-]{8,64}$`). Mesma chave e mesmo corpo → mesma resposta, sem novo efeito e sem gastar outro token do Turnstile. Mesma chave com corpo diferente → 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`. Sem o cabeçalho, o pedido segue normal.
- **Resposta de `entrar`:** igual, em status e corpo, exista ou não a conta, esteja ou não o e-mail bloqueado.
- **Timeouts:** Resend (conexão 5 s, leitura 10 s), Turnstile (conexão 3 s, leitura 5 s).
- **Limites de taxa** (`duora.limites.<regra>.capacidade|periodo`, recarga em intervalo):

  | Regra | Chave | Capacidade | Período |
  |---|---|---|---|
  | `entrar-ip` | IP | 20 | `PT10M` |
  | `entrar-email` | `Email.chaveDeBloqueio()` | 3 | `PT15M` |
  | `confirmar-ip` | IP | 30 | `PT10M` |
  | `cadastro-conta` | `ContaId` | 10 | `PT10M` |

  IP = `request.getRemoteAddr()` (a Etapa 10 liga `server.forward-headers-strategy`). Limites por IP folgados por causa do CGNAT móvel.
- **Acúmulos com limpeza e métrica:** links, sessões, outbox e chaves de idempotência têm política de retenção (Task 9, sobre a interface da Etapa 9) e métrica de tamanho (gauge Micrometer `duora.retencao.tamanho{politica}`; a outbox também expõe `duora.outbox.pendentes` e `duora.outbox.falhas`).
- **Logs:** JSON (`logging.structured.format.console=ecs`) com `correlationId` no MDC de toda requisição e de todo despacho da outbox. Nunca e-mail completo, token, cookie ou data de nascimento; e-mail mascarado (`a***@dominio.com`). Exceção por contrato: o `EmailNoLog` (desenvolvimento) escreve o link.
- **Segredos:** só por variável de ambiente. `application-prod.properties` referencia `${RESEND_API_KEY}`, `${TURNSTILE_SITE_KEY}`, `${TURNSTILE_SECRET}`, `${DUORA_SEGREDO_EMAIL}` e a senha do banco sem padrão (boot falha se faltar). Desenvolvimento e testes usam as chaves de teste da Cloudflare e uma chave HMAC fixa.
- **Erros:**
  - O domínio lança `ErroDeNegocio` com um `CodigoDeErro`, cujo `TipoDeErro` não conhece HTTP. Só o `web/TratamentoDeErros` traduz para status.
  - Códigos novos: `NAO_AUTENTICADO`, `CONTA_INDISPONIVEL`, `CADASTRO_INCOMPLETO`, `EMAIL_INVALIDO`, `EMAIL_DESCARTAVEL`, `VERIFICACAO_HUMANA_FALHOU`, `LINK_INVALIDO`, `DATA_INVALIDA`, `ACEITE_NECESSARIO`, `TERMOS_DESATUALIZADOS`, `MENOR_DE_IDADE`, `CONFIRMACAO_NECESSARIA`, `CADASTRO_JA_CONCLUIDO`, `LIMITE_DE_TAXA` (429 com `Retry-After`), `CHAVE_DE_IDEMPOTENCIA_INVALIDA`, `CHAVE_DE_IDEMPOTENCIA_REUSADA`.
- **Front:** pt-BR, gênero neutro, Tailwind, axe em toda tela nova, seletores por papel/rótulo/texto. Pastas conforme a Etapa 1a (`frontend/src/api/`, `frontend/src/componentes/`, `frontend/src/sessao/`; telas novas em `frontend/src/telas/conta/`).
- **Commits:** refatoração separada de comportamento, sempre com testes verdes. Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Máquina:** comandos pesados um por vez (trava `mkdir /private/tmp/claude-501/duora-pesado.lock`, `caffeinate -dims`); uma suíte com Docker por vez.

## Skills por momento
- **Antes da Task 6:**
  - `mattpocock-skills:domain-modeling` sobre o modelo de Conta proposto aqui (estados, transições, invariantes, o que é entidade e o que é valor);
  - `mattpocock-skills:codebase-design`, com duas alternativas, para `ConsultaDeContas`, `AoExcluirConta` e `Outbox`;
  - se o resultado mudar uma interface pública, atualizar este plano e o plano consumidor (Etapas 3, 4 e 8) antes de codar.
- **Execução:** `superpowers:test-driven-development` ou `mattpocock-skills:tdd`.
- **Bug ou teste instável:** `superpowers:systematic-debugging` ou `mattpocock-skills:diagnosing-bugs`.
- **Revisão por task:**
  - pedir com `superpowers:requesting-code-review` e `mattpocock-skills:code-review`;
  - receber com `superpowers:receiving-code-review`;
  - o revisor cita o smell ou refactoring pelo nome.
- **Antes de dizer "pronto":** `superpowers:verification-before-completion`, depois `duora-etapa`; no fim, `superpowers:finishing-a-development-branch`.

## O terraço anônimo continua até as Etapas 4/5
- `room/` não muda de comportamento: convite anônimo, cookie `duora_assento` (`Path=/api`, `SameSite=Strict`) e polling iguais. Os dois cookies são independentes.
- `conta/RotasPublicas` inclui `/api/salas/**` e `/api/sala/**` (cobre rotas que a 1b/1c tenham criado em `room/`), e o interceptor `ExigeSessao` as deixa passar sem sessão.
- `room/` é legado: a regra de domínio da ArchUnit o exclui nominalmente até as Etapas 4/5 o substituírem; as regras de ciclos e de API pública valem para ele.
- A rota `/` do front continua abrindo o terraço sem login.
- Prova: a suíte atual (`SalaApiTest`, `ConcorrenciaTest`, e2e do terraço) fica verde sem mudar asserções, e o `TerracoAnonimoTest` (Task 6) trava isso. As Etapas 4/5 tiram `/api/sala(s)/**` da allowlist.

## Ordem e paralelismo
| Onda | Tasks (worktrees separados) | Depende de | Flyway (ordem de merge) |
|---|---|---|---|
| A | T1 | — | — |
| B | T2 ∥ T5 (∥ Etapa 9 T1) | T1 | T2: `V1__fundacao.sql` |
| C | T3 ∥ T4 (∥ Etapa 9 T2) | T2 (T3, T4); T5 não é exigida | T3: `V2__idempotencia.sql`, depois T4: `V3__outbox.sql` |
| D | T6 | T2, T5 | `V4__contas.sql`, `V5__sessoes.sql` |
| E | T7 | T3, T4, T5, T6 | `V6__entrada.sql` |
| F | T8 ∥ T9 | T7 + Etapa 9 T1 (T8); T6 + Etapa 9 T2 (T9) | T8: `V7__cadastro.sql`; T9: sem migração |
| G | T10, depois T11 | T7, T8 (T10); T9, T10 (T11) | — |

V8–V19 ficam reservados para correções. No máximo 4 agentes ao mesmo tempo, contando os da Etapa 9.

## Decisões do dono
- **D1. Domínio do remetente e e-mail de contato.** O Resend exige domínio verificado.
  - (a) **Reusar `meetduora.com`** do projeto C#: remetente `Duora <entrar@meetduora.com>`, contato `support@meetduora.com`. Custo zero; o agente confere pelo MCP do Resend que o domínio está verificado. Contra: os dois projetos dividem a marca.
  - (b) Domínio novo só do Java: custo anual e DNS novo; só faz sentido se a Etapa 10 escolher outro domínio para o app.
  - **Recomendo (a).** Não bloqueia: o código lê `duora.email.remetente` e `duora.contato.email` de propriedades e, até a decisão, o desenvolvimento usa `EmailNoLog`.

## Foco de revisão (Review Focus)
1. **Leitor de e-mail que pré-abre o link** (Outlook, antivírus): o link ainda funciona quando a pessoa clica. Coberto por `linkSobreviveAoPreCarregamento` (Task 7).
2. **Botão "Enviar link" tocado duas vezes ou repetido pela rede móvel:** sai um e-mail só, e a repetição não gasta outro token do Turnstile. Coberto por `repeticaoComMesmaChaveNaoDuplicaNemGastaTurnstile` (Task 7).
3. **Resend fora do ar no momento do pedido:** a pessoa recebe 202 e o e-mail chega quando o Resend voltar, sem link duplicado. Coberto por `falhaDoResendTentaDeNovoComMesmaChave` (Task 4).
4. **WebKit/Safari e cookie `Secure` em `http://127.0.0.1`:** a sessão sobrevive a uma recarga nos três navegadores. Coberto por `sessaoSobreviveARecarga` (Task 10). Se o WebKit descartar o cookie, `duora.sessao.cookie-seguro` (padrão `true`, Task 6) é desligada só no e2e do WebKit, nunca em `prod`.
5. **Aniversário de 18 anos perto da meia-noite** (23h de 21/11 em São Paulo = 02h de 22/11 UTC): quem nasceu em 22/11/2008 ainda tem 17. Coberto por `idadeUsaFusoDeSaoPaulo` (Task 8).

---

### Task 1: Núcleo, pacote `web/`, fitness functions e logs estruturados

Três commits, na ordem: refatoração (sem mudar comportamento), ArchUnit e logs.

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/nucleo/{CodigoDeErro,TipoDeErro,ErroDeNegocio,GeradorDeIds}.java` (Java puro, sem Spring)
- Criar: `web/{BaseConfig,CodigoWeb,IdDeCorrelacao}.java`
- Mover (`git mv`, pacote `web`): `room/{TratamentoDeErros,RespostaDeProblema,MesmaOrigem,HostLocal,CabecalhosDeSeguranca,EnderecoLocal}.java`
- Modificar:
  - `room/ErroApi.java` passa a estender `ErroDeNegocio`, e `visao` e `motivo` viram extras;
  - `room/Codigo.java` implementa `CodigoDeErro` (status trocado por `TipoDeErro`) e perde `ORIGEM_RECUSADA`, `HOST_RECUSADO`, `CORPO_INVALIDO` e `ERRO_INTERNO`, que vão para `CodigoWeb`;
  - `room/SalasConfig.java` perde o `Clock` e o `@EnableScheduling`.
- Mover: `src/test/.../room/RelogioDeTeste.java` → `src/test/.../web/RelogioDeTeste.java` (público, com `avancar(Duration)`)
- Modificar: `pom.xml` (`com.tngtech.archunit:archunit-junit5` em teste), `application.properties` (`logging.structured.format.console=ecs`)
- Teste: `nucleo/GeradorDeIdsTest.java`, `web/{TratamentoDeErrosTest,IdDeCorrelacaoTest}.java`, `ArquiteturaTest.java` (raiz de teste)

**Interfaces (produz):**
- `public enum TipoDeErro { ENTRADA_INVALIDA, NAO_AUTENTICADO, PROIBIDO, NAO_ENCONTRADO, CONFLITO, ENCERRADO, GRANDE_DEMAIS, FORMATO_NAO_SUPORTADO, ENDERECO_RECUSADO, REGRA_DE_NEGOCIO, LIMITE, INTERNO, INDISPONIVEL }`.
- `TratamentoDeErros` traduz os tipos para 400, 401, 403, 404, 409, 410, 413, 415, 421, 422, 429, 500 e 503, nessa ordem.
- `public interface CodigoDeErro { String name(); TipoDeErro tipo(); String titulo(); }` (enums implementam direto).
- `public class ErroDeNegocio extends RuntimeException`: `public ErroDeNegocio(CodigoDeErro codigo, String mensagem)`, `public CodigoDeErro codigo()`, `public Map<String, Object> extras()`, `protected ErroDeNegocio comExtra(String chave, Object valor)`.
- `public enum CodigoWeb implements CodigoDeErro`: `ORIGEM_RECUSADA`, `HOST_RECUSADO`, `CORPO_INVALIDO`, `NAO_AUTENTICADO`, `LIMITE_DE_TAXA`, `CHAVE_DE_IDEMPOTENCIA_INVALIDA`, `CHAVE_DE_IDEMPOTENCIA_REUSADA`, `ERRO_INTERNO`.
- Corpo de erro: `application/problem+json` com `type`, `title`, `status`, `detail`, `codigo`, `mensagem` (= `detail`, campo do contrato) e extras.
- `public final class GeradorDeIds` (`nucleo`, registrado como bean em `web/BaseConfig` junto do `Clock`): `public UUID novo()`.
- `IdDeCorrelacao` (filtro de maior precedência): aceita `X-Request-Id` válido (`^[A-Za-z0-9-]{8,64}$`) ou gera um UUID v7; põe em `MDC["correlationId"]` e devolve no cabeçalho `X-Request-Id`.
- `ArquiteturaTest` (ArchUnit, só classes de produção, roda no `verify`):
  - `semCiclosEntreModulos`: fatias `bipotheking.net.duora.(*)..` livres de ciclos.
  - `moduloSoUsaAApiPublicaDeOutro`: classe do módulo A só depende, no módulo B, de classes do pacote raiz de B (nunca de `B.dominio`, `B.web` ou `B.infra`).
  - `dominioSemWebNemJdbc`: classes fora de `..web..`, `..infra..` e `..room..` (legado, com comentário apontando as Etapas 4/5) não dependem de `org.springframework.web..`, `org.springframework.jdbc..`, `org.springframework.data..`, `jakarta.servlet..`, `tools.jackson..` nem `com.fasterxml.jackson..`; `nucleo..` não depende de nenhum `org.springframework..`.

- [ ] **Passo 1:** testes da refatoração.
  - `GeradorDeIdsTest` com relógio fixo em `2026-11-22T12:00:00Z`: `version() == 7`; `variant() == 2`; os 48 bits altos valem `1795348800000`; dois ids no mesmo milissegundo estritamente crescentes (comparação sem sinal); 10 000 ids distintos.
  - `TratamentoDeErrosTest` (controller de teste): `ErroDeNegocio(CodigoWeb.NAO_AUTENTICADO, "Entre para continuar.")` → 401 com `codigo` e `mensagem`; `IllegalStateException("CANARY-<uuid>")` → 500 sem o canário, `Exception`, `at ` nem `bipotheking` no corpo.
- [ ] **Passo 2:** rodar; falha. Implementar, mover e ajustar `room/`. `GeradorDeIds` segue a RFC 9562 (48 bits de milissegundos, versão, 12 bits de sequência monotônica no mesmo milissegundo, variante, 62 bits aleatórios). `./mvnw -q verify` verde sem mudar asserções existentes (só imports). Commit `refactor(web): núcleo de erros sem http, ids v7 e filtros no pacote web`.
- [ ] **Passo 3:** escrever o `ArquiteturaTest` e rodar. Verde no código atual; se apontar algo, corrigir em commit `refactor:` separado antes. Prova de que morde: uma classe temporária em `game/` importando `org.springframework.jdbc.core.JdbcTemplate` faz `dominioSemWebNemJdbc` falhar; apagar a classe. Commit `test(arquitetura): fitness functions de ciclos, API pública e domínio`.
- [ ] **Passo 4:** `IdDeCorrelacaoTest`: com `X-Request-Id: teste-1234-abcd`, a resposta devolve o mesmo valor e a linha de log capturada é JSON com `"correlationId":"teste-1234-abcd"`; sem o cabeçalho, a resposta traz um UUID v7; valor `<script>` é trocado por um id gerado. Rodar, falhar, implementar, verde. Commit `feat(web): logs em json com correlation id`.

### Task 2: PostgreSQL 18, Flyway, Testcontainers, compose e ADR 0001

**Arquivos:**
- Modificar: `pom.xml`:
  - data-jdbc, flyway + `flyway-database-postgresql`, driver `postgresql` em runtime;
  - `spring-boot-docker-compose` opcional e `spring-boot-starter-actuator`;
  - em teste: `spring-boot-testcontainers` e o módulo PostgreSQL do Testcontainers 2.x;
  - surefire e failsafe com `spring.profiles.active=test` em `systemPropertyVariables`.
- Criar: `compose.yaml` (`postgres:18`, `127.0.0.1:5432:5432`, volume `duora-pg`, banco, usuário e senha `duora`, só para desenvolvimento)
- Criar: `src/main/resources/db/migration/V1__fundacao.sql` (só comentário)
- Modificar: `application.properties`:
  - datasource local com `${DUORA_DB_SENHA:duora}`;
  - `spring.docker.compose.lifecycle-management=start-only` e `spring.flyway.out-of-order=false`;
  - `management.endpoints.web.exposure.include=health`;
  - `spring.datasource.hikari.maximum-pool-size=10`;
  - `idle_in_transaction_session_timeout` de 30 s, pela `connection-init-sql` do Hikari.
- Criar: `application-prod.properties` (datasource só por variável, sem padrão; compose desligado)
- Criar: `src/test/resources/application-test.properties` (`spring.datasource.url=jdbc:tc:postgresql:18:///duora?TC_DAEMON=true`, compose desligado)
- Criar: `src/test/java/bipotheking/net/duora/web/{BancoTest,BancoDeTeste}.java`
- Criar: `docs/adr/0001-monolito-modular-e-postgres.md` (contexto, decisão, consequências: um deployable, módulos com API pública e ArchUnit, Postgres 18 com Flyway, Testcontainers, outbox no lugar de broker)
- Modificar: `README.md` (seção "Banco local") e `_mapa.md` (stack, comandos, pacotes `nucleo/`, camadas por módulo)

**Interfaces (produz):**
- `BancoDeTeste` (teste, classe base abstrata com `@SpringBootTest`): `@BeforeEach` faz `TRUNCATE ... CASCADE` nas tabelas do schema `public`, exceto `flyway_schema_history`. Testes com banco estendem esta classe (nome já consumido pelo plano da Etapa 4).

- [ ] **Passo 1:** `BancoTest`: `select version()` começa com `PostgreSQL 18`; `flyway_schema_history` tem a versão `1` com `success = true`; `GET /actuator/health` → 200 e `GET /actuator/env` → 404.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** dependências, configuração e ADR. Se o Testcontainers 2.x não aceitar `jdbc:tc:`, usar `@ServiceConnection` num `@TestConfiguration` importado pela `BancoDeTeste` e pelos `@SpringBootTest` existentes; registrar no commit.
- [ ] **Passo 4:** com o Docker Desktop aberto, `./mvnw -q verify` verde (todos os `@SpringBootTest` sobem com o contêiner). `./mvnw spring-boot:run` sobe o Postgres pelo compose e responde em 127.0.0.1:8080. Commit `build: postgres 18, flyway, testcontainers e compose; adr 0001`.

### Task 3: Limite de taxa e idempotência

**Arquivos:**
- Criar: `db/migration/V2__idempotencia.sql`
- Modificar: `pom.xml` (`com.bucket4j:bucket4j_jdk17-core`; Caffeine vem da gestão do Boot)
- Criar: `web/{LimiteDeTaxa,LimitesConfig,LimiteExcedido,RelogioDoBucket,Idempotencia}.java`
- Modificar: `web/TratamentoDeErros.java` (`LimiteExcedido` → `Retry-After`), `application.properties` (as quatro regras)
- Teste: `web/{LimiteDeTaxaTest,LimiteDeTaxaApiTest,IdempotenciaTest}.java`

**Esquema:** `chave_de_idempotencia(escopo text not null, chave text not null check (chave ~ '^[A-Za-z0-9-]{8,64}$'), impressao text not null, resposta jsonb, criada_em timestamptz not null, primary key (escopo, chave))` + índice em `criada_em`.

**Interfaces:**
- Consome: `ErroDeNegocio`, `CodigoWeb`, `Clock` (T1); `BancoDeTeste` (T2).
- Produz:
  - `public void LimiteDeTaxa.consumir(String regra, String chave)`. Excedido, lança `LimiteExcedido extends ErroDeNegocio` (`LIMITE_DE_TAXA`, mensagem `Muitas tentativas. Tente de novo em alguns minutos.`, `public long segundosParaTentarDeNovo()`). Regra desconhecida → `IllegalStateException`.
  - `public class Idempotencia` (exige transação aberta: `@Transactional(propagation = MANDATORY)`):
    - `public <T> Optional<T> respostaGravada(String escopo, String chave, String impressao, Class<T> tipo)`: existe com impressão diferente → `CHAVE_DE_IDEMPOTENCIA_REUSADA`.
    - `public <T> T executar(String escopo, String chave, String impressao, Class<T> tipo, Supplier<T> efeito)`: `INSERT ... ON CONFLICT DO NOTHING`. Se inseriu, roda o efeito e grava a resposta. Se não inseriu (a outra transação já terminou, porque o insert espera o commit da PK), devolve a resposta gravada, sem efeito.
    - Chave fora do formato → `CHAVE_DE_IDEMPOTENCIA_INVALIDA`.

- [ ] **Passo 1:** testes.
  - `LimiteDeTaxaTest` (`RelogioDeTeste`, regra de teste 3 por `PT15M`): três passam, o quarto lança com `segundosParaTentarDeNovo() == 900`; depois de `avancar(PT15M)` passa; chaves e regras diferentes não se afetam.
  - `LimiteDeTaxaApiTest`: a quarta chamada → 429, `codigo`=`LIMITE_DE_TAXA`, `Retry-After: 900`.
  - `IdempotenciaTest` (`BancoDeTeste`): `mesmaChaveEMesmoCorpoNaoRepeteEfeito` (efeito conta 1, as duas respostas iguais); `mesmaChaveCorpoDiferenteRecusada`; `escoposDiferentesNaoColidem`; `chaveMalFormadaRecusada` (`"abc"`, `"x y z 123"`); `chamadasSimultaneasUmEfeito` (duas threads, cada uma na sua transação, mesma chave → efeito 1); `semTransacaoFalha`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** buckets num Caffeine com chave `regra:chave`, `expireAfterAccess` = período da regra, no máximo 100 000 entradas; tempo do Bucket4j por `RelogioDoBucket` (`TimeMeter` sobre o `Clock`); `Retry-After` arredondado para cima.
- [ ] **Passo 4:** testes verdes. Commit `feat(web): limite de taxa e idempotência por chave`.

### Task 4: E-mail por outbox (Resend e log)

**Arquivos:**
- Criar: `db/migration/V3__outbox.sql`
- Criar:
  - raiz: `notificacao/{Outbox,ProcessadorDeOutbox,EnvioDeEmail,MensagemDeEmail}.java`;
  - adapters: `notificacao/infra/{OutboxJdbc,DespachanteDaOutbox,Backoff,ProcessadorDeEmail,EmailResend,EmailNoLog,EmailConfig,FalhaNoEnvio}.java`.
- Modificar: `application.properties` (`duora.email.provedor=log`, `duora.email.remetente=Duora <entrar@meetduora.com>`, `duora.outbox.intervalo=PT2S`), `application-prod.properties` (`duora.email.provedor=resend`, `duora.email.resend.chave=${RESEND_API_KEY}`)
- Teste: `notificacao/{OutboxTest,DespachanteDaOutboxTest,BackoffTest,EmailResendTest,EmailConfigTest}.java`

**Esquema:** `outbox(id uuid pk, tipo text not null, carga jsonb not null, correlation_id text, criada_em timestamptz not null, proxima_tentativa_em timestamptz not null, reservada_ate timestamptz, tentativas int not null default 0 check (tentativas >= 0), enviada_em timestamptz, falhou_em timestamptz, ultimo_erro text)` + índice parcial em `(proxima_tentativa_em) where enviada_em is null and falhou_em is null`.

**Interfaces (produz):**
- `public interface Outbox { <T> void registrar(String tipo, T carga); String EMAIL = "email"; }`. O adapter exige transação aberta (`MANDATORY`), serializa a carga e grava o `correlationId` do MDC.
- `public interface ProcessadorDeOutbox<T> { String tipo(); Class<T> tipoDaCarga(); void processar(T carga, UUID idDaMensagem); }`. Etapas futuras registram outros tipos (push, eventos).
- `public interface EnvioDeEmail { void enviar(MensagemDeEmail mensagem, UUID chaveDeIdempotencia); }`; `public record MensagemDeEmail(String para, String assunto, String texto, String html)`.
- `ProcessadorDeEmail` (`tipo() = "email"`) chama `EnvioDeEmail` com o id da mensagem como chave.
- `DespachanteDaOutbox`:
  - `@Scheduled(fixedDelayString = "${duora.outbox.intervalo}")` e `public int despacharAgora()` (testes);
  - reserva até 20 mensagens numa transação curta (`UPDATE ... SET reservada_ate = :agora + 2 min WHERE id IN (SELECT id ... ORDER BY id LIMIT 20 FOR UPDATE SKIP LOCKED) RETURNING ...`) e processa fora da transação, com o `correlationId` da mensagem no MDC;
  - sucesso → `enviada_em`; falha → `tentativas + 1` e `proxima_tentativa_em` pelo `Backoff`; na 10ª falha → `falhou_em` + log ERROR com id e tipo (sem a carga);
  - gauges `duora.outbox.pendentes` e `duora.outbox.falhas`.
- `Backoff`: `Duration proxima(int tentativas)` = min(2 s × 2^tentativas, 15 min) × fator em [0,8; 1,2] de um `RandomGenerator` injetado.
- `EmailResend`: `RestClient`, `POST https://api.resend.com/emails`, `Authorization: Bearer`, `Idempotency-Key`, timeouts de 5 s e 10 s; resposta fora de 2xx → `FalhaNoEnvio` (exceção técnica, não `ErroDeNegocio`).
- `EmailNoLog`: loga destinatário mascarado e o texto e guarda as 50 últimas; `public Optional<MensagemDeEmail> ultimaPara(String email)` (testes e e2e). Seleção por `duora.email.provedor` (`log` | `resend`).

- [ ] **Passo 1:** testes.
  - `OutboxTest`: `registrarSemTransacaoFalha`; `rollbackDescartaAMensagem` (registrar e lançar na mesma transação → zero linhas); `gravaCorrelationId`.
  - `DespachanteDaOutboxTest` (`BancoDeTeste`, `RelogioDeTeste`, `EnvioDeEmail` falso):
    - `enviaEMarca`;
    - `falhaDoResendTentaDeNovoComMesmaChave`: o falso falha uma vez; depois de avançar o backoff, a segunda tentativa usa a mesma chave de idempotência, e `enviada_em` fica preenchido;
    - `decimaFalhaMarcaFalha`;
    - `reservaImpedeEnvioDuplo`: duas chamadas a `despacharAgora` em threads → cada mensagem é processada uma vez;
    - `gaugesRefletemFila`.
  - `BackoffTest` com fator fixo 1,0: tentativas 0, 1 e 5 → 2 s, 4 s e 64 s; 20 → 15 min. Com o gerador no mínimo (0,8) e no máximo (1,2), os limites exatos.
  - `EmailResendTest` (`MockRestServiceServer`):
    - método, URL, `Authorization: Bearer re_teste` e `Idempotency-Key` iguais ao UUID passado;
    - corpo com `from`, `to` (um item), `subject`, `text` e `html`;
    - 500 e 422 → `FalhaNoEnvio`;
    - com a chave `re_CANARY-<uuid>`, o canário não aparece na saída capturada.
  - `EmailConfigTest`: `provedor=resend` sem chave → o contexto falha citando `duora.email.resend.chave`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. Conferir o formato do Resend pelo Context7.
- [ ] **Passo 4:** testes verdes. Commit `feat(notificacao): outbox transacional e envio de e-mail pelo Resend`.

### Task 5: E-mail válido, domínios descartáveis e verificação humana

**Arquivos:**
- Criar:
  - domínio: `conta/dominio/{Email,DominiosDescartaveis,VerificacaoHumana,ResumoDeEmail}.java`;
  - raiz: `conta/CodigoConta.java`;
  - adapter: `conta/infra/TurnstileVerificacao.java`.
- Criar: `src/main/resources/conta/dominios-descartaveis.txt` (cópia de `disposable_email_blocklist.conf` do repositório `disposable-email-domains/disposable-email-domains`, licença CC0, com cabeçalho de origem e data) e `scripts/atualizar-dominios-descartaveis.sh`
- Modificar: `application.properties` (`duora.turnstile.chave-do-site=1x00000000000000000000AA`, `duora.turnstile.segredo=1x0000000000000000000000000000000AA`, `duora.segredo-email=<valor fixo de desenvolvimento>`), `application-prod.properties` (`${TURNSTILE_SITE_KEY}`, `${TURNSTILE_SECRET}`, `${DUORA_SEGREDO_EMAIL}`)
- Teste: `conta/dominio/{EmailTest,DominiosDescartaveisTest,ResumoDeEmailTest}.java`, `conta/infra/TurnstileVerificacaoTest.java`, `conta/infra/TurnstileChavesDeTesteIT.java` (rede real, perfil `e2e`)

**Interfaces (produz):**
- `public record Email(String valor)` (parse, don't validate):
  - `public static Email de(String bruto)` aplica strip e minúsculas e exige até 254 caracteres, um `@`, parte local de 1 a 64, domínio com ponto e rótulos `[a-z0-9-]`, sem espaço nem caractere de controle; senão `EMAIL_INVALIDO` (`Confira o e-mail digitado.`);
  - `String dominio()`;
  - `String chaveDeBloqueio()` tira o `+tag` e, em `gmail.com`/`googlemail.com`, tira os pontos e usa `gmail.com`;
  - `String mascarado()`.
- `DominiosDescartaveis` (puro, lista carregada do classpath): `boolean contem(String dominio)` (casa subdomínios).
- `public interface VerificacaoHumana { boolean verificar(String token, String ip); }` (porta).
- `TurnstileVerificacao`:
  - faz `POST` de formulário em `https://challenges.cloudflare.com/turnstile/v0/siteverify` (`secret`, `response`, `remoteip`), com timeouts de 3 s e 5 s, e lê `success`;
  - token vazio ou com mais de 2048 caracteres → `false`, sem chamar a rede;
  - erro de rede, timeout ou 5xx → `false` com log WARN (falha fechada).
- `ResumoDeEmail` (chave injetada): `byte[] de(Email email)` = HMAC-SHA256 de `chaveDeBloqueio()`.
- `public enum CodigoConta implements CodigoDeErro` com os códigos de conta.

- [ ] **Passo 1:** testes.
  - `EmailTest` `@ParameterizedTest`:
    - inválidos: `""`, `" "`, `"a@b"`, `"a@@b.com"`, parte local com 65 caracteres, total com 255, `"x\u0000@a.com"` e `"a b@c.com"`;
    - `" A.B+promo@GMail.com "` → `valor()` = `a.b+promo@gmail.com` e chave `ab@gmail.com`;
    - `"a.b+x@outlook.com"` → chave `a.b@outlook.com`;
    - `mascarado()` de `ana@exemplo.com` = `a***@exemplo.com`.
  - `DominiosDescartaveisTest`: `mailinator.com` e `sub.mailinator.com` → `true`; `gmail.com` → `false`.
  - `ResumoDeEmailTest`: `Nome+x@gmail.com` e `n.ome@gmail.com` → mesmo resumo; chaves diferentes → resumos diferentes; o resumo difere do SHA-256 puro.
  - `TurnstileVerificacaoTest` (`MockRestServiceServer`):
    - `{"success":true}` → `true` e `{"success":false}` → `false`;
    - 500 → `false`, e resposta acima do timeout de leitura → `false`;
    - o corpo enviado contém `secret`, `response` e `remoteip`;
    - token vazio não faz requisição.
  - `TurnstileChavesDeTesteIT`: segredo `1x0000000000000000000000000000000AA` com o token `XXXX.DUMMY.TOKEN.XXXX` → `true`; segredo `2x0000000000000000000000000000000AA` → `false`.
- [ ] **Passo 2:** rodar os unitários; falham.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** unitários verdes; `-Pe2e -Dit.test=TurnstileChavesDeTesteIT` verde. Commit `feat(conta): e-mail normalizado, domínios descartáveis e turnstile`.

### Task 6: Contas, sessão e negação por padrão

Antes do Passo 1: `mattpocock-skills:domain-modeling` e `mattpocock-skills:codebase-design` (ver "Skills por momento").

**Arquivos:**
- Criar: `db/migration/V4__contas.sql`, `db/migration/V5__sessoes.sql`
- Criar:
  - raiz: `conta/{ContaId,EstadoConta,ContaResumo,ConsultaDeContas,ContaAtual,RotasPublicas,Sessoes,SessaoAtiva}.java`;
  - domínio: `conta/dominio/{Conta,Token,RepositorioDeContas,RepositorioDeSessoes}.java`;
  - web: `conta/web/{CookieSessao,ContaAtualResolver,ExigeSessao,ContaWebConfig,ContaController}.java`;
  - infra: `conta/infra/{ContasJdbc,SessoesJdbc}.java`.
- Teste: `conta/{SessoesTest,SessaoApiTest,RotasProtegidasTest,TerracoAnonimoTest,ContasDeTeste}.java`

**Esquema:**
- `conta(id uuid pk, email text unique, estado text not null check (estado in ('PENDENTE','ATIVA','SUSPENSA','BANIDA','EXCLUIDA')), data_nascimento date, criada_em timestamptz not null, cadastro_concluido_em timestamptz, excluida_em timestamptz, check (email = lower(email)), check (estado <> 'EXCLUIDA' or email is null), check (estado not in ('ATIVA','SUSPENSA','BANIDA') or data_nascimento is not null))`. O `unique` em coluna anulável é proposital: contas excluídas com `email` nulo não colidem.
- `sessao(id uuid pk, conta_id uuid not null references conta(id) on delete cascade, token_hash bytea not null unique, criada_em timestamptz not null, expira_em timestamptz not null, renovada_em timestamptz not null)` + índice em `conta_id` e em `expira_em`.

**Interfaces (produz):**
- `public record ContaId(UUID valor)` (não nulo; `toString()` = `valor.toString()`).
- `public enum EstadoConta { PENDENTE, ATIVA, SUSPENSA, BANIDA, EXCLUIDA }`.
- `@Target(PARAMETER) @Retention(RUNTIME) public @interface ContaAtual { boolean cadastroConcluido() default true; }`, num parâmetro `ContaId`.
- `public interface ConsultaDeContas { Optional<ContaResumo> porId(ContaId id); }`; `public record ContaResumo(ContaId id, EstadoConta estado, @Nullable LocalDate dataDeNascimento)` com `public OptionalInt idadeEm(LocalDate hoje)`.
- `public class Sessoes`:
  - `String abrir(ContaId conta)` (devolve o token);
  - `Optional<SessaoAtiva> validar(String token)` (renova por `UPDATE ... WHERE renovada_em < :agora - 24h`);
  - `void encerrar(String token)`;
  - `public void revogarTodas(ContaId conta)` (a Etapa 8 usa no banimento).
- `conta.dominio.Token`: `static Token novo(RandomGenerator)`, `String valor()`, `byte[] resumo()` (SHA-256).
- `RepositorioDeContas` (porta): `ContaId criarPendente(Email email)`, `Optional<Conta> porEmail(Email email)`, `Optional<Conta> porId(ContaId id)`.
- `public final class RotasPublicas`: `List<String> PADROES` = `/api/salas/**`, `/api/sala/**`, `/api/conta/entrar`, `/api/conta/confirmar`, `/api/conta/configuracao`, `/api/legal/**`. Etapas futuras adicionam aqui.
- `ExigeSessao` (interceptor em `/api/**`):
  - rota pública passa;
  - sem cookie, cookie inválido ou expirado → 401 `NAO_AUTENTICADO` (`Entre para continuar.`) e cookie expirado;
  - conta `SUSPENSA`, `BANIDA` ou `EXCLUIDA` → 403 `CONTA_INDISPONIVEL`;
  - conta `PENDENTE` em handler sem `@ContaAtual(cadastroConcluido = false)` → 403 `CADASTRO_INCOMPLETO`;
  - reemite o cookie quando a sessão foi renovada.
- `GET /api/conta` (`@ContaAtual(cadastroConcluido = false)`) → `{"estado":"ATIVA","cadastroConcluido":true}`; `POST /api/conta/sair` → 204 e cookie expirado.
- Propriedade `duora.sessao.cookie-seguro` (padrão `true`).
- Teste (bean `ContasDeTeste`): `ContaDeTeste criar(EstadoConta estado, LocalDate nascimento)` e `ContaDeTeste criarAtiva()`; `record ContaDeTeste(ContaId id, String cookie)` com `cookie` = `duora_sessao=<token>`. A Etapa 3 acrescenta `criar(String apelido, LocalDate nascimento)`.

- [ ] **Passo 1:** testes (estendem `BancoDeTeste`, usam `RelogioDeTeste`).
  - `SessoesTest`:
    - `abrir` + `validar` devolve a conta;
    - `token_hash` = SHA-256 do token, nunca o token;
    - depois de `avancar(P30D)` + 1 s, `validar` volta vazio;
    - depois de 25 h, `validar` empurra `expira_em` para agora + 30 dias; com 23 h, não grava nada (on/off point);
    - `encerrar` invalida;
    - `revogarTodas(A)` apaga as duas sessões de A e mantém a de B;
    - `renovacoesSimultaneasGravamUmaVez`: duas threads validam depois de 25 h → uma linha atualizada.
  - `SessaoApiTest`:
    - ATIVA → 200 com exatamente `estado` e `cadastroConcluido`;
    - PENDENTE → 200 `cadastroConcluido:false`;
    - SUSPENSA → 403 `CONTA_INDISPONIVEL`;
    - `POST /api/conta/sair` → `Set-Cookie` com `duora_sessao=` e `Max-Age=0`, e o mesmo cookie depois → 401.
  - `RotasProtegidasTest`: para cada padrão `/api/**` registrado no `RequestMappingHandlerMapping` fora de `RotasPublicas` (variáveis trocadas por `x`), a chamada sem cookie (com `X-Duora: 1`) → 401 `NAO_AUTENTICADO`; nenhuma rota pública → 401.
  - `TerracoAnonimoTest`: sem `duora_sessao`, `POST /api/salas` responde como hoje (201 com `duora_assento`).
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify` verde (terraço e `ArquiteturaTest` incluídos). Commit `feat(conta): contas, sessão em cookie e negação por padrão na API`.

### Task 7: Link mágico (entrar e confirmar)

**Arquivos:**
- Criar: `db/migration/V6__entrada.sql`
- Criar:
  - raiz: `conta/{Entrada,MensagensDeEntrada}.java`;
  - domínio: `conta/dominio/{RepositorioDeLinks,RepositorioDeBloqueios}.java`;
  - infra: `conta/infra/{LinksJdbc,BloqueiosJdbc}.java`;
  - web: `conta/web/EntradaController.java`.
- Teste: `conta/EntradaApiTest.java` (`VerificacaoHumana` trocada por um stub que recusa só o token `falha` e conta as chamadas; `EmailNoLog` real; `despacharAgora()` para entregar)

**Esquema:**
- `link_de_entrada(id uuid pk, email text not null check (email = lower(email)), token_hash bytea not null unique, criado_em timestamptz not null, expira_em timestamptz not null, usado_em timestamptz)` + índice em `email` e em `expira_em`.
- `email_bloqueado(chave_hmac bytea pk, motivo text not null check (motivo in ('MENOR')), criado_em timestamptz not null)`. A Etapa 8 amplia o `check` com `BANIMENTO`.

**Interfaces:**
- Consome: `LimiteDeTaxa`, `Idempotencia` (T3); `Outbox`, `EmailNoLog` (T4); `Email`, `DominiosDescartaveis`, `VerificacaoHumana`, `ResumoDeEmail` (T5); `RepositorioDeContas`, `Sessoes`, `CookieSessao` (T6).
- Produz:
  - `POST /api/conta/entrar` `{ "email", "tokenVerificacao" }`, cabeçalho opcional `Idempotency-Key` → 202 `{"mensagem":"Enviamos um link para o seu e-mail. Ele vale por 15 minutos."}`. Ordem, numa transação:
    1. limite `entrar-ip`;
    2. `Email.de`;
    3. com chave de idempotência, `respostaGravada` (escopo `entrar:` + hex do `ResumoDeEmail`, impressão = SHA-256 do e-mail normalizado) e devolve a resposta gravada antes de gastar Turnstile;
    4. limite `entrar-email`;
    5. Turnstile (`VERIFICACAO_HUMANA_FALHOU`, `Não foi possível confirmar que você é uma pessoa. Tente de novo.`);
    6. descartável (`EMAIL_DESCARTAVEL`, `Use um e-mail permanente.`);
    7. bloqueado: registra o aviso sem link na outbox e devolve o mesmo 202;
    8. invalida os links abertos do e-mail, grava o link novo e registra o e-mail na outbox (via `Idempotencia.executar` quando há chave).
  - `POST /api/conta/confirmar` `{ "token" }`:
    1. limite `confirmar-ip`;
    2. consumo atômico (`UPDATE ... SET usado_em = :agora WHERE token_hash = :h AND usado_em IS NULL AND expira_em > :agora RETURNING email`); nenhuma linha → 422 `LINK_INVALIDO` (`Este link expirou ou já foi usado. Peça um novo.`);
    3. conta pelo e-mail, ou `criarPendente`;
    4. conta SUSPENSA ou BANIDA → 403;
    5. encerra a sessão do cookie recebido e abre outra;
    6. responde 200 `{"cadastroConcluido": <bool>}` com `Set-Cookie`.
  - `GET /api/conta/configuracao` → `{"chaveDoSiteTurnstile": "..."}`.
  - Texto do e-mail (exato):
    - assunto: `Seu link para entrar no Duora`;
    - corpo: `Olá! Use este link para entrar no Duora. Ele vale por 15 minutos e só funciona uma vez:`, linha em branco, `<duora.url-publica>/entrar/confirmar#t=<token>`, linha em branco, `Se você não pediu, ignore este e-mail.`;
    - aviso de bloqueio: assunto `Cadastro no Duora`, corpo `Não foi possível criar uma conta no Duora com este e-mail.`.

- [ ] **Passo 1:** testes em `EntradaApiTest` (IP pelo `remoteAddr`).
  - Envio e resposta:
    - `entrarEnviaLinkDeQuinzeMinutos`: 202 com a mensagem exata; depois de `despacharAgora`, `ultimaPara` contém `/entrar/confirmar#t=`; `expira_em` = agora + 15 min;
    - `linkEEmailNaMesmaTransacao`: falha forçada depois do registro na outbox → zero links e zero mensagens;
    - `respostaIgualComOuSemConta`: mesmo status e corpo idêntico;
    - `emailDescartavelRecusado` e `verificacaoHumanaRecusada`: 422, outbox vazia.
  - Idempotência:
    - `repeticaoComMesmaChaveNaoDuplicaNemGastaTurnstile`: dois POST com a mesma `Idempotency-Key` → mesma resposta, um link, uma mensagem e uma chamada ao Turnstile;
    - `mesmaChaveOutroEmailRecusada`: 409.
  - Limite de taxa:
    - `rajadaPorIpBarrada`: 20 passam e o 21º → 429 com `Retry-After`;
    - `quartoPedidoDoMesmoEmailBarradoMesmoTrocandoIp`.
  - Confirmação:
    - `confirmarCriaContaPendenteEAbreSessao`: `Set-Cookie` com `duora_sessao=`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` e `Max-Age=2592000`; corpo `{"cadastroConcluido":false}`;
    - `linkUsadoDuasVezesRecusado`; `linkValeAte14min59s` e `linkExpiraAos15min`; `pedidoNovoInvalidaLinkAnterior`;
    - `linkSobreviveAoPreCarregamento`: `GET /entrar/confirmar?t=<token>`, depois o POST → 200;
    - `confirmacoesSimultaneasAbremUmaSessao`: duas threads → um 200 e um 422;
    - `loginNovoEncerraSessaoAnterior`.
  - Bloqueio e log:
    - `emailBloqueadoRecebeAvisoSemLink`: 202 igual, e a mensagem não contém `#t=`;
    - `logNaoTrazEmailNemToken`: com o logger de `notificacao` desligado, a saída capturada não contém o e-mail canário (`canary-<uuid>@exemplo.com`) nem o token.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. O caso de uso `Entrada` fica na raiz (só portas e `web.LimiteDeTaxa`/`web.Idempotencia`); o controller traduz o cabeçalho e o cookie.
- [ ] **Passo 4:** testes e `ArquiteturaTest` verdes. Commit `feat(conta): entrada por link mágico com outbox e idempotência`.

### Task 8: Cadastro (nascimento autodeclarado e aceite)

**Arquivos:**
- Criar: `db/migration/V7__cadastro.sql`
- Criar:
  - domínio: `conta/dominio/{Idade,ResultadoDoCadastro,RepositorioDeAceites}.java` e a regra `Conta.concluirCadastro(...)`;
  - raiz: `conta/Cadastro.java`;
  - infra: `conta/infra/AceitesJdbc.java`;
  - web: `conta/web/CadastroController.java`.
- Teste: `conta/dominio/{IdadeTest,ContaTest}.java`, `conta/CadastroApiTest.java`

**Esquema:** `aceite_documento(conta_id uuid not null references conta(id) on delete cascade, documento text not null check (documento in ('TERMOS','PRIVACIDADE')), versao text not null, resumo_sha256 text not null check (resumo_sha256 ~ '^[0-9a-f]{64}$'), aceito_em timestamptz not null, primary key (conta_id, documento, versao))`.

**Interfaces:**
- Consome:
  - Etapa 9 Task 1: `DocumentosLegais.atual(DocumentoLegal documento)` → `VersaoDeDocumento(String versao, String resumoSha256)` (hex minúsculo);
  - `RepositorioDeBloqueios`, `ResumoDeEmail` (T5, T7);
  - `Sessoes.revogarTodas` (T6).
- Produz:
  - `static boolean Idade.maiorDeIdade(LocalDate nascimento, LocalDate hoje)` (≥ 18 anos completos por `Period.between`).
  - `public sealed interface ResultadoDoCadastro permits Concluido, RecusadoPorIdade` e `ResultadoDoCadastro Conta.concluirCadastro(LocalDate nascimento, LocalDate hoje)`: decisão pura, sem efeito.
    - Estado ≠ PENDENTE → `CADASTRO_JA_CONCLUIDO`; a data é imutável.
    - Data no futuro ou antes de 1900-01-01 → `DATA_INVALIDA`.
  - `POST /api/conta/cadastro` (`@ContaAtual(cadastroConcluido = false)`), corpo estrito `{ "dataDeNascimento": "AAAA-MM-DD", "versaoTermos", "versaoPrivacidade", "aceito": true }`. Checagens, na ordem:
    1. limite `cadastro-conta`;
    2. `aceito` ≠ `true` → 422 `ACEITE_NECESSARIO`;
    3. versões ≠ atuais → 422 `TERMOS_DESATUALIZADOS`;
    4. `concluirCadastro`.
  - Efeitos do resultado, numa transação:
    - `RecusadoPorIdade` → bloqueia o resumo (`INSERT ... ON CONFLICT DO NOTHING`), revoga as sessões e muda a conta para EXCLUIDA com `email` e nascimento nulos; responde 422 `MENOR_DE_IDADE` (`O Duora é só para maiores de 18 anos.`) e expira o cookie;
    - `Concluido` → `UPDATE conta SET ... WHERE id = :id AND estado = 'PENDENTE'` (zero linhas → 409), grava os dois aceites com o resumo e responde 200 `{"cadastroConcluido":true}`.

- [ ] **Passo 1:** testes.
  - `IdadeTest` `@ParameterizedTest`:
    - com hoje = 2026-11-22: 2008-11-22 → `true`, 2008-11-23 → `false`, 1990-01-01 → `true`;
    - com hoje = 2026-02-28, 2008-02-29 → `false`; com 2026-03-01 → `true`.
  - `ContaTest` (puro): `pendenteMaiorConclui`, `pendenteMenorRecusada`, `ativaNaoConcluiDeNovo`, `dataFuturaRecusada`, `dataAntesDe1900Recusada`.
  - `CadastroApiTest`:
    - `maiorConcluiCadastro`: conta ATIVA, com duas linhas de aceite;
    - `segundoEnvioRecusado`: 409, data inalterada;
    - `cadastrosSimultaneosUmVence`: duas threads → um 200 e um 409;
    - `menorRecusadoEBloqueado`: 422, cookie expirado, conta EXCLUIDA sem e-mail e uma linha em `email_bloqueado`;
    - `menorTentaDeNovoComVariacaoDoEmail`: bloqueado `Nome+x@gmail.com`; `entrar` com `n.ome@gmail.com` → o aviso sai sem link;
    - `versaoAntigaRecusada` e `semAceiteRecusado`;
    - `campoExtraRecusado`: `"estado":"ATIVA"` no corpo → 400, conta inalterada;
    - `idadeUsaFusoDeSaoPaulo`: relógio em 2026-11-22T02:00Z com nascimento 2008-11-22 → 422.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** testes verdes. Commit `feat(conta): cadastro com nascimento autodeclarado e aceite versionado`.

### Task 9: Exclusão de conta e limpeza com métrica

**Arquivos:**
- Criar:
  - raiz: `conta/{AoExcluirConta,ExclusaoDeConta}.java`;
  - web: `conta/web/ExclusaoController.java`;
  - infra: `conta/infra/{RetencaoDeLinks,RetencaoDeSessoes}.java`, `notificacao/infra/RetencaoDaOutbox.java` e `web/RetencaoDeIdempotencia.java`.
- Teste: `conta/{ExclusaoApiTest,RetencaoDeContaTest}.java`, `notificacao/RetencaoDaOutboxTest.java`, `web/RetencaoDeIdempotenciaTest.java`

**Interfaces:**
- Consome: Etapa 9 Task 2, `retencao/PoliticaDeRetencao` (`String nome()`, `Duration prazo()`, `int expirar(Instant limite)`, `long tamanho()`).
- Produz:
  - `public interface AoExcluirConta { void excluir(ContaId conta); }`: roda na transação da exclusão, na ordem do `@Order`; arquivos só são apagados no `afterCommit`. As Etapas 3, 5, 6 e 8 implementam (a 8 anonimiza o que fica para moderação).
  - `POST /api/conta/exclusao` (`@ContaAtual(cadastroConcluido = false)`) `{ "confirmo": true }`:
    - sem confirmação → 422 `CONFIRMACAO_NECESSARIA`;
    - senão, numa transação: ganchos → apaga aceites, sessões e links do e-mail → conta vira EXCLUIDA com `email` e `data_nascimento` nulos e `excluida_em`. A linha fica como lápide anônima para as chaves estrangeiras da moderação.
    - Responde 204 com cookie expirado.
  - Políticas (lotes de 1000; `tamanho()` conta as linhas que acumulam):

    | Nome | Prazo | Apaga |
    |---|---|---|
    | `links-de-entrada` | `P1D` | links com `expira_em` < limite |
    | `sessoes-expiradas` | `PT0S` | sessões com `expira_em` < limite |
    | `outbox-enviada` | `P7D` | mensagens com `enviada_em` < limite |
    | `outbox-falhada` | `P30D` | mensagens com `falhou_em` < limite |
    | `chaves-de-idempotencia` | `P1D` | chaves com `criada_em` < limite |

- [ ] **Passo 1:** testes.
  - `ExclusaoApiTest`:
    - `semConfirmacaoRecusa`;
    - `excluiDadosPessoais`: e-mail e nascimento nulos, EXCLUIDA, zero sessões e aceites, cookie antigo → 401;
    - `ganchoRodaNaExclusao`;
    - `falhaNoGanchoDesfazTudo`: 500, e a conta segue ATIVA com e-mail;
    - `mesmoEmailCriaContaNova`;
    - `outraContaIntacta`.
  - Retenção, com uma classe de teste por política:
    - `RetencaoDeContaTest`: link vencido há 2 dias some e o de 12 h fica; sessão vencida some e a válida fica; 2500 links vencidos → `expirar` devolve 2500; `tamanho()` antes e depois.
    - `RetencaoDaOutboxTest`: enviada há 8 dias some; enviada há 6 dias e pendente ficam.
    - `RetencaoDeIdempotenciaTest`: chave de 25 h some e a de 23 h fica.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** testes verdes. Commit `feat(conta): exclusão de conta e limpeza de links, sessões, outbox e idempotência`.

### Task 10: Telas de entrada e cadastro

**Arquivos:**
- Criar: `frontend/src/telas/conta/{Entrar,ConfirmarEntrada,Cadastro}.tsx` com `*.test.tsx`, `frontend/src/componentes/Turnstile.tsx`, `frontend/src/api/conta.ts`
- Modificar:
  - o roteador da 1a: rotas `/entrar`, `/entrar/confirmar` e `/cadastro`, e o link "Entrar" no topo sem sessão;
  - o cliente HTTP da 1a: 401 `NAO_AUTENTICADO` → navega uma vez para `/entrar`, sem laço;
  - `frontend/src/sessao/` (estado de sessão por `GET /api/conta`).
- Modificar: `web/CabecalhosDeSeguranca.java` (CSP ganha `script-src 'self' https://challenges.cloudflare.com` e `frame-src https://challenges.cloudflare.com`; nada de `unsafe-*`)
- Criar: `src/test/.../e2e/{ContaIT,ContaPagina}.java`; Modificar: `NavegadorExtension.Participantes`, `AxeIT`

**Interfaces:**
- Consome: `POST /api/conta/entrar`, `POST /api/conta/confirmar`, `GET /api/conta/configuracao`, `GET /api/conta` (T6, T7); `POST /api/conta/cadastro` (T8); `GET /api/legal/versoes` (Etapa 9 T1).
- Produz:
  - `Participantes.novoComConta(ContaDeTeste conta)` (contexto já com o cookie de sessão; consumido pela Etapa 4);
  - `ContaPagina` (page object): `entrar(String email)`, `abrirLinkRecebido(EmailNoLog caixa, String email)`, `concluirCadastro(LocalDate nascimento)` e `cadastrarContaNova(...)`.
- O formulário Entrar gera `crypto.randomUUID()` por envio e reusa o valor como `Idempotency-Key` numa repetição automática.
- Textos (exatos):
  - **Entrar:**
    - título `Entrar no Duora`, rótulo `Seu e-mail`;
    - botão `Enviar link`, desabilitado até o Turnstile devolver o token;
    - sucesso = mensagem do servidor;
    - 429: `Muitas tentativas. Tente de novo em {n} minutos.` (a partir de `Retry-After`).
  - **Confirmar:** título `Confirmar entrada`, botão `Entrar`; `LINK_INVALIDO` mostra a mensagem do servidor e o link `Pedir novo link`.
  - **Cadastro:**
    - título `Falta pouco`;
    - rótulo `Data de nascimento` (`type=date`, `max` = hoje), com a ajuda `Ela não pode ser alterada depois.`;
    - caixa `Li e aceito os Termos de uso e a Política de privacidade`, com links para `/termos` e `/privacidade` em nova aba (`rel="noopener noreferrer"`);
    - botão `Concluir cadastro`;
    - `MENOR_DE_IDADE` → `O Duora é só para maiores de 18 anos. Não foi possível criar a conta.`

- [ ] **Passo 1:** testes Vitest.
  - `Entrar`:
    - envia e-mail, token e `Idempotency-Key`;
    - botão desabilitado sem token;
    - 429 mostra os minutos.
  - `ConfirmarEntrada`:
    - lê `#t=` e limpa a URL com `history.replaceState` antes do POST;
    - com `cadastroConcluido:false`, vai para `/cadastro`.
  - `Cadastro`: envia as versões de `/api/legal/versoes`.
  - `naoAutenticadoLevaAEntrar`.
- [ ] **Passo 2:** `ContaIT` (Turnstile real com as chaves de teste da Cloudflare). Rodar e ver falhar.
  - `cadastroCompletoNoCelular`: `Participantes.novoCelular()`; entrar; despachar a outbox; link do `EmailNoLog`; Entrar; nascimento 1990-05-10; chega a `/` com "Minha conta" no topo.
  - `menorRecusadoNoCadastro`.
  - `rajadaMostraAviso`: quatro envios do mesmo e-mail → `Muitas tentativas`.
  - `sessaoSobreviveARecarga`.
- [ ] **Passo 3:** implementar. O `Turnstile` carrega `https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit` uma vez e chama `turnstile.render` com a chave de `/api/conta/configuracao`.
- [ ] **Passo 4:** Vitest verde; `ContaIT` e `AxeIT` verdes nos três navegadores. Commit `feat(front): telas de entrada por link mágico e cadastro`.

### Task 11: Minha conta (sair e excluir) e prova no Android

**Arquivos:**
- Criar: `frontend/src/telas/conta/MinhaConta.tsx` + teste; rota `/conta`
- Criar: `src/test/.../e2e/MinhaContaIT.java`; Modificar: `AxeIT`
- Criar: `scripts/android/conta-check.mjs` (padrão do `pwa-check.mjs`: `adb reverse`, Chrome do emulador, CDP)

**Interfaces:**
- Consome: `POST /api/conta/sair` (T6), `POST /api/conta/exclusao` (T9), `ContaPagina` (T10).
- Textos:
  - título `Minha conta`, botão `Sair`;
  - seção `Excluir conta`, com o texto `Apaga seus dados pessoais. Não dá para desfazer.`, a caixa `Entendo que isso apaga meus dados` e o botão `Excluir conta`, desabilitado até marcar;
  - confirmação em `alertdialog`.

- [ ] **Passo 1:** Vitest: `Sair` chama a API e navega para `/entrar`; `Excluir conta` desabilitado até marcar e envia `{"confirmo":true}`.
- [ ] **Passo 2:** `MinhaContaIT`: `sairEncerraSessao`; `excluirApagaConta` (entrar de novo com o mesmo e-mail cai em `/cadastro`). Rodar e ver falhar.
- [ ] **Passo 3:** implementar a tela e o `conta-check.mjs`. O script cadastra uma conta pelo Chrome do emulador, lendo o link do log JSON do `EmailNoLog`, e salva capturas de Entrar, Cadastro e Minha conta em `target/android/`.
- [ ] **Passo 4:** Vitest, `MinhaContaIT` e `AxeIT` verdes nos três navegadores; `node scripts/android/conta-check.mjs` sai com 0 e as capturas mostram o cadastro concluído. `superpowers:verification-before-completion` e `duora-etapa`. Commit `feat(front): minha conta com sair e excluir; prova no Android`.

**Prova da etapa:**
- **Cadastro pelo celular:** `ContaIT.cadastroCompletoNoCelular` e `conta-check.mjs`.
- **Menor recusado:** `CadastroApiTest.menorRecusadoEBloqueado` e `ContaIT.menorRecusadoNoCadastro`.
- **Rajada barrada:** `EntradaApiTest.rajadaPorIpBarrada` e `ContaIT.rajadaMostraAviso`.
- **Terraço:** a suíte do terraço fica verde sem conta, e o `ArquiteturaTest` também.
