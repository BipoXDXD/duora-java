# Etapa 9b — Cenários novos (aquário, piquenique, cachoeira, jantar e fliperama): plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** cinco cenários novos sorteáveis no pareamento. Os quatro de composição (aquário, piquenique, cachoeira, jantar) rodam no motor da 1b/1c, cada um com seus assets, dados, testes e playtest por agente. O fliperama é um jogo cooperativo original em Godot 4, embutido por iframe e sincronizado por WebSocket no Spring.

**Arquitetura:**
- **Composição:** cada cenário é uma classe `game/<Cenario>.java` no molde do `Terraco` (1b): constantes, `Cenario CENARIO` e um `Desfecho` (Strategy). O `cenario.json` é o espelho de apresentação, e o `CenarioJsonTest` confere os dois.
- **Apresentação:** a Task 2 estende o esquema da 1b/1c com quatro tipos de efeito, `variacoes` (efeitos que dependem de um valor do desfecho), `textos` (frase por valor) e `titulo`. Assim, cada cenário novo é só dados, sem código de front próprio.
- **Assets:** o `cena.py` vira um driver genérico com um módulo por cenário (Task 1). Render, export e manifesto seguem a skill `duora-assets`.
- **Fliperama:**
  - "Dois faróis": cada pessoa gira um farol, e um barco só acha o porto quando as duas luzes o cobrem juntas.
  - Servidor autoritativo: a simulação é pura em Java, num relógio de 20 Hz.
  - O WebSocket cru (`/ws/fliperama`) leva as entradas com `seq` e devolve retratos com `tick`.
  - O cliente Godot só desenha e envia a mira. É servido em `/jogos/fliperama/` com CSP própria, num iframe de mesma origem.

**Stack:** Java 25, Spring Boot 4.2.0 (+ `spring-boot-starter-websocket`), React 19 + TS + Tailwind 4 + valibot (1a/1b), Playwright Java 1.65.1, Godot 4 (versão estável exata fixada no spike, com ≥ 14 dias) pelo MCP `Godot-AI`, Blender 5.3 headless, `cwebp`, `ffmpeg`, `brotli`; Hi3D, Tripo e ElevenLabs pelos MCPs.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 9b, Backlog de cenários, Etapas 1b e 1c). Contratos: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md` (valem sobre este plano). Planos de base, pelos blocos Interfaces: `2026-11-22-etapa-1b-motor-de-cenarios.md`, `2026-11-22-etapa-1c-ajustes-playtest.md`, `2026-11-22-etapa-1a-front-react.md`, e `-etapa-2-contas.md`, `-etapa-4-salas-pareamento.md`, `-etapa-5-encontro.md` para conta, sorteio e encontro.

**Skills por momento:**
- `superpowers:brainstorming` já foi aplicado aos designs abaixo (escolhas delegadas pelo dono);
- `mattpocock-skills:prototype` no spike do fliperama (Task 15);
- `mattpocock-skills:codebase-design` antes das Tasks 2, 18 e 20;
- `mattpocock-skills:tdd` na implementação;
- `duora-assets` em toda task de asset;
- `superpowers:verification-before-completion` antes de cada commit e `duora-etapa` ao fechar.

## Restrições globais
- Valem as das Etapas 0–1c e os contratos:
  - Conventional Commits em pt-BR com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`;
  - `-Xlint:all -Werror`, Spotless, lint do front com `--max-warnings 0`;
  - E2E sem skip nos 3 navegadores, `AxeIT` em toda tela ou estado novo;
  - JS inicial ≤ 80 KB gzip (`TamanhoDoJsInicialTest`).
- **Um pesado por vez:** Maven, Blender, export do Godot, compilação de template, Docker e emulador usam a trava `mkdir /private/tmp/claude-501/duora-pesado.lock` (e `rmdir` ao fim) e `caffeinate -dims`. Gerações na nuvem (Hi3D, Tripo, ElevenLabs) não pegam a trava.
- **Formatos (sem exceção):** imagem em WebP q88 (props em 900/1800/2700; fundos em 1280/2560), áudio em MP3 192 kbps / 48 kHz. Masters em `assets/source/`. Cada asset tem linha em `assets/manifest.md` no mesmo commit, com status `Candidato a FINAL`; só o dono promove a `FINAL`.
- **Geração:** 3D no Hi3D (assinatura, imagem Nano-Banana 2 → 3D v3.0). Fallback pago só pela decisão D2. 2D no Tripo `chat_image_2.5_sunburst` 4K, com `tripo_plan` coberto pela decisão D1. Áudio no ElevenLabs: uma chamada por som, `generations_count` no padrão, nunca repetir a chamada para tentar de novo.
- **Engine imutável para o terraço:** as Tasks 1 e 2 não mudam nenhuma asserção existente. O terraço continua idêntico, provado por comparação de capturas e por SSIM dos renders.
- **Formato dos cenários de composição (igual ao terraço, validado no playtest):**
  - cada pessoa tem 1 peça obrigatória (zona de 2 lugares) e 2 opcionais (zona de 4 lugares), das quais vai 1;
  - `limiteAntesDoParceiro` 1, `duracaoSinal` 20 s, `pecasPorPessoaCompleta` 2.
  - Destaque, sinal com destino e acordo para ajustar vêm do motor (1c) e não precisam de código por cenário.
- **Momentos:** os ids técnicos continuam `crepusculo` (montagem) e `noite` (revelação), porque o motor (1a/1b) e os testes dependem deles. Cada cenário dá a sua arte a cada momento.
- **Texto:** pt-BR, voz ativa, gênero neutro para pessoas. Os textos fixados neste plano são usados exatamente como escritos.
- **Liberação:** um cenário só entra em `duora.cenarios.sorteaveis` depois do "sim" do dono (decisão D4). Antes disso, ele fica no catálogo e só é alcançável em teste, playtest ou pelo convite privado com a propriedade de teste.
- **Fliperama:**
  - a CSP muda só em `/jogos/fliperama/**`;
  - o resto do app mantém a CSP atual (sem `unsafe-inline`/`unsafe-eval`);
  - a prova é: abre em ≤ 5 s em cada uma de 5 execuções com cache frio, no emulador Android com 4G emulado (WebPageTest "4G": 9 Mbps ↓ / 9 Mbps ↑, RTT 170 ms) e CPU 4× mais lenta.

## Foco de revisão
1. **`cenario.json` divergindo do `Cenario` Java** (id, zona, dono, obrigatória, lugar): o `CenarioJsonTest` parametrizado por todo cenário do catálogo falha. Coberto na Task 2.
2. **Valor do desfecho sem texto ou sem variação** (erro de digitação em `textos`/`variacoes`, ou valor que o `Desfecho` nunca produz): `todoValorDoDesfechoTemTextoEVariacao` e `nenhumTextoMorto` percorrem todas as composições completas. Coberto na Task 2.
3. **WebSocket barrado pela CSP no WebKit** (`connect-src` cai em `default-src 'self'`): o `FliperamaIT` roda nos 3 navegadores e falha com qualquer violação de CSP no console. Coberto na Task 20.
4. **Celular troca de rede no meio da rodada:** a reconexão substitui a sessão antiga, continua o `seq`, e a rodada fica pausada enquanto um assento está desconectado. Coberto por `reconexaoSubstituiASessaoAntigaEContinuaOSeq` (Task 18), `parceiroDesconectadoPausaARodada` (Task 18) e `reconectaComEsperaCrescente` (Task 19).
5. **Encontro encerrado (saída, denúncia, 30 min) com o jogo aberto:** os sockets fecham com 4100 em ≤ 2 s, e o iframe some junto com a tela do cenário. Coberto por `encontroEncerradoFechaCom4100` (Task 18) e `saidaDuranteORodadaEncerraParaOsDois` (Task 20).

---

## Designs dos cenários de composição

Os quatro seguem o terraço: criação a dois, sem placar, revelação no fim. O desenho de cada um cuida de três coisas:
- o desfecho depende das escolhas **das duas pessoas** e das **posições** (gancho de negociação para o sinal com destino);
- cada peça tem um efeito próprio na revelação;
- há pelo menos 4 títulos ou textos de final diferentes.

Coordenadas em metros, no piso da cena (x lateral, y profundidade, z altura, com padrão 0). São o ponto de partida do módulo Blender; o `cena.json` registra o resultado do render. Cores dos efeitos em hex.

### D-AQ. Aquário (`aquario`)
- **Título:** "Luz no aquário". Montagem: tanque com a luz apagada. Revelação: a luz acende e o cardume nada.
- **Zonas e lugares:**
  - `areia` (obrigatórias):
    - `A1` "na areia, à esquerda" (−0,55; 0,15);
    - `A2` "na areia, à direita" (0,55; 0,15).
  - `cantos` (opcionais):
    - `E1` "atrás, à esquerda" (−1,35; −0,45);
    - `E2` "na frente, à esquerda" (−1,2; 0,45);
    - `D1` "atrás, à direita" (1,35; −0,45);
    - `D2` "na frente, à direita" (1,2; 0,45).
  - Textos das zonas: `areia` "Vai na areia, no meio do tanque" / "na areia, no meio do tanque" / "{nome} na areia"; `cantos` "Vai em um dos cantos do tanque" / "em um canto do tanque".
- **Peças:**
  - A: `rocha` (obrigatória, "Rocha", "a rocha"), `alga` ("Alga", "a alga"), `concha` ("Concha", "a concha");
  - B: `tronco` (obrigatória, "Tronco", "o tronco"), `coral` ("Coral", "o coral"), `anfora` ("Ânfora", "a ânfora").
- **Desfecho:** `secundariaA`, `secundariaB`, `luz`, `cardume`.
  - `luz` pela combinação das secundárias: alga+coral `esmeralda`; alga+ânfora `turquesa`; concha+coral `rosada`; concha+ânfora `dourada`.
  - `cardume`:
    - as duas secundárias em `E*` → `esquerda`;
    - as duas em `D*` → `direita`;
    - senão, `dividido`.
- **Textos:**
  - `luz`: esmeralda "uma luz verde-esmeralda", turquesa "uma luz turquesa", rosada "uma luz rosada", dourada "uma luz dourada";
  - `cardume`: esquerda "o cardume se juntou no canto esquerdo", direita "o cardume se juntou no canto direito", dividido "o cardume se dividiu entre os dois cantos".
- **Legenda:** título "Um aquário de {secundariaA.nomeMinusculo} e {secundariaB.nomeMinusculo}"; texto "{nomeA} e {nomeB} acenderam {luz.texto}, e {cardume.texto}."
- **Efeitos por peça:**
  - rocha: `particulas` bolha, sobe, 6;
  - tronco: `brilho` #9BE3C1, 1,0;
  - alga: `tremular`;
  - concha: `brilho` #F3E6D0, 0,6;
  - coral: `brilho` #F29BB0, 0,8;
  - ânfora: `particulas` bolha, sobe, 3.
- **Variações:**
  - `luz` → `tom` com opacidade 0,18: esmeralda #3FB28A, turquesa #3BB8C4, rosada #E58FA8, dourada #E8B65A;
  - `cardume`:
    - esquerda → `cardume` peixe, 7, âncora esquerda;
    - direita → o mesmo com âncora direita;
    - dividido → dois `cardume` peixe de 3 cada, âncoras esquerda e direita.
- **Relações** (`raioPerto` 0,9):
  - alga–rocha "A alga cresceu encostada na rocha.";
  - coral–tronco "O coral se agarrou ao tronco.";
  - concha–rocha "A concha descansa ao pé da rocha.";
  - anfora–tronco "A ânfora ficou deitada junto ao tronco, como um achado.";
  - alga–coral "Alga e coral lado a lado: um jardim só.";
  - concha–anfora "Concha e ânfora juntas parecem um tesouro.";
  - alga–anfora "A alga abraça a ânfora.";
  - concha–coral "Concha e coral dividem o mesmo canto."
- **Gancho de negociação:** quem quer o cardume junto precisa levar a secundária para o mesmo lado da do outro. O sinal com destino é o jeito de pedir.

### D-PQ. Piquenique (`piquenique`)
- **Título:** "Tarde no gramado". Montagem: começo da tarde. Revelação: fim de tarde dourado, com o clima e um visitante.
- **Zonas e lugares:**
  - `toalha` (obrigatórias):
    - `T1` "na toalha, à esquerda" (−0,6; 0,1);
    - `T2` "na toalha, à direita" (0,6; 0,1).
  - `grama` (opcionais):
    - `G1` "à sombra da árvore" (−2,6; −1,1);
    - `G2` "na grama, à esquerda" (−1,9; 0,9);
    - `G3` "na grama, à direita" (1,9; 0,9);
    - `G4` "na beira do lago" (2,6; −1,1).
  - Textos das zonas: `toalha` "Vai na toalha, no meio do gramado" / "na toalha" / "{nome} na toalha"; `grama` "Vai em um lugar da grama em volta" / "na grama em volta".
