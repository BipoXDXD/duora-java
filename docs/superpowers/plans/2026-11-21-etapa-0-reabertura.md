# Etapa 0 — Reabertura e fundação: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** deixar a demonstração do terraço sólida, pública e com CI verde no GitHub, além de criar as skills de projeto. É a base sobre a qual as etapas 1–11 vão construir.

**Arquitetura:** nada muda no desenho. Os ITs ganham uma infraestrutura comum, que todas as etapas seguintes vão reusar. Dois defeitos de foco do front são corrigidos. A CI fica enxuta, e o README e a licença ficam prontos para o repositório público.

**Stack:** Java 25, Spring Boot 4.1.3, Maven Wrapper, Playwright Java 1.65.1, axe-core 4.10.1, JaCoCo, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 0). Este plano absorve as Tasks 1, 2, 4 e 5 do plano pausado `.claude/handoff/plano-portfolio-publico.md`. A Task 3 de lá (lacunas do "Pronto quando" da demonstração) fica de fora: o terraço vai virar `encontro/` na Etapa 5, e o teste mobile de rolagem horizontal entra na Etapa 1.

## Restrições globais
- Commits em Conventional Commits, terminando com `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Push só na Task 6, e só da `main`. **Nunca** dar push em `backup/pre-reescrita`.
- Um Maven pesado por vez. Antes de `./mvnw`, `pgrep -fl "[c]lassworlds\.launcher" | grep -v RemoteMavenServer` tem de sair vazio.
- Em tarefas paralelas, `git add` e `git commit -- <caminhos>` incluem só os próprios arquivos.
- `-Xlint:all -Werror`, Spotless, um comportamento por teste. ITs com `@DirtiesContext(classMode = AFTER_CLASS)`.
- E2E sem skip, nos três navegadores (`-Dnavegador=chromium|firefox|webkit`).
- Textos em pt-BR, voz ativa. Nada de `alert`/`confirm`/`prompt`.
- Imagens em WebP com qualidade ≥ 88; áudio em MP3 192 kbps / 48 kHz.
- Nada de caminho local (`/Users/…`), e-mail pessoal ou segredo em arquivo versionado.

## Foco de revisão
1. **Polling no meio de uma colocação recusada (409):** a cena muda duas vezes, e o foco não pode cair no `body` nem num lugar bloqueado. Coberto na Task 2.
2. **"Continuar" ou Escape durante o envio da saída:** o bloco de confirmação não fecha enquanto o `POST /api/sala/sair` está pendente. Coberto na Task 2.
3. **CI no Linux:** o WebKit precisa de dependências do sistema (`playwright install --with-deps`), e fontes ou locale diferentes do macOS podem mexer no axe e nas capturas. Coberto pela primeira execução real na Task 6.
4. **README público:** toda captura existe no repo e nenhum link aponta para arquivo ignorado. Coberto pelo grep da Task 4.
5. **Skills versionadas:** sem caminho absoluto do usuário e sem segredo. Coberto pelo grep da Task 5.

---

### Task 1: Infraestrutura comum dos ITs e minors de teste

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/NavegadorExtension.java`
- Criar: `src/test/java/bipotheking/net/duora/e2e/Cenarios.java`
- Modificar: as 7 classes `*IT.java` em `e2e/`, `AcessibilidadeLayoutIT.java:141`, `AcessibilidadeFocoIT.java:195,200`

**Interfaces (produz):**
- `NavegadorExtension` (JUnit 5: `BeforeAllCallback`, `AfterAllCallback`, `AfterEachCallback`, `ParameterResolver`):
  - abre o navegador de `Navegador.abrir(playwright)` uma vez por classe;
  - fecha os contextos após cada teste;
  - resolve um parâmetro `Participantes`.
- `Participantes`:
  - `TerracoPagina novo()`: 1440×900, movimento `REDUCE`;
  - `TerracoPagina novo(int largura, int altura, ReducedMotion movimento)`;
  - `TerracoPagina novo(Consumer<Page> preparar)`: para sondas e `clock().install()`.
