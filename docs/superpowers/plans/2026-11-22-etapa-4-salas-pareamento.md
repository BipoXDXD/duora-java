# Etapa 4 — Encontros públicos e privados e pareamento: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` (ou `mattpocock-skills:tdd`) e fecham cada task com `superpowers:verification-before-completion`.

**Objetivo:** uma pessoa com conta e perfil escolhe encontro **público** (entra na fila contínua e é pareada com alguém compatível) ou **privado** (gera um convite de uso único e escolhe o cenário), e as duas chegam ao mesmo cenário.

> **Emenda 2026-11-22 (paridade com o mercado):** liquidez visível (tempo de espera estimado e horários com mais gente, Task 11 nova), notificações confiáveis e discretas (Task 8 ajustada: no máximo 1 aviso por dia, nunca de madrugada, texto que não expõe nada na tela bloqueada), conta pausada fora da fila (Tasks 5 e 6) e limite de convites de conta nova (Task 7).

**Arquitetura:**
- **Domínio puro** em `pareamento/dominio/` (sem Spring, JDBC nem Jackson): `Compatibilidade` e `Pareador.parear`, chamados a cada ciclo de 5 s por `sala/CicloDePareamento`.
- **Fila no banco:** `fila_espera` guarda a entrada com um retrato do candidato (idade, preferências, célula). O ciclo retira os candidatos com `SELECT ... FOR UPDATE SKIP LOCKED` numa transação curta, pareia em memória, apaga os pareados e publica os eventos na mesma transação.
- **Tempo real:** um stream SSE por sessão em `GET /api/eventos` (`web/eventos/`). Cada evento durável é uma linha de `evento_conta` gravada **na transação do efeito**; o `id` do SSE é a sequência do banco (`seq`), então `Last-Event-ID` reenvia do banco, inclusive depois de reinício. Sinais efêmeros (voz) não são gravados. ADR 0002 (SSE + POST) é escrita aqui, retroativa.
- **Infra transversal** (Task 0, só se a Etapa 2 não entregou): `outbox` (push e efeitos para outros módulos sem dual write) e `Idempotency-Key`.
- **Ponte com a sala atual:** o encontro ainda é a `Sala` de `room/` (a Etapa 5 a transforma em `encontro/`). As portas `sala.AberturaDeEncontro` e `sala.OcupacaoDeEncontros` são implementadas aqui por `room/PonteDeEncontros`, que entrega o cookie de assento atual a quem provar a sessão. A Etapa 5 troca a implementação sem mudar as portas.
- **Web Push** em `notificacao/`, atrás da porta `EnvioDePush` com o adapter `WebPushInterasoAdapter` (`com.interaso:webpush:1.3.0`), disparado só pela outbox.

**Stack:** Java 25, Spring Boot 4.2.0, Spring Data JDBC/`JdbcClient`, PostgreSQL 18 + Flyway, Testcontainers, Bucket4j, ArchUnit, `com.interaso:webpush:1.3.0`, React 19 + TypeScript + Tailwind, Vitest, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (decisões "Quando", "Distância", "Critério de namoro", "Duração", "Reencontro"; Escolhas "Localização", "Excedente da capacidade", "Foto pendente não bloqueia", "Tempo real"; Etapa 4). Contratos e princípios: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Interfaces consumidas: `…-etapa-2-contas.md`, `…-etapa-3-perfil.md`, `…-etapa-9-termos.md`, `…-etapa-1b-motor-de-cenarios.md`.