- **Peças:**
  - A: `cesta` (obrigatória, "Cesta", "a cesta"), `pipa` ("Pipa", "a pipa"), `violao` ("Violão", "o violão");
  - B: `jarra` (obrigatória, "Jarra de limonada", "a jarra de limonada"), `guarda-sol` ("Guarda-sol", "o guarda-sol"), `bicicleta` ("Bicicleta", "a bicicleta").
- **Desfecho:** `secundariaA`, `secundariaB`, `clima`, `visitante`.
  - `clima`: pipa+guarda-sol `brisa`; pipa+bicicleta `ventania`; violão+guarda-sol `sol`; violão+bicicleta `entardecer`.
  - `visitante`:
    - `G4` ocupado → `patos`;
    - senão, `G1` ocupado → `passarinhos`;
    - senão, `borboletas`.
- **Textos:**
  - `clima`: brisa "uma brisa firme", ventania "um vento brincalhão", sol "um sol morno", entardecer "um entardecer cor de pêssego";
  - `visitante`: patos "os patos do lago vieram espiar", passarinhos "passarinhos desceram da árvore", borboletas "borboletas rodearam a toalha".
- **Legenda:** título "Um piquenique de {secundariaA.nomeMinusculo} e {secundariaB.nomeMinusculo}"; texto "{nomeA} e {nomeB} ganharam {clima.texto}, e {visitante.texto}."
- **Efeitos por peça:**
  - cesta: `vapor`;
  - jarra: `brilho` #F7E37A, 0,6;
  - pipa: `tremular`;
  - violão: `notas` (`violao-notas.mp3`, 0,4);
  - guarda-sol: `brilho` #FFE9B0, 1,4;
  - bicicleta: `particulas` faisca, sobe, 4.
- **Variações:**
  - `clima`:
    - brisa → `particulas` folha, deriva, 8;
    - ventania → `particulas` folha, deriva, 16;
    - sol → `tom` #F5C26B, 0,12;
    - entardecer → `tom` #F2A07B, 0,18.
  - `visitante`:
    - patos → `cardume` pato, 3, âncora `lugar:G4`;
    - passarinhos → `cardume` passarinho, 4, âncora `lugar:G1`;
    - borboletas → `cardume` borboleta, 5, âncora centro.
- **Relações** (`raioPerto` 1,5):
  - cesta–jarra "A cesta e a jarra ficaram lado a lado: lanche pronto.";
  - pipa–cesta "A pipa descansa junto da cesta, esperando o vento.";
  - violao–jarra "O violão ficou perto da limonada, para as pausas.";
  - guarda-sol–cesta "O guarda-sol faz sombra na cesta.";
  - bicicleta–jarra "A bicicleta ficou à mão, perto da limonada.";
  - violao–guarda-sol "Violão na sombra do guarda-sol: música sem pressa.";
  - pipa–bicicleta "Pipa e bicicleta juntas: alguém vai correr hoje.";
  - violao–bicicleta "O violão encostado na bicicleta pede uma volta com trilha."
- **Gancho:** quem leva algo à beira do lago chama os patos; a sombra da árvore chama passarinhos. Os dois negociam o visitante pelo lugar.

### D-CA. Cachoeira (`cachoeira`)
- **Título:** "Mirante da cachoeira". Montagem: manhã com névoa. Revelação: o sol abre sobre a queda.
- **Zonas e lugares:**
  - `mirante` (obrigatórias):
    - `M1` "no mirante de pedra, à esquerda" (−1,0; 0,0);
    - `M2` "no deque, à direita" (1,0; 0,0).
  - `trilha` (opcionais):
    - `T1` "na trilha alta, à esquerda" (−2,6; −0,9);
    - `T2` "na margem, à esquerda" (−2,2; 0,9);
    - `T3` "na margem, à direita" (2,2; 0,9);
    - `T4` "na trilha alta, à direita" (2,6; −0,9).
  - Textos das zonas: `mirante` "Vai em um dos mirantes" / "em um dos mirantes" / "{nome} no mirante"; `trilha` "Vai em um lugar da trilha" / "em um lugar da trilha".
- **Peças:**
  - A: `camera` (obrigatória, "Câmera", "a câmera"), `boia` ("Boia", "a boia"), `binoculo` ("Binóculo", "o binóculo");
  - B: `canga` (obrigatória, "Canga", "a canga"), `rede` ("Rede", "a rede"), `cesto` ("Cesto de frutas", "o cesto de frutas").
- **Desfecho:** `secundariaA`, `secundariaB`, `luz`, `arcoIris`.
  - `luz`: câmera em `M1` → `dourada`; em `M2` → `prateada`.
  - `arcoIris`: quantas secundárias estão do lado da câmera (`T1`/`T2` com M1, `T3`/`T4` com M2): 2 → `duplo`; 1 → `simples`; 0 → `nevoa`.
- **Textos:**
  - `luz`: dourada "uma luz dourada de manhã", prateada "uma luz prateada entre a névoa";
  - `arcoIris`: duplo "um arco-íris duplo apareceu sobre a queda", simples "um arco-íris apareceu sobre a queda", nevoa "a névoa brilhou como poeira de luz".
- **Legenda:** título "Uma manhã de {secundariaA.nomeMinusculo} e {secundariaB.nomeMinusculo}"; texto "{nomeA} e {nomeB} pegaram {luz.texto}, e {arcoIris.texto}."
- **Efeitos por peça:**
  - câmera: `brilho` #FFFFFF, 0,4;
  - canga: `particulas` folha, deriva, 4;
  - boia: `particulas` gota, sobe, 5;
  - binóculo: `brilho` #DDEBFF, 0,5;
  - rede: `tremular`;
  - cesto: `brilho` #F6B26B, 0,6.
- **Variações:**
  - `luz`: dourada → `tom` #F4C979, 0,14; prateada → `tom` #CFE0EA, 0,16;
  - `arcoIris`: duplo → `arco-iris` duplo `true`; simples → `arco-iris` duplo `false`; nevoa → `particulas` faisca, deriva, 14.
- **Relações** (`raioPerto` 1,6):
  - boia–canga "A boia ficou pronta perto da canga, para o mergulho.";
  - binoculo–camera "Binóculo e câmera juntos: ninguém perde a queda.";
  - rede–canga "A rede e a canga fizeram um canto de descanso.";
  - cesto–canga "O cesto de frutas ficou à mão de quem deita na canga.";
  - binoculo–rede "Do binóculo à rede, a manhã inteira à vista.";
  - cesto–camera "O cesto de frutas entrou no enquadramento da câmera.";
  - boia–cesto "Boia e cesto lado a lado: mergulho e lanche."
- **Gancho:** quem põe a câmera escolhe a luz; o arco-íris duplo pede que as duas secundárias acompanhem a câmera. Por isso o lugar da câmera e o das secundárias viram assunto.

### D-JA. Jantar (`jantar`)
- **Título:** "Mesa para dois". Montagem: salão no fim da tarde, a mesa sendo posta. Revelação: jantar servido, luz baixa e a trilha escolhida.
- **Zonas e lugares** (tampo a z 0,75; aparador a z 0,9):
  - `pratos` (obrigatórias):
    - `P1` "no lugar da esquerda" (−0,45; 0,0);
    - `P2` "no lugar da direita" (0,45; 0,0).
  - `mesa` (opcionais):
    - `M1` "no centro da mesa" (0,0; −0,1);
    - `M2` "na ponta esquerda" (−0,8; −0,3);
    - `M3` "na ponta direita" (0,8; −0,3);
    - `M4` "no aparador, ao fundo" (0,0; −1,3; z 0,9).
  - Textos das zonas: `pratos` "Vai em um dos dois lugares à mesa" / "em um dos lugares à mesa" / "{nome} servido"; `mesa` "Vai em um ponto livre da mesa ou no aparador" / "na mesa ou no aparador".
- **Peças:**
  - A: `massa` (obrigatória, "Massa", "a massa"), `vitrola` ("Vitrola", "a vitrola"), `vela` ("Vela", "a vela");
  - B: `fondue` (obrigatória, "Fondue", "o fondue"), `flores` ("Vaso de flores", "o vaso de flores"), `caixinha` ("Caixinha de música", "a caixinha de música").
- **Desfecho:** `secundariaA`, `secundariaB`, `trilha`, `centro`.
  - `trilha`: vitrola+caixinha `dueto`; vitrola+flores `jazz`; vela+caixinha `valsa`; vela+flores `serenata`.
  - `centro`: o id da peça em `M1` (`vitrola`, `vela`, `flores` ou `caixinha`), ou `vazio`.
- **Textos:**
  - `trilha`: dueto "dueto de vitrola e caixinha", jazz "jazz de vitrola", valsa "valsa de caixinha", serenata "serenata do salão";
  - `centro`:
    - vela "a vela no centro deixou tudo dourado";
    - flores "as flores no centro roubaram a cena";
    - vitrola "a vitrola no centro virou assunto";
    - caixinha "a caixinha de música no centro girou a noite toda";
    - vazio "o centro da mesa ficou livre para as mãos".
- **Legenda:** título "Um jantar com {trilha.texto}"; texto "{nomeA} e {nomeB} serviram a mesa, e {centro.texto}."
- **Efeitos por peça:**
  - massa: `vapor`;
  - fondue: `tremular`;
  - vitrola: `particulas` nota, sobe, 4;
  - vela: `brilho` #FFB54D, 1,2;
  - flores: `particulas` petala, desce, 5;
  - caixinha: `particulas` faisca, sobe, 4.
- **Variações:**
  - `trilha` → `notas` (8 s cada, volume 0,45): `trilha-dueto.mp3`, `trilha-jazz.mp3`, `trilha-valsa.mp3`, `trilha-serenata.mp3`;
  - `centro`: vela → `tom` #F2B24C, 0,15; os demais → `[]`.
- **Relações** (`raioPerto` 0,8):
  - vela–massa "A vela ilumina a massa de perto.";
  - vela–fondue "A vela e o fondue dividem a mesma luz.";
  - flores–massa "As flores ficaram ao lado da massa, como num restaurante.";
  - flores–fondue "O vaso de flores enfeita o fondue.";
  - vitrola–caixinha "Vitrola e caixinha juntas: a mesa virou um palco.";
  - vela–flores "Vela e flores juntas: a mesa mais bonita do salão.";
  - vitrola–fondue "A vitrola ficou perto do fondue, tocando para quem mergulha o pão."
- **Gancho:** o centro da mesa é um lugar só, e quem o ocupa muda o final. A trilha depende das duas escolhas.

### D-FL. Fliperama (`fliperama`): "Dois faróis"
- **Jogo cooperativo original:**
  - Barcos entram pelo alto de um mar noturno e derivam para as pedras.
  - Cada pessoa gira um farol num canto de baixo: A à esquerda, com luz âmbar #F2B866; B à direita, com luz azul-clara #9CC7F2.
  - Um barco coberto **pelas duas luzes** por 1 s acumulado acha o caminho e vai ao porto (+1).
  - Com uma luz só, o progresso fica parado; sem luz, ele zera.
  - Um barco à deriva que passa da linha das pedras "volta ao mar aberto": conta como perdido, sem punição dita na tela.
- **Rodada:** 180 s; começa quando as duas tocam "Começar".
  - O intervalo entre barcos cai de 6 s para 2,5 s, com no máximo 6 barcos ao mesmo tempo.
  - Recorde conjunto: o maior número de barcos guiados numa rodada do encontro. Sem competição entre as duas pessoas.
- **Controles:** arrastar na metade própria da tela (toque) ou setas ←/→ (teclado) definem o alvo do farol; Enter ou Espaço = "Começar" / "Jogar de novo".
- **Textos:**
  - abertura "Dois faróis" / "Guiem os barcos até o porto. Um barco só acha o caminho quando as duas luzes o encontram juntas." / "Arraste para girar a sua luz. No teclado, use as setas.";
  - botões "Começar" e "Jogar de novo";
  - espera "Esperando {parceiro} começar.";
  - parceiro fora "{parceiro} perdeu a conexão. A rodada espera um pouco.";
  - fim "Vocês guiaram {n} barcos." / "Recorde de vocês: {r}." / "Recorde novo!".
  - Para a região viva do app (pai do iframe): "A rodada começou." e "Vocês guiaram {n} barcos. Recorde de vocês: {r}."

---

## Procedimentos compartilhados

### P-A. Produção de assets de um cenário (usado pelas Tasks 3, 6, 9 e 12)
1. **Saldo antes do lote.**
   - `get_balance` do Hi3D e `quote_generation` de um prop; o lote do cenário custa (props × 49 pts).
   - Saldo insuficiente ou falha do Hi3D: parar e aplicar a decisão D2, sem gerar por outra via.
   - `account_get_usage` do ElevenLabs.
   - Tripo: `tripo_plan` com as 2 imagens. O aceite vem da decisão D1, já tomada para o lote inteiro; sem D1 aprovada, parar aqui.