- `Cenarios` (estáticos):
  - `String raiz(int porta)`;
  - `void entrarEmPar(TerracoPagina ana, TerracoPagina bruno, String raiz)`;
  - `void comporTerraco(TerracoPagina ana, TerracoPagina bruno)`: telescópio C1, banco C2, manta L2, lanterna R1, com as esperas de visibilidade de hoje;
  - constantes `ANA = "Ana"` e `BRUNO = "Bruno"`.

- [ ] **Passo 1:** rodar `./mvnw -q -Pe2e verify` (chromium) antes de mexer e anotar a contagem de testes. O esperado é 0 falhas.
- [ ] **Passo 2:** implementar a extensão e os cenários. Migrar as classes uma a uma, sem mudar asserções nem nomes de teste; o `entrarEmPar` duplicado entre `AxeIT` e `RevelacaoIT` passa a vir de `Cenarios`.
- [ ] **Passo 3:** minors.
  - `AcessibilidadeLayoutIT:141`: o `encerrar.focus()` dá lugar a Tab até o botão (`Navegador.TAB`, com limite), com `assertThat(encerrar).isFocused()` antes do Enter.
  - `AcessibilidadeFocoIT:195,200`: `botao("Sair")` passa a `botaoSair()`.
  - `conviteSemViolacoes`: usar o retorno de `linkDoConvite()` ou trocar por uma espera explícita com nome.
- [ ] **Passo 4:** suíte e2e nos três navegadores, com a mesma contagem do passo 1 e 0 falhas.
- [ ] **Passo 5:** commit `test(e2e): extensão de navegador e cenários comuns`.

### Task 2: Foco após recusa (409) e "Continuar" durante a saída

**Arquivos:**
- Modificar: `src/main/resources/static/js/main.js` (`tratarErroDeAcao`, ~393)
- Modificar: `src/main/resources/static/js/telas.js:48` (listener de `el.continuar`)
- Teste: `src/test/java/bipotheking/net/duora/e2e/AcessibilidadeFocoIT.java`

**Interfaces (consome):** `Participantes`, `Cenarios.entrarEmPar` (Task 1); `cena.focarPrimeiroLugar()` (`cena.js:171`); `painel.focarItem(peca)` (`painel.js:189`); `enviandoSaida` (`telas.js`).

- [ ] **Passo 1:** escrever o teste `lugarRecusadoPeloServidorLevaOFocoAoPrimeiroLugarLivre`.
  1. Bruno retém o `GET /api/sala` com `page.route`, sem responder até o fim do arranjo.
  2. Ana coloca o telescópio em C1.
  3. Bruno seleciona o banco pelo teclado e aperta Enter em C1, que ele ainda vê livre.
  4. Asserções: o aviso de lugar ocupado aparece, e `document.activeElement` tem `data-lugar` ≠ `C1` e não tem `aria-disabled="true"`.
- [ ] **Passo 2:** escrever o teste `continuarDuranteOEnvioDaSaidaNaoFechaAConfirmacao`.
  1. Ana retém o `POST /api/sala/sair` com `page.route`.
  2. Ana abre "Sair", clica em "Encerrar para os dois" e depois em "Continuar".
  3. Asserção: `#confirmar-saida` continua visível.
- [ ] **Passo 3:** rodar os dois testes (`-Dit.test=AcessibilidadeFocoIT`). Os dois devem falhar: hoje o foco fica no C1 bloqueado e o bloco fecha.
- [ ] **Passo 4:** corrigir.
  - Em `tratarErroDeAcao`, depois de um 409 com `erro.visao`: com peça selecionada, `cena.focarPrimeiroLugar()`; sem peça, `painel.focarItem(peca)`, com a peça da ação guardada antes do envio.
  - Em `telas.js`, o listener de "Continuar" ignora o clique quando `enviandoSaida`.
- [ ] **Passo 5:** rodar `AcessibilidadeFocoIT` nos três navegadores, verde.
- [ ] **Passo 6:** commit `fix(web): foco volta a um lugar livre após recusa e saída em andamento não fecha`.

