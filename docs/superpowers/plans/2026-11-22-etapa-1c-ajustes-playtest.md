# Etapa 1c: Ajustes do playtest (motor de encontro). Plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans, task a task. Passos com checkbox (`- [ ]`).

> **Revisão 2026-11-22:** reescrito a partir de três auditorias contra o código da 1b (back, front, transversal). As tasks agora são pequenas e cada uma tem trilha (B = servidor, F = front, A = asset, D = docs, P = playtest). A abertura sem palavras (emenda de paridade) é dividida em B8a–B8c, F8a e F8b.

**Objetivo:** o encontro passa a mostrar o que o outro fez, deixa sugerir com destino, dá a cada peça um efeito próprio na revelação, pede concordância para voltar a ajustar, começa com uma abertura sem palavras e termina de forma gentil. Um novo playtest por agente responde "sim" às perguntas 2–5.

**Arquitetura:**
- No servidor, as regras ficam no motor (`game/Regras`, com o `Cenario` como 1º parâmetro) e valem para qualquer cenário. A fase vira um tipo selado antes de ganhar o pedido de ajuste e a abertura. `prontos` e `sinal` continuam no `Estado` (L1, opção A; a migração para `Montando` fica para a Etapa 5).
- No front, uma task de costuras (F0) cria os pontos de extensão (`MarcasDaCena`, `criarElementosDoEfeito`, `AcoesDaSessao`, `usePassagemAoVivo`). As tasks seguintes só acrescentam. Efeitos, frases de relação e abertura são dados do `cenario.json`; o código tem um desenho por **tipo**.

**Stack:** Java 25, Spring Boot 4.2.0, Vite + React 19 + TS + Tailwind 4, valibot (só em `cenario/ler.ts`), Vitest, Playwright Java 1.65.1, Playwright MCP (playtest), ElevenLabs (som do rádio).

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1c). Contratos: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Perguntas do playtest: `duora-java-mvp.md` §3. Pré-requisito: 1a e 1b integradas (main @ 95d6a23).

**Caminhos:** Java sem prefixo parte de `src/main/java/bipotheking/net/duora/` (testes: `src/test/java/bipotheking/net/duora/`); TS sem prefixo parte de `frontend/src/`; `cenario.json` = `src/main/resources/static/assets/cenarios/terraco/cenario.json`.

**Comandos:** Java pontual `./mvnw -q test -Dtest=<Classe>`; tudo `./mvnw -q verify`; e2e `./mvnw -q -Pe2e verify -Dit.test='<IT>' -Dnavegador=<x>`; front `pnpm -C frontend exec vitest run <arquivo>`, `pnpm -C frontend test`, `pnpm -C frontend lint`. Maven e e2e só com a trava de comando pesado.

**Skills de processo:** `mattpocock-skills:tdd` na implementação; `superpowers:systematic-debugging` em falhas; `superpowers:verification-before-completion` antes de cada commit; `superpowers:requesting-code-review` + `mattpocock-skills:code-review` na revisão; `duora-etapa` ao fechar. Cada task traz a sua linha **Skills**.

## Restrições globais

- **Engenharia (contratos):** commits de refatoração separados dos de comportamento, sempre com a suíte verde ("make the change easy, then make the easy change"). Todo `switch` sobre fase ou tela é exaustivo e sem `default` onde duas ou mais variantes se comportam diferente; guardas de uma fase só usam `if (!(estado.fase() instanceof Fase.Montando m)) return recusar(FASE_INVALIDA);` (Replace Nested Conditional with Guard Clauses).
- **Herdadas (0, 1, 1a, 1b):** Conventional Commits com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; `-Werror`; Spotless; lint do front com `--max-warnings 0`; e2e sem skip nos 3 navegadores; um comando pesado por vez com `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`; CSP inalterada (estilo dinâmico só por CSSOM, `style.setProperty`).
- **`game/` sem Jackson:** nenhum record de `game/` vai direto para a visão. `Fase`, `Sinal` e `Abertura` passam por records de `room/`.
- **Visão:** campos novos entram **no fim** dos records (`Visao(…, sinal, resultado, ajuste, abertura)`, `VisaoSinal(peca, deAssento, lugar)`, `PecaDoParceiro(peca, assento, lugar, destinosSugeriveis)`). As mutações continuam sem `ETag` (só o GET tem). O ETag é o hash do JSON, então muda com `ajuste` e `abertura` mesmo com `versao` igual.
- **Relógio de teste no servidor:** expiração e tempos do servidor em teste usam o `RelogioDeTeste` `@Primary` (testes de `room/`) ou o `RelogioAdiantavel` `@Primary` (e2e, extraído na F0e), mais `@SpringBootTest(properties = …)` quando o tempo do cenário precisa mudar (`duora.cenarios.abertura.maximo`, lida pelo wither `Cenario.comMaximoDeAbertura`). `page.clock()` só controla timers do navegador (destaque de 2400 ms), nunca uma expiração do servidor.
- **Abertura na suíte e2e:** o perfil `e2e` do `pom.xml` passa `duora.cenarios.abertura.maximo=PT0S` ao failsafe (F8b). A abertura conclui apagada já na entrada, e os ITs da 1a/1b ficam intactos. Só o `AberturaIT` liga a abertura (`properties = "duora.cenarios.abertura.maximo=PT90S"`).
- **Som:** o som geral desligado continua silencioso, com uma exceção (decisão do dono): as notas do rádio tocam uma vez, só na passagem ao vivo para a noite, nunca ao recarregar. Todo som de efeito é referenciado por **nome-base** (`"radio-notas"`, `^[a-z0-9-]+$`), e a URL sai do `CenarioFront.audio(nome)` (`/assets/cenarios/<id>/audio/<nome>.mp3`), nunca de caminho no JSON.
- **JS inicial:** teto de 90 KB gzip (`TamanhoDoJsInicialTest`, `LIMITE_BYTES = 92_160`); meta 80 KB; a 1b terminou em 84,78 KB. Toda task de front roda o `TamanhoDoJsInicialTest` e grava o número no corpo do commit.
  - **Plano de lazy (gatilho: > 89 KB):** a task que passar do gatilho move os desenhos só noturnos (`brilho`, `vapor`, `tremular`) para `cena/efeitosNoite.ts`, carregado por `import()` na primeira visão `REVELADO` (fora dos `modulepreload`). Até carregar, esses tipos não criam elemento, e a cena redesenha quando o chunk chega.
  - **Acima de 90 KB depois do lazy:** parar e levar ao dono a escolha de preact/compat (ruling do controlador). Até lá, React.
- **Textos:** pt-BR, voz ativa, gênero neutro quando se refere à pessoa. Os textos novos estão fixados nas tasks; usar exatamente como escritos.
- **Movimento reduzido:** toda animação nova tem estado **base** visível (anel do destaque, vapor parado, chama acesa), e os keyframes terminam nele (a regra global de `base.css:70-78` zera a duração).
- **Seletores:** os testes novos usam papel, rótulo e texto. Os efeitos decorativos (`aria-hidden`) usam `data-efeito` e `data-peca`; os estados de axe novos ficam nos ITs da própria task, pelo `Axe.assertSemViolacoes` (F0e), e o `AxeIT` só muda na F0e.
- **Assets:** o MP3 novo segue a skill `duora-assets` (A4a).
- **Mudanças de asserção permitidas, e só estas.** Cada uma vai citada no corpo do commit com o motivo. "Mecânica" = mesma intenção, outra forma de escrever; "arranjo" = muda a preparação, não a asserção.

| Task | Teste | Mudança |
|---|---|---|
| B0a | `ConcorrenciaTest:100,102` | compara com `"REVELADO"` (mecânica) |
| B0b | `RegrasTest:26,283,292,324,378` | `isInstanceOf(Fase.X.class)` (mecânica) |
| B0b | `RegrasTest:144-147` | `@CsvSource` de fase vira `@MethodSource` com as instâncias (mecânica) |
| B0b | `IgualdadePorValorTest:32-33` | `new Estado(new Fase.Montando(), …)` (mecânica) |
| B1a | `SalaApiTest.semCookieNaoHaAssento:157-175` | sai a linha do GET; renomeado `mutacaoSemCookieContinua401` |
| B1b | `RegrasTest` (testes de `sinalizar`) | ganham destino; `sinalSomeQuandoODonoMoveAPeca:422` e `sinalContinuaQuandoOutraPecaSeMove:431` reencenados com a LANTERNA (com o centro cheio não há destino válido) |
| B1b | `ParametrosDoCenarioTest:58` | `Regras.sinalizar` ganha destino (TELESCOPIO em C1 → C2) |
| B1b | `SalaApiTest.visaoTemExatamenteOsCamposDoContrato:91-117` | + `sinal.lugar`, + `destinosSugeriveis` na peça do parceiro |
| B1b | `SalaApiTest.sinalApareceParaODonoDaPecaSemMudarVersao:330` | corpo ganha `lugar`; renomeado `sinalComDestinoApareceParaODono` |
| B1b | `cliente.test.ts:91`, fake de `sessao.test.ts:54-56,220-225` | `sinalizar(peca, lugar)` |
| B1c | `RegrasTest` (testes de `ajustar`) | passam a `pedirAjuste`/`recusarAjuste` |
| B1c | `SalaApiTest.jornadaCompletaAteARevelacaoEAjusteDeNovo:302` | os dois pedem |
| B1c | `SalaApiTest.visaoTemExatamenteOsCamposDoContrato`, `mutacaoSemCookieContinua401`, `mutacaoSemXDuoraEhRecusada` | + `ajuste`; + linha `/api/sala/ajustar/recusar` |
| B1c | `cliente.test.ts:81`, fake de `sessao.test.ts` | sai `api.ajustar` |
| B1c | `AcessibilidadeFocoIT.ajustarDeNovoLevaOFocoAPrimeiraPeca:222`, `RevelacaoIT.movimentoReduzidoMostraRiscosParadosQueSomemAoAjustar:112` | os dois clicam "Ajustar de novo" (R4) |
| B8b | `RegrasTest`, `ParametrosDoCenarioTest`, `CenarioMinimoTest`, `TerracoTest` (8 chamadas) | `parceiroEntrou(c, e, agora)` (mecânica) |
| B8c | `SalaApiTest.visaoTemExatamenteOsCamposDoContrato`, `mutacaoSemCookieContinua401`, `mutacaoSemXDuoraEhRecusada` | + `abertura`; + linha `/api/sala/abertura` |
| F0 | `cena.test.ts`, `Cena.test.tsx` | `desenhar(visao, marcas)` (mecânica) |
| F0 | `mensagens.test.ts` (`textoEncerrado`) | lê `.texto` (mecânica) |
| F3 | `sessao.test.ts:226`, `novidade.test.ts:34` | texto com o destino |
| F3 | `AcessibilidadeLayoutIT.pecaSinalizadaTemSeloEstaticoENomeComMovimentoReduzido` | sinal em dois passos |
| F3 | `Inventario.test.tsx` (item sinalizado) | ganha o botão "Mover …" |
| F5 | `sessao.test.ts:167,287`, `Painel.test.tsx:9`, `useSessao.test.tsx:55`, `mensagens.test.ts` (`textoEncerrado`) | títulos e textos novos do encerrado |
| F5 | `JornadaDoisNavegadoresIT` (saída), `TerracoPagina.tituloEncerrado()` | títulos novos (regex); `VisualIT:156` e `PollingIT:177,188` passam pela regex |
| F5 | `AcessibilidadeFocoIT:222`, `RevelacaoIT:112` | "Pedir para ajustar" e "Aceitar o ajuste" |
| F6a | `Inventario.test.tsx` (troca) | "Troca a manta pelo rádio" |
| F6b | `sessao.test.ts:262` | sai o aviso de janela anônima |
| F8b | helpers `RegrasTest.montando():458`, `TerracoTest`, `ParametrosDoCenarioTest`, `CenarioMinimoTest`, `SalaApiTest.entrar:732`, `ConcorrenciaTest.mesaMontando`, `CenarioApiTest` (terraço) | passam a abertura com dois toques (arranjo) |