## Restrições globais
- Valem os contratos entre etapas, inclusive "Princípios de engenharia", e as restrições das Etapas 0, 1 e 1b: Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; comando pesado um por vez com a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`; `-Xlint:all -Werror`; Spotless; um comportamento por teste; E2E sem skip nos três navegadores; commit de refatoração separado de commit de comportamento.
- Migrações desta etapa: **V50–V69**. Usadas: V50 `fila_espera`, V51 `par_vetado`, V52 `cenario_jogado`, V53 `push_inscricao`, V54 `evento_conta`, V55 `outbox` e V56 `idempotencia` (as duas últimas só se a Etapa 2 não as criou), V57 `espera_concluida` (Task 11).
- Domínio puro em `pareamento/dominio/`: nada de `org.springframework..`, `java.sql..`, `..jdbc..` nem Jackson. A regra entra na suíte ArchUnit da Etapa 2.
- Todo tempo vem do `Clock`; ids pelo `GeradorDeIds` (UUID v7); erros por `ErroDeNegocio` com um `enum CodigoSala implements CodigoDeErro` (corpo `application/problem+json` com `codigo` e `mensagem` = `detail`); limites por `LimiteDeTaxa.consumir(regra, chave)` com `duora.limites.<regra>.capacidade|periodo`.
- Capacidade: **até 10 encontros simultâneos** (`duora.encontros.maximo-simultaneos=10`), contando públicos e privados.
- Ciclo do pareamento: **5 s** (`duora.pareamento.ciclo=5s`). Prazo para o par entrar depois de pareado: **60 s** (`duora.pareamento.prazo-para-entrar=60s`). Presença na fila: sem stream SSE por **60 s**, a pessoa sai (`duora.fila.ausencia-maxima=60s`). No máximo **200** candidatos retirados por ciclo.
- Distância: o servidor de pareamento só vê `CelulaGeohash` (precisão 5) e usa `CelulaGeohash.distanciaKm`. A tela nunca mostra distância exata, só faixas.
- Raio obrigatório para qualquer um dos dois ⇒ filtro; "só preferência" ⇒ apenas ordena.
- Namoro: o gênero de cada um está entre os gêneros que o outro busca, **nos dois sentidos**.
- Exclusões permanentes: bloqueio, banimento, pares conectados (no pareamento público), "não", saída, denúncia, encontro interrompido sem decisão. Registradas em `par_vetado` com chave `(conta_menor, conta_maior)`.
- SSE: buffer de envio **limitado a 100 eventos por stream** (estouro fecha o stream; o cliente volta com `Last-Event-ID` e o resto vem do banco); reenvio de no máximo 500 eventos; campo `retry:` sorteado entre 2000 e 5000 ms por conexão; reconexão manual no front com backoff exponencial e jitter completo (base 1 s, teto 30 s).
- Push: timeout de conexão 5 s e de requisição 10 s; só pela outbox.
- `Idempotency-Key` (8–64 de `[A-Za-z0-9_-]`) aceito em `POST /api/encontros/privados/entrar`.
- Textos em pt-BR, voz ativa, gênero neutro. Textos fixos:
  - estado vazio com avisos: "Ninguém compatível agora. Vamos te avisar quando alguém chegar.";
  - estado vazio sem avisos: "Ninguém compatível agora. Fique nesta tela ou ative os avisos.";
  - capacidade cheia: "Todos os encontros estão ocupados. Você é a {n}ª pessoa na fila. Tempo estimado: cerca de {m} min.";
  - push: título "Duora", corpo "Alguém compatível está esperando agora." (sem apelido, foto ou distância), com `tag` `duora-compativel` (uma notificação substitui a anterior) e sem imagem;
  - liquidez (Task 11): "Espera típica agora: cerca de {m} min." e "Costuma ter mais gente às {h1}h, {h2}h e {h3}h." (só com amostra suficiente; senão, nada).
- **Etiqueta de notificação (pesquisa de segurança e UX, 4.3):** aviso de reengajamento ("alguém compatível") no máximo 1 por conta a cada 24 h e nunca entre 22h e 8h de `America/Sao_Paulo`; aviso de evento real (mensagem, chamada) segue as regras da Etapa 6. Nenhum push falso. A permissão só é pedida por gesto ("Ativar avisos"), e `requestPermission()` com `'default'` é tratado como "ainda não", sem insistir.
- O service worker nunca guarda `/api` e só passa a tratar `push` e `notificationclick` além do que já faz.
- Front: `pnpm --dir frontend exec vitest run <arquivo>` com o Node/pnpm fixados pela 1a.

## Dependências assumidas
Nomes publicados nos planos das Etapas 1b, 2, 3 e 9. Se divergirem na `main`, ajuste os usos aqui, sem mudar o comportamento.
- **1b:** `game.CatalogoDeCenarios.porId(String) -> Optional<Cenario>` e `todos()` (Java puro, bean montado por `room/CenariosConfig`); `Cenario.id()`; `Salas.criar(Apelido, Cenario)`; `Visao.cenario`.
- **2 (`conta/`, `web/`):** `ContaId(UUID valor)`; `@ContaAtual` (padrão `cadastroConcluido = true`; o `ExigeSessao` já dá 401 `NAO_AUTENTICADO` e 403 `CONTA_INDISPONIVEL`); `ConsultaDeContas.porId(ContaId) -> Optional<ContaResumo>` com `estado()` e `OptionalInt idadeEm(LocalDate hoje)`; `AoExcluirConta.excluir(ContaId)`; `LimiteDeTaxa`/`LimiteExcedido`; `ErroDeNegocio(CodigoDeErro, String)`; `GeradorDeIds.novo()`; testes `BancoDeTeste`, `ContasDeTeste.criar(String apelido, LocalDate nascimento) -> ContaDeTeste(ContaId id, String cookie)`, `RelogioDeTeste`, `Participantes.novoComConta(ContaDeTeste)`.
- **3 (`perfil/`):** `Perfis` com `publico`, `preferencias`, `celula`, `boolean prontoParaFila(ContaId)` (falso com a conta pausada) e `boolean pausada(ContaId)`; `PerfilPublico(ContaId conta, String apelido, Optional<String> fotoBorradaUrl, Optional<String> pronomes, boolean verificado)`; `Preferencias(Set<Intencao> intencao, FaixaEtaria faixaEtaria, Raio raio, boolean raioObrigatorio, Optional<Genero> generoProprio, Set<Genero> generosBuscados)` (Etapa 3 Task 6; a Task 9 não a muda); `FaixaEtaria.contem(int)`; `Raio.km() -> OptionalInt`; `enum Genero`; `CelulaGeohash(String valor)` com `double distanciaKm(CelulaGeohash)`; `Localizacoes.consentida(ContaId)` e `Localizacoes.atualizar(ContaId, double, double)`; teste `PerfisDeTeste.definir(ContaId, String apelido, Preferencias, Optional<CelulaGeohash>)`.
- **9 (`retencao/`):** `PoliticaDeRetencao { String nome(); Duration prazo(); int expirar(Instant limite); }`, rodada diariamente pela `ExpiracaoDeDados`.
- **2 (paridade):** `conta.LimitesDeContaNova.consumir(ContaId, TipoDeLimiteNovo.CONVITE)` (429 `LIMITE_DE_CONTA_NOVA`).
- **Se a Etapa 2 já entregou outbox ou idempotência com outros nomes,** use os dela e pule a parte correspondente da Task 0.

## Decisões de planejamento (o dono pode reverter)
- **P1. Web Push: `com.interaso:webpush:1.3.0`** (MIT, publicada em 2026-01, só `kotlin-stdlib`, `HttpClient` do JDK). O Context7 não indexa bibliotecas Java de Web Push; a escolha foi conferida no Maven Central e no README. Descartadas: `nl.martijndwars:web-push:5.1.2` (puxa `httpasyncclient`, `async-http-client` e `bcprov-jdk15on` antigos) e `dev.blanke.webpush:6.1.3` (parada desde 2024-10). Usamos só `WebPush(subject, vapidKeys).getHeaders/getBody/getSubscriptionState`; o envio é o nosso `HttpClient` com timeout. Registrada em ADR.
- **P2. Ordenação:** quem espera há mais tempo é atendido primeiro; o par dele é o candidato compatível que espera há mais **ciclos completos**; empate no mesmo ciclo vai para a menor distância; distância desconhecida por último; depois `entrouEm` e UUID.
- **P3. Célula ausente:** se alguém exige raio e uma das células falta, o par é incompatível.
- **P4. Privado com capacidade cheia:** o convidado recebe 503 `CAPACIDADE_CHEIA` com `Retry-After: 60` e o convite continua valendo; convite aberto não reserva vaga.
- **P5. Pareado ausente:** quem não pedir o assento em 60 s é tirado; o presente volta à fila com o `entrouEm` original; sem veto.
- **P6. Sorteio:** vence o cenário sorteável jogado por menos pessoas do par (0, 1 ou 2), empate pelo `RandomGenerator` injetado. `duora.cenarios.sorteaveis=terraco` hoje; a 9b libera cada cenário depois do playtest.
- **P7. Push "alguém compatível":** a quem tem inscrição, **não** está na fila, não está pausado, é compatível nos dois sentidos com alguém que já esperou um ciclo inteiro e não recebeu aviso nas últimas **24 h**; nunca entre 22h e 8h de São Paulo; até 50 por ciclo.
- **P8. Retrato do candidato na entrada:** a fila grava idade, preferências e célula no momento da entrada; mudar preferências vale na próxima entrada. Evita N+1 a cada ciclo.

## Foco de revisão
1. **Endpoint de push vindo do cliente (SSRF):** só `https` e host numa allowlist de serviços de push; `http://`, `127.0.0.1`, `[::1]`, `169.254.169.254`, `10.0.0.1`, IP decimal, userinfo, porta explícita e sufixo enganoso ⇒ 422 sem gravar. Teste na Task 8.
2. **Ordem dos eventos de uma conta com transações concorrentes** (duas abas, ciclo + desistência): o `seq` entregue cresce na ordem de commit, e um `Last-Event-ID` nunca pula um evento. Teste na Task 1 (`publicar` toma `pg_advisory_xact_lock` por conta, em ordem de `ContaId`).
3. **Duas abas ou clique duplo em "Entrar na fila":** uma entrada só, posição e `entrouEm` preservados. Teste na Task 5.
4. **Fechar a aba enquanto espera:** sem stream por 60 s, sai da fila; ninguém é pareado com um fantasma. Teste na Task 6.
5. **Dois ciclos ao mesmo tempo** (agendador + chamada manual em teste, ou reinício sobreposto): `SKIP LOCKED` impede que a mesma pessoa entre em dois pares. Teste na Task 6.

---

### Task 0: Outbox e `Idempotency-Key` (só se a Etapa 2 não entregou)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V55__outbox.sql`, `V56__idempotencia.sql`
- Criar: `src/main/java/bipotheking/net/duora/web/outbox/{Outbox,ManipuladorDeOutbox,DespachanteDeOutbox,RetencaoDaOutbox}.java`, `web/idempotencia/{Idempotencia,ChaveDeIdempotencia,RetencaoDaIdempotencia}.java`
- Teste: `web/outbox/DespachanteDeOutboxTest.java`, `web/idempotencia/IdempotenciaTest.java`

