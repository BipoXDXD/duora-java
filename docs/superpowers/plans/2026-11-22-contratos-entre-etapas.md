# Contratos entre etapas (Duora social)

Decisões transversais que todos os planos a partir da Etapa 1a seguem. Este arquivo vale sobre qualquer plano que o contrarie. Para mudar um contrato, primeiro edite este arquivo, depois o plano.

Spec: `docs/superpowers/specs/2026-11-21-duora-social-design.md`. Ordem: 0 → 1 → 1a → 1b → 1c → 2 → 3 → 4 → 7 → 5 → 6; a 8 corre junto da 5, a 9 fecha antes da 10, e a 9b corre em paralelo a partir da 1c.

## Back-end
- Java 25 LTS e Spring Boot 4.2.0. Um módulo Maven só, com Maven Wrapper. Compilação com `-Xlint:all -Werror`, Spotless e JaCoCo.
- Pacotes em `bipotheking.net.duora`:
  - de domínio: `game/`, `encontro/` (evolução de `room/`), `conta/`, `perfil/`, `sala/` (encontros públicos e privados, fila contínua), `pareamento/`, `conexao/`, `voz/`, `moderacao/`, `notificacao/` (inclui o `EnvioDeEmail`), `admin/`, `retencao/` (políticas e jobs de expiração);
  - `nucleo/` (Java puro): `ErroDeNegocio`, `CodigoDeErro` e `TipoDeErro`, que não conhecem HTTP. Só o `web/TratamentoDeErros` traduz para status.
  - Dentro de cada módulo, a raiz é a API pública e os casos de uso; `dominio/` fica sem Spring web, JDBC nem Jackson; `web/` e `infra/` guardam os adapters.
  - A `Outbox` (`registrar`, `ProcessadorDeOutbox`) fica em `notificacao/`. Cada `PoliticaDeRetencao` expõe `tamanho()` para a métrica.
  - `ParDeContas` (par ordenado `menor`/`maior`) é tipo compartilhado em `nucleo/`.
  - A tabela de denúncia e bloqueio nasce na Etapa 5 (`moderacao/`), e a Etapa 8 só acrescenta sobre ela. A suspensão de conta é `conta.SuspensaoDeContas.suspender(ContaId, String motivo)`.
  - `piloto/` (Etapa 11): `sala.LimiteDeSimultaneidade` e `conta.PortaoDeCadastro`, que fica fechado em produção até a abertura. As métricas vêm de portas `Estatisticas*`, uma por módulo dono dos dados, com `nucleo.IntervaloDeDia`.
  - O banimento é `BanimentoDeContas`: publica o evento `ContaBanida`, tratado na mesma transação, e chama `CanalDeEventos.encerrarStreams`.
  - Contas de teste usam o domínio `teste.duora.invalid`: nunca são pareadas com contas reais, ficam fora das métricas e nunca viram admin.
  - Prazos de retenção extras, que exigem uma nova versão da política de privacidade: evidência de denúncia (30 dias depois de resolvida, incluindo o clipe de voz da Etapa 5), comentário do feedback (90 dias), registros de acesso (183 dias, Marco Civil art. 15, Etapa 2) e tentativas de verificação de idade (1 dia; só o resultado, a data e o método ficam).
  - Verificação de idade (Etapa 2): AWS em `us-east-1` (Rekognition Face Liveness, DetectFaces e Textract AnalyzeID), chamada só pelo back-end com o usuário IAM `duora-java-verificacao`; o navegador recebe credencial curta do papel `duora-java-liveness-captura`. Selfie e documento nunca vão para disco, log nem S3. O estado de conta `AGUARDANDO_VERIFICACAO` fica entre `PENDENTE` e `ATIVA`, e `ContaResumo.verificada()` alimenta o selo "verificado".
  - `conta.LimitesDeContaNova` (Etapa 2) é consumida pelo chat e pelas imagens do encontro (Etapa 5) e pelo convite privado (Etapa 4).
  - de infraestrutura: `web/` (filtros, cabeçalhos, MIME, erros, ids, `Clock`, limite de taxa, pipeline de imagens).
