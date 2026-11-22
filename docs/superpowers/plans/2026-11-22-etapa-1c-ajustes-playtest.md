# Etapa 1c — Ajustes do playtest (motor de encontro): plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** o encontro passa a mostrar o que o outro fez, deixa sugerir com destino, dá a cada peça um efeito próprio na revelação, pede concordância para voltar a ajustar e encerra de forma gentil. Um novo playtest por agente responde "sim" às perguntas 2–5.

**Arquitetura:**
- No servidor, as regras novas ficam no motor (`game/Regras` com `Cenario`, da 1b), e valem para qualquer cenário:
  - o `Sinal` ganha um destino;
  - o `Estado` ganha o pedido de ajuste;
  - `GET /api/sala` sem assento responde 204.
- No front, as mudanças ficam no controlador da sessão e na cena imperativa (1a). Os efeitos de revelação e as frases de posição relativa são dados do `cenario.json` (esquema da 1b); o código só tem um efeito por **tipo**.

**Stack:** Java 25, Spring Boot 4.2.0, Vite + React 19 + TS + Tailwind 4 (Etapa 1a), valibot (1b), Playwright Java 1.65.1, Playwright MCP (playtest), ElevenLabs (efeito sonoro do rádio).

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1c). Contratos: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Pré-requisito: as Etapas 1a e 1b integradas. As perguntas do playtest estão em `duora-java-mvp.md` §3, "O que avaliar no playtest".

**Skills por momento:**
- `mattpocock-skills:codebase-design` antes das Tasks 0, 3 e 5 (a forma das fases, a do modo de sugestão e a do pedido de ajuste), com duas alternativas e o aval do controlador para mudar as Interfaces;
- `mattpocock-skills:tdd` na implementação;
- `superpowers:systematic-debugging` ou `mattpocock-skills:diagnosing-bugs` em falhas;
- `superpowers:verification-before-completion` antes de cada commit;
- `superpowers:requesting-code-review` + `mattpocock-skills:code-review` na revisão;
- `duora-etapa` ao fechar.

## Restrições globais
- **Princípios de engenharia dos contratos.**
  - Os commits de refatoração (Task 0 e cada Extract Function preparatório) são separados dos de comportamento, sempre com a suíte verde.
  - O estado do encontro passa a ser um tipo selado (State) antes de ganhar o pedido de ajuste: "make the change easy, then make the easy change".
  - No front, os campos novos de sessão entram só na variante `montagem` da união `EstadoDaTela` (1a), e todo `switch` sobre fase ou tela continua exaustivo.
- Valem as restrições das Etapas 0, 1, 1a e 1b: Conventional Commits com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; `-Werror`; Spotless; lint do front com `--max-warnings 0`; E2E sem skip nos 3 navegadores; um pesado por vez com a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`; CSP inalterada; JS inicial ≤ 80 KB gzip (`TamanhoDoJsInicialTest`).
- **Mudanças de asserção permitidas, e só estas** (a 1c muda comportamento de propósito). Cada uma vai citada no corpo do commit com o motivo:
  - `RegrasTest`: os testes de `ajustar` passam a usar `pedirAjuste`/`recusarAjuste`, e os de `sinalizar` passam a ter destino;
  - `SalaApiTest`:
    - `GET /api/sala` sem cookie passa de 401 a 204;
    - `/sala/sinal` sem `lugar` responde 422 `LUGAR_INVALIDO`;
    - `/sala/ajustar` passa a criar um pedido;
  - `AcessibilidadeFocoIT.ajustarDeNovoLevaOFocoAPrimeiraPeca` e `RevelacaoIT.movimentoReduzidoMostraRiscosParadosQueSomemAoAjustar`: passam a ter pedido e aceite. Na Task 1, os dois participantes clicam "Ajustar de novo", porque pedidos cruzados viram aceite (R4); na Task 5, passam a usar "Pedir para ajustar" e "Aceitar ajustar";
  - `AcessibilidadeLayoutIT.pecaSinalizadaTemSeloEstaticoENomeComMovimentoReduzido`: o sinal passa a ter dois passos;
  - os testes de saída do `JornadaDoisNavegadoresIT` e o `TerracoPagina.tituloEncerrado()`: novos títulos e textos;
  - os testes Vitest de `mensagens` e `Inventario` cujos textos mudam.
- **Textos:** pt-BR, voz ativa, gênero neutro quando se refere à pessoa. Os textos novos estão fixados nas tasks; usar exatamente como escritos.
- **O som geral desligado continua silencioso, com uma exceção:** as notas do rádio na revelação (decisão do dono). Elas tocam uma vez, só na passagem ao vivo para a noite, nunca ao recarregar.
- **Movimento reduzido:** todo destaque e todo efeito novo tem versão estática visível.
- **Assets:** o MP3 novo segue a skill `duora-assets` (ElevenLabs → MP3 192 kbps / 48 kHz → `assets/manifest.md`).
- Os seletores dos testes novos usam papel, rótulo e texto. Os efeitos decorativos (`aria-hidden`) usam `data-efeito`, como o `#meteoros` usa `data-lado`.