**Interfaces (produz):**
- `V55`: `outbox(seq bigint generated always as identity primary key, tipo text not null, carga jsonb not null, criada_em timestamptz not null, tentativas int not null default 0, proxima_em timestamptz not null, processada_em timestamptz, falhou_em timestamptz, ultimo_erro text)`; índice parcial `on outbox (proxima_em) where processada_em is null and falhou_em is null`.
- `public interface Outbox { void registrar(String tipo, Object carga); }`: exige transação ativa (`Propagation.MANDATORY`).
- `public interface ManipuladorDeOutbox { String tipo(); void tratar(JsonNode carga); }`: idempotente por contrato.
- `DespachanteDeOutbox` (`@Scheduled` a cada 1 s): pega até 50 pendentes com `for update skip locked`, chama o manipulador fora da transação de leitura, marca `processada_em`; falha ⇒ `tentativas + 1` e `proxima_em = agora + min(2^tentativas s, 10 min) × (0,8..1,2)`; na 10ª falha, `falhou_em` (fila morta) e log ERROR. Gauge `duora.outbox.pendentes`.
- `V56`: `idempotencia(conta_id uuid not null references conta(id) on delete cascade, chave text not null, rota text not null, resumo_corpo text not null, status int, resposta jsonb, criada_em timestamptz not null, primary key (conta_id, chave))`.
- `public class Idempotencia`: `<T> ResponseEntity<T> executar(ContaId conta, Optional<String> chave, String rota, Object corpo, Supplier<ResponseEntity<T>> efeito)`. Sem chave ⇒ só executa. Com chave: reserva com `insert ... on conflict do nothing` **na mesma transação do efeito**; mesma chave e mesmo corpo ⇒ a resposta gravada; corpo diferente ⇒ 409 `IDEMPOTENCIA_CONFLITO`; reserva sem resposta (outra requisição em andamento) ⇒ 409 `IDEMPOTENCIA_EM_ANDAMENTO`; chave fora do formato ⇒ 400 `IDEMPOTENCIA_INVALIDA`.
- Retenção: `PoliticaDeRetencao` `outbox-processada` (`P7D`, referência `processada_em`) e `idempotencia` (`P1D`).

- [ ] **Passo 1:** testes (`BancoDeTeste`, `RelogioDeTeste`).
  - `DespachanteDeOutboxTest`: `registradoNaTransacaoQueFalhouNaoSai` (rollback ⇒ nada despachado); `despachadoUmaVez`; `falhaReagendaComBackoffEJitter` (entre 1,6 s e 2,4 s depois da 1ª falha); `decimaFalhaVaiParaFilaMorta`; `doisDespachantesNaoPegamOMesmo` (duas threads ⇒ cada linha tratada uma vez).
  - `IdempotenciaTest`: `mesmaChaveMesmoCorpoDevolveAMesmaResposta` (o efeito roda uma vez); `mesmaChaveCorpoDiferente409`; `outraContaComAMesmaChaveNaoVeARespostaAlheia`; `duasSimultaneasUmEfeito` (`CountDownLatch`); `chaveInvalida400`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(web): outbox transacional e Idempotency-Key`.

### Task 1: Canal de eventos SSE com sequência do banco e ADR 0002

**Arquivos:**
- Criar: `src/main/resources/db/migration/V54__evento_conta.sql`
- Criar: `src/main/java/bipotheking/net/duora/web/eventos/{CanalDeEventos,Evento,Envio,EstadoInicial,EventosDeConta,Assinaturas,EventosController,RetencaoDeEventos}.java`
- Criar: `docs/adr/0002-sse-e-post-para-tempo-real.md`
- Criar: `frontend/src/eventos/{canal.ts,useEvento.ts}`
- Teste: `web/eventos/EventosApiTest.java`, `frontend/src/eventos/canal.test.ts`

**Interfaces (produz):**
- Antes de codar, rodar `mattpocock-skills:codebase-design` com duas alternativas para a fronteira `CanalDeEventos` (publicar gravando versus outbox genérica) e registrar a escolhida no ADR.
- `V54`: `evento_conta(seq bigint generated always as identity primary key, conta_id uuid not null references conta(id) on delete cascade, nome text not null, dados jsonb not null, criado_em timestamptz not null)`; índice `(conta_id, seq)`.
- `public record Evento(String nome, Object dados)`: nome casa `^(fila|encontro|chat|voz|conexao|moderacao)\.[a-z][a-zA-Z.]*$`, senão `IllegalArgumentException`.
- `public record Envio(ContaId para, Evento evento)`.
- `public interface CanalDeEventos`:
  - `void publicar(List<Envio> envios)`: grava em `evento_conta` na transação corrente (ou numa própria, se não houver), depois de `pg_advisory_xact_lock` de cada conta em ordem crescente de `ContaId` (Foco 2); entrega aos streams só depois do commit;
  - `default void publicar(ContaId para, Evento evento)`;
  - `void publicarEfemero(ContaId para, Evento evento)`: só memória, sem `id` (voz);
  - `boolean conectada(ContaId conta)`.
- `public interface EstadoInicial { List<Evento> para(ContaId conta); }`: enviados (efêmeros) a cada abertura de stream sem `Last-Event-ID` reenviável.
- `GET /api/eventos` (`text/event-stream`, `@ContaAtual`): com `Last-Event-ID` ≥ o menor `seq` guardado da conta, reenvia `seq >` dele (até 500, depois manda `EstadoInicial`); senão, `EstadoInicial`. Batimento (comentário) a cada 25 s; até 3 streams por conta (o 4º fecha o mais antigo); fila de envio de 100 por stream; `retry:` sorteado em 2000–5000 ms.
- `RetencaoDeEventos implements PoliticaDeRetencao`: `eventos-de-conta`, `P1D`, referência `criado_em`.
- Front: `assinar<T>(nome, aoReceber): () => void` com um `EventSource` compartilhado; ao fechar por erro, reabre com backoff e jitter completo (base 1 s, teto 30 s) e dispara `aoReconectar`; `useEvento<T>(nome, aoReceber)`; `export function aoReconectar(fn: () => void): () => void`.
- ADR 0002: contexto (o terraço usava polling com ETag), decisão (SSE único por sessão + POST por domínio; eventos duráveis com `seq` do banco; efêmeros sem id), alternativas (WebSocket, polling), consequências (filtros `MesmaOrigem`/`X-Duora` reaproveitados, uma instância só, limite de conexões HTTP/1.1 resolvido pelo stream único).

- [ ] **Passo 1:** escrever o `EventosApiTest` (`BancoDeTeste`, `RANDOM_PORT`, stream lido com `HttpClient` e `BodyHandlers.ofLines()`).
  - `semSessaoRecebe401`.
  - `eventoChegaComNomeEIdDaSequencia`: o `id:` é o `seq` gravado.
  - `rollbackNaoEntregaNada`: publicar dentro de uma transação que falha ⇒ nada chega e nada fica em `evento_conta`.
  - `lastEventIdReenviaDoBancoDepoisDeReinicio`: publicar 3, fechar e subir de novo o contexto, reconectar com o `seq` do 1º ⇒ chegam o 2º e o 3º.
  - `ordemPorContaSegueOCommit` (Foco 2): duas transações para a mesma conta, a primeira segura o commit; o `seq` entregue ao stream cresce na ordem de commit e um `Last-Event-ID` intermediário não perde nada.
  - `eventoDeOutraContaNaoChega`, nem com `Last-Event-ID: 0`.
  - `efemeroNaoTemIdNemFicaNoBanco`.
  - `filaDeEnvioCheiaFechaOStream` (101 eventos com o cliente parado ⇒ stream fechado; reconectar com o último id recebido traz o resto).
  - `quartoStreamFechaOMaisAntigo`; `nomeSemPrefixoConhecidoERecusado`.
- [ ] **Passo 2:** RED; implementar; verde.
- [ ] **Passo 3:** `canal.test.ts` com `EventSource` falso: dois assinantes do mesmo nome recebem o evento; cancelar os dois fecha; erro reabre com espera entre 0 e 1 s na 1ª tentativa e nunca acima de 30 s; `aoReconectar` é chamado na reabertura. RED; implementar; verde.
- [ ] **Passo 4:** escrever o ADR 0002. Commit `feat(eventos): SSE com eventos gravados na transação e Last-Event-ID pelo banco`.

### Task 2: Pareamento puro

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/pareamento/dominio/{Candidato,ParDeContas,ParPareado,ResultadoDoCiclo,Compatibilidade,Pareador}.java`
- Modificar: a suíte ArchUnit da Etapa 2 (`pareamento.dominio` sem Spring, JDBC nem Jackson)
- Teste: `pareamento/dominio/{CompatibilidadeTest,PareadorTest}.java`, fixture `pareamento/dominio/CandidatoBuilder.java`