- Faixas do Flyway por etapa (sem out-of-order): 2 → V1–V19, 3 → V20–V39, 9 → V40–V49, 4 → V50–V69, 7 → V70–V79, 5 → V80–V99, 6 → V100–V119, 8 → V120–V139, 11 → V140–V159.
- O acesso é negado por padrão: o interceptor `ExigeSessao` cobre `/api/**`, com uma allowlist em `RotasPublicas` (o terraço anônimo `/api/sala(s)/**` fica nela até a Etapa 5).
- O link mágico leva o token no fragmento (`#t=`) e o consome por POST, então o token nunca chega ao log do servidor nem ao cabeçalho Referer.
- `duora.sessao.cookie-seguro` (padrão `true`) só pode ficar `false` em testes e2e locais por http, porque o WebKit descarta o cookie `Secure`.
- **Banco:**
  - PostgreSQL 18 com Flyway, migrações em `src/main/resources/db/migration/V<n>__<descricao>.sql`, e números novos sempre crescentes.
  - Acesso por Spring Data JDBC (agregados) e `JdbcClient` (consultas próprias). Sem JPA.
  - Testes com Testcontainers (`postgres:18`), uma suíte com Docker por vez na máquina.
- **IDs e tempo:**
  - IDs são UUID v7 gerados na aplicação, guardados em colunas `uuid`.
  - Todo tempo vem de um bean `java.time.Clock` injetado, nunca de `Instant.now()` direto. Os testes usam um relógio fixo.
- **Sessão:**
  - Cookie `duora_sessao` (`HttpOnly; Secure; SameSite=Lax; Path=/`). O banco guarda só o SHA-256 do token.
  - Controllers recebem `ContaId` por um argument resolver anotado com `@ContaAtual`. Sem sessão, a resposta é 401 `NAO_AUTENTICADO`.
  - A CSRF continua coberta pelos filtros atuais `MesmaOrigem` e `X-Duora`.
- **Erros:** `application/problem+json` (RFC 9457) com os campos extras `codigo` (`<CODIGO_EM_MAIUSCULAS>`) e `mensagem` (pt-BR, igual a `detail`) pelo `TratamentoDeErros` existente. 422 para regra de negócio, 409 para conflito ou versão, 429 para limite de taxa (com `Retry-After`).
- **Limite de taxa:** Bucket4j core em memória (uma instância só), chave por IP e por conta ou e-mail, configurada em `application.properties`.
- **Tempo real:**
  - Um único stream SSE por sessão em `GET /api/eventos`. Os eventos têm nome com prefixo do domínio: `fila.*`, `encontro.*`, `chat.*`, `voz.*`, `conexao.*`, `moderacao.*`.
  - Comandos vão por `POST` no domínio dono. Reconexão com `Last-Event-ID`, que é o `seq` da tabela `evento_conta` (eventos gravados, com a ordem por conta garantida por lock por conta na gravação). Os eventos efêmeros, como a sinalização da voz, saem por `publicarEfemero` sem gravar. O polling com ETag fica como plano B só no encontro.
- **E-mail:**
  - A interface `EnvioDeEmail` tem duas implementações: `EmailResend`, pela API HTTP do Resend via `RestClient`, e `EmailNoLog`, para desenvolvimento e testes, que escreve o link no log.
  - A chave fica em variável de ambiente e nunca entra no Git.
- **Antirrobô:** Cloudflare Turnstile, com a verificação no servidor atrás da interface `VerificacaoHumana`. Os testes usam as chaves oficiais de teste da Cloudflare, que sempre passam ou sempre falham.
- **Imagens enviadas:**
  - O servidor reprocessa com libvips (CLI `vips`, presente na imagem Docker e na CI): remove metadados, gera WebP q88 nítido, máximo de 1600 px no maior lado, e a versão borrada.
  - Os arquivos ficam no disco em `duora.imagens.dir`, um volume na VPS, servidos por um controller que confere a permissão. Nunca são estáticos públicos.
- **Voz:** WebRTC 1:1 só de áudio. coturn com credenciais TURN REST (HMAC-SHA1, TTL de 10 min). A sinalização usa o SSE e o POST acima.
- **Push:** Web Push com VAPID. As chaves ficam em variável de ambiente; a biblioteca é escolhida no plano da Etapa 4, conferida pelo Context7.