## Rulings de planejamento (o dono pode reverter)
- **R1. Sem fallback no 204:** o cliente converte o 204 de `obterSala` em `ErroApi({ status: 401, codigo: "SEM_ASSENTO" })`. Assim o caminho de "sem assento" do controlador continua o de hoje.
- **R2. "Ignorar" uma sugestão é não fazer nada.** O sinal expira em `duracaoSinal` (20 s no terraço), como hoje. Não existe botão "Agora não" para sugestões; ele existe só para o pedido de ajuste, que não expira.
- **R3. O destino sugerido precisa estar livre** (nenhuma peça no lugar), na zona da peça e diferente do lugar atual. Se o destino for ocupado antes do aceite, o sinal cai.
- **R4. Pedidos de ajuste cruzados viram aceite:** se B pede enquanto o pedido de A está pendente, o resultado é o mesmo de B aceitar.
- **R5. "Perto" é a distância entre os lugares ≤ `raioPerto` metros**, medida no `cena.json` (`px / pxPorMetro`); o terraço usa 2,0 m. No máximo 2 frases de relação, na ordem do JSON.
- **R6. Tela cheia no celular é de layout (CSS), não a Fullscreen API.** A API exige um gesto, e quem recebe a revelação pelo polling não fez gesto nenhum.

## Foco de revisão
1. **Faixa de status presa no topo do celular cobrindo o elemento focado** (Tab até o inventário). Coberto pelo `AcessibilidadeLayoutIT` existente, verde, mais `scroll-padding-top` (Task 2).
2. **Destino da sugestão ocupado antes do aceite:** o sinal cai, o botão "Mover … para lá" some, e um aceite já em voo recebe 409 com a mensagem de lugar ocupado. Coberto por `sinalCaiQuandoODestinoEOcupado` (Task 1) e `aceiteDepoisQueODestinoFoiOcupadoMostraAviso` (Task 3).
3. **Os dois pedem para ajustar ao mesmo tempo:** sem impasse, os dois voltam à montagem. Coberto por `pedidosCruzadosViramAceite` (Task 1).
4. **Recarregar na noite, no celular:** volta em tela cheia, sem tocar as notas nem o acorde. Coberto por `recarregarNaNoiteNaoTocaNotas` (Task 4).
5. **Alguém sai com um pedido de ajuste pendente:** o encontro termina normalmente para os dois. Coberto por `sairComPedidoPendenteEncerra` (Task 1).

---

### Task 0: Refatoração — fases do encontro como tipo selado

Hoje `Fase` é um enum, e `Regras`, `Estado` e `Visao` decidem por `if (estado.fase() != Fase.X)` espalhados (Switch Statements / Primitive Obsession). Antes de acrescentar o pedido de ajuste, que só existe na revelação, a fase vira um tipo selado. Assim, um pedido fora da revelação nem é representável.

**Arquivos:**
- Modificar: `game/{Fase,Estado,Regras}.java`, `room/{Visao,Retrato,Sala}.java` (onde leem a fase)
- Teste: a suíte atual inalterada, mais `game/FaseTest.java` (novo)

**Interfaces (produz):**
- `sealed interface Fase permits AguardandoPar, Montando, Revelado`, com records sem estado por ora (`record Revelado()`; a Task 1 lhe dá `Optional<Assento> pedidoDeAjuste`) e `String nome()`, que devolve `"AGUARDANDO_PAR"`, `"MONTANDO"` ou `"REVELADO"`.
- O JSON não muda: `Visao.fase` continua a string de hoje.
- Toda decisão por fase em `Regras` usa `switch` com pattern matching exaustivo, sem `default`.

- [ ] **Passo 1:** `FaseTest`:
  - `nomesNaRedeSaoOsDeHoje`;
  - `switchExaustivoCobreAsTres` (um método do teste com `switch` sem `default` que compila só se cobrir as três).
- [ ] **Passo 2:** refatorar em passos pequenos (Replace Conditional with Polymorphism / Replace Type Code with Subclasses, na forma de records), rodando `RegrasTest`, `SalaApiTest` e `ConcorrenciaTest` a cada passo, sem mudar asserção.
- [ ] **Passo 3:** `./mvnw -q verify` verde e o `JornadaDoisNavegadoresIT` em chromium verde. Commit `refactor(game): fases do encontro como tipo selado`.

### Task 1: Servidor — sinal com destino, pedido de ajuste e 204 sem assento

**Arquivos:**
- Modificar: `game/{Sinal,Estado,Regras}.java`, `room/{Pedidos,Visao,SalaController,Sala,Codigo}.java`
- Modificar (cliente mínimo): `frontend/src/api/cliente.ts` (`sinalizar(peca, lugar)`, `pedirAjuste()`, `recusarAjuste()`, e o 204 de `obterSala` conforme R1), `frontend/src/dominio/visao.ts`
- Teste: `game/RegrasTest.java` (asserções listadas nas restrições), `room/SalaApiTest.java`, `room/ConcorrenciaTest.java`, `frontend/src/api/cliente.test.ts`