**Interfaces (produz):**
- `public record Candidato(ContaId conta, Instant entrouEm, int idade, Preferencias preferencias, Optional<CelulaGeohash> celula)`. Sem campo `verificado`: toda conta ATIVA é verificada (Etapa 2), e o filtro "só verificados" saiu (dono, 2026-11-22).
- `public record ParDeContas(ContaId menor, ContaId maior)`: `static ParDeContas de(ContaId a, ContaId b)` (ordena por `UUID.compareTo`; iguais ⇒ `IllegalArgumentException`), `boolean contem(ContaId)`, `ContaId outro(ContaId)`.
- `public record ParPareado(Candidato a, Candidato b, Set<Intencao> intencoes)`: `a` é quem espera há mais tempo; `ParDeContas par()`.
- `public record ResultadoDoCiclo(List<ParPareado> pares, List<Candidato> semPar)`.
- `public final class Compatibilidade`: `static Set<Intencao> intencoesEmComum(Candidato a, Candidato b)` (NAMORO só se o gênero passa nos dois sentidos) e `static boolean compativeis(Candidato a, Candidato b)` (intenções em comum, idade de cada um na faixa do outro, raio obrigatório de cada lado).
- `public final class Pareador`: `static ResultadoDoCiclo parear(List<Candidato> fila, Set<ParDeContas> vetados, int vagas, Instant agora, Duration ciclo)`.

- [ ] **Passo 1:** escrever os testes (valores à mão; células da tabela da Etapa 3: `6gyf4`–`6gycf` ≈ 4,89 km, `6gyf4`–`6gyft` ≈ 16,62 km, `6gyf4`–`6gysh` ≈ 47,46 km, `6gyf4`–`6gxp9` ≈ 58,03 km).
  - `CompatibilidadeTest`: `amizadeComAmizadeECompativel`; `amizadeComNamoroSoNaoE`; `idadeNaBordaDaFaixaECompativel` (25–30, idade 30) e `idadeUmAnoForaNaoE` (31); `faixaDeUmLadoBastaParaRecusar`; `namoroExigeGeneroNosDoisSentidos`; `namoroEAmizadeComGeneroIncompativelSobraAmizade`; `raioObrigatorioDeUmLadoFiltra` (`ATE_5_KM` obrigatório, `6gyf4` × `6gyft`); `mesmoCasoSoPreferenciaNaoFiltra`; `raioNaBordaPassa` (`ATE_5_KM` com `6gyf4` × `6gycf`); `raioObrigatorioSemCelulaNaoE` (P3); `semLimiteObrigatorioNaoFiltra`.
  - `PareadorTest`: `filaVaziaNaoGeraPares`; `umaPessoaFicaSemPar`; `doisCompativeisFormamUmPar` (`a` entrou antes); `parVetadoNaoEPareado`; `vagasLimitamOsPares` (6 compatíveis, 2 vagas ⇒ os 4 mais antigos); `vagaZeroNaoParea`; `quemEsperaMaisEscolhePrimeiro`; `empateNoMesmoCicloVaiParaOMaisPerto` (A em t0; B e C no mesmo ciclo; C em `6gycf`, B em `6gysh` ⇒ {A,C}); `distanciaDesconhecidaFicaPorUltimo`; `ninguemEmDoisPares`; `resultadoNaoDependeDaOrdemDaLista`.
- [ ] **Passo 2:** RED (não compila).
- [ ] **Passo 3:** implementar. O `Pareador` ordena por (`entrouEm`, UUID) e, para cada pessoa sem par, escolhe pela chave de P2: `(ciclosDeEspera desc, distância asc com desconhecida = +∞, entrouEm asc, UUID asc)`, com `ciclosDeEspera = Duration.between(entrouEm, agora).dividedBy(ciclo)`. Para em `vagas` pares. Funções curtas, sem flag argument.
- [ ] **Passo 4:** verde, mais a regra ArchUnit. Commit `feat(pareamento): compatibilidade bidirecional e pareador puro`.

