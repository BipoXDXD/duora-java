# Etapa 1b — Motor de cenários: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** regras, API e front deixam de ter o terraço fixo no código. O terraço vira o primeiro `Cenario`, sem mudança de comportamento, e um cenário mínimo de teste (2 lugares, 2 peças) roda de ponta a ponta pela API.

**Arquitetura:**
- `game/`: `Peca`, `Lugar` e `Zona` viram records com `id` de texto (o JSON da API não muda). Um `Cenario` (record validado na construção) reúne lugares, peças, parâmetros de regra e um `Desfecho` (Strategy). `Regras` continua estática, com `Cenario` como 1º parâmetro de toda função pública. `CatalogoDeCenarios` é Java puro em `game/`, montado como bean por `room/CenariosConfig`.
- `room/`: a `Sala` (futuro `Encontro`) guarda o cenário; `POST /api/salas` aceita `"cenario"` (padrão `"terraco"`); a `Visao` ganha `"cenario"` e o `resultado` vira o mapa ordenado do desfecho.
- Front: `main.tsx` espera `carregarCenario("terraco")`, que busca em paralelo `cenario.json` (apresentação) e `cena/cena.json` (geometria), valida os dois com valibot (só em `cenario/ler.ts`) e devolve um `CenarioFront` com métodos. Os assets vão para `static/assets/cenarios/terraco/`.
- O back-end muda por estrangulamento (B1→B4): cada task compila e fica verde.

