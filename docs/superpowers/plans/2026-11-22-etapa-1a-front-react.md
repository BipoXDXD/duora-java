# Etapa 1a — Front em TypeScript, React e Tailwind: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** o front do terraço passa de JS nativo em `static/` para Vite + React 19 + TypeScript `strict` + Tailwind 4 em `frontend/`, construído pelo Maven, com comportamento, textos e visual idênticos, e o JS inicial medido (meta ≤ 80 KB gzip).

**Arquitetura:**
- `frontend/` é um projeto Vite. O `frontend-maven-plugin` baixa Node e pnpm, roda lint, build e Vitest. O Vite escreve em `target/vite/`, e o `pom.xml` declara essa pasta como recurso com `targetPath` `static`. Assim o resultado cai em `target/classes/static/` junto dos binários de `src/main/resources/static/assets/`, que continuam onde estão.
- A lógica de sessão de `main.js` vira um controlador TS sem React (`criarSessao`), lido pelos componentes com `useSyncExternalStore`. A cena, os meteoros, o som e o `#aviso` continuam imperativos, presos a elementos por ref, porque os testes dependem das mutações de DOM exatas que eles fazem hoje.
- O `sw.js` é gerado no build por um plugin do Vite: a lista do shell sai do `.vite/manifest.json`, e o `CACHE_VERSION` sai da mesma impressão digital que o `ServiceWorkerVersaoTest` confere.

**Stack:** Java 25, Spring Boot 4.2.0, `frontend-maven-plugin` 1.15.1, Node 26 LTS, pnpm 12, Vite 8 (Rolldown), React 19.3, TypeScript 6.0, Tailwind CSS 4.3, Vitest 5 + Testing Library + jsdom, ESLint 10 + typescript-eslint, Prettier 3, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1a). Contratos: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md` (vale sobre este plano). Pré-requisito: a Etapa 1 e a sua onda de correções (`fix-final-etapa-1`, com `pwa/Estaticos`, `ServiceWorkerVersaoTest` e `ShellDoServiceWorkerTest`) integradas no branch de partida.

**Skills por momento** (contratos, "Princípios de engenharia"):
- antes das Tasks 3, 4 e 5: `mattpocock-skills:codebase-design` sobre as Interfaces da task, com duas alternativas. Se uma alternativa for claramente melhor, o implementador a propõe ao controlador antes de codar. As Interfaces só mudam com o aval dele, porque as tasks paralelas dependem delas;
- implementação: `mattpocock-skills:tdd` (ou `superpowers:test-driven-development`);
- falha ou teste instável: `superpowers:systematic-debugging` ou `mattpocock-skills:diagnosing-bugs`;
- versão ou API de biblioteca: `mattpocock-skills:research` + Context7;
- antes de cada commit e de dizer "pronto": `superpowers:verification-before-completion`, depois `duora-etapa`;
- revisão: `superpowers:requesting-code-review` e `mattpocock-skills:code-review`.

## Restrições globais
- **Princípios de engenharia dos contratos** (Clean Code, Refactoring, Clean Architecture, GoF, DDIA). Na 1a:
  - toda task preserva o comportamento, e os commits de porte usam o tipo `refactor`, separados dos de `build`, `test` e `docs`. Nenhum commit mistura porte com mudança visível; as pendências da Etapa 1 (Tasks 9 e 10) têm commits `fix` próprios;
  - o `main.js` (função `iniciar` de ~550 linhas: Long Function, Divergent Change) é desfeito por Extract Function em módulos profundos (`novidade`, `foco`, `aviso`, `sessao`). As fases da UI são um tipo selado (R6).
- Valem as restrições da Etapa 0 e da Etapa 1: Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; `-Xlint:all -Werror` e Spotless; um comportamento por teste; E2E sem skip nos três navegadores; comandos pesados (Maven, Blender, emulador) um por vez com a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`.
- **Versões exatas, sem `^` nem `~`.** Para cada pacote, a versão mais nova da linha abaixo com pelo menos 14 dias de publicada no dia da execução (`npm view <pacote> time --json`), conferida no Context7:
  - Node `v26.13.0` (LTS "Lithium"), pnpm 12.x (também em `packageManager` do `package.json`);
  - vite 8.3.x, @vitejs/plugin-react 6.x, react e react-dom 19.3.x, @types/react e @types/react-dom 19.x;
  - typescript **6.0.x** (o typescript-eslint 8 exige `<6.1`; o TS 7 fica para depois);
  - tailwindcss e @tailwindcss/vite 4.3.x;
  - vitest 5.1.x, jsdom 27.x, @testing-library/react 16.3.x, @testing-library/user-event 14.x, @testing-library/jest-dom 6.x;
  - eslint 10.x, typescript-eslint 8.x, eslint-plugin-react-hooks 7.x, prettier 3.x;
  - `com.github.eirslett:frontend-maven-plugin` 1.15.1.
- **TypeScript:** `strict`, `noUncheckedIndexedAccess`, `verbatimModuleSyntax`, `noEmit` (o Vite transpila). Nada de `any` explícito nem `!` não nulo sem comentário que justifique. Lint com `--max-warnings 0` (o equivalente ao `-Werror`).
- **Comportamento, textos e visual idênticos.** Todo texto visível ou acessível sai igual, caractere por caractere. As asserções dos testes Java não mudam. As únicas mudanças permitidas em teste Java são as listadas na Task 7 (sonda do `PollingIT`, caminhos do `ContrasteTest` e do `Estaticos`, e a regra do shell adaptada a arquivos com hash).
- **Ganchos que os testes usam e que precisam continuar existindo, com o mesmo nome:**
  - classes: `.app`, `.topo`, `.cena`, `.painel`, `.ceu`, `.cidade`, `.terraco-crepusculo`, `.terraco-noite`, `.peca`, `.sprite-crepusculo`, `.sprite-noite`, `.selo`, `.miniatura`, `.rotulo-lugar`, `.lugar`, `.meteoro`, `.meteoro-parado`, `.confirmando-saida` (no `.app`), `.ceu-pintado` (no `.app`);
  - ids: `#par`, `#instrucao`, `#aviso`, `#som`, `#estado-som`, `#meteoros`, `#estrelas`, `#janelas`, `#palco`, `#pecas`, `#lugares`, `#montagem`, `#entrada`, `#convite`, `#encerrado`, `#encerrado-texto`, `#confirmar-saida`, `#resultado`, `#resultado-titulo`, `#link-convite`;
  - atributos: `data-momento` (`.app`), `data-lado` (`#meteoros`), `data-peca`, `data-lugar`, `data-dono`, `data-sinalizada`, `data-selecionando` e `data-enviando` (`#palco`), `data-feito` (itens do "O que falta"), `data-tipo` e `data-detalhe` (`#aviso`);
  - o `<title>Antes da primeira estrela</title>` e o `<link rel="manifest" href="/manifest.webmanifest">`.
- **CSP inalterada** (`CabecalhosDeSeguranca.CSP`): nada de `<script>` ou `<style>` inline no HTML nem atributo `style="..."` no HTML estático. Estilo dinâmico só por CSSOM (`el.style.x = ...` ou a prop `style` do React, que usa CSSOM). Nenhuma dependência de CDN; fontes e imagens seguem em `/assets/`.
- **Tailwind sem preflight:** importar só `tailwindcss/theme.css` e `tailwindcss/utilities.css` em camadas, e manter o reset atual em `base.css` (confirmar a sintaxe no Context7, seção "Disabling Preflight"). CSS próprio só para a cena (sprites, céu, meteoros, brilho, halo, lugares), em `estilos/cena.css`. O painel e as telas usam utilitários, com variantes próprias declaradas em `tokens.css`:
  - `@custom-variant celular (@media (max-width: 860px));`
  - `@custom-variant celular-alto (@media (max-width: 860px) and (min-height: 700px));`
  - e as nativas `forced-colors:`, `motion-reduce:`, `aria-pressed:`, `aria-disabled:`, `data-[...]:`.