**Interfaces:**
- Consome: `Cenario`, `Regras.*(Cenario c, ...)` e `CatalogoDeCenarios` (1b); a `Fase` selada (Task 0).
- Produz:
  - `record Sinal(Peca peca, Assento de, Lugar destino, Instant expiraEm)`.
  - `record Revelado(Optional<Assento> pedidoDeAjuste)` (Task 0), sem campo novo no `Estado`. O pedido só existe na revelação.
  - `Regras.sinalizar(Cenario c, Estado e, Assento quem, Peca peca, Lugar destino, Instant agora)`:
    - `PECA_ALHEIA` quando a peça é de quem sinaliza;
    - `FASE_INVALIDA` fora de `MONTANDO` ou com a peça fora da cena;
    - `LUGAR_INVALIDO` quando o destino está fora da zona da peça ou é o lugar atual;
    - `LUGAR_OCUPADO` quando há peça no destino.
  - `Regras.destinosSugeriveis(Cenario c, Estado e, Peca peca): List<Lugar>`: os lugares livres da zona, sem o atual, na ordem do cenário. Vazio fora de `MONTANDO`.
  - `Regras.colocar(...)` mantém o sinal só se a peça sinalizada não mudou de lugar **e** o destino continua livre.
  - `Regras.pedirAjuste(Cenario c, Estado e, Assento quem)`:
    - `FASE_INVALIDA` fora de `REVELADO`;
    - sem pedido → grava o pedido de `quem` (versão igual);
    - pedido de `quem` → aceita sem mudar nada;
    - pedido do outro → `MONTANDO`, `versao + 1`, sem prontos e sem pedido (o comportamento do antigo `ajustar`).
  - `Regras.recusarAjuste(Cenario c, Estado e, Assento quem)`: limpa o pedido, venha de quem vier (o próprio cancela ou o outro recusa). `FASE_INVALIDA` fora de `REVELADO`; sem pedido, aceita sem mudar nada. O `ajustar` antigo é removido.
  - API:
    - `Pedidos.Sinalizar(@Nullable String peca, @Nullable String lugar)`; `lugar` ausente ou desconhecido → 422 `LUGAR_INVALIDO`;
    - `POST /api/sala/ajustar` → `pedirAjuste`; `POST /api/sala/ajustar/recusar` → `recusarAjuste`. Os dois devolvem a visão com `ETag`;
    - `Visao`:
      - `sinal` ganha `"lugar"`;
      - `VisaoPeca` do parceiro ganha `"destinosSugeriveis": [...]`;
      - campo novo `"ajuste": { "deAssento": "A" } | null`;
    - `GET /api/sala` sem cookie de assento válido → 204 sem corpo nem `ETag`. As mutações sem assento continuam 401 `SEM_ASSENTO`.
  - Cliente: `api.sinalizar(peca: string, lugar: string)`, `api.pedirAjuste()` e `api.recusarAjuste()`. `api.ajustar` é removido.

- [ ] **Passo 1:** escrever os testes.
  - `RegrasTest`:
    - `sinalComDestinoLivreEAceito`;
    - `sinalParaLugarOcupadoERecusado` (`LUGAR_OCUPADO`);
    - `sinalParaOutraZonaERecusado` (`LUGAR_INVALIDO`);
    - `sinalParaOLugarAtualERecusado` (`LUGAR_INVALIDO`);
    - `sinalCaiQuandoODestinoEOcupado` (outra peça vai para o destino → `sinal` vazio);
    - `destinosSugeriveisSaoOsLivresDaZonaSemOAtual`;
    - `pedirAjusteGravaOPedidoSemMudarAFase`;
    - `aceiteDoOutroVoltaAMontagemComVersaoNova`;
    - `pedidosCruzadosViramAceite`;
    - `recusarLimpaOPedido`;
    - `cancelarOProprioPedidoLimpa`;
    - `pedirAjusteForaDaRevelacaoERecusado`.
  - `SalaApiTest`:
    - `obterSalaSemCookieRespondeSemConteudo` (204, corpo vazio);
    - `mutacaoSemCookieContinua401`;
    - `sinalSemLugarResponde422`;
    - `visaoDoParceiroTrazDestinosSugeriveis`;
    - `pedidoDeAjusteApareceNaVisaoDosDois`;
    - `sairComPedidoPendenteEncerra`.
  - `ConcorrenciaTest`: `pedidosDeAjusteSimultaneosTerminamEmMontagem` (as duas threads pedem; o estado final é `MONTANDO`, sem pedido, com `versao + 1` uma vez só).
  - `cliente.test.ts`:
    - `obterSalaSemConteudoViraSemAssento` (204 → `ErroApi` 401 `SEM_ASSENTO`);
    - `sinalizarEnviaPecaELugar`.
- [ ] **Passo 2:** rodar os testes: falham.
- [ ] **Passo 3:** implementar. Até as Tasks 3 e 5, o front:
  - envia o sinal com o primeiro item de `destinosSugeriveis`;
  - chama `pedirAjuste` no botão "Ajustar de novo" atual;
  - e o e2e só muda nas asserções listadas. Nos dois testes que ajustam, o segundo participante também clica "Ajustar de novo".
- [ ] **Passo 4:** `./mvnw -q verify` e o e2e afetado em chromium (`-Dit.test='JornadaDoisNavegadoresIT,AcessibilidadeFocoIT,AcessibilidadeLayoutIT,RevelacaoIT,PollingIT'`) verdes. Atualizar `.claude/handoff/contrato-api.md`. Commit `feat(game): sinal com destino, pedido de ajuste e sala sem assento em 204`.

### Task 2: Ver o que o outro fez — destaque na peça e status abaixo dos nomes

**Arquivos:**
- Modificar: `frontend/src/sessao/{novidade.ts,sessao.ts}`, `frontend/src/cena/cena.ts`, `frontend/src/componentes/{Painel,Moldura}.tsx`
- Criar: `frontend/src/estilos/destaque.css` (importado por `app.css`)
- Teste: `novidade.test.ts`, `sessao.test.ts`, `Cena.test.tsx`, `src/test/java/bipotheking/net/duora/e2e/VerOQueOOutroFezIT.java` (novo)