2. **Props 3D (Hi3D).** Para cada prop, `create_image_generation` (Nano-Banana 2) com o prompt da task + o sufixo `PROMPT_BASE`.
   - Conferir a imagem.
   - Depois `create_generation` (imagem → 3D v3.0, PBR); `get_task_outputs`; baixar o GLB para `assets/source/modelos/<cenario>/<prop>.glb` (fora do Git) e a imagem para `assets/source/referencias/<cenario>/<prop>-hi3d-<8 hex>.png`.
   - Uma imagem ruim gera **uma** nova imagem, nunca um 3D novo de imagem ruim. No máximo 2 imagens por prop.
   - `PROMPT_BASE` = `", stylized cozy 3D prop, soft hand-painted PBR materials, single object, centered, plain light-grey background, no text, no people"`.
3. **Fundos (Tripo).**
   - Gerar o fundo da montagem (texto → imagem, 4K, 16:9) e o da revelação como **edição** do primeiro, para casar o enquadramento.
   - Masters em `assets/source/fundos/<cenario>/fundo-{crepusculo,noite}-4k.png`, e o JSON da tarefa em `tripo-<momento>-task.json`.
   - Exportar com `assets/source/ceu/exportar.sh <cenario>` (Task 1) para `static/assets/cenarios/<cenario>/ceu/ceu-{crepusculo,noite}-{1280,2560}.webp`.
4. **Render (Blender, com a trava).**
   - Escrever `assets/source/blender/cenarios/<cenario>.py` no contrato da Task 1: base procedural, `LUGARES`, `MEDIDAS`, `GIROS`, iluminação dos dois momentos, construtores e props só da noite.
   - Iterar com `DUORA_AMOSTRAS=32 DUORA_CENARIO=<cenario>` e fazer o render final com 96 amostras.
   - Saída: `static/assets/cenarios/<cenario>/cena/<nome>-<momento>-{900,1800,2700}.webp` + `cena.json`, terminando com `DUORA_OK`.
   - **Abrir** os WebP 1800 dos dois momentos e conferir escala, pose e sombra.
   - Os lugares podem ser ajustados no módulo. A mudança vale para o `cena.json` e é avisada à task de dados do cenário.
5. **Áudio (ElevenLabs).**
   - Brutos em `assets/source/audio/brutos/<cenario>/`.
   - Montagem por `assets/source/audio/<cenario>/montar.sh` (funções de `assets/source/audio/comum.sh`, Task 1) para `static/assets/cenarios/<cenario>/audio/*.mp3`.
   - Conferir com `ffprobe` (`mp3,48000,192000`).
   - O `ambiente.mp3` tem 60 s em laço: trilha `eleven_music` instrumental mais ambiência `eleven_text_to_sound` cerca de 12 dB abaixo, com crossfade de 4 s. O `revelacao.mp3` tem cerca de 4 s, −20 LUFS e fade de 0,8 s.
6. **Manifesto e cache.**
   - Uma seção `## Cenário <nome>` em `assets/manifest.md`, com a tabela de exports, a tabela de gerações Hi3D (tarefas de imagem e 3D, master local), as tarefas Tripo e o flow/geração do ElevenLabs, com custos.
   - Rodar o `ServiceWorkerVersaoTest` (depois da 1a, a versão sai da impressão; antes, copiar o valor para o `sw.js`, como manda a skill).
7. **Commit**, separado por natureza:
   - `feat(assets): <cenario> — props, fundos e renders` (WebP + `cena.json` + manifesto);
   - `feat(assets): <cenario> — sons` (MP3 + manifesto + `montar.sh`).

### P-P. Playtest por agente de um cenário (usado pelas Tasks 5, 8, 11 e 14)
1. Antes do playtest, os testes do cenário (Java, Vitest e o `Jornada<Cenario>IT` nos 3 navegadores, um por vez) e o `./mvnw -q verify` estão verdes.
2. O controlador sobe o app numa porta livre conferida antes (`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=<p>`, mais `--duora.cenarios.sorteaveis=terraco,<cenario>` quando a Etapa 4 já existir).
3. Despacha **um agente novo, que não implementou o cenário**. Pelo Playwright MCP, ele joga:
   - Ana em 1440×900;
   - Bruno num contexto isolado de celular, criado por `browser_run_code_unsafe` (`browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })`).
   - O encontro é aberto pelo caminho vigente: sala anônima com `"cenario":"<id>"` antes da Etapa 5; convite privado com o cenário depois.
4. **Roteiro:**
   1. entrar;
   2. composição completa com uma sugestão com destino aceita e uma ignorada;
   3. revelar;
   4. pedir ajuste, uma vez recusado e outra aceito;
   5. segunda composição que **muda pelo menos uma chave de variação do desfecho** (ex.: `cardume`), e revelar;
   6. Bruno sai.
   - Capturas dos dois lados a cada passo em `target/playtest-9b/<cenario>/`, copiadas para `.claude/handoff/playtest-9b/<cenario>/` do repositório principal.
5. **Perguntas** de `duora-java-mvp.md` §3, com sim/não, uma frase e as capturas. Os critérios de "sim" são os da 1c (Task 7), mais:
   - **4:** as duas revelações têm títulos ou textos diferentes **e** efeitos de variação diferentes visíveis nas capturas.
   - A pergunta 1 é respondida para registro.
6. **Fechamento:**
   - Com 2–5 "sim": encerrar o servidor, registrar `relatorio.md` e montar o pacote da decisão D4 (as 4 melhores capturas + o relatório).
   - Com algum "não": cada falha vira task de correção com teste antes, e há nova rodada com outro agente novo (no máximo 2). Persistindo, levar ao dono só a escolha (2–3 opções de mecânica + recomendação).

---

### Task 1: Pipeline de assets multi-cenário (refatoração, terraço idêntico)

"Make the change easy": o `cena.py` hoje conhece só o terraço. Esta task o separa em driver + módulo por cenário, sem mudar nenhum render.

**Arquivos:**
- Criar: `assets/source/blender/comum.py` (utilidades, materiais, primitivas, `importar_glb`, `acender_vela`, render, recorte e export, extraídos do `cena.py`); `assets/source/blender/cenarios/terraco.py`; `assets/source/audio/comum.sh`
- Modificar: `assets/source/blender/cena.py` (vira o driver); `assets/source/ceu/exportar.sh` (aceita `<cenario>`); `assets/source/audio/montar.sh` (passa a usar o `comum.sh`); `assets/manifest.md` (comando novo); `.claude/skills/duora-assets/SKILL.md` (comando com `DUORA_CENARIO` e caminhos por cenário)
- Teste: `assets/source/blender/verificar_terraco.sh` (compara com o commit anterior)

**Interfaces (produz):**
- Driver: `DUORA_CENARIO=<id>` (padrão `terraco`) importa `cenarios/<id>.py`. Grava em `src/main/resources/static/assets/cenarios/<id>/cena/` (caminho da 1b, Task 3). `DUORA_SO`, `DUORA_AMOSTRAS` e `DUORA_LARGURA` continuam valendo.
- Contrato do módulo de cenário:
  - `BASE: str`, o nome do sprite da base (terraço `"terraco"`);
  - `LUGARES: dict[str, tuple]`, com `(x, y)` ou `(x, y, z)` em metros;
  - `MX, MY: float`; `AZIMUTE: float`;
  - `MODELOS: str` (pasta dos GLBs; o terraço mantém `assets/source/modelos/`, e os novos usam `assets/source/modelos/<id>/`);
  - `MEDIDAS: dict[str, tuple[str, float]]`; `GIROS: dict[str, float]`;
  - `construir_base(momento: str) -> bpy.types.Object`; `iluminacao(momento: str) -> None`;
  - `construtores(momento: str, por_glb) -> dict[str, Callable[[], Object]]`;
  - `SO_NOITE: frozenset[str]` (props renderizados só na noite, ex.: animais; padrão vazio).
- `cena.json` ganha a chave `"base"`. As outras chaves e valores do terraço ficam idênticos.
- `exportar.sh <cenario>`: masters em `assets/source/fundos/<cenario>/` (o terraço continua em `assets/source/ceu/`). Saída em `static/assets/cenarios/<cenario>/ceu/`.
- `comum.sh`: `normalizar_efeito <entrada> <saida> <lufs> <fade_s>` e `laco_com_crossfade <trilha> <ambiencia> <saida> <segundos>`, sempre com `-ar 48000 -b:a 192k`.

- [ ] **Passo 1:** escrever o `verificar_terraco.sh`. Ele renderiza o terraço com `DUORA_CENARIO=terraco` em `target/blender-verificacao/` e confere:
  - o `cena.json` igual ao versionado, ignorando só a chave `base` (`jq 'del(.base)'`);
  - SSIM ≥ 0,995 de cada WebP contra o versionado (`ffmpeg -lavfi ssim`).
  - Rodar contra o `cena.py` atual **com a trava**: verde, o que prova que o verificador funciona.
- [ ] **Passo 2:** extrair o `comum.py` e o `cenarios/terraco.py` (Extract Function / Move Function), sem mudar valores. Rodar o verificador: verde.
- [ ] **Passo 3:** `exportar.sh terraco` e `montar.sh` refeitos com o `comum.sh`. Conferir que os MP3 e WebP saem com o mesmo `sha256` dos versionados; quando a saída não for byte a byte, o critério é SSIM ≥ 0,995 e `ffprobe` com os mesmos parâmetros.
- [ ] **Passo 4:** atualizar a skill `duora-assets` (comando, caminhos por cenário, `PROMPT_BASE`) e o manifesto. Commit `refactor(assets): pipeline de render por cenário, terraço idêntico`.

### Task 2: Motor de apresentação — efeitos novos, variações do desfecho, textos e guardas genéricas

**Arquivos:**
- Modificar:
  - `frontend/src/cenario/{esquema.ts,legenda.ts}`, `frontend/src/cena/efeitos.ts`, `frontend/src/estilos/revelacao.css`;
  - `frontend/src/componentes/{Resultado,Moldura}.tsx` (o título vem do cenário);
  - `frontend/src/dominio/dados.ts` (o sprite da base vem de `geometria.base`, com padrão `"terraco"`);
  - `src/main/resources/static/assets/cenarios/terraco/cenario.json` (`titulo` "Antes da primeira estrela", sem mudar mais nada).
- Criar: `frontend/src/cenario/variacoes.ts`, `src/test/java/bipotheking/net/duora/e2e/CenarioPagina.java`
- Teste: `esquema.test.ts` (todo `cenario.json` do disco), `legenda.test.ts`, `efeitos.test.ts`, `variacoes.test.ts`; `src/test/java/bipotheking/net/duora/game/CenarioJsonTest.java` (parametrizado); `e2e/CenarioFrontIT.java`; `VisualIT`/`ComparacaoVisualTest` (terraço idêntico)

**Interfaces:**
- Consome:
  - 1b: `CenarioFront`, `parseCenario`, `interpolar`, `CatalogoDeCenarios`, `Cenario`, `Desfecho`, `Resultado.valores()`;
  - 1c: `Efeito`, `revelacao`, `relacoes`, `raioPerto`, `criarEfeitos(...).mostrar(pecas, aoVivo)`.
- Produz:
  - `Efeito` ganha 4 variantes:
    - `{ tipo: "particulas"; forma: "bolha" | "folha" | "gota" | "petala" | "faisca" | "nota"; direcao: "sobe" | "desce" | "deriva"; quantidade: number /* 1–20 */; ancora?: Ancora }`;
    - `{ tipo: "cardume"; sprite: string; quantidade: number /* 1–10 */; ancora?: Ancora }`;
    - `{ tipo: "arco-iris"; duplo: boolean }`;
    - `{ tipo: "tom"; cor: string /* #rrggbb */; opacidade: number /* 0–0,4 */ }`.
  - `type Ancora = "esquerda" | "direita" | "centro" | \`lugar:${string}\``. Na revelação por peça, a âncora é o lugar da peça e o campo é ignorado. Num efeito de variação, a âncora ausente vale `"centro"`.
  - Esquema: `titulo: string`; `textos: Record<string, Record<string, string>>`; `variacoes: Record<string, Record<string, Efeito[]>>`. Referências cruzadas:
    - o `lugar:<id>` existe;
    - toda chave de `variacoes` e `textos` aparece em algum marcador da legenda ou é chave de variação;
    - a cor é `#rrggbb`.
  - `interpolar`: `{chave.texto}` lê `textos[chave][contexto[chave]]`. Ausente → `ErroCenario` com o caminho `textos.<chave>.<valor>`.
  - `variacoes.ts`: `efeitosDoDesfecho(c: CenarioFront, resultado: Readonly<Record<string, string>>): Efeito[]`, com as chaves na ordem do JSON e um valor sem entrada ⇒ `[]`.
  - `criarEfeitos(...)` ganha `mostrarDesfecho(resultado, aoVivo: boolean)`.
    - Cada elemento é `aria-hidden`, com `data-efeito="<tipo>"` e `data-variacao="<chave>"`.
    - `cardume` usa o sprite `<sprite>-noite-<largura>.webp` do `spritesBase`.
    - `notas` só toca com `aoVivo`, mesmo com o som geral desligado (regra da 1c).
  - Movimento reduzido: as partículas ficam paradas em posições fixas, o cardume fica parado na âncora, e o arco-íris e o tom são estáticos.
  - `CenarioJsonTest` (Java) vira `@ParameterizedTest` sobre todo cenário de **montagem** do catálogo:
    - `espelhoIgualAoCenario` (o teste da 1b, generalizado);
    - `todoValorDoDesfechoTemTextoEVariacao`: enumera **todas** as composições completas (cada obrigatória em cada lugar da sua zona, sem colisão; cada secundária de cada pessoa em cada lugar livre da sua zona), roda o `Desfecho` e exige `textos[k][v]` para cada `{k.texto}` da legenda e `variacoes[k][v]` para cada chave de `variacoes`;
    - `nenhumTextoMorto`: todo valor em `textos`/`variacoes` aparece em alguma composição;
    - `cardumeUsaSpriteDaGeometria`: o `sprite` existe em `cena.json.props`;
    - `peloMenosQuatroFinaisDiferentes`: pelo menos 4 pares (título, texto) distintos, com Ana/Bruno fixos. O terraço fica isento deste teste.
  - `CenarioPagina` (e2e, page object sobre o `TerracoPagina`):
    - `static CenarioPagina abrirEmPar(Page ana, Page bruno, String cenario)`: sala anônima com `cenario` antes da Etapa 5; `entrarEmParComContas` + convite com cenário depois;
    - `void colocar(Page quem, String nomeDaPeca, String nomeDoLugar)`;
    - `void confirmarOsDois()`;
    - `String titulo(Page)` e `String texto(Page)`;
    - `int efeitos(Page, String tipo)` (conta `[data-efeito=<tipo>]`).

