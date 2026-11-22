# Etapa 1b — Motor de cenários: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** as regras, a API e o front deixam de ter o terraço fixo no código. O terraço passa a ser o primeiro `Cenario`, sem nenhuma mudança de comportamento, e um cenário mínimo de teste roda de ponta a ponta pela API.

**Arquitetura:**
- `Peca`, `Lugar` e `Zona` deixam de ser enums e viram records identificados por `id` de texto. O formato na rede não muda (`"manta"`, `"L2"`).
- Um `Cenario` reúne lugares, peças e parâmetros de regra. O final de cada cenário é uma estratégia `Desfecho` em código.
- Um `CatalogoDeCenarios` (bean Spring) resolve o cenário pelo id.
- No front, os textos e efeitos de cada cenário vêm de `/cenarios/<id>/cenario.json`. As regras continuam no servidor.

**Stack:** Java 25, Spring Boot 4.1.3, JS nativo, Playwright Java 1.65.1.

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

### Task 3: Front guiado por `cenario.json`

**Arquivos:**
- Criar: `static/cenarios/terraco/cenario.json`, `static/js/cenario.js` (carrega e valida)
- Mover: `static/assets/cena/*` → `static/assets/cenarios/terraco/cena/*` e `static/assets/ceu/*` → `static/assets/cenarios/terraco/ceu/*`. Ajustar o destino em `assets/source/blender/cena.py` e em `assets/source/ceu/exportar.sh`.
- Modificar: `static/js/{dados,cena,mensagens,painel,main,som}.js`, `index.html` (caminhos das imagens), `css/app.css` (URLs do céu)
- Teste: os ITs e2e existentes (só caminhos, se algum citar asset), mais `e2e/CenarioFrontIT.java` (novo)

**Interfaces:**
- Consome: `Visao.cenario` (Task 2). Antes de existir sessão, o padrão é `"terraco"`.
- Produz, em `cenario.json` do terraço:
  - `id`;
  - `pecas` (`id` → `nome`, `artigo`, `zonaFalada`);
  - `lugares` (`id` → `nome`);
  - `zonas` (textos do checklist e de `main.js:221`);
  - `legenda` (o modelo de `mensagens.js:99-105`, com marcadores `{secundariaA}`, `{secundariaB}` e `{nomeA}`);
  - `efeitos` (o brilho e o halo da lanterna, hoje em `cena.js:26-42`, com os mesmos multiplicadores);
  - `sprites` (variantes por lugar, ex.: o telescópio `-esq`/`-dir` por C2);
  - `ceu` (URLs) e `sons` (URLs e volumes de `som.js:5-10`).
- `cenario.js`: `export async function carregarCenario(id: string): Promise<CenarioFront>`. Com JSON ausente ou inválido, lança `ErroCenario`, que o `main.js` mostra na tela de erro existente.

- [ ] **Passo 1:** escrever o `CenarioFrontIT`.
  - `cenarioInexistenteMostraErroNaTela`: rota que devolve 404 para `/cenarios/terraco/cenario.json`; aparece a tela de erro com texto em pt-BR, e o `body` não fica vazio.
  - `legendaDoFinalVemDoCenario`: rota que troca a `legenda` do JSON por "Teste {nomeA}"; depois de revelar, a legenda mostra "Teste Ana".
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar, extraindo para o JSON as constantes listadas acima, sem mudar texto, imagem nem efeito.
- [ ] **Passo 4:** rodar a suíte e2e completa em chromium, depois o `CenarioFrontIT` e o `AxeIT` em firefox e webkit, tudo verde. Conferir, comparando as capturas de `JornadaDoisNavegadoresIT` antes e depois (`target/e2e-capturas`, diff visual por pixel), que o visual não mudou. Commit `feat(web): front lê o cenário de um JSON por cenário`.

## Ordem e paralelismo
- **Tasks 1 e 2:** em sequência, no **mesmo** worktree, por um único implementador, porque só compilam juntas. O revisor recebe as duas.
- **Task 3:** em paralelo, noutro worktree, a partir do mesmo ponto. Até a Task 2 entrar, ela trata a ausência de `Visao.cenario` como `"terraco"`.
- **Depois:** revisão final, merge na `main`, push e CI.