**Interfaces:**
- Consome: `Visao` da Task 1 e `CenarioFront` (1b).
- Produz:
  - `pecasQueMudaram(anterior: Visao, visao: Visao): string[]`: as peças do parceiro colocadas ou movidas (tiradas não contam, porque não estão na cena).
  - A variante `montagem` de `EstadoDaTela` ganha `destaques: readonly string[]`. Entram a cada visão do polling e saem depois de `DURACAO_DESTAQUE_MS = 2400`, por um `agendar` injetável em `criarSessao`.
  - Cena, para cada peça em destaque:
    - `data-destaque="true"`;
    - um selo visível `span.selo.selo-mudou` "Mudou" (`aria-hidden`);
    - o complemento "Mudou agora" no nome acessível, depois dos complementos atuais.
    - A animação `destaque` é um anel pulsando 2× em 1,2 s; com movimento reduzido, é um anel estático durante o mesmo tempo.
  - Painel: o `#aviso` passa a vir logo depois de `#par`. No celular (`celular:`), o bloco `#par` + `#aviso` fica `sticky` no topo do painel, com fundo opaco. O `html` ganha `scroll-padding-top` com a altura do bloco, para o foco por Tab nunca ficar coberto.

- [ ] **Passo 1:** escrever os testes.
  - `novidade.test.ts`: `colocadaEMovidaContam`, `tiradaNaoConta`, `propriaNaoConta`.
  - `sessao.test.ts`: `destaqueSaiDepoisDoTempo` (com o agendar falso, avançar 2400 → `destaques` vazio).
  - `Cena.test.tsx`: `pecaEmDestaqueTemSeloENome` (nome terminando em ". Mudou agora").
  - `VerOQueOOutroFezIT`:
    - `pecaQueOParceiroColocouGanhaODestaque`: Bruno coloca o banco em C2 → Ana vê o botão "Banco de Bruno, centro, à direita. Mudou agora". Com `page.clock()`, avançar 2,5 s → o nome não tem mais "Mudou agora";
    - `@ParameterizedTest statusVisivelSemRolar` em 1440×835 e 1440×900: depois de Bruno colocar, a caixa do `status` de Ana está inteira dentro da janela;
    - `statusVisivelNoCelularRolado`: Ana em 390×844 rola até o fim; Bruno coloca → a caixa do `status` de Ana está dentro da janela e contém "Bruno colocou o banco".
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm test`, o `VerOQueOOutroFezIT`, o `AcessibilidadeLayoutIT` e o `LayoutCelularIT` em chromium, verdes. Commit `feat(encontro): destaque na peça que o outro mudou e status logo abaixo dos nomes`.

### Task 3: Sinal com destino ("Que tal aqui?")

**Arquivos:**
- Modificar: `frontend/src/sessao/{sessao.ts,novidade.ts}`, `frontend/src/dominio/mensagens.ts`, `frontend/src/cena/cena.ts`, `frontend/src/componentes/{Cena,Inventario}.tsx`, `frontend/src/estilos/cena.css`
- Teste: `sessao.test.ts`, `novidade.test.ts`, `Cena.test.tsx`, `Inventario.test.tsx`, `e2e/SinalComDestinoIT.java` (novo), `e2e/AcessibilidadeLayoutIT.java` (o teste listado nas restrições), `e2e/AxeIT.java` (estado "sugerindo")
- Modificar: `e2e/VisualIT.java` (o estado `sinal` passa ao fluxo de dois passos, com destino R1; o IT só roda com `-Dvisual.saida` e serve às próximas comparações visuais)

**Interfaces:**
- Consome: Task 1 (`destinosSugeriveis`, `sinal.lugar`, `api.sinalizar(peca, lugar)`).
- Produz:
  - A variante `montagem` de `EstadoDaTela` ganha `sugerindo: string | null`.
    - `escolherPeca(peca, false)` em `MONTANDO` liga ou desliga a sugestão daquela peça;
    - o Esc cancela;
    - `escolherLugar` durante a sugestão chama `api.sinalizar(sugerindo, lugar)`.
  - Cena em modo de sugestão: um `.lugar` por destino sugerível, com o rótulo visível "Que tal aqui?" e o nome acessível `Que tal aqui? Sugerir <artigo>: <lugar>`. Nenhum lugar de colocação própria aparece ao mesmo tempo.
  - Para quem recebe: um marcador `div.lugar-sugerido` (`aria-hidden`, com `data-lugar`) no destino, e o selo "Sugerido mover" de hoje na peça. No inventário, o item da peça sinalizada ganha o botão `Mover <artigo> para lá: <lugar>`, que chama `moverPeca(peca, destino)`.
  - Textos novos:
    - aviso de quem sugere: `Você sugeriu que <nome> mova <artigo>: <lugar>.`;
    - novidade de quem recebe: `<nome> sugeriu mover <artigo>: <lugar>.`;
    - instrução durante a sugestão: `Escolha onde <artigo> de <nome> ficaria melhor.`, com " Esc cancela." só fora do celular (regra da Task 6).

- [ ] **Passo 1:** escrever os testes.
  - Vitest:
    - `sugerirChamaSinalizarComPecaELugar`;
    - `escCancelaASugestao`;
    - `novidadeDoSinalCitaODestino`;
    - `itemSinalizadoTemBotaoMoverParaLa`;
    - `aceiteDepoisQueODestinoFoiOcupadoMostraAviso` (409 `LUGAR_OCUPADO` → aviso de lugar ocupado, sem o botão).
  - `SinalComDestinoIT`:
    - `sugestaoAceitaMoveAPecaParaODestino`: Ana toca na lanterna de Bruno (em R2) → vê "Que tal aqui? Sugerir a lanterna: fundo, à direita" → toca. Bruno vê o status "Ana sugeriu mover a lanterna: fundo, à direita." → toca "Mover a lanterna para lá: fundo, à direita" → os dois veem "Lanterna de Bruno, fundo, à direita";
    - `lugarOcupadoNaoApareceComoSugestao`: com a manta de Ana em L2, sugerir a lanterna não oferece L2;
    - `sugestaoIgnoradaExpira`: com `page.clock()`, avançar 21 s → o botão "Mover … para lá" some para Bruno.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm test`, o `SinalComDestinoIT`, o `AcessibilidadeLayoutIT` e o `AxeIT` em chromium, verdes. Commit `feat(encontro): sugestão com destino que o outro aceita ou ignora`.