- **Sem `:has()`** no CSS gerado (a Etapa 1 o tirou por causa de navegadores de celular mais antigos).
- `frontend/node/`, `frontend/node_modules/` e `target/` ficam fora do Git; o `pnpm-lock.yaml` entra.

## Rulings de planejamento (o dono pode reverter)
- **R1. Sem React Router e sem TanStack Query na 1a.** O terraço tem uma tela só, e o seu laço de polling (uma consulta por vez, ETag, recuo 2→4→8→15 s, época que descarta respostas velhas) é medido pelo `PollingIT` com contagem exata de `setTimeout`; o agendamento do TanStack Query mudaria essas contagens. Os dois pacotes também somam cerca de 30 KB gzip, o que estoura a meta de 80 KB. Eles entram na Etapa 2, com as primeiras rotas (cadastro e login) carregadas sob demanda. **Isso pede uma emenda ao contrato** (ver o fim do plano); o controlador aplica a emenda antes de executar este plano. Custo se errado: adicionar os dois provedores em volta do `App`.
- **R2. Saída do Vite em `target/vite/`**, com `emptyOutDir: true`, `assetsDir: "app"` (os arquivos com hash ficam em `/app/`, sem colidir com `/assets/`) e `publicDir: false` no build. No `vite dev`, o `publicDir` aponta para `../src/main/resources/static` e o `/api` vai por proxy para `127.0.0.1:8080`.
- **R3. `sw.js` gerado.** O plugin `pluginServiceWorker` roda no `closeBundle`: lê `.vite/manifest.json`, calcula a impressão, compila `src/sw/sw.ts` como script clássico (IIFE, sem minificar) e escreve `sw.js` com as duas primeiras linhas fixas `const CACHE_VERSION = "duora-<12 hex>";` e `const SHELL = [...];`. Depois apaga `.vite/`, que não deve ser público. As regex do `Estaticos` continuam valendo.
- **R4. O polling fica num chunk próprio** (`app/polling-<hash>.js`, por `build.rolldownOptions.output.codeSplitting.groups`). A sonda do `PollingIT` identifica o laço pela pilha; esse é o único jeito estável de ela achar o módulo depois do bundle.
- **R5. `#aviso` e a cena seguem imperativos.** O `AcessibilidadeFocoIT` conta anúncios por mutação do `#aviso`. O `textContent =` de hoje gera uma mutação a cada aviso, mesmo repetido; o React não gera mutação quando o texto não muda e junta atualizações no mesmo lote. Por isso o `#aviso` é um `<p>` vazio no JSX, preenchido por `criarAviso(el)`, que é o código atual portado.
- **R6. O controlador da sessão é o `main.js` portado**, não uma reescrita em `useReducer`. Ele expõe `obter()`/`assinar()` para o `useSyncExternalStore` e pede foco por dados (`PedidoDeFoco`), aplicados num `useLayoutEffect`. Assim a ordem das decisões continua a mesma de hoje.
  - O estado da tela é uma **união discriminada por `tela`** (State com tipo selado), não um saco de campos anuláveis: cada tela só carrega o que existe nela.
  - Todo `switch (estado.tela)` é exaustivo, com `naoAlcancavel(x: never)` no `default`, e a regra `@typescript-eslint/switch-exhaustiveness-check` fica ligada.
- **R7. Comparação visual:** um pixel conta como diferente quando algum canal muda mais de 32. Cada captura passa com no máximo 0,1% de pixels diferentes e com as mesmas dimensões. Quando falha, corrige-se o CSS, nunca o limite.

## Foco de revisão
1. **O mesmo aviso duas vezes** (duas quedas de conexão com "Conexão de volta.") precisa ser anunciado duas vezes. Coberto pelo teste de `criarAviso` (Task 3) e pelo `AcessibilidadeFocoIT`.
2. **Bundle novo com o service worker antigo no controle:** a navegação vem da rede, e os arquivos `/app/` novos vêm da rede na primeira vez. Nenhum arquivo `/app/` some do cache que o HTML em cache ainda cita. Coberto pelo teste de `rotaDoPedido` (Task 6) e pelo `PwaIT`.
3. **4G lento:** antes de o JS chegar, a página mostra o título e o céu, não uma página em branco. Coberto pelo `PrimeiraPinturaIT` (Task 7).
4. **O polling embutido no chunk principal por engano** zeraria as contagens do `PollingIT` sem explicar o porquê. Coberto pelo `ChunksDoFrontTest.pollingTemChunkProprio` (Task 7).
5. **Celular com navegador mais antigo:** o CSS gerado não pode voltar a usar `:has()`. Coberto pelo `ChunksDoFrontTest.cssSemHas` (Task 7) e pela prova no emulador (Task 8).

---

### Task 1: Capturas visuais de referência (antes da migração)

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/VisualIT.java`
- Criar: `src/test/java/bipotheking/net/duora/visual/ComparadorDeCapturas.java` e `src/test/java/bipotheking/net/duora/visual/ComparacaoVisualTest.java`

**Interfaces (produz):**
- `VisualIT` roda só com `-Dvisual.saida=<dir>` (`@EnabledIfSystemProperty`) e só no Chromium. Grava `<dir>/<estado>-<largura>x<altura>.png` nos tamanhos 1440×900 e 390×844, com DPR 1 e movimento reduzido. Os apelidos são "Ana" e "Bruno". Os estados e a ordem:
  - `entrada` (Ana sem sala), `entrada-convite` (Bruno com `#convite=`), `convite` (Ana esperando);
  - `montagem` (Ana logo depois de Bruno entrar), `selecionando` (Ana com o telescópio selecionado);
  - `com-pecas` (telescópio em C1 e manta em L2 de Ana; banco em C2 e lanterna em R2 de Bruno), `sinal` (Bruno sinalizou a manta, visto por Ana), `confirmar-saida` (Ana com a confirmação aberta);
  - `revelado` (os dois confirmaram, visto por Ana), `encerrado` (Bruno depois de Ana sair).
  - Antes de cada captura: `document.fonts.ready`, todas as `<img>` com `complete`, `.app.ceu-pintado` presente.
  - `Page.ScreenshotOptions`: `fullPage`, `animations(DISABLED)`, `caret(HIDE)` e `mask` no campo "Link para a outra pessoa" (o token muda a cada execução).
- `ComparadorDeCapturas.comparar(Path antes, Path depois, Path diff): Resultado`, com `record Resultado(boolean mesmasDimensoes, long diferentes, long total)`; grava um PNG de diferença em vermelho quando `diferentes > 0`. Usa `javax.imageio`, sem dependência nova, e o limite da R7.
- `ComparacaoVisualTest` roda só com `-Dvisual.antes` e `-Dvisual.depois`: `@TestFactory` com um teste por PNG de `antes`, e diffs em `<depois>/diff/`.

- [ ] **Passo 1:** escrever o `ComparacaoVisualTest` com dois testes de unidade do comparador, que rodam sempre (sem as propriedades):
  - `imagensIguaisTemZeroDiferencas`: duas `BufferedImage` 10×10 iguais → `diferentes == 0`;
  - `umPixelMudadoAlemDoLimiteConta`: um pixel com o canal R a +40 → `diferentes == 1`; outro a +20 → não conta.
