# 0003 — Motor de cenários: regras em Java, apresentação em JSON

- **Estado:** aceita
- **Data:** 2026-11-22
- **Etapa:** 1b

## Contexto

Até a Etapa 1a, o terraço era o único cenário e estava fixo no código: enums `Peca`, `Lugar` e `Zona` em `game/`, constantes de regra em `Regras` e, no front, textos, sprites e posições em constantes TypeScript. As etapas seguintes constroem em cima de vários cenários: a 1c acrescenta abertura e efeitos por peça, a 4 sorteia um cenário que o par ainda não jogou e grava o id em `cenario_jogado`, a 5 grava o id em `encontro` e relê encontros abertos pelo banco, e a 9b acrescenta aquário, piquenique, cachoeira, jantar e fliperama.

Era preciso separar o que é regra (servidor, autoridade) do que é apresentação (front), sem mudar nada do que o terraço mostra nem do JSON da API, e com a suíte existente verde.

## Decisão

- **`Cenario` como dados, `Desfecho` como Strategy.** `game.Cenario` é um record (`id`, `nome`, `lugares`, `pecas`, `limiteAntesDoParceiro`, `duracaoSinal`, `pecasPorPessoaCompleta`, `desfecho`, `abertura`). Componente novo entra no fim, com um construtor secundário que delega com o valor neutro; a 1c acrescentou `abertura` (`Optional<DefinicaoDeAbertura>`, vazio por padrão) e o construtor de 8 argumentos. O que varia de verdade entre cenários, a passagem da composição completa para o resultado, é a interface `Desfecho` (`Resultado de(Composicao, Cenario)`), pura e determinística: sem relógio, sorteio nem estado. Cada cenário é uma classe no molde do `Terraco`, com as constantes e um `Cenario CENARIO`.
- **Enums viram records.** `Peca`, `Lugar` e `Zona` passam a records com id de texto, porque cada cenário tem os seus. A comparação passa a ser por valor (`equals`), nunca por referência.
- **`Regras` continua estática, com `Cenario` como 1º parâmetro** em toda função pública (`Regras.colocar(Cenario, Estado, …)`), inclusive as que etapas seguintes criarem (`tocarAbertura`, `avancarAbertura` na 1c).
- **`Resultado.valores()` preserva a ordem de inserção** (`Collections.unmodifiableMap(new LinkedHashMap<>(…))`): o `ETag` da sala é o SHA-256 dos bytes do JSON, e chaves em ordem instável mudariam o `ETag` para o mesmo estado.
- **Id do cenário permanente.** O id casa `^[a-z0-9-]+$`, é validado na construção do `Cenario` e nunca é renomeado nem removido; os ids de peça e de lugar também ficam estáveis, porque vão para o banco nas Etapas 4 e 5.
- **Catálogo em `game/`, montado por configuração.** `game.CatalogoDeCenarios` é Java puro (`porId`, `todos`) e recebe a lista de cenários pelo construtor. Quem o monta é `room/CenariosConfig`, com um `@Bean Cenario` por cenário; registrar um cenário novo é acrescentar um bean. O catálogo fica em `game/` mesmo quando `room/` virar `encontro/` na Etapa 5.
- **Apresentação em dois arquivos por cenário**, em `static/assets/cenarios/<id>/`: `cenario.json` (escrito à mão: nome, textos, sprites, sons, peças, lugares, zonas, legenda e a chave `revelacao`, peça → `Efeito[]`) e `cena/cena.json` (geometria gerada pelo `cena.py`). O `cenario.json` é um espelho do `Cenario` do servidor, e o `CenarioJsonTest` confere os dois (id, nome, peças com dono, zona e obrigatoriedade, lugares). O front carrega os dois em paralelo e confere um contra o outro.
- **valibot confinado a `frontend/src/cenario/ler.ts` e `frontend/src/api/perfil.ts`.** O primeiro lê o cenário; o segundo valida as respostas da API de perfil, num chunk lazy. Nenhum outro módulo o importa, e a guarda do ESLint permite só esses dois. A leitura do cenário usa esquema estrito e devolve um `CenarioFront` com métodos (`peca`, `sprite`, `som`, `revelacao`, `legenda`…); nenhum consumidor lê o JSON cru.
- **Homônimo `Desfecho`.** O nome fica com a Strategy dos cenários; o enum da decisão pós-encontro da Etapa 5 passa a se chamar `DesfechoDaDecisao`.

## Consequências

- Um cenário de montagem novo é uma classe em `game/`, um bean no `CenariosConfig`, um `cenario.json` e os assets; o front não ganha código por cenário.
- Duas fontes descrevem o mesmo cenário (o `Cenario` e o `cenario.json`). A duplicação é aceita em troca de manter as regras só no servidor, e o `CenarioJsonTest` falha se elas divergirem.
- `Regras` estática mantém as chamadas simples e sem estado, mas toda assinatura carrega o `Cenario`; trocar para instância depois seria uma substituição mecânica nas chamadas e nos planos 1c, 4 e 9b.
- O id permanente impede renomear um cenário depois de lançado; o `nome` de exibição pode mudar à vontade.
- O valibot soma bytes ao JS inicial, e o cenário entra por import estático (o shell do service worker não alcança um `import()` dinâmico); o orçamento continua guardado pelo `TamanhoDoJsInicialTest`.
- Quem ler o código da Etapa 5 encontra `game.Desfecho` e `conexao.dominio.DesfechoDaDecisao`, com nomes distintos de propósito.

## Alternativas consideradas

- **Regras no JSON** (lugares, zonas, portão e desfecho declarados no `cenario.json` e interpretados pelo servidor e pelo front): uma fonte só, mas exigiria uma linguagem de regras para o desfecho, tiraria a autoridade do código tipado e testado do servidor e espalharia a regra pelo front. Rejeitada.
- **`Regras` como instância por cenário** (`new Regras(cenario)`): esconde o parâmetro, mas cria um objeto por sala ou um mapa de instâncias e muda todas as chamadas sem ganho de comportamento. Rejeitada.
- **Catálogo em `room/`** (e depois em `encontro/`): na Etapa 4 o módulo `sala/` precisa do catálogo para sortear, e na 5 `encontro/` consome `sala/`; com o catálogo em `encontro/`, surge o ciclo `sala ↔ encontro`, que a ArchUnit proíbe. Rejeitada.
- **Módulo próprio `cenario/`** para o catálogo: evita o ciclo, mas cria um módulo só para uma classe que depende apenas de `game/`. Rejeitada; `game/` já é o dono do modelo.
- **Leitura manual do JSON no front** (sem biblioteca, com guardas escritas à mão): zero bytes de dependência e foi a recomendação inicial, mas o dono escolheu o valibot. Ficou o valibot, confinado a um arquivo, para a troca continuar barata.