### Task 4: Revelação — efeito por peça, texto com posições relativas e tela cheia no celular

**Arquivos:**
- Modificar: `frontend/src/cenario/esquema.ts` (campos `revelacao`, `relacoes` e `raioPerto`), `src/main/resources/static/assets/cenarios/terraco/cenario.json`
- Criar: `frontend/src/cena/efeitos.ts`, `frontend/src/cenario/relacoes.ts`, `frontend/src/estilos/revelacao.css`
- Modificar: `frontend/src/cena/som.ts` (`notas(url, volume)`), `frontend/src/componentes/{Cena,Resultado,Moldura}.tsx`, `frontend/src/App.tsx`
- Criar asset: `static/assets/cenarios/terraco/audio/radio-notas.mp3` (skill `duora-assets`, ElevenLabs), registrado em `assets/manifest.md`
- Teste: `relacoes.test.ts`, `efeitos.test.ts`, `esquema.test.ts`, `e2e/RevelacaoIT.java` (testes novos), `e2e/RevelacaoCelularIT.java` (novo), `e2e/AxeIT.java` (estado "tela cheia")

**Interfaces:**
- Consome: `CenarioFront`, `Geometria` e `criarSom` (1a/1b).
- Produz:
  - Esquema:
    - `revelacao`: peça → lista de `Efeito`, com `type Efeito =`
      - `{ tipo: "meteoros" }`;
      - `| { tipo: "brilho"; cor: string; largura: number }`;
      - `| { tipo: "vapor" }`;
      - `| { tipo: "tremular" }`;
      - `| { tipo: "notas"; som: string; volume: number }`;
    - `raioPerto: number`;
    - `relacoes: { pecas: [string, string]; perto: string }[]`.
  - `cenario.json` do terraço:
    - `revelacao`:
      - telescópio `meteoros` (o efeito atual);
      - banco `brilho` (âmbar, 1,6 m);
      - manta `brilho` (vinho, 1,2 m);
      - rádio `notas` (`radio-notas.mp3`, volume 0,4);
      - lanterna `tremular`;
      - garrafa `vapor`.
    - `raioPerto` 2.0, e `relacoes` com estas frases:
      - manta–banco: "A manta ficou ao lado do banco, pronta para dividir.";
      - rádio–banco: "O rádio ficou perto do banco, tocando baixinho.";
      - lanterna–telescópio: "A lanterna ilumina o telescópio de perto.";
      - garrafa–telescópio: "A garrafa térmica ficou ao alcance de quem observa.";
      - manta–telescópio: "A manta ficou junto do telescópio, para as horas de espera.";
      - rádio–telescópio: "O rádio acompanha o telescópio com uma trilha baixa.";
      - lanterna–banco: "A lanterna clareia o banco.";
      - garrafa–banco: "A garrafa térmica ficou do lado de quem senta.";
      - manta–lanterna: "Manta e lanterna ficaram juntas: um canto quente e claro.";
      - manta–garrafa: "Manta e garrafa térmica lado a lado: ninguém passa frio.";
      - rádio–lanterna: "Rádio e lanterna juntos fazem um canto de festa.";
      - rádio–garrafa: "Rádio e garrafa térmica lado a lado: música e algo quente.".
  - `relacoes.ts`: `frasesDeRelacao(c: CenarioFront, g: Geometria, pecas: { peca: string; lugar: string }[]): string[]`. Devolve as frases dos pares colocados a no máximo `raioPerto` metros, até 2, na ordem do JSON. O `#resultado-texto` passa a ser a legenda da 1b seguida dessas frases, separadas por espaço.
  - `efeitos.ts`: `criarEfeitos(camada: HTMLElement, c: CenarioFront, g: Geometria, som: Som): Efeitos`, com:
    - `mostrar(pecas: { peca: string; lugar: string }[], aoVivo: boolean)`: cria por peça os elementos `aria-hidden` com `data-efeito="<tipo>"` e `data-peca`, posicionados pelo lugar. `"meteoros"` delega aos meteoros atuais. `"notas"` toca `som.notas(...)` só com `aoVivo`, mesmo com o som geral desligado, e falha em silêncio;
    - `limpar()`.
    - O CSS de `revelacao.css` define as animações e as versões com `motion-reduce:`.
  - Tela cheia no celular: a `Moldura` ganha `data-tela-cheia="true"` quando o momento é noite e a largura é de `celular`.
    - A `.cena` fica `fixed` em `inset 0`, e o `#resultado` aparece sobre a parte de baixo dela.
    - O botão `#ver-painel` "Ver o painel" troca para `data-tela-cheia="false"`, e o botão "Ver o céu" volta.
    - O foco da revelação continua indo ao `#resultado-titulo`.

