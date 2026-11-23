# Etapa 2a — Persistência e contas (sem verificação de idade): plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` ou `mattpocock-skills:tdd`.

**Objetivo:** uma pessoa cria conta pelo celular com e-mail, link mágico, data de nascimento e aceite dos termos e chega a "aguardando verificação"; um menor declarado é recusado, uma rajada de cadastros é barrada, contas novas têm limites por dia, os acessos ficam registrados por 183 dias, há um canal público de denúncia e o contato do encarregado, e o terraço anônimo continua funcionando como hoje.

**Arquitetura:** monólito modular com PostgreSQL 18 e Flyway (ADR 0001). `nucleo/` (Java puro: erros de domínio sem HTTP, ids UUID v7), `web/` (adapters HTTP transversais: tradução de erro, correlation ID, limite de taxa, idempotência, cabeçalhos, rotas do SPA), `conta/` em três camadas (raiz = API pública e casos de uso; `dominio/` sem Spring web, JDBC nem Jackson; `web/` e `infra/` como adapters), `notificacao/` (transactional outbox) e `retencao/` (núcleo da Etapa 9). A ArchUnit trava as fronteiras no `verify`. Um interceptor nega por padrão toda `/api/**` sem sessão, exceto a allowlist, que inclui o terraço anônimo. No front, uma casca de conta com React Router e TanStack Query carregada por `import()` fora do `/`; o terraço continua sem router.