**Stack:** Java 25, Spring Boot 4.2.0, JUnit/AssertJ/MockMvc; front da 1a (Vite 8, React 19.3, TypeScript 6.0 `strict`, Vitest 5) + **valibot 1.x** (nova, runtime); Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1b, linhas 144-150). Contratos: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md` (valem sobre este plano). Insumos: `.claude/handoff/plano-1b/{decisoes,backend,frontend,contratos}.md`.

**Pré-requisito real:** a Etapa 1a integrada na `main` (hoje na branch `etapa-1a`). A branch de integração `etapa-1b` sai da `main` depois disso.

**Skills por momento:** `mattpocock-skills:tdd` (ou `superpowers:test-driven-development`) na implementação; `mattpocock-skills:research` + Context7 para a versão e a API do valibot; `superpowers:systematic-debugging` em falha; `superpowers:verification-before-completion` e `duora-etapa` antes de cada commit; `mattpocock-skills:domain-modeling` na Task D1; `superpowers:requesting-code-review` + `mattpocock-skills:code-review` entre tasks e no fim.

## Restrições globais
- Valem as da Etapa 0, 1 e 1a: Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; `-Xlint:all -Werror`, Spotless, ESLint + Prettier com `--max-warnings 0`; um comportamento por teste; E2E sem skip nos três navegadores; CSP inalterada; textos em pt-BR.
- **Maven pesado um por vez:** pegar a trava com `mkdir /private/tmp/claude-501/duora-pesado.lock` (se falhar, outro está rodando: esperar e tentar de novo), rodar sob `caffeinate -dims`, liberar com `rmdir` mesmo em falha. Todo `./mvnw` deste plano roda assim (o Maven também roda o build e o Vitest do front pelo plugin).
- **pnpm só nas tasks de front.** Abaixo, `pnpm` = `PATH="$PWD/node:$PATH" node node/node_modules/pnpm/bin/pnpm.mjs`, rodado em `frontend/`.
- **Comportamento idêntico para o terraço.** Textos, imagens, sons, efeitos e o JSON da API saem iguais. Campos novos só são acrescentados. Os testes existentes só mudam mecanicamente (imports, argumentos `CENARIO`/`Terraco.CENARIO`, construtores, o caminho de um asset), **exceto estas três mudanças de asserção, as únicas permitidas:**
  1. `SalaApiTest.visaoTemExatamenteOsCamposDoContrato` (`SalaApiTest.java:101-111`): o conjunto exato de chaves da `Visao` passa a incluir `"cenario"` (adição exigida pelos contratos, consumida pela Etapa 4);
  2. `RegrasTest` compara `Resultado` pela nova forma com os **mesmos** valores (`RegrasTest.java:348-361`);
  3. `@CsvSource` do `RegrasTest` ganha conversão explícita de id → record, com os mesmos casos.
  Nenhum outro valor esperado muda. Contagens de hoje: `RegrasTest` 48, `SalaApiTest` 47, `ConcorrenciaTest` 5, `SalasTest` 8, 12 classes `e2e/*IT`.
- **Invariantes do motor** (fixados aqui, registrados no ADR e nos contratos):
  - id de cenário permanente, casando `^[a-z0-9-]+$`; nunca renomeado nem removido (a Etapa 4 o grava em `cenario_jogado`, a 5 em `encontro`); ids de peça e de lugar estáveis;
  - `Desfecho` puro e determinístico (sem relógio, sorteio nem estado), chamado só com composição completa;
  - `Resultado.valores()` preserva a ordem de inserção (`Collections.unmodifiableMap(new LinkedHashMap<>(…))`, nunca `Map.copyOf`): o ETag é o SHA-256 dos bytes do JSON (`SalaController.java:103-106`);
  - ordem das peças do terraço = ordem atual do enum (`Peca.java:13-18`): `TELESCOPIO, MANTA, RADIO, BANCO, LANTERNA, GARRAFA`; lugares `C1, C2, L1, L2, R1, R2`;
  - `game/` sem Spring, Jackson nem JDBC em `src/main`.
- **Front:** valibot só é importado por `frontend/src/cenario/ler.ts` (guarda de ESLint na Task F2). Versão exata, sem `^`/`~`: a 1.x mais nova com ≥ 14 dias de publicada no dia da execução (`npm view valibot time --json`), API conferida no Context7. Nenhum consumidor lê o JSON cru: só os métodos do `CenarioFront`. Um `import()` dinâmico não entra no shell do service worker (`frontend/build/service-worker.ts:110-112`): o cenário e o valibot ficam em imports estáticos.
- JS inicial: o `TamanhoDoJsInicialTest` falha acima de 90 KB gzip (`LIMITE_BYTES = 92_160`); a meta é 80 KB (hoje ~81,7 KB). Medir e registrar em F2, F4 e na prova final.

## Rulings de planejamento (o dono pode reverter)
- **R1. `Regras` estática com `Cenario` como 1º parâmetro em toda função pública** (alternativa A), inclusive as que a 1c criar (`tocarAbertura`, `avancarAbertura`). Custo se errado: virar instância é um `sed` nas chamadas e reescrever as Interfaces de 1c, 4 e 9b.
- **R2. Back-end em estrangulamento (B1→B4)**, cada task verde: records com fachada compatível; cenário ao lado com as `Regras` antigas delegando ao terraço; sala com cenário; remoção da fachada. Custo se errado: uma task a mais de fachada temporária.
- **R3. Só as três mudanças de asserção das Restrições globais.** Custo se errado: um teste reescrito esconderia regressão; o revisor recusa qualquer outra.
- **R4. Ordem das peças no `Terraco` = ordem do enum.** Custo se errado: `pecas[3]` deixa de ser `banco` e quebra `SalaApiTest:97-100` e a ordem do inventário.
- **R5. `Resultado` com `LinkedHashMap` → `Collections.unmodifiableMap`.** Custo se errado: chaves do JSON em ordem instável e ETag diferente para o mesmo estado.
- **R6. Portão e troca de secundária provados por variantes do terraço com outros parâmetros e por um cenário de troca** (o mínimo, com uma peça obrigatória por pessoa, não os exercita); o mínimo prova o ponta a ponta. Custo se errado: regra presa a constante passaria despercebida.
- **R7. Caminho canônico `static/assets/cenarios/<id>/`** com `cenario.json` (apresentação, à mão), `cena/` (sprites + `cena.json`, geometria do `cena.py`), `ceu/` e `audio/`; URL `/assets/cenarios/<id>/cenario.json`. Emendas da spec (S:99, S:146) e seção "Cenários" nos contratos no mesmo PR (Task D1). Custo se errado: mover pastas de novo e corrigir 1c, 5 e 9b.
- **R8. Fixados no código:** `CatalogoDeCenarios.porId`/`todos()`; `Salas.criar(Apelido, Cenario)`; `nome` de exibição no `Cenario` e no `cenario.json` (terraço `"Terraço das Estrelas Cadentes"`, escolhido pelo dono; os nomes dos demais cenários são decididos quando cada um for construído, na 9b, não agora). Custo se errado: renomear um acessor em 4 planos.
- **R9. `Desfecho` é o nome da Strategy;** a Etapa 5 renomeia o seu enum para `DesfechoDaDecisao` (Task D1). Custo se errado: homônimo entre `game` e `conexao.dominio`.
- **R10. Efeitos por peça na chave `revelacao` do `cenario.json`** (a da 1c), com a união `Efeito` = `brilho-piso | halo` na 1b; a 1c só acrescenta variantes. Custo se errado: duas listas de efeitos noturnos por peça.
- **R11. `CONTEXT.md` (glossário) e ADR `docs/adr/0003-motor-de-cenarios.md` nesta etapa** (Task D1).
- **R12. (dono) valibot para o `cenario.json`, confinado a `ler.ts`; `CatalogoDeCenarios` em `game/`, Java puro, bean por configuração.**
- **R13. Front carrega o cenário antes de montar:** sem Suspense nem fallback (o esqueleto do `index.html` já é o fallback); `CenarioFront` com métodos; sem ids com marca (a API traz strings); a sessão TS recebe `cenario` por parâmetro; id `"terraco"` fixo no front até a Etapa 4 (o TS não declara `Visao.cenario` até lá). Custo se errado: o fundo começa a baixar uma ida e volta depois (mitigável com `<link rel=preload>`).
- **R14. Miniatura e céu como hoje.** Miniatura `sprite(peca, lugar ?? "C2")` (`Inventario.tsx:70`, `Inventario.test.tsx:84`) vira `sprite(peca, lugar | null)` com `semLugar: "telescopio-dir"`. O céu fica literal no `cena.css` (par `url()` + `image-set`, `cena.css:170-180`), só com o caminho novo; o `ceu.ts` pré-carrega por `c.urlCeu` e um teste confere que as duas fontes batem. Céu por cenário no CSS fica para a 9b. Custo se errado: a 9b troca o CSS literal por variável escolhida em JS.
- **R15. `VisaoResultado` genérico no front** (`Readonly<Record<string, string>>`), acompanhando o back. Os campos do terraço continuam no JSON.
- **R16. Textos com "terraço" ficam** (`Sala.java:197,223`, `Salas.java:54`, `sessao`/`novidade` no front): comportamento idêntico; revê-los é da 9b, com o segundo cenário jogável. Custo se errado: um cenário novo mostra "terraço" em mensagens de erro.
- **R17. Ordem dos erros em `POST /api/salas`:** apelido (`APELIDO_INVALIDO`) → cenário (`CENARIO_INVALIDO`) → limite (`LIMITE_SALAS`). Ids de peça/lugar resolvidos no controller pelo cenário da sala, depois de `localizar` e antes de entrar na sala (preserva a precedência 422 × 410).

## Foco de revisão
1. **`cenario` desconhecido ou fora do padrão no pedido** (`"vulcao"`, `"../terraco"`): 422 `CENARIO_INVALIDO`, nenhuma sala criada. Fixado por `CenarioApiTest.criarComCenarioDesconhecidoRecusaSemCriarSala` e `cenarioForaDoPadraoERecusado` (Task B3).
2. **Peça ou lugar de outro cenário na sala:** recusado como id inválido, igual a hoje. Fixado por `CenarioApiTest.pecaDoMinimoNaSalaDoTerracoEPecaInvalida`, `pecaDoTerracoNaSalaDoMinimoEPecaInvalida` e `lugarDeOutroCenarioELugarInvalido` (Task B3).
3. **`cenario.json` ou `cena.json` ausente ou inválido:** mensagem na tela, nunca tela branca nem app quebrado no meio. Fixado por `ler.test.ts` (caminho do erro, Task F2), `inicio.test.tsx` "cenário que falha mostra o alerta" e `CenarioFrontIT.cenarioInexistenteMostraErroNaTela` (Task F4).
4. **Ordem estável das peças e das chaves do resultado** (front, testes e ETag dependem dela). Fixado por `TerracoTest.pecasNaOrdemDoContrato` e `resultadoMantemAOrdemDasChaves` (Task B2) e `CenarioApiTest.resultadoDoTerracoSaiNaOrdemDoContrato` (Task B3).
5. **Comparação por referência escondida e regra presa a constante:** `==` entre records passaria "por sorte" com as constantes únicas; portão, sinal, `faltam` e troca têm de seguir o cenário. Fixado por `IgualdadePorValorTest` (Task B1) e `ParametrosDoCenarioTest` (Task B2).

---

Caminhos Java abaixo: `game/…` = `src/main/java/bipotheking/net/duora/game/…` (teste: `src/test/java/bipotheking/net/duora/game/…`); idem `room/`, `pwa/`, `e2e/`.

### Task B1: Peça, lugar e zona como records, com fachada compatível

**Arquivos:**
- Modificar: `game/{Zona,Lugar,Peca,Composicao,Regras}.java`
- Criar teste: `game/IgualdadePorValorTest.java`

**Interfaces:**
- Consome: nada novo.
- Produz (permanente): `public record Zona(String id)`; `public record Lugar(String id, Zona zona)`; `public record Peca(String id, Assento dono, Zona zona, boolean obrigatoria)` com `boolean aceita(Lugar lugar)` por `equals` de zona. Construtores compactos com `Objects.requireNonNull` e id não vazio.
- Produz (fachada provisória, some em B4), com as **mesmas assinaturas do enum**, para nenhum chamador mudar:
  - `Zona.CENTRO = new Zona("centro")`, `Zona.LATERAL = new Zona("lateral")`;
  - `Lugar.C1…R2` (`new Lugar("C1", Zona.CENTRO)`…), `static Lugar[] values()` (cópia, ordem C1, C2, L1, L2, R1, R2), `static Optional<Lugar> deId(String)`, `static List<Lugar> da(Zona)`, `static Lugar valueOf(String nome)`;
  - `Peca.TELESCOPIO = new Peca("telescopio", Assento.A, Zona.CENTRO, true)`, `MANTA ("manta", A, LATERAL, false)`, `RADIO ("radio", A, LATERAL, false)`, `BANCO ("banco", B, CENTRO, true)`, `LANTERNA ("lanterna", B, LATERAL, false)`, `GARRAFA ("garrafa", B, LATERAL, false)`; `static Peca[] values()` nessa ordem, `deId`, `de(Assento)`, `secundariasDe(Assento)`, `static Peca valueOf(String nome)` (nome do enum, ex. `"TELESCOPIO"`: é o único fábrica `String → Peca`, e o JUnit o usa para converter o `@CsvSource` sem mudar o teste).
- `==` → `equals` em `Composicao.ocupante` (:26), `Regras.lugaresPermitidos` (:130) e `Regras.resultado` (:151); `EnumMap(Peca.class)` → `HashMap` em `Composicao.com`/`sem` (:58, :68).

- [ ] **Passo 1: escrever o `IgualdadePorValorTest`** (instâncias iguais, não idênticas):
  - `pecaAceitaLugarIgualMasNaoIdentico`: `assertThat(Peca.MANTA.aceita(new Lugar("L1", new Zona("lateral")))).isTrue()`;
  - `ocupanteAchaLugarIgualMasNaoIdentico`: `Composicao.vazia().com(Peca.MANTA, Lugar.L1).ocupante(new Lugar("L1", new Zona("lateral")))` → `contains(Peca.MANTA)`;
  - `lugaresPermitidosExcluiOLugarAtualPorValor`: `new Estado(Fase.MONTANDO, 0, Composicao.vazia().com(Peca.TELESCOPIO, new Lugar("C1", new Zona("centro"))), Set.of(), Optional.empty())` → `Regras.lugaresPermitidos(estado, Peca.TELESCOPIO)` `containsExactly(Lugar.C2)`.
- [ ] **Passo 2: RED.** `./mvnw -q test -Dtest=IgualdadePorValorTest` → `COMPILATION ERROR` (`Lugar`/`Zona` são enums, sem construtor público).
- [ ] **Passo 3: implementar os três records, a fachada e as trocas acima.** Nenhum arquivo de `room/` nem teste existente muda.
- [ ] **Passo 4: GREEN.** `./mvnw -q verify` → `BUILD SUCCESS`; `git diff --stat main -- src/test` lista só `IgualdadePorValorTest.java`.
- [ ] **Passo 5: commit** `refactor(game): peça, lugar e zona como records comparados por valor`.

### Task B2: `Cenario`, `Desfecho`, `Terraco` e o cenário mínimo

**Arquivos:**
- Criar: `game/{Cenario,Desfecho,Terraco}.java`
- Modificar: `game/{Regras,Resultado,Composicao}.java`, `room/Visao.java` (só o `resultado`)
- Remover: `game/Lado.java`
- Criar teste: `game/CenarioMinimo.java` (fixture pública), `game/{CenarioTest,CenarioMinimoTest,ParametrosDoCenarioTest,TerracoTest}.java`
- Modificar teste: `game/RegrasTest.java:348-361` (mudança de asserção 2)

**Interfaces:**
- Consome: B1.
- Produz:
  - `public record Cenario(String id, String nome, List<Lugar> lugares, List<Peca> pecas, int limiteAntesDoParceiro, Duration duracaoSinal, int pecasPorPessoaCompleta, Desfecho desfecho)`. O construtor compacto valida e lança `IllegalArgumentException` com mensagem que cita o valor ofensor: id casa `^[a-z0-9-]+$`; `nome` não vazio; ids de lugar únicos; ids de peça únicos; toda zona de peça tem ≥ 1 lugar; `limiteAntesDoParceiro ≥ 1`; `duracaoSinal` positiva; `1 ≤ pecasPorPessoaCompleta ≤` peças de cada assento. Listas por `List.copyOf`. Consultas: `Optional<Peca> peca(String id)`, `Optional<Lugar> lugar(String id)`, `List<Lugar> da(Zona zona)` (ordem de `lugares`), `List<Peca> pecasDe(Assento dono)`, `List<Peca> secundariasDe(Assento dono)` (ordem de `pecas`), `int faltam(Composicao c)` (soma por assento de `max(0, pecasPorPessoaCompleta − c.colocadas(assento))`), `boolean completa(Composicao c)`.
  - `@FunctionalInterface public interface Desfecho { Resultado de(Composicao composicao, Cenario cenario); }`, com Javadoc do invariante (puro, determinístico, só com composição completa).
  - `public record Resultado(Map<String, String> valores)`, compacto: `Collections.unmodifiableMap(new LinkedHashMap<>(valores))`.
  - `public final class Terraco`: `ID = "terraco"`; constantes `CENTRO, LATERAL, C1…R2, TELESCOPIO…GARRAFA`; `Cenario CENARIO = new Cenario(ID, "Terraço das Estrelas Cadentes", List.of(C1, C2, L1, L2, R1, R2), List.of(TELESCOPIO, MANTA, RADIO, BANCO, LANTERNA, GARRAFA), 1, Duration.ofSeconds(20), 2, Terraco::desfecho)`. O desfecho devolve, nesta ordem, `secundariaA` (id da secundária colocada de A), `secundariaB`, `ladoTelescopio` (`"esquerdo"` se o telescópio está em `C1` por `equals`, senão `"direito"`). **Nesta task as constantes do `Terraco` apontam para as da fachada** (`TELESCOPIO = Peca.TELESCOPIO`…): o contrário (`Peca.TELESCOPIO = Terraco.TELESCOPIO`) faz `Peca` ler `null` quando `Terraco` inicializa primeiro. B4 inverte.
  - `Regras` ganha as versões com cenário, que passam a ter a lógica (portão por `cenario.limiteAntesDoParceiro()`, sinal por `cenario.duracaoSinal()`, troca por `cenario.secundariasDe`, completa por `cenario.completa`, resultado por `cenario.desfecho()`):
    `parceiroEntrou(Cenario, Estado)`, `colocar(Cenario, Estado, Assento quem, Peca peca, Optional<Lugar> destino, long versaoEsperada)`, `sinalizar(Cenario, Estado, Assento quem, Peca peca, Instant agora)`, `confirmar(Cenario, Estado, Assento quem, boolean pronto, long versao)`, `ajustar(Cenario, Estado)`, `List<Lugar> lugaresPermitidos(Cenario, Estado, Peca)` (por `cenario.da`), `boolean bloqueadaPeloPortao(Cenario, Estado, Peca)`, `Optional<Resultado> resultado(Cenario, Estado)`. As antigas `f(Estado, …)` delegam a `f(Terraco.CENARIO, …)` (provisórias, Javadoc "sai na B4"; sem `@Deprecated`, que o `-Werror` barraria).
  - `Composicao`: sai `PECAS_POR_PESSOA_COMPLETA` e `secundariaDe`; `faltam()`/`completa()` viram fachada delegando a `Terraco.CENARIO` (somem em B4).
  - `room/Visao`: o componente `resultado` passa a `@Nullable Map<String, String>` (`Regras.resultado(estado).map(Resultado::valores)`); o record `VisaoResultado` sai. JSON do terraço idêntico.
  - Fixture `CenarioMinimo` (teste, `public final class`): `ZONA = new Zona("unica")`, `P1 = new Lugar("P1", ZONA)`, `P2 = new Lugar("P2", ZONA)`, `PEDRA = new Peca("pedra", A, ZONA, true)`, `FOLHA = new Peca("folha", B, ZONA, true)`, `CENARIO = new Cenario("minimo", "Mínimo", List.of(P1, P2), List.of(PEDRA, FOLHA), 1, Duration.ofSeconds(20), 1, (c, cen) -> new Resultado(Map.of("juntos", "sim")))`.

- [ ] **Passo 1: escrever os testes.**
  - `CenarioTest` (cada um `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class)` e mensagem com o valor): `idForaDoPadraoNaoConstroi` (`"Terraço"`), `idDeLugarRepetidoNaoConstroi` (`"P1"`), `idDePecaRepetidoNaoConstroi` (`"pedra"`), `zonaSemLugarNaoConstroi`, `pecasPorPessoaAlemDoQueOAssentoTemNaoConstroi` (mínimo com 2); e `listasSaoCopiasImutaveis` (`lugares().add(...)` lança `UnsupportedOperationException`).
  - `CenarioMinimoTest`, a partir de `montando = aceito(Regras.parceiroEntrou(CENARIO, Estado.inicial()))`: `faltamDuasNoComeco` (`CENARIO.faltam(Composicao.vazia()) == 2`); `colocarPrimeiraPecaAceita` (pedra em P1 → `lugarDe(PEDRA)` contém P1, versão 1); `confirmarAntesDeCompletarERecusado` (só a pedra → `Recusada(INCOMPLETA)`); `composicaoCompletaRevelaODesfechoDoCenario` (pedra P1, folha P2, os dois confirmam → `resultado(CENARIO, e).orElseThrow().valores()` `containsExactly(entry("juntos", "sim"))`); `idDeOutroCenarioNaoEPecaDesteCenario` (`CENARIO.peca("manta")`, `CENARIO.lugar("C1")` e `Terraco.CENARIO.peca("pedra")` vazios).
  - `ParametrosDoCenarioTest`, com `variante(int limite, Duration sinal, int porPessoa)` = cópia do `Terraco.CENARIO` (mesmos lugares, peças e desfecho, id `"terraco-variante"`):
    - `limiteDoisDeixaSegundaPecaAntesDoParceiro` (A põe telescópio C1 e manta L1, B sem peça → `Aceita`) e `limiteUmRecusaSegundaPecaAntesDoParceiro` (mesma sequência → `Recusada(ESPERE_PRIMEIRA_PECA)`);
    - `bloqueioDoPortaoSegueOLimite`: só o telescópio de A colocado → `bloqueadaPeloPortao(variante(2,…), e, MANTA)` falso e `(variante(1,…), e, MANTA)` verdadeiro;
    - `sinalDuraOQueOCenarioDiz`: `variante(1, 5 s, 2)`, B sinaliza o telescópio em `AGORA` → `sinal().orElseThrow().expiraEm() == AGORA.plusSeconds(5)`;
    - `faltamSegueOCenario`: `variante(1, 20 s, 1)` → `faltam(vazia) == 2`; com telescópio e banco colocados, `completa` verdadeiro;
    - `trocaDeSecundariaUsaAsPecasDoCenario`: cenário `"troca"` com zonas `meio`/`borda`, lugares `M1, M2` (meio) e `B1, B2` (borda), peças `estrela` (A, meio, obrigatória), `lua` e `sol` (A, borda), `nuvem` (B, meio, obrigatória), limite 1, 20 s, 1 por pessoa; B põe nuvem M2, A põe estrela M1, lua B1, sol B2 → `lugarDe(lua)` vazio e `lugarDe(sol)` contém B2.
  - `TerracoTest`: `pecasNaOrdemDoContrato` (ids `containsExactly("telescopio", "manta", "radio", "banco", "lanterna", "garrafa")`); `lugaresNaOrdemDeHoje` (`"C1", "C2", "L1", "L2", "R1", "R2"`); `parametrosDeHoje` (id `"terraco"`, nome `"Terraço das Estrelas Cadentes"`, 1, 20 s, 2); `resultadoMantemAOrdemDasChaves` (telescópio C1, banco C2, manta L1, lanterna R1, os dois confirmam → `valores()` `containsExactly(entry("secundariaA", "manta"), entry("secundariaB", "lanterna"), entry("ladoTelescopio", "esquerdo"))`); `ladoDoTelescopioComparaLugarPorValor` (`CENARIO.desfecho().de(composição com telescópio em new Lugar("C1", new Zona("centro")), CENARIO)` → `ladoTelescopio` `"esquerdo"`).
  - `RegrasTest:348-361`: `assertThat(Regras.resultado(revelado()).orElseThrow().valores()).containsExactly(entry("secundariaA", "manta"), entry("secundariaB", "lanterna"), entry("ladoTelescopio", "esquerdo"))` e, em `telescopioEmC2ApontaParaADireita`, `manta`, `garrafa`, `direito`.
- [ ] **Passo 2: RED.** `./mvnw -q test -Dtest='Cenario*Test,ParametrosDoCenarioTest,TerracoTest,RegrasTest'` → `COMPILATION ERROR` (`Cenario`, `Terraco` inexistentes).
- [ ] **Passo 3: implementar as assinaturas acima.** O `resultado()` atual vira `Terraco::desfecho`; `Lado` some.
- [ ] **Passo 4: GREEN.** `./mvnw -q verify` → `BUILD SUCCESS`; `SalaApiTest` (47) e `ConcorrenciaTest` (5) sem alteração; no `RegrasTest` só as linhas 348-361 mudaram (`git diff --stat`).
- [ ] **Passo 5: commit** `refactor(game): cenário como dados e terraço como primeiro cenário`.

### Task B3: Sala com cenário, catálogo e API

**Arquivos:**
- Criar: `game/CatalogoDeCenarios.java`, `room/CenariosConfig.java`
- Modificar: `room/{Sala,Salas,SalaController,Visao,Pedidos,Codigo}.java`
- Criar teste: `game/CatalogoDeCenariosTest.java`, `room/CenarioApiTest.java`
- Modificar teste: `room/SalasTest.java`, `room/ConcorrenciaTest.java` (mecânico: `salas.criar(apelido)` → `salas.criar(apelido, Terraco.CENARIO)`), `room/SalaApiTest.java:101-111` (mudança de asserção 1)
- Handoff (não versionado): `.claude/handoff/contrato-api.md` (campo `cenario` no pedido e na `Visao`, `resultado` como mapa do desfecho, `CENARIO_INVALIDO`)

**Interfaces:**
- Consome: B2 (`Cenario`, `Terraco`, `Regras.f(Cenario, …)`, `CenarioMinimo`).
- Produz:
  - `public final class CatalogoDeCenarios { public CatalogoDeCenarios(List<Cenario> cenarios); public Optional<Cenario> porId(String id); public List<Cenario> todos(); }`: ordem de registro, imutável; lista vazia ou id repetido → `IllegalArgumentException` com o id (a aplicação não sobe). Sem `Map` estático.
  - `room/CenariosConfig` (`@Configuration`): `@Bean Cenario terraco()` → `Terraco.CENARIO`; `@Bean CatalogoDeCenarios catalogoDeCenarios(List<Cenario> cenarios)`. Cenário novo = um `@Bean Cenario`.
  - `Sala(String id, Participante criador, Convite convite, Cenario cenario, Clock relogio, LimitesSala limites)` e `Cenario cenario()` (`final`, lido sem a trava); toda chamada a `Regras` passa o cenário.
  - `Salas.criar(Apelido apelido, Cenario cenario)` (`synchronized`, como hoje).
  - `Pedidos.CriarSala(@Nullable String apelido, @Nullable String cenario)`; `fail-on-unknown-properties` continua.
  - `Codigo.CENARIO_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Cenário inválido")`.
  - `SalaController(Salas, CatalogoDeCenarios, JsonMapper)`: `criar` faz `Apelido.de` → `catalogo.porId(requireNonNullElse(pedido.cenario(), Terraco.ID))` (vazio → `ErroApi(CENARIO_INVALIDO, "Cenário desconhecido.")`) → `salas.criar`; `peca`/`destino` resolvem por `eu.sala().cenario().peca(id)`/`.lugar(id)` com os mesmos códigos e mensagens de hoje.
  - `record Visao(String cenario, Fase fase, …)` (o id como 1º componente) e `static Visao de(Cenario cenario, Retrato retrato, Assento quem, Instant agora, LimitesSala limites)`: peças de `cenario.pecas()`, `Confirmacao(fase == MONTANDO && cenario.completa(c), cenario.faltam(c))`, `Regras.f(cenario, …)`.

- [ ] **Passo 1: escrever os testes.**
  - `CatalogoDeCenariosTest` (catálogo com `Terraco.CENARIO` e `CenarioMinimo.CENARIO`): `porIdAchaOCenario` (`"minimo"`); `porIdDesconhecidoVazio` (`"vulcao"`); `todosNaOrdemDeRegistro` (`containsExactly(Terraco.CENARIO, CenarioMinimo.CENARIO)`); `idRepetidoNaoConstroi` (dois `"terraco"` → mensagem contém `terraco`).
  - `CenarioApiTest` (`@SpringBootTest @AutoConfigureMockMvc`, `@TestConfiguration` com `@Bean Cenario minimo()` → `CenarioMinimo.CENARIO`; helpers privados copiados de `SalaApiTest:719-760`, com o corpo de criação recebendo o `cenario` opcional):
    - `criarSemCenarioUsaOTerraco`: 201, `$.cenario` `"terraco"`, `$.pecas` com 6;
    - `criarComCenarioDesconhecidoRecusaSemCriarSala`: `salas.total()` antes; `{"apelido":"Ana","cenario":"vulcao"}` → 422, `application/problem+json`, `$.codigo` `"CENARIO_INVALIDO"`; `total()` depois igual;
    - `cenarioForaDoPadraoERecusado`: `"../terraco"` → 422 `CENARIO_INVALIDO`;
    - `apelidoInvalidoVemAntesDoCenario`: `{"apelido":"","cenario":"vulcao"}` → 422 `APELIDO_INVALIDO`;
    - `cenarioMinimoDePontaAPonta`: criar com `"minimo"` → `$.cenario` `"minimo"`, `$.pecas[*].peca` `["pedra","folha"]`, `$.confirmacao.faltam` 2; Bruno entra; pedra P1 (v0), folha P2 (v1), pronto dos dois na v2 → `$.fase` `"REVELADO"`, `$.resultado.juntos` `"sim"` e `$.resultado` com uma chave só;
    - `pecaDoMinimoNaSalaDoTerracoEPecaInvalida`: `{"peca":"pedra","lugar":"C1"}` → 422 `PECA_INVALIDA`;
    - `pecaDoTerracoNaSalaDoMinimoEPecaInvalida`: `{"peca":"telescopio","lugar":"P1"}` → 422 `PECA_INVALIDA`;
    - `lugarDeOutroCenarioELugarInvalido`: sala do mínimo, `{"peca":"pedra","lugar":"C1"}` → 422 `LUGAR_INVALIDO`;
    - `resultadoDoTerracoSaiNaOrdemDoContrato`: terraço revelado (telescópio C1, banco C2, manta L1, lanterna R1) → no corpo bruto, `indexOf("\"secundariaA\"") < indexOf("\"secundariaB\"") < indexOf("\"ladoTelescopio\"")`.
  - `SalaApiTest:101-111`: acrescentar `"cenario"` ao `containsExactlyInAnyOrder`.
- [ ] **Passo 2: RED.** `./mvnw -q test -Dtest='CatalogoDeCenariosTest,CenarioApiTest,SalaApiTest'` → `COMPILATION ERROR` (`CatalogoDeCenarios` inexistente).
- [ ] **Passo 3: implementar as assinaturas acima** e atualizar `.claude/handoff/contrato-api.md` no repositório principal.
- [ ] **Passo 4: GREEN.** `./mvnw -q verify` → `BUILD SUCCESS`; `SalasTest`/`ConcorrenciaTest` só com o argumento novo (`git diff`).
- [ ] **Passo 5: commit** `feat(room): sala guarda o cenário e o pedido aceita "cenario"`.

### Task B4: Remove a fachada e migra os testes

**Arquivos:**
- Modificar: `game/{Terraco,Peca,Lugar,Zona,Composicao,Regras}.java`
- Criar teste: `game/PecaDoTerraco.java`, `game/LugarDoTerraco.java` (`ArgumentConverter`s)
- Modificar teste: `game/RegrasTest.java`, `game/IgualdadePorValorTest.java` (B1), `room/ConcorrenciaTest.java`

**Interfaces:**
- Consome: B3 (nada em `room/` usa a fachada).
- Produz: as definições das constantes passam para o `Terraco` (`CENTRO = new Zona("centro")`, `C1 = new Lugar("C1", CENTRO)`, `TELESCOPIO = new Peca("telescopio", A, CENTRO, true)`…). Saem: constantes, `values()`, `deId`, `de`, `secundariasDe`, `da`, `valueOf` de `Peca`/`Lugar`/`Zona`; `Composicao.faltam()`/`completa()`; as `Regras.f(Estado, …)` sem cenário, `DURACAO_SINAL` e `LIMITE_ANTES_DO_PARCEIRO`.
- `PecaDoTerraco` (`TypedArgumentConverter<String, Peca>`): `Terraco.CENARIO.peca(texto.toLowerCase(Locale.ROOT)).orElseThrow()`; `LugarDoTerraco`: `Terraco.CENARIO.lugar(texto).orElseThrow()`. O texto dos `@CsvSource` não muda.

- [ ] **Passo 1: migrar os testes** (mudança de asserção 3 e o resto mecânico): no `RegrasTest`, `import static …Terraco.*`; `Regras.f(` → `Regras.f(CENARIO, `; `Lugar.da(peca.zona())` → `CENARIO.da(peca.zona())`; `x.faltam()`/`x.completa()` → `CENARIO.faltam(x)`/`CENARIO.completa(x)`; `@ConvertWith(PecaDoTerraco.class)`/`@ConvertWith(LugarDoTerraco.class)` nos parâmetros `Peca`/`Lugar` dos `@CsvSource` (:45-78). No `ConcorrenciaTest` e no `IgualdadePorValorTest`, `Peca.X`/`Lugar.Y` → `Terraco.X`/`Terraco.Y` e `Lugar.C2` → `Terraco.C2`; a chamada sem cenário vira `Regras.lugaresPermitidos(CENARIO, estado, TELESCOPIO)`.
- [ ] **Passo 2: remover a fachada e inverter as constantes.**
- [ ] **Passo 3: GREEN.** `./mvnw -q verify` → `BUILD SUCCESS`, `RegrasTest` com 48. Conferências (saída vazia nas duas):
  - `grep -rnE 'Peca\.(values|deId|de|secundariasDe|valueOf)\(|Lugar\.(values|deId|da|valueOf)\(|Zona\.(CENTRO|LATERAL)|Peca\.[A-Z]{4,}\b|Lugar\.[CLR][12]\b' src/main src/test/java/bipotheking/net/duora/{game,room}` (o `e2e/` tem o seu `TerracoPagina.Peca`/`Lugar`, fora da conta);
  - `grep -n 'Terraco' src/main/java/bipotheking/net/duora/game/{Regras,Composicao}.java`.
  O revisor confere no `git diff` do `RegrasTest` que nenhum valor esperado mudou.
- [ ] **Passo 4: commit** `refactor(game): remove a fachada do terraço fixo`.

### Task F1: Assets do terraço em `assets/cenarios/terraco/`

**Arquivos:**
- Mover (`git mv`): `src/main/resources/static/assets/{cena,ceu,audio}/*` → `src/main/resources/static/assets/cenarios/terraco/{cena,ceu,audio}/` (o `cena.json` fica em `cenarios/terraco/cena/cena.json`)
- Modificar caminhos: `frontend/src/dominio/dados.ts:84,110`, `frontend/src/cena/som.ts:6-8`, `frontend/src/cena/ceu.ts:58`, `frontend/src/estilos/cena.css:170-180`
- Modificar testes (só caminho): `pwa/ShellDoServiceWorkerTest.java:74,99`, `e2e/PwaIT.java:33`, `pwa/AtributosDoGitTest.java:19`, `frontend/src/componentes/Inventario.test.tsx:84`, `frontend/src/sw/rotas.test.ts:32,44,48`
- Pipeline e docs: `assets/source/blender/cena.py:6,32`, `assets/source/ceu/exportar.sh:8`, `assets/source/audio/montar.sh:7`, `.claude/skills/duora-assets/SKILL.md:18-19,59,61`; `assets/manifest.md` (a linha 3 continua certa; conferir as demais); `.claude/handoff/_mapa.md:14` (não versionado, repositório principal)
- Criar teste: `frontend/src/caminhos.test.ts`

**Interfaces:** nenhuma muda; só o literal do caminho. `VARIANTES` continua em `dados.ts` com o mesmo literal (lido por regex em `pwa/Estaticos.java:57-60`).

- [ ] **Passo 1: escrever `caminhos.test.ts`** › "nenhum código usa o caminho antigo de asset": `import.meta.glob("./**/*.{ts,tsx,css}", { query: "?raw", import: "default", eager: true })`, fora `*.test.*`; nenhum conteúdo casa `/assets/(cena|ceu|audio)/`.
- [ ] **Passo 2: RED.** `pnpm test src/caminhos.test.ts` → FAIL citando `dados.ts`, `som.ts`, `ceu.ts`, `cena.css`.
- [ ] **Passo 3: mover e trocar os caminhos.**
- [ ] **Passo 4: GREEN.** `pnpm test` e `pnpm lint` verdes; `./mvnw -q verify` → `BUILD SUCCESS`; `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dit.test='ImagensIT,PwaIT,RevelacaoIT' -Dnavegador=chromium` → `BUILD SUCCESS`.
- [ ] **Passo 5: commit** `refactor(assets): assets do terraço em assets/cenarios/terraco`.