- [ ] **Passo 1:** escrever os testes.
  - `relacoes.test.ts`, com a geometria real do terraço:
    - `telescopioEmC1ELanternaEmL2SaoPerto` → contém a frase de lanterna–telescópio;
    - `lanternaDoOutroLadoNaoEPerto` (telescópio em C1, lanterna em R2) → não contém;
    - `noMaximoDuasFrases`.
  - `efeitos.test.ts`:
    - `cadaTipoCriaSeuElemento` (os 5 tipos);
    - `notasSoTocamAoVivo` (som falso: `aoVivo` falso → `notas` não chamado).
  - `esquema.test.ts`: `relacaoComPecaInexistenteERecusada`.
  - `RevelacaoIT`, com a sonda de `AudioContext` que já existe:
    - `cadaPecaColocadaTemSeuEfeitoNaNoite`: telescópio, banco, manta e lanterna → `[data-efeito=brilho]` 2, `[data-efeito=tremular]` 1, e `#meteoros` com `data-lado`;
    - `radioTocaNotasComSomDesligado`: com o rádio colocado e `#som` em `aria-pressed="false"` → um `createBufferSource` iniciado na revelação;
    - `recarregarNaNoiteNaoTocaNotas`: recarregar em `REVELADO` → nenhum buffer iniciado;
    - `textoDoResultadoMudaComAPosicao`: duas composições (lanterna em L2 e em R2, com o telescópio em C1) → textos diferentes de `#resultado-texto`.
  - `RevelacaoCelularIT` (390×844, via `novoCelular()`):
    - `revelacaoOcupaATela`: a caixa da `.cena` tem topo 0 e altura ≥ 0,9 × 844, e o heading do resultado fica visível;
    - `verOPainelEVoltar`: tocar "Ver o painel" → o botão "Som" visível (o rótulo de ajuste muda na Task 5, por isso não serve aqui); tocar "Ver o céu" → a cena volta a ocupar a tela.
  - `AxeIT`: estado "tela cheia" sem violações.
- [ ] **Passo 2:** gerar o asset com a skill `duora-assets`. Pela ElevenLabs (efeito sonoro, cerca de 4 s, "notas suaves de rádio antigo tocando jazz baixo, sem voz"), converter para MP3 192 kbps / 48 kHz, conferir com `ffprobe` e registrar no `assets/manifest.md`. Gerar uma vez só, sem repetir a chamada para tentar de novo.
- [ ] **Passo 3:** RED.
- [ ] **Passo 4:** implementar.
- [ ] **Passo 5:** `pnpm test`, o `RevelacaoIT`, o `RevelacaoCelularIT` e o `AxeIT` em chromium, verdes, e o `TamanhoDoJsInicialTest` ≤ 80 KB. Commit `feat(encontro): efeito por peça na revelação, texto pelas posições e tela cheia no celular`.

### Task 5: Concordância para ajustar e saída gentil

**Arquivos:**
- Modificar: `frontend/src/sessao/{sessao.ts,novidade.ts}`, `frontend/src/dominio/mensagens.ts`, `frontend/src/componentes/{Resultado,Encerrado,Topo}.tsx`
- Teste: `sessao.test.ts`, `novidade.test.ts`, `mensagens.test.ts`, `Resultado.test.tsx` (novo), `e2e/AjusteCombinadoIT.java` (novo), `e2e/{JornadaDoisNavegadoresIT,AcessibilidadeFocoIT,TerracoPagina}.java` (as mudanças listadas nas restrições), `e2e/AxeIT.java` (estado "pedido de ajuste")

**Interfaces:**
- Consome: Task 1 (`ajuste`, `api.pedirAjuste()`, `api.recusarAjuste()`).
- Produz:
  - Sessão: `ajustar()` → `pedirAjuste`; `recusarAjuste()` (para quem recebe, "Agora não"; para quem pediu, "Cancelar pedido").
  - `Resultado`, conforme `visao.ajuste`:
    - sem pedido: o botão `#ajustar` "Pedir para ajustar";
    - pedido próprio: o texto "Esperando <nome> aceitar." e o botão "Cancelar pedido";
    - pedido do outro: o texto "<nome> quer ajustar a composição." e os botões `#aceitar-ajuste` "Aceitar ajustar" e `#recusar-ajuste` "Agora não".
  - Avisos e novidades:
    - ao pedir: "Pedido enviado. Esperando <nome> aceitar.";
    - quem recebe: "<nome> quer ajustar a composição.";
    - aceite visto por quem pediu: "<nome> aceitou. De volta à montagem.";
    - quem aceitou: "De volta à montagem. As confirmações foram zeradas." (o texto atual);
    - recusa vista por quem pediu: "<nome> prefere manter esta noite.";
    - quem recusou: "Tudo bem, a noite fica como está.";
    - cancelado, visto por quem pediu: "Pedido cancelado.".
    - Depois do aceite, o foco dos dois vai ao primeiro item do inventário, pela regra atual de `destinoDoFoco`.
  - Saída (sempre unilateral, como manda o spec):
    - a confirmação continua: `Isso encerra o terraço para <nome> também.` / "Encerrar para os dois";
    - quem saiu: título `#encerrado-titulo` "Você saiu do terraço", texto "O encontro terminou para os dois.";
    - o outro: título "O encontro terminou", texto "Obrigado por montar o terraço com <nome>.", sem dizer quem saiu;
    - `EXPIROU` e `SALA_INEXISTENTE` mantêm título e texto de hoje.
    - `TerracoPagina.tituloEncerrado()` passa a aceitar os três títulos (regex).