## Rulings de planejamento (o dono pode reverter)
- **R1. Sem fallback no 204:** o cliente converte o 204 de `obterSala` em `ErroApi({ status: 401, codigo: "SEM_ASSENTO" })`. O caminho de "sem assento" do controlador continua o de hoje.
- **R2. "Ignorar" uma sugestão é não fazer nada.** O sinal expira em `duracaoSinal` (20 s no terraço). Não existe "Agora não" para sugestões; ele existe só para o pedido de ajuste, que não expira.
- **R3. O destino sugerido precisa estar livre**, na zona da peça e diferente do lugar atual. Se o destino for ocupado antes do aceite, o sinal cai.
- **R4. Pedidos de ajuste cruzados viram aceite:** se B pede enquanto o pedido de A está pendente, o resultado é o de B aceitar.
- **R5. "Perto" é a distância entre os lugares ≤ `raioPerto` metros**, medida no `cena.json` (`px / pxPorMetro`); o terraço usa 2,0 m. No máximo 2 frases de relação, na ordem do JSON. `raioPerto` e `relacoes` são só do front (o servidor não os espelha).
- **R6. Tela cheia no celular é de layout (CSS), não a Fullscreen API.** A API exige gesto, e quem recebe a revelação pelo polling não fez gesto nenhum.
- **R7. "Sair" na 1c mostra uma tela neutra ("Você saiu do terraço"); a decisão privada chega na Etapa 5**, sobre conexões reais (contratos:180; dono, 2026-11-22). A spec ganha a nota na D1.

## Foco de revisão
1. **Foco coberto no celular alto** (Tab até o último item com topo + cena + status sticky empilhados em 390×844). Teste: `focoNoUltimoItemNaoFicaCobertoNoCelular` (F2).
2. **Região viva escondida na tela cheia:** o `#aviso` não pode ficar `visibility:hidden`/`display:none` enquanto a noite cobre a tela, senão um pedido de ajuste chega mudo. Teste: `avisoContinuaAnunciandoEmTelaCheia` (F4c).
3. **Abertura vencida sem polling:** uma colocação aos 90,5 s, sem consulta no meio, tem de ser aceita, e o avanço preguiçoso não pode renovar a expiração da sala. Testes: `colocarDepoisDoMaximoSemConsultaEAceito` e `avancarAberturaNaoRenovaExpiracao` (B8c).
4. **Destino ocupado entre a sugestão e o aceite**, inclusive em corrida: nunca fica sinal ativo com destino ocupado, e o aceite em voo recebe 409 com o aviso de lugar ocupado. Testes: `sinalEColocacaoNoDestinoSimultaneos` (B1b) e `aceiteDepoisQueODestinoFoiOcupadoMostraAviso` (F3).
5. **Convite antigo aberto depois que o parceiro entrou:** abrir o próprio link de novo na mesma aba não pode sair da sala nem perder o encontro. Teste: `conviteJaUsadoNestaAbaNaoSai` (F6b).

Também cobertos: pedidos de ajuste simultâneos (`pedidosDeAjusteSimultaneosTerminamEmMontagem`, B1c), ETag que acompanha o pedido (`etagMudaComOPedidoDeAjuste`, B1c), recarregar na noite sem notas (`recarregarNaNoiteNaoTocaNotas`, F4c), sair com pedido pendente (`sairComPedidoPendenteEncerra`, B1c).

---

## Ordem e paralelismo

No máximo 3 implementadores por onda e nunca dois no mesmo arquivo (os arquivos de cada task estão no bloco **Arquivos**). As tasks que tocam `sessao.ts`, `novidade.ts`, `mensagens.ts` ou `AxeIT` andam em série entre si: B1b → B1c → F0/F0e → F2 → F3 → F4c → F5 → F6b → F8a. O back só-Java (B8a–B8c), os dados e desenhos da revelação (F4a, F4b), os textos sem sessão (F6a), os docs (D1) e o asset (A4a) correm ao lado. O controlador executa A4a e P7 e não conta como implementador.

| Onda | Implementador 1 | Implementador 2 | Implementador 3 | Controlador |
|---|---|---|---|---|
| 1 | B0a → B0b → B1a → B1b → B1c | F4a | F6a | A4a (primeiro) |
| 2 | B8a → B8b → B8c | F0 | F0e | — |
| 3 | F2 | F4b (precisa de A4a) | D1 | — |
| 4 | F3 | — | — | — |
| 5 | F4c | — | — | — |
| 6 | F5 | — | — | — |
| 7 | F6b | — | — | — |
| 8 | F8a | — | — | — |
| 9 | F8b | — | — | — |
| 10 | suíte inteira nos 3 navegadores (um por vez), revisão final da branch, merge | | | |
| 11 | — | — | — | P7 |

Modelo sugerido: `opus · high` para B0b, B1b, B1c, B8b, B8c, F0, F4b, F4c, F8a e F8b (forma, concorrência, abertura, efeitos); `opus · medium` para as demais.

---

### A4a: Asset das notas do rádio (controlador)

**Skills:** `duora-assets`.

**Arquivos:**
- Criar: `src/main/resources/static/assets/cenarios/terraco/audio/radio-notas.mp3`; o bruto escolhido em `assets/source/audio/brutos/radio-<n>.mp3` (os outros brutos ficam fora do Git, no checkout principal)
- Modificar: `assets/source/audio/montar.sh` (linha nova), `assets/manifest.md`

- [ ] **Passo 1:** gerar pela ElevenLabs (`eleven_text_to_sound_v3`, flow `1v6oSiEKugInq7KOaP0r`, `generations_count` padrão): "notas suaves de rádio antigo tocando jazz baixo, sem voz", cerca de 4 s. Teto de 200 créditos na 1c, sem nova confirmação (D2). Nunca repetir uma geração em curso; só uma nova chamada depois de `has_failures` confirmado.
- [ ] **Passo 2:** escolher a variação por medição (`ebur128` loudness/LRA e espectrograma) e montar pela linha nova do `montar.sh` (`loudnorm=I=-20:TP=-2:LRA=11`, fade curto, `aresample=48000`, `-b:a 192k`). `ffprobe` → `mp3,48000,192000`.
- [ ] **Passo 3:** linha na tabela "Áudio" do manifesto (export, bruto escolhido, modelo, id da geração, tratamento) e a linha "Custo". Licença `CC BY 4.0 (gerado pelo autor no ElevenLabs)`, status `Candidato a FINAL` (o dono decide no pacote do playtest, D3). O arquivo **não** entra no `assetsDoShell`: offline, as notas falham em silêncio.
- [ ] **Passo 4 (fallback):** se a nova chamada falhar ou faltar crédito, montar no `montar.sh` um trecho de 4 s de `brutos/trilha-2..4.mp3` com filtro de rádio (`highpass=400,lowpass=3000`, leve saturação). Manifesto: "derivado local de `trilha-<n>`", mesma licença e status.
- [ ] **Passo 5:** com a trava, `./mvnw -q verify` (o `ServiceWorkerVersaoTest` confere a impressão nova). Commit `feat(assets): terraço — notas do rádio (MP3 + manifesto + montar.sh)`.

### B0a: Fase da visão como texto (refatoração preparatória)

**Skills:** `mattpocock-skills:tdd`.

**Arquivos:**
- Modificar: `room/Visao.java`
- Teste: `room/ConcorrenciaTest.java` (:100, :102)

**Interfaces:**
- Produz: `Visao(String cenario, String fase, …)`, com `fase = estado.fase().name()` por ora. O JSON fica byte a byte igual, e o ETag também. Motivo: com `Fase` selada, o Jackson serializaria o record vazio como `{}`, e `game/` não pode usar `@JsonValue`.

- [ ] **Passo 1:** trocar o componente e o `ConcorrenciaTest` (compara com `"REVELADO"`).
- [ ] **Passo 2:** `./mvnw -q verify` verde (o `SalaApiTest` confere `$.fase` como texto). Commit `refactor(room): fase da visão como texto`.

### B0b: Fases do encontro como tipo selado

**Skills:** `mattpocock-skills:tdd`, `mattpocock-skills:codebase-design`.

**Arquivos:**
- Modificar: `game/{Fase,Estado,Regras}.java`, `room/Visao.java`
- Criar: `game/FaseTest.java`
- Teste: `game/RegrasTest.java`, `game/IgualdadePorValorTest.java` (mudanças mecânicas da tabela)

**Interfaces:**
- Produz:
  - `public sealed interface Fase { String nome(); record AguardandoPar() implements Fase {…} record Montando() implements Fase {…} record Revelado() implements Fase {…} }`, aninhados no molde de `Decisao.Aceita`. `nome()` por record (sem `switch`): `"AGUARDANDO_PAR"`, `"MONTANDO"`, `"REVELADO"`.
  - `Estado.inicial()` → `new Fase.AguardandoPar()`.
  - `colocar` e `confirmar` **preservam** `estado.fase()`. Só `parceiroEntrou` e o aceite do ajuste criam `Montando` novo (senão a abertura concluída da B8b seria apagada a cada jogada).
  - `Visao` usa `estado.fase().nome()` e `instanceof`.

- [ ] **Passo 1:** testes. `FaseTest.nomesNaRedeSaoOsDeHoje` (os três textos); `FaseTest.switchExaustivoCobreAsTres` (método com `switch` sem `default`, que só compila cobrindo as três); `FaseTest.igualdadePorValor` (`new Fase.Montando().equals(new Fase.Montando())`); `RegrasTest.colocarPreservaAInstanciaDaFase` (`assertThat(depois.fase()).isSameAs(antes.fase())`).
- [ ] **Passo 2:** RED (`./mvnw -q test -Dtest='FaseTest,RegrasTest'`: não compila).
- [ ] **Passo 3:** refatorar em passos pequenos (Replace Type Code with Subclasses, na forma de records), rodando `RegrasTest`, `SalaApiTest` e `ConcorrenciaTest` a cada passo.
- [ ] **Passo 4:** `./mvnw -q verify` e o `JornadaDoisNavegadoresIT` em chromium verdes. Commit `refactor(game): fases do encontro como tipo selado`.

### B1a: Sala sem cookie responde 204 (servidor + cliente R1)

**Skills:** `mattpocock-skills:tdd`.

**Arquivos:**
- Modificar: `room/SalaController.java` (`consultar`), `frontend/src/api/cliente.ts` (`obterSala`)
- Criar: `e2e/PrimeiraCargaIT.java`
- Teste: `room/SalaApiTest.java`, `frontend/src/api/cliente.test.ts`

**Interfaces:**
- Produz:
  - `GET /api/sala` com cookie ausente ou vazio → 204 sem corpo e sem `ETag`, com os cabeçalhos de segurança e `Cache-Control: no-store`. O 204 sai antes de `salas.localizar`.
  - Token desconhecido continua 404 `SALA_INEXISTENTE`; lápide continua 410 com `motivo`; mutações sem cookie continuam 401 `SEM_ASSENTO`.
  - Cliente: só `obterSala` converte o 204 (R1). O `requisitar` genérico já trata 204 como `{ visao: null }`.

