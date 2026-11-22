# Etapa 1b — Motor de cenários: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** as regras, a API e o front deixam de ter o terraço fixo no código. O terraço passa a ser o primeiro `Cenario`, sem nenhuma mudança de comportamento, e um cenário mínimo de teste roda de ponta a ponta pela API.

**Arquitetura:**
- `Peca`, `Lugar` e `Zona` deixam de ser enums e viram records identificados por `id` de texto. O formato na rede não muda (`"manta"`, `"L2"`).
- Um `Cenario` reúne lugares, peças e parâmetros de regra. O final de cada cenário é uma estratégia `Desfecho` em código.
- Um `CatalogoDeCenarios` (bean Spring) resolve o cenário pelo id.
- No front, os textos e efeitos de cada cenário vêm de `/cenarios/<id>/cenario.json`. As regras continuam no servidor.

**Stack:** Java 25, Spring Boot 4.2.0, front React + TypeScript + Tailwind (Etapa 1a), Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 1b). Pré-requisito: a Etapa 1 integrada na `main`, porque o `cena.json` já terá `variantes`.

## Restrições globais
- Valem as da Etapa 0 e da Etapa 1: Conventional Commits com trailer, Maven pesado um por vez (trava `mkdir /private/tmp/claude-501/duora-pesado.lock`), `caffeinate -dims`, `-Werror`, Spotless, E2E sem skip nos três navegadores, pt-BR.
- **Comportamento idêntico para o terraço.** Os testes existentes só podem mudar mecanicamente (imports, construtores, o caminho de um asset), nunca nas asserções:
  - RegrasTest (48);
  - SalaApiTest (47);
  - ConcorrenciaTest (5);
  - os 8 ITs e2e.
- **JSON da API compatível.** Nenhum campo existente muda de nome nem de tipo, e campos novos só são acrescentados. Isso inclui `resultado.secundariaA`, `resultado.secundariaB` e `resultado.ladoTelescopio` no terraço.
- O cenário padrão é `"terraco"` quando o pedido não informar outro.

## Foco de revisão
1. **Pedido com `cenario` desconhecido:** `422 CENARIO_INVALIDO`, sem criar sala. Coberto na Task 2.
2. **Peça ou lugar de outro cenário enviado à sala do terraço** (por exemplo, um id do cenário mínimo): recusado como id inválido, igual a hoje. Coberto na Task 2.
3. **`cenario.json` ausente ou inválido no front:** mensagem de erro na tela, nunca uma tela branca. Coberto na Task 3.
4. **Ordem estável:** as peças e os lugares saem na mesma ordem de hoje na `Visao`, porque o front e os testes dependem dela. Coberto pelo SalaApiTest inalterado.
5. **Regra do portão e troca de secundária no cenário mínimo:** valem pelos parâmetros do cenário, não por constantes. Coberto na Task 1.

---

### Task 1: Tipos genéricos, `Cenario`, `Desfecho` e o terraço em dados

**Arquivos:**
- Modificar: `src/main/java/bipotheking/net/duora/game/{Peca,Lugar,Zona,Composicao,Regras,Resultado}.java`
- Criar: `game/Cenario.java`, `game/Desfecho.java`, `game/Terraco.java` (constantes, cenário e desfecho do terraço)
- Remover: `game/Lado.java` (vira um valor do desfecho do terraço)
- Teste: `src/test/java/bipotheking/net/duora/game/RegrasTest.java` (só mecânico), `game/CenarioMinimoTest.java` (novo), `game/CenarioMinimo.java` (fixture de teste)

**Interfaces (produz):**
- `record Zona(String id)`.
- `record Lugar(String id, Zona zona)`.
- `record Peca(String id, Assento dono, Zona zona, boolean obrigatoria)`, com `boolean aceita(Lugar l)` (mesma zona).
- `record Cenario(String id, List<Lugar> lugares, List<Peca> pecas, int limiteAntesDoParceiro, Duration duracaoSinal, int pecasPorPessoaCompleta, Desfecho desfecho)`, com:
  - `Optional<Peca> peca(String id)` e `Optional<Lugar> lugar(String id)`;
  - `List<Lugar> da(Zona z)`, na ordem de `lugares`.