### Task 3: CI enxuta e cobertura

**Arquivos:** modificar `.github/workflows/ci.yml` e `pom.xml`.

- [ ] **Passo 1:** no `pom.xml`:
  - propriedade `skipUnitTests` (padrão `false`), ligada ao `skipTests` do surefire;
  - `exec-maven-plugin` com versão fixa em `pluginManagement`;
  - `jacoco-maven-plugin` (versão estável mais recente no Maven Central) com `prepare-agent` e `report` na fase `verify`, só como relatório, sem limite mínimo.
- [ ] **Passo 2:** no `ci.yml`:
  - o job `e2e` ganha `needs: verify` e roda `./mvnw -B -Pe2e -DskipUnitTests=true -Dspotless.check.skip=true verify`, sem `-q`;
  - o navegador é instalado com `--with-deps`;
  - cache de `~/.cache/ms-playwright` com a chave `playwright-1.65.1-${{ matrix.navegador }}`;
  - o upload inclui `target/failsafe-reports`, com `if: always()`;
  - o job `verify` publica `target/site/jacoco`;
  - as actions continuam fixadas por SHA, e o SHA de qualquer action nova é conferido com `gh api`.
- [ ] **Passo 3:** verificar.
  - `actionlint` sai sem erros (se faltar, instalar com `go install github.com/rhysd/actionlint/cmd/actionlint@latest`).
  - `./mvnw -q verify` gera `target/site/jacoco/index.html`.
  - `./mvnw -q -Pe2e -DskipUnitTests=true -Dspotless.check.skip=true verify -Dit.test=AxeIT` roda só esse IT.
- [ ] **Passo 4:** commit `ci: e2e após verify sem repetir testes, cache do playwright e cobertura`.

### Task 4: README, licença e manifesto

**Arquivos:**
- Criar: `LICENSE` (MIT padrão, `Copyright (c) 2026 BipoXDXD`)
- Criar: `docs/capturas/{entrada,colocacao,espera,final}-{1440x900,390x844}.webp`, a partir de `target/e2e-capturas/*.png` com `cwebp -q 88`
- Modificar: `README.md`, `assets/manifest.md`

- [ ] **Passo 1:** README.
  - Capturas: duas grandes (montagem e final, no desktop) e uma linha de capturas mobile.
  - Badge da CI: `https://github.com/BipoXDXD/<REPO>/actions/workflows/ci.yml/badge.svg`. O `<REPO>` é preenchido na Task 6.
  - Correções: "três navegadores" no lugar de "Chromium"; "seis props".
  - Seção "Decisões", uma linha cada:
    - polling em vez de WebSocket;
    - sem Spring Security (`SameSite=Strict`, `Origin`, `X-Duora`);
    - estado em memória;
    - câmera ortográfica.
  - Seção "Para onde vai": 2–3 linhas sobre a rede social, com link para a spec.
  - Seção "Status": aceite visual e playtest pendentes.
  - Seção "Licença": código MIT; os assets foram gerados pelo autor (ver o manifesto).
- [ ] **Passo 2:** no manifesto, a coluna "Licença" passa a dizer "Gerado pelo autor em conta própria (Hi3D / Tripo / ElevenLabs); redistribuição conforme os termos do plano do autor".
- [ ] **Passo 3:** verificar.
  - `grep -rnE "/Use[r]s/|$(whoami)" README.md assets/manifest.md docs/ LICENSE` sai vazio.
  - Todo `docs/capturas/*` citado no README existe.
- [ ] **Passo 4:** commit `docs: licença MIT, capturas, decisões e rumo no readme`.

### Task 5: Skills de projeto

**Arquivos:**
- Criar: `.claude/skills/duora-assets/SKILL.md`
- Criar: `.claude/skills/duora-etapa/SKILL.md`
- Modificar: `.gitignore`, para versionar `.claude/skills/` e manter ignorados `.claude/handoff/` e `.claude/settings.local.json`