- [ ] **Passo 1:** testes.
  - `SalaApiTest.obterSalaSemCookieRespondeSemConteudo`: `status().isNoContent()`, `content().string("")`, `header().doesNotExist(ETAG)`, `header().string(CACHE_CONTROL, "no-store")`.
  - `mutacaoSemCookieContinua401` (o antigo `semCookieNaoHaAssento`, sem a linha do GET). `tokenDesconhecidoNaoEncontraSala` e `respostasDaApiTrazemCabecalhosDeSegurancaESemCache` inalterados.
  - `cliente.test.ts` `obterSalaSemConteudoViraSemAssento`: 204 → rejeita com `ErroApi` `{ status: 401, codigo: "SEM_ASSENTO" }`.
  - `PrimeiraCargaIT.semErroNoConsoleNemRespostaDeErro`: contexto novo; coletar `onConsoleMessage` do tipo `error` e `onResponse` com status ≥ 400 até o heading da entrada → as duas listas vazias.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify`, `pnpm -C frontend test` e o `PrimeiraCargaIT` em chromium verdes. Commit `feat(room): sala sem cookie responde 204`.

### B1b: Sinal com destino

**Skills:** `mattpocock-skills:tdd`.

**Arquivos:**
- Modificar: `game/{Sinal,Regras}.java`, `room/{Pedidos,SalaController,Sala,Visao}.java`; front mínimo `frontend/src/api/cliente.ts`, `frontend/src/dominio/visao.ts`, `frontend/src/sessao/sessao.ts`
- Teste: `game/{RegrasTest,ParametrosDoCenarioTest,CenarioMinimoTest}.java`, `room/{SalaApiTest,ConcorrenciaTest}.java`, `frontend/src/api/cliente.test.ts`, `frontend/src/sessao/sessao.test.ts`

**Interfaces:**
- Consome: `Fase` selada (B0b).
- Produz:
  - `record Sinal(Peca peca, Assento deAssento, Lugar destino, Instant expiraEm)` (o JSON `sinal.deAssento` já é contrato).
  - `Regras.sinalizar(Cenario c, Estado e, Assento quem, Peca peca, Lugar destino, Instant agora) -> Decisao`, com recusas nesta ordem: `PECA_ALHEIA` (peça de quem sinaliza) → `FASE_INVALIDA` (fora de `Montando` ou peça fora da cena) → `LUGAR_INVALIDO` (outra zona ou lugar atual) → `LUGAR_OCUPADO`. O novo sinal substitui o anterior, venha de quem vier.
  - `Regras.destinosSugeriveis(Cenario c, Estado e, Peca peca) -> List<Lugar>`: livres da zona, sem o atual, na ordem do cenário; vazio fora de `Montando` e com a peça fora da cena.
  - `Regras.colocar` mantém o sinal só se a peça sinalizada não mudou de lugar **e** `nova.ocupante(s.destino()).isEmpty()`.
  - `Pedidos.Sinalizar(@Nullable String peca, @Nullable String lugar)`; resolvedor `lugar(Cenario, @Nullable String)` no controlador, simétrico a `peca()`: nulo ou desconhecido → 422 `LUGAR_INVALIDO`. Precedência HTTP: 400 `CORPO_INVALIDO` → 403 `ORIGEM_RECUSADA` → 401 → 404 → 422 `PECA_INVALIDA` → 422 `LUGAR_INVALIDO` → 410 → recusas da regra.
  - `Sala.sinalizar(Assento, Peca, Lugar)`.
  - `Visao.VisaoSinal(String peca, Assento deAssento, String lugar)`; `Visao.PecaDoParceiro(String peca, Assento assento, @Nullable String lugar, List<String> destinosSugeriveis)`.
  - Cliente: `api.sinalizar(peca: string, lugar: string)`; `Visao` TS ganha `sinal.lugar` e `destinosSugeriveis`. Até a F3, a sessão envia `destinosSugeriveis[0]`, e ignora o toque se a lista estiver vazia.
  - O `detalhe` de `LUGAR_OCUPADO` não muda (o front usa o próprio texto por `codigo`).

- [ ] **Passo 1:** testes (terraço; ordem C1, C2, L1, L2, R1, R2).
  - `RegrasTest.sinalComDestinoLivreEAceito`: lanterna (B) em R2; A sinaliza `LANTERNA → R1` → `sinal == Optional.of(new Sinal(LANTERNA, A, R1, AGORA.plusSeconds(20)))`, `versao` igual.
  - `sinalParaLugarOcupadoERecusado` (manta em L2, `LANTERNA → L2` → `LUGAR_OCUPADO`); `sinalParaOutraZonaERecusado` (`LANTERNA → C1` livre → `LUGAR_INVALIDO`); `sinalParaOLugarAtualERecusado` (`→ R2` → `LUGAR_INVALIDO`); `sinalSobrePecaNaoColocadaEhRecusado` (`BANCO → C1`, banco fora → `FASE_INVALIDA`).
  - `sinalCaiQuandoODestinoEOcupado`: sinal `LANTERNA → L2`; A coloca a MANTA em L2 → `sinal` vazio.
  - `destinosSugeriveisSaoOsLivresDaZonaSemOAtual`: `completa()` (telescópio C1, banco C2, manta L1, lanterna R1) → `LANTERNA → [L2, R2]`, `MANTA → [L2, R2]`, `BANCO → []`. `destinosSugeriveisVaziosParaPecaForaDaCena` (`GARRAFA → []`); `destinosSugeriveisVaziosForaDaMontagem`.
  - `CenarioMinimoTest.sinalComDestinoNoCenarioMinimo`: FOLHA em P1 → `destinosSugeriveis(FOLHA) == [P2]` e o sinal `FOLHA → P2` aceito.
  - `SalaApiTest`: `sinalSemLugarResponde422` (`{"peca":"lanterna"}` → 422 `LUGAR_INVALIDO`); `sinalComLugarDesconhecidoResponde422` (`"Z9"`); `pecaDesconhecidaNoSinalVemAntesDoLugar` (`{"peca":"foguete"}` → `PECA_INVALIDA`); `sinalComLugarInvalidoEmSalaEncerradaResponde422`; `sinalComDestinoApareceParaODono` (banco em C2, `{"peca":"banco","lugar":"C1"}` → Bruno vê `$.sinal.lugar == "C1"`, `$.sinal.deAssento == "A"`, `$.versao == 1`); `visaoDoParceiroTrazDestinosSugeriveis` (Bruno põe a lanterna em R2 → na visão de Ana, `$.pecas[4].destinosSugeriveis == ["L1","L2","R1"]`, `$.pecas[3].destinosSugeriveis == []`).
  - `ConcorrenciaTest.sinalEColocacaoNoDestinoSimultaneos`, repetido 300×: invariante, nunca fica sinal ativo com destino ocupado.
  - `cliente.test.ts` `sinalizarEnviaPecaELugar` (corpo `{"peca":"manta","lugar":"L2"}`); `sessao.test.ts` `sinalEnviaOPrimeiroDestinoSugerivel`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify`, `pnpm -C frontend test` e o e2e afetado em chromium (`JornadaDoisNavegadoresIT,AcessibilidadeLayoutIT`) verdes. Commit `feat(game): sinal com destino`.

### B1c: Pedido de ajuste com aceite do outro

**Skills:** `mattpocock-skills:tdd`, `mattpocock-skills:codebase-design`.

**Arquivos:**
- Modificar: `game/{Fase,Regras}.java`, `room/{Sala,SalaController,Visao}.java`; front mínimo `frontend/src/api/cliente.ts`, `frontend/src/dominio/visao.ts`, `frontend/src/sessao/sessao.ts`
- Teste: `game/{RegrasTest,CenarioMinimoTest}.java`, `room/{SalaApiTest,ConcorrenciaTest}.java`, `frontend/src/api/cliente.test.ts`, `frontend/src/sessao/sessao.test.ts`, `e2e/AcessibilidadeFocoIT.java`, `e2e/RevelacaoIT.java`
- Fora do Git: `.claude/handoff/contrato-api.md` do repositório principal

**Interfaces:**
- Produz:
  - `record Revelado(Optional<Assento> pedidoDeAjuste)` (com `Objects.requireNonNull`), sem campo novo no `Estado`.
  - `Regras.pedirAjuste(Cenario c, Estado e, Assento quem) -> Decisao`: `FASE_INVALIDA` fora de `Revelado`; sem pedido → grava o de `quem` (versão igual); pedido de `quem` → aceito sem mudança; pedido do outro → `new Fase.Montando()`, `versao + 1`, `prontos` vazio, `sinal` vazio, mesma composição.
  - `Regras.recusarAjuste(Cenario c, Estado e, Assento quem) -> Decisao`: limpa o pedido, venha de quem vier; sem pedido → aceito sem mudança; `FASE_INVALIDA` fora de `Revelado`. Repetições contam como ação e renovam a expiração (contrato-api.md:69). `Regras.ajustar` é removido.
  - `Sala.pedirAjuste(Assento)`, `Sala.recusarAjuste(Assento)`; `POST /api/sala/ajustar` e `POST /api/sala/ajustar/recusar` → 200 `Visao`.
  - `Visao.ajuste: VisaoAjuste(Assento deAssento) | null`, no fim.
  - Cliente: `api.pedirAjuste()`, `api.recusarAjuste()`; sai `api.ajustar`. `Visao` TS ganha `ajuste: { deAssento: Assento } | null`. Até a F5, o "Ajustar de novo" chama `pedirAjuste`.

- [ ] **Passo 1:** testes.
  - `RegrasTest`: `pedirAjusteGravaOPedidoSemMudarAFase` (fase `new Fase.Revelado(Optional.of(A))`, `versao` igual, `prontos == {A, B}`); `pedirDeNovoNaoMudaNada`; `aceiteDoOutroVoltaAMontagemComVersaoNova` (`Montando`, `versao + 1`, `prontos` vazio, mesma composição); `aceiteDoAjusteLimpaOSinal`; `recusarLimpaOPedido`; `cancelarOProprioPedidoLimpa`; `recusarSemPedidoNaoMudaNada`; `pedirAjusteForaDaRevelacaoERecusado` e `recusarAjusteForaDaRevelacaoERecusado` (`FASE_INVALIDA`).
  - `CenarioMinimoTest.ajusteCombinadoNoCenarioMinimo`.
  - `SalaApiTest`: `pedidoDeAjusteApareceNaVisaoDosDois` (Ana pede → `$.fase "REVELADO"`, `$.ajuste.deAssento "A"`, `$.versao 4`; Bruno vê o mesmo); `etagMudaComOPedidoDeAjuste` (ETags diferentes com `versao` igual); `pedidosCruzadosViramAceite` (os dois POST `/ajustar` → `MONTANDO`, `versao 5`, `$.ajuste` nulo); `recusarAjusteLimpaOPedido`; `ajustarDuranteAMontagemResponde409` (`FASE_INVALIDA`, com `visao`); `sairComPedidoPendenteEncerra` (Bruno sai → Ana recebe 410 `SAIU`; caracterização, verde já no RED).
  - `ConcorrenciaTest.pedidosDeAjusteSimultaneosTerminamEmMontagem`: `mesaRevelada`, os dois pedem → os dois aceitos, exatamente uma resposta com fase `"MONTANDO"`; no fim `versao 5` e `ajuste == null`.
  - `cliente.test.ts` `pedirERecusarAjusteUsamAsRotasNovas`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar; os dois ITs da tabela clicam "Ajustar de novo" nas duas páginas.
- [ ] **Passo 4:** `./mvnw -q verify`, `pnpm -C frontend test` e o e2e em chromium (`JornadaDoisNavegadoresIT,AcessibilidadeFocoIT,AcessibilidadeLayoutIT,RevelacaoIT,PollingIT`) verdes. Atualizar o `contrato-api.md` (rotas, precedência do sinal, campos novos, 204, "ETag muda também com `ajuste`"). Commit `feat(game): pedido de ajuste com aceite do outro`.

### F4a: Relações e som das notas (dados do cenário)

**Skills:** `mattpocock-skills:tdd`, `design-skills:ux-writing` (frases de relação); revisão `design-skills:accessibility-audit` + `design-skills:design-critique`. (`frontend-design` e `interaction-design` não se aplicam: sem desenho.)