### Task F2: Leitura e carregamento do cenário (valibot)

**Arquivos:**
- Criar: `frontend/src/cenario/{tipos.ts,ler.ts,carregar.ts}`, `frontend/src/cenario/{ler,carregar}.test.ts`, `src/main/resources/static/assets/cenarios/terraco/cenario.json`, `frontend/src/teste/{cenarioDoTerraco.ts,cenarioMinimo.ts}`
- Modificar: `frontend/package.json` + `pnpm-lock.yaml` (valibot em `dependencies`), `frontend/eslint.config.js` (`no-restricted-imports` de `valibot`, com exceção só para `src/cenario/ler.ts`)

**Interfaces:**
- Consome: F1 (pasta nova). `Geometria`, `Momento` e `VARIANTES` seguem em `dominio/dados.ts`.
- Produz (`cenario/tipos.ts`):
  ```ts
  export class ErroCenario extends Error {}
  export type NomeSom = "ambiente" | "meteoro" | "revelacao";
  export type Efeito =
    | { readonly tipo: "brilho-piso"; readonly largura: number }                        // metros
    | { readonly tipo: "halo"; readonly alturaProjetada: number; readonly largura: number };
  export interface PecaDoCenario { readonly nome: string; readonly artigo: string; readonly assento: Assento; readonly zona: string; readonly obrigatoria: boolean }
  export interface LugarDoCenario { readonly nome: string }
  export interface ZonaDoCenario { readonly descricao: string; readonly instrucao: string; readonly checklist: string } // checklist com {nome}
  export interface Legenda { readonly titulo: string; readonly texto: string }
  export interface CenarioFront {
    readonly id: string; readonly nome: string; readonly geometria: Geometria; readonly fundo: string;
    peca(id: string): PecaDoCenario;        // desconhecido → Error(`Peça desconhecida: ${id}`), como hoje
    lugar(id: string): LugarDoCenario;      // → Error(`Lugar desconhecido: ${id}`)
    zona(id: string): ZonaDoCenario;
    sprite(peca: string, lugar: string | null): string; // sem entrada → id; null → semLugar ?? padrao; senão porLugar[lugar] ?? padrao
    urlSprite(nome: string, momento: Momento, largura: number): string; // /assets/cenarios/<id>/cena/<nome>-<momento>-<largura>.webp
    urlCeu(momento: Momento, largura: number): string;                  // /assets/cenarios/<id>/ceu/ceu-<momento>-<largura>.webp
    som(nome: NomeSom): { readonly url: string; readonly volume: number }; // /assets/cenarios/<id>/audio/<nome>.mp3
    revelacao(peca: string): readonly Efeito[];                          // [] sem efeitos
    legenda(resultado: Readonly<Record<string, string>>, nomes: Readonly<Record<Assento, string>>): Legenda; // F3
  }
  ```