- `interface Desfecho { Resultado de(Composicao c, Cenario cenario); }`.
- `record Resultado(Map<String, String> valores)`, com ordem de inserção preservada (`LinkedHashMap` copiado para imutável).
- `final class Terraco`:
  - `Cenario CENARIO`;
  - constantes `TELESCOPIO`, `BANCO`, `MANTA`, `RADIO`, `LANTERNA`, `GARRAFA` e `C1`, `C2`, `L1`, `L2`, `R1`, `R2`, todas com os mesmos ids, donos e zonas de hoje;
  - valores atuais: `limiteAntesDoParceiro` 1, `duracaoSinal` 20 s, `pecasPorPessoaCompleta` 2;
  - o desfecho devolve as chaves `secundariaA`, `secundariaB` e `ladoTelescopio`, com os mesmos valores de hoje.
- `Regras`: toda função pública ganha `Cenario` como primeiro parâmetro, por exemplo `colocar(Cenario c, Estado e, Assento quem, Peca peca, Optional<Lugar> destino, long versaoEsperada)`. Também `Estado.inicial()` e as funções de `Composicao` que hoje usam `values()`.

- [ ] **Passo 1:** migrar o `RegrasTest` mecanicamente.
  - `import static ...Terraco.*` e `Terraco.CENARIO` nas chamadas.
  - Rodar e conferir que **não compila**: esse é o RED estrutural.
- [ ] **Passo 2:** escrever o `CenarioMinimoTest` com a fixture `CenarioMinimo`.
  - A fixture tem 2 lugares (`A`, `B` na zona `unica`), uma peça obrigatória por pessoa (`pedra` de A, `folha` de B), `limiteAntesDoParceiro` 1, `pecasPorPessoaCompleta` 1 e um desfecho que devolve `{"juntos": "sim"}`.
  - `colocarPrimeiraPecaAceita`;
  - `segundaPecaAntesDoParceiroERecusadaPeloPortao` (só se aplicável com 1 peça; senão `confirmarAntesDeCompletarERecusado`);
  - `composicaoCompletaRevelaODesfechoDoCenario`: `resultado.valores()` igual a `{"juntos": "sim"}`;
  - `idDeOutroCenarioNaoEPecaDesteCenario`: `CenarioMinimo.CENARIO.peca("manta")` vazio.
- [ ] **Passo 3:** implementar os tipos e o `Terraco`. As constantes de regra saem de `Regras` e `Composicao` para o `Cenario`. O `resultado()` atual vira o `Desfecho` do terraço.
- [ ] **Passo 4:** rodar o `RegrasTest` (48 verdes, sem mudar asserção) e o `CenarioMinimoTest`. O `room/` ainda não compila; seguir para a Task 2 no mesmo worktree, porque as duas tasks compartilham a compilação. Commit `refactor(game): cenário como dados e terraço como primeiro cenário`.

### Task 2: Catálogo e sala com cenário

**Arquivos:**
- Criar: `room/CatalogoDeCenarios.java` (bean com `Map<String, Cenario>` e `Optional<Cenario> porId(String id)`; registra `Terraco.CENARIO`)
- Modificar: `room/{Sala,Salas,Retrato,SalaController,Visao,Pedidos,Codigo}.java`
- Teste: `room/{SalaApiTest,ConcorrenciaTest,SalasTest}.java` (só mecânico), `room/CenarioApiTest.java` (novo, com `@TestConfiguration` que registra o `CenarioMinimo`)

**Interfaces:**
- Consome: Task 1 (`Cenario`, `Terraco`, `Regras` com `Cenario`).
- Produz:
  - `Pedidos.CriarSala(String apelido, @Nullable String cenario)`: o campo é declarado, e a rejeição de campos desconhecidos continua.
  - `Codigo.CENARIO_INVALIDO` (422).
  - `Visao` ganha `"cenario": "<id>"`. As peças e os lugares vêm do cenário da sala, na ordem dele.
  - `VisaoResultado` passa a ser serializado a partir de `Resultado.valores()`, com os mesmos campos JSON no terraço.

- [ ] **Passo 1:** escrever o `CenarioApiTest`.
  - `criarSemCenarioUsaOTerraco`: `visao.cenario == "terraco"` e 6 peças.
  - `criarComCenarioDesconhecidoRecusa`: 422 com `codigo` `CENARIO_INVALIDO`, e nenhuma sala criada (`Salas` com contagem zero).
  - `cenarioMinimoDePontaAPonta`: criar com `"cenario":"minimo"`, entrar, colocar as duas peças, confirmar os dois e ler `resultado.juntos == "sim"`.
  - `pecaDeOutroCenarioNaSalaDoTerracoERecusada`: `{"peca":"pedra"}` recebe o mesmo status e código de uma peça inexistente hoje.