## Front (a partir da Etapa 1a)
- **Código e build:**
  - O código fica em `frontend/`: Vite, React 19, TypeScript `strict` (com `noUncheckedIndexedAccess`) e Tailwind CSS 4 sem preflight, para não alterar o visual atual.
- Os bundles com hash são servidos em `/app/` com `Cache-Control: immutable`. O `sw.js` é gerado no build a partir do manifest do Vite, e os testes de guarda leem `target/classes/static`.
  - O build roda pelo `frontend-maven-plugin`, com Node e pnpm fixados e baixados pelo plugin. A saída vai para `target/classes/static/` e não é versionada.
  - Os assets binários continuam em `src/main/resources/static/assets/`.
- **Rotas e dados:** React Router (modo de dados, rotas com lazy loading) e TanStack Query para o estado do servidor, ambos entrando **na Etapa 2**, que é quando surgem várias telas. A 1a mantém a tela única, e o encontro mantém o próprio laço de polling até o SSE. Sem Redux. Os formulários usam elementos nativos com validação de UI; a validação de verdade fica no servidor.
- **Testes:**
  - Unidade e componentes com Vitest e Testing Library.
  - O e2e continua no Playwright Java (`*IT`), com seletores por papel, rótulo e texto, nunca por classe CSS.
  - Axe em toda tela nova.
- **Service worker:** a lista do shell sai do manifest do Vite, e a versão do cache sai da impressão digital (guarda criada na Etapa 1). Nunca há cache de `/api`.
- **CSP:** sem `unsafe-inline` nem `unsafe-eval` no app. Só duas páginas, cada uma com entrada HTML própria e CSP própria, ganham `'wasm-unsafe-eval'`: a do fliperama (9b, `/jogos/fliperama/**`) e a da verificação de idade (Etapa 2, `/verificacao`), que também libera câmera, `https://cdn.liveness.rekognition.amazonaws.com` e `wss://streaming-rekognition.us-east-1.amazonaws.com`. O SPA chega a elas por navegação de página inteira, nunca pelo roteador.
- **Texto e estilo:** todo texto em pt-BR e com gênero neutro quando se refere à pessoa. O estilo segue `~/.claude/knowledge/ui-design.md` (Refactoring UI).

## Assets e ferramentas
- **Formatos:** imagens em WebP q88, áudio em MP3 192 kbps / 48 kHz. Sempre um formato por tipo, sem exceções. Todo asset é registrado em `assets/manifest.md` pela skill `duora-assets`.
- **Ferramentas de geração e edição:**
  - 3D: Hi3D (assinatura). Se o Hi3D falhar ou não tiver o recurso, Tripo ou Rodin-Business, avisando o dono do custo.
  - Imagens 2D: Tripo (`chat_image_2.5_sunburst`, 4K).
  - Áudio: ElevenLabs. Vídeo ou efeito, se for preciso: Higgsfield.
  - Edição, render e export: Blender local (MCP ou headless).
  - Fliperama: Godot 4 pelo MCP Godot-AI, com export web single-thread.