### Task 3: Simulação com 30 perfis fictícios

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/pareamento/dominio/{PerfisFicticios,SimulacaoDePareamentoTest}.java`

**Interfaces:**
- Consome: Task 2.
- Produz: `PerfisFicticios.trinta()`: lista fixa de 30 `Candidato`s com UUIDs literais: 10 só amizade, 10 só namoro, 10 com as duas; idades 18–60; 4 sem célula; 6 com raio obrigatório; células `6gyf4`, `6gycf`, `6gyft`, `6gysh`, `6gxp9` (São Paulo e arredores) e `75cm9` (Rio). A tabela fica no comentário da classe, com o par esperado de cada pessoa e o motivo de quem fica sem par.

- [ ] **Passo 1:** escrever o `SimulacaoDePareamentoTest`: `trintaPerfisGeramOsParesEsperados` (conjunto literal, `vagas=10`); `todoParECompativelNosDoisSentidos`; `ninguemEmDoisPares`; `nenhumParVetado` (5 vetos da tabela); `maximalidade` (com vagas sobrando, nenhum par de `semPar` é compatível); `capacidadeCheiaNaoParea`; `ciclosSucessivosNaoInventamPares` (3 rodadas tirando os pareados, `agora` + 5 s, mesmo total). Grava `target/simulacao-pareamento.md` com os pares e os motivos (evidência da prova).
- [ ] **Passo 2:** rodar. Divergência ⇒ conferir a regra no papel; a tabela só muda se o erro estiver nela. Commit `test(pareamento): simulação com 30 perfis fictícios`.

### Task 4: Vetos de par, histórico e sorteio de cenário

**Arquivos:**
- Criar: `src/main/resources/db/migration/V51__par_vetado.sql`, `V52__cenario_jogado.sql`
- Criar: `src/main/java/bipotheking/net/duora/pareamento/{VetosDePar,MotivoVeto,PareamentoAoExcluirConta}.java`, `sala/{HistoricoDeCenarios,SorteioDeCenario}.java`
- Modificar: `game/CatalogoDeCenarios.java` (`sorteaveis()`, com a lista recebida pelo construtor; o catálogo continua sem Spring), `room/CenariosConfig.java` (lê `duora.cenarios.sorteaveis` e a passa ao catálogo), `application.properties` (`duora.cenarios.sorteaveis=terraco`)
- Teste: `pareamento/VetosDeParTest.java`, `sala/{HistoricoDeCenariosTest,SorteioDeCenarioTest}.java`, `game/CatalogoDeCenariosTest.java`

**Interfaces (produz):**
- `V51`: `par_vetado(conta_menor uuid not null references conta(id), conta_maior uuid not null references conta(id), motivo text not null check (motivo in ('NAO','SAIDA','DENUNCIA','BLOQUEIO','CONECTADOS','SEM_DECISAO')), vetado_em timestamptz not null, primary key (conta_menor, conta_maior), check (conta_menor < conta_maior))`; índice em `conta_maior`.
- `V52`: `cenario_jogado(conta_id uuid not null references conta(id), cenario_id text not null, primeira_vez_em timestamptz not null, primary key (conta_id, cenario_id))`.
- `public enum MotivoVeto { NAO, SAIDA, DENUNCIA, BLOQUEIO, CONECTADOS, SEM_DECISAO }`.
- `VetosDePar`: `void vetar(ParDeContas par, MotivoVeto motivo)` (`insert ... on conflict (conta_menor, conta_maior) do update set motivo = excluded.motivo where par_vetado.motivo = 'CONECTADOS'`: um veto real sobrepõe `CONECTADOS`, nunca o contrário); `Optional<MotivoVeto> motivo(ParDeContas)`; `Set<ParDeContas> vetadosEntre(Collection<ContaId>)` (uma consulta, `= any(:contas)` nas duas colunas).
- `HistoricoDeCenarios`: `void registrar(ContaId, String cenarioId)` (idempotente); `Set<String> jogados(ContaId)`.
- `SorteioDeCenario.sortear(Set<String> jogadosA, Set<String> jogadosB, List<String> sorteaveis, RandomGenerator aleatorio) -> String` (P6; lista vazia ⇒ `IllegalStateException`).
- `CatalogoDeCenarios.sorteaveis() -> List<Cenario>` na ordem da propriedade, que o `CenariosConfig` lê e entrega ao catálogo; id sem cenário ⇒ a aplicação não sobe.
- `PareamentoAoExcluirConta implements AoExcluirConta`: apaga os vetos, o histórico e a entrada na fila da conta.

- [ ] **Passo 1:** testes: `VetosDeParTest` (`vetarDuasVezesNaoDuplica`; `vetoRealSobrepoeConectados`; `conectadosNaoSobrepoeVetoReal`; `valeNosDoisSentidos`; `vetadosEntreTrazSoParesDoConjunto`; `listaVaziaNaoConsulta`); `HistoricoDeCenariosTest` (`registrarDuasVezesUmaLinha`; `semHistoricoVazio`); `SorteioDeCenarioTest` com `new SplittableRandom(42)` (`preferePrimeiroOQueNinguemJogou`; `semIneditoEscolheOQueSoUmJogou`; `todosJogadosAindaSorteia`; `listaVaziaFalha`); `CatalogoDeCenariosTest` (`sorteaveisSegueAPropriedade`; `idDesconhecidoImpedeASubida`); exclusão: `excluirContaApagaVetosEHistorico`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(pareamento): vetos permanentes de par e sorteio de cenário inédito`.

### Task 5: Fila contínua (entrar, estado, desistir)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V50__fila_espera.sql`
- Criar: `src/main/java/bipotheking/net/duora/sala/{Fila,FilaJdbc,EstadoNaFila,SituacaoNaFila,FilaController,EstadoInicialDaFila,CodigoSala,AberturaDeEncontro,OcupacaoDeEncontros,EncontroAberto,ConvitePrivado}.java`
- Modificar: `application.properties` (`duora.fila.*`, `duora.encontros.maximo-simultaneos`, regra `fila-entrar`: 10 por `PT1M`)
- Teste: `sala/FilaApiTest.java`

**Interfaces:**
- Consome: Tasks 1, 2 e 4; `Perfis`, `ConsultaDeContas`, `Localizacoes`.
- Produz:
  - `V50`: `fila_espera(conta_id uuid primary key references conta(id), entrou_em timestamptz not null, idade int not null check (idade >= 18), preferencias jsonb not null, celula char(5), ausente_desde timestamptz)`; índice `(entrou_em)`. `preferencias` é coluna de extensão serializada pelo adapter `FilaJdbc` (P8).
  - `public enum SituacaoNaFila { AGUARDANDO_COMPATIVEL, AGUARDANDO_VAGA, PAREADO }`.
  - `public record EstadoNaFila(SituacaoNaFila situacao, Instant desde, @Nullable Integer posicao, @Nullable Integer estimativaMinutos, @Nullable String encontroId, @Nullable String cenario)`; o JSON tem exatamente essas chaves.
  - Portas (borda real entre `sala/` e o módulo do encontro; implementadas na Task 6 pela ponte e na Etapa 5 por `encontro/`):
    - `public interface AberturaDeEncontro { EncontroAberto abrirPareado(ParPareado par, String cenarioId); ConvitePrivado abrirPrivado(ContaId criador, String cenarioId); EncontroAberto entrarPrivado(String convite, ContaId convidado); Optional<String> encontroAtivoDe(ContaId conta); void cancelar(String encontroId); boolean presente(String encontroId, ContaId conta); }`;
    - `public interface OcupacaoDeEncontros { int ativos(); List<Instant> terminosPrevistos(); }` (crescente);
    - `public record EncontroAberto(String encontroId, String cenarioId, ContaId a, ContaId b)`; `public record ConvitePrivado(String encontroId, String convite, String cenarioId)`.
  - `public record Coordenadas(double latitude, double longitude)` (só em trânsito, nunca gravada).
  - `Fila`: `EstadoNaFila entrar(ContaId conta, Optional<Coordenadas> posicao)`, `void desistir(ContaId)`, `Optional<EstadoNaFila> estado(ContaId)`.
  - Rotas: `POST /api/fila` com `{ "latitude"?, "longitude"? }` (os dois ou nenhum; com coordenadas e localização consentida, chama `Localizacoes.atualizar` e descarta a coordenada) ⇒ 200 `EstadoNaFila`; `GET /api/fila` (200 ou 404 `FORA_DA_FILA`); `DELETE /api/fila` (204, idempotente).
  - Códigos em `CodigoSala`: `PERFIL_INCOMPLETO` (422, `prontoParaFila` falso por perfil), `CONTA_PAUSADA` (422, `Sua conta está pausada. Volte a aparecer em Meu perfil para entrar na fila.`), `JA_EM_ENCONTRO` (409), `FORA_DA_FILA` (404).
  - `posicao` e `estimativaMinutos` só em `AGUARDANDO_VAGA`: a posição é a ordem por `entrouEm`; a estimativa são os minutos (para cima) até o `ceil(posicao/2)`-ésimo término previsto, ou `null`.
  - `fila.estado` é publicado só quando muda para a conta.

- [ ] **Passo 1:** escrever o `FilaApiTest` (`BancoDeTeste`, `RANDOM_PORT`, `RelogioDeTeste`, portas falsas via `@TestConfiguration`).
  - `entrarComPerfilProntoAguardaCompativel` (chaves exatas do JSON).
  - `entrarDuasVezesMantemUmaEntradaEOEntrouEm` (Foco 3).
  - `perfilIncompletoRecebe422ENadaGrava`.
  - `contaPausadaRecebe422ContaPausada`.
  - `jaEmEncontroRecebe409`.
  - `coordenadaSemConsentimentoRecebe422` e `coordenadaComConsentimentoAtualizaACelula` (a coordenada não aparece em nenhuma tabela da fila).
  - `capacidadeCheiaMostraPosicaoEEstimativa` (ocupação 10, términos +4, +9, +14 min; a 3ª pessoa vê posição 3 e 9 min).
  - `desistirTiraDaFilaEEIdempotente`.
  - `estadoInicialDoStreamTrazAFila`.
  - `decimaPrimeiraEntradaNoMinutoRecebe429` (com `Retry-After`).