- `ler.ts`: `lerCenario(cenario: unknown, geometria: unknown, id: string): CenarioFront`. Esquemas valibot **estritos** (`strictObject`) para os dois JSON; erro → `ErroCenario` cuja mensagem traz o caminho pontuado do primeiro problema (`v.getDotPath`). Conferências cruzadas, cada uma com o caminho: `id` do JSON igual ao pedido; `zona` de cada peça existe em `zonas`; chaves de `sprites` e de `revelacao` são peças; chaves de `porLugar` são lugares; todo lugar tem ponto em `geometria.lugares`; todo nome de sprite de peça (id sem entrada, `padrao`, `porLugar`, `semLugar`) tem medidas em `geometria.props` (o `fundo` não, porque não está no `cena.json`). Nesta task `legenda` lança `Error("legenda: Task F3")`.
- `carregar.ts`: `CENARIO_PADRAO = "terraco"`; `carregarCenario(id: string, buscar: typeof fetch = fetch): Promise<CenarioFront>`: id fora de `^[a-z0-9-]+$` rejeita com `ErroCenario` sem buscar; busca em paralelo `/assets/cenarios/<id>/cenario.json` e `/assets/cenarios/<id>/cena/cena.json`; resposta não-ok → `ErroCenario("cenário <id> indisponível (<status>)")`.
- `cenario.json` do terraço (valores de hoje, de `dados.ts`, `Inventario.tsx:26-28`, `novidade.ts:55-57`, `Falta.tsx:19`, `cena.ts:103-121`, `som.ts:13`, `mensagens.ts:101-116`): `id` `"terraco"`, `nome` `"Terraço das Estrelas Cadentes"`, `fundo` `"terraco"`; `zonas.centro` = `{descricao: "Vai na área de observação, no centro", instrucao: "na área de observação, no centro", checklist: "{nome} no centro"}`, `zonas.lateral` = `{descricao: "Vai em um dos lugares laterais", instrucao: "em um lugar lateral", checklist: ""}`; `lugares` e `pecas` iguais a `LUGARES`/`PECAS` (`obrigatoria` explícito em todas); `sprites.telescopio` = `{padrao: "telescopio-esq", porLugar: {C2: "telescopio-dir"}, semLugar: "telescopio-dir"}`; `revelacao.lanterna` = `[{tipo: "brilho-piso", largura: 2.4}, {tipo: "halo", alturaProjetada: 0.3464, largura: 0.75}]`; `sons` = `{ambiente: {volume: 0.35}, meteoro: {volume: 0.5}, revelacao: {volume: 0.6}}`; `legenda.titulo` = `"Uma noite de {secundariaA.nomeMinusculo} e {secundariaB.nomeMinusculo}"`, `legenda.texto` = `"O telescópio de {nomeA} aponta para o lado {ladoTelescopio} do céu, e é por ali que os meteoros passam. {nomeA} trouxe {secundariaA.artigo}; {nomeB}, o banco e {secundariaB.artigo}."`.
- Fixtures: `teste/cenarioDoTerraco.ts` exporta `jsonsDoTerraco(): { cenario: unknown; geometria: unknown }` (cópias novas a cada chamada, lidas por import `?raw` de `../../../src/main/resources/static/assets/cenarios/terraco/{cenario.json,cena/cena.json}`) e `cenarioDoTerraco(): CenarioFront`. `teste/cenarioMinimo.ts` exporta `CENARIO_MINIMO: CenarioFront` lido de JSON inline: id `"minimo"`, nome `"Mínimo"`, zona `unica` (`"Vai em qualquer lugar"`, `"em qualquer lugar"`, `"{nome} na cena"`), lugares `P1` (`"primeiro lugar"`) e `P2` (`"segundo lugar"`), peças `pedra` (A, `"Pedra"`, `"a pedra"`) e `folha` (B, `"Folha"`, `"a folha"`), obrigatórias, `fundo` `"chao"`, sem sprites nem revelação, legenda `{titulo: "Juntos: {juntos}", texto: "{nomeA} e {nomeB} terminaram."}`, geometria com os pontos de P1/P2 e `props` de `pedra` e `folha`.