- **Provas no Android:** emulador local (`scripts/android/`). Playtests são feitos por agentes. Ao dono só chegam escolhas, nunca tarefas.
- **Máquina:** comandos pesados (Maven, Blender, Docker, emulador) correm um por vez, com a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`.

## Processo
- **Planos:** um plano por etapa (ou grupo) em `docs/superpowers/plans/2026-11-22-etapa-<n>-<tema>.md`, no formato da skill writing-plans.
- **Execução:** pela skill subagent-driven-development, com agentes Opus High e no máximo 4 simultâneos.
- **Commits:** Conventional Commits em pt-BR, com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Decisões do dono:** um plano que precise de uma escolha que só o dono pode fazer (fornecedor, custo, algo irreversível) a lista numa seção "Decisões do dono", com opções e uma recomendação, e não bloqueia o resto.

## Princípios de engenharia (pedido do dono, 2026-11-22)
Valem as regras de `~/.claude/knowledge/regras-globais.md`: Qualidade de código, Design patterns, Arquitetura, Sistemas distribuídos e dados, Testes. Os revisores citam o smell ou o refactoring pelo nome (Fowler, Clean Code) e a regra violada.

**Clean Code e Refactoring**
- Commit de refatoração separado de commit de comportamento, sempre com testes verdes.
- "Make the change easy, then make the easy change": um plano que mexe em código ruim começa com uma task de refatoração.
- Funções curtas com nome de intenção, sem flag argument, guard clauses, "parse, don't validate" na fronteira (tipos de domínio nascem válidos) e módulos profundos.

**Clean Architecture**
- **Dependências:** o domínio (`game/`, regras de `pareamento/`, `encontro/`, `conexao/`) não importa Spring web, JDBC nem Jackson. As portas são interfaces no núcleo, e os adapters ficam na infraestrutura, com o sufixo `...Adapter` ou o nome da tecnologia.
- **Portas:** só onde há borda real: `Clock`, `EnvioDeEmail`, `VerificacaoHumana`, `ProcessadorDeImagem`, `EnvioDePush`, `CredenciaisTurn`, `ProvaDeVida` e `LeituraDeDocumento` (AWS, Etapa 2), `ClassificadorDeImagem` (ONNX, Etapa 5; Sightengine, Etapa 8) e os repositórios. Nada de interface 1:1 por reflexo.
- **Boundary:** parcial por padrão, com a completa só quando a parcial começar a custar.
- **Fronteiras com fitness functions (ArchUnit no `verify`):**
  - nenhum ciclo entre módulos;
  - nenhum módulo importa as classes internas de outro, só a API pública dele (package-private por padrão);
  - o domínio não depende de `org.springframework.web` nem `..jdbc..`;
  - a ArchUnit entra na Etapa 2.
- **ADR:** toda decisão significativa vira `docs/adr/NNNN-titulo.md`. As retroativas são: 0001 monólito modular + Postgres, 0002 SSE + POST, 0003 motor de cenários, 0004 front React, 0005 PWA e cache por impressão.

**GoF, só quando o problema existe**
- **Strategy:** `Desfecho` por cenário, como função ou record.
- **State:** as fases do encontro, com tipo selado (`sealed interface`) e `switch` exaustivo em vez de enum mais ifs espalhados.
- **Adapter:** em volta do Resend, do Turnstile, do libvips, do Web Push e do coturn.
- **Observer:** pelos eventos do Spring (`ApplicationEventPublisher`) só quando houver vários efeitos independentes, por exemplo encontro encerrado → decisão, métricas e notificação.
- **Proibido:** Singleton manual e `static` mutável. A injeção é sempre pelo construtor.

**DDIA**
- **Sem dual write:** e-mail, push e eventos para outros módulos saem por transactional outbox (tabela `outbox` na mesma transação, despachante com retry, backoff e jitter, idempotente).
- **Idempotência:** POSTs repetíveis (pedir link mágico, decidir, aceitar convite, enviar mensagem) aceitam o cabeçalho `Idempotency-Key`, gravado com o efeito, com `UNIQUE` e limpeza automática.
- **Concorrência:**
  - sem read-modify-write;
  - o encontro usa versão otimista (`versao`) e UPDATE condicional;
  - invariantes viram constraints, por exemplo um encontro ativo por conta com índice único parcial e um par já decidido "não" com `UNIQUE (menor_id, maior_id)`;
  - o pareamento retira da fila com `SELECT ... FOR UPDATE SKIP LOCKED` numa transação curta.
- **Ordem e tempo:**
  - o `Last-Event-ID` do SSE e a ordem do chat vêm de uma sequência do banco, nunca de timestamp;
  - instantes em `timestamptz` UTC;
  - durações medidas com relógio monotônico.
- **Robustez:**
  - timeout em toda chamada externa (Resend, Turnstile, Push, libvips via processo);
  - filas, pools e consultas limitados;
  - todo acúmulo (sessões, tokens, outbox, chaves de idempotência, chat de 30 dias) tem job de limpeza e métrica de tamanho.
- **Logs:** estruturados (JSON), com correlation ID por requisição desde a Etapa 2.

**Skills a usar (superpowers e mattpocock)**
| Momento | Skill |
|---|---|
| Ideia nova ou etapa sem spec | `superpowers:brainstorming`; `mattpocock-skills:grilling` para apertar requisitos |
| Modelagem de domínio (contas, encontro, conexão, moderação) | `mattpocock-skills:domain-modeling` |
| Desenho de módulo ou interface antes do plano | `mattpocock-skills:codebase-design` (com duas alternativas, regra 34) |
| Plano | `superpowers:writing-plans` |
| Execução | `superpowers:subagent-driven-development`; implementadores com `superpowers:test-driven-development` ou `mattpocock-skills:tdd` |
| Bug ou teste instável | `superpowers:systematic-debugging` ou `mattpocock-skills:diagnosing-bugs` |
| Dúvida técnica ou de biblioteca | `mattpocock-skills:research` + Context7 |
| Experimento descartável (WebRTC, Godot, SSE) | `mattpocock-skills:prototype` |
| Revisão | `superpowers:requesting-code-review`, `mattpocock-skills:code-review`; ao receber, `superpowers:receiving-code-review` |
| Antes de dizer "pronto" | `superpowers:verification-before-completion`, depois `duora-etapa` |
| Fechar a branch | `superpowers:finishing-a-development-branch` |

## Decisões do dono já tomadas (2026-11-22)
Valem sobre as seções "Decisões do dono" dos planos.
- **Etapa 5, decisão pós-encontro:** três respostas. "Sim" cria a conexão se for mútuo. "Agora não" bloqueia o par. "Topo outro encontro antes de decidir" permite reencontro se ninguém disse não.
- **Etapa 9b, créditos:**
  - O lote inteiro está aprovado: Tripo (~360) para 2D e ElevenLabs (~10–14 mil) para os sons de todos os cenários.
  - O 3D sai do Hi3D. Se o Hi3D falhar ou não tiver o recurso, o fallback é o **Rodin-Business**: antes de gerar, consultar `rodin_estimate` e registrar o custo e o motivo do fallback. Não usar o Tripo para 3D.
- **Etapa 9b, aceite de cada cenário (D4):** o cenário entra no sorteio se o playtest por agentes der "sim" nas perguntas 2–5 e as capturas não tiverem defeito visual. O dono recebe o pacote (capturas e relatório) e pode vetar, mas o padrão é liberar.
- **Etapa 10, hospedagem:**
  - VPS Hostinger em São Paulo, operada pelo conector Hostinger, num subdomínio de `meetduora.com`;
  - backup no Backblaze B2 com Object Lock;
  - monitoramento com UptimeRobot e Healthchecks.io;
  - imagem pública no GHCR;
  - um ambiente só, com o cadastro fechado até a abertura.
- **Etapa 8, moderação:** o dono é o único admin. Fila com severidade (spec, 2026-11-22): **P0 ≤ 12 h** (parece menor, suspeita de abuso infantil), **P1 ≤ 24 h** (imagem sensível, assédio, imagem da triagem automática, fotos de perfil) e **P2 ≤ 48 h** (spam ou golpe, outro, denúncia pública sem conta), com alerta por e-mail. A suspensão por "parece menor" continua automática.

**Escolhas padrão feitas pelo controlador** (o dono pode reverter):
- **Remetente e contato:** `meetduora.com`.
- **Gêneros:** lista inclusiva com "Prefiro descrever".
- **Controlador na política de privacidade:** "o responsável pelo Duora".
- **"Sair" na 1c:** mostra uma tela neutra; a decisão privada chega na Etapa 5.
- **Prova de voz:** no emulador, forçado a passar pelo TURN, repetida na Etapa 10 com o coturn da VPS.
- **JS inicial da 1a:** acima de 80 KB gzip, aceitar até +10 KB; além disso, avaliar preact/compat.
- **Critério do piloto e rampa de capacidade (3 → 5 → 10):** confirmar com o dono antes de abrir o cadastro na Etapa 11.
- **Paridade com o mercado (2026-11-22), valores propostos em propriedades:** idade aprovada com `AgeRange.Low ≥ 21`, recusada com `AgeRange.High < 18`, prova de vida com confiança ≥ 90; contas com menos de 7 dias: 200 mensagens, 10 imagens e 3 convites privados por dia; triagem de imagem bloqueia com pontuação NSFW ≥ 0,90 e manda à fila de 0,50 a 0,90.
- **Feedback pós-encontro:** sai da Etapa 11 (Task 2) para a Etapa 5 (Task 18), com a migração `V89`; a Etapa 11 só o mede.