- [ ] **Passo 2:** RED; implementar (`insert ... on conflict (conta_id) do nothing`; o candidato é montado na entrada com `idadeEm(hoje em America/Sao_Paulo)`); verde. Commit `feat(sala): fila contínua com posição, estimativa e desistência`.

### Task 6: Ciclo de pareamento e ponte com a sala atual

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/sala/CicloDePareamento.java`, `room/{PonteDeEncontros,AssentoController}.java`
- Modificar: `room/{Salas,Sala,Participante,Retrato,Visao}.java`
- Teste: `sala/CicloDePareamentoTest.java`, `room/PonteDeEncontrosTest.java`

**Interfaces:**
- Consome: Tasks 1, 2, 4 e 5.
- Produz:
  - Primeiro um commit de refatoração ("make the change easy"): `Participante` ganha `@Nullable ContaId conta` sem mudar comportamento; a suíte atual verde.
  - `Salas`: `String criarPareada(ContaId a, Apelido apelidoA, ContaId b, Apelido apelidoB, Cenario cenario)`; `Entrada reivindicar(String salaId, ContaId conta)` (token novo; o anterior deixa de valer); `Optional<String> salaAtivaDe(ContaId)`.
  - `PonteDeEncontros implements AberturaDeEncontro, OcupacaoDeEncontros`: término previsto = abertura + 30 min; `presente` depois do primeiro `reivindicar`.
  - `POST /api/encontros/{id}/assento` (`@ContaAtual`): 200 com a `Visao` e o `Set-Cookie` do assento; conta de fora ⇒ 404 `ENCONTRO_INEXISTENTE`.
  - `Visao` ganha `encontroId` (nulo no fluxo anônimo).
  - Eventos: `fila.pareado` `{ "encontroId", "cenario" }` para os dois; `fila.estado` para quem segue esperando, se mudou.
  - `CicloDePareamento.executar()` (`@Scheduled(fixedDelayString="${duora.pareamento.ciclo}")`), numa transação curta, sem chamada externa:
    1. `select ... from fila_espera order by entrou_em for update skip locked limit 200`;
    2. remove quem está com `ausente_desde` há mais que `ausencia-maxima` (o ciclo marca `ausente_desde` quando `!canal.conectada` e limpa quando volta) ou cuja conta não está mais ATIVA;
    3. cancela encontros pareados sem os dois presentes depois do prazo (P5), devolvendo o presente à fila com o `entrou_em` original;
    4. `vagas = maximo - ocupacao.ativos()`; `Pareador.parear(...)` com `vetados = vetos.vetadosEntre(...)`;
    5. por par: sorteia o cenário, `abrirPareado`, `historico.registrar` dos dois, apaga as duas linhas, `canal.publicar` de `fila.pareado`; se a transação reverter, uma `TransactionSynchronization.afterCompletion` cancela as salas abertas em memória (a Etapa 5 grava o encontro na mesma transação e dispensa a compensação).
  - Depois do commit, chama `AvisoDeCompativel.executar()` (Task 8) se existir.

- [ ] **Passo 1:** `CicloDePareamentoTest` (`BancoDeTeste`, `RelogioDeTeste`, `executar()` à mão): `doisCompativeisSaoPareadosEAvisados` (mesmo `encontroId`, `cenario` `terraco`, fila vazia, duas linhas em `cenario_jogado`); `parVetadoContinuaNaFila`; `capacidadeCheiaNaoParea`; `semStreamPor60sSaiDaFila` (59 s fica, 61 s sai; Foco 4); `contaSuspensaDuranteAEsperaSai`; `contaPausadaDuranteAEsperaSai` (o passo 2 do ciclo também remove quem `Perfis.pausada`); `pareadoAusenteCancelaEDevolveOPresente` (P5); `doisCiclosSimultaneosNaoDuplicamPessoa` (duas threads em `executar()` com latch ⇒ cada conta em no máximo um par; Foco 5); `falhaAoAbrirRevertETudo` (abertura falsa que lança no 2º par ⇒ fila intacta e nenhum evento).
- [ ] **Passo 2:** `PonteDeEncontrosTest`: `reivindicarEmiteCookieQueAbreASala`; `reivindicarDeNovoInvalidaOTokenAnterior`; `contaDeForaRecebe404SemDados`; `ativosContaSoSalasComConta`.
- [ ] **Passo 3:** RED; implementar; verde; rodar também `SalaApiTest`, `ConcorrenciaTest` e `CenarioApiTest` sem mudar asserções. Commit `feat(sala): ciclo de pareamento com SKIP LOCKED abre o cenário para o par`.

### Task 7: Encontro privado por convite

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/sala/EncontrosPrivadosController.java`
- Modificar: `room/PonteDeEncontros.java` (`abrirPrivado`, `entrarPrivado`)
- Teste: `sala/EncontroPrivadoApiTest.java`

**Interfaces:**
- Consome: Tasks 0, 4, 5 e 6.
- Produz:
  - `POST /api/encontros/privados` `{ "cenario": "<id>" | null }` ⇒ 201 `{ "encontroId", "convite", "cenario" }` e o `Set-Cookie` do criador. Sem `cenario`, sorteia com o histórico do criador. Fora de `sorteaveis()` ⇒ 422 `CENARIO_INVALIDO`.
  - `POST /api/encontros/privados/entrar` `{ "convite" }` com `Idempotency-Key` opcional ⇒ 200 com a `Visao` e o `Set-Cookie`. O link compartilhado é `/#convite=<token>` (fragmento, fora dos logs).
  - Códigos: `CONVITE_INVALIDO` (404, também para convite usado, encerrado ou par vetado), `CONVITE_PROPRIO` (422), `CAPACIDADE_CHEIA` (503 + `Retry-After: 60`, P4), `JA_EM_ENCONTRO` (409).
  - Veto de qualquer motivo exceto `CONECTADOS` ⇒ `CONVITE_INVALIDO` (o bloqueado não fica sabendo). `CONECTADOS` só vale no pareamento público; a Etapa 6 restringe o convite a conexões ativas.
  - Ao entrar, `historico.registrar` dos dois.
  - Ao criar, `LimitesDeContaNova.consumir(criador, CONVITE)` na mesma transação (conta com menos de 7 dias: 3 por dia).