**Arquivos:**
- Modificar: `cenario/{tipos,ler}.ts`, `cena/som.ts`, `cenario.json` (`raioPerto`, `relacoes`)
- Criar: `cenario/relacoes.ts`
- Teste: `cenario/{ler,relacoes}.test.ts` (novo `relacoes.test.ts`), `cena/som.test.ts`

**Interfaces:**
- Produz:
  - Esquema (só em `ler.ts`): `raioPerto: v.optional(v.pipe(v.number(), v.minValue(0)))`; `relacoes: v.optional(v.array(v.strictObject({ pecas: v.tuple([v.string(), v.string()]), perto: v.string() })), [])`. O `conferir` recusa peça inexistente, par com peças iguais e `relacoes` não vazia sem `raioPerto`. O `CENARIO_MINIMO` continua válido.
  - `interface PecaNoLugar { readonly peca: string; readonly lugar: string }`.
  - `CenarioFront.frasesDeRelacao(colocadas: readonly PecaNoLugar[]): readonly string[]`, implementado por `frasesDeRelacao(relacoes: readonly Relacao[], raioPerto: number, g: Geometria, colocadas: readonly PecaNoLugar[]): string[]` em `relacoes.ts`. Pares sem ordem; distância entre lugares em metros; ≤ `raioPerto`; até 2, na ordem do JSON.
  - `CenarioFront.audio(nome: string): string` → `/assets/cenarios/<id>/audio/<nome>.mp3` (mesma regra do `som()`).
  - `Som.notas(url: string, volume: number): void` (ignora o `ligado`, cria ou retoma o contexto, falha em silêncio) e `Som.desbloquear(): void` (só cria e retoma o contexto, sem baixar nada).
  - `cenario.json`: `raioPerto: 2.0` e `relacoes` com estas frases (pares e textos fixados):
    - manta–banco "A manta ficou ao lado do banco, pronta para dividir."; rádio–banco "O rádio ficou perto do banco, tocando baixinho.";
    - lanterna–telescópio "A lanterna ilumina o telescópio de perto."; garrafa–telescópio "A garrafa térmica ficou ao alcance de quem observa.";
    - manta–telescópio "A manta ficou junto do telescópio, para as horas de espera."; rádio–telescópio "O rádio acompanha o telescópio com uma trilha baixa.";
    - lanterna–banco "A lanterna clareia o banco."; garrafa–banco "A garrafa térmica ficou do lado de quem senta.";
    - manta–lanterna "Manta e lanterna ficaram juntas: um canto quente e claro."; manta–garrafa "Manta e garrafa térmica lado a lado: ninguém passa frio.";
    - rádio–lanterna "Rádio e lanterna juntos fazem um canto de festa."; rádio–garrafa "Rádio e garrafa térmica lado a lado: música e algo quente.".

- [ ] **Passo 1:** testes (`cenarioDoTerraco()`; distâncias reais: C1–L2 1,24 m, C1–R2 3,23 m, C1–L1 1,77 m, L1–L2 1,17 m, C2–R2 1,74 m).
  - `relacoes.test.ts`: `telescopioEmC1ELanternaEmL2SaoPerto` (contém a frase lanterna–telescópio); `lanternaDoOutroLadoNaoEPerto` (C1 e R2 → não contém); `noMaximoDuasFrases` (telescópio C1, banco C2, manta L1, lanterna L2 → exatamente `["A lanterna ilumina o telescópio de perto.", "A manta ficou junto do telescópio, para as horas de espera."]`); `parSemOrdemCasa`.
  - `ler.test.ts`: `relacaoComPecaInexistenteERecusada`, `relacaoComPecasIguaisERecusada`, `relacoesSemRaioPertoRecusadas`, `cenarioSemRelacoesContinuaValido`, `audioUsaONomeBase` (`audio("radio-notas") == "/assets/cenarios/terraco/audio/radio-notas.mp3"`).
  - `som.test.ts`: `notasTocamComSomDesligado` (um `start()` com `ligado` falso), `notasFalhamEmSilencio` (fetch rejeitado → não lança), `desbloquearNaoBaixaNada`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `pnpm -C frontend lint`, `./mvnw -q verify` (o `CenarioJsonTest` lê o JSON) e `TamanhoDoJsInicialTest` verdes. Commit `feat(cenario): relações de posição e som de notas como dados do cenário`.

### F6a: Textos e defeitos sem sessão

**Skills:** `design-skills:ux-writing`, `frontend-design:frontend-design` (anel de foco); revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `dominio/artigos.ts` (+ `artigos.test.ts`), `dispositivo.ts` (+ `dispositivo.test.ts`)
- Modificar: `componentes/{Inventario,Falta,Convite,estilos}.tsx/.ts` (ou seja, `Inventario.tsx`, `Falta.tsx`, `Convite.tsx`, `estilos.ts`)
- Teste: `componentes/{Inventario,Falta,Convite}.test.tsx`
- Plano B (só se o anel cair no Firefox/WebKit): criar `modalidade.ts` e uma linha de import em `main.tsx`

**Interfaces:**
- Produz:
  - `porArtigo(artigo: string): string` em `dominio/artigos.ts`: "o "→"pelo ", "a "→"pela ", "os "→"pelos ", "as "→"pelas "; outro → `"por " + artigo`. O inventário diz `Troca <artigo> <porArtigo(artigo)>`.
  - `ehToque(): boolean` em `dispositivo.ts` = `matchMedia("(hover: none) and (pointer: coarse)").matches`, falso sem `matchMedia`.
  - `Convite` recebe `ehToque` como prop com padrão `ehToque` do dispositivo (o `Painel` não muda). Com toque: dica "O convite vale para uma entrada." e aviso de cópia "Convite copiado.". Fora do toque, como hoje.
  - `Falta` fica `hidden` quando as quatro linhas estão feitas.
  - `TITULO_DE_RESULTADO` (`estilos.ts`): sem contorno em `:focus:not(:focus-visible)`; com `:focus-visible`, o anel de hoje. Plano B: `data-modalidade="teclado"` no `<html>`, ligado no `keydown` e desligado no `pointerdown`.

- [ ] **Passo 0:** rodar `AcessibilidadeLayoutIT.tituloDoEncerradoFocadoPeloTecladoMantemOAnelDeFoco` nos 3 navegadores com a mudança de `estilos.ts`. Se cair em algum, aplicar o plano B.
- [ ] **Passo 1:** testes. `porArtigo` (4 casos + fallback); `trocaDizPelo` ("Troca a manta pelo rádio", "Troca a lanterna pela garrafa térmica"); `faltaSomeQuandoNadaFalta` (`toHaveAttribute("hidden")`); `conviteNoToque` (os dois textos); `conviteForaDoToqueComoHoje`; `ehToqueFalsoSemMatchMedia`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` e o `AcessibilidadeLayoutIT` nos 3 navegadores verdes. Commit `fix(encontro): "pelo" na troca, o que falta some e convite sem dica de janela no toque`.

### B8a: Cenário pode declarar uma abertura

**Skills:** `mattpocock-skills:tdd`.

**Arquivos:**
- Criar: `game/DefinicaoDeAbertura.java`
- Modificar: `game/Cenario.java`, `room/CenariosConfig.java`
- Teste: `game/CenarioTest.java`

**Interfaces:**
- Produz:
  - `record DefinicaoDeAbertura(String tipo, Duration janela, Duration maximo)`; o construtor lança IAE citando o valor: `tipo` fora de `^[a-z0-9-]+$`, `janela` não positiva, `maximo` negativo. `maximo == Duration.ZERO` = conclui apagada na entrada (usado pela suíte e2e).
  - `Cenario(…, Desfecho desfecho, Optional<DefinicaoDeAbertura> abertura)`: componente novo **no fim**, mais um construtor de 8 argumentos que delega com `Optional.empty()` (nenhum `new Cenario(...)` existente muda).
  - `Cenario comMaximoDeAbertura(Duration maximo)`: troca só o `maximo`; sem abertura, devolve `this`.
  - `CenariosConfig.terraco(...)` lê a propriedade opcional `duora.cenarios.abertura.maximo` (`Duration`) e aplica `comMaximoDeAbertura`. O terraço **ainda não** declara abertura.

- [ ] **Passo 1:** testes `cenarioSemAberturaPorPadrao`, `tipoDeAberturaForaDoPadraoERecusado`, `janelaNaoPositivaERecusada`, `maximoNegativoERecusado` (IAE com o valor na mensagem), `comMaximoTrocaSoOMaximo`, `comMaximoSemAberturaDevolveOMesmo` (`isSameAs`).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify`. Commit `feat(game): cenário pode declarar uma abertura`.

### B8b: Abertura no motor

**Skills:** `mattpocock-skills:tdd`, `mattpocock-skills:codebase-design`.

**Arquivos:**
- Criar: `game/Abertura.java`, `game/AberturaTest.java`
- Modificar: `game/{Fase,Regras,Recusa}.java`, `room/Sala.java` (`entrar` passa o instante; `detalhe`), `room/Codigo.java`
- Teste: `game/CenarioMinimo.java` (+ `COM_ABERTURA`), `game/{RegrasTest,ParametrosDoCenarioTest,CenarioMinimoTest,TerracoTest}.java` (mecânica de `parceiroEntrou`)

**Interfaces:**
- Consome: `DefinicaoDeAbertura`, `Cenario.abertura()` (B8a).
- Produz:
  - `record Montando(Optional<Abertura> abertura)`. Vazia = cenário sem abertura ou montagem depois de um ajuste (a abertura acontece uma vez por encontro). A abertura concluída fica presente, com `concluidaEm`.
  - `record Abertura(Instant iniciadaEm, Map<Assento, Instant> toques, Optional<Instant> concluidaEm, boolean acesa)` com `boolean ativa()` = `concluidaEm.isEmpty()`; `toques` com `Map.copyOf` (último toque por assento).
  - `Regras.parceiroEntrou(Cenario c, Estado e, Instant agora)`: com abertura declarada, `Montando(Optional.of(new Abertura(agora, Map.of(), Optional.empty(), false)))`.
  - `Regras.tocarAbertura(Cenario c, Estado e, Assento quem, Instant agora) -> Decisao`: `FASE_INVALIDA` em `AguardandoPar`, `Revelado` ou `Montando` sem abertura; concluída → aceita sem mudança; registra o toque e, se o outro tocou há no máximo `janela` (inclusivo: `!toqueDoOutro.plus(janela).isBefore(agora)`), conclui com `acesa = true`. Nunca muda `versao`.
  - `Regras.avancarAbertura(Cenario c, Estado e, Instant agora) -> Estado`: pura e total; conclui com `acesa = false` quando `!agora.isBefore(iniciadaEm.plus(maximo))`; sem mudança devolve a mesma instância.
  - `Regras.podeConfirmar(Cenario c, Estado e) -> boolean` (sai de `Visao`): `Montando` sem abertura ativa e composição completa.
  - Durante a abertura ativa, `colocar`, `retirar`, `sinalizar` e `confirmar` recusam `ABERTURA_EM_ANDAMENTO`; `lugaresPermitidos` e `destinosSugeriveis` vazios. Precedência em `colocar`: `PECA_ALHEIA` → `LUGAR_INVALIDO` → idempotente (lugar atual) → `ABERTURA_EM_ANDAMENTO` → `FASE_INVALIDA`; em `confirmar`: idempotente → `ABERTURA_EM_ANDAMENTO` → `FASE_INVALIDA`.
  - `Recusa.ABERTURA_EM_ANDAMENTO`; `Codigo.ABERTURA_EM_ANDAMENTO(CONFLICT, "Abertura em andamento")`; `Sala.detalhe` → "Espere as lanternas acenderem.".
  - `CenarioMinimo.COM_ABERTURA`: id `minimo-abertura`, `new DefinicaoDeAbertura("acender-junto", 5 s, 90 s)`.