- [ ] **Passo 1: instalar o valibot** (versão das Restrições globais) e commitar `build(front): valibot <versão>` (`package.json` + lockfile).
- [ ] **Passo 2: escrever os testes.**
  - `ler.test.ts`: "o cenário do terraço é válido" (id `terraco`, nome `Terraço das Estrelas Cadentes`); "bate com as constantes de hoje" (para cada id de `PECAS`/`LUGARES`, `peca(id)` igual aos dados com `obrigatoria: dados.obrigatoria === true`, e `lugar(id)` igual; rede de segurança para apagá-las na F5); "falta de artigo aponta o caminho" (sem `pecas.manta.artigo` → `ErroCenario` contendo `pecas.manta.artigo`); "chave desconhecida é recusada" (`pecas.manta.cor` → contém `pecas.manta`); "sprite de peça inexistente é recusado" (`sprites.vaso`); "zona inexistente é recusada" (`pecas.manta.zona = "teto"` → contém `pecas.manta.zona`); "lugar sem ponto no cena.json é recusado" (sem `geometria.lugares.C1` → contém `C1`); "sprite sem medidas é recusado" (sem `props["telescopio-dir"]` → contém `telescopio-dir`); "id diferente do pedido é recusado"; "peça desconhecida lança como hoje" (`peca("vaso")` → `"Peça desconhecida: vaso"`); `sprite("telescopio", "C2")` → `"telescopio-dir"`, `("telescopio", "C1")` → `"telescopio-esq"`, `("telescopio", null)` → `"telescopio-dir"`, `("banco", "L1")` → `"banco"`; `urlSprite("banco", "noite", 900)` → `"/assets/cenarios/terraco/cena/banco-noite-900.webp"`; `urlCeu("crepusculo", 1280)` → `"/assets/cenarios/terraco/ceu/ceu-crepusculo-1280.webp"`; `som("meteoro")` → `{url: "/assets/cenarios/terraco/audio/meteoro.mp3", volume: 0.5}`; `revelacao("lanterna")` igual aos dois efeitos e `revelacao("banco")` → `[]`; "o cenário mínimo é lido".
  - `carregar.test.ts`: "id com barra não busca" (`carregarCenario("../x", buscar)` rejeita `ErroCenario`, `buscar` não chamado); "busca os dois JSON do cenário" (chamado com `/assets/cenarios/terraco/cenario.json` e `/assets/cenarios/terraco/cena/cena.json`); "404 vira ErroCenario com id e status" (mensagem contém `terraco` e `404`).