- [ ] **Passo 1:** escrever os testes.
  - `esquema.test.ts`:
    - `todoCenarioDoDiscoEValido` (glob em `static/assets/cenarios/*/cenario.json`);
    - `ancoraDeLugarInexistenteERecusada`;
    - `corForaDoFormatoERecusada`;
    - `opacidadeAcimaDoLimiteERecusada`.
  - `legenda.test.ts`: `marcadorTextoLeOValor` e `marcadorTextoSemValorLancaComCaminho`.
  - `variacoes.test.ts`: `valorSemEntradaNaoTemEfeito` e `ordemDasChavesSegueOJson`.
  - `efeitos.test.ts`:
    - `cadaTipoNovoCriaSeuElemento`;
    - `cardumeUsaOSpriteDoCenario`;
    - `notasDeVariacaoSoTocamAoVivo`;
    - `movimentoReduzidoNaoAnima` (classe `motion-reduce` presente, sem animação inline).
  - `CenarioJsonTest`: os cinco acima, só com o terraço, por enquanto.
  - `CenarioFrontIT.variacaoDoDesfechoApareceNaNoite`: a rota troca o `cenario.json` do terraço por um com `variacoes.ladoTelescopio.esquerdo = [{tipo:"tom",cor:"#112233",opacidade:0.1}]` e telescópio em C1 → depois de revelar, um `[data-efeito=tom][data-variacao=ladoTelescopio]`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. A `Moldura` usa `cenario.titulo` no heading, e o texto do terraço não muda.
- [ ] **Passo 4:** rodar `pnpm test`, `./mvnw -q verify`, a suíte e2e em chromium, o `CenarioFrontIT` + `AxeIT` em firefox e webkit, e a comparação visual do terraço contra o "depois" da 1c, tudo verde. `TamanhoDoJsInicialTest` ≤ 80 KB.
- [ ] **Passo 5:** commits separados:
  - `refactor(front): título e base da cena vindos do cenário` (comportamento idêntico);
  - `feat(cenario): efeitos de partículas, cardume, arco-íris e tom, e variações pelo desfecho`.

### Task 3: Aquário — assets

**Procedimento:** P-A. Design: D-AQ.

**Arquivos:**
- Criar:
  - `assets/source/blender/cenarios/aquario.py`;
  - `assets/source/audio/aquario/montar.sh`;
  - `assets/source/fundos/aquario/*`;
  - `src/main/resources/static/assets/cenarios/aquario/{cena,ceu,audio}/*`.
- Modificar: `assets/manifest.md`

**Interfaces (produz):**
- Sprites `rocha`, `alga`, `concha`, `tronco`, `coral`, `anfora` (dois momentos) e `peixe` (só noite); base `aquario`.
- `cena.json` com os lugares `A1 A2 E1 E2 D1 D2`.
- `ceu-{crepusculo,noite}-{1280,2560}.webp`; `audio/{ambiente,revelacao}.mp3`.

**Entradas específicas:**
- Hi3D (prompt + `PROMPT_BASE`; medida; giro inicial 0):
  - `rocha` "a large rounded aquarium rock with a small cave opening, grey-blue stone with soft moss patches" (altura 0,55);
  - `alga` "tall wavy aquarium seaweed, bright green ribbons rising from a small rock base" (altura 0,9);
  - `concha` "a large spiral seashell resting on sand, cream and peach, glossy inside" (largura 0,35);
  - `tronco` "a small submerged driftwood branch, twisted dark wood with tiny moss tufts" (largura 0,9);
  - `coral` "a soft pink branching coral cluster on a small base" (altura 0,5);
  - `anfora` "an ancient terracotta amphora lying slightly tilted, faded painted stripes, small chip on the rim" (largura 0,6);
  - `peixe` "a small stylized tropical fish, orange with white stripes, side view, fins spread" (largura 0,18; `SO_NOITE`).
  - Total: 7 × 49 = 343 pts.
- Base procedural: tanque de vidro de 3,6 × 1,6 m (`MX` 1,8, `MY` 0,8), areia clara, moldura escura. A parede de trás fica transparente, para o fundo aparecer.
  - Na noite, uma luz de área fria por cima, com volume leve na água.
  - Na montagem, só a luz da sala, fraca e quente.
- Tripo:
  - montagem: "inside of a home aquarium, background only: deep blue-grey water, tank light off, faint silhouettes of distant rocks, calm, painterly illustration, 16:9, no fish, no text";
  - revelação (edição): "same view with the tank light on: bright clear turquoise water, soft light rays from above".
- ElevenLabs:
  - ambiente: trilha "gentle underwater lullaby, soft electric piano and pads, 70 bpm, instrumental, no vocals" + ambiência "quiet aquarium filter hum and small bubbles in a calm room";
  - revelacao: "aquarium light switching on: soft click, rising bubbles and a crystalline shimmer".

- [ ] **Passo 1:** P-A 1–3 (saldo, props, fundos).
- [ ] **Passo 2:** P-A 4 (módulo e render com a trava; conferir os WebP).
- [ ] **Passo 3:** P-A 5–6 (áudio, manifesto, cache).
- [ ] **Passo 4:** commits do P-A 7.

### Task 4: Aquário — dados e testes

**Arquivos:**
- Criar:
  - `src/main/java/bipotheking/net/duora/game/Aquario.java`;
  - `src/main/resources/static/assets/cenarios/aquario/cenario.json`;
  - `src/test/java/bipotheking/net/duora/game/AquarioTest.java`;
  - `src/test/java/bipotheking/net/duora/e2e/JornadaAquarioIT.java`.
- Modificar:
  - `CatalogoDeCenarios.java` (em `room/` antes da Etapa 5, `encontro/` depois): registrar `Aquario.CENARIO`;
  - `e2e/AxeIT.java` (estados "aquário montagem" e "aquário revelação").

**Interfaces:**
- Consome: `Cenario`, `Zona`, `Lugar`, `Peca`, `Desfecho`, `Resultado`, `Regras.*(Cenario, ...)` (1b/1c); `CenarioPagina` (Task 2); assets da Task 3 (`cena.json`, sprites `peixe`).
- Produz:
  - `public final class Aquario`:
    - `CENARIO` com id `"aquario"`;
    - zonas `AREIA` (`"areia"`) e `CANTOS` (`"cantos"`);
    - lugares `A1, A2, E1, E2, D1, D2`, nessa ordem;
    - peças `ROCHA, ALGA, CONCHA` (A) e `TRONCO, CORAL, ANFORA` (B), nessa ordem;
    - os parâmetros das restrições globais;
    - `Desfecho DESFECHO` com as chaves `secundariaA, secundariaB, luz, cardume`, nessa ordem, e os valores de D-AQ.
  - `cenario.json` com tudo de D-AQ:
    - `titulo`, `pecas`, `lugares`, `zonas`, `legenda`, `textos`, `revelacao`, `variacoes`, `relacoes`, `raioPerto`;
    - `sprites` (padrão = id da peça);
    - `ceu`;
    - `sons` (`ambiente` 0,35; `revelacao` 0,6).

- [ ] **Passo 1:** escrever o `AquarioTest`.
  - `@ParameterizedTest luzVemDaCombinacaoDasSecundarias`: (alga, coral, esmeralda), (alga, anfora, turquesa), (concha, coral, rosada), (concha, anfora, dourada).
  - `@ParameterizedTest cardumeSegueOLadoDasSecundarias`: (E1, E2, esquerda), (D1, D2, direita), (E1, D2, dividido), (E2, D1, dividido).
  - `composicaoCompletaRevelaComTodasAsChaves`: Ana põe rocha em A1 e alga em E1; Bruno põe tronco em A2 e coral em E2; os dois confirmam → `REVELADO` e `resultado.valores()` igual a `{secundariaA: alga, secundariaB: coral, luz: esmeralda, cardume: esquerda}`, na ordem.
  - `trocarDeSecundariaTiraAOutra`: com a alga em E1, pôr a concha em D1 → a alga volta ao inventário.
  - `rochaNosCantosERecusada` (`LUGAR_INVALIDO`).
  - `idDoTerracoNaoEPecaDoAquario`: `Aquario.CENARIO.peca("manta")` vazio.
- [ ] **Passo 2:** RED; implementar o `Aquario` e registrar no catálogo; verde. O `CenarioJsonTest` passa a falhar (falta o JSON): é o RED do passo seguinte.
- [ ] **Passo 3:** escrever o `cenario.json` e o `JornadaAquarioIT`.
  - `JornadaAquarioIT.composicaoDeAlgaECoralAcendeEsmeralda`: o caminho do teste anterior pela UI → nos dois lados, o título "Um aquário de alga e coral"; o texto começa com "Ana e Bruno acenderam uma luz verde-esmeralda, e o cardume se juntou no canto esquerdo."; `efeitos(tom) == 1`, `efeitos(cardume) == 1`, `efeitos(particulas) ≥ 1`.
  - `JornadaAquarioIT.secundariasSeparadasDividemOCardume`: alga em E1, coral em D1 → `efeitos(cardume) == 2` e o texto contém "dividiu".
  - `AxeIT`: os dois estados sem violações.
- [ ] **Passo 4:** `./mvnw -q verify` (com o `CenarioJsonTest` dos cinco testes), `pnpm test`, e o `JornadaAquarioIT` + `AxeIT` nos 3 navegadores, um por vez. Commit `feat(cenario): aquário`.

### Task 5: Aquário — playtest por agente

**Procedimento:** P-P, com `<cenario>` = `aquario`. Na 2ª composição, mudar o `cardume` (ex.: juntas → divididas).
- [ ] **Passo 1:** P-P 1–4.
- [ ] **Passo 2:** P-P 5–6. Prova: capturas + "sim" em 2–5, registrados no handoff, e o pacote da D4 pronto.

### Task 6: Piquenique — assets

**Procedimento:** P-A. Design: D-PQ. Arquivos no mesmo molde da Task 3, com `piquenique`.

**Interfaces (produz):**
- Sprites `cesta`, `jarra`, `pipa`, `violao`, `guarda-sol`, `bicicleta` (dois momentos) e `pato`, `passarinho`, `borboleta` (só noite); base `piquenique`.
- `cena.json` com `T1 T2 G1 G2 G3 G4`.
- `audio/{ambiente,revelacao,violao-notas}.mp3`.

**Entradas específicas:**
- Hi3D:
  - `cesta` "a wicker picnic basket with the lid half open, bread and a checkered napkin peeking out" (largura 0,6);
  - `jarra` "a glass pitcher of lemonade with lemon slices and ice" (altura 0,35);
  - `pipa` "a colorful diamond kite with a ribbon tail lying on the grass" (largura 0,9);
  - `violao` "an acoustic guitar lying on its back, warm honey wood" (largura 1,0);
  - `guarda-sol` "an open sun umbrella with cream and terracotta stripes, pole planted in the ground" (altura 2,0);
  - `bicicleta` "a vintage mint-green city bicycle with a front basket, on its kickstand" (largura 1,7);
  - `arvore` "a lush rounded shade tree with a thick trunk" (altura 4,0; parte da base, atrás de G1);
  - `pato` "a small cartoon mallard duck, standing" (altura 0,35; `SO_NOITE`);
  - `passarinho` "a small round songbird, brown and yellow" (altura 0,15; `SO_NOITE`);
  - `borboleta` "a butterfly with open orange and black wings" (largura 0,12; `SO_NOITE`).
  - Total: 10 × 49 = 490 pts.
- Base procedural: gramado de 7 × 4 m (`MX` 3,5, `MY` 2,0), toalha xadrez (`material_xadrez`) sob T1/T2, a árvore (GLB) no fundo à esquerda e a borda de um lago no fundo à direita (plano com material de água).
- Tripo:
  - montagem: "early afternoon sky over soft green hills and a distant lake, scattered clouds, painterly illustration, 16:9, no people, no text";
  - revelação (edição): "same view at golden late afternoon, warm peach light".