- [ ] **Passo 2:** rodar `./mvnw -q test -Dtest=ComparacaoVisualTest`. Deve falhar por falta do comparador.
- [ ] **Passo 3:** implementar `ComparadorDeCapturas` e o `VisualIT`, reaproveitando `TerracoPagina` e `Participantes.novo(...)`.
- [ ] **Passo 4:** gravar a referência **no commit de partida, antes de qualquer mudança no front**:
  `BASE="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.claude/handoff/visual-1a/antes"`, depois `./mvnw -q -Pe2e verify -Dit.test=VisualIT -Dvisual.saida="$BASE"`. Conferir 20 PNGs (10 estados × 2 tamanhos). Rodar duas vezes e comparar as duas rodadas com o `ComparacaoVisualTest`: zero diferenças prova que as capturas são determinísticas.
- [ ] **Passo 5:** commit `test(visual): capturas de referência e comparador para a migração do front` (os PNGs não entram no Git; ficam em `.claude/handoff/`).

### Task 2: Esqueleto do front e build pelo Maven

**Arquivos:**
- Criar: `frontend/{package.json,pnpm-lock.yaml,tsconfig.json,tsconfig.node.json,vite.config.ts,eslint.config.js,.prettierrc.json,.prettierignore,index.html}`
- Criar: `frontend/src/main.tsx` (provisório: monta `<p>Duora</p>`; a Task 7 o substitui)
- Criar: `frontend/src/estilos/{app.css,tokens.css,base.css}`
- Criar: `frontend/src/dominio/{visao.ts,dados.ts,mensagens.ts}` e os testes `dados.test.ts`, `mensagens.test.ts`
- Criar: `frontend/src/teste/configurar.ts` (jest-dom)
- Criar: `docs/adr/0004-front-react.md` (criar `docs/adr/` se não existir)
- Modificar: `pom.xml`, `.gitignore`, `.github/workflows/ci.yml`

**Interfaces (produz):**
- Scripts do `package.json`:
  - `dev`: `vite`;
  - `build`: `tsc -b && vite build`;
  - `lint`: `eslint . --max-warnings 0 && prettier --check .`;
  - `test`: `vitest run`.
- `eslint.config.js`: typescript-eslint `strictTypeChecked`, `react-hooks` e `@typescript-eslint/switch-exhaustiveness-check` como erro.
- `vite.config.ts`, com `defineConfig(({ command }) => ...)`:
  - `build.outDir` `../target/vite`, `emptyOutDir: true`, `assetsDir: "app"`, `manifest: true`;
  - `publicDir`: `false` no build e `../src/main/resources/static` no `serve`;
  - `server.proxy` `/api` → `http://127.0.0.1:8080`;
  - plugins `react()` e `tailwindcss()`;
  - `test`: `environment: "jsdom"`, `setupFiles: ["src/teste/configurar.ts"]`.
- `pom.xml`, com propriedades `node.version` `v26.13.0` e `pnpm.version`, e o `frontend-maven-plugin` com `workingDirectory` `frontend` e as execuções:
  - `install-node-and-pnpm` (fase `initialize`);
  - `pnpm install --frozen-lockfile` (`initialize`);
  - `pnpm run lint` (`process-sources`);
  - `pnpm run build` (`generate-resources`);
  - `pnpm test` (`test`, com `<skip>${skipUnitTests}</skip>`; conferir no Context7 se `skip` vale por execução).
  - **Ainda sem** declarar `target/vite` como recurso. O app servido continua o antigo até a Task 7.
- `dominio/visao.ts`: os tipos da API, campo a campo como o `room/Visao.java` serializa:
  - `Fase` = `"AGUARDANDO_PAR" | "MONTANDO" | "REVELADO"`, `Assento` = `"A" | "B"`;
  - `Visao`, `VisaoPeca` (com `lugaresPermitidos?` e `bloqueio?`), `VisaoSinal`, `VisaoResultado`, `Prontos`, `Confirmacao`.
- `dominio/dados.ts`: porte tipado de `js/dados.js`, com `PECAS`, `LUGARES`, `sprite`, `escolherVariante`, `VARIANTES` (a linha `export const VARIANTES = [900, 1800, 2700];` fica literal, porque o `ShellDoServiceWorkerTest` a lê), `LARGURA_MINIATURA`, `urlSprite` e `carregarCena(): Promise<Geometria>`, além do tipo `Geometria` do `cena.json`.
- `dominio/mensagens.ts`: porte tipado de `js/mensagens.js`, com as mesmas funções e os mesmos textos. `ErroApi` entra como tipo estrutural `{ codigo: string; status: number; motivo: string | null }` para não depender da Task 3.
- `estilos/tokens.css`:
  - o bloco `:root` de `css/app.css` copiado com os mesmos nomes de variável (o `ContrasteTest` vai ler este arquivo);
  - `@theme inline` com `--color-*` e `--font-*` apontando para essas variáveis;
  - as `@custom-variant` das restrições globais.
- `estilos/base.css`: os `@font-face`, o reset e as regras de `body`, `.so-leitor` e foco de `css/app.css`.
- `estilos/app.css`: `@layer theme, base, components, utilities;`, os dois imports do Tailwind sem preflight, `tokens.css`, `base.css` e, a partir da Task 4, `cena.css`.

- [ ] **Passo 1:** escrever os testes Vitest.
  - `escolherVariante`: (390, 1) → 900; (390, 3) → 1800; (1440, 2) → 2700; (4000, 1) → 2700.
  - `sprite`: (`"telescopio"`, `"C2"`) → `"telescopio-dir"`; (`"telescopio"`, `"C1"`) → `"telescopio-esq"`; (`"manta"`, `"L2"`) → `"manta"`.
  - `mensagemDeErro`: um caso por código do `switch` atual, com o texto exato, mais o `default` com o status.
  - `textoEncerrado` (4 motivos), `textoConfirmarSaida` (com e sem parceiro), `textoMotivoConfirmacao` (falta 1, faltam 2, você confirmou, parceiro confirmou, vazio).
  - `textoResultado` com manta e lanterna: `titulo` igual a `"Uma noite de manta e lanterna"`.
  - `textoPecasDoParceiro`: colocou, moveu, tirou, e `null` sem mudança.
- [ ] **Passo 2:** `pnpm test` falha (módulos inexistentes). Portar `dados.ts` e `mensagens.ts`; `pnpm test`, `pnpm run lint` e `pnpm run build` passam.
- [ ] **Passo 3:** ligar o plugin no `pom.xml` e rodar `./mvnw -q verify`. Conferir `target/vite/index.html` e `target/vite/app/*.js`, e que o app servido ainda é o antigo (`target/classes/static/js/main.js` existe).
- [ ] **Passo 4:** CI. Nos dois jobs:
  - `actions/cache` de `frontend/node` com a chave `node-v26.13.0-pnpm-<versão>`;
  - `actions/cache` do store do pnpm (`~/.local/share/pnpm/store`) com a chave `hashFiles('frontend/pnpm-lock.yaml')`;
  - actions fixadas por SHA, como as atuais.
  - Rodar o `actionlint` limpo.
- [ ] **Passo 5:** `.gitignore` com `frontend/node/` e `frontend/node_modules/`. Commit `build(front): esqueleto Vite + React + TS + Tailwind construído pelo Maven`.
- [ ] **Passo 6:** ADR `0004-front-react.md` (Contexto, Decisão, Consequências, Alternativas consideradas). Registrar:
  - Vite + React 19 + TS `strict` + Tailwind 4 sem preflight;
  - build pelo Maven para `target/vite`;
  - cena, aviso e polling imperativos (R4, R5);
  - Router e Query adiados (R1);
  - a meta de 80 KB;
  - alternativas: manter JS nativo, Preact, Svelte.
  - Commit `docs(adr): 0004 front em React com build pelo Maven`.

### Task 3: Núcleo da sessão (API, polling, aviso e controlador)

**Arquivos:**
- Criar: `frontend/src/api/{cliente.ts,polling.ts}` e testes `cliente.test.ts`, `polling.test.ts`
- Criar: `frontend/src/sessao/{sessao.ts,aviso.ts,foco.ts,novidade.ts}` e testes `sessao.test.ts`, `aviso.test.ts`, `novidade.test.ts`
- Modificar: `frontend/vite.config.ts` (grupo `polling` em `codeSplitting.groups`, `test: /src\/api\/polling\.ts/`)