- [ ] **Passo 1:** testes em `AberturaTest` (T0 = entrada de B, `COM_ABERTURA`):
  - `osDoisNaJanelaAcendem` (A em 0 s, B em 4,999 s → `acesa`, `concluidaEm == T0+4,999 s`); `noLimiteDaJanelaAcende` (5,000 s); `foraDaJanelaNaoAcende` (0 e 5,001 s → `ativa()`); `novoToqueDoPrimeiroDentroDaJanelaAcende` (A 0, B 5,001, A 6,0 → acesa); `doisToquesDoMesmoNaoAcendem`.
  - `aberturaTerminaSozinhaEm90s` (89,999 s → ativa; 90 s → concluída, `acesa == false`); `toqueDepoisDeConcluidaNaoMudaNada`; `tocarForaDaMontagemERecusado` (`FASE_INVALIDA`); `tocarNaoMudaVersao`.
  - `pecaDuranteAberturaRecusada`, `sinalDuranteAberturaRecusado`, `prontoDuranteAberturaRecusado` (`ABERTURA_EM_ANDAMENTO`); `lugaresPermitidosEDestinosVaziosDuranteAbertura`; `naoPodeConfirmarDuranteAbertura`.
  - `colocarPreservaAAberturaConcluida`; `ajusteVoltaAMontagemSemAbertura`; `maximoZeroConcluiNaPrimeiraAvancada`; `cenarioSemAberturaComecaMontando` (`CenarioMinimo.CENARIO` → `new Fase.Montando(Optional.empty())`); `nomeDaFaseNaRedeNaoMuda` (`"MONTANDO"`).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify`. Commit `feat(game): abertura no motor`.

### B8c: Abertura na API

**Skills:** `mattpocock-skills:tdd`, `mattpocock-skills:codebase-design`.

**Arquivos:**
- Modificar: `room/{Sala,Retrato,SalaController,Visao}.java`
- Teste: `room/{CenarioApiTest,SalaApiTest}.java`
- Fora do Git: `.claude/handoff/contrato-api.md`

**Interfaces:**
- Consome: `tocarAbertura`, `avancarAbertura`, `podeConfirmar` (B8b).
- Produz:
  - A `Sala` aplica `avancarAbertura`, sob o lock, no início de `consultar` e antes **e** depois da regra em `agir` e `entrar` (com `maximo` zero, a abertura já conclui na resposta da entrada). Publica com `Retrato.comEstado(Estado)`, que **não** mexe em `ultimaAcao` (avançar não é ação).
  - `Sala.tocarAbertura(Assento)`; `POST /api/sala/abertura` (sem corpo) → 200 `Visao`; recusas de regra respondem 409 com a `visao`.
  - `Visao.abertura: VisaoAbertura(boolean ativa, boolean voceTocou, boolean outroTocou, boolean acesa) | null`, no fim. `voceTocou`/`outroTocou` = toque há no máximo `janela`, com o `agora` da visão (como `sinalAtivoEm`). `Confirmacao.podeConfirmar` usa `Regras.podeConfirmar`.

- [ ] **Passo 1:** testes HTTP (`CenarioApiTest`, sala `minimo-abertura`, `RelogioDeTeste`): `aberturaPorHttpAcende` (os dois POST → `$.abertura.acesa true`, `$.abertura.ativa false`); `colocarDuranteAberturaResponde409ComVisao` (`ABERTURA_EM_ANDAMENTO`, `$.visao.abertura.ativa true`); `visaoDaAberturaPorLado` (Ana tocou → Ana `voceTocou true`, Bruno `outroTocou true`); `aberturaConcluiPeloTempoNaConsulta` (+90 s → GET com `ativa false`, `acesa false`, ETag novo); `colocarDepoisDoMaximoSemConsultaEAceito`; `avancarAberturaNaoRenovaExpiracao` (expiração da sala calculada da última ação, não do GET); `SalaApiTest` com as linhas novas da tabela.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify`. Atualizar o `contrato-api.md` (`POST /api/sala/abertura`, `Visao.abertura`, `ABERTURA_EM_ANDAMENTO` 409). Commit `feat(room): abertura na API`.

### F0: Costuras do front (refatoração, sem comportamento)

**Skills:** `mattpocock-skills:codebase-design`, `frontend-design:frontend-design` (ordem das camadas da cena); revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `cena/efeitos.ts` (+ `efeitos.test.ts`), `usePassagemAoVivo.ts`, `estilos/destaque.css` e `estilos/revelacao.css` (vazios, importados)
- Modificar: `cena/cena.ts`, `componentes/{Cena,Painel,Encerrado,Inventario,Resultado,Acoes}.tsx`, `App.tsx`, `useSessao.ts`, `sessao/{tipos,sessao}.ts`, `dominio/mensagens.ts`, `estilos/{app,cena}.css`
- Teste: `cena/cena.test.ts`, `componentes/Cena.test.tsx`, `dominio/mensagens.test.ts` (mecânicas da tabela)

**Interfaces:**
- Produz:
```ts
// cena/efeitos.ts — fábrica pura; o cena.ts põe "piso" antes do botão da peça e "frente" depois
export type CamadaDoEfeito = "piso" | "frente";
export interface ElementoDeEfeito { readonly camada: CamadaDoEfeito; readonly el: HTMLElement }
export function criarElementosDoEfeito(efeito: Efeito, peca: string, ponto: Ponto, g: Geometria): readonly ElementoDeEfeito[];
// cena/cena.ts (Introduce Parameter Object); PropsCena.selecionada vira marcas
export interface MarcasDaCena { readonly selecionada: string | null; readonly sugerindo: string | null; readonly destaques: readonly string[] }
desenhar(visao: Visao, marcas: MarcasDaCena): void;
// useSessao.ts
export function marcasDaCena(estado: EstadoDaSessao): MarcasDaCena;
// usePassagemAoVivo.ts — extraído da regra de App.tsx:80-89; serve ao acorde e às notas
export function usePassagemAoVivo(estado: EstadoDaSessao): boolean;
// sessao/tipos.ts
| { tela: "entrada"; comConvite: boolean; trocaDeSala: boolean; erroApelido: string }
| { tela: "montagem"; visao: Visao; selecionada: string | null; sugerindo: string | null; destaques: readonly string[]; instrucao: string }
| { tela: "encerrado"; titulo: string; texto: string }
// sessao/sessao.ts — Painel e folhas recebem um objeto; ação nova não toca App nem Painel
export type AcoesDaSessao = Pick<Sessao, "escolherPeca" | "retirar" | "confirmar" | "ajustar" | "criarOutro" | "enviarApelido" | "limparErroApelido">;
// dominio/mensagens.ts
export function textoEncerrado(motivo: string, nomeParceiro?: string | null): { titulo: string; texto: string }; // titulo "Terraço encerrado" por ora
```
- O piso leva `class="brilho-piso"` e `data-efeito="brilho-piso"`; o halo, `data-efeito="halo"`; os dois com `data-peca`. A classe `.brilho` é renomeada em `cena.css` (o tipo novo `brilho` chega na F4b).
- Valores padrão: `sugerindo: null`, `destaques: []`, `trocaDeSala: false`.

- [ ] **Passo 1:** `efeitos.test.ts`: `brilhoPisoVaiParaOPisoComDataEfeito`, `haloVaiParaAFrente`; `useSessao.test.tsx` `marcasDaCenaForaDaMontagemSaoVazias`.
- [ ] **Passo 2:** refatorar em passos pequenos (Extract Function, Introduce Parameter Object, Move Function), com a suíte Vitest a cada passo.
- [ ] **Passo 3:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` (linha de base no commit) e o e2e em chromium (`RevelacaoIT,AcessibilidadeLayoutIT,JornadaDoisNavegadoresIT`) verdes. Commit `refactor(front): costuras da cena, da sessão e dos efeitos`.

### F0e: Costuras do e2e (refatoração)

**Skills:** `mattpocock-skills:codebase-design`; revisão `design-skills:accessibility-audit`.

**Arquivos:**
- Criar: `e2e/Axe.java` (`assertSemViolacoes` extraído de `AxeIT.java:99`), `e2e/RelogioAdiantavel.java` (extraído de `AcessibilidadeFocoIT`)
- Modificar: `e2e/AxeIT.java`, `e2e/AcessibilidadeFocoIT.java`, `e2e/TerracoPagina.java` (`Peca.RADIO`, `Peca.GARRAFA`, `Lugar.L1`)

**Interfaces:**
- Produz: `static void Axe.assertSemViolacoes(Page pagina, String estado)`; `public final class RelogioAdiantavel extends Clock` com `void adiantar(Duration d)`, registrado nos ITs por `@TestConfiguration` + `@Bean @Primary`.

- [ ] **Passo 1:** extrair (Extract Class / Move Function), sem mudar asserção.
- [ ] **Passo 2:** `AxeIT` e `AcessibilidadeFocoIT` em chromium verdes. Commit `refactor(e2e): axe e relógio adiantável compartilhados`.

### F2: Ver o que o outro fez (destaque na peça e status abaixo dos nomes)

**Skills:** `frontend-design:frontend-design`, `design-skills:interaction-design` (anel, selo, faixa sticky); `design-skills:ux-writing`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `sessao/destaques.ts` (+ `destaques.test.ts`), `e2e/VerOQueOOutroFezIT.java`
- Modificar: `sessao/sessao.ts`, `cena/cena.ts`, `componentes/Painel.tsx`, `estilos/destaque.css`
- Teste: `sessao/sessao.test.ts`, `componentes/Cena.test.tsx`

**Interfaces:**
- Consome: `MarcasDaCena`, `destaques` (F0).
- Produz:
```ts
export const DURACAO_DESTAQUE_MS = 2400;
export function pecasQueMudaram(anterior: Visao, visao: Visao): string[]; // do parceiro, colocadas ou movidas; tiradas não contam
export type Agendar = (acao: () => void, ms: number) => () => void;     // OpcoesDaSessao.agendar?: Agendar (padrão setTimeout)
```
- Os `destaques` são calculados em **todo** `aplicarVisao` com `anterior` (polling ou resposta da própria ação). A mesma peça de novo reinicia o tempo; `limparSessao` cancela todos os timers.
- Cena, por peça em destaque: `data-destaque="true"`; selo visível `span.selo.selo-mudou` "Mudou" (`aria-hidden`, sem sobrepor o `.selo-sinal`); sufixo `. Mudou agora` no fim do nome acessível. Animação: anel pulsando 2× em 1,2 s; o anel estático vale enquanto `data-destaque` existir.
- Painel: `#aviso` logo depois de `#par`. Só no `celular-alto` (≤ 860 px de largura e ≥ 700 px de altura), o bloco `#par` + `#aviso` fica sticky em `top: calc(var(--altura-topo) + var(--altura-cena))`, com fundo opaco; `--altura-cena: calc(96px + 112vw * 1186 / 1800 + var(--e-3))`; o `html` ganha `scroll-padding-top` de topo + cena + 2 linhas do bloco. Fora do celular alto, basta a ordem no DOM.