- [ ] **Passo 1:** testes: `criarDevolveConviteECookie`; `criarSemCenarioSorteiaUmSorteavel`; `cenarioForaDosSorteaveisRecebe422`; `convidadoEntraEOsDoisVeemOMesmoCenario`; `conviteEDeUsoUnico` (terceira conta: 404, corpo sem apelido); `criadorAbrindoOProprioConviteRecebe422`; `parVetadoPorNaoRecebeConviteInvalido` (status e corpo iguais aos de um token inexistente); `paresConectadosPodemUsarConvite`; `capacidadeCheiaRecebe503EConviteSegueValendo`; `jaEmEncontroRecebe409`; `mesmaIdempotencyKeyDevolveAMesmaResposta` (a segunda chamada não cria outro assento); `quartoConviteDeContaNovaRecebe429` (`LIMITE_DE_CONTA_NOVA`, nenhum encontro criado).
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(sala): encontro privado por convite de uso único com escolha de cenário`.

### Task 8: Web Push opcional pela outbox

**Arquivos:**
- Criar: `src/main/resources/db/migration/V53__push_inscricao.sql`
- Criar: `src/main/java/bipotheking/net/duora/notificacao/{ConfiguracaoPush,EnvioDePush,ResultadoDoEnvio,WebPushInterasoAdapter,EnvioDePushNoLog,InscricaoDePush,InscricoesDePush,EnderecoDePush,PushController,AvisoDeCompativel,ManipuladorDePush,NotificacaoAoExcluirConta}.java`
- Criar: `docs/adr/<próximo número livre>-web-push-com-interaso.md`
- Modificar: `pom.xml` (`com.interaso:webpush:1.3.0`), `application.properties` (`duora.push.*`), o service worker da 1a
- Criar: `frontend/src/notificacao/push.ts`
- Teste: `notificacao/{EnderecoDePushTest,PushApiTest,AvisoDeCompativelTest,WebPushInterasoAdapterTest}.java`, `frontend/src/notificacao/push.test.ts`

**Interfaces:**
- Consome: Tasks 0, 2 e 5; `Outbox`.
- Produz:
  - `V53`: `push_inscricao(id uuid primary key, conta_id uuid not null references conta(id), endpoint text not null unique check (length(endpoint) <= 1024), p256dh text not null, auth text not null, criada_em timestamptz not null, ultimo_aviso_em timestamptz)`; índice em `conta_id`; até 5 por conta (a 6ª apaga a mais antiga).
  - Porta `public interface EnvioDePush { ResultadoDoEnvio enviar(InscricaoDePush inscricao, String titulo, String corpo, String url); }`, `enum ResultadoDoEnvio { ENTREGUE, EXPIRADA, FALHOU }`. `WebPushInterasoAdapter` (com `duora.push.habilitado=true`): `HttpClient` com `connectTimeout` 5 s e timeout de requisição 10 s; 404/410 ⇒ `EXPIRADA`. `EnvioDePushNoLog` nos demais casos.
  - `ConfiguracaoPush` (`duora.push`): `habilitado`, `assunto` (`mailto:`), `chavePublica`, `chavePrivada` (`DUORA_VAPID_PUBLICA`, `DUORA_VAPID_PRIVADA`; habilitado sem chave ⇒ não sobe), `hostsPermitidos` (padrão `fcm.googleapis.com`, `updates.push.services.mozilla.com`, `push.apple.com`, `notify.windows.com`).
  - `EnderecoDePush.validar(String) -> URI`: só `https`, sem userinfo nem porta explícita, host igual a um item da lista ou subdomínio dele; senão 422 `INSCRICAO_INVALIDA` (parse, don't validate: devolve o tipo já válido).
  - Rotas: `GET /api/push/chave-publica` ⇒ `{ "chave" }`; `PUT /api/push/inscricao` `{ "endpoint", "chaves": { "p256dh", "auth" } }` ⇒ 204; `DELETE /api/push/inscricao` `{ "endpoint" }` ⇒ 204.
  - `AvisoDeCompativel.executar()` aplica P7 numa transação: atualiza `ultimo_aviso_em` e `outbox.registrar("push", {inscricaoId, titulo, corpo, url})` juntos. `ManipuladorDePush` (tipo `push`) envia e apaga a inscrição `EXPIRADA`.
  - `NotificacaoAoExcluirConta`: apaga as inscrições.
  - Front: `ativarAvisos(): Promise<'ativado' | 'negado' | 'adiado' | 'indisponivel'>`, `avisosAtivos(): Promise<boolean>`.

- [ ] **Passo 1:** testes.
  - `EnderecoDePushTest` (`@ParameterizedTest`, Foco 1): aceitos `https://fcm.googleapis.com/fcm/send/abc`, `https://web.push.apple.com/x`, `https://updates.push.services.mozilla.com/wpush/v2/x`; recusados `http://fcm.googleapis.com/x`, `https://127.0.0.1/x`, `https://[::1]/x`, `https://169.254.169.254/x`, `https://10.0.0.1/x`, `https://2130706433/x`, `https://fcm.googleapis.com.evil.example/x`, `https://user@fcm.googleapis.com/x`, `https://fcm.googleapis.com:8443/x`, `javascript:alert(1)`.
  - `PushApiTest`: `inscreverGrava`; `mesmoEndpointNaoDuplica`; `endpointProibido422ENadaGrava`; `sextaApagaAMaisAntiga`; `removerSoADaConta`.
  - `AvisoDeCompativelTest`: `avisaCompativelForaDaFila` (uma linha `push` na outbox); `naoAvisaQuemEstaNaFila`; `naoAvisaIncompativel`; `naoAvisaDuasVezesEm24h` (23 h 59 não, 24 h sim); `naoAvisaEntre22hE8h` (21:59 sim, 22:00 não, 07:59 não, 08:00 sim, horário de São Paulo); `naoAvisaContaPausada`; `corpoEOTextoFixo`; `rollbackNaoDeixaPushNaOutbox`.
  - `WebPushInterasoAdapterTest` (`HttpServer` local liberado só no teste): 201 ⇒ `ENTREGUE`; 410 ⇒ `EXPIRADA`; resposta que demora 15 s ⇒ `FALHOU` em menos de 11 s.
  - `push.test.ts`: sem `PushManager` ⇒ `'indisponivel'`; permissão negada ⇒ `'negado'` e nenhum `PUT`; `requestPermission()` devolvendo `'default'` (prompt silencioso do Chrome no Android) ⇒ `'adiado'`, nenhum `PUT`, e um ouvinte de `permissions.query({name:'notifications'})` `onchange` faz o `PUT` se a pessoa conceder depois.
  - Service worker: `notificacaoDiscreta` (a notificação mostrada tem `tag` `duora-compativel`, `renotify` falso, sem `image` e só o corpo fixo).
- [ ] **Passo 2:** RED; implementar; verde. No service worker: `push` mostra a notificação com o ícone 192; `notificationclick` foca ou abre a `url`. A lista de cache segue sem `/api`.
- [ ] **Passo 3:** ADR da biblioteca (P1). Commit `feat(notificacao): Web Push opcional pela outbox`.

### Task 9: Telas de encontrar, fila e convite

**Arquivos:**
- Criar: `frontend/src/sala/{EncontrarTela,FilaTela,ConvitePrivadoTela,EntrarPorConviteTela,api}.tsx|ts`
- Modificar: o roteador da 1a (`/encontrar`, `/fila`, `/convite`; `#convite=` em `/` leva a `EntrarPorConviteTela`)
- Teste: `frontend/src/sala/{FilaTela,EncontrarTela}.test.tsx`

**Interfaces:**
- Consome: rotas das Tasks 5–8; `useEvento('fila.estado' | 'fila.pareado')`, `aoReconectar` (Task 1); `ativarAvisos` (Task 8).
- Produz (textos e papéis usados pelos e2e):
  - `EncontrarTela`: botões "Encontro público" e "Encontro privado". O público, com localização consentida (`GET /api/perfil/localizacao`), pede a posição ao navegador e a manda no `POST /api/fila`; sem consentimento ou com erro, entra sem coordenadas.
  - `FilaTela`: `role="status"` com o texto do estado; tempo de espera em minutos; "Desistir"; "Ativar avisos" quando inativos; ao receber `fila.pareado`, chama `POST /api/encontros/{id}/assento` e navega para o cenário; em `aoReconectar`, refaz `GET /api/fila`.
  - `ConvitePrivadoTela`: rádio "Cenário" (sorteáveis + "Sortear"), "Criar convite", campo só leitura com o link e "Copiar link".
  - "Sair" (voltar) sempre visível.