**Interfaces:**
- Consome: `dominio/visao.ts`, `dominio/dados.ts`, `dominio/mensagens.ts` (Task 2).
- Produz:
  - `api/cliente.ts`:
    - `class ErroApi extends Error`, com `status`, `codigo`, `motivo`, `visao` e o getter `transitorio`;
    - `type Resposta = { naoMudou: boolean; visao: Visao | null; etag: string | null }`;
    - `interface Api` com `criarSala`, `entrar`, `obterSala`, `moverPeca`, `sinalizar`, `marcarPronto`, `ajustar` e `sair`, com os mesmos parâmetros de `js/api.js`; `const api: Api`.
  - `api/polling.ts`: `criarPolling(api: Api, { aoReceber, aoFalhar, aoRecuperar }): Polling`, com `iniciar()`, `parar()`, `consultarAgora()` e `aceitar(etag)`. Mesmas constantes (2000, 10000, [2000, 4000, 8000, 15000]) e o mesmo `setTimeout` encadeado; o `setTimeout` é chamado **dentro deste módulo**.
  - `sessao/aviso.ts`: `criarAviso(el: HTMLElement): Aviso`, com `avisar(texto, tipo?)` e `avisarComDetalhe(fixo, variavel, tipo?)`, portados de `painel.js`.
  - `sessao/novidade.ts`, funções puras extraídas de `main.js`:
    - `novidade(anterior: Visao, visao: Visao, propria: boolean): string | null`;
    - `instrucao(visao: Visao, selecionada: string | null, enviando: Acao | null): string`.
  - `sessao/sessao.ts`:
    - `type Tela = "carregando" | "entrada" | "convite" | "montagem" | "encerrado"`;
    - `type Acao = "entrada" | "pecas" | "retirar" | "sinal" | "pronto" | "ajustar" | "sair"`;
    - `type PedidoDeFoco =`
      - `{ alvo: "apelido" | "link-convite" | "titulo-encerrado" | "sair" | "continuar" | "primeiro-lugar" }`;
      - `| { alvo: "item"; peca: string }`;
      - `| { alvo: "apos-recusa"; peca: string }` (primeiro lugar livre ou, se não houver, o item);
    - `type EstadoDaTela =`
      - `| { tela: "carregando" }`
      - `| { tela: "entrada"; comConvite: boolean; erroApelido: string; aviso: string | null }`
      - `| { tela: "convite"; visao: Visao }`
      - `| { tela: "montagem"; visao: Visao; selecionada: string | null; instrucao: string }`
      - `| { tela: "encerrado"; texto: string }`;
    - `type EstadoDaSessao = EstadoDaTela & { enviando: Acao | null; confirmandoSaida: boolean; somLigado: boolean; momento: "crepusculo" | "noite"; ladoTelescopio: string | null }`;
    - `naoAlcancavel(x: never): never` em `sessao/naoAlcancavel.ts`;
    - `criarSessao({ api, aviso, convite, criarPolling? }): Sessao`, com:
      - `obter()` e `assinar(fn)`;
      - `iniciar()`, `escolherPeca(peca, minha)`, `escolherLugar(lugar, bloqueio)`, `retirar()`, `confirmar()`, `ajustar()`;
      - `enviarApelido(apelido)`, `criarOutro()`, `abrirSaida()`, `fecharSaida()`, `encerrar()`, `cancelarSelecao()`, `alternarSom()`;
      - `consumirFoco(): PedidoDeFoco | null`.
    - O corpo é o de `main.js`, decisão por decisão: `aplicarVisao`, `enviar`, `tratarErroDeAcao`, `entrarOuCriar`, `sair`, `encerrarSessao`. Cada `.focus()` de hoje vira um `PedidoDeFoco`, e cada `painel.avisar` vira `aviso.avisar`. A leitura do `#convite=` (com `history.replaceState`) fica no `main.tsx`, que passa o valor.
  - `sessao/foco.ts`: `visivel(el)`, `destinoDoFoco(fase, raiz)` e `levarFocoSeSumiu(ativoAntes, trocouDeFase, raiz)`, portados de `main.js`.

- [ ] **Passo 1:** escrever os testes Vitest.
  - `cliente.test.ts` (fetch falso):
    - 304 → `naoMudou: true`;
    - 409 com `{codigo:"LUGAR_OCUPADO", visao}` → `ErroApi` com a visão;
    - falha de rede → `codigo "REDE"` e `transitorio` verdadeiro;
    - 503 `LIMITE_SALAS` → `transitorio` falso;
    - o cabeçalho `X-Duora: 1` sempre presente.
  - `polling.test.ts` (`vi.useFakeTimers`):
    - `umaConsultaPorVez`: com a resposta segurada, avançar 10 s → uma chamada só;
    - `recuaEmErroTransitorio`: três 500 seguidos → esperas 2000, 4000, 8000;
    - `paraEmErroDefinitivo`: 401 → `aoFalhar(erro, null)` e nenhuma nova chamada;
    - `aceitarDescartaRespostaEmVoo`: `aceitar("x")` durante o voo → `aoReceber` não é chamado com a resposta velha.
  - `aviso.test.ts`:
    - `mesmoTextoDuasVezesGeraDuasMutacoes`: `MutationObserver` do jsdom, `avisar("Conexão de volta.")` duas vezes → duas mutações `childList`;
    - `detalheMudaSemTrocarOTexto`: dois `avisarComDetalhe("Sem conexão…", "…2 s")`/`("…", "…4 s")` → o primeiro nó de texto é o mesmo objeto.
  - `novidade.test.ts`:
    - sinal novo do parceiro → `"Bruno sugeriu mover a manta."`;
    - parceiro entrou → `"Bruno entrou no terraço. Escolham as primeiras peças."`;
    - REVELADO → MONTANDO → `"Bruno voltou a ajustar o terraço."`;
    - confirmação desfeita pela mudança → o texto atual exato.
    - `instrucao`: um caso por ramo de `main.js:208-232`.
  - `sessao.test.ts` (api e polling falsos):
    - `semAssentoMostraEntrada`: polling falha com 401 → `tela "entrada"`, `comConvite false`;
    - `encerradoNaoCarregaVisao`: depois de sair, o estado é `{ tela: "encerrado", texto }` e não tem `visao` (o tipo também impede isso);
    - `recusa409ComVisaoPedeFocoAposRecusa`: `moverPeca` rejeita 409 com visão → `consumirFoco()` igual a `{ alvo: "apos-recusa", peca: "telescopio" }`;
    - `segundaAcaoDuranteEnvioEIgnorada`: duas chamadas a `confirmar()` → uma chamada a `marcarPronto`;
    - `sairComErroDeRedeReabreOPolling`: `sair` rejeita com `REDE` → `tela "montagem"` e o polling iniciado de novo;
    - `conviteDoMesmoNavegadorAvisa`: com convite e primeira visão já com sala → o aviso atual "Este navegador já está em um terraço…".
- [ ] **Passo 2:** rodar `pnpm test`: falha (módulos inexistentes).
- [ ] **Passo 3:** portar os módulos e o grupo de chunk. Conferir com `pnpm run build` que existe `target/vite/app/polling-*.js` e que ele contém `setTimeout`.
- [ ] **Passo 4:** `pnpm test` e `pnpm run lint` verdes. Commit `refactor(front): API, polling e sessão do terraço em TypeScript`.

### Task 4: Cena imperativa em componente React