Use `superpowers:writing-skills`: primeiro um cenário-base com um subagente sem a skill, depois a skill e então o mesmo cenário com ela.

- [ ] **Passo 1:** `duora-assets`. Ativa ao gerar ou trocar um asset visual ou sonoro do Duora. Conteúdo:
  - fontes: Hi3D para 3D; Tripo `chat_image_2.5_sunburst` 4K para 2D; ElevenLabs para áudio;
  - pipeline: GLB em `assets/source/modelos/` (fora do Git) → `cena.py` no Blender headless (comando do `_mapa.md`) → WebP q88 em `static/assets/`;
  - áudio em MP3 192 kbps / 48 kHz;
  - linha obrigatória em `assets/manifest.md` (fonte, task id, data, licença);
  - nada de regenerar na nuvem quando a edição local resolve.
  - Cenário-base: "troque o render da lanterna por uma versão mais quente", sem a skill. Anotar onde o agente erra o formato ou pula o manifesto.
- [ ] **Passo 2:** `duora-etapa`. Ativa ao concluir uma task ou etapa do Duora. É um checklist:
  1. testes afetados com o comando exato do `_mapa.md`;
  2. `AxeIT` cobrindo a tela nova;
  3. Spotless;
  4. greps de caminho local e segredo;
  5. commit em Conventional Commits;
  6. handoff atualizado (Objetivo/Decisões/Arquivos/Estado/Próximos/Pendências);
  7. resumo de decisões para o dono;
  8. linha final padrão de etapa concluída.
  - Cenário-base: "conclua a task X", sem a skill. Anotar o que o agente esquece.
- [ ] **Passo 3:** reaplicar os cenários com as skills e ajustar o texto até que os erros anotados não se repitam.
- [ ] **Passo 4:** `grep -rnE "/Use[r]s/|$(whoami)|@gmai[l]\.com" .claude/skills` sai vazio. `git check-ignore .claude/handoff/x .claude/settings.local.json` lista os dois.
- [ ] **Passo 5:** commit `chore: skills de projeto duora-assets e duora-etapa`.

### Task 6: Publicação e primeira CI (controlador, com o dono)

- [ ] **Passo 1:** confirmar com o dono o nome do repo (sugestão: `duora`) e a visibilidade pública, porque é uma ação externa e difícil de desfazer. Rodar o `gitleaks detect` na `main` e conferir 0 vazamentos.
- [ ] **Passo 2:** `gh repo create BipoXDXD/<REPO> --public --source . --remote origin`, depois `git push -u origin main` (só a `main`). Preencher `<REPO>` no badge e commitar.
- [ ] **Passo 3:** `gh run watch` até concluir. Cada falha passa por `superpowers:systematic-debugging`, com teste antes quando for defeito de código. A meta é ter `verify` e os 3 jobs e2e verdes.
- [ ] **Passo 4:** atualizar o handoff e o `_mapa.md` (remoto e CI).

### Task 7: Playtest e aceite do terraço (dono)

- [ ] **Passo 1:** o controlador sobe `./mvnw spring-boot:run`, com a porta conferida antes, e entrega ao dono:
  - o roteiro: duas pessoas, dois aparelhos ou duas janelas, uma composição completa e depois outra diferente;
  - as cinco perguntas de `duora-java-mvp.md` §3 ("O que avaliar no playtest").
- [ ] **Passo 2:** o dono responde. Ajustes de mecânica ou visual viram tasks novas antes da Etapa 1. O agente não declara aprovação sozinho.
- [ ] **Passo 3:** encerrar o servidor e registrar o resultado no handoff.

## Ordem e paralelismo
- **Primeiro:** a Task 1, sozinha, porque mexe em todos os ITs.
- **Depois da 1:**
  - a Task 2 em série (usa o Maven e os ITs);
  - as Tasks 3, 4 e 5 em paralelo, porque os arquivos são disjuntos (a 3 usa o Maven só no passo 3, respeitando a fila).
- **No fim:** a Task 6, depois da revisão final do branch.
- **A qualquer momento:** a Task 7, que depende só do dono.