- [ ] **Passo 1:** testes.
  - `destaques.test.ts`: `colocadaEMovidaContam`, `tiradaNaoConta`, `propriaNaoConta`.
  - `sessao.test.ts`: `destaqueSaiDepoisDoTempo` (agendar falso; 2400 ms → `destaques == []`); `destaqueTambemNaRespostaDaPropriaAcao`; `mesmaPecaDeNovoReiniciaOTempo`; `limparSessaoCancelaOsTimers`.
  - `Cena.test.tsx` `pecaEmDestaqueTemSeloENome` (nome termina em ". Mudou agora"; selo com texto "Mudou").
  - `VerOQueOOutroFezIT`: `pecaQueOParceiroColocouGanhaODestaque` (`page.clock()` instalado antes de navegar; Bruno põe o banco em C2 → Ana vê botão com nome casando `^Banco de Bruno, centro, à direita\..*Mudou agora$`; `runFor(2500)` → sem "Mudou agora"); `@ParameterizedTest statusVisivelSemRolar` (1440×835 e 1440×900: caixa do status inteira na janela); `statusVisivelNoCelularRolado` (390×844, Ana rola ao fim; Bruno coloca → caixa na janela e contém "Bruno colocou o banco"); `focoNoUltimoItemNaoFicaCobertoNoCelular` (390×844, Tab até o último item → a caixa dele não intercepta topo + cena + bloco).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` e o e2e em chromium (`VerOQueOOutroFezIT,AcessibilidadeLayoutIT,LayoutCelularIT`) verdes. Commit `feat(encontro): destaque na peça que o outro mudou e status logo abaixo dos nomes`.

### F4b: Efeitos por peça na noite

**Skills:** `frontend-design:frontend-design`, `design-skills:interaction-design` (os 5 desenhos e as versões paradas); revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Modificar: `cenario/{tipos,ler}.ts` (variantes de `Efeito`), `cena/efeitos.ts`, `estilos/revelacao.css`, `cenario.json` (`revelacao`)
- Criar: `e2e/RevelacaoEfeitosIT.java`
- Teste: `cena/efeitos.test.ts`, `cenario/ler.test.ts`, `game/CenarioJsonTest.java` (`somDasNotasTemArquivo`)

**Interfaces:**
- Consome: `criarElementosDoEfeito` (F0), `radio-notas.mp3` (A4a).
- Produz:
```ts
export type Efeito =
  | { readonly tipo: "brilho-piso"; readonly largura: number }
  | { readonly tipo: "halo"; readonly alturaProjetada: number; readonly largura: number }
  | { readonly tipo: "meteoros" }
  | { readonly tipo: "brilho"; readonly cor: string; readonly largura: number } // cor ^#[0-9a-f]{6}$i, aplicada por style.setProperty("--cor-efeito", cor)
  | { readonly tipo: "vapor" }
  | { readonly tipo: "tremular" }
  | { readonly tipo: "notas"; readonly som: string; readonly volume: number }; // som = nome-base ^[a-z0-9-]+$
```
- `criarElementosDoEfeito`, `switch` exaustivo: `brilho-piso`, `brilho` → piso; `halo`, `vapor`, `tremular` → frente; `meteoros` e `notas` → `[]` (a F4c os liga). Todos `aria-hidden`, com `data-efeito` e `data-peca`, visíveis só à noite por CSS, com versão parada em `prefers-reduced-motion`.
- `cenario.json` `revelacao`: telescópio `meteoros`; banco `brilho` (`#f2bd70`, 1,6 m); manta `brilho` (vinho, cor escolhida no visual, 1,2 m); rádio `notas` (`"radio-notas"`, volume 0,4); lanterna mantém `brilho-piso` e `halo` e ganha `tremular`; garrafa `vapor`.

- [ ] **Passo 1:** testes. `efeitos.test.ts`: `cadaTipoCriaSeuElemento` (os 5 que criam: `brilho-piso`, `halo`, `brilho`, `vapor`, `tremular`, cada um com seu `data-efeito` e camada); `meteorosENotasNaoCriamElemento`; `brilhoUsaACorPorCssom`. `ler.test.ts`: `corForaDoFormatoRecusada`, `somDeNotasComBarraRecusado` (`"../x"`), `somDeNotasComExtensaoRecusado` (`"radio-notas.mp3"`). `CenarioJsonTest.somDasNotasTemArquivo` (todo `notas.som` tem `audio/<som>.mp3`). `RevelacaoEfeitosIT.cadaPecaColocadaTemSeuEfeitoNaNoite` (telescópio, banco, manta, lanterna → `[data-efeito=brilho]` 2, `[data-efeito=tremular]` 1, `[data-efeito=brilho-piso]` 1, `[data-efeito=halo]` 1).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `./mvnw -q verify`, `TamanhoDoJsInicialTest` (plano de lazy se > 89 KB) e o `RevelacaoEfeitosIT` em chromium verdes. Commit `feat(encontro): cada peça com seu efeito na noite`.

### D1: Emendas de documentação

**Skills:** `design-skills:ux-writing`.

**Arquivos (só docs):** `docs/superpowers/specs/2026-11-21-duora-social-design.md`, `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`, `docs/adr/0003-motor-de-cenarios.md`, `docs/superpowers/plans/2026-11-22-etapa-5-encontro.md`, `docs/superpowers/plans/2026-11-22-etapa-9b-cenarios.md`, `.claude/skills/duora-assets/SKILL.md`, `CONTEXT.md`

- [ ] **Passo 1:** spec:156, nota "na 1c, tela neutra 'Você saiu do terraço'; a decisão privada chega na Etapa 5" (R7); spec:267, "a 9b começa depois da 1c".
- [ ] **Passo 2:** contratos:82 e ADR 0003:15, componente `abertura` (`Optional<DefinicaoDeAbertura>`, último, com construtor secundário); contratos:89, métodos novos do `CenarioFront` (`frasesDeRelacao`, `audio`); contratos:91, o `CenarioJsonTest` espelha `abertura` (presença e `tipo`) e confere o arquivo de `notas.som`.
- [ ] **Passo 3:** etapa-5:85, rotas `/ajustar/recusar` e `/abertura`; sem encontro ativo, `GET /api/encontro` responde 204 (o `PrimeiraCargaIT` não aceita resposta ≥ 400 na primeira carga); a suíte e2e herda `abertura.maximo=PT0S` do `pom.xml`, e só o `AberturaIT` liga a abertura.
- [ ] **Passo 4:** 9b:327, o playtest cria o segundo lado pelo passo 0 de harness da P7 (com fallback para perfil persistente); 9b:1069, ordem dos componentes `…, abertura, atividade`.
- [ ] **Passo 5:** `SKILL.md:86`, a dica de "copiar para o `sw.js`" sai (o `CACHE_VERSION` vem do build; rodar o `ServiceWorkerVersaoTest`). `CONTEXT.md`: termos **Abertura** e **Pedido de ajuste** (o pedido de ajuste não é o Sinal).
- [ ] **Passo 6:** commit `docs: emendas da Etapa 1c (Sair neutro, abertura, rotas e skill de assets)`.

### F3: Sugestão com destino ("Que tal aqui?")

**Skills:** `frontend-design:frontend-design`, `design-skills:interaction-design` (modo de sugestão, marcador do destino); `design-skills:ux-writing`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `e2e/SinalComDestinoIT.java`
- Modificar: `sessao/{sessao,novidade,tipos}.ts`, `cena/cena.ts`, `estilos/cena.css`, `componentes/Inventario.tsx`, `e2e/VisualIT.java` (estado `sinal` em dois passos, destino R1)
- Teste: `sessao/{sessao,novidade}.test.ts`, `componentes/{Cena,Inventario}.test.tsx`, `e2e/AcessibilidadeLayoutIT.java` (o teste da tabela)

**Interfaces:**
- Consome: `destinosSugeriveis`, `sinal.lugar`, `api.sinalizar(peca, lugar)` (B1b); `sugerindo`, `AcoesDaSessao` (F0).
- Produz:
  - `escolherPeca(p, false)` em `MONTANDO` liga ou desliga a sugestão; `escolherPeca(p, true)` e `cancelarSelecao()` (Esc) a limpam; invariante: no máximo um entre `selecionada` e `sugerindo` não nulo. `escolherLugar` durante a sugestão chama `api.sinalizar(sugerindo, lugar)`.
  - `moverPara(peca: string, lugar: string): Promise<void>` em `Sessao` e `AcoesDaSessao`.
  - `PedidoDeFoco` ganha `{ alvo: "peca-na-cena"; peca: string }`: depois de sugerir, o foco volta à peça na cena.
  - Cena em sugestão: um `button.lugar[data-sugestao="true"]` por destino, rótulo visível "Que tal aqui?" (também no celular) e nome `Que tal aqui? Sugerir <artigo>: <lugar>`; nenhum lugar de colocação própria ao mesmo tempo. Peça do parceiro sem destinos: `aria-disabled="true"` e sem o complemento "Ative para sugerir que mova".
  - Para quem recebe: `div.lugar-sugerido[aria-hidden="true"][data-lugar]` no destino (com contorno, não só cor) e o selo "Sugerido mover" de hoje. No inventário, o item ganha o botão `Mover <artigo>: <lugar>`, que chama `moverPara`.
  - Textos: aviso de quem sugere `Você sugeriu que <nome> mova <artigo>: <lugar>.`; novidade `<nome> sugeriu mover <artigo>: <lugar>.`; instrução `Escolha onde <artigo> de <nome> ficaria melhor. Esc cancela.` (a F6b tira o " Esc cancela." no toque).

- [ ] **Passo 1:** testes.
  - Vitest: `sugerirChamaSinalizarComPecaELugar`; `escCancelaASugestao`; `selecionarPropriaCancelaSugestao`; `semDestinoAPecaFicaIndisponivel`; `focoVoltaAPecaDepoisDeSugerir`; `novidadeDoSinalCitaODestino` ("Ana sugeriu mover a lanterna: fundo, à direita."); `itemSinalizadoTemBotaoMover` (nome "Mover a lanterna: fundo, à direita"); `aceiteDepoisQueODestinoFoiOcupadoMostraAviso` (409 `LUGAR_OCUPADO` → "Ana acabou de usar este lugar. Escolha outro.", sem o botão).
  - `SinalComDestinoIT` (com `RelogioAdiantavel`): `sugestaoAceitaMoveAPecaParaODestino` (Ana toca a lanterna de Bruno em R2 → "Que tal aqui? Sugerir a lanterna: fundo, à direita" → toca; Bruno vê "Ana sugeriu mover a lanterna: fundo, à direita." → toca "Mover a lanterna: fundo, à direita" → os dois veem "Lanterna de Bruno, fundo, à direita"); `lugarOcupadoNaoApareceComoSugestao` (manta de Ana em L2 → L2 não é oferecido); `sugestaoIgnoradaExpira` (`relogio.adiantar(Duration.ofSeconds(21))` → o botão "Mover …" some para Bruno no próximo polling); `sugerindoSemViolacoes` (`Axe`).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` e o e2e em chromium (`SinalComDestinoIT,AcessibilidadeLayoutIT`) verdes. Commit `feat(encontro): sugestão com destino que o outro aceita ou ignora`.

### F4c: Revelação integrada (notas, meteoros, frases e tela cheia)

**Skills:** `frontend-design:frontend-design`, `design-skills:interaction-design` (tela cheia, "Ver o painel"/"Ver o céu"); `design-skills:ux-writing`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `e2e/RevelacaoCelularIT.java`
- Modificar: `componentes/{Resultado,Moldura,Cena}.tsx`, `App.tsx`, `dominio/mensagens.ts` (`textoResultado`), `estilos/revelacao.css`, `e2e/RevelacaoEfeitosIT.java` (testes novos)
- Teste: `dominio/mensagens.test.ts`

**Interfaces:**
- Consome: `frasesDeRelacao`, `audio`, `Som.notas`, `Som.desbloquear` (F4a); `Efeito` (F4b); `usePassagemAoVivo` (F0).
- Produz:
  - `textoResultado(c, visao)`: `texto` = legenda da 1b seguida das frases de relação, separadas por espaço.
  - `Cena.tsx`: chama `meteoros.mostrar(lado)` só se alguma peça colocada declara `meteoros`.
  - `App`: na passagem ao vivo (`usePassagemAoVivo`, lido de ref para o StrictMode não tocar duas vezes), cada peça colocada com `notas` toca `som.notas(c.audio(som), volume)` uma vez. `som.desbloquear()` no primeiro `pointerdown`/`keydown` do documento.
  - Tela cheia (R6), só no `celular-alto`: `vendoPainel` local no `App`, que volta a `false` a cada passagem para a noite; `Moldura` recebe `telaCheia: boolean` → `data-tela-cheia`. A `.cena` fica fixa em `inset: 0`, com o palco em 100% da largura ancorado embaixo, acima do `#resultado` (`max-height: 40dvh`, rolável). O resto do painel e o `Topo` ficam `visibility:hidden`, **menos** o `#resultado` e o `#aviso`. `Resultado` recebe `telaCheia`, `aoVerPainel`, `aoVerCeu`: botões `#ver-painel` "Ver o painel" e `#ver-ceu` "Ver o céu", que somem fora do celular alto. O foco da revelação continua no `#resultado-titulo`.