**Arquivos:**
- Criar: `frontend/src/cena/{cena.ts,meteoros.ts,som.ts,ceu.ts}` e o teste `meteoros.test.ts`
- Criar: `frontend/src/componentes/Cena.tsx` e o teste `Cena.test.tsx`
- Criar: `frontend/src/estilos/cena.css` (as regras de cena, céu, meteoros, peças, selos e lugares de `css/app.css`, linhas ~144–571, mais as partes de cena dos blocos `@media` de celular, `forced-colors` e `prefers-reduced-motion`, copiadas sem mudar valores)
- Modificar: `frontend/src/estilos/app.css` (importa `cena.css`)

**Interfaces:**
- Consome: `dominio/dados.ts` e `dominio/visao.ts` (Task 2).
- Produz:
  - `cena/cena.ts`:
    - `criarCena(opcoes: { palco; camadaPecas; camadaLugares; geometria: Geometria; aoEscolherPeca(peca: string, minha: boolean); aoEscolherLugar(lugar: string, bloqueio: string | null) }): CenaImperativa`, com `desenhar(visao, selecionada)`, `limpar()` e `focarPrimeiroLugar(): boolean`;
    - `mostrarTerraco(palco, variantes): number`. Porte literal de `cena.js`, com o mesmo DOM, as mesmas classes, os mesmos `aria-label` e as porcentagens com 3 casas.
  - `cena/ceu.ts`: `desenharCeu(svg, janelas)` e `prepararCeuPintado(app)`, com a mesma semente 7 e as mesmas contagens.
  - `cena/meteoros.ts`: `criarMeteoros(camada, { aoMeteoro, movimentoReduzido }): Meteoros`, com `mostrar(lado)`, `limpar()` e `reiniciar()`.
  - `cena/som.ts`: `criarSom(): Som`, com `definir(ligado)`, `meteoro()` e `revelacao()`. Mesmos arquivos e volumes.
  - `componentes/Cena.tsx`, com as props:
    - `visao: Visao | null`, `selecionada: string | null`, `enviando: boolean`;
    - `momento: "crepusculo" | "noite"`, `ladoTelescopio: string | null`, `geometria: Geometria | null`;
    - `aoEscolherPeca`, `aoEscolherLugar`, `aoMeteoro()`;
    - `ref: Ref<{ focarPrimeiroLugar(): boolean }>`.
    - Renderiza `<section class="cena">` com `.ceu`, `#estrelas`, `#meteoros`, `#palco` (cidades, horizonte, `#janelas`, os dois `<img class="terraco ...">` sem `src`, `#pecas` e `#lugares`), com a mesma árvore de `index.html:31-49`.
    - No `useLayoutEffect` de montagem: `desenharCeu`, `mostrarTerraco(palco, VARIANTES)` e `criarMeteoros`.
    - Quando chega a `geometria`: `criarCena`.
    - A cada `visao` ou `selecionada`: guarda o `data-peca`/`data-lugar` focado nas camadas, chama `desenhar` ou `limpar`, devolve o foco ao equivalente (a lógica de `lembrarFoco`) e grava `data-enviando` e `data-selecionando`.
    - Quando `momento` vira `"noite"`: `meteoros.mostrar(ladoTelescopio)`. Fora disso: `limpar()`.
    - O `prefers-reduced-motion` muda → `reiniciar()`.

- [ ] **Passo 1:** escrever os testes.
  - `meteoros.test.ts` (timers falsos):
    - com movimento reduzido, `mostrar("esquerdo")` → 4 `.meteoro-parado` e `data-lado="esquerdo"`;
    - `limpar()` → 0 `.meteoro` e sem `data-lado`.
  - `Cena.test.tsx` (Testing Library, geometria mínima de 2 lugares):
    - `pecaDoParceiroTemNomeComDonoELugar`: botão "Manta de Bruno, frente, à esquerda. Ative para sugerir que mova";
    - `selecionadaMostraLugaresPermitidos`: com `selecionada "telescopio"` e `lugaresPermitidos ["C1"]` → botão "Colocar o telescópio: centro, à esquerda";
    - `focoNumaPecaSobreviveAoRedesenho`: focar `[data-peca=manta]`, re-render com outra visão → o foco continua num `[data-peca=manta]`.
- [ ] **Passo 2:** rodar `pnpm test`: falha.
- [ ] **Passo 3:** portar, e copiar o CSS de cena para `cena.css`. Não converter este CSS para utilitários (é o "CSS próprio" que o spec permite).
- [ ] **Passo 4:** `pnpm test`, `pnpm run lint` e `pnpm run build` verdes. Commit `refactor(front): cena, céu, meteoros e som num componente com ref`.

### Task 5: Painel e telas em componentes com Tailwind

**Arquivos:**
- Criar: `frontend/src/componentes/{Moldura,Topo,Entrada,Convite,Encerrado,Par,Falta,Inventario,Resultado,Acoes,Painel}.tsx` e testes `Topo.test.tsx`, `Entrada.test.tsx`, `Inventario.test.tsx`, `Falta.test.tsx`
- Criar: `frontend/src/dominio/apelido.ts` (`validarApelido`, de `telas.js`) e `apelido.test.ts`

**Interfaces:**
- Consome: `dominio/*` (Task 2). O tipo `Acao` é declarado aqui como `string | null` e trocado pelo de `sessao.ts` na Task 7.
- Produz componentes de apresentação: recebem props e chamam callbacks, sem estado de servidor. Todo botão "indisponível" usa `aria-disabled="true"` e ignora o clique, como hoje.
  - `Moldura({ momento, confirmandoSaida, ceuPintado, children })`: `<div class="app">` com `data-momento`, as classes `confirmando-saida`/`ceu-pintado` e o grid de layout (`css/app.css` 101–143 e os `@media` de celular).
  - `Topo({ mostrarSair, nomeParceiro, confirmando, enviandoSaida, aoAlternarSaida, aoContinuar, aoEncerrar })`: o `<header class="topo">` com `h1.titulo`, `#sair` (`aria-expanded`, `aria-controls`), o grupo `#confirmar-saida` (`aria-labelledby`) com `#continuar` e `#encerrar`. O Esc dentro do botão ou do grupo fecha a confirmação com `stopPropagation`, e não fecha durante o envio.
  - `Entrada({ comConvite, enviando, erro, aoEnviar(apelido) })`: o form `#entrada` `novalidate`, `#apelido` (`maxlength 20`, `aria-describedby`, `aria-invalid` quando há erro), `#apelido-dica`, `#apelido-erro` e o botão `#entrar`, com os textos de `telas.js`. Valida com `validarApelido` antes de chamar `aoEnviar`, e foca o campo quando a validação falha.
  - `Convite({ link, aoCopiar })`: `#convite`, `#link-convite` somente leitura e `#copiar`; a dica `#convite-dica` com o texto atual.
  - `Encerrado({ texto })`: `#encerrado`, `h2#encerrado-titulo` com `tabindex -1`, `#encerrado-texto` e `#criar-outro`.
  - `Par({ visao })`: `ul#par`, com a mesma marcação de `painel.js:28-42`.
  - `Falta({ visao })`: a seção "O que falta" e `ul#falta`, com `li[data-feito]`, os mesmos SVG (como JSX) e o sufixo `.so-leitor` ", pronto"/", falta".
  - `Inventario({ visao, selecionada, enviando, aoEscolher(peca), aoRetirar })`: a seção "Suas peças", `ul#inventario` com o `li.grupo-escolha`, botões `.item[data-peca]` com `aria-pressed`, `img.miniatura`, `.nome-item` e `.estado-item`, e o `#retirar`. Os textos são os de `painel.js:72-126`.
  - `Resultado({ visao, enviando, aoAjustar })`: `#resultado`, `h2#resultado-titulo` com `tabindex -1`, `#resultado-texto` e `#ajustar`.
  - `Acoes({ visao, enviando, somLigado, aoConfirmar, aoAlternarSom })`: `.acoes` com `#pronto`, `#motivo` e `#som` (`aria-pressed`), mais `#estado-som` com `aria-hidden`.
  - `Painel(props)`: `<main class="painel" aria-label="Painel do terraço">`, com `#par`, `#instrucao`, a tela da vez (por `switch` exaustivo sobre `estado.tela`), `div#montagem` (Falta, Inventario, Resultado, Acoes) e o `<p id="aviso" role="status" aria-live="polite">` **vazio**, cuja ref vai para `refAviso`.