- [ ] **Passo 3: RED.** `pnpm test src/cenario` → FAIL (módulos inexistentes).
- [ ] **Passo 4: implementar as assinaturas acima, o JSON, as fixtures e a regra do ESLint.**
- [ ] **Passo 5: GREEN.** `pnpm test`, `pnpm lint` e `pnpm build` verdes; um `import * as v from "valibot"` de teste em outro arquivo faz o `pnpm lint` falhar (conferir e desfazer).
- [ ] **Passo 6: commit** `refactor(front): cenário lido e conferido de cenario.json e cena.json`.

### Task F3: Legenda do cenário

**Arquivos:**
- Criar: `frontend/src/cenario/legenda.ts`, `frontend/src/cenario/legenda.test.ts`
- Modificar: `frontend/src/cenario/ler.ts` (`legenda` delega)

**Interfaces:**
- Consome: F2.
- Produz: `interpolar(modelo: string, valores: Readonly<Record<string, string>>, c: Pick<CenarioFront, "peca">): string` (exportado; a 9b acrescenta campos). Marcadores: `{chave}` → `valores[chave]`; `{chave.campo}` com `campo` ∈ `nome | nomeMinusculo | artigo` → campo de `c.peca(valores[chave])` (`nomeMinusculo` = `nome.toLowerCase()`, como hoje); chave ou campo desconhecido → `ErroCenario("marcador desconhecido: {…}")`. `legenda(resultado, nomes)` interpola `titulo` e `texto` com `{...resultado, nomeA: nomes.A, nomeB: nomes.B}`.

- [ ] **Passo 1: escrever `legenda.test.ts`** (casos de `mensagens.test.ts:112-148`): "legenda do terraço igual a hoje" (`{secundariaA: "manta", secundariaB: "lanterna", ladoTelescopio: "leste"}`, Ana/Bruno → título `"Uma noite de manta e lanterna"` e texto `"O telescópio de Ana aponta para o lado leste do céu, e é por ali que os meteoros passam. Ana trouxe a manta; Bruno, o banco e a lanterna."`); "rádio e garrafa" (`oeste` → `"… Ana trouxe o rádio; Bruno, o banco e a garrafa térmica."`); "marcador desconhecido lança" (`"{xyz}"`); "campo desconhecido lança" (`"{secundariaA.cor}"`); "legenda do cenário mínimo" (`CENARIO_MINIMO.legenda({juntos: "sim"}, {A: "Ana", B: "Bruno"})` → `{titulo: "Juntos: sim", texto: "Ana e Bruno terminaram."}`).
- [ ] **Passo 2: RED.** `pnpm test src/cenario/legenda.test.ts` → FAIL.
- [ ] **Passo 3: implementar `interpolar` e `legenda`.**
- [ ] **Passo 4: GREEN.** `pnpm test` e `pnpm lint` verdes.
- [ ] **Passo 5: commit** `refactor(front): legenda do cenário por marcadores`.

### Task F4: App inicia pelo cenário e mostra o erro na tela

**Arquivos:**
- Criar: `frontend/src/inicio.tsx`, `frontend/src/inicio.test.tsx`, `frontend/src/componentes/ErroDeCenario.tsx`, `frontend/src/cenario/contexto.ts`, `frontend/src/teste/comCenario.tsx`, `e2e/CenarioFrontIT.java`
- Modificar: `frontend/src/main.tsx`, `frontend/src/App.tsx` (sai `useGeometria`, :39-62), `frontend/src/sessao/sessao.ts` (`OpcoesDaSessao`), `frontend/src/componentes/Cena.tsx` (geometria não nula), testes de `App`/`useSessao`/`Cena` (só a fixture), `e2e/AxeIT.java`

**Interfaces:**
- Consome: F2, F3.
- Produz:
  - `cenario/contexto.ts`: `ProvedorDeCenario` (= `Context.Provider` de `CenarioFront`); `useCenario(): CenarioFront` (fora do provedor → `Error("useCenario fora do ProvedorDeCenario")`).
  - `teste/comCenario.tsx`: `renderComCenario(ui: ReactElement, c: CenarioFront = cenarioDoTerraco())`.
  - `inicio.tsx`: `iniciarApp(raiz: HTMLElement, convite: string | null, carregar: (id: string) => Promise<CenarioFront> = carregarCenario): Promise<void>`: chama `carregar(CENARIO_PADRAO)`; sucesso → `createRoot(raiz).render(<StrictMode><App convite={convite} cenario={c} /></StrictMode>)`; falha → renderiza `<ErroDeCenario />` e registra o erro com `console.error`. Até lá o esqueleto do `index.html` fica na tela.
  - `main.tsx`: `void iniciarApp(raiz, lerConvite())`; `registrarServiceWorker()` continua.
  - `ErroDeCenario`: `Moldura` (crepúsculo, sem céu pintado) com o `Topo` sem "Sair" e, no painel, `<p role="alert">Não foi possível carregar o cenário. Recarregue a página.</p>`.
  - `App({ convite, cenario }: { convite: string | null; cenario: CenarioFront })`: envolve com `ProvedorDeCenario`; inicia a sessão num efeito (`sessao.iniciar()`, limpeza `sessao.parar()`); `geometria={cenario.geometria}`.
  - `OpcoesDaSessao.cenario: CenarioFront` (usado na F5).
  - `Cena`: `geometria: Geometria` (o ramo `geometria === null` sai).

- [ ] **Passo 1: escrever os testes.**
  - `inicio.test.tsx` (com `vi.stubGlobal("fetch", () => new Promise(() => {}))`): "cenário que falha mostra o alerta" (`carregar` rejeita → `getByRole("alert")` com o texto acima e `getByRole("heading", { name: "Antes da primeira estrela" })`); "cenário carregado monta o app" (`carregar` resolve `cenarioDoTerraco()` → chamado com `"terraco"` e `raiz.querySelector(".app")` presente).
  - `CenarioFrontIT.cenarioInexistenteMostraErroNaTela`: `participantes.novo(page -> page.route("**/assets/cenarios/terraco/cenario.json", r -> r.fulfill(new Route.FulfillOptions().setStatus(404))))` → `alert` com o texto e o heading visíveis (estilo do `PrimeiraPinturaIT`).
  - `AxeIT.erroDeCenarioSemViolacoes` (tela nova; mesma rota 404).