- [ ] **Passo 2:** RED. A sala ainda não conhece cenário.
- [ ] **Passo 3:** implementar.
  - A `Sala` guarda o `Cenario` e delega a `Regras` com ele.
  - O `SalaController` resolve ids pelo cenário da sala.
  - A `Visao` usa `cenario.pecas()` e `lugares()`.
  - `Sala.java:188`: o texto "O terraço mudou…" vira "A cena mudou…" só se nenhum teste depender dele. Se algum depender, manter o texto.
- [ ] **Passo 4:** rodar a suíte unitária e de API inteira, verde e com as asserções intactas. Commit `feat(room): sala com cenário e catálogo de cenários`.

### Task 3: Front guiado por `cenario.json` (React)

Reescrita para o front da Etapa 1a (Vite + React + TS). Pré-requisito: a 1a integrada.

Skills: `mattpocock-skills:codebase-design` antes de fixar o formato do `CenarioFront` e o provedor (duas alternativas, com aval do controlador para mudar as Interfaces); `mattpocock-skills:tdd` na implementação; `superpowers:verification-before-completion` antes de cada commit. Princípio aplicado: **"parse, don't validate"**. O JSON vira um `CenarioFront` válido uma vez, na fronteira, e nenhum consumidor revalida ids.

**Arquivos:**
- Criar: `src/main/resources/static/assets/cenarios/terraco/cenario.json`
- Mover (com `git mv`):
  - `static/assets/cena/*` → `static/assets/cenarios/terraco/cena/*`;
  - `static/assets/ceu/*` → `static/assets/cenarios/terraco/ceu/*`;
  - `static/assets/audio/*` → `static/assets/cenarios/terraco/audio/*`.
  - Ajustar o destino em `assets/source/blender/cena.py` e em `assets/source/ceu/exportar.sh`, e os caminhos em `assets/manifest.md`.
- Criar: `frontend/src/cenario/{esquema.ts,carregar.ts,apresentacao.ts,legenda.ts,ProvedorDeCenario.tsx,LimiteDeErro.tsx}` e os testes `esquema.test.ts`, `carregar.test.ts`, `legenda.test.ts`, `apresentacao.test.ts`
- Modificar, trocando constantes por leitura do cenário:
  - `frontend/src/dominio/{visao.ts,dados.ts,mensagens.ts}`;
  - `frontend/src/sessao/{novidade.ts,sessao.ts}`;
  - `frontend/src/cena/{cena.ts,som.ts,ceu.ts}`;
  - `frontend/src/componentes/{Cena,Falta,Inventario,Resultado}.tsx`, `frontend/src/App.tsx`;
  - `frontend/src/estilos/cena.css`: as URLs do céu viram as variáveis `--ceu-crepusculo` e `--ceu-noite`, definidas pelo `App` via a prop `style` (CSSOM, permitido pela CSP).
- Modificar só no mecânico (caminho de asset):
  - `e2e/PwaIT.java` (`CENA_JSON`);
  - `pwa/ShellDoServiceWorkerTest.java` (o caminho do `cena.json` e o prefixo dos sprites).
- Teste: `src/test/java/bipotheking/net/duora/game/CenarioJsonTest.java` (novo) e `src/test/java/bipotheking/net/duora/e2e/CenarioFrontIT.java` (novo)

**Interfaces:**
- Consome:
  - `Visao.cenario` (Task 2). Antes da sessão, ou enquanto a Task 2 não entrar, o id é `"terraco"` (`visao?.cenario ?? "terraco"`);
  - `api`, `Sessao` e componentes da Etapa 1a.
