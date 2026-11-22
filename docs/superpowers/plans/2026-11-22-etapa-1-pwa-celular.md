# Etapa 1 — PWA e imagens no celular: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** o terraço fica nítido e sem rolagem lateral no celular, e o Duora pode ser instalado como PWA no Android.

**Arquitetura:** os renders ganham três larguras, e o JS escolhe a menor que cobre a largura da cena × DPR. O céu usa `image-set()`. Um service worker na raiz guarda o shell e os assets, e nunca toca em `/api`. O manifest e os ícones seguem os critérios de instalação do Chrome. Nos testes, o service worker fica bloqueado por padrão, porque o WebKit ignora `page.route` em página controlada, e liberado só nos testes de PWA.

**Stack:** Java 25, Spring Boot 4.1.3, JS nativo (ES modules, sem bundler), Blender (Cycles, headless), `cwebp`, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1). Fontes da pesquisa:
- https://web.dev/articles/install-criteria
- https://web.dev/articles/service-worker-lifecycle
- https://web.dev/articles/maskable-icon
- https://playwright.dev/docs/service-workers
- https://playwright.dev/java/docs/api/class-cdpsession

## Restrições globais
- Valem todas as restrições globais da Etapa 0 (`docs/superpowers/plans/2026-11-21-etapa-0-reabertura.md`):
  - Conventional Commits com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`;
  - Maven pesado um por vez (`pgrep` antes);
  - `-Xlint:all -Werror`, Spotless, um comportamento por teste;
  - E2E sem skip nos três navegadores;
  - pt-BR em voz ativa;
  - nada de caminho local, e-mail ou segredo.
- Suítes locais longas rodam com `caffeinate -dims`, porque o Mac suspende no meio delas.
- Imagens de cena e céu em WebP com qualidade 88. Ícones do manifest em PNG (ver ruling R2).
- O `cena.json` continua em unidades de referência de 1800 px de largura, e as posições dos lugares não mudam.
- O service worker nunca responde, guarda nem intercepta requisições `/api/*` nem métodos diferentes de GET.
- A CSP atual não muda: `default-src 'self'` já cobre o SW e o manifest da mesma origem.

## Rulings de planejamento (o dono pode reverter)
- **R1. Nada de render "retrato" separado.** Os lugares vêm de uma câmera ortográfica única. No celular em pé, o enquadramento já é resolvido pelo `.palco` com 112%. O que melhora a imagem no celular é a densidade (larguras 900/1800/2700), não outra composição. Custo se estiver errado: uma task nova de enquadramento retrato.
- **R2. Ícones em PNG.** O Chrome e o iOS (`apple-touch-icon`) exigem ou preferem PNG. A regra "um formato por tipo" vale para a cena e o céu.
- **R4. JS escolhe a largura em vez de `srcset`.** A largura útil do terraço depende do `.palco` (112% no celular) e os sprites são posicionados em unidades do `cena.json`; uma função pura `escolherVariante` dá a mesma família ao terraço e aos props, o que `srcset`/`sizes` não garante. Custo se errado: trocar por `srcset` no `<img>` do terraço.
- **R3. O SW não força atualização** (sem `skipWaiting`). O shell vem da rede primeiro, então uma versão nova chega no próximo carregamento sem misturar versões.

## Foco de revisão
1. **Celular deitado (844×390):** nenhuma rolagem lateral. Coberto pelo teste parametrizado da Task 1.
2. **App aberto sem rede depois de instalado:** a navegação mostra o shell em cache, não o erro do navegador. Coberto na Task 3.
3. **Testes com `page.route` seguem funcionando com o SW existindo:** o bloqueio por padrão na extensão garante isso. Coberto pela suíte inteira verde na Task 3.
4. **DPR 3 numa tela de 390 px:** carrega a variante 1800, não a 900. Coberto na Task 2.
5. **Nenhuma URL `/api/` dentro do Cache Storage** depois de uma sessão inteira. Coberto na Task 3.

---

### Task 1: Contextos de celular, sem rolagem lateral e sem `:has()`

**Arquivos:**
- Modificar: `src/test/java/bipotheking/net/duora/e2e/NavegadorExtension.java` (`Participantes`)
- Criar: `src/test/java/bipotheking/net/duora/e2e/LayoutCelularIT.java`
- Modificar: `src/main/resources/static/css/app.css` (linhas ~961, ~1045, ~1049)
- Modificar: `src/main/resources/static/js/telas.js` (abrir/fechar a confirmação de saída)

**Interfaces (produz):**
- `TerracoPagina Participantes.novo(Browser.NewContextOptions opcoes)`: usa as opções como vêm e preenche só `reducedMotion=REDUCE` se faltar.
- `TerracoPagina Participantes.novoCelular()`: 390×844, `deviceScaleFactor` 3, `isMobile` true, `hasTouch` true, movimento `REDUCE`.
- A classe CSS `confirmando-saida` vai no elemento `.app` enquanto `#confirmar-saida` está visível.

- [ ] **Passo 1:** escrever os testes em `LayoutCelularIT`.
  - `@ParameterizedTest semRolagemLateral(int largura, int altura, String fase)`:
    - tamanhos 390×844 (celular), 844×390 (celular deitado) e 1440×900;
    - fases `entrada`, `montagem` (depois de `entrarEmPar` + uma peça) e `revelado` (depois de `comporTerraco` + as duas confirmações);
    - asserções: `assertEquals(largura, innerWidth)` (guarda contra o layout de 980 px), depois `scrollWidth <= clientWidth` de `document.documentElement`.
  - `confirmacaoAbertaNoCelularSoltaOTopo`: em 390×860, abrir "Sair". Asserções: `.app` tem a classe `confirmando-saida`, e `getComputedStyle(.topo).position` é `"static"`.
- [ ] **Passo 2:** rodar `-Dit.test=LayoutCelularIT` (chromium). `confirmacaoAberta…` deve falhar por falta da classe. Se `semRolagemLateral` falhar em algum caso, isso é defeito real: corrigir no CSS, mantendo o teste.
- [ ] **Passo 3:** trocar os três seletores `:has(...)` por `.app.confirmando-saida ...`, com o mesmo efeito. O `telas.js` põe e tira a classe junto com o `hidden` do bloco.
- [ ] **Passo 4:** rodar `LayoutCelularIT` e `AcessibilidadeLayoutIT` nos três navegadores, verdes. Commit `feat(web): layout de celular sem :has() e sem rolagem lateral`.

### Task 2: Renders em três larguras

**Arquivos:**
- Modificar: `assets/source/blender/cena.py`, `assets/source/ceu/exportar.sh`
- Modificar: `src/main/resources/static/js/dados.js` (`urlSprite`), `js/cena.js` (sprites e terraço), `index.html` (linhas 40–41), `css/app.css` (`.cidade-crepusculo/-noite`, ~309/314)
- Gerar: `static/assets/cena/*-{900,1800,2700}.webp`, `static/assets/ceu/ceu-*-{1280,2560}.webp` e o `cena.json` com o campo `variantes`
- Teste: `src/test/java/bipotheking/net/duora/e2e/ImagensIT.java`
- Remover: os `.webp` antigos sem sufixo de largura (o SW e o JS não os usam mais)

**Interfaces:**
- Consome: `Participantes.novo(Browser.NewContextOptions)` (Task 1).
- Produz:
  - `cena.json` ganha `"variantes": [900, 1800, 2700]`;
  - nomes `<nome>-<momento>-<largura>.webp`, em que a largura é a do terraço da família (um prop da família 900 tem 0,5× o tamanho da 1800);
  - `dados.js`: `export function escolherVariante(larguraCss: number, dpr: number, variantes: number[]): number` devolve a menor variante ≥ `larguraCss × dpr`, ou a maior;
  - `dados.js`: `urlSprite(nome, momento, largura)`.

- [ ] **Passo 1:** escrever os testes em `ImagensIT`, cada um num contexto novo, porque o cache atrapalharia.
  - `celularComumCarregaATerracoNa900`: 390×844, DPR 1. Depois de `img.complete`, o `currentSrc` do terraço termina em `-900.webp`.
  - `celularDeAltaDensidadeCarregaATerracoNa1800`: 390×844, DPR 3. Termina em `-1800.webp`.
  - `desktopRetinaCarregaATerracoNa2700`: 1440×900, DPR 2. Termina em `-2700.webp`.
  - `propsUsamAMesmaFamiliaDoTerraco`: DPR 3 a 390 px, depois de colocar uma peça. O sprite dela termina em `-1800.webp`.
- [ ] **Passo 2:** rodar `-Dit.test=ImagensIT` (chromium). Deve falhar, porque hoje os nomes não têm largura.
- [ ] **Passo 3:** `cena.py`.
  - Renderizar uma vez na largura mestre 2700 (`DUORA_LARGURA`, padrão 2700). As coordenadas do `cena.json` continuam em unidades de 1800.
  - Exportar cada imagem (terraço e props recortados) em 2700, 1800 e 900, reduzindo do mestre (redimensionamento do Blender ou `cwebp -resize`, sempre `-q 88`).
  - Gravar `variantes` no `cena.json`.
  - Rodar o render completo com o comando do `_mapa.md` (GLBs em `assets/source/modelos/`; seguir a skill `duora-assets`).
- [ ] **Passo 4:** `exportar.sh` do céu gera `ceu-<momento>-1280.webp` e `-2560.webp`. O CSS passa a `background-image: image-set(url(...-1280.webp) 1x, url(...-2560.webp) 2x)`.
- [ ] **Passo 5:** `cena.js` e `index.html`.
  - O terraço e os sprites usam `escolherVariante(larguraDoPalco, devicePixelRatio, variantes)`, calculado uma vez por carregamento depois do layout.
  - O HTML começa com a `-1800`, para quem está sem JS ou no primeiro paint.
- [ ] **Passo 6:** rodar `ImagensIT` nos três navegadores e a suíte e2e completa em chromium, tudo verde.
- [ ] **Passo 7:** atualizar `assets/manifest.md` (variantes e data) e conferir com `file` que todo `*.webp` novo é WebP. Commit `feat(cena): renders em três larguras escolhidos pela densidade da tela`.

### Task 3: PWA instalável

**Arquivos:**
- Criar: `static/manifest.webmanifest`, `static/sw.js`, `static/js/pwa.js`, `static/assets/icones/{icone-192,icone-512,maskable-192,maskable-512,apple-touch-180}.png`
- Modificar: `static/index.html` (head), `static/js/main.js` (registro), `src/main/resources/application.properties` ou um customizer (MIME), `room/CabecalhosDeSeguranca.java` (Cache-Control do `sw.js` e do manifest)
- Modificar: `NavegadorExtension.java` (`ServiceWorkerPolicy.BLOCK` em todo contexto, mais `novoComServiceWorker()`), `.github/workflows/ci.yml` (instalar também `chromium` nos jobs e2e), `README.md` (seção "Instalar no Android")
- Teste: `src/test/java/bipotheking/net/duora/room/PwaApiTest.java`, `src/test/java/bipotheking/net/duora/e2e/PwaIT.java`, `src/test/java/bipotheking/net/duora/e2e/InstalabilidadeIT.java`

**Interfaces (produz):**
- `pwa.js`: `export async function registrarServiceWorker(): Promise<void>`. Sem `navigator.serviceWorker`, ou com o `register` stub devolvendo `undefined`, não faz nada e não loga erro.
- Valores do manifest:
  - `"id": "/"`, `"scope": "/"`, `"start_url": "/"`;
  - `"name": "Duora"`, `"short_name": "Duora"`;
  - `"display": "standalone"`;
  - `"background_color": "#132B40"`, `"theme_color": "#132B40"`, `"lang": "pt-BR"`;
  - ícones 192 e 512 com `"purpose": "any"` e 192 e 512 com `"purpose": "maskable"`, em entradas separadas.
- `sw.js`:
  - `CACHE_VERSION = "duora-v1"` e a lista `SHELL`: `/`, `/index.html`, `/css/app.css`, `/js/*.js`, as fontes, os ícones e o manifest;
  - `install`: precache do `SHELL`;
  - `activate`: apaga caches `duora-*` diferentes da versão atual;
  - `fetch`:
    - ignora o que não é GET e `/api/` (sem `respondWith`);
    - navegação: rede primeiro, com fallback para o `/` em cache;
    - `/assets/`: cache primeiro, guardando na primeira busca;
    - o resto do shell: rede primeiro, com fallback para o cache.
- `Participantes.novoComServiceWorker()`: igual a `novo()`, com `ServiceWorkerPolicy.ALLOW`.

- [ ] **Passo 1:** testes de servidor em `PwaApiTest`, no estilo do `SalaApiTest`.
  - `manifestTemContentTypeDeManifest`: `Content-Type` começa com `application/manifest+json`.
  - `serviceWorkerNaoFicaEmCacheHttp`: `Cache-Control` do `/sw.js` contém `no-cache`.
  - `iconesDoManifestExistem`: cada `src` do manifest responde 200 `image/png`, com a largura e a altura do IHDR iguais ao `sizes`.
- [ ] **Passo 2:** testes e2e em `PwaIT`, em todos os navegadores.
  - `manifestDeclaraOQueOAndroidExigeParaInstalar`: lê o `href` de `link[rel=manifest]` pelo `context.request()` e confere os valores da interface acima.
  - `serviceWorkerAssumeAPaginaDepoisDeRecarregar`: com `novoComServiceWorker()`, esperar `navigator.serviceWorker.ready`, recarregar e conferir `navigator.serviceWorker.controller !== null`.
  - `apiNuncaEntraNoCache`: com o SW ativo, criar um terraço e entrar em par. Depois, nenhuma `request.url` de `caches` contém `/api/`.
  - `abertoSemRedeMostraOApp`: com o SW controlando, `context.setOffline(true)` e reload. O título da página é o do Duora, não um erro do navegador.
- [ ] **Passo 3:** `InstalabilidadeIT`. Sempre usa Chromium completo, independentemente de `-Dnavegador`: `launchPersistentContext(dirTemporario, setChannel("chromium").setHeadless(true))`.
  - `chromeNaoApontaErroDeInstalacao`: `Page.getInstallabilityErrors` via `CDPSession` devolve a lista vazia.
  - O teste de controle `paginaSemManifestTemErro` (rota que serve um HTML sem manifest) devolve um erro não vazio. Isso prova que o vazio do teste anterior significa algo.
- [ ] **Passo 4:** rodar os três testes novos. Devem falhar, porque não há manifest nem SW.
- [ ] **Passo 5:** implementar.
  - Manifest, ícones e `index.html`: `<link rel="manifest">`, `<meta name="theme-color" content="#132B40">` e `<link rel="apple-touch-icon">`.
  - Ícones gerados a partir do `icone.svg`: os maskable com a estrela dentro do círculo central de 80%; os `any` sem margem extra. Ferramenta livre, por exemplo `rsvg-convert` ou um screenshot do Playwright.
  - `sw.js`, `pwa.js` e o registro chamado no fim do `main.js`.
  - MIME do `.webmanifest`.
  - `Cache-Control: no-cache` para `/sw.js` e `/manifest.webmanifest`.
  - `ServiceWorkerPolicy.BLOCK` por padrão em `Participantes` e `novoComServiceWorker()`.
  - No `ci.yml`, os jobs e2e instalam também `chromium` (`install --with-deps <navegador> chromium`).
- [ ] **Passo 6:** rodar a suíte unitária, a suíte e2e completa em chromium e `PwaIT` + `InstalabilidadeIT` em firefox e webkit, tudo verde e sob `caffeinate`. O `actionlint` sai limpo.
- [ ] **Passo 7:** README, seção "Instalar no Android (teste local)":
  1. `./mvnw spring-boot:run`;
  2. `adb reverse tcp:8080 tcp:8080`;
  3. abrir `http://localhost:8080` no Chrome do Android;
  4. menu → "Instalar app".
  - Explicar que a porta tem de ser a mesma dos dois lados, por causa do `HostLocal`.
  - Commit `feat(pwa): manifest, ícones e service worker para instalar no Android`.

### Task 4: Prova no Android (emulador, por agente)
- [ ] Um agente sobe o AVD `duora-pixel` com `scripts/android/emulador.sh subir`, sobe o app (`./mvnw spring-boot:run`) e roda `scripts/android/pwa-check.py 8080`. O script faz o `adb reverse`, abre a URL no Chrome do emulador, checa `Page.getInstallabilityErrors` vazio, instala e captura telas em pé e deitado em `target/android/`.
- [ ] O agente confere nas capturas três coisas: o ícone na tela inicial, a abertura sem a barra do navegador e o terraço nítido (variante 1800 no DPR do Pixel).
- [ ] O resultado vai para o handoff. Problemas viram tasks. Ao dono chegam só escolhas, nunca a execução.

## Ordem e paralelismo
- **Primeiro:** a Task 1, sozinha, porque cria as fábricas de contexto que as outras usam.
- **Depois, em paralelo, em worktrees:**
  - Task 2: `cena.py`, assets, `dados.js`, `cena.js`, `index.html` (corpo) e `app.css` (céu);
  - Task 3: `index.html` (head), `main.js`, SW, servidor, `NavegadorExtension` e CI.
  - Os trechos de `index.html` e `app.css` não se sobrepõem. O render do Blender (Task 2) e o Maven (Task 3) entram na fila de "um pesado por vez".
- **No fim:** a Task 4, por agente, no emulador.