- [ ] **Passo 2: RED.** `pnpm test src/inicio.test.tsx` → FAIL (`inicio.tsx` inexistente).
- [ ] **Passo 3: implementar as assinaturas acima** e dar a fixture aos testes existentes, sem mudar asserção.
- [ ] **Passo 4: GREEN.** `pnpm test`, `pnpm lint`; `./mvnw -q verify` → `BUILD SUCCESS` (registrar o valor de `target/tamanho-js-inicial.txt`, já com o valibot); `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dit.test='CenarioFrontIT,AxeIT,PrimeiraPinturaIT,JornadaDoisNavegadoresIT' -Dnavegador=chromium` → `BUILD SUCCESS`.
- [ ] **Passo 5: commit** `feat(front): app espera o cenário e mostra erro quando ele não carrega`.

### Task F5: Textos pelo cenário

**Arquivos:**
- Modificar: `frontend/src/dominio/mensagens.ts`, `frontend/src/sessao/novidade.ts`, `frontend/src/sessao/sessao.ts` (toda chamada a `novidade`, `instrucao` e `dadosDaPeca`, :226 e :258), `frontend/src/componentes/{Inventario,Falta,Resultado}.tsx`, `frontend/src/dominio/dados.ts` (apagar `PECAS`, `LUGARES`, `DadosPeca`, `DadosLugar`, `dadosDaPeca`, `dadosDoLugar`), `frontend/src/cenario/ler.test.ts` ("bate com as constantes de hoje" passa a valores literais)
- Testes (só o parâmetro ou o provedor): `mensagens.test.ts`, `novidade.test.ts`, `sessao.test.ts`, `Inventario.test.tsx`, `Falta.test.tsx`, `Painel.test.tsx`; `e2e/CenarioFrontIT.java`

**Interfaces:**
- Consome: F4 (`useCenario`, `OpcoesDaSessao.cenario`, `renderComCenario`).
- Produz: `textoResultado(c: CenarioFront, visao: Visao)` (→ `c.legenda(visao.resultado, { A: nome no assento A, B: nome no assento B })`, mesmas exceções de hoje); `textoPecasDoParceiro(c, anterior, visao)`; `novidade(c, …)`, `instrucao(c, …)` (onde = `c.zona(peca.zona).instrucao`); `linhasDaFalta(c: CenarioFront, visao: Visao)` (obrigatória por `c.peca().obrigatoria`, linha = `interpolar(c.zona(zona).checklist, { nome }, c)`; "Peça central", "Sua segunda peça" e "Segunda peça de X" ficam no código); `Inventario` descreve a zona por `c.zona(zona).descricao`. Componentes leem `useCenario()`; funções puras recebem `c`.

- [ ] **Passo 1: escrever os testes novos.** `Inventario.test` › "cenário mínimo usa os nomes dele" (`renderComCenario` com `CENARIO_MINIMO` e visão com `pedra`/`folha` → item `Pedra` com `Vai em qualquer lugar`); `Falta.test` › "checklist vem da zona do cenário" (`CENARIO_MINIMO` → linha `Pedra na cena`); `CenarioFrontIT.legendaDoFinalVemDoCenario` (rota que faz `route.fetch()` do `cenario.json` e troca `legenda.texto` por `"Teste {nomeA}"`; passos de `Cenarios.entrarEmPar`/`comporTerraco` e a confirmação dos dois, como no `RevelacaoIT` → `#resultado-texto` com `"Teste Ana"`).
- [ ] **Passo 2: RED.** `pnpm test src/componentes` → FAIL (os componentes ainda leem `PECAS`).
- [ ] **Passo 3: implementar** e passar `c` aos testes existentes, sem mudar texto esperado.
- [ ] **Passo 4: GREEN.** `pnpm test`, `pnpm lint`, `pnpm build`; `grep -rn "dadosDaPeca\|PECAS\|LUGARES" frontend/src` vazio; `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dit.test='CenarioFrontIT,JornadaDoisNavegadoresIT,AcessibilidadeFocoIT' -Dnavegador=chromium` → `BUILD SUCCESS`.
- [ ] **Passo 5: commit** `refactor(front): textos de peças, lugares e legenda vindos do cenário`.

### Task F6: Cena, som e céu pelo cenário

**Arquivos:**
- Modificar: `frontend/src/cena/{cena.ts,som.ts,ceu.ts}`, `frontend/src/componentes/{Cena,Inventario}.tsx`, `frontend/src/App.tsx`, `frontend/src/dominio/dados.ts` (apagar `sprite`, `urlSprite`, `carregarCena`; ficam `VARIANTES`, `escolherVariante`, `LARGURA_MINIATURA` e os tipos), `frontend/src/dominio/dados.test.ts` (sai o `describe("sprite")`, coberto em `ler.test.ts`)
- Criar teste: `frontend/src/cena/{cena,som,ceu}.test.ts`; `Cena.test.tsx` só com a fixture

**Interfaces:**
- Consome: F5.
- Produz: `OpcoesCena.cenario: CenarioFront` (sprites por `c.sprite`/`c.urlSprite`; efeitos por `c.revelacao(p.peca)` no lugar de `p.peca === "lanterna"`, com o `brilho-piso` antes do botão da peça e o `halo` depois, como hoje em `cena.ts:103-121,187-190`); `mostrarTerraco(palco: HTMLElement, c: CenarioFront): number` (fundo por `c.urlSprite(c.fundo, momento, variante)`, variantes de `VARIANTES`); `arquivosDoSom(c: CenarioFront): Record<NomeSom, { url: string; volume: number }>` e `criarSom(c: CenarioFront): Som`; `urlsDoCeu(c: CenarioFront, dpr: number): string[]` e `prepararCeuPintado(c: CenarioFront): Promise<boolean>`; `Inventario` com miniatura `c.urlSprite(c.sprite(peca.peca, peca.lugar), "crepusculo", LARGURA_MINIATURA)`. O `cena.css` não muda (R14).

- [ ] **Passo 1: escrever os testes.** `cena.test.ts` › "lanterna ganha brilho antes e halo depois do botão" (`criarCena` com o terraço e a lanterna em R1 → no `#palco`, `.brilho` antes de `button[data-peca="lanterna"]`, que vem antes de `.halo`; `halo.style.width` = `((pxPorMetro * 0.75 / largura) * 100).toFixed(3) + "%"`) e "peça sem efeito não ganha brilho" (`CENARIO_MINIMO`); `som.test.ts` › "volumes e URLs vêm do cenário" (`arquivosDoSom(terraço).ambiente` → `{url: "/assets/cenarios/terraco/audio/ambiente.mp3", volume: 0.35}`, idem `meteoro` 0,5 e `revelacao` 0,6); `ceu.test.ts` › "pré-carrega a largura do dpr" (`urlsDoCeu(c, 1)` → crepúsculo e noite em 1280; `urlsDoCeu(c, 2)` → 2560) e "cena.css usa as mesmas URLs do céu do terraço" (`import css from "../estilos/cena.css?raw"` contém `c.urlCeu(m, l)` para `m` ∈ crepúsculo/noite e `l` ∈ 1280/2560).
- [ ] **Passo 2: RED.** `pnpm test src/cena` → FAIL.
- [ ] **Passo 3: implementar.**
- [ ] **Passo 4: GREEN.** `pnpm test`, `pnpm lint`, `pnpm build`; `./mvnw -q verify` → `BUILD SUCCESS`; `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dit.test='ImagensIT,RevelacaoIT,PwaIT' -Dnavegador=chromium` → `BUILD SUCCESS`.
- [ ] **Passo 5: commit** `refactor(front): cena, som e céu lidos do cenário`.

### Task F7: Integração com o back-end

**Arquivos:**
- Modificar: `frontend/src/dominio/visao.ts:43-47`, `frontend/src/sessao/sessao.ts:149`, `frontend/src/sessao/sessao.test.ts`
- Criar teste: `src/test/java/bipotheking/net/duora/game/CenarioJsonTest.java`

**Interfaces:**
- Consome: B4 (`Terraco.CENARIO` e acessores) e F6.
- Produz: `export type VisaoResultado = Readonly<Record<string, string>>`; `ladoTelescopio: noite ? (estado.visao?.resultado?.["ladoTelescopio"] ?? null) : null`.

- [ ] **Passo 1: escrever os testes.**
  - `CenarioJsonTest` (lê `src/main/resources/static/assets/cenarios/terraco/cenario.json` com o `JsonMapper` já usado no projeto, `tools.jackson.databind.json.JsonMapper`): `idENomeIguaisAoCenario`; `pecasIguaisAoCenario` (chaves de `pecas` = ids de `Terraco.CENARIO.pecas()`; para cada uma, `assento` = `dono().name()`, `zona` = `zona().id()`, `obrigatoria` = `obrigatoria()`); `lugaresIguaisAoCenario` (chaves de `lugares` = ids de `lugares()`).
  - `sessao.test` › "resultado sem ladoTelescopio não mostra meteoros" (visão `REVELADO` com `resultado: { juntos: "sim" }` → `obter().ladoTelescopio` nulo).
- [ ] **Passo 2: RED.** `pnpm test src/sessao/sessao.test.ts` → FAIL de tipo/valor; `./mvnw -q test -Dtest=CenarioJsonTest` → falha só se o espelho divergir (conferir quebrando um id de propósito e desfazer).
- [ ] **Passo 3: implementar.**
- [ ] **Passo 4: GREEN.** `pnpm test`, `pnpm lint`; `./mvnw -q verify` → `BUILD SUCCESS`.
- [ ] **Passo 5: commit** `test(game): espelho do cenario.json conferido contra o Terraco` e `refactor(front): resultado da visão genérico`.