- O estilo é convertido para utilitários, declaração por declaração, a partir de `css/app.css` (linhas ~101–143 e ~572–942, mais as partes de painel dos `@media`). Os estados viram variantes (`aria-pressed:`, `aria-disabled:`, `data-[feito=true]:`, `forced-colors:`, `motion-reduce:`, `celular:`, `celular-alto:`). O que depende do `.app` (`.app.confirmando-saida .topo`) usa `group` com `group-[.confirmando-saida]:`.

- [ ] **Passo 1:** escrever os testes.
  - `apelido.test.ts`: `""` → "Escreva um apelido."; 21 caracteres → "Use no máximo 20 caracteres."; `"a\u0007"` → "Use só letras, números e símbolos comuns."; `"Ana"` → `""`.
  - `Entrada.test.tsx`:
    - `apelidoVazioMostraErroEFocaOCampo`: enviar vazio → `aria-invalid="true"`, o texto de erro e o foco no campo, sem chamar `aoEnviar`;
    - `comConviteOBotaoDizEntrarNoTerraco`.
  - `Topo.test.tsx`:
    - `escFechaAConfirmacao`: aberto, Esc no grupo → `aoAlternarSaida` chamado;
    - `continuarDuranteEnvioNaoFecha`: `enviandoSaida` verdadeiro, clicar "Continuar no terraço" → nada.
  - `Inventario.test.tsx`:
    - `segundaPecaMostraATroca`: o texto "Troca a manta por o rádio" exatamente como hoje (a 1c corrige a contração; aqui o texto não muda);
    - `itemBloqueadoTemAriaDisabled`.
  - `Falta.test.tsx`: `quatroLinhasComEstadoParaOLeitor`.
- [ ] **Passo 2:** rodar `pnpm test`: falha.
- [ ] **Passo 3:** implementar os componentes e a conversão de estilo.
- [ ] **Passo 4:** `pnpm test`, `pnpm run lint` e `pnpm run build` verdes. Commit `refactor(front): painel e telas em React com Tailwind`.

### Task 6: Service worker gerado pelo build

**Arquivos:**
- Criar: `frontend/build/{service-worker.ts,impressao.ts}` e testes `frontend/build/{impressao.test.ts,service-worker.test.ts}`
- Criar: `frontend/src/sw/{sw.ts,rotas.ts}` e o teste `rotas.test.ts`
- Criar: `frontend/src/pwa.ts` (porte de `js/pwa.js` da onda de correções: registro depois do `load`)
- Modificar: `frontend/vite.config.ts` (`pluginServiceWorker({ raizEstatica: "../src/main/resources/static", assetsDoShell: [...] })`)
- Criar: `docs/adr/0005-pwa-cache-por-impressao.md`

**Interfaces:**
- Produz:
  - `impressao.ts`: `impressao(arquivos: { caminho: string; conteudo: Uint8Array }[]): string`.
    - SHA-256 sobre os arquivos em ordem de `caminho`: para cada um, os bytes UTF-8 do caminho, um byte zero e o conteúdo.
    - Devolve os 12 primeiros hex. É o mesmo algoritmo do `ServiceWorkerVersaoTest`.
  - `service-worker.ts`: `pluginServiceWorker(opcoes: { raizEstatica: string; assetsDoShell: string[] }): Plugin`, com `apply: "build"`. No `closeBundle`:
    1. lê `<outDir>/.vite/manifest.json`;
    2. monta o `SHELL` = `"/"`, `"/index.html"`, `"/manifest.webmanifest"`, o `file` da entrada `index.html` e, em recursão, os seus `imports` e `css`, mais os `assetsDoShell` (as 3 fontes, `icone.svg` e os 5 ícones de hoje);
    3. calcula a impressão sobre todo arquivo de `outDir` (menos `sw.js` e `.vite/`) mais todo arquivo de `raizEstatica` (menos os que começam com `.`), com caminhos relativos à raiz servida;
    4. compila `src/sw/sw.ts` com a API `build()` do Vite (`lib`, `formats: ["iife"]`, `minify: false`, `emptyOutDir: false`);
    5. grava `<outDir>/sw.js` com as duas linhas fixas da R3 seguidas do código compilado;
    6. apaga `<outDir>/.vite/`.
    - Um item do `SHELL` sem arquivo, em `outDir` ou em `raizEstatica`, falha o build com mensagem clara.
  - `sw/rotas.ts`: `rotaDoPedido(p: { url: URL; origem: string; metodo: string; modo: RequestMode; temRange: boolean }): "ignorar" | "navegacao" | "rede-guardando" | "cache-primeiro" | "rede"`.
    - `"ignorar"`: não é GET, outra origem, `/api/` ou `Range`;
    - `"navegacao"`: navegação;
    - `"rede-guardando"`: `/assets/*.json`;
    - `"cache-primeiro"`: o resto de `/assets/` e `/app/`;
    - `"rede"`: o resto.
  - `sw/sw.ts`: o `sw.js` da onda de correções em TS. Usa `declare const CACHE_VERSION: string; declare const SHELL: string[];`, o precache com `cache: "reload"`, o `activate` que apaga os `duora-*` antigos, e o `fetch` pelo `rotaDoPedido`.
  - `pwa.ts`: `registrarServiceWorker(): void`, que registra `/sw.js` com `scope "/"` como script clássico.

- [ ] **Passo 1:** escrever os testes.
  - `impressao.test.ts`:
    - `ordemDosArquivosNaoImporta` (mesma lista embaralhada → mesmo valor);
    - `valorConhecido`: os arquivos `a.txt` = "x" e `b/c.txt` = "yz" → o hex calculado à mão no teste com `node:crypto`, com o mesmo algoritmo escrito por extenso.
  - `service-worker.test.ts` (diretório temporário com um manifest falso e arquivos):
    - `shellTemAEntradaESeusImportsECss`;
    - `swComecaComAsDuasLinhasFixas`: regex `^const CACHE_VERSION = "duora-[0-9a-f]{12}";\nconst SHELL = \[`;
    - `itemDoShellSemArquivoFalha`;
    - `pastaViteEApagada`.
  - `rotas.test.ts`: `POST` → ignorar; `/api/sala` → ignorar; com `Range` → ignorar; navegação → navegacao; `/assets/cena/cena.json` → rede-guardando; `/assets/cena/banco-noite-900.webp` → cache-primeiro; `/app/index-abc.js` → cache-primeiro; `/manifest.webmanifest` → rede.
- [ ] **Passo 2:** rodar `pnpm test`: falha.
- [ ] **Passo 3:** implementar e ligar no `vite.config.ts`. Rodar `pnpm run build` e conferir `target/vite/sw.js` (as duas linhas, mais a ausência de `target/vite/.vite`).
- [ ] **Passo 4:** `pnpm test` e `pnpm run lint` verdes. Commit `build(pwa): service worker gerado do manifest do Vite com versão pela impressão`.
- [ ] **Passo 5:** ADR `0005-pwa-cache-por-impressao.md`. Registrar:
  - shell pelo manifest do Vite;
  - `CACHE_VERSION` pela impressão SHA-256, conferida pelo `ServiceWorkerVersaoTest`;
  - nada de `/api` no cache;
  - sem `skipWaiting` (R3 da Etapa 1);
  - `/app/` imutável;
  - alternativas: Workbox (`vite-plugin-pwa`) e versão manual.
  - Commit `docs(adr): 0005 PWA com cache versionado pela impressão`.