- ElevenLabs:
  - ambiente: trilha "light acoustic picnic tune, fingerpicked guitar and soft ukulele, 90 bpm, instrumental" + ambiência "birds, wind in leaves and a gentle lake in a park";
  - revelacao: "warm guitar strum with birds chirping";
  - violao-notas: "four soft fingerpicked acoustic guitar notes, no other sounds" (cerca de 4 s, −20 LUFS).

- [ ] **Passos 1–4:** como na Task 3.

### Task 7: Piquenique — dados e testes

**Arquivos:** no molde da Task 4: `game/Piquenique.java`, `cenario.json`, `game/PiqueniqueTest.java`, `e2e/JornadaPiqueniqueIT.java`, o catálogo e o `AxeIT`.

**Interfaces (produz):** `public final class Piquenique`:
- `CENARIO` `"piquenique"`;
- zonas `TOALHA`, `GRAMA`;
- lugares `T1, T2, G1, G2, G3, G4`;
- peças `CESTA, PIPA, VIOLAO` (A) e `JARRA, GUARDA_SOL, BICICLETA` (B; ids `"guarda-sol"` etc.);
- `DESFECHO` com as chaves `secundariaA, secundariaB, clima, visitante`;
- e o `cenario.json` de D-PQ.

- [ ] **Passo 1:** `PiqueniqueTest`:
  - `@ParameterizedTest climaVemDaCombinacao` (os 4 casos de D-PQ);
  - `@ParameterizedTest visitanteSegueOsLugares`: (G4 + G2 → patos), (G1 + G3 → passarinhos), (G1 + G4 → patos), (G2 + G3 → borboletas);
  - `composicaoCompletaRevelaComTodasAsChaves`;
  - `trocarDeSecundariaTiraAOutra`;
  - `cestaNaGramaERecusada`.
- [ ] **Passo 2:** RED; implementar e registrar; verde.
- [ ] **Passo 3:** `cenario.json` e `JornadaPiqueniqueIT`:
  - `pipaEGuardaSolComBeiraDoLagoTrazPatos`: título "Um piquenique de pipa e guarda-sol"; texto "Ana e Bruno ganharam uma brisa firme, e os patos do lago vieram espiar."; `efeitos(cardume) == 1`; `efeitos(particulas) ≥ 1`.
  - `violaoTocaNotasComSomDesligado`: com a sonda de `AudioContext` do `RevelacaoIT`, um buffer iniciado na revelação ao vivo.
  - `AxeIT`: dois estados.
- [ ] **Passo 4:** como a Task 4. Commit `feat(cenario): piquenique`.

### Task 8: Piquenique — playtest por agente
**Procedimento:** P-P com `piquenique`. A 2ª composição muda o `visitante`.
- [ ] **Passos 1–2:** como na Task 5.

### Task 9: Cachoeira — assets

**Procedimento:** P-A. Design: D-CA.

**Interfaces (produz):**
- Sprites `camera`, `canga`, `boia`, `binoculo`, `rede`, `cesto` (dois momentos); base `cachoeira`.
- `cena.json` com `M1 M2 T1 T2 T3 T4`.
- `audio/{ambiente,revelacao}.mp3`.

**Entradas específicas:**
- Hi3D:
  - `camera` "a vintage camera mounted on a wooden tripod" (altura 1,5);
  - `canga` "a colorful beach sarong spread flat on the ground, tropical pattern" (largura 1,6);
  - `boia` "an inflatable swim ring, coral red with white stripes" (largura 0,8);
  - `binoculo` "a pair of vintage brass binoculars resting on a small rock" (largura 0,3);
  - `rede` "a woven cotton hammock hanging between two short wooden posts" (largura 2,2);
  - `cesto` "a round woven basket full of mango, banana and passion fruit" (largura 0,55).
  - Total: 6 × 49 = 294 pts.
- Base procedural: duas plataformas, uma de pedra à esquerda e um deque de madeira à direita (`MX` 3,4, `MY` 1,8), o rio raso entre elas (plano de água), pedras e trilha de terra.
- Tripo:
  - montagem: "tall waterfall in a lush Atlantic rainforest seen from a viewpoint, morning mist, soft grey light, painterly illustration, 16:9, no people, no text";
  - revelação (edição): "same waterfall with the sun breaking through, bright and clear, sparkling spray".
- ElevenLabs:
  - ambiente: trilha "airy morning tune, soft flute and marimba, 80 bpm, instrumental" + ambiência "steady distant waterfall, rainforest birds";
  - revelacao: "soft chime with sparkling water shimmer".

- [ ] **Passos 1–4:** como na Task 3.

### Task 10: Cachoeira — dados e testes

**Arquivos:** no molde da Task 4: `game/Cachoeira.java`, `cenario.json`, `game/CachoeiraTest.java`, `e2e/JornadaCachoeiraIT.java`, o catálogo e o `AxeIT`.

**Interfaces (produz):** `public final class Cachoeira`:
- `CENARIO` `"cachoeira"`;
- zonas `MIRANTE`, `TRILHA`;
- lugares `M1, M2, T1, T2, T3, T4`;
- peças `CAMERA, BOIA, BINOCULO` (A) e `CANGA, REDE, CESTO` (B);
- `DESFECHO` com as chaves `secundariaA, secundariaB, luz, arcoIris`;
- e o `cenario.json` de D-CA.

- [ ] **Passo 1:** `CachoeiraTest`:
  - `luzSegueOMiranteDaCamera` (M1 dourada, M2 prateada);
  - `@ParameterizedTest arcoIrisContaAsSecundariasDoLadoDaCamera`: (M1, T1, T2, duplo), (M1, T1, T3, simples), (M1, T3, T4, nevoa), (M2, T3, T4, duplo);
  - `composicaoCompletaRevelaComTodasAsChaves`;
  - `trocarDeSecundariaTiraAOutra`;
  - `cameraNaTrilhaERecusada`.
- [ ] **Passo 2:** RED; implementar e registrar; verde.
- [ ] **Passo 3:** `cenario.json` e `JornadaCachoeiraIT.secundariasDoLadoDaCameraFazemArcoIrisDuplo`: título "Uma manhã de boia e rede"; texto "Ana e Bruno pegaram uma luz dourada de manhã, e um arco-íris duplo apareceu sobre a queda."; `efeitos(arco-iris) == 1`. Mais o `AxeIT` em dois estados.
- [ ] **Passo 4:** como a Task 4. Commit `feat(cenario): cachoeira`.

### Task 11: Cachoeira — playtest por agente
**Procedimento:** P-P com `cachoeira`. A 2ª composição muda o `arcoIris`.
- [ ] **Passos 1–2:** como na Task 5.

### Task 12: Jantar — assets

**Procedimento:** P-A. Design: D-JA.

**Interfaces (produz):**
- Sprites `massa`, `fondue`, `vitrola`, `vela`, `flores`, `caixinha` (dois momentos); base `jantar`.
- `cena.json` com `P1 P2 M1 M2 M3 M4` (com z).
- `audio/{ambiente,revelacao,trilha-dueto,trilha-jazz,trilha-valsa,trilha-serenata}.mp3`.

**Entradas específicas:**
- Hi3D:
  - `massa` "a plate of spaghetti with tomato sauce and basil" (largura 0,3);
  - `fondue` "a small cheese fondue pot on a stand with forks and bread cubes" (altura 0,3; chama acesa na noite pelo `acender_vela` do `comum.py`);
  - `vitrola` "a compact vintage suitcase record player with a vinyl" (largura 0,4);
  - `vela` "a brass candlestick with a tall candle" (altura 0,35; acesa na noite);
  - `flores` "a small glass vase with wildflowers" (altura 0,35);
  - `caixinha` "an open wooden music box with a tiny dancer" (largura 0,18).
  - Total: 6 × 49 = 294 pts.
- Base procedural: mesa de 1,8 × 1,0 m com toalha branca, duas cadeiras, aparador ao fundo e piso de madeira. Na noite, luz baixa quente pendente.
- Tripo:
  - montagem: "large restaurant window overlooking a city at dusk, warm interior wall around it, painterly illustration, 16:9, no people, no text";
  - revelação (edição): "same window at night with city lights and a warm candle-lit interior".
- ElevenLabs:
  - ambiente: trilha "quiet restaurant piano, slow and warm, 70 bpm, instrumental" + ambiência "calm restaurant room, distant cutlery, soft murmur with no intelligible words";
  - revelacao: "two glasses clinking softly";
  - trilhas de 8 s (`eleven_music`, instrumental): dueto "vinyl jazz trio joined by a music box melody"; jazz "warm vinyl jazz trio"; valsa "music box waltz"; serenata "soft solo restaurant piano".

- [ ] **Passos 1–4:** como na Task 3.

### Task 13: Jantar — dados e testes

**Arquivos:** no molde da Task 4: `game/Jantar.java`, `cenario.json`, `game/JantarTest.java`, `e2e/JornadaJantarIT.java`, o catálogo e o `AxeIT`.

**Interfaces (produz):** `public final class Jantar`:
- `CENARIO` `"jantar"`;
- zonas `PRATOS`, `MESA`;
- lugares `P1, P2, M1, M2, M3, M4`;
- peças `MASSA, VITROLA, VELA` (A) e `FONDUE, FLORES, CAIXINHA` (B);
- `DESFECHO` com as chaves `secundariaA, secundariaB, trilha, centro`;
- e o `cenario.json` de D-JA.

- [ ] **Passo 1:** `JantarTest`:
  - `@ParameterizedTest trilhaVemDaCombinacao` (4 casos);
  - `@ParameterizedTest centroEAPecaEmM1` (vela em M1 → vela; flores em M1 → flores; nada em M1 → vazio);
  - `composicaoCompletaRevelaComTodasAsChaves`;
  - `trocarDeSecundariaTiraAOutra`;
  - `massaNoAparadorERecusada`.
- [ ] **Passo 2:** RED; implementar e registrar; verde.
- [ ] **Passo 3:** `cenario.json` e `JornadaJantarIT`:
  - `velaNoCentroDeixaTudoDourado`: Ana põe a massa em P1 e a vela em M1; Bruno põe o fondue em P2 e as flores em M3 → título "Um jantar com serenata do salão"; texto "Ana e Bruno serviram a mesa, e a vela no centro deixou tudo dourado."; `efeitos(tom) == 1`.
  - `trilhaTocaUmaVezAoVivo`: com a sonda de `AudioContext`, um buffer na revelação e nenhum ao recarregar.
  - `AxeIT`: dois estados.
- [ ] **Passo 4:** como a Task 4. Commit `feat(cenario): jantar`.

### Task 14: Jantar — playtest por agente
**Procedimento:** P-P com `jantar`. A 2ª composição muda a `trilha` e o `centro`.
- [ ] **Passos 1–2:** como na Task 5.

---

### Task 15: Spike do fliperama — Godot web single-thread sob CSP e orçamento de 5 s (protótipo descartável)

Pergunta que o spike responde: **o export web single-thread do Godot 4, sem `unsafe-inline`/`unsafe-eval`, abre em ≤ 5 s no emulador com 4G e CPU 4× e fala WebSocket com o Spring?** Seguir `mattpocock-skills:prototype`:
- todo o código fica na branch `spike/fliperama-godot`, em pastas com "prototipo" no nome;
- sem testes nem polimento;
- só o veredito e a ferramenta de medida voltam à branch da etapa.

**Arquivos (branch do spike):**
- `jogos/fliperama-prototipo/` (projeto Godot: `project.godot`, `principal.tscn`, `principal.gd`, `web/{shell.html,iniciar.js,jogo.css}`, `export_presets.cfg`)
- `src/main/java/.../prototipo/EcoPrototipoSocket.java` (eco WebSocket em `/ws/eco-prototipo`)
- CSP por rota, em rascunho
- **Voltam para a branch da etapa:**
  - `scripts/android/jogo-tempo.mjs`;
  - `docs/adr/0006-fliperama.md` (rascunho com o veredito);
  - `.claude/handoff/spike-fliperama.md` (fora do Git).

**Interfaces (produz):**
- `node scripts/android/jogo-tempo.mjs --porta <p> --caminho /jogos/fliperama/index.html --execucoes 5`. Com o emulador ligado (`scripts/android/emulador.sh subir`), a cada execução, pelo CDP do Chrome do AVD (mesmo esquema do `pwa-check.mjs`):
  - `Storage.clearDataForOrigin` (`all`) e `Network.clearBrowserCache`;
  - `Network.emulateNetworkConditions { latency: 170, downloadThroughput: 1125000, uploadThroughput: 1125000 }`;
  - `Emulation.setCPUThrottlingRate { rate: 4 }`;
  - navegar e esperar `performance.getEntriesByName("duora-jogo-pronto")`.
  - Imprime JSON `{ execucoes: [ms...], max, mediana, bytesTransferidos }` e grava uma captura por execução em `target/android/jogo-<n>.png`.
  - Sai com código 1 se alguma execução passar de 5000 ms ou se não houver marca em 20 s.