- [ ] **Passo 1:** testes.
  - `mensagens.test.ts` `textoDoResultadoTrazAsFrases`.
  - `RevelacaoEfeitosIT` (sonda de `AudioContext` do `RevelacaoIT`): `radioTocaNotasComSomDesligado` (rádio colocado, `#som` com `aria-pressed="false"` → ≥ 1 `createBufferSource` iniciado na revelação); `recarregarNaNoiteNaoTocaNotas` (recarregar em `REVELADO` → nenhum buffer iniciado); `textoDoResultadoMudaComAPosicao` (telescópio C1 e lanterna em L2, depois em R2 → `#resultado-texto` diferente); `meteorosSoComTelescopioColocado`.
  - `RevelacaoCelularIT` (390×844, `novoCelular()`): `revelacaoOcupaATela` (`.cena` com topo 0 e altura ≥ 0,9 × 844; heading do resultado visível); `verOPainelEVoltar` ("Ver o painel" → "Som" visível; "Ver o céu" → a cena volta a ocupar a tela); `avisoContinuaAnunciandoEmTelaCheia` (`#aviso` sem `visibility:hidden` e com o texto novo); `sairAlcancavelDepoisDeVerOPainel`; `semTelaCheiaComZoom` (640×360 → sem `data-tela-cheia="true"`); `telaCheiaSemViolacoes` (`Axe`).
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` (plano de lazy se > 89 KB) e o e2e em chromium (`RevelacaoEfeitosIT,RevelacaoCelularIT,RevelacaoIT`) verdes. Commit `feat(encontro): notas do rádio, texto pelas posições e tela cheia no celular`.

### F5: Concordância para ajustar e saída gentil

**Skills:** `design-skills:interaction-design` (estados do pedido); `design-skills:ux-writing`; `frontend-design:frontend-design`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `componentes/Resultado.test.tsx`, `e2e/AjusteCombinadoIT.java`
- Modificar: `sessao/{sessao,novidade,tipos}.ts`, `dominio/mensagens.ts`, `componentes/{Resultado,Encerrado}.tsx`, `e2e/{JornadaDoisNavegadoresIT,AcessibilidadeFocoIT,RevelacaoIT,TerracoPagina}.java`
- Teste: `sessao/{sessao,novidade}.test.ts`, `dominio/mensagens.test.ts`, `componentes/Painel.test.tsx`, `useSessao.test.tsx` (tabela)

**Interfaces:**
- Consome: `ajuste`, `api.pedirAjuste()`, `api.recusarAjuste()` (B1c); `textoEncerrado` com título (F0).
- Produz:
  - `Acao` ganha `"recusar-ajuste"`; `Sessao.recusarAjuste(): Promise<void>` (em `AcoesDaSessao`); `ajustar()` → `pedirAjuste`.
  - `situacaoDoAjuste(visao: Visao): "nenhum" | "meu" | "do-outro"` em `dominio/`.
  - `Resultado`: sem pedido, `#ajustar` "Pedir para ajustar"; pedido meu, "Esperando <nome> aceitar." e "Cancelar pedido"; pedido do outro, "<nome> quer ajustar a composição." e `#aceitar-ajuste` "Aceitar o ajuste" / `#recusar-ajuste` "Agora não".
  - Avisos e novidades: ao pedir "Pedido enviado. Esperando <nome> aceitar."; quem recebe "<nome> quer ajustar a composição."; aceite visto por quem pediu "<nome> aceitou. De volta à montagem."; quem aceitou "De volta à montagem. As confirmações foram zeradas."; recusa vista por quem pediu "<nome> prefere manter esta noite."; quem recusou "Tudo bem, a noite fica como está."; cancelado "Pedido cancelado.". Se a resposta do pedido já vem `MONTANDO` (R4), vale o texto do aceite, não "Pedido enviado…". Depois do aceite, o foco vai ao primeiro item do inventário pela regra de `destinoDoFoco`.
  - Saída (unilateral): a confirmação continua `Isso encerra o terraço para <nome> também.` / "Encerrar para os dois". Quem saiu: `#encerrado-titulo` "Você saiu do terraço", texto "O encontro terminou para os dois.". O outro (`SAIU`): título "O encontro terminou", texto "Que bom montar o terraço com <nome>." (sem nome: "Que bom montar o terraço a dois."), sem dizer quem saiu. `EXPIROU` e `SALA_INEXISTENTE`: título "Terraço encerrado" e os textos de hoje.
  - `TerracoPagina.tituloEncerrado()` aceita `^(Terraço encerrado|Você saiu do terraço|O encontro terminou)$`; `pedirAjuste()`, `aceitarAjuste()`, `recusarAjuste()`.

- [ ] **Passo 1:** testes.
  - Vitest: `pedirAjusteChamaAApi`; `pedidoCruzadoVoltaAMontagemComTextoDeAceite`; `resultadoMostraAceitarERecusarParaQuemRecebe`; `resultadoMostraCancelarParaQuemPediu`; `recusaVistaPorQuemPediu`; `textoEncerrado` com `{ titulo, texto }` dos 4 motivos.
  - `AjusteCombinadoIT`: `ajusteSoVoltaAMontagemDepoisDoAceite` (Ana pede → continua em `data-momento="noite"` com "Esperando Bruno aceitar."; Bruno toca "Aceitar o ajuste" → os dois em `crepusculo`); `recusaMantemANoite` (Bruno toca "Agora não" → Ana vê "Bruno prefere manter esta noite." e continua na noite); `quemFicaVeOEncontroTerminouSemCulpa` (Ana sai → Bruno vê o heading "O encontro terminou" e "Que bom montar o terraço com Ana.", sem "saiu"); `pedidoDeAjusteSemViolacoes` (os dois lados, `Axe`).
- [ ] **Passo 2:** RED. **Passo 3:** implementar; atualizar os testes da tabela.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` e a suíte e2e completa em chromium verdes. Commit `feat(encontro): ajustar pede o aceite do outro e a saída termina com gentileza`.

### F6b: Convite na mesma aba e toque na sessão

**Skills:** `design-skills:ux-writing`, `design-skills:interaction-design` (troca de sala); revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `convite.ts` (+ `convite.test.ts`), `e2e/TrocaDeConviteIT.java`
- Modificar: `sessao/{sessao,novidade}.ts`, `main.tsx`, `App.tsx`, `componentes/{Entrada,Painel}.tsx`
- Teste: `sessao/{sessao,novidade}.test.ts`, `componentes/Entrada.test.tsx`

**Interfaces:**
- Consome: `ehToque` (F6a); `trocaDeSala` (F0).
- Produz:
  - `lerConvite(local: Location, historia: History): string | null` em `convite.ts` (lê e limpa `#convite=`). O `App` assina `hashchange` num `useEffect` (com cleanup) e chama `sessao.receberConvite`; `main.tsx` só repassa o convite inicial.
  - `Sessao.receberConvite(token: string): void`. Sem sessão → entrada com convite. Convite "próprio" = o token com que esta aba entrou ou qualquer `visao.convite` já visto (lista em memória) → aviso "Este é o convite do seu terraço. Mande para a outra pessoa." (substitui o aviso de janela anônima), sem sair. Sessão em outra sala → entrada com `trocaDeSala: true`, dica "Entrar neste convite encerra o terraço atual para os dois." e o botão "Continuar no terraço"; ao enviar, `api.sair()` (ignora `SEM_ASSENTO`, `SALA_INEXISTENTE`, `SALA_ENCERRADA`) e depois `api.entrar(token, apelido)`. O polling continua, e a tela de troca vence enquanto houver convite em espera.
  - `OpcoesDaSessao.ehToque?: () => boolean`; a instrução recebe o sufixo de cancelar (`""` no toque, `" Esc cancela."` fora), sem flag argument.
  - Textos: entrada com convite "Você recebeu um convite para montar um terraço a dois. Escolha seu apelido."; no `Painel`, "Você foi chamado…" sai.