### Task 7: Integração e corte para o front novo

**Arquivos:**
- Criar: `frontend/src/{App.tsx,main.tsx,useSessao.ts}`; reescrever `frontend/index.html`
- Modificar: `pom.xml` (recurso `target/vite` → `static`; `maven-clean-plugin` na fase `initialize` apagando `target/classes/static/app`)
- Remover: `src/main/resources/static/{index.html,sw.js,css/,js/}`
- Modificar: `room/CabecalhosDeSeguranca.java` (`/app/**` com `Cache-Control: public, max-age=31536000, immutable`)
- Modificar só no mecânico:
  - `e2e/PollingIT.java`: na `SONDA`, `includes("/js/api.js")` vira `/\/app\/polling-[^/]+\.js/.test(String(new Error().stack))`;
  - `visual/ContrasteTest.java`: lê `frontend/src/estilos/tokens.css` do disco, com o mesmo regex e os mesmos pares;
  - `pwa/Estaticos.java`: `RAIZ` vira `target/classes/static`; `variantesDoDadosJs()` lê `frontend/src/dominio/dados.ts`;
  - `pwa/ShellDoServiceWorkerTest.java`: `todoModuloJsEstaNoShell` vira `todoArquivoCitadoNoIndexEstaNoShell` (cada `script[src]`, `link[rel=modulepreload]` e `link[rel=stylesheet]` do `index.html` gerado está no `SHELL`);
  - `pwa/ServiceWorkerVersaoTest.java`: só a mensagem do `.as(...)`, que passa a citar o `pluginServiceWorker`.
- Criar testes: `pwa/ChunksDoFrontTest.java`, `pwa/TamanhoDoJsInicialTest.java`, `room/CacheDosBundlesTest.java` (no estilo do `PwaApiTest`) e `e2e/PrimeiraPinturaIT.java`

**Interfaces:**
- Consome: Tasks 2–6.
- Produz:
  - `useSessao(sessao: Sessao): EstadoDaSessao`, por `useSyncExternalStore`. O callback de assinatura guarda `document.activeElement` **antes** do re-render. Um `useLayoutEffect` depois de cada commit:
    - aplica `sessao.consumirFoco()` aos elementos (por id ou por `data-peca`; `"primeiro-lugar"`/`"apos-recusa"` vão pela ref da `Cena`);
    - em seguida, `levarFocoSeSumiu(ativoAntes, trocouDeFase, raiz)`.
  - `App.tsx`:
    - cria a `Sessao` uma vez, com `criarAviso(refAviso.current)`;
    - carrega a geometria com `carregarCena()`; com erro, mostra no `#aviso` "Não foi possível carregar a cena: <mensagem>", como hoje;
    - liga o `criarSom()` ao `somLigado` e toca `revelacao()` na passagem `crepusculo → noite` vista ao vivo;
    - compõe `Moldura`, `Topo`, `Cena` e `Painel`;
    - o Esc global com peça selecionada chama `sessao.cancelarSelecao()`.
  - `main.tsx`: lê e limpa o `#convite=` (`lerConvite` de `main.js`), monta `<App convite=... />` em `#raiz` com `createRoot` e chama `registrarServiceWorker()`.
  - `index.html`: o `<head>` atual inteiro, com `<script type="module" src="/src/main.tsx">` no lugar do `main.js` e sem o `<link rel="stylesheet">` (o CSS entra pelo `import "./estilos/app.css"`). O `<body>` tem `<div id="raiz">` com um esqueleto estático (só classes, sem `style`): `.app[data-momento=crepusculo]` → `header.topo` → `h1.titulo` "Antes da primeira estrela", mais `section.cena` → `div.ceu`. O `createRoot` substitui esse esqueleto.
  - `ChunksDoFrontTest`:
    - `pollingTemChunkProprio`: existe exatamente um `app/polling-*.js` em `target/classes/static`;
    - `cssSemHas`: nenhum `app/*.css` contém `:has(`.
  - `TamanhoDoJsInicialTest.jsInicialCabeNaMeta`:
    - soma o gzip nível 9 de cada `script[type=module][src]` e `link[rel=modulepreload][href]` do `index.html` gerado;
    - grava `target/tamanho-js-inicial.txt` com os bytes e os KB;
    - exige no máximo 81 920 bytes.
  - `CacheDosBundlesTest.bundleComHashEImutavel`: `GET /app/<o arquivo do index>` → `Cache-Control` contém `immutable`; `GET /index.html` não contém.
  - `PrimeiraPinturaIT.tituloApareceAntesDoJs`: rota `**/app/*.js` atrasada 3 s; o título "Antes da primeira estrela" (`heading`) fica visível em até 1 s.

- [ ] **Passo 1:** escrever os testes Java novos (`ChunksDoFrontTest`, `TamanhoDoJsInicialTest`, `CacheDosBundlesTest`, `PrimeiraPinturaIT`) e fazer as mudanças mecânicas listadas. Rodar `./mvnw -q verify`: devem falhar, porque `target/vite` ainda não é servido.
- [ ] **Passo 2:** implementar `useSessao`, `App`, `main.tsx` e `index.html`; declarar o recurso e o `clean` no `pom.xml`; remover os arquivos antigos de `static/`; ajustar o `CabecalhosDeSeguranca`.
- [ ] **Passo 3:** `./mvnw -q verify` verde. O valor de `target/tamanho-js-inicial.txt` vai para o resumo. **Se passar de 80 KB, parar e levar a escolha ao dono** (ver "Decisões do dono").
- [ ] **Passo 4:** e2e completo em chromium: `./mvnw -q -Pe2e verify -Dnavegador=chromium`, verde sem mudar asserção. Cada falha passa por `superpowers:systematic-debugging`; a correção vai no front, nunca no teste.
- [ ] **Passo 5:** conferir no Chromium, com a página aberta, que o console não tem violação de CSP (`page.onConsoleMessage` num teste rápido ou no Playwright MCP). Commit `refactor(front): app em React servido pelo Spring no lugar do JS nativo`.

### Task 8: Prova da etapa

**Arquivos:**
- Modificar: `README.md` (seção "Front": `pnpm dev` com o Spring em 8080, comandos e o tamanho medido), `.claude/handoff/_mapa.md` (stack, estrutura `frontend/`, comandos)

- [ ] **Passo 1:** e2e e axe em firefox e webkit: `./mvnw -q -Pe2e verify -Dnavegador=firefox` e depois `webkit`, um por vez sob a trava. Tudo verde.
- [ ] **Passo 2:** gravar as capturas "depois" com o `VisualIT` em `.../visual-1a/depois` e rodar `./mvnw -q test -Dtest=ComparacaoVisualTest -Dvisual.antes=<...>/antes -Dvisual.depois=<...>/depois`. Verde. Se algum estado falhar, abrir o PNG de `diff/`, corrigir o CSS (Task 5 ou 4) e repetir.
- [ ] **Passo 3:** prova no Android, por agente: `scripts/android/emulador.sh subir`, `./mvnw spring-boot:run`, `node scripts/android/pwa-check.mjs --porta 8080` (com as correções da Task 9). Conferir:
  - a instalabilidade sem erro;
  - a captura em pé, tirada depois do `verificarTerraco`, com o terraço nítido;
  - a captura deitada no layout da Task 10;
  - a captura `-standalone.png` do app aberto pelo atalho, sem a barra do navegador.
- [ ] **Passo 4:** README e `_mapa.md`. Registrar no handoff o tamanho do JS inicial, o resultado da comparação visual e as capturas do emulador. Commit `docs: front em React, comandos e tamanho do JS inicial`.

### Task 9: Pendências da Etapa 1 no repositório e no `pwa-check`