- Produz:
  - `dominio/visao.ts`: `Visao` ganha `cenario?: string`.
  - `cenario.json` do terraço, com os mesmos textos e valores de hoje:
    - `id`; `geometria` (URL do `cena.json`); `spritesBase` (pasta dos sprites);
    - `pecas`: id → `{ nome, artigo, assento, zona, obrigatoria }`;
    - `lugares`: id → `{ nome }`;
    - `zonas`: id → `{ descricao, instrucao, checklist }`, com os textos de `descreverZona`, de `instrucao()` e de `desenharFalta`:
      - `centro`: "Vai na área de observação, no centro" / "na área de observação, no centro" / "{nome} no centro";
      - `lateral`: "Vai em um dos lugares laterais" / "em um lugar lateral".
    - `legenda`: `{ titulo, texto }` com marcadores. O terraço usa:
      - `"titulo": "Uma noite de {secundariaA.nomeMinusculo} e {secundariaB.nomeMinusculo}"`;
      - no `texto`, `{nomeA}`, `{nomeB}`, `{ladoTelescopio}`, `{secundariaA.artigo}` e `{secundariaB.artigo}`, reproduzindo a frase atual de `textoResultado`.
    - `efeitos`: peça → lista de efeitos da cena, em metros (× `pxPorMetro`). A lanterna tem `{ "tipo": "brilho-piso", "largura": 2.4 }` e `{ "tipo": "halo", "alturaProjetada": 0.3464, "largura": 0.75 }`, iguais a `cena.ts` (0,4 m × 0,866).
    - `sprites`: peça → `{ padrao, porLugar?, miniatura? }`. O telescópio tem `padrao "telescopio-esq"`, `porLugar { "C2": "telescopio-dir" }` e `miniatura "telescopio-dir"` (o inventário de hoje usa a variante de C2).
    - `ceu`: `{ base, larguras: [1280, 2560] }`;
    - `sons`: `ambiente`, `meteoro` e `revelacao`, cada um `{ url, volume }` com os volumes de hoje (0,35, 0,5 e 0,6).
  - `cenario/esquema.ts`:
    - o esquema `EsquemaCenario` em **valibot** (1.x, versão exata com ≥ 14 dias, cerca de 2 KB gzip);
    - `parseCenario(json: unknown): CenarioFront`, que lança `ErroCenario`. É a única porta de entrada.
    - Além da forma, confere as referências cruzadas:
      - cada chave de `sprites` e `efeitos` é uma peça;
      - cada chave de `porLugar` é um lugar;
      - cada `zona` de peça existe em `zonas`.
    - Devolve os ids com marca (`type IdPeca = string & { readonly __marca: "IdPeca" }`, idem `IdLugar`), para que as funções de `apresentacao.ts` só aceitem ids que vieram de um cenário já lido.
    - O tipo `CenarioFront` sai do esquema (`v.InferOutput` mais as marcas), para o tipo e a validação nunca divergirem.
  - `cenario/carregar.ts`:
    - `class ErroCenario extends Error`;
    - `carregarCenario(id: string, buscar = fetch): Promise<CenarioFront>`:
      - o `id` precisa casar `^[a-z0-9-]+$`, senão rejeita sem buscar;
      - busca `/assets/cenarios/<id>/cenario.json` (a rota de JSON em `/assets/` do service worker já é "rede primeiro");
      - uma resposta não-ok vira `ErroCenario("cenário <id> indisponível (<status>)")`;
      - um JSON fora do esquema vira `ErroCenario` com o caminho do primeiro problema (ex.: `pecas.manta.artigo`), via `parseCenario`.
  - `cenario/apresentacao.ts`: `nomeDaPeca(c, id)`, `artigoDaPeca(c, id)`, `spriteDe(c, peca, lugar | null)`, `miniaturaDe(c, peca)` e `urlSprite(c, nome, momento, largura)`. Substituem `PECAS`, `LUGARES`, `sprite` e `urlSprite` de `dados.ts`. `escolherVariante` e `VARIANTES` ficam em `dados.ts`, porque a lista de larguras é do pipeline, não do cenário.
  - `cenario/legenda.ts`: `interpolar(modelo: string, contexto: Record<string, string>, c: CenarioFront): string`.
    - `{chave}` vira o valor do contexto;
    - `{chave.campo}`, com `campo` em `nome`, `nomeMinusculo` ou `artigo`, lê a peça cujo id é o valor da chave;
    - um marcador desconhecido lança `ErroCenario`.
  - `cenario/ProvedorDeCenario.tsx`:
    - `ProvedorDeCenario({ id, children })` lê `use(promessaDoCenario(id))`, com cache de promessa por id. `Suspense` usa como fallback o esqueleto estático da 1a, e o `LimiteDeErro` envolve tudo;
    - `useCenario(): CenarioFront`.
  - `cenario/LimiteDeErro.tsx`: um componente de classe. Com erro, mostra a `Moldura` com o título e, no painel, `<p role="alert">Não foi possível carregar o cenário. Recarregue a página.</p>`. Nunca uma tela branca.
  - As funções de `mensagens.ts` e `novidade.ts` que hoje leem `PECAS`/`LUGARES` recebem `c: CenarioFront` como primeiro parâmetro, sem mudar nenhum texto.

