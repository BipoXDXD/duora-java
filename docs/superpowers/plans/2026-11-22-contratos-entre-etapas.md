# Contratos entre etapas (Duora social)

Decisões transversais que todos os planos a partir da Etapa 1a seguem. Este arquivo vale sobre qualquer plano que o contrarie. Para mudar um contrato, primeiro edite este arquivo, depois o plano.

Spec: `docs/superpowers/specs/2026-11-21-duora-social-design.md`. Ordem: 0 → 1 → 1a → 1b → 1c → 2 → 3 → 4 → 7 → 5 → 6; a 8 corre junto da 5, a 9 fecha antes da 10, e a 9b corre em paralelo a partir da 1c.

## Back-end
- Java 25 LTS e Spring Boot 4.2.0. Um módulo Maven só, com Maven Wrapper. Compilação com `-Xlint:all -Werror`, Spotless e JaCoCo.
- Pacotes em `bipotheking.net.duora`: `game/`, `encontro/` (evolução de `room/`), `conta/`, `perfil/`, `sala/` (encontros públicos e privados, fila contínua), `pareamento/`, `conexao/`, `voz/`, `moderacao/`, `notificacao/`, `admin/`, `web/` (filtros, cabeçalhos, MIME, erros).
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
- **Erros:** JSON `{ "codigo": "<CODIGO_EM_MAIUSCULAS>", "mensagem": "<pt-BR>" }` pelo `TratamentoDeErros` existente. 422 para regra de negócio, 409 para conflito ou versão, 429 para limite de taxa (com `Retry-After`).
- **Limite de taxa:** Bucket4j core em memória (uma instância só), chave por IP e por conta ou e-mail, configurada em `application.properties`.
- **Tempo real:**
  - Um único stream SSE por sessão em `GET /api/eventos`. Os eventos têm nome com prefixo do domínio: `fila.*`, `encontro.*`, `chat.*`, `voz.*`, `conexao.*`, `moderacao.*`.
  - Comandos vão por `POST` no domínio dono. Reconexão com `Last-Event-ID`. O polling com ETag fica como plano B só no encontro.
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
  - O código fica em `frontend/`: Vite, React 19, TypeScript `strict` (com `noUncheckedIndexedAccess`) e Tailwind CSS 4.
  - O build roda pelo `frontend-maven-plugin`, com Node e pnpm fixados e baixados pelo plugin. A saída vai para `target/classes/static/` e não é versionada.
  - Os assets binários continuam em `src/main/resources/static/assets/`.
- **Rotas e dados:** React Router (modo de dados) e TanStack Query para o estado do servidor. Sem Redux. Os formulários usam elementos nativos com validação de UI; a validação de verdade fica no servidor.
- **Testes:**
  - Unidade e componentes com Vitest e Testing Library.
  - O e2e continua no Playwright Java (`*IT`), com seletores por papel, rótulo e texto, nunca por classe CSS.
  - Axe em toda tela nova.
- **Service worker:** a lista do shell sai do manifest do Vite, e a versão do cache sai da impressão digital (guarda criada na Etapa 1). Nunca há cache de `/api`.
- **CSP:** sem `unsafe-inline` nem `unsafe-eval` no app. Só a página do fliperama (9b) ganha `'wasm-unsafe-eval'`.
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