Pendências da revisão da Etapa 1, que entram aqui porque a 1a reescreve o shell.

**Arquivos:**
- Modificar: `.gitattributes`, `scripts/android/pwa-check.mjs`
- Criar: `scripts/android/pwa-check.test.mjs` (`node --test`, sem dependências) e `src/test/java/bipotheking/net/duora/pwa/AtributosDoGitTest.java`

**Interfaces (produz), em `pwa-check.mjs`, exportadas, com o `main()` só rodando quando o arquivo é executado direto:**
- `comandosParaRestaurar({ automatica, usuario }): string[]`: para cada valor `"null"`, `settings delete system <chave>`; para os outros, `settings put system <chave> <valor>`.
- `PASSOS: readonly string[]`, a ordem do `main()`: `"instalabilidade"`, `"verificarTerraco"`, `"capturarEmPe"`, `"capturarDeitado"`, `"instalar"`, `"abrirInstalado"`, `"capturarStandalone"`, `"capturarHome"`. O `main()` percorre essa lista.
- `abrirInstalado(adb, escopo)`: abre o WebAPK pelo pacote (`am start` com a URL do escopo) ou, sem WebAPK, toca no ícone do atalho pelo `uiautomator dump` (reusa `lerTela`/`esperarNo`).
- `ehStandalone(cdp): Promise<boolean>`: avalia `matchMedia('(display-mode: standalone)').matches` na aba do app instalado. Se der falso, o script sai com código 1.

- [ ] **Passo 1:** escrever os testes.
  - `pwa-check.test.mjs`:
    - `rotacaoNulaVoltaComDelete`: `{ automatica: "1", usuario: "null" }` → contém `settings delete system user_rotation` e `settings put system accelerometer_rotation 1`;
    - `rotacaoComValorVoltaComPut`;
    - `terracoVerificadoAntesDaCapturaEmPe`: o índice de `"verificarTerraco"` é menor que o de `"capturarEmPe"`;
    - `standaloneDepoisDeInstalar`.
  - `AtributosDoGitTest` (pula com `Assumptions` se `git` não existir):
    - `textoDeStaticNormalizaParaLf`: `git check-attr text eol -- src/main/resources/static/manifest.webmanifest` → `text: auto` e `eol: lf`;
    - `webpEBinario`: o `.webp` do terraço tem `text: unset`.
- [ ] **Passo 2:** RED (`node --test 'scripts/android/*.test.mjs'` e `./mvnw -q test -Dtest=AtributosDoGitTest`).
- [ ] **Passo 3:** implementar.
  - `.gitattributes`: `src/main/resources/static/** text=auto eol=lf`, com as linhas de binários mantidas.
  - `git add --renormalize .` e conferir com `git status` que nenhum arquivo mudou de conteúdo (se mudar, o commit traz só a normalização, separado).
  - `pwa-check.mjs` com as funções acima, extraídas do `main()` atual por Extract Function. A captura em pé passa para depois do `verificarTerraco`, e entram `abrirInstalado` e `capturarStandalone`.
- [ ] **Passo 4:** testes verdes e uma rodada real no emulador (`emulador.sh subir`, app no ar, `pwa-check.mjs --porta 8080`). Conferir:
  - os 4 PNGs: em pé, deitado, standalone e home;
  - que `settings get system user_rotation` volta a `null` quando era `null`.
  - Commits separados: `refactor(android): passos do pwa-check em funções testáveis` e `fix(android): rotação nula restaurada, captura em pé após a verificação e modo standalone`; mais `fix(git): static com text=auto eol=lf`.

### Task 10: Layout deitado no celular

**Arquivos:**
- Modificar: `frontend/src/componentes/Moldura.tsx` (e onde o layout de celular estiver)
- Teste: `src/test/java/bipotheking/net/duora/e2e/LayoutCelularIT.java` (testes novos; os existentes ficam intactos)

**Interfaces:**
- Consome: Tasks 5 e 7.
- Layout pretendido para celular deitado (`(max-width: 860px) and (max-height: 500px) and (orientation: landscape)`, variante `celular-deitado`):
  - duas colunas: a cena à esquerda (cerca de 55%) e o painel à direita (cerca de 45%), com rolagem própria;
  - nenhuma área vazia maior que a cena, e nenhuma rolagem lateral (o teste da Etapa 1 continua).
  - Custo se errado: trocar as colunas por cena em cima e painel embaixo.

- [ ] **Passo 1:** diagnóstico. Capturar 844×390 nas fases entrada, montagem e revelado (Playwright MCP ou `LayoutCelularIT` com `capturar`), e comparar com a captura deitada do emulador.
  - Se o painel some e a metade direita fica vazia, é defeito: seguir.
  - Se o layout já é o pretendido acima, registrar no handoff, manter só os testes do passo 2 e fechar a task.
- [ ] **Passo 2:** escrever os testes em `LayoutCelularIT`:
  - `deitadoMostraCenaEPainelLadoALado`: em 844×390 na montagem, as caixas de `.cena` e `.painel` estão dentro da janela, com `cena.right <= painel.left + 1`, e o heading "Suas peças" alcançável rolando só o painel;
  - `deitadoSemAreaVaziaADireita`: a borda direita do painel fica a ≥ 95% da largura da janela.
- [ ] **Passo 3:** RED, depois corrigir com a variante `celular-deitado` na `Moldura`.
- [ ] **Passo 4:** `LayoutCelularIT`, `AcessibilidadeLayoutIT` e `AxeIT` em chromium, verdes. A comparação visual da Task 8 não muda, porque ela usa 1440×900 e 390×844. Commit `fix(front): celular deitado com cena e painel lado a lado`.

## Ordem e paralelismo
- **Primeiro, em paralelo (3 worktrees):**
  - Task 1, que grava a referência no commit de partida e não toca no front;
  - Task 2, o esqueleto. Ela não muda o app servido, então a referência da Task 1 continua válida;
  - Task 9 (`.gitattributes` e `pwa-check`), com arquivos disjuntos. A rodada no emulador entra na fila de pesados.
- **Depois da Task 2, em paralelo (4 worktrees, o limite de agentes):** Tasks 3, 4, 5 e 6. Cada uma cria só os seus arquivos em `frontend/src/<pasta>/` ou `frontend/build/`. Os únicos arquivos compartilhados são:
  - `vite.config.ts` (3 e 6, em blocos diferentes);
  - `estilos/app.css` (4, uma linha de import).
  - Os conflitos esperados no merge são triviais. Rodam só `pnpm`, sem Maven, exceto o passo de build de conferência.
- **Depois:** a Task 7, sozinha, porque integra tudo e roda o e2e completo.
- **Em seguida:** a Task 10 (layout deitado), que depende do layout em React.
- **No fim:** a Task 8 (e2e firefox/webkit, comparação visual e emulador, um pesado por vez).

## Decisões do dono
- **Só se a Task 7 medir mais de 80 KB gzip no JS inicial.** Opções:
  - (a) aceitar o valor medido como nova meta e registrá-lo. Custo zero, mas o carregamento fica mais lento no 4G;
  - (b) trocar `react-dom` por `preact/compat`, que corta cerca de 45 KB. Custo: compatibilidade a vigiar a cada lib nova;
  - (c) adiar a medição até a Etapa 2.
  - **Recomendação: (a)** se o excesso for até 10 KB; senão, (b).

## Emenda proposta aos contratos (aplicar antes de executar)
Em "Front (a partir da Etapa 1a) → Rotas e dados", acrescentar: "React Router e TanStack Query entram na Etapa 2, com as primeiras rotas carregadas sob demanda. Até o SSE (Etapa 5), o encontro usa o laço de polling próprio (`api/polling.ts`), e o JS inicial do encontro fica em no máximo 80 KB gzip, medido pelo `TamanhoDoJsInicialTest`."