- [ ] **Passo 1:** escrever os testes.
  - Vitest: `pedirAjusteChamaAApi`; `resultadoMostraAceitarERecusarParaQuemRecebe`; `resultadoMostraCancelarParaQuemPediu`; e os textos novos de `textoEncerrado` e `novidade`.
  - `AjusteCombinadoIT`:
    - `ajusteSoVoltaAMontagemDepoisDoAceite`: Ana pede → Ana continua na noite (`data-momento="noite"`) com "Esperando Bruno aceitar." → Bruno toca "Aceitar ajustar" → os dois em `crepusculo`;
    - `recusaMantemANoite`: Bruno toca "Agora não" → Ana vê "Bruno prefere manter esta noite." e continua na noite;
    - `quemFicaVeOEncontroTerminouSemCulpa`: Ana sai → Bruno vê o heading "O encontro terminou" e "Obrigado por montar o terraço com Ana.", e o texto não contém "saiu".
  - `AxeIT`: estado "pedido de ajuste" (dos dois lados) sem violações.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. Atualizar os testes listados nas restrições, citando cada um no corpo do commit.
- [ ] **Passo 4:** `pnpm test` e a suíte e2e completa em chromium, verdes. Commit `feat(encontro): ajustar pede o aceite do outro e a saída termina com gentileza`.

### Task 6: Correções de texto e defeitos do playtest

**Arquivos:**
- Modificar:
  - `frontend/src/dominio/mensagens.ts` (`porArtigo`);
  - `frontend/src/componentes/{Inventario,Entrada,Convite,Falta,Resultado,Encerrado}.tsx`;
  - `frontend/src/sessao/{sessao.ts,novidade.ts}`;
  - `frontend/src/main.tsx` (`hashchange`);
  - `frontend/src/estilos/base.css` (foco dos títulos com `tabindex -1`).
- Teste: `mensagens.test.ts`, `Inventario.test.tsx`, `Falta.test.tsx`, `sessao.test.ts`, `e2e/TrocaDeConviteIT.java` (novo), `e2e/PrimeiraCargaIT.java` (novo)

**Interfaces:**
- Produz:
  - `porArtigo(artigo: string): string`: "o rádio" → "pelo rádio", "a manta" → "pela manta", "os …" → "pelos …", "as …" → "pelas …". O inventário passa a dizer `Troca <artigo> <porArtigo(artigo)>`, ex.: "Troca a manta pelo rádio".
  - Entrada com convite: "Você recebeu um convite para montar um terraço a dois. Escolha seu apelido."
  - `ehToque(): boolean` = `matchMedia("(hover: none) and (pointer: coarse)").matches`, injetado em `criarSessao` e nos componentes. Com toque:
    - a dica do convite fica só "O convite vale para uma entrada.";
    - o aviso de cópia fica "Convite copiado.";
    - a instrução de seleção perde " Esc cancela.".
    - Fora do toque, tudo como hoje.
  - "O que falta" (`Falta`) fica `hidden` quando as quatro linhas estão feitas.
  - `#resultado-titulo` e `#encerrado-titulo`: sem contorno em `:focus:not(:focus-visible)`. Com `:focus-visible`, o anel atual continua (o `AcessibilidadeLayoutIT` existente cobre).
  - Troca de convite: `sessao.receberConvite(token: string)`, chamada no início e a cada `hashchange` (com o `#convite=` lido e limpo, como hoje). Sem sessão → entrada com convite.
    - Se `token` é o `visao.convite` da própria sala: aviso "Este é o convite do seu terraço. Mande para a outra pessoa." Substitui o aviso de "janela anônima".
    - Se há sessão em outra sala: entrada com convite, mais a dica "Entrar neste convite encerra o terraço atual.". Ao enviar, `api.sair()` (ignorando `SEM_ASSENTO`, `SALA_INEXISTENTE` e `SALA_ENCERRADA`) e depois `api.entrar(token, apelido)`.
  - Primeira carga: com o 204 da Task 1, nenhuma resposta ≥ 400 e nenhum erro no console.