### Task D1: Spec, contratos, planos seguintes, glossário e ADR

**Arquivos:**
- Modificar: `docs/superpowers/specs/2026-11-21-duora-social-design.md` (S:99, S:146), `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md` (seção nova "## Cenários", depois de "Assets e ferramentas"), `docs/superpowers/plans/2026-11-22-etapa-{1c-ajustes-playtest,4-salas-pareamento,5-encontro,9b-cenarios}.md`
- Criar: `CONTEXT.md` (raiz), `docs/adr/0003-motor-de-cenarios.md`

**Interfaces:** consome os nomes fixados em B1–B3 e F2–F3; não muda código.

- [ ] **Passo 1: spec.** S:99 → "`game/`: regras puras dos cenários (motor da Etapa 1b)". S:146 → "O front lê a apresentação de cada cenário (`cenario.json`) e a geometria (`cena/cena.json`) em vez de constantes; os assets ficam em `assets/cenarios/<id>/` (`cena/`, `ceu/`, `audio/`)." Na seção "Backlog de cenários", acrescentar: "Nome de exibição do terraço: **Terraço das Estrelas Cadentes**. O nome de cada cenário novo é decidido pelo dono depois de construí-lo (Etapa 9b), sem padrão único." No plano da 9b, a mesma regra como passo de cada cenário.
- [ ] **Passo 2: contratos, seção "Cenários"**, uma linha por item: caminho canônico e URL (R7); id permanente `^[a-z0-9-]+$`, ids de peça e lugar estáveis; `game.Cenario` (com `nome`), `Desfecho` puro e determinístico, `Resultado.valores()` ordenado; `Regras.*(Cenario, …)` com `Cenario` primeiro em toda função pública; `game.CatalogoDeCenarios` (`porId`, `todos`) montado por `room/CenariosConfig` (um `@Bean Cenario` por cenário); `POST /api/salas {"cenario"}` (padrão `"terraco"`), `422 CENARIO_INVALIDO`, `Visao.cenario`; front: valibot só em `cenario/ler.ts`, esquema estrito, `CenarioFront` com métodos, `revelacao` = peça → `Efeito[]` (1b: `brilho-piso`, `halo`); `CenarioJsonTest` confere o espelho.
- [ ] **Passo 3: emendas pontuais nos planos.**
  - 1c: `Regras.tocarAbertura(Cenario, Estado, Assento, Instant)` e `avancarAbertura(Cenario, Estado, Instant)` (P1c:421); Task 4 e Task da abertura: `esquema.ts` → `cenario/ler.ts`; a chave `revelacao` já existe, a 1c acrescenta `meteoros | brilho | vapor | tremular | notas` à união `Efeito`, e a lanterna mantém `brilho-piso` e `halo` e ganha `tremular`.
  - 5: `conexao.dominio.Desfecho` → `DesfechoDaDecisao` em todas as ocorrências (P5:249, P5:258 e usos); o catálogo fica em `game/` quando `room/` vira `encontro/`.
  - 4: `room.CatalogoDeCenarios` → `game.CatalogoDeCenarios` (P4:47, P4:172); `sorteaveis()` recebe a lista pelo `CenariosConfig`, que lê `duora.cenarios.sorteaveis` (o catálogo continua sem Spring).
  - 9b: catálogo em `game/` (P9b:496); registrar cenário = `@Bean Cenario` no `CenariosConfig` (P9b:1031); `esquema.ts`/`parseCenario` → `cenario/ler.ts`/`lerCenario` (P9b:381, 390, 1034).
- [ ] **Passo 4: `CONTEXT.md`** (skill `mattpocock-skills:domain-modeling`): só o glossário de `.claude/handoff/plano-1b/contratos.md` §3 (termo, definição, _Evitar_), com as ambiguidades resolvidas: Desfecho (Strategy; a Etapa 5 usa `DesfechoDaDecisao`), Sala (hoje `room/Sala`, futuro `Encontro`), efeito × `revelacao`.
- [ ] **Passo 5: ADR 0003** (Contexto, Decisão, Consequências, Alternativas, no formato do `0004-front-react.md`): `Cenario` como dados e `Desfecho` como Strategy; `Regras` estática com `Cenario` primeiro (instância por cenário rejeitada); enums → records; catálogo em `game/` por configuração (`room/`→`encontro/` e módulo `cenario/` rejeitados pelo ciclo `sala ↔ encontro` no ArchUnit); `cenario.json` espelho + `cena.json` geometria, conferidos pelo `CenarioJsonTest`; valibot confinado a `ler.ts` (leitura manual considerada); id permanente; regras no JSON rejeitadas; aviso do homônimo `Desfecho`.
- [ ] **Passo 6: verificar.** `grep -n "assets/cena/<cenario>\|regras puras do terraço" docs/superpowers/specs/2026-11-21-duora-social-design.md` vazio; `grep -rn "room.CatalogoDeCenarios\|parseCenario\|tocarAbertura(Estado" docs/superpowers/plans` vazio.
- [ ] **Passo 7: commits** `docs: motor de cenários na spec, nos contratos, nos planos seguintes e no glossário` e `docs(adr): 0003 motor de cenários`.

---

## Ordem, paralelismo e divisão para execução

**Grafo de dependências:**
```
B1 → B2 → B3 → B4 ───────────────┐
F1 → F2 → F3 → F4 → F5 → F6 ─────┴→ F7 → prova final → revisão final → main
             D1 (depois de B3 e F3) ──────────┘
```

**Implementadores (no máximo 3 simultâneos, cada um no seu worktree):**
- **Trilha back (1 implementador):** B1 → B2 → B3 → B4, em sequência.
- **Trilha front (1 implementador):** F1 → F2 → F3 → F4 → F5 → F6, em sequência; F7 depois de B4 e F6 integrados. F2 depende de F1 porque a fixture lê o `cena.json` na pasta nova; F5 e F6 tocam os mesmos arquivos (`Inventario.tsx`, `dados.ts`), por isso também em sequência.
- **Docs (1 implementador):** D1 começa quando B3 e F3 estiverem integradas (os nomes que os planos seguintes consomem já existem).
- As trilhas back e front tocam arquivos disjuntos (back: `game/`, `room/` e seus testes; front: `frontend/`, `static/assets/`, `pwa/`, `e2e/`), então rodam em paralelo desde o início.

**Worktrees e integração:**
- Branch de integração `etapa-1b`, criada da `main` depois da 1a integrada. Cada task numa branch `etapa-1b-<task>` (ex.: `etapa-1b-b2`), em worktree próprio, criada da ponta atual de `etapa-1b` depois que a task anterior da mesma trilha foi integrada.
- Worktree novo: um `./mvnw -q initialize` (sob a trava) instala Node, pnpm e as dependências em `frontend/` antes do primeiro `pnpm`.
- Fluxo por task: implementador (TDD, commit) → revisor (`superpowers:requesting-code-review` + `mattpocock-skills:code-review`, com o diff da task e este plano) → correções → `git merge --no-ff` na `etapa-1b` → `./mvnw -q verify` na `etapa-1b` (sob a trava) → remover o worktree.
- Pontos de merge que destravam outras trilhas: **B3 + F3** → D1; **B4 + F6** → F7; **F7 + D1** → prova final.
- Maven sempre sob a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` e `caffeinate -dims`, um por vez na máquina. `pnpm` só nas tasks F (Vitest e lint leves, fora da trava; `pnpm build` sob a trava quando outro Maven estiver rodando).

**Prova final (na `etapa-1b`, tudo integrado, um comando pesado por vez):**
1. `./mvnw -q verify` → `BUILD SUCCESS` (unidade, API, Vitest pelo plugin, `TamanhoDoJsInicialTest`). Registrar o valor de `target/tamanho-js-inicial.txt`: ≤ 90 KB obrigatório, comparado com a meta de 80 KB e com os 81,7 KB da 1a.
2. E2E nos três navegadores, um de cada vez: `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dnavegador=chromium`, depois `firefox`, depois `webkit` → `BUILD SUCCESS` em cada um (inclui `CenarioFrontIT` e `AxeIT`).
3. Comparação visual: `./mvnw -q -Pe2e verify -DskipUnitTests=true -Dit.test=VisualIT -Dvisual.saida=<repo principal>/.claude/handoff/visual-1b/depois` e `./mvnw -q test -Dtest=ComparacaoVisualTest -Dvisual.antes=<repo principal>/.claude/handoff/visual-1a/depois -Dvisual.depois=<repo principal>/.claude/handoff/visual-1b/depois` → verde (limite da R7 da 1a; se falhar, corrige-se o código, nunca o limite).
4. Revisão final da branch inteira (`main..etapa-1b`) com `superpowers:requesting-code-review` + `mattpocock-skills:code-review`; correções em commits próprios e a prova repetida no que elas tocarem.
5. `superpowers:finishing-a-development-branch`: merge na `main`, `git push`, CI verde (`gh run watch`). Depois, `duora-etapa` (handoff e `_mapa.md`: pasta `assets/cenarios/`, `CatalogoDeCenarios`, `CenariosConfig`).