- [ ] **Passo 1:** Vitest com `fetch` e `canal` falsos: `estadoVazioComAvisosMostraTextoHonesto`; `estadoVazioSemAvisosOfereceAtivar`; `capacidadeCheiaMostraPosicaoEEstimativa` (texto exato com 3 e 9); `pareadoPedeAssentoENavega`; `desistirChamaDeleteEVolta`; `semLocalizacaoAindaEntraNaFila`; `reconexaoRefazOEstado`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(web): telas de encontro público, fila e convite privado`.

### Task 10: E2E e acessibilidade

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/{FilaPublicaIT,EncontroPrivadoIT,FilaVaziaIT,EncontrarPagina}.java`
- Modificar: `e2e/AxeIT.java`, `.claude/handoff/_mapa.md` (pacotes `pareamento/`, `sala/`, `notificacao/`, `web/eventos/`, `web/outbox/`)

**Interfaces:**
- Produz: `EncontrarPagina` com `entrarNaFilaPublica()`, `textoDoEstado()`, `desistir()`, `criarConvite(String cenario)`, `linkDoConvite()`.

- [ ] **Passo 1:** ITs (ciclo de 1 s por propriedade de teste):
  - `FilaPublicaIT.doisCompativeisSaoPareadosEAbremOCenario` (em até 10 s, as duas veem o terraço com o apelido da outra);
  - `FilaPublicaIT.incompativeisNaoSaoPareados` (4 ciclos, estado vazio);
  - `EncontroPrivadoIT.conviteLevaAoCenarioEscolhido` e `conviteUsadoNaoValeParaTerceiro` ("Este convite não vale mais.");
  - `FilaVaziaIT.estadoVazioHonestoEDesistir`;
  - `AxeIT`: `encontrarSemViolacoes`, `filaVaziaSemViolacoes`, `conviteSemViolacoes`.
- [ ] **Passo 2:** `./mvnw -q -Pe2e verify -Dit.test='FilaPublicaIT,EncontroPrivadoIT,FilaVaziaIT,AxeIT'` em chromium, firefox e webkit: verde, sem skip.
- [ ] **Passo 3:** `_mapa.md` (só as linhas novas). Commit `test(e2e): fila pública, convite privado e estado vazio`.

### Task 11: Liquidez visível (espera típica e horários com mais gente)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V57__espera_concluida.sql`
- Criar: `src/main/java/bipotheking/net/duora/sala/{MovimentoDaFila,Movimento,EsperasConcluidas,EsperasConcluidasJdbc,MovimentoController,RetencaoDeEsperas}.java` (o cálculo puro em `sala/MovimentoDaFila`, sem Spring)
- Modificar: `sala/{Fila,CicloDePareamento}.java` (registram a espera ao sair da fila, na mesma transação), `frontend/src/sala/{FilaTela,EncontrarTela}.tsx`
- Teste: `sala/{MovimentoDaFilaTest,MovimentoApiTest,RetencaoDeEsperasTest}.java`, `frontend/src/sala/{FilaTela,EncontrarTela}.test.tsx` (casos novos)

**Interfaces:**
- Consome: Tasks 5 e 6; `Clock`; o domínio de contas de teste do contrato (`teste.duora.invalid`).
- Produz:
  - `V57`: `espera_concluida(id uuid primary key, entrou_em timestamptz not null, saiu_em timestamptz not null, pareada boolean not null, check (saiu_em >= entrou_em))`; índice em `saiu_em`. **Sem `conta_id`**: só o necessário para a estatística. Contas de teste não geram linha.
  - `record Espera(Instant entrouEm, Instant saiuEm, boolean pareada)`; `record Movimento(OptionalInt esperaTipicaMinutos, List<Integer> horasDePico)`.
  - `MovimentoDaFila.calcular(List<Espera> esperas, Instant agora, ZoneId zona) -> Movimento`:
    - espera típica = mediana, arredondada para cima em minutos, das esperas **pareadas** com `saiuEm` nas últimas 24 h; vazia com menos de 5 amostras;
    - horas de pico = as 3 horas do dia (0–23, em `America/Sao_Paulo`) com mais entradas nos últimos 14 dias, empate pela hora menor, devolvidas em ordem crescente; vazia com menos de 20 entradas no período.
  - `GET /api/fila/movimento` (`@ContaAtual`) → `{ "esperaTipicaMinutos": n | null, "horasDePico": [h, ...] }`, calculado no máximo a cada 5 min (cache em memória com o `Clock`).
  - `RetencaoDeEsperas implements PoliticaDeRetencao`: `esperas-concluidas`, `P15D` sobre `saiu_em`.
  - Front: `FilaTela` em `AGUARDANDO_COMPATIVEL` mostra "Espera típica agora: cerca de {m} min." quando houver; `EncontrarTela` mostra "Costuma ter mais gente às {h1}h, {h2}h e {h3}h." quando houver. Sem dado, nada aparece (estado honesto, sem número inventado).
- [ ] **Passo 1:** testes.
  - `MovimentoDaFilaTest`: `medianaDasPareadasArredondaParaCima` (esperas de 2, 3, 4, 7 e 9 min → 4; com 2,5 min de mediana → 3); `quatroAmostrasNaoBastam`; `desistenciasNaoEntramNaMediana`; `esperaDeOntemNaoConta` (24 h + 1 s fora); `horasDePicoNoFusoDeSaoPaulo` (entradas às 22:30Z contam na hora 19); `empateVaiParaAHoraMenor`; `dezenoveEntradasNaoBastam`.
  - `MovimentoApiTest`: `chavesExatas`; `saidaDaFilaGravaEsperaSemConta` (a linha não tem coluna de conta; pareada `true` no ciclo, `false` na desistência); `contaDeTesteNaoEntraNoMovimento`; `cacheDe5Min` (duas chamadas em 4 min → uma consulta).
  - `RetencaoDeEsperasTest`: 15 dias + 1 s sai; 14 dias fica.
  - Vitest: `esperaTipicaSoComDado`; `horasDePicoComTextoExato` (`[19, 20, 21]` → "Costuma ter mais gente às 19h, 20h e 21h.").
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(sala): espera típica e horários com mais gente na fila`.

## Ordem e paralelismo
- **Leva 1 (paralela, worktrees separados, no máximo 4):** Task 0 (se necessária), Task 1 (eventos), Task 2 (domínio puro) e Task 4 (vetos e sorteio).
- **Leva 2 (paralela):** Task 3 (depois da 2) e o `EnderecoDePush` + adapter da Task 8.
- **Leva 3:** Task 5 (depois da 1, 2 e 4); a Task 9 começa junto, contra as Interfaces, com `fetch` falso.
- **Leva 4:** Task 6 (depois da 5).
- **Leva 5:** Task 7 (mesmo arquivo de ponte da 6), o resto da Task 8, a Task 11 (liquidez; toca `FilaTela`, então entra depois do fim da Task 9) e o fim da Task 9.
- **Por último:** Task 10, revisão final (`superpowers:requesting-code-review`), merge, push e CI.
- Cada task cria só as suas migrações, então os worktrees não colidem.

## Decisões do dono
Nenhuma bloqueia a etapa. P1–P8 acima são escolhas de planejamento reversíveis.