- [ ] **Passo 1:** escrever os testes.
  - Vitest:
    - `porArtigo` (os 4 casos);
    - `trocaDizPelo` ("Troca a manta pelo rádio" e "Troca a lanterna pela garrafa térmica");
    - `faltaSomeQuandoNadaFalta`;
    - `noToqueAInstrucaoNaoCitaEsc`;
    - `proprioConviteAvisaSemTrocar`;
    - `conviteDeOutraSalaSaiEEntra` (ordem das chamadas: `sair` e depois `entrar`).
  - `TrocaDeConviteIT`:
    - `conviteNovoNaMesmaAbaTrocaDeSala`: Ana com Bruno na sala 1; Carla cria a sala 2; Ana navega na mesma aba para `/#convite=<sala 2>` → vê "Entrar no terraço" e a dica → entra → `#par` contém "Carla". Bruno vê "O encontro terminou";
    - `proprioConviteNaMesmaAbaAvisa`.
  - `PrimeiraCargaIT.semErroNoConsoleNemRespostaDeErro`: contexto novo, coletar `onConsoleMessage` do tipo `error` e `onResponse` com status ≥ 400 até a tela de entrada → as duas listas vazias.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm test`, os dois ITs novos, o `JornadaDoisNavegadoresIT` e o `AcessibilidadeLayoutIT` em chromium, verdes. Commit `fix(encontro): textos do playtest, convite na mesma aba e primeira carga sem erro`.

### Task 7: Playtest por agente

**Arquivos:**
- Criar: `.claude/handoff/playtest-1c/` (fora do Git), com as capturas e o `relatorio.md`
- Modificar: o handoff da sessão (resultado e próximos passos)

- [ ] **Passo 1:** antes do playtest, rodar os três navegadores (`./mvnw -q -Pe2e verify -Dnavegador=<x>`, um por vez) e o `./mvnw -q verify`, tudo verde.
- [ ] **Passo 2:** o controlador sobe `./mvnw spring-boot:run` (porta conferida antes) e despacha **um agente novo, que não implementou a 1c**, com o roteiro e as perguntas de `duora-java-mvp.md` §3. O agente joga as duas pessoas pelo Playwright MCP:
  - Ana na página do MCP, em 1440×900;
  - Bruno num segundo contexto isolado criado por `browser_run_code_unsafe` (`browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })`), para os cookies não se misturarem.
  - **Roteiro:**
    1. criar e entrar;
    2. uma composição completa com pelo menos uma sugestão aceita e uma ignorada;
    3. revelar;
    4. pedir ajuste (uma vez recusado, outra aceito);
    5. uma segunda composição diferente e revelar de novo;
    6. Bruno sai.
  - Capturas dos dois lados em cada passo, em `target/playtest-1c/`, copiadas depois para `.claude/handoff/playtest-1c/` do repositório principal.
- [ ] **Passo 3:** o agente responde cada pergunta com "sim" ou "não", uma frase de porquê e as capturas que provam. Critérios de "sim":
  - **2 (cada pessoa percebe a contribuição da outra):** em pelo menos 3 momentos, a mudança do outro aparece na captura sem rolar (destaque na peça ou status abaixo dos nomes), nos dois aparelhos;
  - **3 (negociação ou curiosidade):** houve uma sugestão com destino aceita, uma ignorada e um pedido de ajuste recusado e depois aceito, e cada um aparece nas capturas dos dois lados;
  - **4 (o resultado parece consequência das escolhas):** as duas revelações têm títulos ou textos diferentes, com pelo menos uma frase de posição relativa diferente, e efeitos visíveis diferentes nas capturas;
  - **5 (vontade de experimentar outra composição):** da noite até a montagem de novo bastam no máximo 2 toques de cada lado, e a segunda revelação difere da primeira;
  - a pergunta 1 também é respondida, para registro.
- [ ] **Passo 4:** com as quatro respostas "sim", encerrar o servidor, registrar o relatório no handoff e fechar a etapa (skill `duora-etapa`). Com algum "não":
  - cada falha vira uma task de correção, com teste antes;
  - nova rodada de playtest por outro agente novo, no máximo 2 rodadas;
  - persistindo, levar ao dono **só a escolha**: o que mudar na mecânica, com 2–3 opções e uma recomendação.

## Ordem e paralelismo
- **Primeiro:** a Task 0 (refatoração), e depois a Task 1, as duas sozinhas e no mesmo worktree. A Task 1 muda o contrato da API e o cliente mínimo.
- **Onda A, em paralelo (3 worktrees):**
  - Task 2: `cena.ts` (desenho da peça), `Painel`, `destaque.css`;
  - Task 4: `efeitos.ts`, `relacoes.ts`, o esquema e o JSON, `som.ts`, `Resultado` (texto), `Moldura`, `revelacao.css` e o asset;
  - Task 6: `mensagens.ts`, `Inventario`, `Entrada`, `Convite`, `Falta`, `main.tsx` e `base.css`.
  - O `sessao.ts` muda nas Tasks 2 e 6, em funções diferentes, e o `app.css` ganha uma linha de import em 2 e 4. O merge resolve à mão.
  - O asset da Task 4 (ElevenLabs) e o Maven entram na fila de "um pesado por vez".
- **Onda B, em paralelo (2 worktrees), depois do merge da onda A:**
  - Task 3: modo de sugestão na sessão e na cena, mais o botão no `Inventario`;
  - Task 5: ajuste e saída na sessão, mais `Resultado`, `Encerrado` e `Topo`.
- **Depois:** a suíte inteira nos 3 navegadores, a revisão final e o merge.
- **No fim:** a Task 7 (playtest por agente).

## Decisões do dono
- **O que "Sair" leva a quem saiu na 1c.** O spec diz "leva quem saiu à decisão privada", mas a decisão privada só tem efeito com contas e conexões (Etapa 5). Opções:
  - (a) mostrar já a pergunta "Quer encontrar <nome> de novo?" (Sim/Não), guardada só em memória e sem efeito até a Etapa 5;
  - (b) mostrar a tela neutra "Você saiu do terraço" e construir a decisão privada na Etapa 5, sobre conexões reais.
  - **Recomendação: (b)**, porque evita um botão que não faz nada. O plano segue (b) por padrão; escolher (a) acrescenta um passo à Task 5 (tela e `POST /api/sala/decisao` em memória) e não bloqueia o resto.