- [ ] **Passo 1:** testes. `noToqueAInstrucaoNaoCitaEsc`; `proprioConviteAvisaSemTrocar`; `conviteJaUsadoNestaAbaNaoSai` (o token de entrada aberto de novo depois que B entrou → nem `sair` nem `entrar`); `conviteDeOutraSalaSaiEEntra` (ordem `sair` → `entrar`); `continuarNoTerracoDesisteDaTroca`; `entradaComConviteDizRecebeu`; `lerConviteLimpaOHash`. `TrocaDeConviteIT`: `conviteNovoNaMesmaAbaTrocaDeSala` (Ana com Bruno na sala 1; Carla cria a sala 2; Ana navega na mesma aba para `/#convite=<sala 2>` → vê a dica → entra → `#par` contém "Carla"; Bruno vê "O encontro terminou"); `proprioConviteNaMesmaAbaAvisa`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` e o e2e em chromium (`TrocaDeConviteIT,JornadaDoisNavegadoresIT,AcessibilidadeLayoutIT,PrimeiraCargaIT`) verdes. Commit `fix(encontro): convite na mesma aba troca de sala e instrução sem Esc no toque`.

### F8a: Abertura no front (lanternas de mão)

**Skills:** `frontend-design:frontend-design`, `design-skills:interaction-design` (lanternas, tremular, acender; versão parada); `design-skills:ux-writing`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Modificar: `cenario/{tipos,ler}.ts` (`abertura`), `dominio/visao.ts`, `api/cliente.ts`, `sessao/{sessao,novidade,tipos,foco}.ts`, `cena/cena.ts`, `componentes/{Cena,Painel}.tsx`, `estilos/revelacao.css`
- Teste: `cenario/ler.test.ts`, `api/cliente.test.ts`, `sessao/{sessao,novidade,foco}.test.ts`, `componentes/Cena.test.tsx`

**Interfaces:**
- Consome: `POST /api/sala/abertura`, `Visao.abertura` (B8c).
- Produz:
  - Esquema: `abertura: v.optional(v.strictObject({ tipo: v.picklist(["acender-junto"]), sprite: v.string(), escala: v.number(), lugares: v.strictObject({ A: v.string(), B: v.string() }) }))`; o `conferir` exige sprite e lugares existentes. `CenarioFront.abertura(): AberturaDoCenario | null`.
  - `Visao.abertura: { ativa; voceTocou; outroTocou; acesa } | null`; `api.tocarAbertura()`; `Acao` ganha `"abertura"`; `Sessao.tocarAbertura()` (em `AcoesDaSessao`).
  - Com `abertura.ativa`: `instrucao()` devolve `""`; `Painel` esconde `Inventario`, `Falta` e `Acoes` (`hidden`); a cena desenha uma lanterna por lado (sprite e escala do cenário, nos lugares A e B). A própria é `button[data-abertura]` com `aria-label="Acender sua lanterna"`, ícone de mão e chama, sem frase na tela; a do outro tremula (`data-efeito="tremular"`) enquanto `outroTocou`. `foco.ts` põe `[data-abertura] button` antes do inventário.
  - Ao concluir: os botões são removidos (o `levarFocoSeSumiu` leva o foco à 1ª peça); com `acesa`, as duas acendem com o brilho da revelação em escala menor e a região viva anuncia "As lanternas acenderam."; concluída pelo tempo, nada é anunciado. Movimento reduzido: troca direta de apagada para acesa.

- [ ] **Passo 1:** testes. `aberturaComLugarInexistenteRecusada`; `tocarAberturaChamaARota`; `durantAAberturaSemInstrucao` (`instrucao == ""`); `painelEscondeInventarioDuranteAbertura`; `lanternaDoOutroTremulaQuandoEleToca`; `focoVaiParaALanternaDuranteAbertura`; `lanternasAcesasAnunciam` ("As lanternas acenderam."); `aberturaPeloTempoNaoAnuncia`.
- [ ] **Passo 2:** RED. **Passo 3:** implementar.
- [ ] **Passo 4:** `pnpm -C frontend test`, `lint`, `TamanhoDoJsInicialTest` (plano de lazy se > 89 KB) e a suíte e2e em chromium (o terraço ainda sem abertura: nada muda) verdes. Commit `feat(encontro): abertura com as lanternas no front`.

### F8b: O terraço abre com as lanternas

**Skills:** `mattpocock-skills:tdd`, `frontend-design:frontend-design`, `design-skills:interaction-design`; revisão `design-skills:accessibility-audit` + `design-skills:design-critique`.

**Arquivos:**
- Criar: `e2e/AberturaIT.java`
- Modificar: `game/Terraco.java`, `cenario.json` (`abertura`), `pom.xml` (failsafe do perfil `e2e`: `<duora.cenarios.abertura.maximo>PT0S</duora.cenarios.abertura.maximo>` em `systemPropertyVariables`), `e2e/TerracoPagina.java` (`passarAbertura(TerracoPagina ana, TerracoPagina bruno)`)
- Teste: `game/CenarioJsonTest.java`, `room/CenariosConfigTest.java` (novo), helpers da tabela (`RegrasTest`, `TerracoTest`, `ParametrosDoCenarioTest`, `CenarioMinimoTest`, `SalaApiTest`, `ConcorrenciaTest`, `CenarioApiTest`)

**Interfaces:**
- Produz: `Terraco.CENARIO` com `Optional.of(new DefinicaoDeAbertura("acender-junto", Duration.ofSeconds(5), Duration.ofSeconds(90)))`; `cenario.json` `"abertura": { "tipo": "acender-junto", "sprite": "lanterna", "escala": 0.5, "lugares": { "A": "L2", "B": "R2" } }`.

- [ ] **Passo 1:** testes. `CenarioJsonTest.aberturaIgualAoCenario` (presente se e só se o `Cenario` declara; mesmo `tipo`); `CenariosConfigTest.maximoDaAberturaVemDaPropriedade` (`ApplicationContextRunner` com `PT3S` → terraço com `maximo == 3 s`). `AberturaIT` (`properties = "duora.cenarios.abertura.maximo=PT90S"`, `RelogioAdiantavel`): `osDoisAcendemEMontam` (os dois tocam → `[data-efeito=brilho]` nas duas lanternas e o inventário visível nos dois); `soUmTocaEMontaDepoisDe90s` (`relogio.adiantar(Duration.ofSeconds(90))` → inventário visível no próximo polling, sem anúncio); `aberturaPorTeclado` (Tab chega a "Acender sua lanterna", Enter toca); `aberturaSemViolacoes` (`Axe`).
- [ ] **Passo 2:** RED. **Passo 3:** ligar; ajustar os helpers da tabela (dois toques, sem mudar asserção).
- [ ] **Passo 4:** `./mvnw -q verify` e a suíte e2e completa em chromium verdes; depois firefox e webkit, um por vez. Commit `feat(encontro): o terraço abre com as lanternas acesas juntas`.

### P7: Playtest por agente

**Arquivos:**
- Criar (fora do Git): `<worktree>/target/playtest-1c/rodada-<n>/` com `NN-<passo>-{ana,bruno}.png` e `log.json`, copiados ao fim para `.claude/handoff/playtest-1c/rodada-<n>/` do repositório principal, mais `relatorio.md`
- Modificar: o handoff da sessão

- [ ] **Passo 1 (controlador):** com a trava, `./mvnw -q verify` e `./mvnw -q -Pe2e verify -Dnavegador=<x>` para chromium, firefox e webkit, um por vez, tudo verde.
- [ ] **Passo 2 (controlador):** porta livre (`lsof -nP -iTCP:<p> -sTCP:LISTEN` vazio); pegar a trava e mantê-la durante todo o playtest; subir em segundo plano `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=<p>` (tempos reais: sinal 20 s, abertura 5 s/90 s), anotar o PID e esperar `GET http://127.0.0.1:<p>/` responder 200.
- [ ] **Passo 3:** despachar **um agente novo, que não implementou a 1c** (`model: "opus"`, `effort: "medium"`), com a porta, o caminho absoluto de saída, este roteiro e as perguntas do mvp §3. Sem emulador Android.
  - **Passo 0 do agente (harness, sem jogar):**
    1. Pelo `browser_run_code_unsafe`, limpar a página do MCP (Ana, 1440×900): `context.clearCookies()`, `localStorage.clear()`, desregistrar todo service worker e apagar `caches`; navegar a `http://127.0.0.1:<p>/`.
    2. Criar Bruno, nesta ordem: (a) `page.context().browser()?.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, serviceWorkers: "block" })`; (b) se o browser for `null` (perfil persistente), `require("playwright").chromium.launch()` e o mesmo `newContext`; (c) em último caso, nova página no mesmo contexto em `http://localhost:<p>` (host diferente separa os cookies), com toque emulado por CDP (`Emulation.setTouchEmulationEnabled`, `setDeviceMetricsOverride` com `mobile: true`, 390×844).
    3. Guardar `globalThis.duora = { ana, bruno }`; instalar com `addInitScript` a sonda de `AudioContext` do `RevelacaoIT` nos dois lados; conferir `document.visibilityState === "visible"` (senão `bringToFront()` antes de cada passo); registrar no `log.json` a variante usada.
  - **Regra:** cada passo do roteiro é **um** `browser_run_code_unsafe`, que age dos dois lados, espera os seletores (timeout 5 s), grava as duas capturas da viewport (`page.screenshot({ path, fullPage: false })`) e acrescenta ao `log.json`: hora, toques de cada lado, status de cada lado, presença de `[data-destaque=true]`, `data-momento`, `#resultado-titulo`, `#resultado-texto`, contagem de `[data-efeito]` por tipo e contagem da sonda de áudio.
  - **Roteiro** (Ana = A: telescópio + manta ou rádio; Bruno = B: banco + lanterna ou garrafa; composições fixas):
    1. Ana cria e convida; Bruno entra pelo link. Os dois tocam "Acender sua lanterna" no mesmo script (`Promise.all`) → as duas lanternas acesas e o inventário visível. Registrar, só como dado, se a abertura deixou o começo mais leve.
    2. Composição 1: Ana telescópio C1 e manta L1; Bruno banco C2 e lanterna R2; depois de cada colocação, capturar quem recebe assim que aparecer `[data-destaque=true]`. Ana sugere a lanterna em L2 e Bruno toca "Mover a lanterna: fundo, à esquerda" no mesmo script. Bruno sugere a manta em R1 e ninguém age: esperar 21 s reais e capturar o botão "Mover …" sumido.
    3. Os dois confirmam e revelam: capturar a noite dos dois lados, a tela cheia de Bruno e a contagem de efeitos.
    4. Ana pede para ajustar e Bruno toca "Agora não" (captura); Ana pede de novo e Bruno toca "Aceitar o ajuste" (captura). Contar os toques de cada lado da noite ao crepúsculo.
    5. Composição 2: Ana troca a manta pelo rádio, em R1, e põe o telescópio em C2; Bruno troca a lanterna pela garrafa, em L1, e põe o banco em C1. Revelar; a sonda conta ≥ 1 buffer iniciado (o rádio toca com o som desligado).
    6. Bruno sai e confirma: capturar "Você saiu do terraço" (Bruno) e "O encontro terminou" (Ana).
- [ ] **Passo 4:** o agente responde cada pergunta com "sim" ou "não", uma frase de porquê e as capturas e entradas do `log.json` que provam. Critérios de "sim":
  - **2:** em ≥ 3 momentos **para cada lado**, a mudança do outro aparece numa captura de viewport (destaque na peça ou status abaixo dos nomes);
  - **3:** uma sugestão com destino aceita, uma ignorada (expirada) e um pedido de ajuste recusado e depois aceito, cada um nas capturas dos dois lados;
  - **4:** as duas revelações têm títulos diferentes, `#resultado-texto` diferente com pelo menos uma frase de posição relativa diferente e contagens de `[data-efeito]` por tipo diferentes;
  - **5:** da noite à montagem, no máximo 2 toques de cada lado, e a segunda revelação difere da primeira;
  - **1:** respondida, só para registro.
  - Falha de harness (seletor que não apareceu por tempo, ferramenta com erro) refaz aquele passo uma vez, registrado. Falha do produto vira "não".
- [ ] **Passo 5 (controlador):** matar o PID, conferir a porta livre e soltar a trava; copiar `target/playtest-1c/` para o handoff e escrever o `relatorio.md`.
  - Com 2–5 "sim": fechar a etapa (`duora-etapa`). O relatório diz que os "sim" seguem os critérios objetivos (o agente não declara a aprovação do dono, mvp:62) e que "bonito" e "divertido" continuam avaliação humana. O dono recebe o pacote (4 capturas, relatório e o `radio-notas.mp3` como `Candidato a FINAL`) com veto opcional; o padrão é seguir.
  - Com algum "não": cada falha vira uma task de correção com teste antes, e outra rodada roda com outro agente novo (rodada = roteiro inteiro; no máximo 2). Persistindo, levar ao dono só a escolha de mecânica (2–3 opções + recomendação).

---

## Self-review

- **Cobertura da spec (Etapa 1c):** ver o que o outro fez → F2; sinal com destino → B1b, F3; efeito por peça e texto por posições → F4a, F4b, F4c (A4a para o som); concordância para ajustar → B1c, F5; "Sair" → R7, F5, D1; tela cheia no celular → F4c; correções ("pelo" → F6a; "Você foi chamado" → F6b; janela anônima e Esc no celular → F6a/F6b; "O que falta" → F6a; contorno do título → F6a; convite na mesma aba → F6b; 401 da primeira carga → B1a); motor genérico → B0b, B1b, B1c, B8a–B8c; abertura (emenda) → B8a–F8b; prova → P7.
- **Nomes e tipos entre tasks:** `Sinal.deAssento`/`destino` (B1b) → `VisaoSinal.lugar` → F3; `Revelado.pedidoDeAjuste` (B1c) → `VisaoAjuste.deAssento` → `situacaoDoAjuste` (F5); `Montando.abertura` e `Abertura.ativa()` (B8b) → `VisaoAbertura` (B8c) → `Visao.abertura` TS (F8a); `MarcasDaCena`, `criarElementosDoEfeito`, `AcoesDaSessao`, `usePassagemAoVivo` (F0) → F2, F3, F4b, F4c, F5, F8a; `CenarioFront.frasesDeRelacao`/`audio` (F4a) → F4c; `Efeito.notas.som` nome-base (F4b) → `audio` (F4c); `RelogioAdiantavel`/`Axe` (F0e) → F3, F4c, F5, F8b; `comMaximoDeAbertura` (B8a) → F8b.
- **Proporção:** o plano fixa decisões, assinaturas, textos e asserções; corpos de função ficam com o implementador.