- [ ] **Passo 1:** escrever os testes.
  - `esquema.test.ts`:
    - `cenarioDoTerracoEValido`: lê o `cenario.json` real do disco (`../src/main/resources/static/assets/cenarios/terraco/cenario.json`) e o valida;
    - `faltaDeArtigoApontaOCaminho`: sem `pecas.manta.artigo` → `ErroCenario` cuja mensagem contém `pecas.manta.artigo`;
    - `spriteDePecaInexistenteERecusado`: `sprites.vaso` → `ErroCenario` contendo `sprites.vaso`;
    - `zonaInexistenteERecusada`.
  - `carregar.test.ts`:
    - `idComBarraNaoBusca`: `carregarCenario("../x", buscar)` rejeita, e `buscar` não é chamado;
    - `respostaQuatrocentosEQuatroViraErroCenario`.
  - `legenda.test.ts`:
    - `legendaDoTerracoIgualAHoje`: manta e lanterna, Ana (A) e Bruno (B), lado esquerdo → o título e o texto exatos de hoje;
    - `marcadorDesconhecidoLanca`.
  - `apresentacao.test.ts`: `spriteDe(terraco, "telescopio", "C2")` → `"telescopio-dir"`; `miniaturaDe(terraco, "telescopio")` → `"telescopio-dir"`; `urlSprite(...)` → `/assets/cenarios/terraco/cena/banco-noite-900.webp`.
  - `CenarioJsonTest` (Java, unidade): os ids, as zonas, os donos e o `obrigatoria` das peças, e os ids dos lugares do `cenario.json` do terraço, são iguais aos de `Terraco.CENARIO`. Isso impede que o espelho de apresentação diverja das regras.
  - `CenarioFrontIT`:
    - `cenarioInexistenteMostraErroNaTela`: rota que devolve 404 para `**/cenarios/terraco/cenario.json` → o `alert` com o texto acima, e o heading "Antes da primeira estrela" visível;
    - `legendaDoFinalVemDoCenario`: rota que troca `legenda.texto` por "Teste {nomeA}" → depois de revelar, `#resultado-texto` é "Teste Ana".
- [ ] **Passo 2:** RED: `pnpm test` e `-Dit.test=CenarioFrontIT` falham.
- [ ] **Passo 3:** mover os assets, criar o JSON e implementar os módulos. Trocar as constantes pelos dados do cenário, sem mudar texto, imagem nem efeito.
- [ ] **Passo 4:** rodar `./mvnw -q verify` (com o `CenarioJsonTest`, o `ShellDoServiceWorkerTest` e o `TamanhoDoJsInicialTest` ainda ≤ 80 KB), a suíte e2e completa em chromium, e o `CenarioFrontIT` + `AxeIT` em firefox e webkit, tudo verde.
- [ ] **Passo 5:** comparação visual. As capturas "depois" da 1a viram o "antes" desta task:
  - `VisualIT` em `.../visual-1b/depois`;
  - `ComparacaoVisualTest` contra `.../visual-1a/depois`, verde.
  - Commits separados:
    - `refactor(assets): assets do terraço em assets/cenarios/terraco` (só o `git mv` e os caminhos, com a suíte verde);
    - `refactor(front): cenário lido de um JSON por cenário` (o comportamento é idêntico, provado pela comparação visual).

## Ordem e paralelismo
- **Tasks 1 e 2:** em sequência, no **mesmo** worktree, por um único implementador, porque só compilam juntas. O revisor recebe as duas.
- **Task 3:** começa depois da Etapa 1a integrada. Roda em paralelo às Tasks 1–2, noutro worktree, a partir do mesmo ponto. Até a Task 2 entrar, ela trata a ausência de `Visao.cenario` como `"terraco"`. Só a Task 3 usa `pnpm`; o Maven pesado segue um por vez.
- **Depois:**
  - merge das três e a suíte inteira (unidade, Vitest e e2e nos 3 navegadores);
  - ADR `docs/adr/0003-motor-de-cenarios.md` (Contexto, Decisão, Consequências, Alternativas), commit `docs(adr): 0003 motor de cenários`. Registrar:
    - o `Cenario` como dados e o `Desfecho` como Strategy por cenário;
    - o catálogo;
    - o `cenario.json` como espelho de apresentação, conferido pelo `CenarioJsonTest`;
    - a alternativa de regras no próprio JSON, rejeitada porque as regras ficam no servidor;
  - revisão final (`superpowers:requesting-code-review` + `mattpocock-skills:code-review`), merge na `main`, push e CI.