**Stack:** Java 25, Spring Boot 4.2 (webmvc, data-jdbc, flyway, restclient, docker-compose, actuator só com `health`), PostgreSQL 18, Testcontainers 2.x (`postgres:18`), ArchUnit (JUnit 5), Bucket4j + Caffeine, commonmark, `RestClient` (Resend, Turnstile siteverify), log estruturado do Spring Boot; React 19 + TypeScript 6 strict + Tailwind 4 (Vite 8), React Router e TanStack Query (só na casca de conta), Vitest + Testing Library, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 2: 18+, login, LGPD). **Contratos e princípios (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. **Origem:** este plano substitui as Tasks 1–11, 15 e 16 de `docs/superpowers/plans/2026-11-22-etapa-2-contas.md` e as Tasks 1 e 2 de `docs/superpowers/plans/2026-11-22-etapa-9-termos.md`. As Tasks 12, 13, 14 e 17 (verificação de idade) vão para o plano da Etapa 2b. Conferir pelo Context7 as APIs antes da task que as usa.

**Pré-requisitos:** Etapa 1c na `main` (0053aea). Nenhuma conta externa: Turnstile com as chaves de teste da Cloudflare, e-mail pelo `EmailNoLog`, Postgres local por Docker.

## Decisões do dono (2026-11-23)
- **D-A Escopo:** 2a agora, 2b depois. Na 2a a conta para em `AGUARDANDO_VERIFICACAO` e o SPA mostra a tela "aguardando verificação". Nenhuma flag leva a conta a `ATIVA` sem verificação.
- **D-F1 Roteador:** React Router + TanStack Query só na casca de conta, carregada por `import()` quando o caminho não é `/`. Teto do JS inicial: 100 KB gzip, medido na F2.
- **D-1 Outbox:** a carga é zerada ao enviar ou na falha definitiva; a linha guarda tipo, datas e erro. A exclusão de conta zera as mensagens pendentes dela.
- **D-3 Aceites:** apagados com a conta.

## Rulings do controlador (o dono pode reverter)
- **D-2** IP e porta do registro de acesso vêm de `OrigemDaRequisicao` (`remoteAddr`/`remotePort`); a fonte atrás da Cloudflare é decidida na Etapa 10.
- **D-4** Logs em ECS em `prod` e no teste de formato; texto em `dev`.
- **D-F3** Turnstile falso por `page.route` no e2e; chaves de teste da Cloudflare em dev e test.
- **D-F4** `duora.sessao.cookie-seguro=false` só no e2e do WebKit; HTTPS local fica para a Etapa 10.
- **D-F5** Link de conta no topo do terraço já na 2a: `GET /api/conta` a cada carga do `/`; 401 significa anônimo e nunca redireciona.
- **Migrações:** V1 fundação, V2 idempotência, V3 outbox, V4 contas, V5 sessões, V6 entrada, V7 cadastro, V8 registro de acessos, V9 conta nova, V10 denúncia pública. A 2b usa V11–V12 e entra na `main` antes da V20 da Etapa 3; V13–V19 para correções. Uma migração menor nunca entra depois de uma maior; `spring.flyway.out-of-order=false`.
- **ADRs:** 0001 fundação (P2), 0006 retenção por política (P10), 0007 verificação de idade (2b).
- **Ordem entre etapas:** 2a → 3 ∥ 2b → 4.

## Global Constraints
- Tudo do arquivo de contratos, inclusive "Princípios de engenharia", vale aqui; em conflito, o contrato vence e o plano é corrigido.
- **Camadas por módulo:** a raiz do pacote é a API pública e os casos de uso (pode `@Service`/`@Transactional`, nunca `org.springframework.web..`, `..jdbc..`, `..data..`, `jakarta.servlet..` nem Jackson); `dominio/` é Java puro; `web/` tem controllers, filtros, interceptors e cookies; `infra/` tem JDBC e serviços externos. Outro módulo só usa a raiz. Classes package-private por padrão. Exceção registrada: `web/Idempotencia` (P7) e `web/RetencaoDeIdempotencia` (P11) usam JDBC dentro de `web/`, porque a reserva é parte do filtro HTTP. Os enums de código de erro (`Codigo*`) ficam na raiz do módulo, como API pública (as strings são o contrato com o front); o `dominio/` os lança, e o ciclo raiz ↔ `dominio` por esses códigos é aceito (a ArchUnit não o vê).
- **Portas só em borda real:** `Clock`, `EnvioDeEmail`, `VerificacaoHumana`, `Outbox` e os repositórios. Nada de interface 1:1 por reflexo.
- **Tempo e ids:** todo instante vem do `Clock` injetado; todo id vem de `GeradorDeIds` (UUID v7). Idade no fuso `America/Sao_Paulo`. Sem `static` mutável; injeção pelo construtor (inclusive `RandomGenerator` para jitter).
- **Invariantes no banco:** e-mail único e normalizado (`unique` + `check (email = lower(email))`); token de link e de sessão `unique`, consumo por `UPDATE` condicional; chave HMAC do e-mail bloqueado como PK; aceite com PK `(conta_id, documento, versao)`; chave de idempotência com PK `(escopo, chave)`.
- **Segredos aleatórios:** tokens de 32 bytes de `SecureRandom` em base64url; o banco guarda só o SHA-256 (`bytea`). E-mail bloqueado vira HMAC-SHA256 com `DUORA_SEGREDO_EMAIL`.
- **Link mágico:** uso único, vale 15 min, um pedido novo invalida os anteriores do mesmo e-mail. Token no fragmento (`/entrar/confirmar#t=<token>`), consumido só por `POST`. Um link de e-mail bloqueado nunca cria nem reativa conta.
- **Sessão:** cookie `duora_sessao`, `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=2592000`. Renovação deslizante gravada no máximo a cada 24 h, por `UPDATE` condicional. Cada login abre uma sessão nova e encerra a anterior do mesmo navegador. Logout apaga a linha.
- **Sem dual write:** e-mails vão para a `outbox` na mesma transação do fato. Despachante com retry, backoff exponencial (2 s × 2ⁿ, teto 15 min, jitter ±20 %) e `Idempotency-Key` = id da mensagem no Resend; depois de 10 falhas, falha definitiva e log ERROR. Carga zerada ao enviar ou falhar de vez (D-1).
- **Idempotência:** `POST /api/conta/entrar` aceita `Idempotency-Key` (`^[A-Za-z0-9-]{8,64}$`). A chave é reservada antes de chamar o Turnstile. A impressão é `Resumos.sha256Hex` do e-mail normalizado (o token do Turnstile muda a cada tentativa e fica fora). Mesma chave e mesmo e-mail → mesma resposta, sem novo efeito nem novo token do Turnstile; mesma chave com outro e-mail → 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`. Sem o cabeçalho, segue normal.
- **Resposta de `entrar`:** igual em status e corpo, exista ou não a conta, esteja ou não o e-mail bloqueado.
- **Timeouts:** Resend (conexão 5 s, leitura 10 s), Turnstile (conexão 3 s, leitura 5 s). Nenhuma chamada HTTP externa dentro de transação de banco.
- **Limites de taxa** (`duora.limites.regras.<regra>.capacidade|periodo`, multiplicados por `duora.limites.multiplicador`, formato da P6): `entrar-ip` IP 20/`PT10M`; `entrar-email` `Email.chaveDeBloqueio()` 3/`PT15M`; `confirmar-ip` IP 30/`PT10M`; `cadastro-conta` `ContaId` 10/`PT10M`; `denuncia-publica-ip` IP 5/`PT1H`. IP = `OrigemDaRequisicao`. No e2e, o failsafe põe `duora.limites.multiplicador=1000` (P2): limites folgados por propriedade de teste, nunca desligados no código.
- **Contas novas:** nos primeiros 7 dias depois de `ativada_em`, `duora.conta-nova.mensagens-por-dia=200`, `imagens-por-dia=10`, `convites-por-dia=3` (dia em `America/Sao_Paulo`).
- **Registros de acesso (Marco Civil art. 15):** IP, porta de origem e instante de cada login e renovação de sessão, por 183 dias, em tabela própria, sem API de leitura e nunca no log.
- **Acúmulos com limpeza e métrica:** links, sessões, outbox e chaves de idempotência têm política de retenção (sobre `retencao/PoliticaDeRetencao`) e gauge `duora.retencao.tamanho{politica}`; a outbox expõe `duora.outbox.pendentes` e `duora.outbox.falhas`.
- **Logs:** `correlationId` no MDC de toda requisição e todo despacho. Nunca e-mail completo, token, cookie ou data de nascimento; e-mail mascarado (`a***@dominio.com`). Exceção: o `EmailNoLog` (dev) escreve o link.
- **Segredos:** só por variável de ambiente; `application-prod.properties` referencia `${RESEND_API_KEY}`, `${TURNSTILE_SITE_KEY}`, `${TURNSTILE_SECRET}`, `${DUORA_SEGREDO_EMAIL}`, `${DUORA_EMAIL_ENCARREGADO}` e a senha do banco sem padrão (boot falha se faltar). Dev e test usam as chaves de teste da Cloudflare, chave HMAC fixa e `encarregado@exemplo.invalid`.
- **Erros:** o domínio lança `ErroDeNegocio` com um `CodigoDeErro` cujo `TipoDeErro` não conhece HTTP; só `web/TratamentoDeErros` traduz para status. Códigos da 2a: `NAO_AUTENTICADO`, `CONTA_INDISPONIVEL`, `CADASTRO_INCOMPLETO`, `EMAIL_INVALIDO`, `EMAIL_DESCARTAVEL`, `VERIFICACAO_HUMANA_FALHOU`, `LINK_INVALIDO`, `DATA_INVALIDA`, `ACEITE_NECESSARIO`, `TERMOS_DESATUALIZADOS`, `MENOR_DE_IDADE`, `CONFIRMACAO_NECESSARIA`, `CADASTRO_JA_CONCLUIDO`, `LIMITE_DE_TAXA` (429 com `Retry-After`), `CHAVE_DE_IDEMPOTENCIA_INVALIDA`, `CHAVE_DE_IDEMPOTENCIA_REUSADA`, `LIMITE_DE_CONTA_NOVA` 429, `DENUNCIA_PUBLICA_INVALIDA` 422, `DOCUMENTO_INEXISTENTE` 404.
- **Terraço anônimo:** `room/` não muda de comportamento (convite anônimo, cookie `duora_assento` com `Path=/api` e `SameSite=Strict`, polling). A allowlist cobre todas as rotas reais de `room/`, inclusive `GET /api/sala` → 204 sem assento. A suíte atual fica verde sem mudar asserções; `TerracoAnonimoTest` trava isso.
- **Front:** pt-BR, gênero neutro, voz ativa, Tailwind, sem `alert`/`confirm`/`prompt`, axe em toda tela nova, seletores por papel/rótulo/texto. Telas novas em `frontend/src/telas/conta/` e `frontend/src/telas/legal/`. Skills obrigatórias nas tasks de tela: frontend-design, interaction-design e ux-writing; revisão com accessibility-audit e design-critique. JS inicial ≤ 100 KB gzip.
- **Testes com banco:** depois da P2, todo `verify` e todo e2e precisam de Docker. Config de teste em `src/test/resources/config/application.properties` (vale pela IDE). Um único contêiner Postgres reutilizado por JVM de teste.
- **Commits:** refatoração separada de comportamento, sempre com testes verdes; Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Nunca `git add -A`.
- **Máquina:** comandos pesados um por vez, sob a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` com `caffeinate -dims`; uma suíte com Docker por vez; remover os contêineres criados.

## Posse de arquivos por trilha
- **P (plataforma):** `nucleo/`, `web/` (exceto `web/RotasDoSpa*` e `web/CabecalhosDeSeguranca*` depois da P1), `notificacao/`, `retencao/`, `pom.xml` (dona única), `compose.yaml`, `application*.properties` (cria os blocos `# plataforma`, `# conta`, `# legal`, `# front`; cada trilha edita só o seu), `src/test/resources/config/`, V1–V3, ADRs 0001 e 0006, `.github/workflows/`, `.claude/skills/duora-etapa/SKILL.md`, `README.md`.
- **C (contas):** `conta/**`, `src/main/resources/{legal,conta}/`, `src/test/resources/legal-teste/`, `scripts/atualizar-dominios-descartaveis.sh`, V4–V10, testes de back de conta e legais.
- **F (front):** `frontend/**`, `web/RotasDoSpa*`, `web/CabecalhosDeSeguranca*` (depois da P1), `src/test/java/**/web/CspTest.java`, `src/test/java/**/pwa/**`, `src/test/java/**/e2e/**`, scripts do Android.

## Skills por momento
- **Antes da C3:** `mattpocock-skills:domain-modeling` sobre o modelo de Conta (estados, transições, invariantes) e `mattpocock-skills:codebase-design` com duas alternativas para `ConsultaDeContas`, `AoExcluirConta` e `Outbox`. Se uma interface pública mudar, atualizar este plano e os consumidores (Etapas 3, 4 e 8) antes de codar.
- **Antes da P10:** `mattpocock-skills:codebase-design` com duas alternativas para `PoliticaDeRetencao`.
- **Execução:** TDD. **Bug ou teste instável:** `superpowers:systematic-debugging`. **Revisão por task:** o revisor cita smell ou refactoring pelo nome. **Antes de "pronto":** `superpowers:verification-before-completion` e `duora-etapa`.

## Ordem e paralelismo: três filas com portões
Cada trilha é uma fila sequencial com um implementador por vez (Opus · High, worktree isolado). As três filas correm em paralelo; uma task só começa quando os seus portões (tasks de outras trilhas) já estão na branch `etapa-2`. No máximo 3 implementadores e 1 revisor ao mesmo tempo.

| Trilha | Fila | Portões de outras trilhas |
|---|---|---|
| P | P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9 → P10 → P11 | nenhum |
| C | C1 → C2 → L1 → C3 → C4 → C5 → C6 → C7 → C8 → C9 → C10 → C11 → C12 | C1: P2, P5 · C2: P2 · L1: P2, P5 · C3: P3, P5 (merge depois de P8, por V2/V3) · C4: P2, P5 · C5: P6–P9 · C6: P6, F3 · C7: P6 · C8: P8 · C9–C12: P10 (C12 também P8, P9). A fila P é sequencial: P5 implica P1–P4 |
| F | F1 → F2 → F3 → F5 → F4 → F6 → F7 → F8 → L2 → F9 → F10 → F11 → F12 → F13 | F3, F5: P1 · F4: C4 · F6: C5 · F7: C6 · F8: C7, L1 · L2: L1 · F9: C8 · F10: P2, P6, P8, P9, C2, C4–C7 · F11: C8 · F12: C12, L1 · F13: C12, L1 |

- Telas F6–F9 podem ser escritas contra o contrato com `fetch` falso nos testes de unidade; o portão vale para o merge, não para o início.
- **Migrações:** a ordem de merge é V1 (P2) → V2 (P7) → V3 (P8) → V4/V5 (C3) → V6 (C5) → V7 (C7) → V8 (C10) → V9 (C11) → V10 (C12). A C3 espera a P8 para o merge mesmo que esteja pronta antes.
- **Caminho crítico:** a trilha C (migrações sequenciais). A C1 começa assim que a P5 entra (a C2 só precisa da P2 e corre logo depois na fila).
- **Arquivos compartilhados:** `pom.xml` só na P2; `application*.properties` por bloco de trilha; `AxeIT` não é tocado (casos novos em `AxeContaIT` e no `LegalIT`).

## Review Focus
1. **Leitor de e-mail que pré-abre o link** (Outlook, antivírus): o link ainda funciona quando a pessoa clica. `linkSobreviveAoPreCarregamento` (C6, com a F3).
2. **"Enviar link" tocado duas vezes ou repetido pela rede móvel, inclusive ao mesmo tempo:** sai um e-mail só e o Turnstile é gasto uma vez. `repeticaoComMesmaChaveNaoDuplicaNemGastaTurnstile` e `duploToqueSimultaneoUmTurnstile` (C5, sobre a reserva da P7).
3. **Link antigo aberto depois da recusa por idade:** nunca recria nem reativa a conta. `linkAntigoDeEmailBloqueadoNaoCriaConta` (C6).
4. **Resend fora do ar no pedido:** 202 para a pessoa, e o e-mail sai quando o Resend volta, com a mesma `Idempotency-Key`. `falhaTentaDeNovoComAMesmaChave` (P8).
5. **Aniversário de 18 anos perto da meia-noite** (23h de 21/11 em São Paulo = 02h de 22/11 UTC): quem nasceu em 22/11/2008 ainda tem 17. `idadeUsaFusoDeSaoPaulo` (C7).
6. **Cookie `Secure` em `http://127.0.0.1` no WebKit:** a sessão sobrevive a uma recarga nos três navegadores. `sessaoSobreviveARecarga` (F10).
7. **Terraço sem conta:** a primeira carga do `/` não gera resposta ≥ 400 nem redireciona; as rotas de `room/` passam sem sessão. `contaSemSessaoResponde204` (C4), `TerracoAnonimoTest` (C4), `PrimeiraCargaIT` (F4, sem mudar asserções).
8. **JS inicial:** a casca de conta não entra no JS inicial do `/`; o teto de 100 KB gzip falha o build se for passado (F2).

---

## Trilha P — plataforma

Back transversal: `nucleo/`, `web/` (sem `RotasDoSpa*` e, depois da P1, sem `CabecalhosDeSeguranca*`), `notificacao/`, `retencao/`, build, banco, CI e a skill `duora-etapa`. Abreviações: `m/` = `src/main/java/bipotheking/net/duora/`, `t/` = `src/test/java/bipotheking/net/duora/`, `r/` = `src/main/resources/`.

**Regras de execução da trilha**
- Todo comando com Docker (qualquer `@SpringBootTest` a partir da P2) roda dentro da trava: `mkdir /private/tmp/claude-501/duora-pesado.lock && docker info >/dev/null && caffeinate -dims ./mvnw …; rmdir /private/tmp/claude-501/duora-pesado.lock`. Teste puro (sem Spring) roda fora dela com `./mvnw -q test -Dtest=<Classe>`.
- Portão de cada task: `./mvnw -q verify` verde, depois a skill `duora-etapa`.
- Arquivos de configuração com blocos por trilha (`# plataforma`, `# conta`, `# legal`, `# front`), criados na P2. A P só edita `# plataforma`.

### Renomeações (em relação ao plano original)
| Antigo | Novo | Motivo |
|---|---|---|
| `ErroDeNegocio.comExtra(chave, valor)` | construtor `protected ErroDeNegocio(CodigoDeErro, String, Map<String,Object> extras, @Nullable Duration tentarDeNovoEm)` + `public Optional<Duration> tentarDeNovoEm()` | exceção imutável; o `Retry-After` sai só da P1 (backend.md §6, plano:159) |
| `TipoDeErro` (13 valores) | + `METODO_NAO_PERMITIDO` (405) | toda resposta do Spring ganha `codigo` (backend.md §1 item 2) |
| `CodigoWeb` (8 valores) | + `REQUISICAO_INVALIDA`, `RECURSO_INEXISTENTE`, `METODO_NAO_PERMITIDO`, `FORMATO_NAO_SUPORTADO`, `REQUISICAO_EM_ANDAMENTO` | idem; reserva da idempotência |
| `web/LimitesConfig`, `duora.limites.<regra>.*` | `web/LimitesDeTaxa`, `duora.limites.regras.<regra>.capacidade|periodo` + `duora.limites.multiplicador` | o e2e folga os limites sem duplicar valores (backend.md §1 item 13) |
| `Idempotencia.respostaGravada` / `executar` | `reservar` / `concluir` / `liberar` | reserva antes do Turnstile (backend.md §6, plano:401) |
| `Outbox.registrar(tipo, carga)` | `registrar(tipo, titular, carga)` + `esquecerTitular(titular)` | D-1: exclusão zera as mensagens pendentes do titular sem ciclo `notificacao → conta` |
| `src/test/resources/application-test.properties` + perfil `test` | `src/test/resources/config/application.properties`, sem perfil | funciona no Maven e na IDE (backend.md §6, plano:192) |
| `IdDeCorrelacao` usa `"correlationId"` literal | constante `nucleo.Correlacao.CHAVE_MDC` | `retencao` e `notificacao` usam a chave sem depender de `web` (evita o ciclo `web ↔ retencao`) |
| `@Scheduled` no `DespachanteDaOutbox` e no `ExpiracaoDeDados` | beans `AgendamentoDaOutbox` e `AgendamentoDaRetencao` com `@ConditionalOnProperty("duora.agendamentos.habilitados")` | agendador desligado nos testes (backend.md §6, plano:251) |
| ADR "próximo livre" da retenção | `docs/adr/0006-retencao-por-politica.md` | decisoes.md |

---

### Task P1: Núcleo de erros, filtros e MIME em `web/`, `BaseConfig`

Dois commits: refatoração pura, depois o comportamento novo (`mensagem` e códigos do Spring).

**Files:**
- Create: `m/nucleo/{TipoDeErro,CodigoDeErro,ErroDeNegocio}.java`, `m/web/{BaseConfig,CodigoWeb}.java`
- Move (`git mv` para `m/web/`): `m/room/{TratamentoDeErros,RespostaDeProblema,MesmaOrigem,HostLocal,CabecalhosDeSeguranca,EnderecoLocal,BundlesComHash,TipoDoManifest}.java`
- Modify: `m/room/{ErroApi,Codigo,SalasConfig,SalaController}.java`
- Move: `t/room/RelogioDeTeste.java` → `t/web/RelogioDeTeste.java` (público); imports em `t/room/{SalasTest,SalaApiTest,CenarioApiTest,ConcorrenciaTest}.java`
- Test: `t/web/{TratamentoDeErrosTest,ControllerDeTesteDeErros}.java` (rotas em `/teste/erros/**`)
- Convenção para toda a trilha P: controllers de teste da plataforma (`ControllerDeTesteDe*` em `t/web/`) mapeiam rotas em `/teste/**`, fora de `/api`, para não cair no `ExigeSessao` da C4 (que nega toda `/api/**` fora da allowlist).

**Interfaces:**
- Consumes: —
- Produces (nomes estáveis que C e F consomem):
  - `public enum TipoDeErro { ENTRADA_INVALIDA, NAO_AUTENTICADO, PROIBIDO, NAO_ENCONTRADO, METODO_NAO_PERMITIDO, CONFLITO, ENCERRADO, GRANDE_DEMAIS, FORMATO_NAO_SUPORTADO, ENDERECO_RECUSADO, REGRA_DE_NEGOCIO, LIMITE, INTERNO, INDISPONIVEL }` → 400, 401, 403, 404, 405, 409, 410, 413, 415, 421, 422, 429, 500, 503.
  - `public interface CodigoDeErro { String name(); TipoDeErro tipo(); String titulo(); }`
  - `public class ErroDeNegocio extends RuntimeException`: `public ErroDeNegocio(CodigoDeErro codigo, String mensagem)`; `protected ErroDeNegocio(CodigoDeErro codigo, String mensagem, Map<String, Object> extras, @Nullable Duration tentarDeNovoEm)`; `public CodigoDeErro codigo()`; `public Map<String, Object> extras()` (cópia imutável); `public Optional<Duration> tentarDeNovoEm()`. `super(mensagem, null, false, false)` (sem stack trace). Campos `transient` e `private static final long serialVersionUID`, como o `ErroApi` atual (senão `[serial]` quebra o `-Xlint:all -Werror`); vale para toda subclasse.
  - `public enum CodigoWeb implements CodigoDeErro`: `ORIGEM_RECUSADA` 403, `HOST_RECUSADO` 421, `CORPO_INVALIDO` 400, `REQUISICAO_INVALIDA` 400, `RECURSO_INEXISTENTE` 404, `METODO_NAO_PERMITIDO` 405, `FORMATO_NAO_SUPORTADO` 415, `NAO_AUTENTICADO` 401, `LIMITE_DE_TAXA` 429, `CHAVE_DE_IDEMPOTENCIA_INVALIDA` 400, `CHAVE_DE_IDEMPOTENCIA_REUSADA` 409, `REQUISICAO_EM_ANDAMENTO` 409, `ERRO_INTERNO` 500.
  - Tradutor HTTP `web.TratamentoDeErros` (`@RestControllerAdvice`, único que conhece status): `static HttpStatus status(TipoDeErro tipo)` (switch exaustivo); corpo `application/problem+json` com `type`, `title`, `status`, `detail`, `codigo`, `mensagem` (= `detail`) e cada extra como campo; `tentarDeNovoEm` → `Retry-After` em segundos arredondados para cima. Erro lançado em interceptor ou controller passa por ele.
  - `public final class RespostaDeProblema`: `public static void escrever(HttpServletResponse response, JsonMapper json, CodigoDeErro codigo, String mensagem)` (filtros; também escreve `mensagem`).
  - `web.BaseConfig` (`@Configuration @EnableScheduling`): `@Bean Clock relogio()` = `Clock.systemUTC()`.
  - `public final class CabecalhosDeSeguranca` com `public static final String CSP` (passa à trilha F depois do merge).
  - `public class MesmaOrigem` com `public static final String CABECALHO` e `public static final String SEC_FETCH_SITE` (o `SalaApiTest` e o `CenarioApiTest` usam).
  - Ordem dos filtros: `CabecalhosDeSeguranca` = `HIGHEST_PRECEDENCE + 1`, `HostLocal` + 2, `MesmaOrigem` + 3 (o `HIGHEST_PRECEDENCE` fica livre para a P4; a F não precisa mexer).
  - `t/web/RelogioDeTeste` público: `public RelogioDeTeste(Instant inicio)`, `public void avancar(Duration)`.

- [ ] **Step 1: Refatoração (commit 1).** `git mv` dos 8 arquivos; `Codigo implements CodigoDeErro` (status → `TipoDeErro`; `SALA_ENCERRADA` → `ENCERRADO`, `LIMITE_SALAS` → `INDISPONIVEL`, `HOST_RECUSADO` sai, assim como `ORIGEM_RECUSADA`, `CORPO_INVALIDO` e `ERRO_INTERNO`, que vão para `CodigoWeb`); `ErroApi extends ErroDeNegocio` com `@Override public Codigo codigo()` (covariante), `visao` e `motivo.name()` viram extras; `SalaController:164` lança `new ErroDeNegocio(CodigoWeb.CORPO_INVALIDO, "Falta um campo obrigatório.")`; `SalasConfig` perde `Clock` e `@EnableScheduling`; `CabecalhosDeSeguranca` e `CSP` viram `public` (o `SalaApiTest:714,738,749` usa); `MesmaOrigem`, `CABECALHO` e `SEC_FETCH_SITE` viram `public` (o `SalaApiTest` e o `CenarioApiTest` usam); ordens +1.
- [ ] **Step 2: Run** `./mvnw -q verify`. Expected: verde sem mudar asserção (só imports). Commit:
```bash
git add src/main/java/bipotheking/net/duora/{nucleo,web,room} src/test/java/bipotheking/net/duora/{web,room}
git commit -m "refactor(web): núcleo de erros sem http e filtros no pacote web"
```
- [ ] **Step 3: Testes que falham** em `TratamentoDeErrosTest` (`@SpringBootTest` + MockMvc; rotas do `ControllerDeTesteDeErros` em `/teste/erros/**`):
  - `erroDeNegocioViraProblemaComCodigoEMensagem`: `ErroDeNegocio(CodigoWeb.NAO_AUTENTICADO, "Entre para continuar.")` → 401, `application/problem+json`, `$.codigo == "NAO_AUTENTICADO"`, `$.mensagem == $.detail == "Entre para continuar."`;
  - `extrasViramCampos` (`$.visao` presente, como no `SalaApiTest`);
  - `tentarDeNovoEmViraRetryAfter`: `Duration.ofMillis(899_200)` → `Retry-After: 900`;
  - `falhaInesperadaNaoVaza`: `IllegalStateException("CANARY-<uuid>")` → 500 `ERRO_INTERNO`, corpo sem o canário, `Exception`, `at ` nem `bipotheking`;
  - `formatoNaoSuportadoTemCodigo`: `POST` JSON com `text/plain` → 415 `FORMATO_NAO_SUPORTADO`, `mensagem` não vazia;
  - `metodoNaoPermitidoTemCodigo` → 405 `METODO_NAO_PERMITIDO` com cabeçalho `Allow`;
  - `cabecalhoAusenteTemCodigo` (`@RequestHeader` obrigatório) → 400 `REQUISICAO_INVALIDA`;
  - `filtroDeOrigemEscreveMensagem`: `POST /api/salas` sem `X-Duora` → 403 `ORIGEM_RECUSADA` com `mensagem`;
  - `@ParameterizedTest cadaTipoTemSeuStatus` (`@CsvSource` com os 14 pares acima).
- [ ] **Step 4: Run** `./mvnw -q test -Dtest=TratamentoDeErrosTest`. Expected: FAIL (`$.mensagem` ausente; 415 sem `codigo`).
- [ ] **Step 5: Implementar** `handleExceptionInternal(Exception, @Nullable Object, HttpHeaders, HttpStatusCode, WebRequest)` sobrescrito: mantém status e cabeçalhos do Spring e escolhe o código pelo status (404 → `RECURSO_INEXISTENTE`, 405 → `METODO_NAO_PERMITIDO`, 415 → `FORMATO_NAO_SUPORTADO`, outro 4xx → `REQUISICAO_INVALIDA`, 5xx → `ERRO_INTERNO`), com mensagem pt-BR fixa por código. `@ExceptionHandler(ErroDeNegocio.class)` genérico (sem conhecer `room`).
- [ ] **Step 6: Run** `./mvnw -q verify`. Expected: verde. Commit `feat(web): mensagem e código em toda resposta de erro, com Retry-After`.

### Task P2: Dependências, PostgreSQL 18, Flyway, Testcontainers, compose, CI e skill

**Files:**
- Modify: `pom.xml`, `r/application.properties`, `.github/workflows/ci.yml`, `.claude/skills/duora-etapa/SKILL.md`, `README.md` (seção "Banco local")
- Create: `compose.yaml`, `r/db/migration/V1__fundacao.sql` (só comentário), `r/application-prod.properties`, `src/test/resources/config/application.properties`, `docs/adr/0001-monolito-modular-e-postgres.md`
- Test: `t/web/{BancoDeTeste,RelogioDeTesteConfig,BancoTest,ConfiguracaoDeProdTest}.java`; `t/web/RelogioDeTeste.java` (ganha `ajustar`)
- Fora do escopo da P2: `duora.sessao.cookie-seguro=false` no WebKit (D-F4) fica no `@DynamicPropertySource` da base de e2e da F, que lê `Navegador.NOME` e funciona também pela IDE; o `pom.xml` não ganha essa propriedade.

**Interfaces:**
- Consumes: P1 (`web/`, `RelogioDeTeste`).
- Produces:
  - `pom.xml` com **todas** as deps de back da 2a: `spring-boot-starter-data-jdbc`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `spring-boot-docker-compose` (optional), `spring-boot-starter-actuator`, `spring-boot-starter-restclient`, `com.bucket4j:bucket4j_jdk17-core` `${bucket4j.version}=8.22.0`, `com.github.ben-manes.caffeine:caffeine`, `org.commonmark:{commonmark,commonmark-ext-gfm-tables,commonmark-ext-yaml-front-matter}` `${commonmark.version}=0.32.0`; teste: `spring-boot-starter-restclient-test`, `org.testcontainers:testcontainers-postgresql`, `com.tngtech.archunit:archunit-junit5` `${archunit.version}=1.5.1`. O AWS SDK fica para a 2b.
  - Failsafe (`e2e`) ganha em `systemPropertyVariables`: `duora.limites.multiplicador=1000`, `duora.agendamentos.habilitados=true`, `duora.outbox.intervalo=PT0.2S`. O teste de rajada sobrescreve com `duora.limites.multiplicador=1` (propriedade de teste vence propriedade de sistema). O failsafe também ganha `<exclude>**/TurnstileChavesDeTesteIT.java</exclude>` ao lado do `VisualIT` (rede real: roda só com `-Dit.test=TurnstileChavesDeTesteIT`; o e2e e a CI não dependem de rede).
  - Config de teste (`classpath:/config/`, carregada além da principal, no Maven e na IDE): `spring.datasource.url=jdbc:tc:postgresql:18:///duora?TC_DAEMON=true`, `spring.datasource.hikari.maximum-pool-size=4`, `minimum-idle=0`, `idle-timeout=10s`, `duora.agendamentos.habilitados=false`.
  - `public abstract class BancoDeTeste` (`@SpringBootTest @AutoConfigureMockMvc @Import(RelogioDeTesteConfig)`): `public static final Instant INICIO = Instant.parse("2026-11-22T12:00:00Z")`; `protected MockMvc mvc`, `protected JdbcClient jdbc`, `protected RelogioDeTeste relogio` (`@Primary`); `@BeforeEach protected void limpar()` faz `TRUNCATE … CASCADE` em todas as tabelas de `public` menos `flyway_schema_history` e `relogio.ajustar(INICIO)`. Convenção: teste com banco estende `BancoDeTeste` sem `properties=` nem `@TestConfiguration` próprios (um contexto, pool pequeno).
  - `RelogioDeTeste.ajustar(Instant instante)`.

- [ ] **Step 1: Testes que falham.**
  - `BancoTest extends BancoDeTeste`: `versaoEPostgres18` (`select version()` começa com `PostgreSQL 18`); `flywayAplicouV1` (`flyway_schema_history` versão `1`, `success = true`); `healthResponde` (`GET /actuator/health` → 200) e `envNaoExposto` (`GET /actuator/env` → 404); `configDeTesteCarregada` (`HikariDataSource.getMaximumPoolSize() == 4`); `transacaoOciosaExpira` (`show idle_in_transaction_session_timeout` = `30s`); `limparEsvaziaTabelasMenosHistorico` (cria `tabela_teste`, insere, chama `limpar()`, conta 0, histórico do Flyway intacto, `drop`).
  - `ConfiguracaoDeProdTest` (puro, lê `r/application-prod.properties`): `segredosSemPadrao` (todo `${…}` sem `:`); `composeDesligado` (`spring.docker.compose.enabled=false`).
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='BancoTest,ConfiguracaoDeProdTest'`. Expected: FAIL (sem datasource; arquivo de prod ausente).
- [ ] **Step 3: Implementar.**
  - `compose.yaml`: serviço `postgres`, `image: postgres:18`, `POSTGRES_DB/USER/PASSWORD=duora`, `ports: ["127.0.0.1::5432"]` (porta efêmera: worktrees não colidem), volume `duora-pg:/var/lib/postgresql` (o PGDATA da 18 mudou). Ciclo padrão `start-and-stop` (sem `start-only`).
  - `application.properties` `# plataforma`: `spring.flyway.out-of-order=false`, `management.endpoints.web.exposure.include=health`, `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.connection-init-sql=SET idle_in_transaction_session_timeout = '30s'`. Sem URL de banco: o compose fornece a conexão em dev. Blocos vazios `# conta`, `# legal`, `# front`. Atenção: o arquivo passa por *resource filtering* com `@`; nunca dois `@` na mesma linha.
  - `application-prod.properties` `# plataforma`: `spring.docker.compose.enabled=false`, `spring.datasource.url=${DUORA_DB_URL}`, `username=${DUORA_DB_USUARIO}`, `password=${DUORA_DB_SENHA}`; os blocos das outras trilhas vazios.
  - ADR 0001: contexto, decisão (um deployable, módulos com API pública travada pela ArchUnit, Postgres 18 + Flyway, Spring Data JDBC/`JdbcClient` sem JPA, Testcontainers, outbox no lugar de broker), consequências (Docker obrigatório para `verify` e `spring-boot:run`).
  - CI: `verify` com `timeout-minutes: 20`; nos dois jobs, passo `docker pull postgres:18` antes do Maven; sem `services:`.
  - Skill `duora-etapa`: item 1 com `docker info` respondendo, trava `mkdir …/duora-pesado.lock` + `caffeinate -dims` (uma suíte com Docker por vez) e liberação no fim; limpeza: `docker ps --filter label=org.testcontainers -q` vazio depois dos testes e `docker compose ps -q` vazio depois de `spring-boot:run`; migração nova única, maior que a maior da `main` e dentro de V1–V19 (`ls src/main/resources/db/migration`); item 4 ganha `AKIA[0-9A-Z]{16}` e `re_[A-Za-z0-9_]{16,}` no segundo grep.
  - README "Banco local": Docker Desktop aberto para `verify`, e2e e `spring-boot:run`.
- [ ] **Step 4: Run** `./mvnw -q verify`, depois `./mvnw -q -Pe2e verify -Dnavegador=chromium`, `firefox` e `webkit`, um de cada vez. Expected: verde; os 26 `@SpringBootTest` atuais (5 no `verify`, 21 no e2e) sobem com o contêiner. Conferir no log do primeiro `./mvnw spring-boot:run` que o Flyway não avisa "PostgreSQL 18 is newer than this version" (se avisar, fixar `flyway.version` na menor 11.x com suporte ao 18) e que `./mvnw -q dependency:tree -Dincludes=org.testcontainers` mostra ≥ 2.0.2 (Docker Engine 29). Atualizar `.claude/handoff/_mapa.md` (stack, Docker, pacotes). Commit `build: postgres 18, flyway, testcontainers e compose; adr 0001`.

### Task P3: `GeradorDeIds` (UUID v7), aleatório e resumos

**Files:**
- Create: `m/nucleo/{GeradorDeIds,Resumos}.java`
- Modify: `m/web/BaseConfig.java`
- Test: `t/nucleo/{GeradorDeIdsTest,ResumosTest}.java`

**Interfaces:**
- Consumes: P1 (`BaseConfig`, `RelogioDeTeste`).
- Produces:
  - `public final class GeradorDeIds`: `public GeradorDeIds(Clock relogio, RandomGenerator aleatorio)`; `public synchronized UUID novo()`. Bean em `BaseConfig`.
  - `@Bean RandomGenerator aleatorio()` = `new SecureRandom()` em `BaseConfig` (consumido pelo `Backoff`, pelo `Token` da C3 e por jitter em geral).
  - `public final class Resumos`: `public static byte[] sha256(String texto)` (UTF-8); `public static String sha256Hex(String texto)` (minúsculo). Funções puras (C3 usa no token, P7 na impressão).

- [ ] **Step 1: Testes que falham** (relógio `RelogioDeTeste` em `2026-11-22T12:00:00Z`):
  - `versaoEVariante`: `version() == 7`, `variant() == 2`;
  - `milissegundosNosBitsAltos`: `id.getMostSignificantBits() >>> 16 == 1795348800000L`;
  - `mesmoMilissegundoEstritamenteCrescente` (comparação sem sinal de msb e lsb);
  - `estouroDaSequenciaAvancaOMilissegundo`: relógio parado, o 4097º id tem milissegundo `1795348800001`; 10 000 ids distintos e crescentes;
  - `relogioQueVoltaNaoQuebraAOrdem`: depois de `ajustar(INICIO.minusSeconds(5))`, o próximo id é maior que o anterior;
  - `mesmaSementeMesmosIds` (dois geradores com `new SplittableRandom(42)` como `RandomGenerator`);
  - `ResumosTest`: `sha256Hex("abc")` = `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='GeradorDeIdsTest,ResumosTest'`. Expected: FAIL (classes inexistentes).
- [ ] **Step 3: Implementar** RFC 9562 §6.2 método 1: estado `ultimoMs`/`sequencia` na instância; `ms = max(relogio.millis(), ultimoMs)`; se `ms == ultimoMs`, `sequencia++` e, passando de `0xFFF`, `ms++` e `sequencia = 0`; senão `sequencia = 0`. `msb = ms << 16 | 0x7000 | sequencia`; `lsb = 62 bits de aleatorio.nextLong() | 0x8000_0000_0000_0000L` (variante `10`).
- [ ] **Step 4: Run** o mesmo comando. Expected: PASS. Depois `./mvnw -q verify` (trava).
- [ ] **Step 5: Commit** `feat(nucleo): ids uuid v7 monotônicos e resumos sha-256`.

### Task P4: Correlation ID e logs

**Files:**
- Create: `m/nucleo/Correlacao.java`, `m/web/IdDeCorrelacao.java`
- Modify: `r/application-prod.properties` (`# plataforma`: `logging.structured.format.console=ecs`)
- Test: `t/web/{IdDeCorrelacaoTest,FormatoDoLogTest,ControllerDeTesteDeLog}.java` (rota `/teste/log`, convenção da P1)

**Interfaces:**
- Consumes: P1 (ordem dos filtros), P3 (`GeradorDeIds`).
- Produces:
  - `public final class Correlacao { public static final String CHAVE_MDC = "correlationId"; }`
  - `IdDeCorrelacao` (`@Order(Ordered.HIGHEST_PRECEDENCE)`): `public static final String CABECALHO = "X-Request-Id"`; aceita valor `^[A-Za-z0-9-]{8,64}$` ou gera `GeradorDeIds.novo()`; põe em `MDC[Correlacao.CHAVE_MDC]`, devolve no cabeçalho e faz `MDC.remove` no `finally`.
  - Log em texto em dev; ECS (JSON) só em `prod` e no `FormatoDoLogTest` (D-4).

- [ ] **Step 1: Testes que falham.** O `ControllerDeTesteDeLog` loga `INFO "pedido-de-teste-correlacao"`.
  - `IdDeCorrelacaoTest extends BancoDeTeste`: `devolveOValorRecebido` (`X-Request-Id: teste-1234-abcd`); `semCabecalhoGeraUuidV7` (cabeçalho casa `^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`); `valorInvalidoETrocado` (`<script>` → id gerado); `presenteNaRecusaDeHost` (`Host: exemplo.com` → 421 com `X-Request-Id`, prova a precedência); `mdcLimpoDepois` (`MDC.get("correlationId")` nulo depois da chamada).
  - `FormatoDoLogTest` (`@SpringBootTest(properties = "logging.structured.format.console=ecs")`, `OutputCaptureExtension`): a linha com `pedido-de-teste-correlacao` é JSON e contém `"correlationId":"teste-1234-abcd"`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='IdDeCorrelacaoTest,FormatoDoLogTest'`. Expected: FAIL (sem cabeçalho).
- [ ] **Step 3: Implementar** `IdDeCorrelacao extends OncePerRequestFilter`.
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): correlation id por requisição e log ecs em produção`.

### Task P5: `ArquiteturaTest` (ArchUnit)

**Files:**
- Test: `t/ArquiteturaTest.java`, `t/arquitetura/RegrasDeArquitetura.java`, `t/arquitetura/RegrasDeArquiteturaTest.java`, exemplos em `t/arquitetura/exemplos/{moduloa/UsaInterno,modulob/infra/Interno,moduloc/dominio/UsaJdbc,moduloc/dominio/UsaHttp}.java`

**Interfaces:**
- Consumes: P1 (`nucleo/`, `web/`), P2 (dep ArchUnit).
- Produces: regras que valem para C e para as próximas etapas, rodando no `verify` sobre classes de produção (`@AnalyzeClasses(packages = "bipotheking.net.duora", importOptions = ImportOption.DoNotIncludeTests.class)`):
  - `semCiclosEntreModulos`: `slices().matching("bipotheking.net.duora.(*)..").should().beFreeOfCycles()`;
  - `moduloSoUsaAApiPublicaDeOutro`: classe de `duora.A..` só depende, em `duora.B` (B ≠ A), do pacote raiz de B (nunca `B.dominio`, `B.web`, `B.infra`);
  - `dominioSemWebNemJdbc`: classes fora de `..web..`, `..infra..` e `..room..` (legado até a Etapa 5, com comentário) não dependem de `org.springframework.web..`, `org.springframework.http..`, `org.springframework.jdbc..`, `org.springframework.data..`, `jakarta.servlet..`, `tools.jackson..`, `com.fasterxml.jackson..`;
  - `nucleoSemSpring`: `nucleo..` não depende de `org.springframework..`.
  - `RegrasDeArquitetura`: `static ArchRule apiPublica(String raiz)`, `static ArchRule dominioSemWebNemJdbc(String raiz)` (o `ArquiteturaTest` usa com a raiz real).

- [ ] **Step 1: Teste que falha** `RegrasDeArquiteturaTest` (importa só `…arquitetura.exemplos`): `apiPublicaMorde` (`UsaInterno` → `modulob.infra.Interno` viola); `dominioSemJdbcMorde` (`UsaJdbc` com `JdbcTemplate` viola); `dominioSemHttpMorde` (`UsaHttp` com `HttpStatus` viola).
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=RegrasDeArquiteturaTest`. Expected: FAIL (`RegrasDeArquitetura` inexistente).
- [ ] **Step 3: Implementar** as regras com `ArchCondition` próprio para a API pública (compara o segundo segmento depois da raiz).
- [ ] **Step 4: Run** `./mvnw -q test -Dtest='RegrasDeArquiteturaTest,ArquiteturaTest'`. Expected: PASS no código atual; se o `ArquiteturaTest` apontar algo, corrigir antes em commit `refactor:` separado. Prova que a versão lê bytecode 69 (Java 25).
- [ ] **Step 5: Commit** `test(arquitetura): fitness functions de ciclos, api pública e domínio`.

### Task P6: `OrigemDaRequisicao` e `LimiteDeTaxa`

**Files:**
- Create: `m/nucleo/Origem.java`, `m/web/{OrigemDaRequisicao,LimiteDeTaxa,LimitesDeTaxa,LimiteExcedido,RelogioDoBucket}.java`
- Modify: `src/test/resources/config/application.properties` (`# plataforma`: regra `teste`)
- Test: `t/web/{OrigemDaRequisicaoTest,LimiteDeTaxaTest,LimiteDeTaxaApiTest,ControllerDeTesteDeLimite}.java` (rota `/teste/limite`, convenção da P1)

**Interfaces:**
- Consumes: P1 (`ErroDeNegocio`, `CodigoWeb.LIMITE_DE_TAXA`, `Clock`), P2 (`BancoDeTeste`, deps).
- Produces:
  - `public record Origem(String ip, int porta)` (`nucleo`, puro: a raiz de `conta` usa sem servlet).
  - `@Component public class OrigemDaRequisicao`: `public Origem de(HttpServletRequest request)` = `remoteAddr`/`remotePort` (D-2; a Etapa 10 troca só esta classe por `CF-Connecting-IP` das faixas da Cloudflare). Consumida por `LimiteDeTaxa` (pelos controllers), `Entrada` e `RegistroDeAcessos` (C).
  - `@ConfigurationProperties("duora.limites") public record LimitesDeTaxa(Map<String, Regra> regras, @DefaultValue("1") int multiplicador)`, `public record Regra(int capacidade, Duration periodo)`. Os valores das regras de conta (`entrar-ip` 20/`PT10M`, `entrar-email` 3/`PT15M`, `confirmar-ip` 30/`PT10M`, `cadastro-conta` 10/`PT10M`, `denuncia-publica-ip` 5/`PT1H`) ficam no bloco `# conta`, escritos pela C.
  - `@Component public class LimiteDeTaxa`: `public void consumir(String regra, String chave)`; regra desconhecida → `IllegalStateException`. Estado em memória por contexto: testes de API variam o IP com `.with(r -> { r.setRemoteAddr("10.0.0.7"); return r; })`.
  - `public final class LimiteExcedido extends ErroDeNegocio` (`LIMITE_DE_TAXA`, mensagem `Muitas tentativas. Tente de novo em alguns minutos.`, `tentarDeNovoEm` preenchido, `serialVersionUID` como na P1): `public long segundosParaTentarDeNovo()`.

- [ ] **Step 1: Testes que falham.**
  - `OrigemDaRequisicaoTest` (puro, `MockHttpServletRequest`): `usaEnderecoEPortaRemotos` (`203.0.113.7`, `51234`); `ignoraXForwardedFor`; `ignoraCfConnectingIp`.
  - `LimiteDeTaxaTest` (puro, `RelogioDeTeste`, regra 3/`PT15M`): `quartoPedidoExcede` com `segundosParaTentarDeNovo() == 900`; `recargaDepoisDoPeriodo` (`avancar(PT15M)` libera); `chavesERegrasIsoladas`; `multiplicadorAumentaCapacidade` (multiplicador 2 → o 7º excede); `regraDesconhecidaFalha`.
  - `LimiteDeTaxaApiTest extends BancoDeTeste` (regra `teste` 3/`PT15M` na config de teste): a 4ª chamada → 429, `$.codigo == "LIMITE_DE_TAXA"`, `Retry-After: 900`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='OrigemDaRequisicaoTest,LimiteDeTaxaTest,LimiteDeTaxaApiTest'`. Expected: FAIL.
- [ ] **Step 3: Implementar.** Buckets num Caffeine com chave `regra:chave`, `expireAfterAccess` = período da regra, `maximumSize(100_000)` e `ticker` sobre o `Clock`; `Bucket.builder().addLimit(l -> l.capacity(c * m).refillIntervally(c * m, periodo)).withCustomTimePrecision(new RelogioDoBucket(clock))`; `RelogioDoBucket implements TimeMeter` (`currentTimeNanos()` do `Clock`, `isWallClockBased() = true`); espera por `tryConsumeAndReturnRemaining(1).getNanosToWaitForRefill()` arredondada para cima.
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): origem da requisição e limite de taxa em memória`.

### Task P7: Idempotência com reserva antes do efeito (V2)

**Files:**
- Create: `r/db/migration/V2__idempotencia.sql`, `m/web/Idempotencia.java`
- Modify: `r/application.properties` (`# plataforma`: `duora.idempotencia.reserva-maxima=PT30S`)
- Test: `t/web/IdempotenciaTest.java`

**Esquema:** `chave_de_idempotencia(escopo text not null, chave text not null check (chave ~ '^[A-Za-z0-9-]{8,64}$'), impressao text not null, resposta jsonb, criada_em timestamptz not null, reservada_em timestamptz not null, primary key (escopo, chave))` + índice em `criada_em`.

**Interfaces:**
- Consumes: P1 (`CodigoWeb`, `Clock`), P2 (`BancoDeTeste`), P3 (`Resumos`).
- Produces (`@Component public class Idempotencia`):
  - `@Transactional(propagation = REQUIRES_NEW) public <T> Optional<T> reservar(String escopo, String chave, String impressao, Class<T> tipo)`: `INSERT … ON CONFLICT DO NOTHING` com `resposta` nula. Vazio = a chave é sua, siga. Presente = resposta já gravada, devolva sem efeito. Impressão diferente → `CHAVE_DE_IDEMPOTENCIA_REUSADA` (409); reserva aberta há ≤ 30 s → `REQUISICAO_EM_ANDAMENTO` (409, `tentarDeNovoEm` 1 s); reserva aberta há > 30 s → retomada (`UPDATE … SET reservada_em = :agora WHERE resposta IS NULL AND reservada_em < :agora - 30 s`); formato inválido → `CHAVE_DE_IDEMPOTENCIA_INVALIDA` (400).
  - `@Transactional(propagation = MANDATORY) public void concluir(String escopo, String chave, Object resposta)`: grava a resposta na transação do efeito (link + outbox, na C5).
  - `@Transactional(propagation = REQUIRES_NEW) public void liberar(String escopo, String chave)`: apaga a reserva sem resposta (o efeito falhou, por exemplo Turnstile recusado).
  - Uso esperado na C5: `reservar` → Turnstile fora de transação → transação curta do efeito com `concluir`; em erro, `liberar` e relança. Escopo fixo por caso de uso (`entrar`); impressão = `Resumos.sha256Hex` do e-mail normalizado (o token do Turnstile muda a cada tentativa e fica fora da impressão).

- [ ] **Step 1: Testes que falham** (`IdempotenciaTest extends BancoDeTeste`):
  - `primeiraReservaEVazia`; `concluidaDevolveRespostaSemEfeito` (reservar → concluir em transação → reservar de novo devolve a resposta igual);
  - `mesmaChaveImpressaoDiferenteRecusada` (409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`); `escoposDiferentesNaoColidem`; `chaveMalFormadaRecusada` (`"abc"`, `"x y z 123"`);
  - `reservasSimultaneasSoUmaGanha` (duas threads com `CountDownLatch`: uma recebe vazio, a outra `REQUISICAO_EM_ANDAMENTO` com `tentarDeNovoEm == 1 s`);
  - `reservaNoLimiteSegueEmAndamento` (`avancar(PT30S)` → 409) e `reservaVencidaERetomada` (`PT30S` + 1 s → vazio);
  - `liberarPermiteNovaReserva`; `liberarNaoApagaConcluida`;
  - `concluirSemTransacaoFalha` (`IllegalTransactionStateException`); `rollbackDoEfeitoNaoGravaResposta`.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest=IdempotenciaTest`. Expected: FAIL (tabela e classe inexistentes).
- [ ] **Step 3: Implementar** com `JdbcClient` e `JsonMapper` para a resposta.
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): idempotência por chave com reserva antes do efeito`.

### Task P8: Outbox transacional e despachante (V3)

**Files:**
- Create: `r/db/migration/V3__outbox.sql`, `m/notificacao/{Outbox,ProcessadorDeOutbox}.java`, `m/notificacao/infra/{OutboxJdbc,DespachanteDaOutbox,AgendamentoDaOutbox,Backoff}.java`
- Modify: `r/application.properties` (`# plataforma`: `duora.agendamentos.habilitados=true`, `duora.outbox.intervalo=PT2S`)
- Test: `t/notificacao/infra/{OutboxJdbcTest,DespachanteDaOutboxTest,BackoffTest,ProcessadorFalso}.java`

**Esquema:** `outbox(id uuid primary key, tipo text not null, titular text not null, carga jsonb, correlation_id text, criada_em timestamptz not null, proxima_tentativa_em timestamptz not null, reservada_ate timestamptz, tentativas int not null default 0 check (tentativas >= 0), enviada_em timestamptz, falhou_em timestamptz, ultimo_erro text, check ((carga is not null) = (enviada_em is null and falhou_em is null)))` + índice parcial `(proxima_tentativa_em) where carga is not null` + índice `(titular) where carga is not null`. A carga só existe enquanto pendente (D-1).

**Interfaces:**
- Consumes: P1 (`Clock`), P2 (`BancoDeTeste`, actuator), P3 (`GeradorDeIds`, `RandomGenerator`), P4 (`Correlacao.CHAVE_MDC`).
- Produces:
  - `public interface Outbox { String EMAIL = "email"; <T> void registrar(String tipo, String titular, T carga); int esquecerTitular(String titular); }`. `registrar` exige transação (`MANDATORY`), gera id por `GeradorDeIds` e grava o `correlationId` do MDC. `titular` é uma chave opaca (a C usa o resumo do e-mail). `esquecerTitular` zera a carga das pendentes do titular (`falhou_em = agora`, `ultimo_erro = 'titular-esquecido'`) e devolve quantas; a C8 chama na exclusão.
  - `public interface ProcessadorDeOutbox<T> { String tipo(); Class<T> tipoDaCarga(); void processar(T carga, UUID idDaMensagem); }` (retorno normal = enviada; exceção = falha).
  - `public class DespachanteDaOutbox` (pública: testes de `t/conta` e do e2e a chamam): `public int despacharAgora()` (devolve quantas foram enviadas); gauges `duora.outbox.pendentes` e `duora.outbox.falhas`. `AgendamentoDaOutbox` (`@ConditionalOnProperty(name = "duora.agendamentos.habilitados", havingValue = "true", matchIfMissing = true)`) chama `despacharAgora()` em `@Scheduled(fixedDelayString = "${duora.outbox.intervalo}")`.
  - `Backoff`: `public Duration proxima(int tentativas)` = `min(2 s × 2^tentativas × fator, 15 min)`, `fator = 0,8 + 0,4 × aleatorio.nextDouble()` (jitter antes do teto).

- [ ] **Step 0:** `mattpocock-skills:codebase-design` com duas alternativas para `Outbox`/`ProcessadorDeOutbox`; se a interface mudar, atualizar este bloco antes de codar.
- [ ] **Step 1: Testes que falham.** O despachante é montado no teste com `JdbcClient`, `TransactionTemplate`, `RelogioDeTeste`, `ProcessadorFalso` e `SimpleMeterRegistry` (sem contexto novo).
  - `OutboxJdbcTest extends BancoDeTeste`: `registrarSemTransacaoFalha`; `rollbackDescartaAMensagem`; `gravaCorrelationIdDoMdc`; `esquecerTitularZeraSoAsPendentesDele` (devolve 1; a enviada e a de outro titular ficam).
  - `DespachanteDaOutboxTest extends BancoDeTeste`: `enviaMarcaEApagaACarga` (`enviada_em = INICIO`, `carga` nula); `falhaTentaDeNovoComAMesmaChave` (o falso falha uma vez; `tentativas = 1`; antes do backoff nada roda; depois de `avancar`, a 2ª tentativa usa o mesmo `UUID` e marca enviada); `decimaFalhaMarcaFalhaSemLogarACarga` (`falhou_em` preenchido, carga nula, log ERROR com id e tipo, sem o canário da carga); `tipoSemProcessadorFalhaDeVez`; `loteDeCinco` (7 pendentes → `despacharAgora() == 5`); `reservaImpedeEnvioDuplo` (duas threads → cada mensagem processada uma vez); `reservaVencidaVoltaAFila` (`reservada_ate == agora` não é retomada; `agora + 1 s` depois, é); `correlationIdDaMensagemNoMdc`; `gaugesRefletemFila` (2 pendentes, 1 falha); `agendamentoDesligadoNoTeste` (contexto sem bean `AgendamentoDaOutbox`).
  - `BackoffTest` (puro, `RandomGenerator` falso com `nextDouble` fixo): 0,5 (fator 1,0) → tentativas 0, 1, 5 e 20 dão 2 s, 4 s, 64 s e 15 min; 0,0 (fator 0,8) → tentativa 0 = 1,6 s e tentativa 8 = 409,6 s; 1,0 (fator 1,2) → tentativa 0 = 2,4 s e tentativa 9 = 15 min exatos.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='OutboxJdbcTest,DespachanteDaOutboxTest,BackoffTest'`. Expected: FAIL.
- [ ] **Step 3: Implementar.** Reserva numa transação curta: `UPDATE outbox SET reservada_ate = :agora + 2 min WHERE id IN (SELECT id FROM outbox WHERE carga IS NOT NULL AND proxima_tentativa_em <= :agora AND (reservada_ate IS NULL OR reservada_ate < :agora) ORDER BY id LIMIT 5 FOR UPDATE SKIP LOCKED) RETURNING …` (5 × 15 s de timeout cabem nos 2 min). Processa fora da transação, com o `correlationId` da mensagem no MDC. Falha: `tentativas + 1`, `proxima_tentativa_em = agora + backoff`, `reservada_ate = null`, `ultimo_erro` = classe + mensagem (até 200 caracteres); na 10ª, `falhou_em` e carga nula. Expoente limitado para não estourar `long`.
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notificacao): outbox transacional e despachante com backoff`.

### Task P9: `EnvioDeEmail` pelo Resend e pelo log

**Files:**
- Create: `m/nucleo/EmailMascarado.java`, `m/notificacao/{EnvioDeEmail,MensagemDeEmail}.java`, `m/notificacao/infra/{ProcessadorDeEmail,EmailResend,EmailNoLog,EmailConfig,ConfiguracaoDeEmail,FalhaNoEnvio}.java`
- Modify: `r/application.properties` e `r/application-prod.properties` (`# plataforma`)
- Test: `t/nucleo/EmailMascaradoTest.java`, `t/notificacao/infra/{EmailResendTest,EmailResendTimeoutTest,EmailConfigTest,EmailNoLogTest,ProcessadorDeEmailTest}.java`, `t/web/ServidorHttpLento.java` (helper de teste)

**Interfaces:**
- Consumes: P8 (`Outbox.EMAIL`, `ProcessadorDeOutbox`), P2 (restclient).
- Produces:
  - `public interface EnvioDeEmail { void enviar(MensagemDeEmail mensagem, UUID chaveDeIdempotencia); }`; `public record MensagemDeEmail(String para, String assunto, String texto, String html)`. A C grava com `outbox.registrar(Outbox.EMAIL, titular, new MensagemDeEmail(…))`.
  - `public final class EmailMascarado { public static String mascarar(String email); }` (`ana@exemplo.com` → `a***@exemplo.com`; sem `@` → `***`). A trilha C não cria `Email.mascarado()`; quem precisar mascarar chama `EmailMascarado.mascarar`.
  - `ProcessadorDeEmail` (`tipo() = "email"`, carga `MensagemDeEmail`) chama `EnvioDeEmail` com o id da mensagem.
  - `public final class EmailNoLog implements EnvioDeEmail`: loga o destinatário mascarado e o texto (exceção por contrato), guarda as 50 últimas; `public Optional<MensagemDeEmail> ultimaPara(String email)` (testes da C e e2e da F).
  - `@ConfigurationProperties("duora.email") record ConfiguracaoDeEmail(String provedor, String remetente, Resend resend)`, `record Resend(@Nullable String chave, @DefaultValue("PT5S") Duration conexao, @DefaultValue("PT10S") Duration leitura)`. `provedor` = `log` | `resend`. Dev: `duora.email.provedor=log`, `duora.email.remetente=Duora <entrar@meetduora.com>`; prod: `provedor=resend`, `duora.email.resend.chave=${RESEND_API_KEY}`.
  - `FalhaNoEnvio extends RuntimeException` (técnica; a mensagem nunca leva destinatário nem chave).
  - Teste (em `t/web/`): `public final class ServidorHttpLento implements AutoCloseable` (`HttpServer` do JDK em porta efêmera que dorme um `Duration` antes de responder; `public static ServidorHttpLento iniciar(Duration atraso)`, `public URI uri()`). A C2 reaproveita.

- [ ] **Step 1: Testes que falham.**
  - `EmailMascaradoTest`: os três casos acima e `a@x.io` → `a***@x.io`.
  - `EmailResendTest` (`MockRestServiceServer.bindTo(RestClient.builder())`): `POST https://api.resend.com/emails`, `Authorization: Bearer re_teste`, `Idempotency-Key` = o `UUID` passado; corpo com `from`, `to` (um item), `subject`, `text`, `html`; 500 e 422 → `FalhaNoEnvio`; `withException(new SocketTimeoutException())` → `FalhaNoEnvio`; com a chave `re_CANARY-<uuid>`, o canário não aparece na saída capturada nem na mensagem da exceção.
  - `EmailResendTimeoutTest`: `ServidorHttpLento.iniciar(PT1S)`; leitura `PT0.2S` → `FalhaNoEnvio` em menos de 1 s (prova que a fábrica recebe os timeouts).
  - `EmailConfigTest` (`ApplicationContextRunner`): `resendSemChaveFalha` (mensagem cita `duora.email.resend.chave`); `logEscolheEmailNoLog`; `prodComLogFalha` (perfil `prod` + `provedor=log` → o contexto não sobe); `provedorDesconhecidoFalha`; `timeoutsPadrao` (5 s e 10 s).
  - `EmailNoLogTest`: `logaDestinatarioMascarado` (sem `ana@exemplo.com` na linha do destinatário); `guardaAs50Ultimas` (a 51ª descarta a 1ª); `ultimaParaDevolveAMaisRecente`.
  - `ProcessadorDeEmailTest`: `tipo() == "email"`; chama o falso com o id da mensagem.
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='EmailMascaradoTest,EmailResend*Test,EmailConfigTest,EmailNoLogTest,ProcessadorDeEmailTest'`. Expected: FAIL.
- [ ] **Step 3: Implementar.** Conferir pelo Context7 o corpo do `POST /emails` do Resend e a API de timeouts do Boot 4.2 (`ClientHttpRequestFactoryBuilder` + `HttpClientSettings`). Bean por `@ConditionalOnProperty("duora.email.provedor")`; validação de `prod` + `log` no `EmailConfig`.
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notificacao): envio de e-mail pelo resend e pelo log`.

### Task P10: Núcleo de retenção (Etapa 9 T2, ADR 0006)

**Files:**
- Create: `m/retencao/{PoliticaDeRetencao,ExpiracaoDeDados,ResultadoDaExpiracao,AgendamentoDaRetencao,Lotes}.java`, `docs/adr/0006-retencao-por-politica.md`
- Modify: `r/application.properties` (`# plataforma`: `duora.retencao.cron=0 30 3 * * *`)
- Test: `t/retencao/{ExpiracaoDeDadosTest,LotesTest,PoliticaFalsa}.java` (puros), `t/retencao/AgendamentoDaRetencaoTest.java` (`BancoDeTeste`)

**Interfaces:**
- Consumes: P1 (`Clock`), P2 (actuator/Micrometer), P3 (`GeradorDeIds`), P4 (`Correlacao.CHAVE_MDC`).
- Produces (C9, C10, C11, C12 e P11 implementam):
  - `public interface PoliticaDeRetencao { String nome(); Duration prazo(); int expirar(Instant limite); long tamanho(); }` (semântica do plano da Etapa 9 T2: nome kebab-case único; apaga o que é anterior a `limite`, estrito, em lotes de até 1000 por transação até acabar, por `Lotes.apagarAteAcabar`; idempotente; `tamanho()` vira gauge).
  - `public final class Lotes { public static int apagarAteAcabar(IntSupplier lote); }` (puro): chama `lote` até ele devolver 0 e devolve a soma. Cada política só fornece o `DELETE` de um lote de até 1000 linhas, numa transação própria (`TransactionTemplate`); nenhuma repete o laço.
  - `public record ResultadoDaExpiracao(String politica, int removidos, boolean falhou)`.
  - `@Component public class ExpiracaoDeDados` (bean em `retencao/`; a P11 e a C12 o injetam): construtor `(List<PoliticaDeRetencao>, Clock, GeradorDeIds, MeterRegistry)`; nomes repetidos → `IllegalStateException`; `public synchronized List<ResultadoDaExpiracao> executar()` (ordem de nome, `limite = agora - prazo`, falha isolada com log ERROR só com nome, `correlationId` próprio no MDC); `public Map<String, Duration> prazos()`; métricas `duora.retencao.tamanho{politica}`, `duora.retencao.removidos{politica}`, `duora.retencao.falhas{politica}`.
  - `AgendamentoDaRetencao` (`@ConditionalOnProperty` igual ao da P8): `@Scheduled(cron = "${duora.retencao.cron}", zone = "America/Sao_Paulo")` chama `executar()`.

- [ ] **Step 0:** `mattpocock-skills:codebase-design` com duas alternativas para `PoliticaDeRetencao` (transversal.md §7, R1).
- [ ] **Step 1: Testes que falham** (`PoliticaFalsa`, relógio fixo em `2026-11-22T06:30:00Z`, `SimpleMeterRegistry`): `limiteEAgoraMenosOPrazo` (`P30D` recebe `2026-10-23T06:30:00Z`); `rodaEmOrdemDeNome`; `falhaDeUmaPoliticaNaoParaAsOutras` (só a do meio com `falhou = true`; log com o nome dela); `nomesRepetidosFalhamNaConstrucao`; `execucoesSimultaneasSaoSerializadas`; `prazosExpostos`; `metricasPorPolitica` (removidos, gauge e falhas); `logSoTemNomeEContagem` (canário de e-mail fora do log); `cadaExecucaoTemCorrelationIdProprio` (a política vê um UUID v7 no MDC; o MDC fica limpo depois). `LotesTest`: `somaOsLotesAteZero` (lotes 1000, 1000, 500, 0 → 2500, quatro chamadas); `primeiroLoteVazioDevolveZero` (uma chamada). `AgendamentoDaRetencaoTest extends BancoDeTeste`: `agendamentoDesligadoNoTeste` (sem bean `AgendamentoDaRetencao`; bean `ExpiracaoDeDados` presente).
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='ExpiracaoDeDadosTest,LotesTest'` (puros, fora da trava) e `./mvnw -q test -Dtest=AgendamentoDaRetencaoTest` (trava). Expected: FAIL.
- [ ] **Step 3: Implementar** e escrever o ADR 0006 (cada módulo registra o que apaga; job diário único; falha isolada; métrica por política; `retencao/` só raiz, sem web nem JDBC).
- [ ] **Step 4: Run** os mesmos comandos e `./mvnw -q verify`. Expected: PASS (o `ArquiteturaTest` cobre `retencao`).
- [ ] **Step 5: Commit** `feat(retencao): núcleo de expiração diária por política`.

### Task P11: Retenção da outbox e das chaves de idempotência

**Files:**
- Create: `m/notificacao/infra/RetencaoDaOutbox.java` (duas políticas), `m/web/RetencaoDeIdempotencia.java`
- Test: `t/notificacao/infra/RetencaoDaOutboxTest.java`, `t/web/RetencaoDeIdempotenciaTest.java`, `t/retencao/PrazosDaPlataformaTest.java`

**Interfaces:**
- Consumes: P7 (tabela `chave_de_idempotencia`), P8 (tabela `outbox`), P10 (`PoliticaDeRetencao`, `Lotes.apagarAteAcabar`, `ExpiracaoDeDados.prazos()`).
- Produces: políticas `outbox-enviada` (`P7D`, `enviada_em < limite`), `outbox-falhada` (`P30D`, `falhou_em < limite`), `chaves-de-idempotencia` (`P1D`, `criada_em < limite`). Cada uma passa a `Lotes.apagarAteAcabar` o `DELETE` de um lote de 1000: na outbox, `DELETE … WHERE id IN (SELECT id … LIMIT 1000)`; nas chaves (PK `(escopo, chave)`, sem `id`), `DELETE … WHERE (escopo, chave) IN (SELECT escopo, chave … LIMIT 1000)`. `tamanho()` conta as linhas que a política apagará um dia (enviadas, falhadas, todas as chaves).

- [ ] **Step 1: Testes que falham** (`extends BancoDeTeste`; linhas inseridas com instantes explícitos; `expirar(limite)` chamado direto):
  - `RetencaoDaOutboxTest`: `enviadaHaSeteDiasExatosFica` e `enviadaHaSeteDiasEUmSegundoSome`; `falhadaHaTrintaDiasExatosFica` e `+1 s` some; `pendenteNuncaSome`; `apagaEmLotes` (2500 enviadas vencidas → `expirar` devolve 2500); `tamanhoAntesEDepois`.
  - `RetencaoDeIdempotenciaTest`: `chaveDeVinteEQuatroHorasExatasFica`; `chaveDeVinteEQuatroHorasEUmSegundoSome`; `reservaSemRespostaTambemSome`.
  - `PrazosDaPlataformaTest`: `ExpiracaoDeDados.prazos()` contém `outbox-enviada=P7D`, `outbox-falhada=P30D`, `chaves-de-idempotencia=P1D` (a tabela da política de privacidade).
- [ ] **Step 2: Run** `./mvnw -q test -Dtest='RetencaoDaOutboxTest,RetencaoDeIdempotenciaTest,PrazosDaPlataformaTest'`. Expected: FAIL.
- [ ] **Step 3: Implementar** as três políticas como beans (`RetencaoDaOutbox` expõe dois `@Bean` de uma `@Configuration` ou duas classes internas; nomes acima).
- [ ] **Step 4: Run** o mesmo comando e `./mvnw -q verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(notificacao): retenção da outbox e das chaves de idempotência`.

---

### Dependências entre trilhas
Nenhuma task P depende de C ou F. Ordem interna: fila sequencial P1 → P2 → … → P11 (um implementador por vez na trilha).

O que as outras trilhas esperam da P na `main`:
- **P1** antes de C1, C2, L1, C4 (`CodigoWeb.NAO_AUTENTICADO`; controllers de teste da P em `/teste/**`, fora do `ExigeSessao`) e de qualquer task da F que mexa em `web/CabecalhosDeSeguranca` (F3, F5).
- **P2** antes de C1 (blocos `# conta`), de toda task com `@SpringBootTest` (C2, L1 e C3 em diante; e2e da F) e de L1 (commonmark). A F3 pode entrar antes da P2: roda sem banco, e a P2 a mantém verde no seu Step 4.
- **P5** antes de C1, L1, C3 e C4 (rodam o `ArquiteturaTest`).
- **P3** antes de C3 (`GeradorDeIds`, `RandomGenerator`, `Resumos`).
- **P6, P7, P8, P9** antes de C5; **P8** antes de C8 (`esquecerTitular`); **P6** antes de C10 e C12; **P9** antes de C12 e de F10 (`EmailNoLog.ultimaPara`).
- **P10** antes de C9, C10, C11 e C12.

### Riscos da trilha
- Docker passa a ser obrigatório no `verify`: a trava `duora-pesado.lock` serializa os `verify` das três trilhas e é o gargalo do paralelismo.
- O e2e compartilha o banco do `TC_DAEMON` entre os 21 contextos (sem `TRUNCATE`): testes da F precisam de e-mails únicos.
- Versões fixadas sem o Maven rodar no plano (ArchUnit 1.5.1, Bucket4j 8.22.0, commonmark 0.32.0, Flyway × PG 18): a P2 confere no primeiro build.
- `FormatoDoLogTest` reconfigura o Logback num contexto próprio; se vazar formato para os testes seguintes, isolá-lo com `@DirtiesContext`.
- `LimiteDeTaxa` guarda estado em memória por contexto: testes de API da C que somam pedidos do mesmo IP ficam instáveis se não variarem o `remoteAddr`.

---

## Trilha C — contas

Dona de `conta/**` (raiz, `dominio/`, `web/`, `infra/`), `src/main/resources/{legal,conta}/`, migrações V4–V10, blocos `# conta` e `# legal` de `application*.properties` e dos testes de back de conta e legais. Abreviações: `m/` = `src/main/java/bipotheking/net/duora/`, `t/` = `src/test/java/bipotheking/net/duora/`, `r/` = `src/main/resources/`, `mig/` = `r/db/migration/`.

**Regras da trilha (valem para toda task C):**
- Teste com banco estende `BancoDeTeste` (P2) **sem** `properties=` nem `@TestConfiguration` próprios (um contexto só, pool pequeno); usa o `mvc`, o `jdbc` e o `relogio` dele (relógio começa em `BancoDeTeste.INICIO` = `2026-11-22T12:00:00Z`). Dublês de teste são `@Component` em `t/conta/` (o component scan do `@SpringBootTest` os pega em todo contexto, inclusive no e2e).
- Cada regra de limite que a task usa entra no bloco `# conta` como `duora.limites.regras.<regra>.capacidade=<n>` e `duora.limites.regras.<regra>.periodo=<ISO-8601>` (formato da P6). No e2e o failsafe multiplica por 1000 (P2).
- A partir da C5 (que cria o `OrigemDeTeste`), cada teste de API usa IP e e-mail únicos (`OrigemDeTeste.ipAleatorio()`, `canary-<uuid>@exemplo.com`), porque os buckets do `LimiteDeTaxa` vivem no contexto em cache. Só os testes de rajada repetem IP ou e-mail.
- Testes com banco exigem Docker: antes, `mkdir /private/tmp/claude-501/duora-pesado.lock` (trava; `rmdir` no fim) e `caffeinate -dims`. Testes puros rodam fora da trava.
- Códigos de erro: um enum por caso de uso, na raiz de `conta/`, implementando `CodigoDeErro` (P1). Os nomes em string são o contrato com o front.
- A conta para em `AGUARDANDO_VERIFICACAO` (D-A). Nenhuma propriedade, perfil ou rota leva a conta a `ATIVA` na 2a; só `ContasDeTeste.criarAtiva()` (código de teste) grava `ativada_em`.
- Commits com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Renomeações (antigo → novo, motivo)
- `conta/CodigoConta` (um enum editado por todas as tasks) → `CodigoEmail` (C1), `CodigoVerificacaoHumana` (C2), `CodigoLegal` (L1), `CodigoSessao` (C4), `CodigoEntrada` (C6), `CodigoCadastro` (C7), `CodigoExclusao` (C8), `CodigoContaNova` (C11), `CodigoDenuncia` (C12). Motivo: posse por task (backend.md §5). As strings dos códigos não mudam.
- `conta/Entrada` + `conta/web/EntradaController` + `EntradaApiTest` → `PedidoDeEntrada` + `PedidoDeEntradaController` + `PedidoDeEntradaApiTest` (C5) e `ConfirmacaoDeEntrada` + `ConfirmacaoController` + `ConfirmacaoApiTest` (C6). Motivo: duas tasks revisáveis.
- Coluna `conta.cadastro_concluido_em` → `ativada_em`. Motivo: o nome antigo mentia (backend.md:200); `ContaResumo.ativadaEm` não muda.
- `Email.mascarado()` sai do `conta.dominio.Email`; quem precisar mascara com `nucleo.EmailMascarado.mascarar(String)` (P9). Motivo: o ciclo `conta ↔ notificacao` (backend.md:199) e não prender a C1 (primeira onda) à P9. A P9 cita "o `Email.mascarado()` da C1 delega a ela": ver Dúvidas.
- `OrigemDoAcesso(InetAddress, int)` (indefinido no plano original) → `nucleo.Origem(String ip, int porta)` da P6. A C10 converte para `inet` no SQL (`cast(:ip as inet)`).
- "dono" da mensagem na outbox → `titular` (nome da P8). A C usa `ResumoDeEmail.hex(email)` como titular das mensagens de entrada.
- `GET /api/conta` sem sessão: 401 → **204 sem corpo** (ruling do controlador; o terraço chama a rota a cada carga do `/` e o `PrimeiraCargaIT` reprova resposta ≥ 400). A rota entra em `RotasPublicas`. As outras rotas de `/api/conta/**` continuam com 401.
- `V9__conta_nova_e_acessos.sql` (T15) → `V8__registro_acesso.sql` (C10) + `V9__uso_conta_nova.sql` (C11). Motivo: `decisoes.md` (migrações).
- `LimitesConfigurados` (indefinido) → `LimitesDeContaNovaConfig`.
- `RetencaoDeContaTest` → `RetencaoDeLinksTest` + `RetencaoDeSessoesTest`.
- `Sessoes.abrir(ContaId)` e `Sessoes.validar(String)` ganham `nucleo.Origem` na C10. Nenhum consumidor fora de `conta` (o `ContasDeTeste` é ajustado na C10).
- `EncarregadoDeDados` e o `encarregado` em `/api/legal/versoes` saem da T16 e entram na L1 (Etapa 9 T1 back).

### Nomes consumidos da trilha P (de `secao-P.md`)
| Task P | Nome e assinatura |
|---|---|
| P1 | `nucleo.CodigoDeErro { String name(); TipoDeErro tipo(); String titulo(); }`; `nucleo.TipoDeErro` (`NAO_AUTENTICADO`→401, `PROIBIDO`→403, `NAO_ENCONTRADO`→404, `CONFLITO`→409, `REGRA_DE_NEGOCIO`→422, `LIMITE`→429…); `nucleo.ErroDeNegocio`: `public ErroDeNegocio(CodigoDeErro codigo, String mensagem)`, `protected ErroDeNegocio(CodigoDeErro codigo, String mensagem, Map<String,Object> extras, @Nullable Duration tentarDeNovoEm)`, `public Optional<Duration> tentarDeNovoEm()` (vira `Retry-After` no `TratamentoDeErros`); `web.CodigoWeb.{NAO_AUTENTICADO, CORPO_INVALIDO, FORMATO_NAO_SUPORTADO, ORIGEM_RECUSADA, LIMITE_DE_TAXA, CHAVE_DE_IDEMPOTENCIA_INVALIDA, CHAVE_DE_IDEMPOTENCIA_REUSADA, REQUISICAO_EM_ANDAMENTO}`; `web.TratamentoDeErros` (problem+json com `codigo`, `mensagem` = `detail`; erro de interceptor passa por ele); bean `Clock` em `web.BaseConfig`; teste: `web.RelogioDeTeste(Instant)` com `avancar(Duration)` |
| P2 | teste: `BancoDeTeste` (`INICIO`, `mvc`, `jdbc`, `relogio` `@Primary`, `limpar()` com `TRUNCATE … CASCADE`); `RelogioDeTeste.ajustar(Instant)`; deps (`restclient`, `commonmark` + front matter, `bucket4j`, `testcontainers-postgresql`); blocos `# conta` e `# legal` vazios nos `application*.properties`; `ConfiguracaoDeProdTest.segredosSemPadrao` (todo `${…}` de prod sem `:`); config de teste com `duora.agendamentos.habilitados=false`; failsafe com `duora.limites.multiplicador=1000` e `<exclude>` do `TurnstileChavesDeTesteIT` |
| P3 | `nucleo.GeradorDeIds.novo(): UUID`; bean `RandomGenerator` (`SecureRandom`) em `BaseConfig`; `nucleo.Resumos.sha256(String): byte[]` e `sha256Hex(String): String` |
| P6 | `nucleo.Origem(String ip, int porta)`; `web.OrigemDaRequisicao.de(HttpServletRequest): Origem`; `web.LimiteDeTaxa.consumir(String regra, String chave)` (regra desconhecida → `IllegalStateException`); `web.LimiteExcedido extends ErroDeNegocio` (429 `LIMITE_DE_TAXA` + `Retry-After`); `web.LimitesDeTaxa` lê `duora.limites.regras.<regra>.capacidade|periodo` (valores de conta no bloco `# conta`, escritos pela C) |
| P7 | `web.Idempotencia`: `<T> Optional<T> reservar(String escopo, String chave, String impressao, Class<T> tipo)` (`REQUIRES_NEW`; vazio = a chave é sua; presente = resposta gravada; 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`, 409 `REQUISICAO_EM_ANDAMENTO` com `Retry-After: 1`, 400 `CHAVE_DE_IDEMPOTENCIA_INVALIDA`); `void concluir(String escopo, String chave, Object resposta)` (`MANDATORY`); `void liberar(String escopo, String chave)` (`REQUIRES_NEW`) |
| P8 | `notificacao.Outbox { String EMAIL = "email"; <T> void registrar(String tipo, String titular, T carga); int esquecerTitular(String titular); }` (`registrar` é `MANDATORY`); teste: `public class notificacao.infra.DespachanteDaOutbox` com `despacharAgora(): int` |
| P9 | `notificacao.MensagemDeEmail(String para, String assunto, String texto, String html)`; `nucleo.EmailMascarado.mascarar(String)`; teste: `notificacao.infra.EmailNoLog.ultimaPara(String email): Optional<MensagemDeEmail>`, `web.ServidorHttpLento.iniciar(Duration)` |
| P10 | `retencao.PoliticaDeRetencao { String nome(); Duration prazo(); int expirar(Instant limite); long tamanho(); }` (apaga o anterior a `limite`, estrito, lotes de até 1000); `retencao.Lotes.apagarAteAcabar(IntSupplier lote): int` (a política só fornece o `DELETE` de um lote); `retencao.ExpiracaoDeDados.prazos(): Map<String, Duration>`; agendamento só com `duora.agendamentos.habilitados=true` |

---

### Contrato de API da 2a

Valem para toda rota: corpo de erro `application/problem+json` com `type`, `title`, `status`, `detail`, `codigo` e `mensagem` (= `detail`); toda mutação exige `X-Duora: 1` e mesma origem (senão 403 `ORIGEM_RECUSADA`); corpo com campo desconhecido, tipo errado ou data mal formada → 400 `CORPO_INVALIDO`; `Content-Type` diferente de JSON → 415 `FORMATO_NAO_SUPORTADO`; método não mapeado numa rota pública → 405 `METODO_NAO_PERMITIDO`; toda resposta de `/api` sai com `Cache-Control: no-store`. Toda rota de `/api/**` fora da lista pública abaixo responde 401 `NAO_AUTENTICADO` sem sessão, **inclusive rota inexistente** (nunca 404 sem sessão). Rotas públicas (`RotasPublicas`, C4): `/api/salas/**`, `/api/sala/**` (cobre `GET /api/sala`), `/api/conta` (só o caminho exato; sessão opcional), `/api/conta/entrar`, `/api/conta/confirmar`, `/api/conta/configuracao`, `/api/legal/**`, `/api/denuncias-publicas`.

| Rota | Método | Corpo / cabeçalhos | Sucesso | Erros (status `CODIGO`) | Task |
|---|---|---|---|---|---|
| `/api/conta` | GET | cookie `duora_sessao` opcional | Com sessão válida: 200 `{"estado","cadastroConcluido","verificada"}` (exatamente esses campos): `PENDENTE` → `cadastroConcluido:false`; `AGUARDANDO_VERIFICACAO` → `false`; `ATIVA` → `true`; `verificada:false` em todos até a 2b; reemite o cookie se a sessão renovou. **Sem sessão: 204 sem corpo** (anônimo); cookie inválido ou vencido → 204 + `Set-Cookie` expirado | 403 `CONTA_INDISPONIVEL` (`SUSPENSA`, `BANIDA`). Nunca 401 | C4 |
| `/api/conta/sair` | POST | cookie | 204 + `Set-Cookie: duora_sessao=; Max-Age=0` | 401 `NAO_AUTENTICADO` | C4 |
| `/api/conta/configuracao` | GET | — | 200 `{"chaveDoSiteTurnstile":"..."}` | — | C5 |
| `/api/conta/entrar` | POST | `{"email","tokenVerificacao"}`; `Idempotency-Key` opcional (`^[A-Za-z0-9-]{8,64}$`) | 202 `{"mensagem":"Enviamos um link para o seu e-mail. Ele vale por 15 minutos."}`, idêntico exista ou não a conta, bloqueado ou não; repetição com a mesma chave e o mesmo e-mail → a mesma resposta, sem efeito | 422 `EMAIL_INVALIDO`, `EMAIL_DESCARTAVEL`, `VERIFICACAO_HUMANA_FALHOU`; 400 `CHAVE_DE_IDEMPOTENCIA_INVALIDA`; 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`, `REQUISICAO_EM_ANDAMENTO` (+ `Retry-After: 1`); 429 `LIMITE_DE_TAXA` (+ `Retry-After` em segundos) | C5 |
| `/api/conta/confirmar` | POST | `{"token"}`; cookie anterior opcional | 200 `{"cadastroConcluido":bool}` + `Set-Cookie: duora_sessao=<token>; Path=/; Max-Age=2592000; HttpOnly; Secure; SameSite=Lax` (sem `Secure` se `duora.sessao.cookie-seguro=false`) | 400 `CORPO_INVALIDO` (JSON mal formado ou campo extra); 403 `ORIGEM_RECUSADA` (sem `X-Duora: 1`); 403 `CONTA_INDISPONIVEL` (conta do e-mail `SUSPENSA` ou `BANIDA`; nenhuma sessão abre; mensagem `Esta conta não está disponível.`); 415 `FORMATO_NAO_SUPORTADO`; 422 `LINK_INVALIDO` (token ausente, vazio, vencido, usado, inexistente ou de e-mail bloqueado: mesma resposta); 429 `LIMITE_DE_TAXA` + `Retry-After` (regra `confirmar-ip`). Sem 401: a rota é pública | C6 |
| `/api/conta/cadastro` | POST | `{"dataDeNascimento":"AAAA-MM-DD","versaoTermos","versaoPrivacidade","aceito":true}` (estrito) | 200 `{"cadastroConcluido":false,"proximoPasso":"VERIFICACAO"}`; conta vai a `AGUARDANDO_VERIFICACAO` | 401; 403 `CONTA_INDISPONIVEL`; 409 `CADASTRO_JA_CONCLUIDO`; 422 `ACEITE_NECESSARIO`, `TERMOS_DESATUALIZADOS`, `DATA_INVALIDA`, `MENOR_DE_IDADE` (+ cookie expirado); 429 `LIMITE_DE_TAXA` | C7 |
| `/api/conta/exclusao` | POST | `{"confirmo":true}` | 204 + cookie expirado | 401; 422 `CONFIRMACAO_NECESSARIA` | C8 |
| `/api/legal/versoes` | GET | — | 200 `{"termos":{"versao"},"privacidade":{"versao"},"contato","encarregado":{"nome","email"}}` | — | L1 |
| `/api/legal/{documento}` | GET | `documento` ∈ `termos`, `privacidade` | 200 `{"versao","html"}` | 404 `DOCUMENTO_INEXISTENTE` | L1 |
| `/api/denuncias-publicas` | POST | `{"motivo","descricao","referencia"?,"emailContato"?,"tokenVerificacao"}`; `motivo` ∈ `PARECE_MENOR`, `ABUSO_INFANTIL`, `IMAGEM_INTIMA`, `ASSEDIO`, `GOLPE`, `OUTRO` | 202 `{"mensagem":"Recebemos sua denúncia. Se você deixou um e-mail, respondemos por lá."}` | 422 `DENUNCIA_PUBLICA_INVALIDA`, `VERIFICACAO_HUMANA_FALHOU`; 429 `LIMITE_DE_TAXA` | C12 |

Em qualquer rota protegida: 403 `CADASTRO_INCOMPLETO` quando a conta está `PENDENTE` ou `AGUARDANDO_VERIFICACAO` e o handler não declara `@ContaAtual(cadastroConcluido = false)` (na 2a, só `sair`, `cadastro` e `exclusao` declaram; `GET /api/conta` é pública e lê a sessão sozinha). `sair`, `cadastro` e `exclusao` sem sessão → 401 `NAO_AUTENTICADO`.

**Mensagens exatas (`mensagem`):** `NAO_AUTENTICADO` `Entre para continuar.` · `CONTA_INDISPONIVEL` `Esta conta não está disponível.` · `CADASTRO_INCOMPLETO` `Conclua o cadastro para continuar.` · `EMAIL_INVALIDO` `Confira o e-mail digitado.` · `EMAIL_DESCARTAVEL` `Use um e-mail permanente.` · `VERIFICACAO_HUMANA_FALHOU` `Não foi possível confirmar que você é uma pessoa. Tente de novo.` · `LINK_INVALIDO` `Este link expirou ou já foi usado. Peça um novo.` · `DATA_INVALIDA` `Confira a data de nascimento.` · `ACEITE_NECESSARIO` `Aceite os Termos de uso e a Política de privacidade para continuar.` · `TERMOS_DESATUALIZADOS` `Os termos mudaram. Recarregue a página e leia a versão nova.` · `MENOR_DE_IDADE` `O Duora é só para maiores de 18 anos.` · `CADASTRO_JA_CONCLUIDO` `O cadastro já foi concluído.` · `CONFIRMACAO_NECESSARIA` `Confirme que entende que a exclusão apaga seus dados.` · `DOCUMENTO_INEXISTENTE` `Documento não encontrado.` · `LIMITE_DE_CONTA_NOVA` (429, consumido pelas Etapas 4/5) `Contas novas têm um limite por dia nos primeiros 7 dias. Tente de novo amanhã.` · `DENUNCIA_PUBLICA_INVALIDA`, por campo: `Escolha o que aconteceu.` (motivo), `Conte o que aconteceu em 10 a 2000 caracteres.` (descricao), `Use até 200 caracteres no apelido ou link.` (referencia), `Confira o e-mail para resposta.` (emailContato).

---

### Task C1: E-mail normalizado e domínios descartáveis

**Files:**
- Create: `m/conta/dominio/{Email,DominiosDescartaveis,ResumoDeEmail}.java`, `m/conta/CodigoEmail.java`, `m/conta/infra/ContaConfig.java`
- Create: `r/conta/dominios-descartaveis.txt` (cópia de `disposable_email_blocklist.conf` de `disposable-email-domains/disposable-email-domains`, CC0, com cabeçalho de origem e data), `scripts/atualizar-dominios-descartaveis.sh`
- Modify: bloco `# conta` de `r/application.properties` (`duora.segredo-email=<valor fixo de desenvolvimento, 32+ caracteres>`) e de `r/application-prod.properties` (`duora.segredo-email=${DUORA_SEGREDO_EMAIL}`, sem padrão: o `ConfiguracaoDeProdTest` da P2 cobra)
- Test: `t/conta/dominio/{EmailTest,DominiosDescartaveisTest,ResumoDeEmailTest}.java`

**Interfaces:**
- Consumes: P1 `ErroDeNegocio`, `CodigoDeErro`, `TipoDeErro.REGRA_DE_NEGOCIO`; P2 blocos `# conta` e `ConfiguracaoDeProdTest`; P5 `ArquiteturaTest`.
- Produces: `public record Email(String valor)` com `public static Email de(String bruto)` (strip + minúsculas; até 254 caracteres, um `@`, parte local 1–64, domínio com ponto e rótulos `[a-z0-9-]`, sem espaço nem controle; senão `EMAIL_INVALIDO`), `public String dominio()`, `public String chaveDeBloqueio()` (tira `+tag`; em `gmail.com`/`googlemail.com` tira os pontos e usa `gmail.com`). `public final class DominiosDescartaveis`: `public boolean contem(String dominio)` (casa subdomínios). `public final class ResumoDeEmail` (chave injetada): `public byte[] de(Email email)` = HMAC-SHA256 de `chaveDeBloqueio()`; `public String hex(Email email)`. `public enum CodigoEmail implements CodigoDeErro { EMAIL_INVALIDO, EMAIL_DESCARTAVEL }` (ambos `REGRA_DE_NEGOCIO`). `ContaConfig` registra `DominiosDescartaveis` (lido de `classpath:conta/dominios-descartaveis.txt`) e `ResumoDeEmail` (`duora.segredo-email`).

- [ ] **Step 1: testes que falham.** `EmailTest` `@ParameterizedTest` inválidos → `ErroDeNegocio` com `codigo() == EMAIL_INVALIDO`: `""`, `" "`, `"a@b"`, `"a@@b.com"`, parte local de 65, total de 255, `"x\u0000@a.com"`, `"a b@c.com"`; `normalizaEChaveDoGmail`: `" A.B+promo@GMail.com "` → `valor()` `a.b+promo@gmail.com`, `chaveDeBloqueio()` `ab@gmail.com`; `chaveForaDoGmailMantemPontos`: `"a.b+x@outlook.com"` → `a.b@outlook.com`; `parteLocalDe64Aceita` e `totalDe254Aceito` (on points). `DominiosDescartaveisTest`: `mailinator.com` e `sub.mailinator.com` → `true`; `gmail.com` e `notmailinator.com` → `false`. `ResumoDeEmailTest`: `Nome+x@gmail.com` e `n.ome@gmail.com` → mesmo resumo; chaves diferentes → resumos diferentes; resumo ≠ SHA-256 puro; `hex` tem 64 caracteres `[0-9a-f]`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='EmailTest,DominiosDescartaveisTest,ResumoDeEmailTest'` → FAIL (símbolos inexistentes).
- [ ] **Step 3: implementar.** `DominiosDescartaveis` recebe as linhas no construtor (puro); ignora `#` e linhas vazias; `contem` sobe os sufixos do domínio. `ResumoDeEmail` usa `javax.crypto.Mac` com `HmacSHA256`.
- [ ] **Step 4:** o mesmo comando → PASS; `./mvnw -q test -Dtest=ArquiteturaTest` → PASS. No grep de dados pessoais da `duora-etapa`, os acertos de `@gmail.com` nas fixtures de teste são esperados (e-mails fictícios); registrar no relatório.
- [ ] **Step 5:** `git commit -m "feat(conta): e-mail normalizado e domínios descartáveis"`

### Task C2: Verificação humana pelo Turnstile

**Files:**
- Create: `m/conta/dominio/VerificacaoHumana.java`, `m/conta/CodigoVerificacaoHumana.java`, `m/conta/infra/{TurnstileVerificacao,TurnstileConfig}.java`
- Modify: bloco `# conta` de `r/application.properties` (`duora.turnstile.chave-do-site=1x00000000000000000000AA`, `duora.turnstile.segredo=1x0000000000000000000000000000000AA`) e de `r/application-prod.properties` (`duora.turnstile.chave-do-site=${TURNSTILE_SITE_KEY}`, `duora.turnstile.segredo=${TURNSTILE_SECRET}`, sem padrão)
- Test: `t/conta/infra/{TurnstileVerificacaoTest,TurnstileChavesDeTesteIT}.java` (o IT fica fora do failsafe padrão pelo `<exclude>` da P2), `t/conta/VerificacaoHumanaDeTeste.java` (dublê)

**Interfaces:**
- Consumes: P1 `CodigoDeErro`; P2 `RestClient.Builder` (restclient).
- Produces: porta `public interface VerificacaoHumana { boolean verificar(String token, String ip); }`. `public enum CodigoVerificacaoHumana implements CodigoDeErro { VERIFICACAO_HUMANA_FALHOU }` (`REGRA_DE_NEGOCIO`). `TurnstileVerificacao(RestClient.Builder, TurnstileConfig)`: `POST` de formulário em `duora.turnstile.url` (padrão `https://challenges.cloudflare.com/turnstile/v0/siteverify`) com `secret`, `response`, `remoteip`; timeouts conexão 3 s e leitura 5 s; lê `success`; token vazio ou > 2048 caracteres → `false` sem rede; rede, timeout ou 5xx → `false` + WARN (falha fechada). `TurnstileConfig` (`@ConfigurationProperties("duora.turnstile")`): `chaveDoSite`, `segredo`, `url`. Teste: `@Component @Primary public class VerificacaoHumanaDeTeste implements VerificacaoHumana` (recusa só o token `falha`; `int chamadas()`, `void atrasar(Duration)`, `void zerar()`).

- [ ] **Step 1: testes que falham.** `TurnstileVerificacaoTest` (`MockRestServiceServer.bindTo(builder)`, sem contexto Spring): `sucessoTrue`; `sucessoFalse`; `erro500ViraFalse`; `excecaoDeTimeoutViraFalse` (`withException(new SocketTimeoutException())`); `corpoTemSecretResponseERemoteip`; `tokenVazioNaoFazRequisicao` e `tokenDe2049NaoFazRequisicao` (`server.verify()` sem expectativas); `respostaQueDemora6sViraFalseEmMenosDe6s` (servidor que dorme 6 s, `duora.turnstile.url` apontando para ele: `false` e tempo < 6 s, prova o timeout real da fábrica; usa `t/web/ServidorHttpLento` se a P9 já estiver na `etapa-2`, senão um `HttpServer` do JDK no próprio teste, duplicação aceita e sem volta). `TurnstileChavesDeTesteIT` (rede real; fora do failsafe padrão, roda só com `-Dit.test`; `Assumptions.assumeTrue("chromium".equals(System.getProperty("navegador", "chromium")))`): segredo `1x0000000000000000000000000000000AA` + token `XXXX.DUMMY.TOKEN.XXXX` → `true`; segredo `2x0000000000000000000000000000000AA` → `false`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=TurnstileVerificacaoTest` → FAIL.
- [ ] **Step 3: implementar.** `RestClient` com `ClientHttpRequestFactoryBuilder` + `HttpClientSettings` (3 s / 5 s), a mesma API da P9, conferida no Context7; resposta lida como `record RespostaSiteverify(boolean success)` (ignora outros campos).
- [ ] **Step 4:** o mesmo comando → PASS; `./mvnw -q -Pe2e verify -Dit.test=TurnstileChavesDeTesteIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): verificação humana pelo turnstile"`

### Task L1: Documentos legais, back (Etapa 9 T1)

Textos com `design-skills:ux-writing`; modelo em `docs/legal/{terms,privacy}/2026-11-07/pt-BR.md` do repositório C# irmão (o caminho vem no dispatch; só leitura).

**Files:**
- Create: `r/legal/{termos,privacidade}.md` (front matter `versao: AAAA-MM-DD`; marcadores `{{contato}}` e `{{encarregado}}`; conteúdo obrigatório de `etapa-9-termos.md:99-102`, limites de 800 e 1300 palavras)
- Create: `m/conta/{DocumentoLegal,VersaoDeDocumento,DocumentosLegais,EncarregadoDeDados,CodigoLegal}.java`, `m/conta/web/LegalController.java`, `m/conta/infra/LegalConfig.java`
- Modify: bloco `# legal` de `r/application.properties` (`duora.contato.email=support@meetduora.com`, `duora.encarregado.nome=o responsável pelo Duora`, `duora.encarregado.email=encarregado@exemplo.invalid`, uma por linha) e de `r/application-prod.properties` (`duora.encarregado.email=${DUORA_EMAIL_ENCARREGADO}`, sem padrão)
- Create: `src/test/resources/legal-teste/{valido,sem-front-matter,marcador-desconhecido,com-html,sem-privacidade}/{termos,privacidade}.md`
- Test: `t/conta/{DocumentosLegaisTest,LegalApiTest,ConteudoLegalTest,EncarregadoDeDadosTest}.java`

**Interfaces:**
- Consumes: P1 `CodigoDeErro`, `TipoDeErro.NAO_ENCONTRADO`; P2 `commonmark`, `BancoDeTeste`; P5 `ArquiteturaTest`.
- Produces: `public enum DocumentoLegal { TERMOS, PRIVACIDADE }` (`public String caminho()` = `termos`/`privacidade`). `public record VersaoDeDocumento(String versao, String resumoSha256)` (hex minúsculo, 64). `public class DocumentosLegais(ResourceLoader carregador, String diretorio, String contato, EncarregadoDeDados encarregado)`: `public VersaoDeDocumento atual(DocumentoLegal documento)`, `public String html(DocumentoLegal documento)`; carrega tudo na construção de `duora.legal.dir` (padrão `classpath:legal/`); resumo = SHA-256 do texto já com os marcadores trocados; marcador desconhecido, arquivo ausente, front matter ausente ou versão fora de `\d{4}-\d{2}-\d{2}` → `IllegalStateException` citando o arquivo (boot falha). Commonmark com `escapeHtml(true)` e `sanitizeUrls(true)`. `@ConfigurationProperties("duora.encarregado") @Validated public record EncarregadoDeDados(@NotBlank String nome, @NotBlank @Email String email)`. `public enum CodigoLegal implements CodigoDeErro { DOCUMENTO_INEXISTENTE }`. Rotas da tabela (L1). `Cache-Control` fica o `no-store` do filtro (o `LegalController` não o sobrescreve).

- [ ] **Step 1: testes que falham.** `DocumentosLegaisTest` (constrói com `classpath:legal-teste/<cenario>/`, contato `teste@exemplo.com`, encarregado `dpo@exemplo.invalid`): `leVersaoEResumo` (versão `2026-01-02`; resumo = literal calculado uma vez com `shasum -a 256` e gravado no teste); `trocaMarcadoresDeContatoEEncarregado`; `htmlBrutoEscapado` (`<script>alert(1)</script>` → `&lt;script&gt;`); `linkJavascriptSaneado` (sem `href="javascript:`); `semFrontMatterFalha`, `marcadorDesconhecidoFalha`, `arquivoAusenteFalha` (`IllegalStateException` com o nome do arquivo). `LegalApiTest` (`BancoDeTeste`, sem cookie): `versoesSemSessao` (200, chaves exatas `termos`, `privacidade`, `contato`, `encarregado`; `encarregado.email` = `encarregado@exemplo.invalid`); `documentoSemSessao` (200, `versao` e `html`); `documentoDesconhecido404` (`codigo` `DOCUMENTO_INEXISTENTE`). `ConteudoLegalTest` (arquivos reais): as asserções de `etapa-9-termos.md:107`. `EncarregadoDeDadosTest`: `semEmailOContextoFalha` (`ApplicationContextRunner` sem `duora.encarregado.email` → falha citando `duora.encarregado.email`); `prodExigeAVariavel` (a linha `duora.encarregado.email=${DUORA_EMAIL_ENCARREGADO}` existe em `application-prod.properties`, sem `:`).
- [ ] **Step 2:** `./mvnw -q test -Dtest='DocumentosLegaisTest,EncarregadoDeDadosTest'` → FAIL.
- [ ] **Step 3: implementar** código e textos.
- [ ] **Step 4:** `./mvnw -q test -Dtest='DocumentosLegaisTest,LegalApiTest,ConteudoLegalTest,EncarregadoDeDadosTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(legal): termos, privacidade e encarregado versionados (api)"`

### Task C3: Contas e sessões no banco

Antes do Step 1: `mattpocock-skills:domain-modeling` (estados e invariantes de `Conta`) e `mattpocock-skills:codebase-design` com duas alternativas para `ConsultaDeContas` e `Sessoes`; se mudar interface pública, atualizar este plano antes.

**Files:**
- Create: `mig/V4__contas.sql`, `mig/V5__sessoes.sql`
- Create: `m/conta/{ContaId,EstadoConta,ContaResumo,ConsultaDeContas,Sessoes,SessaoAtiva}.java`, `m/conta/dominio/{Conta,Token,RepositorioDeContas,RepositorioDeSessoes}.java`, `m/conta/infra/{ContasJdbc,SessoesJdbc}.java`
- Test: `t/conta/{SessoesTest,ContasJdbcTest,ContasDeTeste}.java`

**Esquema:** V4 `conta(id uuid primary key, email text unique, estado text not null check (estado in ('PENDENTE','AGUARDANDO_VERIFICACAO','ATIVA','SUSPENSA','BANIDA','EXCLUIDA')), data_nascimento date, criada_em timestamptz not null, ativada_em timestamptz, excluida_em timestamptz, check (email = lower(email)), check ((estado = 'EXCLUIDA') = (email is null)), check (estado not in ('AGUARDANDO_VERIFICACAO','ATIVA','SUSPENSA','BANIDA') or data_nascimento is not null))`. V5 `sessao(id uuid primary key, conta_id uuid not null references conta(id) on delete cascade, token_hash bytea not null unique, criada_em timestamptz not null, expira_em timestamptz not null, renovada_em timestamptz not null)` + índices em `conta_id` e `expira_em`.

**Interfaces:**
- Consumes: P1 `Clock`; P2 `BancoDeTeste`, `RelogioDeTeste.ajustar`; P3 `GeradorDeIds.novo()`, bean `RandomGenerator`, `Resumos.sha256`; P5 `ArquiteturaTest`; C1 `Email`.
- Produces: `public record ContaId(UUID valor)` (não nulo; `toString()` = `valor.toString()`). `public enum EstadoConta { PENDENTE, AGUARDANDO_VERIFICACAO, ATIVA, SUSPENSA, BANIDA, EXCLUIDA }`. `public interface ConsultaDeContas { Optional<ContaResumo> porId(ContaId id); }`; `public record ContaResumo(ContaId id, EstadoConta estado, @Nullable LocalDate dataDeNascimento, boolean verificada, @Nullable Instant ativadaEm)` com `public OptionalInt idadeEm(LocalDate hoje)` (`verificada` sempre `false` na 2a). `public record SessaoAtiva(ContaId conta, EstadoConta estado, boolean renovada)`. `public class Sessoes`: `public String abrir(ContaId conta)`, `public Optional<SessaoAtiva> validar(String token)` (válida se `expira_em > agora`; renova por `UPDATE ... SET renovada_em = :agora, expira_em = :agora + 30d WHERE id = :id AND renovada_em < :agora - 24h`), `public void encerrar(String token)`, `public void revogarTodas(ContaId conta)`. `dominio.Token`: `static Token novo(RandomGenerator)` (32 bytes, base64url sem padding), `static Token de(String valor)`, `String valor()`, `byte[] resumo()` (= `Resumos.sha256(valor)`). A C3 não cria bean de aleatório: usa o `RandomGenerator` da P3. `RepositorioDeContas`: `ContaId criarPendente(Email email)`, `Optional<Conta> porEmail(Email email)`, `Optional<Conta> porId(ContaId id)`. `ContasJdbc implements RepositorioDeContas, ConsultaDeContas`. Teste (consumido pela F e pela Etapa 3/4): `@Component public class ContasDeTeste` com `public ContaDeTeste criar(EstadoConta estado, LocalDate nascimento)`, `public ContaDeTeste criarAtiva()` (nascimento 1990-05-10, `ativada_em` = agora); `public record ContaDeTeste(ContaId id, String cookie)` com `cookie` = `duora_sessao=<token>`; e-mails `<uuid>@teste.duora.invalid`.

- [ ] **Step 1: testes que falham** (`BancoDeTeste`, `relogio.avancar`). `SessoesTest`: `abrirEValidarDevolveAConta`; `bancoGuardaSoOResumo` (`token_hash` = SHA-256 do token); `expiraAos30DiasExatos` (30 d → vazio) e `valeAte30DiasMenos1s`; `naoRenovaCom24hExatas` (nenhuma linha muda) e `renovaCom24hE1s` (`expira_em` = agora + 30 d, `renovada() == true`); `encerrarInvalida`; `revogarTodasSoDaConta` (A perde 2, B mantém 1); `renovacoesSimultaneasGravamUmaVez` (duas threads após 24 h + 1 s → uma `renovada() == true`). `ContasJdbcTest`: `emailUnicoNoBanco` (segundo `criarPendente` do mesmo e-mail → `DuplicateKeyException`); `excluidaSemEmailNaoColide` (duas linhas `EXCLUIDA` com `email` nulo); `checkRecusaAtivaSemNascimento`; `porIdDevolveResumo` (`verificada == false`).
- [ ] **Step 2:** `./mvnw -q test -Dtest='SessoesTest,ContasJdbcTest'` → FAIL.
- [ ] **Step 3: implementar** (adapters com `JdbcClient`; ids pelo `GeradorDeIds`; instantes pelo `Clock`).
- [ ] **Step 4:** `./mvnw -q test -Dtest='SessoesTest,ContasJdbcTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): contas e sessões no banco"`

### Task C4: Sessão em cookie e negação por padrão na API

**Files:**
- Create: `m/conta/{ContaAtual,RotasPublicas,CodigoSessao}.java`, `m/conta/web/{CookieSessao,SessaoDaRequisicao,ContaAtualResolver,ExigeSessao,ContaWebConfig,ContaController}.java`
- Test: `t/conta/{SessaoApiTest,RotasProtegidasTest,TerracoAnonimoTest,ControllerDeTesteDeSessao}.java`

**Interfaces:**
- Consumes: C3 `Sessoes`, `ConsultaDeContas`, `ContasDeTeste`; P1 `CodigoWeb.NAO_AUTENTICADO`, `TratamentoDeErros`, convenção `/teste/**` dos controllers de teste da P; P2 `BancoDeTeste`; P5 `ArquiteturaTest`.
- Produces: `@Target(PARAMETER) @Retention(RUNTIME) public @interface ContaAtual { boolean cadastroConcluido() default true; }` num parâmetro `ContaId`. `public final class RotasPublicas`: só `public static final List<String> PADROES` (strings) = a lista final da tabela de contrato (ninguém mais edita na 2a). O casamento fica em `conta/web/ExigeSessao` (`PathPatternParser`, de `org.springframework.web.util.pattern`, proibido na raiz pela regra `dominioSemWebNemJdbc` da P5; `/api/sala/**` casa `/api/sala`). `public enum CodigoSessao implements CodigoDeErro { CONTA_INDISPONIVEL, CADASTRO_INCOMPLETO }` (ambos `PROIBIDO`). `CookieSessao(@Value("${duora.sessao.cookie-seguro:true}") boolean seguro)`: `String emitir(String token)`, `String expirar()`, `Optional<String> ler(HttpServletRequest)`. `SessaoDaRequisicao` (único que valida o cookie; usado pelo interceptor e pelo `ContaController`): `Optional<SessaoAtiva> ler(HttpServletRequest, HttpServletResponse)`: sem cookie → vazio sem `Set-Cookie`; cookie inválido ou vencido → vazio + `Set-Cookie` expirado; renovada → reemite o cookie. `ExigeSessao` (interceptor em `/api/**`): casa o caminho com os `RotasPublicas.PADROES` já compilados pelo `PathPatternParser`; rota pública passa **sem ler cookie**; `ler` vazio → 401 `NAO_AUTENTICADO`; `SUSPENSA`/`BANIDA`/`EXCLUIDA` → 403 `CONTA_INDISPONIVEL`; `PENDENTE`/`AGUARDANDO_VERIFICACAO` sem `@ContaAtual(cadastroConcluido = false)` → 403 `CADASTRO_INCOMPLETO`. `ContaController`: `GET /api/conta` (pública) chama `ler`; vazio → 204 sem corpo; `SUSPENSA`/`BANIDA` → 403 `CONTA_INDISPONIVEL`; senão 200 com os três campos. `POST /api/conta/sair` (protegida, `@ContaAtual(cadastroConcluido = false)`) → 204 + cookie expirado. Teste: `@RestController ControllerDeTesteDeSessao` com `GET /api/teste/so-ativa` (`@ContaAtual ContaId` → 200).

- [ ] **Step 1: testes que falham** (`BancoDeTeste`, MockMvc). `SessaoApiTest`: `ativaRecebeOsTresCampos` (`{"estado":"ATIVA","cadastroConcluido":true,"verificada":false}`, nada além); `aguardandoRecebeCadastroConcluidoFalse` e `aguardandoNaRotaSoAtivaRecebe403CadastroIncompleto`; `pendenteRecebeCadastroConcluidoFalse`; `suspensaRecebe403ContaIndisponivel`; `contaSemSessaoResponde204` (`GET /api/conta` sem cookie → 204, corpo vazio, sem `Set-Cookie`); `exclusaoSemSessaoResponde401` (`POST /api/conta/exclusao` sem cookie → 401 `NAO_AUTENTICADO`; a rota pode ainda não existir na C4: o 401 vem do interceptor, e a C8 mantém o teste verde); `sairSemSessaoRecebe401SemSetCookie`; `cookieInvalidoEmGetContaRecebe204EApagaCookie` (204 + `Max-Age=0`); `cookieInvalidoEmRotaProtegidaRecebe401EApagaCookie` (`/api/teste/so-ativa` → 401 + `Max-Age=0`); `renovacaoReemiteCookie` (após 24 h + 1 s, `GET /api/conta` → `Set-Cookie` com `Max-Age=2592000`); `sairApagaCookieESessao` (204 + `Max-Age=0`; o mesmo cookie em `GET /api/conta` → 204 e em `/api/teste/so-ativa` → 401); `cookieSeguroPorPadrao` (`Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`). `RotasProtegidasTest`: para cada padrão `/api/**` do `RequestMappingHandlerMapping` fora de `RotasPublicas` (variáveis → `x`, `X-Duora: 1`), sem cookie → 401 `NAO_AUTENTICADO` (varre só `/api/**`: os controllers de teste da P ficam em `/teste/**`, P1); `rotaInexistenteRecebe401` (`GET /api/qualquer` e `GET /api/conta/qualquer`); nenhuma rota pública → 401; `getContaEhPublicaSoNoCaminhoExato` (sem cookie: `GET /api/conta` → 204; `POST /api/conta/sair` e `POST /api/conta/cadastro` → 401). `TerracoAnonimoTest`, sem `duora_sessao` e depois com `duora_sessao=invalido` (resposta idêntica): `GET /api/sala` → 204; `POST /api/salas` → 201 + `duora_assento`; `POST /api/salas/entrar` com convite válido → 200; `POST /api/sala/pecas` com `duora_assento` → 200; `POST /api/sala/sair` → 204.
- [ ] **Step 2:** `./mvnw -q test -Dtest='SessaoApiTest,RotasProtegidasTest,TerracoAnonimoTest'` → FAIL.
- [ ] **Step 3: implementar.** `ContaWebConfig implements WebMvcConfigurer` registra o interceptor e o `ContaAtualResolver` (lê o `ContaId` que o `ExigeSessao` pôs num atributo da requisição). Erro do interceptor sai pelo `TratamentoDeErros`; o `Set-Cookie` expirado é escrito na resposta antes da exceção.
- [ ] **Step 4:** `./mvnw -q verify` → PASS (suíte do terraço sem mudar asserção, `ArquiteturaTest` incluído).
- [ ] **Step 5:** `git commit -m "feat(conta): sessão em cookie e negação por padrão na api"`

### Task C5: Pedido de link mágico com outbox e idempotência

**Files:**
- Create: `mig/V6__entrada.sql`
- Create: `m/conta/{PedidoDeEntrada,MensagensDeEntrada}.java`, `m/conta/dominio/{RepositorioDeLinks,RepositorioDeBloqueios}.java`, `m/conta/infra/{LinksJdbc,BloqueiosJdbc}.java`, `m/conta/web/PedidoDeEntradaController.java`
- Modify: bloco `# conta` (`duora.url-publica=http://127.0.0.1:8080`; regras `entrar-ip` 20/`PT10M` e `entrar-email` 3/`PT15M` no formato `duora.limites.regras.*`; prod `duora.url-publica=${DUORA_URL_PUBLICA}`)
- Test: `t/conta/{PedidoDeEntradaApiTest,OrigemDeTeste}.java`

**Esquema:** `link_de_entrada(id uuid primary key, email text not null check (email = lower(email)), token_hash bytea not null unique, criado_em timestamptz not null, expira_em timestamptz not null, usado_em timestamptz)` + índices em `email` e `expira_em`; `email_bloqueado(chave_hmac bytea primary key, motivo text not null check (motivo in ('MENOR')), criado_em timestamptz not null)`.

**Interfaces:**
- Consumes: P3 `Resumos.sha256Hex`; P6 `LimiteDeTaxa.consumir`, `OrigemDaRequisicao.de`; P7 `Idempotencia.reservar`/`concluir`/`liberar`; P8 `Outbox.registrar(Outbox.EMAIL, ResumoDeEmail.hex(email), MensagemDeEmail)`, `DespachanteDaOutbox.despacharAgora()`; P9 `MensagemDeEmail`, `EmailNoLog.ultimaPara`; C1 `Email`, `DominiosDescartaveis`, `ResumoDeEmail`, `CodigoEmail`; C2 `VerificacaoHumana`, `CodigoVerificacaoHumana`, `VerificacaoHumanaDeTeste`; C3 `Token`, `GeradorDeIds`.
- Produces: `public RespostaDeEntrada PedidoDeEntrada.pedir(String emailBruto, String tokenVerificacao, String ip, Optional<String> chaveDeIdempotencia)`; `public record RespostaDeEntrada(String mensagem)`. `RepositorioDeLinks`: `void invalidarAbertos(Email email, Instant agora)`, `void gravar(UUID id, Email email, byte[] tokenHash, Instant criadoEm, Instant expiraEm)`, `Optional<Email> consumir(byte[] tokenHash, Instant agora)`, `int apagarDoEmail(Email email)`. `RepositorioDeBloqueios`: `boolean bloqueado(byte[] resumo)`, `void bloquear(byte[] resumo, String motivo, Instant agora)` (`ON CONFLICT DO NOTHING`). `MensagensDeEntrada`: `MensagemDeEmail link(Email para, String token)` e `MensagemDeEmail avisoDeBloqueio(Email para)` com os textos exatos do plano original (linhas 419–421; link `<duora.url-publica>/entrar/confirmar#t=<token>`; `html` = o mesmo texto em `<p>` com o link num `<a>`). Rotas `POST /api/conta/entrar` e `GET /api/conta/configuracao` da tabela. Teste: `OrigemDeTeste.ipAleatorio(): RequestPostProcessor` (`remoteAddr` `10.x.y.z` a partir de um UUID; forma da P6: `.with(r -> { r.setRemoteAddr(ip); return r; })`).
- Ordem de `pedir` (não determinada pela assinatura): 1. `consumir("entrar-ip", ip)`; 2. `Email.de`; 3. com chave: `idempotencia.reservar("entrar", chave, Resumos.sha256Hex(email.valor()), RespostaDeEntrada.class)` (transação própria da P7); presente → devolve sem efeito; 4. `consumir("entrar-email", email.chaveDeBloqueio())`; 5. Turnstile **fora de transação** (`false` → `VERIFICACAO_HUMANA_FALHOU`); 6. descartável → `EMAIL_DESCARTAVEL`; 7. numa `TransactionTemplate`: bloqueado → registra só o aviso; senão `invalidarAbertos`, link novo (`expira_em` = agora + 15 min) e o e-mail na outbox (titular `ResumoDeEmail.hex(email)`); com chave, `idempotencia.concluir("entrar", chave, resposta)` na mesma transação; 8. qualquer exceção depois do passo 3 → `idempotencia.liberar("entrar", chave)` e relança. O efeito do passo 7 fica num método package-private `gravarLinkEMensagem(Email, Token...)` para o teste de atomicidade (grava `token.resumo()`, o `byte[] tokenHash` do `RepositorioDeLinks.gravar`, e põe `token.valor()` no link do e-mail).

- [ ] **Step 1: testes que falham** (`BancoDeTeste`, `VerificacaoHumanaDeTeste` zerado no `@BeforeEach`). `entrarEnviaLinkDeQuinzeMinutos` (202, mensagem exata; após `despacharAgora`, `ultimaPara` contém `/entrar/confirmar#t=`; `expira_em` = agora + 15 min); `linkEEmailNaMesmaTransacao` (`TransactionTemplate` chama `gravarLinkEMensagem` e lança → zero links e zero mensagens); `respostaIgualComOuSemConta` e `respostaIgualComEmailBloqueado` (status e corpo idênticos); `emailDescartavelRecusado`, `emailInvalidoRecusado` e `verificacaoHumanaRecusada` (422, outbox vazia); `repeticaoComMesmaChaveNaoDuplicaNemGastaTurnstile` (dois POST seguidos → mesma resposta, 1 link, 1 mensagem, `chamadas() == 1`); `duploToqueSimultaneoUmTurnstile` (`atrasar(300 ms)`, duas threads com a mesma chave → `chamadas() == 1`, 1 link, 1 mensagem; respostas ∈ {202 igual, 409 `REQUISICAO_EM_ANDAMENTO` com `Retry-After: 1`}); `mesmaChaveOutroEmailRecusada` (409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`); `turnstileRecusadoLiberaAChave` (token `falha` → 422; mesma chave com token bom → 202); `rajadaPorIpBarrada` (20 passam, 21º → 429 com `Retry-After`); `quartoPedidoDoMesmoEmailBarradoMesmoTrocandoIp`; `pedidoNovoInvalidaLinkAnterior` (`usado_em` preenchido no antigo); `emailBloqueadoRecebeAvisoSemLink` (mensagem sem `#t=`); `logNaoTrazEmailNemToken` (`OutputCaptureExtension`: o e-mail canário cru não aparece em linha nenhuma; o token só aparece em linhas do `EmailNoLog`, exceção da P9 por contrato); `configuracaoDevolveChaveDoSite` (`1x00000000000000000000AA`).
- [ ] **Step 2:** `./mvnw -q test -Dtest=PedidoDeEntradaApiTest` → FAIL.
- [ ] **Step 3: implementar** na ordem acima; o controller traduz cabeçalho, IP (`OrigemDaRequisicao`) e corpo (`record PedidoDeEntradaJson(String email, String tokenVerificacao)`).
- [ ] **Step 4:** `./mvnw -q test -Dtest='PedidoDeEntradaApiTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): pedido de link mágico com outbox e idempotência"`

### Task C6: Confirmação do link e abertura de sessão

**Files:**
- Create: `m/conta/{ConfirmacaoDeEntrada,CodigoEntrada}.java`, `m/conta/web/ConfirmacaoController.java`
- Modify: bloco `# conta` (regra `confirmar-ip` 30/`PT10M`)
- Test: `t/conta/ConfirmacaoApiTest.java`

**Interfaces:**
- Consumes: C5 `RepositorioDeLinks.consumir`, `RepositorioDeBloqueios.bloqueado`, `PedidoDeEntrada`; C3 `RepositorioDeContas`, `Sessoes`; C4 `CookieSessao`, `CodigoSessao.CONTA_INDISPONIVEL`; C1 `ResumoDeEmail`; P3 `Resumos.sha256`; P6 `LimiteDeTaxa`, `OrigemDaRequisicao.de`; F3 (encaminhamento do SPA, só para `linkSobreviveAoPreCarregamento`).
- Produces: `public enum CodigoEntrada implements CodigoDeErro { LINK_INVALIDO }` (`REGRA_DE_NEGOCIO`). `public ResultadoDaConfirmacao ConfirmacaoDeEntrada.confirmar(String token, String ip, Optional<String> tokenDaSessaoAnterior)`; `public record ResultadoDaConfirmacao(String tokenDaSessao, boolean cadastroConcluido)`. Ordem, numa transação (depois do limite): 1. `consumir("confirmar-ip", ip)`; 2. `consumir(Resumos.sha256(token), agora)` (token nulo, vazio ou com mais de 64 caracteres → `LINK_INVALIDO` sem consulta) (`UPDATE ... SET usado_em = :agora WHERE token_hash = :h AND usado_em IS NULL AND expira_em > :agora RETURNING email`); vazio → `LINK_INVALIDO`; 3. e-mail bloqueado (`RepositorioDeBloqueios.bloqueado(resumo)`) → `LINK_INVALIDO`, sem criar conta (link antigo nunca recria conta bloqueada); 4. conta pelo e-mail ou `criarPendente`; 5. `SUSPENSA`/`BANIDA` → `CONTA_INDISPONIVEL` (a transação desfaz o consumo do link); 6. encerra a sessão anterior, se houver, e abre outra. Rota `POST /api/conta/confirmar` da tabela.

- [ ] **Step 1: testes que falham.** `confirmarCriaContaPendenteEAbreSessao` (`Set-Cookie` com `duora_sessao=`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Max-Age=2592000`; corpo `{"cadastroConcluido":false}`); `contaAtivaRecebeCadastroConcluidoTrue`; `linkUsadoDuasVezesRecusado`; `linkValeAte14min59s` e `linkExpiraAos15min` (on/off point, `expira_em > agora`); `tokenInexistenteRecusado`; `linkSobreviveAoPreCarregamento` (`GET /entrar/confirmar` → 200 pelo encaminhamento do F3 e `usado_em` continua nulo; depois o POST → 200); `confirmacoesSimultaneasAbremUmaSessao` (duas threads → um 200 e um 422; uma linha em `sessao`); `loginNovoEncerraSessaoAnterior` (a linha da sessão do cookie enviado some); `contaSuspensaRecebe403` (`codigo` `CONTA_INDISPONIVEL`, sem `Set-Cookie`, zero sessões); `tokenVazioRecusado` (`{"token":""}` → 422 `LINK_INVALIDO`); `campoExtraRecebe400` (400 `CORPO_INVALIDO`); `linkAntigoDeEmailBloqueadoNaoCriaConta` (`bloquear(resumo)` depois do pedido → 422 `LINK_INVALIDO`, zero contas); `rajadaDeConfirmacaoPorIpBarrada` (31º em 10 min → 429).
- [ ] **Step 2:** `./mvnw -q test -Dtest=ConfirmacaoApiTest` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** `./mvnw -q test -Dtest='ConfirmacaoApiTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): confirmação do link e abertura de sessão"`

### Task C7: Cadastro com nascimento declarado e aceite versionado

**Files:**
- Create: `mig/V7__cadastro.sql`
- Create: `m/conta/dominio/{Idade,ResultadoDoCadastro,RepositorioDeAceites}.java`, `m/conta/{Cadastro,CodigoCadastro}.java`, `m/conta/infra/AceitesJdbc.java`, `m/conta/web/CadastroController.java`
- Modify: `m/conta/dominio/{Conta,RepositorioDeContas}.java`, `m/conta/infra/ContasJdbc.java`, bloco `# conta` (regra `cadastro-conta` 10/`PT10M`)
- Test: `t/conta/dominio/{IdadeTest,ContaTest}.java`, `t/conta/CadastroApiTest.java`

**Esquema:** `aceite_documento(conta_id uuid not null references conta(id) on delete cascade, documento text not null check (documento in ('TERMOS','PRIVACIDADE')), versao text not null, resumo_sha256 text not null check (resumo_sha256 ~ '^[0-9a-f]{64}$'), aceito_em timestamptz not null, primary key (conta_id, documento, versao))`.

**Interfaces:**
- Consumes: L1 `DocumentosLegais.atual(DocumentoLegal)` → `VersaoDeDocumento`; C5 `RepositorioDeBloqueios.bloquear`, `RepositorioDeLinks.invalidarAbertos`; C3 `Sessoes.revogarTodas`; C4 `@ContaAtual`, `CookieSessao.expirar()`; C1 `ResumoDeEmail.hex`; P6 `LimiteDeTaxa` (`cadastro-conta`); P8 `Outbox.esquecerTitular`.
- Produces: `static boolean Idade.maiorDeIdade(LocalDate nascimento, LocalDate hoje)` (≥ 18 por `Period.between`). `public sealed interface ResultadoDoCadastro permits Concluido, RecusadoPorIdade`; `ResultadoDoCadastro Conta.concluirCadastro(LocalDate nascimento, LocalDate hoje)` (puro; estado ≠ `PENDENTE` → `CADASTRO_JA_CONCLUIDO`; futuro ou antes de 1900-01-01 → `DATA_INVALIDA`). `public enum CodigoCadastro implements CodigoDeErro { DATA_INVALIDA, ACEITE_NECESSARIO, TERMOS_DESATUALIZADOS, MENOR_DE_IDADE (REGRA_DE_NEGOCIO), CADASTRO_JA_CONCLUIDO (CONFLITO) }`. `RepositorioDeContas` ganha `boolean marcarAguardandoVerificacao(ContaId id, LocalDate nascimento)` (`UPDATE ... WHERE id = :id AND estado = 'PENDENTE'`) e `void excluir(ContaId id, Instant agora)` (`EXCLUIDA`, `email` e `data_nascimento` nulos, `excluida_em`). `RepositorioDeAceites`: `void gravar(ContaId, DocumentoLegal, VersaoDeDocumento, Instant)`, `int apagarDaConta(ContaId)`. `public ResultadoDoCadastro Cadastro.concluir(ContaId conta, LocalDate nascimento, String versaoTermos, String versaoPrivacidade, boolean aceito)`: limite → `aceito` → versões → `concluirCadastro`, com "hoje" = `LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Sao_Paulo"))`. Efeitos, numa transação: `RecusadoPorIdade` → bloqueia o resumo, invalida os links abertos do e-mail, `outbox.esquecerTitular(ResumoDeEmail.hex(email))` (D-1: o e-mail de link pendente perde a carga), revoga sessões, `excluir`; o controller responde 422 `MENOR_DE_IDADE` com cookie expirado. `Concluido` → `marcarAguardandoVerificacao` (falso → `CADASTRO_JA_CONCLUIDO`) + dois aceites com o resumo atual. Rota `POST /api/conta/cadastro` da tabela.

- [ ] **Step 1: testes que falham.** `IdadeTest` `@ParameterizedTest`: hoje 2026-11-22 → 2008-11-22 `true`, 2008-11-23 `false`, 1990-01-01 `true`; hoje 2026-02-28 com 2008-02-29 → `false`; hoje 2026-03-01 → `true`. `ContaTest`: `pendenteMaiorConclui`, `pendenteMenorRecusada`, `aguardandoNaoConcluiDeNovo`, `dataFuturaRecusada`, `dataAntesDe1900Recusada`, `1900_01_01Aceita`. `CadastroApiTest`: `maiorVaiParaAguardandoVerificacao` (200 com o corpo exato; `GET /api/conta` → `AGUARDANDO_VERIFICACAO`, `cadastroConcluido:false`, `verificada:false`; duas linhas de aceite com o resumo do `DocumentosLegais`; `/api/teste/so-ativa` → 403 `CADASTRO_INCOMPLETO`); `segundoEnvioRecusado` (409, data inalterada); `cadastrosSimultaneosUmVence` (um 200 e um 409); `menorRecusadoEBloqueado` (422, `Set-Cookie` `Max-Age=0`, conta `EXCLUIDA` sem e-mail, 1 linha em `email_bloqueado`, zero sessões; com uma mensagem de link do mesmo e-mail pendente na outbox (pedido sem `despacharAgora`), a linha fica com `carga` nula e `ultimo_erro = 'titular-esquecido'`); `menorNaoReabreContaComLinkAntigo` (pede o link A, confirma A, pede o link B já com sessão, cadastro menor; confirmar B → 422 `LINK_INVALIDO`, zero contas novas); `menorTentaDeNovoComVariacaoDoEmail` (bloqueado `Nome+x@gmail.com`; `entrar` com `n.ome@gmail.com` → aviso sem link); `versaoAntigaRecusada` (422 `TERMOS_DESATUALIZADOS`); `semAceiteRecusado` (422 `ACEITE_NECESSARIO`); `campoExtraRecusado` (`"estado":"ATIVA"` → 400 `CORPO_INVALIDO`, conta inalterada); `idadeUsaFusoDeSaoPaulo` (`relogio.ajustar(2026-11-22T02:00Z)`, nascimento 2008-11-22 → 422) e `idadeVira18AMeiaNoiteDeSaoPaulo` (03:00Z → 200); `undecimoCadastroEm10MinRecebe429`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='IdadeTest,ContaTest,CadastroApiTest'` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** `./mvnw -q test -Dtest='IdadeTest,ContaTest,CadastroApiTest,ArquiteturaTest'` → PASS. No grep de dados pessoais da `duora-etapa`, os acertos de `@gmail.com` nas fixtures de teste são esperados (e-mails fictícios); registrar no relatório.
- [ ] **Step 5:** `git commit -m "feat(conta): cadastro com nascimento declarado e aceite versionado"`

### Task C8: Exclusão de conta com ganchos

**Files:**
- Create: `m/conta/{AoExcluirConta,ExclusaoDeConta,CodigoExclusao}.java`, `m/conta/web/ExclusaoController.java`
- Test: `t/conta/{ExclusaoApiTest,GanchoDeExclusaoDeTeste}.java`

**Interfaces:**
- Consumes: C7 `RepositorioDeContas.excluir`, `RepositorioDeAceites.apagarDaConta`; C5 `RepositorioDeLinks.apagarDoEmail`; C3 `Sessoes.revogarTodas`; C1 `ResumoDeEmail.hex`; P8 `Outbox.esquecerTitular(String titular)`.
- Produces: `public interface AoExcluirConta { void excluir(ContaId conta); }` (roda na transação da exclusão, na ordem do `@Order`; arquivos só no `afterCommit`; Etapas 3, 5, 6 e 8 implementam). `public enum CodigoExclusao implements CodigoDeErro { CONFIRMACAO_NECESSARIA }` (`REGRA_DE_NEGOCIO`). `public void ExclusaoDeConta.excluir(ContaId conta, boolean confirmo)`: sem confirmação → erro; senão, numa transação: ganchos → `outbox.esquecerTitular(ResumoDeEmail.hex(email))` (D-1; o titular junta as variações do mesmo e-mail, como o bloqueio) → apaga aceites (D-3), sessões e links do e-mail → `excluir` (lápide anônima). Rota `POST /api/conta/exclusao` da tabela. Teste: `@Component GanchoDeExclusaoDeTeste implements AoExcluirConta` (registra as contas recebidas; `falharNaProxima()`).

- [ ] **Step 1: testes que falham.** `semConfirmacaoRecusa` (422 `CONFIRMACAO_NECESSARIA`, conta intacta); `excluiDadosPessoais` (204 + `Max-Age=0`; e-mail e nascimento nulos, `EXCLUIDA`, `excluida_em` = agora; zero sessões, aceites e links do e-mail; o cookie antigo → 401); `mensagemPendenteDaContaPerdeACarga` (pedido de link sem despachar, depois exclusão → `despacharAgora() == 0`, `ultimaPara` vazio; na linha da outbox `carga` nula, `falhou_em` preenchido e `ultimo_erro = 'titular-esquecido'`); `ganchoRodaNaExclusao`; `falhaNoGanchoDesfazTudo` (500; conta segue `ATIVA` com e-mail, sessões e aceites); `mesmoEmailCriaContaNova` (entrar e confirmar de novo → conta `PENDENTE` nova, id diferente); `outraContaIntacta`; `aguardandoTambemExclui` (`AGUARDANDO_VERIFICACAO` → 204).
- [ ] **Step 2:** `./mvnw -q test -Dtest=ExclusaoApiTest` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** `./mvnw -q test -Dtest='ExclusaoApiTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): exclusão de conta com ganchos"`

### Task C9: Retenção de links e sessões

**Files:**
- Create: `m/conta/infra/{RetencaoDeLinks,RetencaoDeSessoes}.java`
- Test: `t/conta/{RetencaoDeLinksTest,RetencaoDeSessoesTest}.java`

**Interfaces:**
- Consumes: P10 `PoliticaDeRetencao`, `Lotes.apagarAteAcabar`; C5 `link_de_entrada`; C3 `sessao`.
- Produces: `RetencaoDeLinks implements PoliticaDeRetencao` (`nome()` = `links-de-entrada`, `prazo()` = `P1D`, apaga `expira_em < limite`); `RetencaoDeSessoes` (`sessoes-expiradas`, `PT0S`, `expira_em < limite`). Cada uma passa a `Lotes.apagarAteAcabar` o lote de uma transação (`DELETE ... WHERE id IN (SELECT id ... LIMIT 1000)`); `tamanho()` = linhas com `expira_em < agora`.

- [ ] **Step 1: testes que falham** (`BancoDeTeste`, chamam `expirar(agora - prazo)` direto). `RetencaoDeLinksTest`: `linkVencidoHaMaisDeUmDiaSome` (1 d + 1 s); `linkVencidoHaUmDiaExatoFica` (on point); `linkValidoFica`; `apaga2500EmLotes` (`expirar` devolve 2500, zero restantes); `tamanhoContaVencidos` (antes 3, depois 0); `nomeEPrazo`. `RetencaoDeSessoesTest`: `sessaoVencidaSome` (vencida há 1 s); `sessaoQueVenceAgoraFica` (`expira_em` = agora); `sessaoValidaFica`; `nomeEPrazo`.
- [ ] **Step 2:** `./mvnw -q test -Dtest='RetencaoDeLinksTest,RetencaoDeSessoesTest'` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** o mesmo comando → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): retenção de links e sessões"`

### Task C10: Registros de acesso por 183 dias

**Files:**
- Create: `mig/V8__registro_acesso.sql`
- Create: `m/conta/{RegistroDeAcessos,EventoDeAcesso}.java`, `m/conta/dominio/RepositorioDeAcessos.java`, `m/conta/infra/{RegistroDeAcessosJdbc,RetencaoDeAcessos}.java`
- Modify: `m/conta/Sessoes.java`, `m/conta/ConfirmacaoDeEntrada.java`, `m/conta/web/{SessaoDaRequisicao,ConfirmacaoController}.java`, `t/conta/{ContasDeTeste,SessoesTest}.java` (e `t/conta/SessaoApiTest.java` só se chamar `Sessoes` direto)
- Test: `t/conta/RegistroDeAcessosTest.java`

**Esquema:** `registro_acesso(id uuid primary key, conta_id uuid references conta(id), ip inet not null, porta int check (porta between 0 and 65535), instante timestamptz not null, evento text not null check (evento in ('LOGIN','RENOVACAO')))` + índice em `instante`. Sem `on delete cascade`: a conta excluída é lápide e o registro fica pelo prazo legal.

**Interfaces:**
- Consumes: P6 `nucleo.Origem`, `OrigemDaRequisicao.de`; P10 `PoliticaDeRetencao`, `Lotes.apagarAteAcabar`; C3 `Sessoes`; C4 `SessaoDaRequisicao`; C6 `ConfirmacaoDeEntrada`; C8 (exclusão, no teste).
- Produces: `public enum EventoDeAcesso { LOGIN, RENOVACAO }`; `public void RegistroDeAcessos.registrar(Optional<ContaId> conta, Origem origem, EventoDeAcesso evento)` (MANDATORY; nunca loga; grava `cast(:ip as inet)`, sem DNS). `Sessoes.abrir(ContaId conta, Origem origem)` registra `LOGIN` e `Sessoes.validar(String token, Origem origem)` registra `RENOVACAO` só quando o `UPDATE` condicional renovou, na mesma transação. `RetencaoDeAcessos`: `registros-de-acesso`, `P183D`, `instante < limite` (lote `DELETE ... WHERE id IN (SELECT id ... LIMIT 1000)` passado a `Lotes.apagarAteAcabar`). A origem vem de `OrigemDaRequisicao.de(request)` (D-2: `remoteAddr`/`remotePort` até a Etapa 10, que troca só a classe da P6).

- [ ] **Step 1: testes que falham.** `loginRegistraIpPortaEInstante` (confirmar com `remoteAddr` `203.0.113.77` e porta 54321 → uma linha `LOGIN` com esses valores e o instante do relógio); `renovacaoRegistraUmaVezPor24h` (duas chamadas a `GET /api/conta` após 24 h + 1 s → uma linha `RENOVACAO`); `chamadaSemRenovacaoNaoRegistra`; `exclusaoDaContaMantemORegistro` (C8; a linha continua com `conta_id`); `registroFica183DiasExatos` e `registroSai183DiasE1sDepois`; `logDaAplicacaoNaoTrazOIp` (saída capturada sem `203.0.113.77`).
- [ ] **Step 2:** `./mvnw -q test -Dtest=RegistroDeAcessosTest` → FAIL.
- [ ] **Step 3: implementar;** ajustar as chamadas de `abrir`/`validar` (`ConfirmacaoController`, `SessaoDaRequisicao`, `ContasDeTeste` com `new Origem("127.0.0.1", 0)`).
- [ ] **Step 4:** `./mvnw -q test -Dtest='RegistroDeAcessosTest,SessoesTest,SessaoApiTest,ConfirmacaoApiTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): registros de acesso por 183 dias"`

### Task C11: Limites de conta nova

**Files:**
- Create: `mig/V9__uso_conta_nova.sql`
- Create: `m/conta/{LimitesDeContaNova,TipoDeLimiteNovo,LimitesDeContaNovaConfig,CodigoContaNova,LimiteDeContaNovaExcedido}.java`, `m/conta/dominio/{RegraDeContaNova,RepositorioDeUsoDeContaNova}.java`, `m/conta/infra/{LimitesDeContaNovaJdbc,RetencaoDeUsoDeContaNova}.java`
- Test: `t/conta/dominio/RegraDeContaNovaTest.java`, `t/conta/LimitesDeContaNovaTest.java`

**Esquema:** `uso_conta_nova(conta_id uuid not null references conta(id) on delete cascade, dia date not null, tipo text not null check (tipo in ('MENSAGEM','IMAGEM','CONVITE')), usados int not null check (usados >= 0), primary key (conta_id, dia, tipo))`.

**Interfaces:**
- Consumes: C3 `ConsultaDeContas` (`ativadaEm`), `ContasDeTeste.criarAtiva()`; P10 `PoliticaDeRetencao`, `Lotes.apagarAteAcabar`; P1 construtor `protected ErroDeNegocio(CodigoDeErro, String, Map<String,Object>, @Nullable Duration)` (o `TratamentoDeErros` transforma `tentarDeNovoEm` em `Retry-After`).
- Produces (Etapas 4 e 5 chamam na transação do efeito): `public enum TipoDeLimiteNovo { MENSAGEM, IMAGEM, CONVITE }`. `@ConfigurationProperties("duora.conta-nova") public record LimitesDeContaNovaConfig(@DefaultValue("200") int mensagensPorDia, @DefaultValue("10") int imagensPorDia, @DefaultValue("3") int convitesPorDia)`. `RegraDeContaNova` (pura): `static boolean emPeriodoDeContaNova(Instant ativadaEm, Instant agora)` (< 7 dias); `static int limite(TipoDeLimiteNovo tipo, int mensagens, int imagens, int convites)`. Porta `RepositorioDeUsoDeContaNova`: `OptionalInt consumir(ContaId conta, LocalDate dia, TipoDeLimiteNovo tipo, int limite)` (`INSERT ... ON CONFLICT (conta_id, dia, tipo) DO UPDATE SET usados = uso_conta_nova.usados + 1 WHERE uso_conta_nova.usados < :limite RETURNING usados`; vazio = no limite). `public void LimitesDeContaNova.consumir(ContaId conta, TipoDeLimiteNovo tipo)` (MANDATORY): fora do período não faz nada; `ativadaEm` nulo conta como dentro (falha fechada); dia em `America/Sao_Paulo`; vazio → lança `LimiteDeContaNovaExcedido` (429 `LIMITE_DE_CONTA_NOVA`, `Retry-After` até a próxima meia-noite de São Paulo). `public enum CodigoContaNova implements CodigoDeErro { LIMITE_DE_CONTA_NOVA }` (`LIMITE`). `public final class LimiteDeContaNovaExcedido extends ErroDeNegocio` com `LimiteDeContaNovaExcedido(Duration ateAMeiaNoite)` chamando o construtor protegido da P1 (`extras` vazio; `serialVersionUID` como na P1). `RetencaoDeUsoDeContaNova`: `uso-conta-nova`, `P8D` sobre `dia` (sem `id`: lote `DELETE ... WHERE (conta_id, dia, tipo) IN (SELECT conta_id, dia, tipo ... LIMIT 1000)` passado a `Lotes.apagarAteAcabar`).

- [ ] **Step 1: testes que falham.** `RegraDeContaNovaTest`: 6 d 23 h 59 min 59 s → `true`; 7 d exatos → `false`. `LimitesDeContaNovaTest` (`BancoDeTeste`, chama dentro de `TransactionTemplate`): `ducentesimaPrimeiraMensagemNoDiaRecebe429` (200 passam; a 201ª lança `LIMITE_DE_CONTA_NOVA`); `retryAfterAteAMeiaNoiteDeSaoPaulo` (`relogio.ajustar(2026-11-22T23:00Z)` = 20:00 em SP → `tentarDeNovoEm() == PT4H`); `contaComSeteDiasNaoTemLimite`; `diaNovoEmSaoPauloZera` (02:59Z e 03:00Z do dia seguinte); `consumosSimultaneosNaoPassamDoLimite` (20 threads com limite 10 → exatamente 10 sucessos); `quartoConviteRecebe429`; `retencaoApagaDiasCom8DiasEMeio` (`expirar` remove o dia de 9 dias atrás e mantém o de 8).
- [ ] **Step 2:** `./mvnw -q test -Dtest='RegraDeContaNovaTest,LimitesDeContaNovaTest'` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** `./mvnw -q test -Dtest='RegraDeContaNovaTest,LimitesDeContaNovaTest,ArquiteturaTest'` → PASS.
- [ ] **Step 5:** `git commit -m "feat(conta): limites de conta nova"`

### Task C12: Canal público de denúncia (API)

**Files:**
- Create: `mig/V10__denuncia_publica.sql`
- Create: `m/conta/{DenunciasPublicas,CodigoDenuncia}.java`, `m/conta/dominio/{DenunciaPublica,MotivoDeDenunciaPublica,RepositorioDeDenunciasPublicas}.java`, `m/conta/infra/{DenunciasPublicasJdbc,RetencaoDeDenunciasPublicas}.java`, `m/conta/web/DenunciaPublicaController.java`
- Modify: bloco `# conta` (regra `denuncia-publica-ip` 5/`PT1H`)
- Test: `t/conta/DenunciaPublicaApiTest.java`, `t/conta/RetencaoDeDenunciasPublicasTest.java`

**Esquema:** `denuncia_publica(id uuid primary key, motivo text not null check (motivo in ('PARECE_MENOR','ABUSO_INFANTIL','IMAGEM_INTIMA','ASSEDIO','GOLPE','OUTRO')), descricao text not null check (char_length(descricao) between 10 and 2000), referencia text check (char_length(referencia) <= 200), email_contato text check (char_length(email_contato) <= 254), criada_em timestamptz not null, situacao text not null default 'PENDENTE' check (situacao in ('PENDENTE','RESOLVIDA')))` + índice parcial `where situacao = 'PENDENTE'`.

**Interfaces:**
- Consumes: C2 `VerificacaoHumana`, `CodigoVerificacaoHumana`; C1 `Email.de`; L1 `duora.contato.email`; C5 `duora.url-publica`, `OrigemDeTeste`; P6 `LimiteDeTaxa` (`denuncia-publica-ip`), `OrigemDaRequisicao.de`; P8 `Outbox.registrar(Outbox.EMAIL, "denuncia:" + id, MensagemDeEmail)`; P9 `MensagemDeEmail`, `EmailNoLog`; P10 `PoliticaDeRetencao`, `Lotes.apagarAteAcabar`, `ExpiracaoDeDados.prazos()`; C9, C10, C11 (políticas afirmadas em `prazosDaContaExpostos`).
- Produces: `public enum CodigoDenuncia implements CodigoDeErro { DENUNCIA_PUBLICA_INVALIDA }` (`REGRA_DE_NEGOCIO`, mensagens por campo da tabela). `dominio.DenunciaPublica.de(String motivo, String descricao, @Nullable String referencia, @Nullable String emailContato)` (parse; `emailContato` passa por `Email.de`, erro vira `DENUNCIA_PUBLICA_INVALIDA`). `public RespostaDeDenuncia DenunciasPublicas.registrar(String motivo, String descricao, @Nullable String referencia, @Nullable String emailContato, String tokenVerificacao, String ip)`: limite → parse → Turnstile (fora de transação) → numa transação grava e registra na outbox o e-mail para `duora.contato.email`, assunto `Duora: denúncia pública recebida`, texto `Motivo: <MOTIVO>` + linha em branco + `<duora.url-publica>/admin` (nunca a descrição, a referência nem o e-mail de contato). `RetencaoDeDenunciasPublicas`: `denuncias-publicas`, `P183D`, apaga `situacao = 'RESOLVIDA' AND criada_em < limite` (lote `DELETE ... WHERE id IN (SELECT id ... LIMIT 1000)` passado a `Lotes.apagarAteAcabar`). Rota da tabela; `motivo` chega como `String` (motivo desconhecido → 422, não 400).

- [ ] **Step 1: testes que falham.** `DenunciaPublicaApiTest` (sem cookie, IP único): `semSessaoAceita202` (mensagem exata; uma linha `PENDENTE`); `turnstileRecusado422ENadaGrava` (token `falha` → 422 `VERIFICACAO_HUMANA_FALHOU`, zero linhas, outbox vazia); `sextaNaHoraRecebe429` (5 passam no mesmo IP, a 6ª → 429 com `Retry-After`); `descricaoCom9Recebe422` e `descricaoCom10Aceita`; `descricaoCom2001Recebe422`; `referenciaCom201Recebe422`; `motivoDesconhecidoRecebe422`; `emailDeContatoInvalidoRecebe422`; `emailDoAvisoNaoTrazADescricao` (descrição e referência canário ausentes da `MensagemDeEmail` de `ultimaPara(duora.contato.email)` e da carga na outbox); `campoExtraRecebe400`. `RetencaoDeDenunciasPublicasTest`: `resolvidaCom183DiasE1sSome`; `resolvidaCom183DiasExatosFica`; `pendenteAntigaFica`; `prazosDaContaExpostos` (`ExpiracaoDeDados.prazos()` contém `links-de-entrada=P1D`, `sessoes-expiradas=PT0S`, `registros-de-acesso=P183D`, `uso-conta-nova=P8D`, `denuncias-publicas=P183D`: a tabela de retenção da política de privacidade).
- [ ] **Step 2:** `./mvnw -q test -Dtest='DenunciaPublicaApiTest,RetencaoDeDenunciasPublicasTest'` → FAIL.
- [ ] **Step 3: implementar.**
- [ ] **Step 4:** `./mvnw -q verify` → PASS (fim da trilha: suíte inteira, terraço e `ArquiteturaTest`).
- [ ] **Step 5:** `git commit -m "feat(conta): canal público de denúncia (api)"`

---

### Ordem dentro da trilha
C1 → C2 → L1 → C3 → C4 → C5 → C6 → C7 → C8 → C9 → C10 → C11 → C12 (fila sequencial; a C9 espera a P10 e vem antes da C12, que afirma as políticas dela em `prazosDaContaExpostos`). Merge das migrações estritamente em V4/V5 (C3) → V6 (C5) → V7 (C7) → V8 (C10) → V9 (C11) → V10 (C12).

### Dúvidas resolvidas pelo controlador
1. O `VerificacaoHumanaDeTeste` (`@Component @Primary` em `t/conta/`) vale também no failsafe: o e2e não depende de rede. O widget do front é falso por `page.route` (F10). As chaves de teste da Cloudflare ficam no dev e no `TurnstileChavesDeTesteIT` (fora do failsafe padrão pelo `<exclude>` da P2; rede real só com `-Dit.test`).
2. Sem `Email.mascarado()`: quem mascarar usa `nucleo.EmailMascarado.mascarar` (P9).
3. `GET /api/conta` com conta `SUSPENSA`/`BANIDA` mantém 403 `CONTA_INDISPONIVEL`.
4. `RotasPublicas` só por caminho: `POST /api/conta` → 405 `METODO_NAO_PERMITIDO` é aceito.
5. `/api/legal/*` fica com `no-store` (filtro atual).
6. A C12 usa o titular `denuncia:<id>` na outbox.

### Dependências entre trilhas (task C → tasks de outras trilhas já na main)
- C1 → P2 (bloco `# conta`), P5 (`ArquiteturaTest`). C2 → P1, P2. L1 → P2, P5.
- C3 → P2, P3, P5 (e merge depois de P7 e P8, por V2/V3).
- C4 → P1 (`CodigoWeb`, `TratamentoDeErros`), P2, P5.
- C5 → P3 (`Resumos`), P6, P7, P8, P9.
- C6 → P6, F3 (`linkSobreviveAoPreCarregamento`).
- C7 → P6, P8 (`esquecerTitular`; ambas já na `main` desde a C5). C8 → P8 (`esquecerTitular`).
- C9 → P10. C10 → P6 (`Origem`), P10. C11 → P1 (construtor protegido), P10. C12 → P6, P8, P9, P10.
- Consumidores na F: F4 ← C4 (`GET /api/conta`: 204 = anônimo, 200 = conta; nunca 401); F5/F6 ← C5; F7 ← C6; F8 ← C7, L1; F9 ← C4, C8; L2 ← L1; F10/F11 ← `ContasDeTeste` (C3); F12 ← C12, L1.

### Riscos da trilha
- Contextos Spring: qualquer `properties=` ou `@MockitoBean` numa classe C cria contexto novo com pool Hikari próprio; a regra "estende `BancoDeTeste` sem propriedades" precisa ser cobrada na revisão.
- `GET /api/conta` pública com sessão opcional: a validação do cookie vive em `SessaoDaRequisicao`, usada pelo interceptor e pelo controller; duplicar a lógica em outro lugar reabre o risco de renovação ou expiração divergentes.
- Cabeçalhos antes de exceção: o 422 `MENOR_DE_IDADE` e o 401 dependem de o `Set-Cookie` sobreviver ao `TratamentoDeErros`; os testes da C4 e da C7 provam, e falhar exige mudar o desenho (resposta montada no controller).
- A C6 depende do F3 só por um teste; se o F3 atrasar, o teste espera ou a C6 entra sem ele e o teste vem no F3.
- O caminho crítico é a C (12 merges sequenciais por causa das migrações); a C1 começa assim que a P5 entra.

---

## Trilha F — front

Dona de `frontend/**` (inclui `package.json` e `pnpm-lock.yaml`), `web/RotasDoSpa*` e `web/CabecalhosDeSeguranca*` (depois da P1), `src/test/java/.../pwa/*` (guardas do bundle), `src/test/java/.../e2e/**` e `scripts/android/conta-*`. Não toca `pom.xml`, `application*.properties`, `conta/**`, `notificacao/**` nem migrações.

**Convenções da trilha**
- `$PNPM` = `cd frontend && PATH="$PWD/node:$PATH" node node/node_modules/pnpm/bin/pnpm.mjs`. Vitest de um arquivo: `$PNPM test <arquivo>`; lint: `$PNPM lint` (zero avisos).
- Java sem navegador: `./mvnw -q test -Dtest=<Classe>`. E2e de um IT: `./mvnw -q -Pe2e verify -Dit.test=<IT> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dnavegador=<chromium|firefox|webkit>`, nos três navegadores, um por vez, com a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` + `caffeinate -dims` e o Docker aberto (depois da P2, todo `@SpringBootTest` e todo e2e usam Testcontainers; a config de teste mora em `src/test/resources/config/application.properties`, sem perfil `test`).
- Toda task que muda o que o `/` carrega roda `./mvnw -q test -Dtest='TamanhoDoJsInicialTest,ChunksDoFrontTest'` e anota o valor de `target/tamanho-js-inicial.txt` no handoff. Teto: 102 400 B gzip (hoje 92 965 B).
- Telas: `frontend-design:frontend-design` antes de desenhar; `design-skills:interaction-design` (estados, erro, carregamento, foco); `design-skills:ux-writing` (textos); antes do commit, `design-skills:accessibility-audit` e `design-skills:design-critique`. Textos em pt-BR, gênero neutro, voz ativa. Sem `alert`/`confirm`/`prompt` (ESLint `no-alert`). Indisponível = `aria-disabled` + `seDisponivel` (`componentes/estilos.ts`), nunca `disabled`, sempre com o motivo em texto ligado por `aria-describedby`. Seletores do e2e por papel, rótulo e texto.
- Mensagens de erro do servidor: a tela mostra `ErroHttp.mensagem` quando existe; o texto fixo da tela só cobre rede e 5xx (`Não conseguimos falar com o servidor. Tente de novo.`).
- Commits: Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

### Renomeações
| Antigo | Novo | Motivo |
|---|---|---|
| `Participantes.novoComConta(ContaDeTeste)` (plano:557; consumido pela Etapa 4) | `Contas.comConta(TerracoPagina pagina, ContaDeTeste conta, String raiz)` em `e2e/Contas.java` | não mexer na `NavegadorExtension` compartilhada (frontend.md §4) |
| `web/PaginasDoFront` (frontend.md) | `web/RotasDoSpa` | id do brief |
| estado da conta em `frontend/src/sessao/` (plano:548) | `frontend/src/api/estadoDaConta.ts` no `/`; TanStack Query na casca | `sessao/` é a sessão do terraço |
| destino depois do cadastro `/verificacao` (plano:575-576) | `/aguardando` (tela "Cadastro recebido") | D-A: a 2a para em `AGUARDANDO_VERIFICACAO`; a 2b troca o destino |
| botão `Sair` de Minha conta (plano:597) | `Sair da conta` | o `Topo` do terraço já tem um `Sair` |
| casos novos no `AxeIT` (plano:550, 595, 791) | `AxeContaIT` (F13) e axe dentro do `LegalIT` (L2) | `AxeIT` deixa de ser ponto de conflito |

### Contrato de API consumido (a tabela da trilha C vence; o controlador reconcilia)
Erro: `application/problem+json` com `codigo`, `mensagem` (= `detail`) e `detail` em toda resposta de erro, inclusive as do próprio Spring (P1); `429` traz `Retry-After` em segundos arredondados para cima. Toda requisição leva `X-Duora: 1`.

| Rota | Corpo | Respostas usadas pelo front |
|---|---|---|
| `GET /api/conta` | — | 200 `{estado: "PENDENTE"\|"AGUARDANDO_VERIFICACAO"\|"ATIVA", cadastroConcluido, verificada}` (`verificada` sempre `false` na 2a); sem sessão (ou sessão inválida): **204 sem corpo** — rota pública, nunca 401 (ruling do controlador; mantém o `PrimeiraCargaIT` sem resposta ≥ 400); 403 `CONTA_INDISPONIVEL` |
| `GET /api/conta/configuracao` | — | 200 `{chaveDoSiteTurnstile}` |
| `POST /api/conta/entrar` | `{email, tokenVerificacao}` + `Idempotency-Key` | 202 `{mensagem}`; 422 `EMAIL_INVALIDO`/`EMAIL_DESCARTAVEL`/`VERIFICACAO_HUMANA_FALHOU`; 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`; 409 `REQUISICAO_EM_ANDAMENTO` + `Retry-After: 1` (backend.md, P7); 429 `LIMITE_DE_TAXA` |
| `POST /api/conta/confirmar` | `{token}` | 200 `{cadastroConcluido}` + `Set-Cookie`; 422 `LINK_INVALIDO` (também para e-mail bloqueado, C6); conta indisponível e demais códigos conforme o contrato da seção C; 429 |
| `POST /api/conta/cadastro` | `{dataDeNascimento: "AAAA-MM-DD", versaoTermos, versaoPrivacidade, aceito: true}` | 200 `{cadastroConcluido: false, proximoPasso: "VERIFICACAO"}`; 422 `MENOR_DE_IDADE` (cookie expirado)/`DATA_INVALIDA`/`ACEITE_NECESSARIO`/`TERMOS_DESATUALIZADOS`; 409 `CADASTRO_JA_CONCLUIDO`; 401; 429 |
| `POST /api/conta/sair` | — | 204 + cookie expirado |
| `POST /api/conta/exclusao` | `{confirmo: true}` | 204 + cookie expirado; 422 `CONFIRMACAO_NECESSARIA`; 401 |
| `GET /api/legal/versoes` | — | 200 `{termos: {versao}, privacidade: {versao}, contato, encarregado: {nome, email}}` (L1) |
| `GET /api/legal/{termos\|privacidade}` | — | 200 `{versao, html}` (HTML já escapado e saneado pelo servidor); 404 `DOCUMENTO_INEXISTENTE` |
| `POST /api/denuncias-publicas` | `{motivo, descricao, referencia?, emailContato?, tokenVerificacao}`; `motivo` ∈ `PARECE_MENOR`, `ABUSO_INFANTIL`, `IMAGEM_INTIMA`, `ASSEDIO`, `GOLPE`, `OUTRO` | 202 `{mensagem}`; 422 `DENUNCIA_PUBLICA_INVALIDA`/`VERIFICACAO_HUMANA_FALHOU`; 429 |

Rotas do SPA (fonte única no front: `src/rotas/caminhosDaCasca.ts`; no back: `web/RotasDoSpa.CAMINHOS`): `/entrar`, `/entrar/confirmar`, `/cadastro`, `/aguardando`, `/conta`, `/termos`, `/privacidade`, `/denunciar`, `/encarregado`. Nenhuma começa por `/app/` (contrato:56).

---

### Task F1: Transporte HTTP genérico

**Files:**
- Create: `frontend/src/api/http.ts`, `frontend/src/api/http.test.ts`
- Modify: `frontend/src/api/cliente.ts` (passa a usar o transporte; `cliente.test.ts` não muda)

**Interfaces:**
- Consumes: corpo de erro `{codigo, mensagem, detail}` (P1).
- Produces:
  - `export class ErroHttp extends Error { readonly status: number; readonly codigo: string; readonly mensagem: string | null; readonly tentarDeNovoEmSegundos: number | null; readonly corpo: unknown; get transitorio(): boolean }` (`status` 0 = rede; `transitorio` = 0 ou ≥ 500).
  - `export interface OpcoesDaRequisicao { corpo?: unknown; cabecalhos?: Record<string, string>; etag?: string | null; sinal?: AbortSignal }`
  - `export interface RespostaHttp { status: number; cabecalhos: Headers; dados: unknown }`
  - `export function requisitarHttp(metodo: "GET" | "POST", caminho: string, opcoes?: OpcoesDaRequisicao): Promise<RespostaHttp>` (caminho relativo a `/api`; 2xx e 304 não lançam).
  - `export function novaChaveDeIdempotencia(cripto?: Pick<Crypto, "getRandomValues"> & Partial<Pick<Crypto, "randomUUID">>): string` (casa `^[A-Za-z0-9-]{8,64}$`).

- [ ] **Passo 1: teste que falha** (`vi.stubGlobal("fetch")`, como em `cliente.test.ts:6-14`)
  - `enviaXDuoraAcceptECredenciais`: `headers["X-Duora"] === "1"`, `credentials === "same-origin"`, `cache === "no-store"`.
  - `leCodigoEMensagemDoProblemJson`: 422 `{codigo:"EMAIL_INVALIDO", mensagem:"Use um e-mail válido.", detail:"x"}` → `ErroHttp{status:422, codigo:"EMAIL_INVALIDO", mensagem:"Use um e-mail válido."}`.
  - `semMensagemUsaDetail`: só `detail:"d"` → `mensagem === "d"`.
  - `leRetryAfterEmSegundos`: 429 com `Retry-After: 120` → `tentarDeNovoEmSegundos === 120`; sem o cabeçalho → `null`.
  - `falhaDeRedeViraStatusZero`: `fetch` rejeita → `status === 0`, `codigo === "REDE"`, `transitorio === true`.
  - `cabecalhosExtrasSaem`: `{cabecalhos: {"Idempotency-Key": "k-12345678"}}` chega ao `fetch`.
  - `respostaSemJsonDevolveDadosNulos`: 202 `text/plain` → `dados === null`.
  - `novaChaveUsaRandomUUID` e `novaChaveSemRandomUUIDUsaGetRandomValues` (só `getRandomValues` → 32 hex, casa a regex).
- [ ] **Passo 2:** `$PNPM test src/api/http.test.ts` → FAIL (`requisitarHttp` não existe).
- [ ] **Passo 3: implementar** `requisitarHttp` e `novaChaveDeIdempotencia` em `http.ts`; `cliente.ts/requisitar` delega a `requisitarHttp` e converte `ErroHttp` em `ErroApi` (lendo `motivo` e `visao` de `ErroHttp.corpo`). Refatoração sem mudança de comportamento.
- [ ] **Passo 4:** `$PNPM test src/api/` e `$PNPM lint` → PASS (o `cliente.test.ts` intacto prova a refatoração).
- [ ] **Passo 5:** `git add frontend/src/api && git commit -m "refactor(front): transporte http genérico"`

### Task F2: Casca de conta com React Router e TanStack Query sob demanda

**Files:**
- Create: `frontend/src/despachar.ts` (+`.test.ts`), `frontend/src/rotas/{montar.tsx,rotas.tsx,caminhosDaCasca.ts,LayoutDeConta.tsx,NaoEncontrada.tsx}`, `frontend/src/rotas/montar.test.tsx`
- Modify: `frontend/src/main.tsx`, `frontend/package.json`, `frontend/pnpm-lock.yaml`, `frontend/vite.config.ts` (grupo `casca-de-conta`), `frontend/eslint.config.js` (`no-alert: "error"`), `src/test/java/.../pwa/{Estaticos,TamanhoDoJsInicialTest,ChunksDoFrontTest}.java`

**Interfaces:**
- Produces:
  - `export function ehCaminhoDoTerraco(caminho: string): boolean` (`/` e `/index.html`).
  - `export interface DependenciasDoDespacho { iniciarTerraco: (raiz: HTMLElement, convite: string | null) => unknown; carregarCasca: () => Promise<{ montarCasca: (raiz: HTMLElement, local: Location, historia: History) => Root }> }`
  - `export function despachar(local: Location, historia: History, raiz: HTMLElement, deps: DependenciasDoDespacho): void` — no terraço chama `lerConvite` e `iniciarTerraco`; fora dele limpa o esqueleto do terraço (`raiz.replaceChildren()`) e chama `carregarCasca` **sem** tocar no fragmento. O `main.tsx` passa `() => import("./rotas/montar")`.
  - `export function montarCasca(raiz: HTMLElement, local: Location, historia: History): Root` — `StrictMode` + `QueryClientProvider` (`retry` só para `ErroHttp.transitorio`, até 2) + `RouterProvider(createBrowserRouter(criarRotas(contexto)))`.
  - `export interface ContextoDaCasca { clienteDeConsulta: QueryClient; tokenDoLink: string | null }` e `export function criarRotas(contexto: ContextoDaCasca): RouteObject[]` (layout `LayoutDeConta` + `*` → `NaoEncontrada`; cada tela futura entra com `lazy`).
  - `export const CAMINHOS_DA_CASCA` (as 9 rotas acima, `as const`).
  - Java: `static List<String> Estaticos.jsInicial()` (extraído do `TamanhoDoJsInicialTest`).

- [ ] **Passo 1: testes que falham**
  - `despachar.test.ts`: `raizAbreOTerracoComOConvite` (`/` + `#convite=abc` → `iniciarTerraco(raiz, "abc")`, `carregarCasca` não chamada); `indexHtmlEhTerraco`; `outraRotaCarregaACascaSemTocarNoFragmento` (`/entrar/confirmar#t=xyz` → `carregarCasca` chamada, `iniciarTerraco` e `replaceState` não chamados, `location.hash === "#t=xyz"`); `outraRotaLimpaOEsqueleto` (`raiz.childElementCount === 0`).
  - `montar.test.tsx`: `rotaDesconhecidaMostraNaoEncontrada` (heading `Página não encontrada`, link `Voltar ao terraço` com `href="/"`); `tituloDaAbaSegueATela` (`document.title === "Página não encontrada · Duora"`, via `<title>` do React 19).
  - `ChunksDoFrontTest.cascaDeContaForaDoJsInicial`: exatamente um `app/casca-de-conta-*.js` e nenhum item de `Estaticos.jsInicial()` contém `casca-de-conta`.
- [ ] **Passo 2:** `$PNPM test src/despachar.test.ts src/rotas/` → FAIL; `./mvnw -q test -Dtest=ChunksDoFrontTest` → FAIL (chunk inexistente).
- [ ] **Passo 3: implementar.** `pnpm add` de `react-router` 7.x e `@tanstack/react-query` 5.x com versão exata (sem `^`), conferidas no Context7 antes. Grupo do Vite: `{ name: "casca-de-conta", test: /node_modules\/(react-router|@tanstack)\// }` ao lado do `polling`. `LayoutDeConta`: `<main>` com os tokens de `tokens.css`, coluna única de até 40ch, `Outlet`; sem `Moldura`/`Topo` do terraço. Nada de `react-router`/`@tanstack` fora de `src/rotas/**` e `src/telas/**`.
- [ ] **Passo 4:** Vitest e lint → PASS; `./mvnw -q test -Dtest='TamanhoDoJsInicialTest,ChunksDoFrontTest'` → PASS com o JS inicial ≤ 102 400 B (esperado ~94 KB; anotar o número). Se passar do teto, parar e levar ao controlador (contrato:182 manda avaliar preact/compat).
- [ ] **Passo 5:** `git commit -m "feat(front): casca de conta com React Router e TanStack Query sob demanda"`

### Task F3: Spring encaminha as rotas do SPA sem cache

**Files:**
- Create: `src/main/java/bipotheking/net/duora/web/RotasDoSpa.java`, `src/test/java/bipotheking/net/duora/web/RotasDoSpaTest.java`
- Modify: `src/main/java/bipotheking/net/duora/web/CabecalhosDeSeguranca.java` (`no-cache` também em `RotasDoSpa.CAMINHOS`)

**Interfaces:**
- Consumes: P1 — `public final class web.CabecalhosDeSeguranca` com `public static final String CSP` (ordem `HIGHEST_PRECEDENCE + 1`, a F não muda) e `web.MesmaOrigem.ehApi(HttpServletRequest)` já em `web/`.
- Produces: `@Configuration public class RotasDoSpa implements WebMvcConfigurer { public static final List<String> CAMINHOS; public void addViewControllers(ViewControllerRegistry r) }` — cada caminho → `forward:/index.html`. O `linkSobreviveAoPreCarregamento` da C6 usa este encaminhamento.

- [ ] **Passo 1: teste que falha** (`@SpringBootTest @AutoConfigureMockMvc`, como `PaginaInicialTest`)
  - `@ParameterizedTest` `cadaRotaDoSpaEncaminhaParaOIndex` sobre `CAMINHOS`: 200, `forwardedUrl("/index.html")`, `Cache-Control: no-cache`, `Content-Security-Policy` = `CabecalhosDeSeguranca.CSP`.
  - `confirmarComTokenNaQueryNaoTemEfeito`: `GET /entrar/confirmar?t=abc` → 200 encaminhado.
  - `rotaDesconhecidaContinua404`: `GET /nao-existe` → 404.
  - `nenhumaRotaComecaPorApp`: nenhum item de `CAMINHOS` começa por `/app/`.
  - `rotasIguaisAsDoFront`: os literais `"/..."` de `frontend/src/rotas/caminhosDaCasca.ts` (lidos do disco) = `CAMINHOS` como conjunto.
- [ ] **Passo 2:** `./mvnw -q test -Dtest=RotasDoSpaTest` → FAIL (404).
- [ ] **Passo 3: implementar** `RotasDoSpa` e a união `SEMPRE_REVALIDADOS ∪ CAMINHOS` em `CabecalhosDeSeguranca`.
- [ ] **Passo 4:** `./mvnw -q test -Dtest='RotasDoSpaTest,SalaApiTest,PaginaInicialTest'` → PASS.
- [ ] **Passo 5:** `git commit -m "feat(web): rotas do SPA servidas pelo Spring sem cache"`

### Task F4: Estado da conta e link no topo do terraço

**Files:**
- Create: `frontend/src/api/estadoDaConta.ts` (+`.test.ts`), `frontend/src/componentes/LinkDaConta.tsx` (+`.test.tsx`)
- Modify: `frontend/src/componentes/Topo.tsx` (+`Topo.test.tsx`)

**Interfaces:**
- Consumes: C4 — `GET /api/conta` (tabela). F1 — `requisitarHttp`, `ErroHttp`.
- Produces:
  - `export type EstadoDaConta = "PENDENTE" | "AGUARDANDO_VERIFICACAO" | "ATIVA"`; `export interface Conta { estado: EstadoDaConta; cadastroConcluido: boolean; verificada: boolean }`.
  - `export function obterConta(): Promise<Conta | null>` — 200 → `Conta`; 204 → `null` (anônimo); qualquer outro status lança `ErroHttp`. Nunca redireciona.
  - `export function LinkDaConta({ obter }: { obter?: () => Promise<Conta | null> }): ReactElement | null`.

- [ ] **Passo 1: testes que falham**
  - `estadoDaConta.test.ts`: `contaAtivaDevolveOsCampos`; `semConteudo204EhAnonimo` (→ `null`, `location.assign` nunca chamado); `erro401Lanca` (a rota nunca devolve 401; se vier, é erro e o link some); `erro500Lanca`.
  - `LinkDaConta.test.tsx`: `anonimoVeEntrar` (link `Entrar`, `href="/entrar"`); `comContaVeMinhaConta` (link `Minha conta`, `href="/conta"`); `erroNaoMostraLink` (nenhum link); `antesDaRespostaNaoMostraLink`.
  - `Topo.test.tsx`: `linkDaContaApareceSoSemOSair` (com `mostrarSair=false` há o link; com `mostrarSair=true` não há: a cena em montagem fica como está e o topo do celular não quebra linha).
- [ ] **Passo 2:** `$PNPM test src/api/estadoDaConta.test.ts src/componentes/` → FAIL.
- [ ] **Passo 3: implementar** com `fetch` simples (sem Query: o `/` não carrega a casca). Navegação para a casca por `<a href>` (página inteira). Skills de tela (convenções).
- [ ] **Passo 4:** Vitest e lint → PASS; JS inicial medido. E2e do terraço sem mudar asserções, nos três navegadores: `PrimeiraCargaIT`, `LayoutCelularIT`, `AcessibilidadeLayoutIT`, `AcessibilidadeFocoIT`, `AxeIT`. O `PrimeiraCargaIT` prova o 204: nenhuma resposta ≥ 400 nem erro no console. `VisualIT` + `ComparacaoVisualTest` contra `.claude/handoff/visual-1a/antes`: a única diferença aceita é o link no topo da entrada; então **atualizar a referência protegida** com a captura nova (D-F5 decidiu o link) e registrar no relatório da task que ela vai no pacote de veto do dono.
- [ ] **Passo 5:** `git commit -m "feat(front): estado da conta e link no topo do terraço"`

### Task F5: Widget do Turnstile e CSP

**Files:**
- Create: `frontend/src/componentes/Turnstile.tsx` (+`.test.tsx`), `frontend/src/api/conta.ts` (+`.test.ts`), `src/test/java/bipotheking/net/duora/web/CspTest.java`
- Modify: `src/main/java/bipotheking/net/duora/web/CabecalhosDeSeguranca.java` (`CSP`; `ROTAS_COM_TURNSTILE` e `Referrer-Policy` por rota só se o passo 3 exigir)

**Interfaces:**
- Consumes: C5 — `GET /api/conta/configuracao`. P1 — `CabecalhosDeSeguranca` em `web/`.
- Produces:
  - `export interface ApiTurnstile { render(el: HTMLElement, opcoes: OpcoesDoWidget): string; reset(id: string): void; remove(id: string): void }`; `OpcoesDoWidget = { sitekey: string; callback: (token: string) => void; "expired-callback": () => void; "error-callback": () => void; language: "pt-br"; theme: "dark" }`.
  - `export function criarCarregadorDoTurnstile(documento: Document): () => Promise<ApiTurnstile>` (um `<script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit" async>` por documento) e `export const carregarTurnstile`.
  - `export interface ControleDoTurnstile { reiniciar(): void }`; `export function Turnstile(props: { chaveDoSite: string; aoObterToken: (t: string) => void; aoExpirar: () => void; aoFalhar: () => void; carregar?: () => Promise<ApiTurnstile>; ref?: Ref<ControleDoTurnstile> }): ReactElement`.
  - `api/conta.ts`: `export function obterConfiguracao(): Promise<{ chaveDoSiteTurnstile: string }>` (o módulo cresce nas F6–F9; só a casca o importa).
  - CSP = a atual + `script-src 'self' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com`.

- [ ] **Passo 1: testes que falham**
  - `Turnstile.test.tsx` (carregador falso): `renderizaComChaveIdiomaETema`; `tokenChegaPeloCallback`; `expiradoAvisa`; `erroAvisa`; `reiniciarChamaReset`; `desmontarChamaRemove`; `scriptCarregaUmaVez` (dois `carregar()` → um `<script>` com `render=explicit`).
  - `conta.test.ts`: `configuracaoDevolveAChave`.
  - `CspTest` (MockMvc em `/entrar` e `/`): contém `script-src 'self' https://challenges.cloudflare.com` e `frame-src https://challenges.cloudflare.com`; mantém `default-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`; não contém `unsafe-`.
- [ ] **Passo 2:** `$PNPM test src/componentes/Turnstile.test.tsx src/api/conta.test.ts` e `./mvnw -q test -Dtest=CspTest` → FAIL.
- [ ] **Passo 3: conferir no Context7 (Turnstile)** se o widget funciona com `Referrer-Policy: no-referrer` e sem `style-src`. Se `no-referrer` não funcionar: trocar **só** nas rotas que carregam o widget (`/entrar` e `/denunciar`, constante `CabecalhosDeSeguranca.ROTAS_COM_TURNSTILE`) pela política mínima que funcione (provável `strict-origin`), e acrescentar ao `CspTest` `referrerMinimoSoNasRotasDoWidget` (`/entrar` com a política nova; `/`, `/conta` e `/api/conta` continuam `no-referrer`). Se faltar `style-src`, levar ao controlador.
- [ ] **Passo 3b: implementar** `Turnstile`, `criarCarregadorDoTurnstile`, `obterConfiguracao` e a `CSP`.
- [ ] **Passo 4:** Vitest, lint e `./mvnw -q test -Dtest='CspTest,RotasDoSpaTest,SalaApiTest'` → PASS.
- [ ] **Passo 5:** `git commit -m "feat(front): widget do turnstile e csp"`

### Task F6: Tela Entrar

**Files:**
- Create: `frontend/src/telas/conta/Entrar.tsx` (+`.test.tsx`), `frontend/src/telas/conta/textos.ts` (+`.test.ts`)
- Modify: `frontend/src/api/conta.ts` (+teste), `frontend/src/rotas/rotas.tsx` (`/entrar`, `lazy`)

**Interfaces:**
- Consumes: C5 — `POST /api/conta/entrar`. F1, F5.
- Produces: `export function pedirLink(email: string, tokenVerificacao: string, chave: string): Promise<{ mensagem: string }>`; `export function textoDeEspera(segundos: number): string` (`Math.max(1, Math.ceil(s / 60))`; 1 → `Muitas tentativas. Tente de novo em 1 minuto.`, n → `… em {n} minutos.`).
- Textos: título `Entrar no Duora`; rótulo `Seu e-mail` (`type=email`, `autocomplete=email`); botão `Enviar link`; motivo do `aria-disabled`: `Aguarde a verificação de segurança acima.`; falha do widget: `Não conseguimos carregar a verificação de segurança.` + botão `Tentar de novo`; sucesso = `mensagem` do servidor, num `role="status"`.

- [ ] **Passo 1: testes que falham** (`fetch` e carregador do Turnstile falsos)
  - `enviaEmailTokenEChave`: corpo `{email, tokenVerificacao}` e `Idempotency-Key` presente.
  - `semTokenBotaoIndisponivelComMotivo`: `aria-disabled="true"` e `aria-describedby` aponta o motivo; clique não chama `fetch`.
  - `tokenExpiradoVoltaAIndisponivel`.
  - `repeticaoAutomaticaReusaChaveEToken`: 1ª resposta 409 `REQUISICAO_EM_ANDAMENTO` (`Retry-After: 1`) ou falha de rede → uma 2ª chamada com a mesma chave e o mesmo token; nunca uma 3ª.
  - `respostaFinalReiniciaOWidget`: depois de 202, 422 ou 429 → `reset` chamado e chave nova no próximo envio.
  - `sucessoMostraAMensagemDoServidor`; `erro422MostraAMensagemDoServidor`.
  - `retryAfter60Mostra1Minuto`, `retryAfter61Mostra2Minutos` (`textos.test.ts`) e `limiteMostraOsMinutos` (tela).
- [ ] **Passo 2:** `$PNPM test src/telas/conta/` → FAIL.
- [ ] **Passo 3: implementar** com `useMutation`. Skills de tela (convenções).
- [ ] **Passo 4:** Vitest e lint → PASS; JS inicial sem mudança.
- [ ] **Passo 5:** `git commit -m "feat(front): tela entrar com link mágico"`

### Task F7: Confirmar entrada pelo link

**Files:**
- Create: `frontend/src/rotas/tokenDoLink.ts` (+`.test.ts`), `frontend/src/rotas/consultas.ts`, `frontend/src/telas/conta/ConfirmarEntrada.tsx` (+`.test.tsx`)
- Modify: `frontend/src/rotas/montar.tsx` (lê o token uma vez), `frontend/src/rotas/rotas.tsx` (`/entrar/confirmar`), `frontend/src/api/conta.ts` (+teste)

**Interfaces:**
- Consumes: C6 — `POST /api/conta/confirmar`; F4 — `obterConta`; F2 — `ContextoDaCasca.tokenDoLink`.
- Produces:
  - `export function lerTokenDoLink(local: Location, historia: History): string | null` — lê `t` do fragmento e o tira da barra com `replaceState(null, "", pathname + search)`. Chamado **uma vez** em `montarCasca`, fora do React; o token chega à tela por prop. `convite.ts` e `despachar` não mudam.
  - `export function confirmarEntrada(token: string): Promise<{ cadastroConcluido: boolean }>`.
  - `export const consultaDaConta = queryOptions({ queryKey: ["conta"], queryFn: obterConta })` em `rotas/consultas.ts` (a F8 e a F9 reaproveitam).
  - `ConfirmarEntrada({ token, navegarParaPagina = (url) => location.assign(url) })`.
- Erros tratados pelo `codigo` (conforme o contrato da seção C): `LINK_INVALIDO` → mensagem do servidor + `Pedir novo link`; os códigos de conta indisponível → mensagem do servidor, sem `Pedir novo link`; `LIMITE_DE_TAXA` → `textoDeEspera`.
- Destino depois do 200: `cadastroConcluido: true` → `navegarParaPagina("/")`; `false` → invalida `consultaDaConta` (`["conta"]`), lê `GET /api/conta`: `PENDENTE` → `/cadastro`, `AGUARDANDO_VERIFICACAO` → `/aguardando` (navegação do roteador).
- Textos: título `Confirmar entrada`; botão `Entrar` (o POST só sai no clique, então leitor que pré-abre o link não o gasta); sem token: `Não encontramos o código do link. Abra o link do e-mail de novo ou peça um novo.`; `LINK_INVALIDO`: mensagem do servidor + link `Pedir novo link` (`/entrar`).

- [ ] **Passo 1: testes que falham**
  - `tokenDoLink.test.ts`: `leOTokenELimpaAUrl` (`#t=abc` → `"abc"`, `replaceState` com `/entrar/confirmar`); `semFragmentoDevolveNull`.
  - `montar.test.tsx`: `tokenSobreviveAoStrictMode` (em `/entrar/confirmar#t=abc`, a tela recebe `"abc"` mesmo com a dupla renderização, e `location.hash === ""`).
  - `ConfirmarEntrada.test.tsx`: `naoPostaSemClique`; `cliqueEnviaOToken`; `pendenteVaiParaCadastro`; `aguardandoVaiParaAguardando`; `contaConcluidaVaiParaOTerraco` (`navegarParaPagina("/")`); `linkInvalidoOferecePedirNovo`; `contaIndisponivelMostraAMensagemSemPedirNovo` (código conforme o contrato da seção C); `semTokenExplica`.
- [ ] **Passo 2:** `$PNPM test src/rotas/ src/telas/conta/ConfirmarEntrada.test.tsx` → FAIL.
- [ ] **Passo 3: implementar.** Skills de tela (convenções).
- [ ] **Passo 4:** Vitest e lint → PASS.
- [ ] **Passo 5:** `git commit -m "feat(front): confirmar entrada pelo link sem perder o token"`

### Task F8: Cadastro e tela "aguardando verificação"

**Files:**
- Create: `frontend/src/rotas/guardas.ts` (+`guardas.test.ts`), `frontend/src/telas/conta/{Cadastro,Aguardando}.tsx` (+testes), `frontend/src/api/legal.ts` (+`.test.ts`)
- Modify: `frontend/src/rotas/rotas.tsx` (`/cadastro`, `/aguardando`), `frontend/src/api/conta.ts` (+teste)

**Interfaces:**
- Consumes: C7 — `POST /api/conta/cadastro`; L1 — `GET /api/legal/versoes`; F4 — `obterConta`; F7 — `consultaDaConta`.
- Produces:
  - `export async function exigirConta(cliente: QueryClient, aceitos: readonly EstadoDaConta[]): Promise<Conta>` — `null` → `redirect("/entrar")`; estado fora de `aceitos` → `PENDENTE` `/cadastro`, `AGUARDANDO_VERIFICACAO` `/aguardando`, `ATIVA` `/conta`. O anônimo (204) só redireciona aqui, nos loaders das rotas que exigem conta.
  - `export interface DadosDoCadastro { dataDeNascimento: string; versaoTermos: string; versaoPrivacidade: string; aceito: true }`; `export function concluirCadastro(d: DadosDoCadastro): Promise<{ cadastroConcluido: false; proximoPasso: "VERIFICACAO" }>`.
  - `export interface VersoesLegais { termos: { versao: string }; privacidade: { versao: string }; contato: string; encarregado: { nome: string; email: string } }`; `export function obterVersoesLegais(): Promise<VersoesLegais>`.
- Textos **Cadastro**: título `Falta pouco`; rótulo `Data de nascimento` (`type=date`, `max` = hoje em São Paulo), ajuda `Não dá para mudar depois.`; parágrafo `Leia os Termos de uso e a Política de privacidade.` com os dois links (nova aba, `rel="noopener noreferrer"`) **fora** do `<label>`; caixa `Li e aceito os Termos de uso e a Política de privacidade`; botão `Concluir cadastro` (sempre disponível; sem data: `Informe sua data de nascimento.`; sem aceite: `Marque a caixa para aceitar os termos.`); `MENOR_DE_IDADE`: `O Duora é só para maiores de 18 anos. Não criamos a conta.` + link `Voltar ao terraço`, foco no texto.
- Textos **Aguardando** (`/aguardando`, aceita só `AGUARDANDO_VERIFICACAO`): título `Cadastro recebido`; `Falta confirmar sua idade. Essa etapa chega em breve.`; link `Minha conta`. Sem flag que pule a verificação (D-A).

- [ ] **Passo 1: testes que falham**
  - `guardas.test.ts`: `anonimoVaiParaEntrar`; `pendenteForaDoCadastroVaiParaCadastro`; `aguardandoNoCadastroVaiParaAguardando`; `estadoAceitoDevolveAConta`.
  - `Cadastro.test.tsx`: `enviaAsVersoesDoServidor` (versões de `/api/legal/versoes` no corpo, `aceito: true`); `semAceiteNaoEnvia`; `cadastroConcluidoLevaAAguardando` (rota `/aguardando`, nunca `location.assign`); `menorMostraRecusaENaoFicaLogado` (invalida `["conta"]`); `naoAutenticadoLevaAEntrar` (401 na mutação → `/entrar`, uma vez); `linksDosTermosForaDoRotulo`.
  - `Aguardando.test.tsx`: `mostraOProximoPasso`.
  - `legal.test.ts`: `versoesTrazemContatoEEncarregado`.
- [ ] **Passo 2:** `$PNPM test src/rotas/guardas.test.ts src/telas/conta/ src/api/legal.test.ts` → FAIL.
- [ ] **Passo 3: implementar.** Skills de tela (convenções).
- [ ] **Passo 4:** Vitest e lint → PASS.
- [ ] **Passo 5:** `git commit -m "feat(front): cadastro com nascimento e aceite; tela aguardando verificação"`

### Task L2: Documentos legais, front (Etapa 9 T1 front)

**Files:**
- Create: `frontend/src/telas/legal/DocumentoLegalPagina.tsx` (+`.test.tsx`), `frontend/src/componentes/Rodape.tsx` (+`.test.tsx`), `src/test/java/bipotheking/net/duora/e2e/LegalIT.java`
- Modify: `frontend/src/api/legal.ts` (+teste), `frontend/src/rotas/{rotas.tsx,LayoutDeConta.tsx}`, `frontend/src/componentes/Entrada.tsx` (+teste), `frontend/src/componentes/Painel.test.tsx` (`montagemSemRodape`)

**Interfaces:**
- Consumes: L1 — `GET /api/legal/{termos|privacidade}`, `GET /api/legal/versoes`; F8 — `obterVersoesLegais`.
- Produces:
  - `export type DocumentoLegal = "termos" | "privacidade"`; `export function obterDocumentoLegal(d: DocumentoLegal): Promise<{ versao: string; html: string }>`.
  - `export function Rodape({ obterContato }: { obterContato?: () => Promise<string> }): ReactElement` — links `Termos de uso` (`/termos`), `Privacidade` (`/privacidade`) e `Contato` (`mailto:` do `contato`, só depois de obtido). Busca por `fetch` simples: também roda no `/`.
  - Rodapé na tela de entrada do terraço (`Entrada.tsx`) e no `LayoutDeConta`; **nunca** sobre a cena em montagem (transversal §4).
- Textos: títulos `Termos de uso` e `Política de privacidade`; `Versão de 22/11/2026` para `versao: "2026-11-22"`. O HTML entra por `dangerouslySetInnerHTML` só a partir de `/api/legal/*`.

- [ ] **Passo 1: testes que falham**
  - `DocumentoLegalPagina.test.tsx`: `mostraTituloEVersao`; `documentoInexistenteMostraNaoEncontrada`.
  - `Rodape.test.tsx`: `temOsTresLinks` (`Contato` com `href="mailto:teste@exemplo.com"`); `semContatoAindaTemTermosEPrivacidade`.
  - `Entrada.test.tsx`: `entradaMostraORodape`. `Painel.test.tsx` (montagem) continua sem link `Termos de uso`: acrescentar `montagemSemRodape`.
  - `LegalIT.rodapeAbreTermosEPrivacidade`: do `/`, clica `Termos de uso` → heading `Termos de uso`; idem `Privacidade`; `Axe.assertSemViolacoes(page, "termos")`.
- [ ] **Passo 2:** `$PNPM test src/telas/legal/ src/componentes/` → FAIL; `LegalIT` (chromium) → FAIL.
- [ ] **Passo 3: implementar.** Skills de tela (convenções).
- [ ] **Passo 4:** Vitest, lint, JS inicial medido; `LegalIT`, `LayoutCelularIT`, `AcessibilidadeLayoutIT`, `VerOQueOOutroFezIT`, `PrimeiraCargaIT` e `AxeIT` verdes nos três navegadores, sem mudar asserções. `VisualIT` + `ComparacaoVisualTest` contra a referência protegida (já atualizada pela F4): a única diferença aceita é o rodapé na entrada; então **atualizar a referência protegida** com a captura nova e registrar no relatório da task que ela vai no pacote de veto do dono.
- [ ] **Passo 5:** `git commit -m "feat(legal): página dos documentos e rodapé fora da cena"`

### Task F9: Minha conta (sair e excluir; sem selo)

**Files:**
- Create: `frontend/src/telas/conta/MinhaConta.tsx` (+`.test.tsx`), `frontend/src/componentes/DialogoDeConfirmacao.tsx` (+`.test.tsx`)
- Modify: `frontend/src/rotas/rotas.tsx` (`/conta`), `frontend/src/api/conta.ts` (+teste), `frontend/src/teste/configurar.ts` (só se o jsdom não tiver `showModal`/`close`)

**Interfaces:**
- Consumes: C4 — `POST /api/conta/sair`; C8 — `POST /api/conta/exclusao`; F8 — `exigirConta(cliente, ["AGUARDANDO_VERIFICACAO", "ATIVA"])`.
- Produces: `export function sairDaConta(): Promise<void>`; `export function excluirConta(): Promise<void>` (envia `{confirmo: true}`); `export function DialogoDeConfirmacao(props: { aberto: boolean; titulo: string; texto: string; acao: string; aoConfirmar: () => void; aoCancelar: () => void; enviando: boolean }): ReactElement` (`<dialog>` + `showModal()`, foco inicial em `Cancelar`, Esc cancela, foco volta ao botão de origem).
- Textos: título `Minha conta`; com `AGUARDANDO_VERIFICACAO`: `Falta confirmar sua idade. Essa etapa chega em breve.`; botão `Sair da conta`; seção `Excluir conta` com `Apaga seus dados pessoais. Não dá para desfazer.` e botão `Excluir conta`; diálogo `Excluir sua conta?` / `Apagamos seus dados pessoais agora. Não dá para desfazer.` / `Excluir conta` e `Cancelar` (um par só: botão + diálogo, sem caixa; frontend.md plano:602). Depois de excluir: título `Conta excluída` e link `Voltar ao terraço`. Sem selo (`verificada` é da 2b).

- [ ] **Passo 1: testes que falham**
  - `MinhaConta.test.tsx`: `sairChamaAApiEVaiParaEntrar` (e limpa `["conta"]`); `excluirAbreODialogoSemChamarAApi`; `confirmarEnviaConfirmo` (corpo exato `{"confirmo":true}`, depois mostra `Conta excluída`); `cancelarNaoExclui`; `aguardandoMostraOProximoPasso`.
  - `DialogoDeConfirmacao.test.tsx`: `focoIniciaEmCancelar`; `escCancela`; `focoVoltaAoBotaoDeOrigem`; `enviandoDeixaAcaoIndisponivel` (`aria-disabled`).
- [ ] **Passo 2:** `$PNPM test src/telas/conta/MinhaConta.test.tsx src/componentes/DialogoDeConfirmacao.test.tsx` → FAIL.
- [ ] **Passo 3: implementar.** Skills de tela (convenções).
- [ ] **Passo 4:** Vitest e lint → PASS.
- [ ] **Passo 5:** `git commit -m "feat(front): minha conta com sair e excluir"`

### Task F10: E2e da jornada de conta

**Files:**
- Create: `src/test/java/bipotheking/net/duora/e2e/{ContaE2eBase,ContaPagina,Contas,TurnstileFalso,ContaIT,RajadaDeEntradaIT}.java`

**Interfaces:**
- Consumes: P8 — `notificacao.infra.DespachanteDaOutbox` com `public int despacharAgora()` (no e2e o agendador fica ligado, `duora.outbox.intervalo=PT0.2S`, pelo failsafe da P2); P9 — `public final class notificacao.infra.EmailNoLog` com `public Optional<MensagemDeEmail> ultimaPara(String email)`, `public record notificacao.MensagemDeEmail(String para, String assunto, String texto, String html)`; C2 — no back de teste quem responde é o `VerificacaoHumanaDeTeste` (`@Primary`, sem rede): aceita todo token menos `falha`, então o `XXXX.DUMMY.TOKEN.XXXX` do `TurnstileFalso` passa; C3/C4 — `duora.sessao.cookie-seguro`, `@Component public class ContasDeTeste { public ContaDeTeste criar(EstadoConta e, LocalDate n); public ContaDeTeste criarAtiva() }`, `public record ContaDeTeste(ContaId id, String cookie)`; P6/P2 — `duora.limites.multiplicador` (o failsafe já põe `1000`; a F não repete valores de regra); C5–C7 reais.
- Produces:
  - `abstract class ContaE2eBase` (`@SpringBootTest(RANDOM_PORT)`, `@DirtiesContext(AFTER_CLASS)`, `@ExtendWith(NavegadorExtension.class)`) com `@DynamicPropertySource`: só `duora.sessao.cookie-seguro = !"webkit".equals(Navegador.NOME)` (D-F4); `protected String raiz()`, `protected String linkRecebido(String email)` (`despacharAgora()` e espera até 5 s por `ultimaPara`, depois extrai `/entrar/confirmar#t=…`; a reserva da outbox impede envio duplo com o agendador).
  - `final class TurnstileFalso { static void instalar(Page page) }` — `page.route("https://challenges.cloudflare.com/turnstile/v0/api.js*")` serve um `window.turnstile` cujo `render`/`reset` emitem `XXXX.DUMMY.TOKEN.XXXX` (D-F3; o SW já fica bloqueado nos contextos).
  - `final class ContaPagina { ContaPagina(Page page); static ContaPagina de(TerracoPagina t); ContaPagina abrir(String url); void pedirLink(String email); void abrirLink(String link); void confirmarEntrada(); void concluirCadastro(LocalDate nascimento); ContaPagina cadastrarContaNova(String raiz, String email, LocalDate nascimento, Function<String, String> link); Page page() }`.
  - `final class Contas { static TerracoPagina comConta(TerracoPagina pagina, ContaDeTeste conta, String raiz) }` (`context().addCookies`; consumido pela Etapa 4).

- [ ] **Passo 1: testes que falham** em `ContaIT extends ContaE2eBase` (e-mails `conta-<uuid>@exemplo.com`)
  - `cadastroCompletoNoCelular`: `novoCelular()` + `TurnstileFalso`; entrar; link; `Entrar`; nascimento 1990-05-10; heading `Cadastro recebido`.
  - `menorRecusadoNoCadastro`: nascimento 2010-01-01 → `O Duora é só para maiores de 18 anos.`; abrir `/conta` cai em `/entrar`.
  - `RajadaDeEntradaIT extends ContaE2eBase` (classe própria, porque sobrescreve com `@DynamicPropertySource` `duora.limites.multiplicador=1`): `rajadaMostraAviso` — quatro pedidos do mesmo e-mail → texto `Muitas tentativas. Tente de novo em`.
  - `linkUsadoOferecePedirNovo`: o mesmo link aberto de novo → `Pedir novo link`.
  - `sessaoSobreviveARecarga`: depois de confirmar, `context().cookies()` tem `duora_sessao`; recarregar `/aguardando` mantém `Cadastro recebido`. No WebKit a flag está desligada e o teste não prova `Secure` (quem prova é a API da C4).
  - `topoMostraMinhaContaDepoisDeEntrar`: com sessão, o `/` mostra o link `Minha conta`; sem sessão, `Entrar`.
- [ ] **Passo 2:** rodar `ContaIT` e `RajadaDeEntradaIT` (chromium) → FAIL.
- [ ] **Passo 3: implementar** os helpers acima (sem mudar `NavegadorExtension`, `TerracoPagina` nem `Axe`).
- [ ] **Passo 4:** `ContaIT` e `RajadaDeEntradaIT` verdes em chromium, firefox e webkit.
- [ ] **Passo 5:** `git commit -m "test(e2e): jornada de entrada e cadastro nos três navegadores"`

### Task F11: E2e de Minha conta e prova no Android

**Files:**
- Create: `src/test/java/bipotheking/net/duora/e2e/MinhaContaIT.java`, `scripts/android/conta-check.mjs`, `scripts/android/conta-check.test.mjs`

**Interfaces:**
- Consumes: F10 — `ContaE2eBase`, `ContaPagina`, `TurnstileFalso`; C8 real.
- Produces: `export function ultimoLinkDoLog(texto: string): string | null` (último `http…/entrar/confirmar#t=<base64url>` do log de texto do `EmailNoLog`); `export function passosDaRodada(args): string[]` (padrão do `pwa-check.mjs`).

- [ ] **Passo 1: testes que falham**
  - `MinhaContaIT`: `sairEncerraSessao` (`Sair da conta` → `/entrar`; abrir `/conta` volta a `/entrar`); `excluirApagaConta` (diálogo → `Excluir conta` → `Conta excluída`; entrar de novo com o mesmo e-mail cai em `/cadastro`); `cancelarMantemAConta`.
  - `conta-check.test.mjs`: `ultimoLinkVenceOAnterior`; `semLinkDevolveNull`; `linkSemTokenIgnorado`; `passosParamNaTelaAguardando` (o último passo captura `aguardando`).
- [ ] **Passo 2:** `node --test 'scripts/android/conta-check.test.mjs'` → FAIL; `MinhaContaIT` (chromium) → FAIL.
- [ ] **Passo 3: implementar** o script: `adb reverse`, Chrome do emulador e CDP como no `pwa-check.mjs`; argumentos `--porta 8080 --log <arquivo do spring-boot:run>`; widget **real** do Turnstile (chaves de teste do dev), então prova a CSP no Chrome; falha a cada evento `securitypolicyviolation`; capturas `entrar`, `cadastro`, `aguardando` e `minha-conta` em `target/android/`; para na tela `Cadastro recebido`.
- [ ] **Passo 4:** `MinhaContaIT` verde nos três navegadores; `node --test 'scripts/android/*.test.mjs'` verde; `node scripts/android/conta-check.mjs --porta 8080 --log target/app.log` sai 0 (agente sobe e desce o emulador e o app; `docker compose down` no fim).
- [ ] **Passo 5:** `git commit -m "test(e2e): minha conta e prova do cadastro no android"`

### Task F12: Denúncia pública e contato do encarregado (telas)

**Files:**
- Create: `frontend/src/telas/legal/{DenunciarPublico,Encarregado}.tsx` (+testes), `frontend/src/api/denuncia.ts` (+`.test.ts`)
- Modify: `frontend/src/rotas/rotas.tsx` (`/denunciar`, `/encarregado`, sem guarda), `frontend/src/componentes/Rodape.tsx` (+teste)

**Interfaces:**
- Consumes: C12 — `POST /api/denuncias-publicas`; L1 — `encarregado` em `/api/legal/versoes`; F5 — `Turnstile`, `obterConfiguracao`; F8 — `obterVersoesLegais`.
- Produces: `export type MotivoDaDenuncia = "PARECE_MENOR" | "ABUSO_INFANTIL" | "IMAGEM_INTIMA" | "ASSEDIO" | "GOLPE" | "OUTRO"`; `export interface DenunciaPublica { motivo: MotivoDaDenuncia; descricao: string; referencia?: string; emailContato?: string; tokenVerificacao: string }`; `export function enviarDenuncia(d: DenunciaPublica): Promise<{ mensagem: string }>`. Rodapé ganha `Denunciar` (`/denunciar`) e `Encarregado de dados` (`/encarregado`).
- Textos `/denunciar` (plano:801): título `Denunciar um problema`; `Use este canal se você não tem conta ou não consegue denunciar pelo app. Em caso de risco imediato, ligue 190.`; grupo `O que aconteceu` (rádios `Parece menor de idade`, `Abuso ou exploração de criança`, `Imagem íntima sem consentimento`, `Assédio ou ameaça`, `Golpe`, `Outro`); `Conte o que aconteceu` (10–2000, contador); `Apelido ou link, se souber` (≤ 200); `Seu e-mail para resposta (opcional)`; botão `Enviar denúncia` (`aria-disabled` sem token, como na F6); sucesso = `mensagem` do servidor; reset do widget depois de cada resposta.
- Textos `/encarregado`: título `Encarregado de dados`; nome e e-mail (`mailto:`); `Use este contato para pedidos sobre seus dados pessoais (acesso, correção, exclusão).`

- [ ] **Passo 1: testes que falham**
  - `DenunciarPublico.test.tsx`: `motivoObrigatorio` (sem motivo não envia e mostra `Escolha o que aconteceu.`); `descricaoCurtaAvisa` (9 caracteres); `enviaCamposOpcionaisSoQuandoPreenchidos`; `sucessoMostraAMensagemDoServidor`; `respostaReiniciaOWidget`.
  - `Encarregado.test.tsx`: `encarregadoMostraNomeEEmail`.
  - `Rodape.test.tsx`: `temDenunciarEEncarregado`.
- [ ] **Passo 2:** `$PNPM test src/telas/legal/ src/api/denuncia.test.ts src/componentes/Rodape.test.tsx` → FAIL.
- [ ] **Passo 3: implementar.** Skills de tela (convenções).
- [ ] **Passo 4:** Vitest, lint e JS inicial (o rodapé está no `/`) → PASS. `VisualIT` + `ComparacaoVisualTest` contra a referência protegida (já atualizada pela L2): a única diferença aceita são os links novos no rodapé da entrada; então **atualizar a referência protegida** com a captura nova e registrar no relatório da task que ela vai no pacote de veto do dono.
- [ ] **Passo 5:** `git commit -m "feat(front): denúncia pública e contato do encarregado"`

### Task F13: E2e da denúncia e axe dos estados novos

**Files:**
- Create: `src/test/java/bipotheking/net/duora/e2e/{DenunciaPublicaIT,AxeContaIT}.java`

**Interfaces:**
- Consumes: F10 — `ContaE2eBase`, `ContaPagina`, `TurnstileFalso`, `Contas.comConta`; C12 e L1 reais; `Axe.assertSemViolacoes(Page, String)`.
- Produces: nada consumido por outras tasks.

- [ ] **Passo 1: testes que falham**
  - `DenunciaPublicaIT`: `denunciarSemConta` (sem cookie, `/denunciar`, motivo `Golpe`, descrição de 20 caracteres → `Recebemos sua denúncia.`); `rodapeLevaADenunciarEEncarregado` (do `/`, `Denunciar` → heading `Denunciar um problema`; `Encarregado de dados` → `encarregado@exemplo.invalid`).
  - `AxeContaIT` (um teste por estado, movimento reduzido; `entrarComLimite` alcança o 429 por `page.route` em `/api/conta/entrar` devolvendo 429 `LIMITE_DE_TAXA` com `Retry-After`, porque o multiplicador 1000 do failsafe não deixa a rajada chegar lá): `entrar`, `entrarComLimite`, `confirmarComLinkInvalido`, `cadastro`, `cadastroComMenorRecusado`, `aguardando`, `minhaConta`, `dialogoDeExclusaoAberto`, `denunciar`, `encarregado`, `paginaNaoEncontrada`. O `AxeIT` não muda.
- [ ] **Passo 2:** rodar os dois ITs (chromium) → FAIL.
- [ ] **Passo 3: implementar** só o que o axe apontar nas telas (correção em `frontend/src/telas/**`, com teste Vitest do defeito).
- [ ] **Passo 4:** `DenunciaPublicaIT` e `AxeContaIT` verdes nos três navegadores; depois o portão da etapa (`duora-etapa`).
- [ ] **Passo 5:** `git commit -m "test(e2e): denúncia sem conta e axe das telas de conta"`

---

### Dependências entre trilhas
| Task F | Precisa na `main` antes |
|---|---|
| F1, F2 | — |
| F3 | P1 (`CabecalhosDeSeguranca` e `MesmaOrigem` em `web/`, `CSP` público) |
| F4 | F3; C4 (`GET /api/conta` pública, 204 sem sessão) |
| F5 | P1, F3 (C5 só para o e2e; o Vitest usa falsos) |
| F6–F9, F12 | escritas contra o contrato com `fetch` falso; o merge espera os portões C da tabela "Ordem e paralelismo" |
| L2 | F8 (cria `api/legal.ts`); L1 para o `LegalIT` |
| F10 | P2 (Testcontainers e failsafe com `duora.limites.multiplicador=1000` e agendador ligado), P6, P8, P9, C2, C4, C5, C6, C7 |
| F11 | F10, C8 |
| F13 | F10, F12, C12, L1 (com `encarregado`) |

Ordem interna: a fila F da tabela "Ordem e paralelismo" (F1 → F2 → F3 → F5 → F4 → F6 → F7 → F8 → L2 → F9 → F10 → F11 → F12 → F13).

### Riscos da trilha
1. **`PrimeiraCargaIT`:** reprova qualquer resposta ≥ 400 ou erro no console na primeira carga do `/`. Se a C devolver outra coisa que não 204 em `GET /api/conta` sem sessão, a F4 não fecha.
2. **Orçamento:** folga de 9 435 B; F2, F4, L2 e F12 tocam o `/`. Estourou, para e volta ao controlador (preact/compat).
3. **Turnstile real só no Android:** o e2e usa o falso; a CSP e o widget reais só são provados pelo `conta-check.mjs` (F11). No back de teste, o `VerificacaoHumanaDeTeste` (C2, `@Primary` no classpath de teste) responde sem rede, também no e2e.
4. **WebKit sem `Secure`:** com a flag desligada, o e2e do WebKit não prova o cookie `Secure` (fica com a API da C4 e a Etapa 10).
5. **Referência visual protegida:** F4 (link do topo), L2 (rodapé na entrada) e F12 (links novos no rodapé) atualizam a referência do `VisualIT`; as capturas novas vão no pacote de veto do dono.

## Prova da etapa
- `./mvnw -q verify` verde (com Docker) e `-Pe2e` verde no Chromium, no Firefox e no WebKit, um de cada vez, sob a trava.
- JS inicial ≤ 100 KB gzip, medido pelo build.
- Prova da spec (Etapa 2): cadastro pelo celular até "aguardando verificação" (`conta-check.mjs`, F11, emulador em VM, rodado por agente); menor declarado recusado (`MENOR_DE_IDADE`, conta excluída e e-mail bloqueado: C7 e F10); rajada barrada (`RajadaDeEntradaIT`, F10); `LimitesDeContaNovaTest` (C11), `RegistroDeAcessosTest` (C10), `DenunciaPublicaIT` (F13).
- Terraço anônimo intacto: suíte atual de `room/` e e2e do terraço verdes sem mudar asserções.
- Axe sem violação em toda tela nova (`AxeContaIT`, `LegalIT`).
- Pacote para o dono (veto opcional): capturas novas do `VisualIT` (link de conta no topo, rodapé na entrada e seus links novos) e das telas de conta.