- Veredito no ADR:
  - versão exata do Godot;
  - template usado (padrão ou enxuto, com a receita);
  - tamanhos brutos e `.br` de cada arquivo;
  - os 5 tempos;
  - a lista de violações de CSP (deve ser vazia);
  - o orçamento de bytes que vira o limite do `TamanhoDoFliperamaTest` (Task 19): o total `.br` medido + 10 %.

- [ ] **Passo 1: ambiente.**
  - Instalar o Godot 4 estável (versão exata com ≥ 14 dias) em `/Applications/Godot.app` e os export templates da mesma versão em `~/Library/Application Support/Godot/export_templates/<versao>.stable/`.
  - Conferir `godot --version`.
  - Ligar o plugin do MCP `Godot-AI` no projeto do protótipo e conferir a sessão (`session_manage`/`editor_state`).
- [ ] **Passo 2: protótipo pelo MCP.**
  - `project_manage` cria `jogos/fliperama-prototipo/`.
  - `scene_manage`/`node_create` montam `principal.tscn`: `Node2D` com dois `Polygon2D` (luzes), um `Sprite2D` (barco) e um `Label`.
  - `script_create` escreve o `principal.gd`:
    - lê `location` por `JavaScriptBridge.get_interface("location")` (**nunca** `JavaScriptBridge.eval`, que exige `unsafe-eval`);
    - abre um `WebSocketPeer` para `ws(s)://<host>/ws/eco-prototipo`;
    - envia o ângulo pelas setas ou pelo arraste;
    - desenha o ângulo ecoado;
    - depois do primeiro quadro (`await get_tree().process_frame`), chama `JavaScriptBridge.get_interface("performance").mark("duora-jogo-pronto")`.
  - Conferir com `project_run` + `editor_screenshot`.
- [ ] **Passo 3: export.** Preset "Web" com:
  - `variant/thread_support=false`;
  - `html/custom_html_shell="res://web/shell.html"`.
  - O shell não tem script nem estilo inline: a configuração vai num `<script type="application/json" id="godot-config">$GODOT_CONFIG</script>` (bloco de dados, que a CSP não executa), lido pelo `iniciar.js`; o CSS fica em `jogo.css`.
  - Exportar com a trava: `godot --headless --path jogos/fliperama-prototipo --export-release "Web" <saida>/index.html`.
  - Comprimir `.wasm`, `.pck` e `.js` com `brotli -q 11` (mantendo os originais).
- [ ] **Passo 4: servir.**
  - Copiar o export para `static/jogos/fliperama/`.
  - Ligar `spring.web.resources.chain.compressed=true`.
  - CSP do jogo só nessa rota: `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'self'; form-action 'self'`.
  - Conferir com `curl -sI -H 'Accept-Encoding: br'`: `Content-Type: application/wasm` e `Content-Encoding: br`.
- [ ] **Passo 5: desktop.**
  - Pelo Playwright MCP, abrir `/` com um iframe de teste para o jogo e coletar `securitypolicyviolation` + console: zero violações.
  - Conferir o eco do WebSocket e o áudio liberado no primeiro toque (`allow="autoplay"` no iframe).
- [ ] **Passo 6: medida no Android** (com a trava). Rodar o `jogo-tempo.mjs` com o template padrão.
  - Se alguma execução passar de 5 s: compilar o template enxuto e medir de novo. Receita: emsdk na versão que a doc do Godot fixa, `scons platform=web target=template_release threads=no disable_3d=yes optimize=size_extra lto=full`, com um build profile que desliga as classes não usadas.
  - Se ainda passar, registrar e abrir a decisão D3, sem seguir para as Tasks 17–21 até a resposta.
- [ ] **Passo 7: captura.**
  - Commit do protótipo na branch `spike/fliperama-godot`: `chore(prototipo): spike do fliperama (descartável)`.
  - Na branch da etapa: `scripts/android/jogo-tempo.mjs` limpo e o rascunho do ADR 0006, commit `docs(adr): 0006 fliperama — veredito do spike`.
  - O handoff aponta para a branch do spike.

### Task 16: Contratos, ADR e CSP por rota

**Arquivos:**
- Modificar:
  - `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`:
    - em "Tempo real": "Exceção: o fliperama (9b) usa WebSocket em `/ws/fliperama`, só para o jogo; os eventos do encontro continuam no SSE.";
    - em "Assets": "Fliperama: fundo no Tripo; barcos e faróis vetoriais no Godot (`Polygon2D`).".
  - `docs/adr/0006-fliperama.md`: completo, com Contexto, Decisão, Consequências e Alternativas (lockstep rejeitado, ver abaixo).
  - `CabecalhosDeSeguranca.java` (em `web/` a partir da Etapa 2);
  - `application.properties` (`spring.web.resources.chain.compressed=true`);
  - o mapa de MIME do `web/` (`.wasm` → `application/wasm`, `.pck` → `application/octet-stream`).
- Teste: `web/CspDoJogoTest.java` (MockMvc)

**Interfaces (produz):**
- `CabecalhosDeSeguranca.CSP_JOGO`: a string do Passo 4 da Task 15.
- Aplicada quando `request.getRequestURI()` começa com `/jogos/fliperama/` (com a barra final). Toda outra rota mantém a `CSP` atual, byte a byte.

**Decisão de sincronia (registrada no ADR):** servidor autoritativo, não lockstep.
- **Fonte única da verdade:** o servidor simula e guarda o placar e o recorde conjunto (o spec pede que o servidor guarde o placar). Com lockstep, o placar sairia dos clientes e não seria confiável.
- **Sem exigência de determinismo** entre navegadores e aparelhos. O lockstep precisaria de matemática bit a bit idêntica no Godot e de replay na reconexão.
- **Testável em JUnit** como função pura, no estilo do `RegrasTest`.
- **Cliente fino:** só desenha e envia a mira. Isso mantém a saída da D3(b) barata, porque outro cliente fala o mesmo protocolo.
- **Ordem e idempotência (DDIA):** a ordem vem do `tick` do servidor (sequência lógica, nunca timestamp). As entradas carregam `seq` por assento, são absolutas (o alvo do farol, não um delta) e são aplicadas só se `seq > ultimoSeq`. Repetição e reordenação não mudam o resultado.
- **Custo aceito:** até 10 partidas × 20 Hz em uma thread; a latência da luz do parceiro é escondida por interpolação de 100 ms. A luz própria é prevista no cliente.

- [ ] **Passo 1:** `CspDoJogoTest`:
  - `paginaDoJogoTemCspDoJogo` (`/jogos/fliperama/index.html` → `CSP_JOGO`);
  - `appMantemACspDeHoje` (`/` e `/api/conta` → `CSP` exata);
  - `caminhoParecidoNaoGanhaCspDoJogo` (`/jogos/fliperama-x/index.html`, `/jogos/fliperamaindex.html`);
  - `wasmServidoComTipoCerto`;
  - `wasmPreComprimidoComBrotli` (`Accept-Encoding: br` → `Content-Encoding: br` e `Vary: Accept-Encoding`; usar um `index.wasm` mínimo de teste em `src/test/resources/static/jogos/fliperama/`).
- [ ] **Passo 2:** RED; implementar; verde (`./mvnw -q verify`).
- [ ] **Passo 3:** commits separados:
  - `docs: contrato de tempo real com a exceção do fliperama` + `docs(adr): 0006 fliperama`;
  - `feat(web): CSP do jogo só na rota do fliperama e wasm pré-comprimido`.

### Task 17: Simulação dos faróis (domínio puro)

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/fliperama/dominio/{ParametrosDoJogo,EstadoDoJogo,FaseDoJogo,Farol,Barco,Entrada,Luz,Simulacao}.java`
- Teste: `src/test/java/bipotheking/net/duora/fliperama/dominio/SimulacaoDosFaroisTest.java`

**Interfaces (produz):** sem Spring, sem Jackson (fronteira do contrato); ArchUnit confere.
- `record ParametrosDoJogo(Duration rodada, Duration tick, double velocidadeAngular, double meiaAbertura, Duration tempoParaGuiar, int barcosMax, Duration intervaloInicial, Duration intervaloFinal)`. `PADRAO` = 180 s, 50 ms, 120°/s, 9°, 1 s, 6, 6 s, 2,5 s (ângulos em radianos).
- Campo: 600 × 900 unidades.
  - Farol A em (60, 880) e B em (540, 880). O ângulo 0 aponta para cima, e o alvo fica limitado a ±80°.
  - Os barcos nascem em y = −20, com x uniforme em [80, 520], e descem a 45 u/s.
  - Guiados, vão ao porto (300, 900) a 90 u/s e somam ao chegar (distância < 10).
  - À deriva com y ≥ 800: removidos, com `perdidos + 1`.
- `sealed interface FaseDoJogo permits Aguardando, Jogando, Fim`, com:
  - `record Aguardando(Set<Assento> prontos)`;
  - `record Jogando(Duration restante)`;
  - `record Fim(int salvos)`;
  - `String nome()` = `"AGUARDANDO"`, `"JOGANDO"` ou `"FIM"`.
- `record Farol(double angulo, double alvo)`; `record Barco(long id, double x, double y, Duration progresso, boolean guiado)`; `enum Luz { NENHUMA, A, B, AMBOS }`.
- `record EstadoDoJogo(long tick, FaseDoJogo fase, Map<Assento, Farol> farois, List<Barco> barcos, int salvos, int perdidos, int recorde, int rodadas, long proximoId, Duration ateProximoBarco)`.
- `sealed interface Entrada permits Mirar, Pronto`, com `record Mirar(Assento quem, double alvo)` e `record Pronto(Assento quem)`.
- `final class Simulacao`:
  - `static EstadoDoJogo inicial(int recorde, int rodadas)`;
  - `static EstadoDoJogo aplicar(EstadoDoJogo e, Entrada entrada)`;
  - `static EstadoDoJogo avancar(EstadoDoJogo e, ParametrosDoJogo p, RandomGenerator aleatorio)`, que avança um tick;
  - `static Luz luzEm(EstadoDoJogo e, Barco b, ParametrosDoJogo p)`.
- Regras:
  - `Pronto` em `Aguardando` soma; com os dois, vira `Jogando(rodada)` e zera barcos, salvos e perdidos.
  - `Pronto` em `Fim` vira `Aguardando({quem})`.
  - Em `Jogando`: `progresso` cresce com `AMBOS`, fica parado com uma luz só e zera sem luz. Chegando a `tempoParaGuiar`, o barco fica guiado.
  - O intervalo de nascimento cai linearmente do inicial ao final ao longo da rodada.
  - Com `restante` em 0: `Fim(salvos)`, `recorde = max`, `rodadas + 1` e barcos limpos.
  - O farol gira em direção ao alvo em qualquer fase.

- [ ] **Passo 1:** `SimulacaoDosFaroisTest`:
  - `farolGiraNoMaximoAVelocidadeAngular` (1 tick → 6°);
  - `alvoForaDoLimiteELimitado` (90° → 80°);
  - `umProntoSoEspera`; `doisProntosComecamARodada`; `prontoRepetidoNaoMudaNada`;
  - `barcoIluminadoPelosDoisPorUmSegundoEGuiado` (20 ticks);
  - `luzDeUmSoSeguraOProgressoSemGuiar`; `semLuzZeraOProgresso`;
  - `barcoGuiadoChegaAoPortoESoma`;
  - `barcoADerivaQuePassaDaLinhaVoltaAoMar`;
  - `rodadaTerminaEmCentoEOitentaSegundosEGuardaORecorde`; `recordeNuncaDiminui`;
  - `mesmaSementeMesmosBarcos`;
  - `nuncaPassaDoMaximoDeBarcos`;
  - `luzEmUsaOCone` (um barco a 300 u acima do farol A, com o ângulo 0, é `A`; a 30° fora do cone é `NENHUMA`).
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(fliperama): simulação pura dos dois faróis`.

### Task 18: Servidor do fliperama — WebSocket autoritativo

**Arquivos:**
- Modificar: `pom.xml` (`spring-boot-starter-websocket`, versão gerida pelo Boot); `encontro/Encontros.java` e `encontro/RetratoJson.java` (registro genérico do jogo); `encontro/Visao.java` (`jogo`)
- Criar: `src/main/java/bipotheking/net/duora/fliperama/`, com:
  - `PartidaDeFliperama` e `MesaDeFliperama` (registro limitado);
  - `RelogioDoFliperama` (uma thread, 20 Hz);
  - `SocketDoFliperama` (`TextWebSocketHandler`) e `AutenticacaoDoSocket` (`HandshakeInterceptor`);
  - `ProtocoloDoFliperama` (JSON ↔ records, com Jackson; é adapter);
  - `EncontrosDoFliperama` (porta) e `EncontrosDoFliperamaAdapter`;
  - `FliperamaConfig`.
- Teste: `fliperama/PartidaDeFliperamaTest.java`, `fliperama/ProtocoloDoFliperamaTest.java`, `fliperama/SocketDoFliperamaTest.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers + `StandardWebSocketClient`)

**Interfaces:**
- Consome:
  - Task 17;
  - Etapa 2: `conta.Sessoes.validar(String token): Optional<SessaoAtiva>`, cookie `duora_sessao`;
  - Etapa 5: `Encontros.ativoDe(ContaId)`, `Participante`, `RetratoJson` (formato 1, leitor tolerante).
- Produz:
  - `encontro`: `record RegistroDoJogo(int recorde, int rodadas)`. O Retrato do encontro ganha `RegistroDoJogo jogo`, que o `RetratoJson` grava como campo opcional (formato continua 1). `public void registrarRodadaDoJogo(String encontroId, int pontos)`: `recorde = max`, `rodadas + 1`, por write-through com versão.
  - A `Visao` ganha `"jogo": { "recorde": n, "rodadas": n } | null`.
  - O `encontro` não conhece o `fliperama`; a dependência é só `fliperama → encontro`, sem ciclo.
  - Porta `EncontrosDoFliperama`:
    - `Optional<Participacao> participacao(ContaId conta)`, onde `record Participacao(String encontroId, Assento assento, String apelidoDoParceiro)` só existe se o encontro está ativo e o cenário é `"fliperama"`;
    - `boolean ativo(String encontroId)`;
    - `RegistroDoJogo registro(String encontroId)`;
    - `void registrarRodada(String encontroId, int salvos)`.
  - Handshake em `/ws/fliperama` (`AutenticacaoDoSocket`):
    - `Origin` diferente da origem da própria requisição → 403;
    - sem cookie, ou sessão inválida → 401;
    - sem `participacao` → 403;
    - limite de 10 handshakes por conta por minuto (Bucket4j) → 429.
    - Os atributos da sessão WS são `conta`, `encontroId` e `assento`.
  - Protocolo (JSON em frames de texto, `v` 1):
    - **Cliente → servidor:**
      - `{"t":"mirar","seq":<long ≥ 1>,"alvo":<double rad>}`;
      - `{"t":"pronto","seq":<long ≥ 1>}`.
    - **Servidor → cliente:**
      - ao conectar: `{"t":"ola","v":1,"assento":"A","parceiro":"<apelido>","ultimoSeq":<long>,"estado":{...}}`;
      - a cada 2 ticks (10 Hz): `{"t":"estado","tick":<long>,"fase":"AGUARDANDO|JOGANDO|FIM","restanteMs":<int>,"prontos":["A"],"farois":{"A":{"angulo":<3 casas>},"B":{...}},"ultimoSeq":{"A":<long>,"B":<long>},"barcos":[{"id":<long>,"x":<1 casa>,"y":<1 casa>,"luz":"NENHUMA|A|B|AMBOS","progresso":<0..1, 2 casas>,"guiado":<bool>}],"salvos":<int>,"perdidos":<int>,"recorde":<int>,"rodadas":<int>,"parceiroConectado":<bool>}`;
      - uma vez por rodada: `{"t":"fimDaRodada","rodada":<int>,"salvos":<int>,"recorde":<int>,"novoRecorde":<bool>}`;
      - `{"t":"erro","codigo":"MENSAGEM_INVALIDA"}` para JSON inválido, `t` desconhecido ou `seq` ausente, sem fechar.
    - Fechamentos:
      - 4000 "substituída" (o mesmo assento conectou de novo);
      - 4100 "encontro encerrado";
      - 1008 rajada: mais de 30 mensagens/s sustentadas por 3 s; abaixo disso, o excedente é descartado;
      - 1009 frame acima de 512 bytes.
  - `PartidaDeFliperama` (uma por encontro, com lock próprio):
    - `ultimoSeq` por assento, aplicado só se `seq > ultimoSeq`;
    - uma sessão por assento, e a nova fecha a antiga com 4000;
    - nasce com `Simulacao.inicial(registro.recorde(), registro.rodadas())`;
    - com um assento desconectado em `JOGANDO`, **não avança**, e a rodada pausa. Passados 60 s assim, encerra a rodada como `Fim` (`registrarRodada` com o que houver);
    - na transição para `Fim`, chama `registrarRodada` uma vez só;
    - é removida 60 s depois que as duas sessões caem.
  - `MesaDeFliperama`: no máximo `duora.encontros.simultaneos` partidas. Acima disso, o handshake responde 503.
  - `RelogioDoFliperama`:
    - `ScheduledExecutorService` de uma thread, `fliperama-relogio`, a cada 50 ms;
    - passo fixo, com no máximo 3 ticks de recuperação por volta (o resto é descartado);
    - a cada 20 ticks confere `ativo(encontroId)`; encerrado → fecha com 4100 e remove;
    - o envio passa por `ConcurrentWebSocketSessionDecorator(sessao, 1000, 65536, OverflowStrategy.DROP)`. Um retrato perdido é substituído pelo próximo, porque cada retrato é o estado completo.
  - Propriedades: `duora.fliperama.rodada` (padrão `PT180S`; `PT2S` nos testes) e `duora.fliperama.semente` (opcional, para testes).

- [ ] **Passo 1:** escrever os testes.
  - `ProtocoloDoFliperamaTest`:
    - `estadoTemExatamenteAsChavesDoContrato`;
    - `numerosArredondados`;
    - `mensagemSemSeqViraErro`;
    - `tipoDesconhecidoViraErro`.
  - `PartidaDeFliperamaTest` (sem rede, com sessões falsas):
    - `seqRepetidoOuAntigoEIgnorado`;
    - `parceiroDesconectadoPausaARodada`;
    - `pausaLongaEncerraARodada` (com o relógio injetado);
    - `fimRegistraARodadaUmaVez`;
    - `nasceComORecordeDoEncontro`.
  - `SocketDoFliperamaTest`:
    - `semSessaoOHandshakeERecusado`;
    - `origemDeOutroSiteERecusada`;
    - `contaForaDoEncontroERecusada`;
    - `encontroDeOutroCenarioERecusado`;
    - `osDoisRecebemOlaComAssentoEParceiro`;
    - `mirarDeUmApareceNoEstadoDosDois`;
    - `entradaRepetidaOuAtrasadaEIgnorada`: seq 5 e depois seq 3 → `ultimoSeq.A == 5` e o alvo de 5;
    - `reconexaoSubstituiASessaoAntigaEContinuaOSeq`: a antiga recebe 4000, a nova recebe `ola.ultimoSeq == 5`;
    - `doisProntosERodadaCurtaTerminamComRecorde`: com `PT2S`, os dois recebem `fimDaRodada`, e `GET` do encontro traz `jogo.recorde` igual a `salvos`;
    - `mensagemGrandeDemaisFechaCom1009`;
    - `rajadaDeEntradasFechaCom1008`;
    - `encontroEncerradoFechaCom4100`: `POST` de sair → os dois sockets fecham com 4100 em ≤ 2 s.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. O `fliperama` não importa classes internas do `encontro` (ArchUnit) e usa só a API pública pelo adapter.
- [ ] **Passo 4:** `./mvnw -q verify` com a trava (uma suíte com Docker por vez). Commit `feat(fliperama): servidor autoritativo por WebSocket com entradas idempotentes`.

### Task 19: Cliente Godot "Dois faróis", assets e export

**Arquivos:**
- Criar o projeto `jogos/fliperama/`:
  - `project.godot`, `export_presets.cfg`, `cenas/principal.tscn`;
  - `scripts/{rede.gd,protocolo.gd,interpolacao.gd,farol.gd,barco.gd,controles.gd,ponte.gd,telas.gd}`;
  - `web/{shell.html,iniciar.js,jogo.css}`;
  - `arte/fundo.webp`, `som/*.mp3`;
  - os testes em `testes/`;
  - `exportar.sh`.
- Criar o export: `src/main/resources/static/jogos/fliperama/*` (arquivos originais + `.br`)
- Criar: `src/test/java/bipotheking/net/duora/pwa/TamanhoDoFliperamaTest.java`
- Modificar: `assets/manifest.md` (seção `## Fliperama`)

**Interfaces:**
- Consome: o protocolo da Task 18 (exato); o shell, a CSP e o template do veredito da Task 15.
- Produz:
  - `protocolo.gd`:
    - `func mirar(alvo: float) -> String` e `func pronto() -> String`, que incrementam o `seq` local;
    - `func receber_ola(msg: Dictionary)` ajusta o `seq` a `ultimoSeq` + 1;
    - `func ler(texto: String) -> Dictionary` (vazio se inválido).
  - `interpolacao.gd`:
    - `func empurrar(estado: Dictionary)` descarta `tick` ≤ último;
    - `func amostra(agora_ms: int) -> Dictionary` interpola barcos e o farol do parceiro com atraso de 100 ms.
  - `rede.gd`:
    - `WebSocketPeer` para `<ws|wss>://<location.host>/ws/fliperama`;
    - reconexão com espera de 0,5 s × 2ⁿ até 8 s, com jitter de ±20 %;
    - ao reconectar, continua o `seq` pelo `ola`.
  - `ponte.gd` (`postMessage` ao pai, com `targetOrigin = location.origin`):
    - envia `{tipo:"duora.jogo.pronto"}`, `{tipo:"duora.jogo.fase", fase}` e `{tipo:"duora.jogo.rodada", salvos, recorde, novoRecorde}`;
    - recebe `{tipo:"duora.jogo.som", ligado}`, ignorando outra origem;
    - marca `performance.mark("duora-jogo-pronto")` depois do primeiro quadro;
    - não usa `JavaScriptBridge.eval`.
  - Visual:
    - luz própria prevista localmente, girando a 120°/s até o alvo;
    - luz do parceiro e barcos pelos retratos interpolados;
    - barcos e faróis em `Polygon2D` (vetor, licença `Própria`);
    - o anel do barco mostra a cor de cada luz que o toca e o progresso.
  - Textos: os de D-FL. Respeita `prefers-reduced-motion` (sem ondas animadas no fundo).
  - Assets:
    - Tripo, 1 imagem: "calm night sea seen from above, dark blue with gentle waves, a rocky harbor entrance at the bottom center, painterly illustration, portrait 2:3, no lighthouses, no boats, no text". Master em `assets/source/fundos/fliperama/`; `cwebp -q 88` para 1200×1800 em `arte/fundo.webp`; import no Godot com `compress/mode` Lossy e `lossy_quality` 0,88.
    - ElevenLabs: `som/trilha.mp3` (60 s em laço, "calm night synthwave, soft arpeggios, 80 bpm, instrumental"), `som/guiado.mp3` (cerca de 1 s, "soft rising chime"), `som/inicio.mp3` (cerca de 1 s, "gentle start bell") e `som/fim.mp3` (cerca de 3 s, "warm resolving chord"), todos MP3 192 kbps / 48 kHz.
  - `exportar.sh` (com a trava): exporta o preset "Web" (single-thread, shell próprio, template do veredito) para `static/jogos/fliperama/`, comprime com `brotli -q 11` e imprime os tamanhos.
  - `TamanhoDoFliperamaTest.totalComprimidoNoOrcamento`: a soma dos `.br` de `static/jogos/fliperama/` ≤ o orçamento do ADR 0006, numa constante com comentário apontando o ADR.
  - Os exports são versionados, como os renders. O `.wasm` do template só muda com a versão do Godot.

- [ ] **Passo 1:** testes do cliente com o framework que o `test_manage` do Godot-AI suportar (GdUnit4, de preferência), rodados pelo `test_run`:
  - `seqCresceEContinuaDoOla`;
  - `mensagemInvalidaViraVazio`;
  - `estadoAntigoEDescartado`;
  - `interpolaEntreDoisRetratos`;
  - `reconectaComEsperaCrescente` (0,5 → 1 → 2 → … ≤ 8 s, dentro de ±20 %);
  - `mensagemDeOutraOrigemEIgnorada`.
- [ ] **Passo 2:** RED; implementar pelo MCP Godot-AI (`scene_manage`, `node_create`, `script_create`/`script_patch`, `input_map_manage` para as setas e Enter, `ui_manage` para as telas); verde. Conferir as telas com `project_run` + `editor_screenshot`.
- [ ] **Passo 3:** assets (saldo e D1 antes; manifesto completo).
- [ ] **Passo 4:**
  - `exportar.sh`;
  - `TamanhoDoFliperamaTest` verde;
  - `ServiceWorkerVersaoTest` verde (a impressão muda);
  - jogo aberto direto em `/jogos/fliperama/index.html` no Playwright MCP, sem violação de CSP.
  - Commits:
    - `feat(fliperama): cliente Godot dos dois faróis`;
    - `feat(assets): fundo e sons do fliperama`;
    - `build(fliperama): export web single-thread`.

### Task 20: Fliperama no encontro — cenário de jogo, iframe e e2e

**Arquivos:**
- Modificar:
  - `game/Cenario.java`: `sealed interface Atividade permits Atividade.Montagem, Atividade.Jogo`, com `record Montagem()` e `record Jogo(String caminho)`. O `Cenario` ganha `Atividade atividade` como último componente, e um construtor secundário sem ele assume `Montagem` (as chamadas existentes não mudam).
  - `game/Regras.java`: com `Jogo`, `colocar`, `sinalizar` e `confirmar` recusam com `FASE_INVALIDA`.
  - `CatalogoDeCenarios`: registrar `Fliperama.CENARIO`.
  - `CenarioJsonTest`: os cenários `Jogo` ficam fora dos testes de montagem e têm o seu.
  - Front:
    - `frontend/src/cenario/esquema.ts`: o cenário vira uma união por `atividade.tipo` — `"montagem"` (o esquema atual) ou `"jogo"` (`{ tipo: "jogo"; src: string; titulo: string }`, com `src` começando por `/jogos/`);
    - a tela do cenário: com `jogo`, renderiza `<JogoEmbutido>` no lugar de `<Cena>`, `<Inventario>` e `<Resultado>`;
    - `frontend/src/sw/rotas.ts`: `/jogos/` → cache primeiro em tempo de execução, sem precache, no cache `duora-<versão>`.
- Criar:
  - `game/Fliperama.java` (`CENARIO` id `"fliperama"`, sem lugares nem peças, desfecho vazio, `Jogo("/jogos/fliperama/")`);
  - `static/assets/cenarios/fliperama/cenario.json` (`atividade`, `titulo` "Dois faróis", `sons` vazio);
  - `frontend/src/componentes/JogoEmbutido.tsx` e `frontend/src/jogo/ponte.ts`.
- Teste:
  - `game/FliperamaCenarioTest.java`;
  - `JogoEmbutido.test.tsx`, `ponte.test.ts`, `esquema.test.ts` (o JSON do jogo), `rotas.test.ts`;
  - `e2e/FliperamaIT.java`, `e2e/AxeIT.java` (estado "fliperama").

**Interfaces (produz):**
- `<JogoEmbutido src titulo somLigado />` renderiza `<iframe src={src} title={titulo} sandbox="allow-scripts allow-same-origin" allow="autoplay" loading="eager">` dentro de um contêiner `data-jogo="carregando|pronto"`, mais um `<p role="status">` com os textos de D-FL para o pai.
  - Mantém o iframe com foco por Tab e o rótulo "Dois faróis".
  - Ao mudar `somLigado`, envia `duora.jogo.som`.
- `ponte.ts`: `ouvirJogo(janela: Window, iframe: HTMLIFrameElement, aoEvento: (e: EventoDoJogo) => void): () => void`.
  - Aceita só `event.origin === location.origin && event.source === iframe.contentWindow`.
  - `type EventoDoJogo = { tipo: "pronto" } | { tipo: "fase"; fase: "AGUARDANDO" | "JOGANDO" | "FIM" } | { tipo: "rodada"; salvos: number; recorde: number; novoRecorde: boolean }`, validado com valibot.
- O encontro com fliperama continua tendo chat, voz, sair, denunciar e 30 min (Etapa 5). O fim do encontro desmonta o iframe.

- [ ] **Passo 1:** escrever os testes.
  - `FliperamaCenarioTest`: `colocarEConfirmarSaoRecusados` e `terracoContinuaMontagem`.
  - Vitest:
    - `mensagemDeOutraOrigemEIgnorada`;
    - `mensagemDeOutroFrameEIgnorada`;
    - `rodadaAnunciaNoStatus` ("Vocês guiaram 3 barcos. Recorde de vocês: 5.");
    - `somMudaEnviaMensagem`;
    - `rotaDosJogosECachePrimeiro`.
  - `FliperamaIT` (dois contextos, Ana em desktop e Bia em 390×844 com toque; `duora.cenarios.sorteaveis=terraco,fliperama` e `duora.fliperama.rodada=PT5S` no perfil e2e):
    - `osDoisVeemOJogoPronto`: o iframe "Dois faróis" e `data-jogo="pronto"` em ≤ 15 s, nos dois lados;
    - `prontoDosDoisComecaARodada`: foco no iframe, Enter nos dois → status "A rodada começou." nos dois;
    - `miraDeAnaChegaABia`: Ana segura ← por 500 ms → em Bia, um frame WS recebido (`page.onWebSocket` → `framereceived`) com `farois.A.angulo < 0`;
    - `fimDaRodadaMostraORecordeNosDois`: depois de 5 s, os dois status batem com "Vocês guiaram \d+ barcos. Recorde de vocês: \d+.";
    - `semViolacaoDeCsp`: nenhum console de erro com "Content Security Policy" nas duas páginas, inclusive no frame;
    - `saidaDuranteORodadaEncerraParaOsDois`: Bia sai → Ana vê "O encontro terminou" e o iframe some.
  - `AxeIT`: estado "fliperama" (título do iframe, status) sem violações.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. Commits separados:
  - `refactor(game): atividade do cenário como tipo selado` (só a Montagem, com a suíte verde);
  - `feat(fliperama): jogo embutido no encontro`.
- [ ] **Passo 4:** `./mvnw -q verify`, `pnpm test`, o `FliperamaIT` + `AxeIT` nos 3 navegadores (um por vez) e o `TamanhoDoJsInicialTest` ≤ 80 KB (o jogo não entra no JS inicial), tudo verde.

### Task 21: Prova de 5 s no Android e playtest do fliperama

**Arquivos:**
- Modificar: `scripts/android/jogo-tempo.mjs` (aceita `--caminho` e `--execucoes`; sem mudança de critério)
- Criar: `.claude/handoff/playtest-9b/fliperama/` (fora do Git)

- [ ] **Passo 1:** prova (com a trava): app numa porta livre, `scripts/android/emulador.sh subir`, `node scripts/android/jogo-tempo.mjs --porta <p> --caminho /jogos/fliperama/index.html --execucoes 5`. Esperado: código 0, as 5 execuções ≤ 5000 ms. Registrar o JSON e as capturas no handoff e no ADR 0006 ("Prova"). Desligar o emulador.
- [ ] **Passo 2:** playtest por agente novo (P-P, com o roteiro trocado):
  - Ana em desktop e Bia no contexto de celular;
  - 3 rodadas: a 1ª sem combinar nada; a 2ª combinando pelo chat do encontro quem cobre qual lado; a 3ª tentando bater o recorde;
  - depois, Bia sai.
  - Critérios de "sim":
    - **2:** em cada captura de jogo, as duas luzes aparecem com cores diferentes, e o anel do barco mostra as duas;
    - **3:** houve coordenação no chat, e a 2ª rodada teve mais barcos guiados que a 1ª, ou o agente explica por que não;
    - **4:** os números de cada rodada diferem e batem com o que as capturas mostram;
    - **5:** "Jogar de novo" está a 1 toque, e o agente jogou a 3ª rodada por escolha.
- [ ] **Passo 3:** fechamento do P-P 6 (pacote da D4 do fliperama) e commit `docs(adr): 0006 com a prova no Android`.

### Task 22: Liberação no sorteio e fechamento da etapa

**Arquivos:**
- Modificar: `src/main/resources/application.properties` (`duora.cenarios.sorteaveis`), `assets/manifest.md` (status `FINAL` só do que o dono aprovou), `.claude/handoff/_mapa.md` (pacote `fliperama/`, `jogos/`, pipeline por cenário)

- [ ] **Passo 1:** para cada cenário com o "sim" do dono na D4, acrescentar o id em `duora.cenarios.sorteaveis`, na ordem `terraco,aquario,piquenique,cachoeira,jantar,fliperama` (só os aprovados). O `CatalogoDeCenariosTest.sorteaveisSegueAPropriedade` (Etapa 4) continua verde.
- [ ] **Passo 2:** `SorteioComOsSeisIT` (com a Etapa 4 integrada):
  - `@ParameterizedTest cadaCenarioLiberadoAbrePeloConvite`: para cada id liberado, um convite privado com o cenário abre a tela certa (o heading do `titulo` ou o iframe "Dois faróis");
  - `pareamentoPublicoSorteiaUmInedito`: duas contas que já jogaram o terraço são pareadas em outro cenário.
- [ ] **Passo 3:** a suíte inteira: unidade, Vitest e e2e nos 3 navegadores, um por vez. Depois, a revisão final (`superpowers:requesting-code-review` + `mattpocock-skills:code-review`) e `duora-etapa`. Commit `feat(sala): cenários da 9b liberados no sorteio`.

## Ordem e paralelismo
- **Pré-requisitos:**
  - Task 1: depois da 1b Task 3, quando os assets já estão em `assets/cenarios/terraco`.
  - Task 2: depois da 1c.
  - Tasks 15 e 17: podem começar logo depois da 1c, porque não dependem de conta. O spike cedo reduz o risco do fliperama.
- **Assets (Tasks 3, 6, 9, 12):** depois da Task 1, em paralelo, no máximo 4 subagentes ao mesmo tempo, somando todas as tasks.
  - As gerações na nuvem (Hi3D, Tripo, ElevenLabs) correm em paralelo.
  - **Render no Blender, export do Godot, Maven e emulador são serializados pela trava da máquina:** quem não pega a trava espera, sem iniciar outro pesado.
  - Cada worktree precisa dos GLBs do seu cenário (passo 1 do pipeline da skill).
- **Dados (Tasks 4, 7, 10, 13):**
  - Passos 1–2 (Java: `Cenario`, `Desfecho`, testes e catálogo): só dependem da 1b, e podem correr junto dos assets.
  - Passos 3–4 (`cenario.json`, e2e, axe): esperam a Task 2 e a task de assets do mesmo cenário.
  - O catálogo recebe uma linha por cenário; o merge resolve à mão.
- **Playtests (Tasks 5, 8, 11, 14):** depois da task de dados do cenário, e portanto depois da 1c. Um playtest por vez na máquina (um servidor só, e Playwright MCP serializado).
- **Fliperama:**
  1. Task 15 (spike);
  2. Task 16;
  3. Task 17, independente;
  4. Task 18, que espera a Etapa 5 (contas e encontro) e a Task 17;
  5. Task 19, em paralelo à 18, porque o protocolo está fixado aqui;
  6. Task 20, depois da 18, da 19 e da Etapa 5;
  7. Task 21.
  - A D3, se aberta, bloqueia as Tasks 17–21 até a resposta, e só elas.
- **Fim:** a Task 22 vem depois das respostas da D4 e com a Etapa 4 integrada. Os cenários aprovados podem ser liberados aos poucos, numa Task 22 por lote.

## Decisões do dono
- **D1. Lote de créditos de imagem (Tripo) e áudio (ElevenLabs).**
  - Tripo: 8 fundos de cenário mais 1 do fliperama = 9 imagens × 40 = **cerca de 360 créditos**.
  - ElevenLabs: cerca de 6 trilhas longas, 9 ambiências e efeitos curtos, 4 trilhas de 8 s e 4 sons do jogo, estimados em **10–14 mil créditos** pela média do terraço.
  - Opções:
    - (a) aprovar o lote inteiro agora;
    - (b) aprovar por cenário, na ordem aquário → piquenique → cachoeira → jantar → fliperama.
  - **Recomendação: (a)**, com o saldo conferido antes de cada cenário. Assim nenhum agente para no meio esperando aceite; o custo é o mesmo.
- **D2. Fallback pago se o Hi3D falhar ou faltar saldo** (o lote precisa de 29 props × 49 = **cerca de 1.421 pts**). Opções:
  - (a) esperar a renovação da assinatura e seguir só com o Hi3D;
  - (b) Tripo (`image_to_3d`, créditos da conta Tripo);
  - (c) Rodin-Business (créditos pagos da Business API, com `rodin_estimate` antes).
  - **Recomendação: (a)** se a renovação vier em até uma semana, porque nada bloqueia o resto (dados e fliperama seguem). Senão, **(b)**, pelo mesmo fluxo imagem → 3D já usado.
- **D3. Só se o spike reprovar o orçamento de 5 s mesmo com o template enxuto.** Opções:
  - (a) manter o Godot e aceitar o tempo medido, com tela de carregamento;
  - (b) trocar só o cliente para TypeScript + Canvas 2D: o mesmo protocolo e o servidor intactos, cerca de 50 KB; perde o "feito em Godot" do portfólio;
  - (c) pré-carregar o jogo durante o sorteio, o que muda a prova, porque deixa de ser cache frio.
  - **Recomendação: (b)**, porque cumpre a prova do spec sem mexer no servidor nem no protocolo.
- **D4. Aceite de cada cenário (playtest do dono, exigido pelo spec).** Para cada cenário chegam:
  - as 4 melhores capturas (montagem e revelação, desktop e celular);
  - o relatório do agente;
  - a escolha **aprovar** (entra no sorteio e os assets viram `FINAL`) ou **ajustar** (com 2–3 ajustes sugeridos).
  - **Recomendação:** aprovar os que tiveram "sim" em 2–5 e capturas sem defeito visual.
- **D5. Sincronia do fliperama (informativa; o dono pode reverter).** O spec descreve "repasse das entradas dos dois", e o plano usa servidor autoritativo (justificativa na Task 16).
  - **Recomendação:** manter o servidor autoritativo, pelo placar confiável, pela ausência de exigência de determinismo e pelo cliente fino.
  - Reverter para lockstep custaria a simulação em GDScript determinística e o replay na reconexão.
