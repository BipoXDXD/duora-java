# Etapa 11 — Piloto aberto: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** abrir o cadastro com capacidade que cresce aos poucos (limite de simultaneidade ajustável no painel), coletar feedback depois de cada encontro, medir o piloto com métricas internas (sem telemetria remota) num painel admin, provar tudo com uma rodada de encontros feita por agentes contra a homologação, e ter um critério de sucesso que decide entre ajustar, ampliar ou criar cenários.

**Arquitetura:**
- Pacote novo `piloto/` (proposto ao contrato): configuração do piloto, feedback, métricas diárias e o critério de sucesso. Camadas do contrato: raiz = API e casos de uso; `piloto/dominio/` Java puro (`CriterioDeSucesso`, `RecomendacaoDeCapacidade`, definições das métricas); `piloto/web/` e `piloto/infra/` adapters.
- **Configuração viva no banco** (`configuracao_piloto`), lida a cada uso, com o padrão vindo das propriedades. `ConfiguracaoDoPiloto` implementa `conta.PortaoDeCadastro` (Etapa 10) e a porta nova `sala.LimiteDeSimultaneidade`. Toda mudança passa pela `moderacao.Auditoria` (Etapa 8).
- **Métricas por portas dos módulos donos** (ver "Desenho"): cada módulo conta o que é dele; `piloto` só soma e guarda um retrato diário em `metrica_diaria`, recalculado todo dia para os últimos 15 dias (as métricas de retorno olham 14 dias para a frente). Contas de teste (`@teste.duora.invalid`, Etapa 10 P5) nunca entram.
- Nada sai do processo: sem analytics, sem pixel, sem serviço externo de métricas.

**Stack:** Java 25, Spring Boot 4.2.0, `JdbcClient`, PostgreSQL 18, Flyway, Testcontainers, React 19 + TypeScript + Tailwind 4 (rotas lazy do admin da Etapa 8), TanStack Query, Vitest, Playwright Java (perfil `piloto` contra uma URL), emulador Android (`scripts/android/`).

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 11; decisões "Ambição" e "Duração": até 10 encontros simultâneos; hipótese herdada; não-objetivo "telemetria remota"). **Contratos:** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Planos de que depende: Etapa 4 (`duora.encontros.maximo-simultaneos`, `Fila`, `CicloDePareamento`), Etapa 5 (encontro, decisão, `MotivoEncerramento`), Etapa 6 (conversa), Etapa 8 (`Auditoria`, `TipoDeAcao.CADASTRO_ALTERADO`/`CAPACIDADE_ALTERADA`, rotas `/admin`), Etapa 9 (`PoliticaDeRetencao`, documentos legais versionados), Etapa 10 (`PortaoDeCadastro`, contas de teste, `POST /api/teste/sessao`, smoke).

## Restrições globais (Global Constraints)
- Tudo do contrato vale aqui.
- **Flyway:** faixa proposta **V140–V159** (adição ao contrato). Usadas: V140 `configuracao_piloto`, V142 `metrica_diaria`. V141 fica livre: o `feedback_encontro` nasce na Etapa 5 como `V89` (emenda de 2026-11-22).
- **Dia das métricas:** dia civil em `America/Sao_Paulo` (00:00 a 00:00), convertido em intervalo de `Instant` meio-aberto `[início, fim)`.
- **Contas de teste fora de tudo:** toda porta de estatística recebe `Set<ContaId> excluir` (as contas de teste) e ignora encontros, decisões, conexões, denúncias e feedback em que alguma delas participe.
- **Capacidade:** inteiro de **1 a 50**; padrão `duora.encontros.maximo-simultaneos` (10, Etapa 4); valor inicial do piloto **3** (ver D2). Baixar a capacidade nunca encerra encontro em andamento: só impede novos até a ocupação cair.
- **Feedback:** opcional; uma resposta por pessoa por encontro; só quem participou de encontro iniciado; até **7 dias** depois do fim. Pergunta e respostas exatas: "Você gostaria de fazer algo assim de novo com alguém?" — "Sim", "Talvez", "Não". Campo livre opcional "Quer contar mais?" de até **500** caracteres. Comentário apagado em **90 dias** (a resposta fica, sem texto).
- **Textos (pt-BR, gênero neutro, exatos):** depois de enviar, "Recebemos sua resposta. Ela ajuda a melhorar o Duora."; botões "Enviar" e "Pular"; no painel, os nomes das métricas da tabela abaixo.
- **Métricas (nomes exatos em `metrica_diaria.nome`):**

  | Nome | Definição (dia D) | Dono |
  |---|---|---|
  | `encontros_publicos` | encontros com `iniciado_em` em D e origem `PUBLICO` | encontro |
  | `encontros_privados` | idem, origem `PRIVADO` | encontro |
  | `encontros_concluidos` | encerrados em D que chegaram à revelação ou terminaram por `TEMPO_ESGOTADO` ou `ACORDO` | encontro |
  | `estreantes` | contas cujo primeiro encontro iniciado foi em D | encontro |
  | `retornaram_14d` | estreantes de D com outro encontro iniciado em até 14 dias depois do primeiro | encontro |
  | `decisoes_completas` | encontros cuja segunda resposta de decisão foi registrada em D | conexão |
  | `sim_mutuo` | conexões criadas em D | conexão |
  | `conversas_iniciadas` | conexões criadas em D com ao menos uma mensagem de cada pessoa em até 7 dias | conexão |
  | `denuncias` e `denuncias_<motivo>` | denúncias criadas em D (total e por motivo) | moderação |
  | `banimentos` | linhas de `banimento` com `banida_em` em D | moderação |
  | `denuncias_resolvidas`, `denuncias_no_prazo` | resolvidas em D, e destas as resolvidas até o prazo do SLA | moderação |
  | `contas_novas` | cadastros concluídos em D | conta |
  | `feedback_sim`, `feedback_talvez`, `feedback_nao` | respostas de feedback criadas em D | piloto |

  Taxas exibidas (nunca guardadas): sim mútuo = `sim_mutuo / decisoes_completas`; conversa após conexão = `conversas_iniciadas / sim_mutuo`; retorno = `retornaram_14d / estreantes`; desejo = `feedback_sim / (sim + talvez + não)`; SLA = `denuncias_no_prazo / denuncias_resolvidas`. Denominador zero ⇒ "—".
- **Logs:** nunca comentário de feedback; métricas só aparecem no painel admin.

## Desenho das métricas (duas alternativas, `mattpocock-skills:codebase-design`)
- **A. Uma consulta central em `piloto/`** que faz JOIN em `encontro`, `decisao_encontro`, `conexao`, `mensagem_conexao`, `denuncia` e `conta`. Uma classe, rápida de escrever; mas lê tabelas de cinco donos (viola "cada módulo é dono das suas tabelas"), quebra em silêncio quando um dono muda o esquema, e a regra "concluído" (que depende da fase do jogo no `jsonb`) vaza para fora de `encontro`.
- **B. Uma porta por módulo dono**, cada uma com um método que devolve um record de contagens para um intervalo, e `piloto` só soma. A definição de "concluído" ou "conversa iniciada" fica junto dos dados; mudança de esquema quebra o teste do próprio dono.
- **Escolha: B.** São quatro portas pequenas (um método cada) e profundas (escondem SQL, `jsonb` e regras); `piloto` testa a soma com falsos, e cada dono testa a própria contagem com banco.

## Decisões de planejamento (o dono pode reverter)
- **P1. Feedback logo depois da decisão**, na mesma tela, opcional ("Pular" sempre visível). Quem saiu antes também recebe o convite. Ninguém mais vê a resposta.
- **P2. A rodada de agentes não cria denúncia na homologação:** a denúncia iria para a fila e para o e-mail do dono (D1 da Etapa 8). A jornada denúncia → banimento já é provada localmente pela Etapa 8; aqui os agentes cobrem sair, bloquear e o resto da jornada.
- **P3. Contas de teste nunca são admin:** `Administradores` recusa qualquer e-mail `@teste.duora.invalid`, mesmo na lista, para que o segredo da rota de teste não vire acesso ao painel.
- **P4. "Hoje" no painel é calculado na hora** pelas mesmas portas e não é gravado; os dias fechados vêm de `metrica_diaria`.

## Decisões do dono
| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| D1 | Critério de sucesso | (a) ao fim de **4 semanas** e com pelo menos **40 encontros concluídos**: **desejo ≥ 60%** "Sim" no feedback (mín. 30 respostas) **e retorno ≥ 30%** em 14 dias; guarda-corpos: denúncias confirmadas ≤ 5% dos encontros e SLA ≥ 90%; (b) mais exigente: 70% e 40%; (c) só o desejo, sem retorno | (a): mede a hipótese ("eu gostaria de fazer isso com alguém") e o comportamento ("voltam"), com amostra mínima para não decidir com 5 pessoas. Os limiares ficam em propriedades |
| D2 | Capacidade inicial e regra de crescimento | (a) começar com **3** encontros simultâneos; o painel recomenda subir para 5 e depois 10 quando, por **3 dias seguidos**, alguém esperou vaga por mais de **10 min**, o SLA está ≥ 90% e não há denúncia urgente vencida; (b) começar com 10 (o teto da spec) | (a): o dono modera sozinho; crescer devagar mantém a fila de denúncias sob controle. O painel só recomenda: quem sobe é o admin |
| D3 | Quando abrir e onde divulgar | (a) abrir depois de 7 dias seguidos de backup e monitor verdes e com a rodada de agentes aprovada, divulgando em círculos próximos primeiro; (b) abrir assim que a rodada passar | (a). "Abrir cadastro" é um clique do dono no painel (é a própria decisão); a divulgação é escolha dele |
| D4 | Retenção do comentário do feedback | (a) 90 dias; (b) 30 dias; (c) até o fim do piloto | (a): dá tempo de ler e agrupar depois do piloto sem guardar texto livre para sempre. Exige versão nova da política de privacidade (Task 2) |

Nenhuma bloqueia o código: limiares, capacidade e prazos vêm de propriedades com (a) como padrão.

## Foco de revisão (Review Focus)
1. **Admin baixa a capacidade com encontros em andamento:** ninguém é expulso; quem está na fila vê a posição recalculada; ninguém novo é pareado até a ocupação cair abaixo do novo limite. Coberto por `baixarCapacidadeNaoEncerraEImpedeNovos` (T1).
2. **Métrica recalculada duas vezes, ou job rodando enquanto o painel lê:** o valor de um dia fechado não dobra e não oscila. Coberto por `recalcularEIdempotente` (T4).
3. **Encontro que vira a meia-noite** (começa 23:50, termina 00:20): conta como iniciado num dia e concluído no outro, uma vez cada. Coberto por `encontroQueViraAMeiaNoiteContaUmaVezEmCadaMetrica` (T3).
4. **Contas de teste da rodada e do smoke diário:** nenhuma aparece em nenhuma métrica, nem no "hoje". Coberto por `contasDeTesteNaoEntramEmNenhumaMetrica` (T3) e `RodadaDeAgentesIT.metricasReaisNaoMudam` (T6).
5. **Feedback enviado duas vezes (duplo toque, rede lenta) ou depois de 7 dias:** uma resposta só; a segunda recebe 409; fora do prazo, 422 e a tela não oferece mais o formulário. Coberto por `segundoEnvioRecebe409` e `foraDoPrazoRecebe422` (T2).

---

### Task 1: Configuração do piloto (cadastro e capacidade)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V140__configuracao_piloto.sql`
- Criar: `piloto/{ConfiguracaoDoPiloto,EstadoDoPiloto}.java`, `piloto/infra/ConfiguracaoDoPilotoJdbc.java`, `piloto/web/PilotoAdminController.java`; `sala/LimiteDeSimultaneidade.java`
- Modificar: `sala/` (o `CicloDePareamento` e a entrada em encontro privado leem `LimiteDeSimultaneidade.maximo()` a cada uso, no lugar da propriedade), `conta/PortaoPorConfiguracao.java` (Etapa 10; deixa de ser bean quando `ConfiguracaoDoPiloto` existe), `admin/Administradores.java` (P3)
- Teste: `piloto/{ConfiguracaoDoPilotoTest,PilotoAdminApiTest}.java`, `sala/CapacidadeDinamicaTest.java`, `admin/AdministradoresTest.java` (caso novo)

**Esquema (V140):** `configuracao_piloto(id smallint primary key check (id = 1), cadastro_aberto boolean not null, maximo_simultaneos int not null check (maximo_simultaneos between 1 and 50), alterada_em timestamptz not null, alterada_por uuid references conta(id))`. Sem linha ⇒ valores das propriedades (`duora.cadastro.aberto`, `duora.encontros.maximo-simultaneos`).

**Interfaces:**
- Consome: `PortaoDeCadastro`, `ContasDeTesteDoAmbiente` (Etapa 10 T2); `Auditoria.registrar` + `TipoDeAcao.CADASTRO_ALTERADO|CAPACIDADE_ALTERADA` (Etapa 8 T2); `OcupacaoDeEncontros.ativos()` e `Fila` (Etapa 4).
- Produz:
  - `public interface LimiteDeSimultaneidade { int maximo(); }` em `sala/` (substitui a leitura direta da propriedade).
  - `ConfiguracaoDoPiloto implements PortaoDeCadastro, LimiteDeSimultaneidade`: `boolean aceitaNovaConta(Email)` (aberto, ou e-mail de teste); `int maximo()`; `EstadoDoPiloto estado()`; `@Transactional void abrirCadastro(ContaId admin, boolean aberto)` e `@Transactional void definirCapacidade(ContaId admin, int maximo)` (upsert da linha 1 + auditoria; fora de 1..50 ⇒ 422 `CAPACIDADE_INVALIDA`, `A capacidade vai de 1 a 50 encontros.`).
  - `record EstadoDoPiloto(boolean cadastroAberto, int maximoSimultaneos, int encontrosAtivos, int aguardandoVaga, Optional<Instant> esperaMaisLonga)`.
  - Rotas: `GET /api/admin/piloto` → `EstadoDoPiloto`; `PUT /api/admin/piloto/cadastro` `{ "aberto": bool }`; `PUT /api/admin/piloto/capacidade` `{ "maximo": int }` (corpo estrito; 204).
- [ ] **Passo 1:** testes.
  - `ConfiguracaoDoPilotoTest` (`BancoDeTeste`): `semLinhaUsaAsPropriedades`; `abrirGravaEAudita` (uma `acao_admin` `CADASTRO_ALTERADO` com `alvo_ref` `aberto`); `capacidadeForaDaFaixaRecebe422ENaoAudita` (0 e 51); `emailDeTesteSempreEntra` (cadastro fechado).
  - `CapacidadeDinamicaTest` (ciclo executado à mão, relógio fixo): `subirCapacidadeLiberaParNoProximoCiclo` (3 ativos, limite 3 ⇒ par espera; limite 4 ⇒ pareado no ciclo seguinte); `baixarCapacidadeNaoEncerraEImpedeNovos` (5 ativos, limite 3 ⇒ os 5 seguem; par novo espera até restarem 2).
  - `PilotoAdminApiTest`: conta comum ⇒ 403 nas três rotas; corpo com campo extra ⇒ 400; `GET` com chaves exatas do record.
  - `AdministradoresTest`: `emailDeTesteNuncaEAdmin`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify` verde (os testes de capacidade da Etapa 4 continuam verdes com o padrão 10). Commit `feat(piloto): cadastro e capacidade ajustáveis pelo admin com auditoria`.

### Task 2: Feedback depois do encontro (movida para a Etapa 5 Task 18)

> **Emenda 2026-11-22:** a spec pôs o feedback pós-encontro na Etapa 5. A Etapa 5 Task 18 implementa esta especificação com a migração `V89`, o pacote `encontro/` e o front em `frontend/src/encontro/`. Aqui ela fica como referência; nesta etapa nada é implementado, e a T4 consome `encontro.Feedbacks`. Onde abaixo se lê `piloto/` e `V141`, vale o que a Etapa 5 Task 18 diz.

**Arquivos:**
- Criar: `src/main/resources/db/migration/V141__feedback_encontro.sql`
- Criar: `piloto/{Feedbacks,RetencaoDeComentarios}.java`, `piloto/dominio/{RespostaDeFeedback,Comentario}.java`, `piloto/web/FeedbackController.java`; `encontro/ParticipacaoEmEncontros.java` (porta pública de leitura, se a Etapa 5 não tiver uma equivalente)
- Criar: `frontend/src/piloto/FeedbackTela.tsx`
- Modificar: `frontend/src/conexao/DecisaoTela.tsx` (depois de responder, mostra o `FeedbackTela`), `src/main/resources/legal/privacidade.md` (versão nova: feedback e prazo de 90 dias do comentário; tabela de prazos da Etapa 9 ganha a linha `feedback-comentarios`)
- Teste: `piloto/{FeedbackApiTest,RetencaoDeComentariosTest}.java`, `piloto/dominio/ComentarioTest.java`, `frontend/src/piloto/FeedbackTela.test.tsx`; o `privacidadePrometeOsPrazosDoCodigo` da Etapa 9 passa a cobrir a linha nova

**Esquema (V141):** `feedback_encontro(encontro_id uuid not null references encontro(id) on delete cascade, conta_id uuid not null references conta(id), resposta text not null check (resposta in ('SIM','TALVEZ','NAO')), comentario text check (char_length(comentario) <= 500), criado_em timestamptz not null, primary key (encontro_id, conta_id))`; índice em `criado_em`.

**Interfaces:**
- Consome: `ParticipacaoEmEncontros` (abaixo); `PoliticaDeRetencao` (com `tamanho()`); `Idempotencia` (Etapa 4) opcional pelo cabeçalho `Idempotency-Key`.
- Produz:
  - `encontro.ParticipacaoEmEncontros`: `Optional<Instant> encerradoEmSeParticipou(String encontroId, ContaId conta)` (vazio se não participou, não começou ou ainda está aberto).
  - `enum RespostaDeFeedback { SIM, TALVEZ, NAO }`; `record Comentario(String valor)` (1–500 code points, sem controle exceto `\n`; `static Optional<Comentario> de(@Nullable String)`).
  - `Feedbacks.registrar(String encontroId, ContaId conta, RespostaDeFeedback r, Optional<Comentario> c)`: insert; PK duplicada ⇒ 409 `FEEDBACK_JA_REGISTRADO`; sem participação ⇒ 404 `ENCONTRO_INEXISTENTE`; `encerradoEm + 7 dias` < agora ⇒ 422 `FEEDBACK_FORA_DO_PRAZO`.
  - Rotas: `POST /api/encontros/{id}/feedback` `{ "resposta", "comentario"? }` ⇒ 201 `{ "registrado": true }`; `GET /api/encontros/{id}/feedback` ⇒ `{ "pode": bool }` (falso se já respondeu, fora do prazo ou sem participação).
  - `RetencaoDeComentarios implements PoliticaDeRetencao`: `feedback-comentarios`, `P90D`, referência `criado_em`; zera `comentario` em lotes de 1000; `tamanho()` = linhas com comentário.
  - `FeedbackTela`: grupo de rádio com a pergunta e as três respostas (obrigatório para "Enviar"), `textarea` "Quer contar mais?" com `maxLength` 500 e contador, "Enviar" e "Pular"; depois de enviar, o texto de agradecimento.
- [ ] **Passo 1:** testes.
  - `ComentarioTest`: 500 passa, 501 falha, `\u0000` falha, só espaços ⇒ vazio.
  - `FeedbackApiTest`: `registraUmaVez`; `segundoEnvioRecebe409`; `foraDoPrazoRecebe422` (7 dias ok; 7 dias + 1 s ⇒ 422); `quemNaoParticipouRecebe404`; `encontroAbertoRecebe404`; `quemSaiuAntesPodeResponder`; `corpoComCampoExtraRecebe400`; `outraPessoaNuncaVeAResposta` (não há rota que devolva a resposta alheia: `GET` só traz `pode`); `comentarioNaoApareceNoLog` (canário).
  - `RetencaoDeComentariosTest`: 89 dias fica; 90 dias + 1 s zera o comentário e mantém a resposta.
  - Vitest: `enviarExigeResposta`; `pularNaoEnvia`; `contadorAte500`; `agradeceDepoisDeEnviar`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar; publicar a versão nova da política (front matter com a data do dia; a Etapa 9 recalcula o resumo).
- [ ] **Passo 4:** verde. Commit `feat(piloto): feedback opcional depois do encontro com comentário que expira em 90 dias`.

### Task 3: Portas de estatística dos módulos donos

**Arquivos:**
- Criar: `nucleo/IntervaloDeDia.java`; `encontro/{EstatisticasDeEncontros,ContagemDeEncontros}.java`; `conexao/{EstatisticasDeConexoes,ContagemDeConexoes}.java`; `moderacao/{EstatisticasDeModeracao,ContagemDeModeracao}.java`; `conta/{EstatisticasDeContas}.java` (+ `Set<ContaId> contasDeTeste()` em `ContasDeTesteDoAmbiente`); `sala/EstatisticasDaFila.java`
- Teste: `nucleo/IntervaloDeDiaTest.java`, `encontro/EstatisticasDeEncontrosTest.java`, `conexao/EstatisticasDeConexoesTest.java`, `moderacao/EstatisticasDeModeracaoTest.java`, `conta/EstatisticasDeContasTest.java`

**Interfaces:**
- Produz:
  - `record IntervaloDeDia(LocalDate dia, Instant inicio, Instant fim)` com `static IntervaloDeDia de(LocalDate dia)` (fuso `America/Sao_Paulo`, fim exclusivo; dia de 23 h ou 25 h se houver horário de verão).
  - `EstatisticasDeEncontros.contar(IntervaloDeDia dia, Set<ContaId> excluir) -> ContagemDeEncontros(int publicos, int privados, int concluidos, int estreantes, int retornaram14d)`. "Chegou à revelação" é decidido dentro de `encontro/` (fase do `Retrato` no `jsonb` ou coluna, à escolha do dono do módulo).
  - `EstatisticasDeConexoes.contar(IntervaloDeDia, Set<ContaId>) -> ContagemDeConexoes(int decisoesCompletas, int simMutuo, int conversasIniciadas)`.
  - `EstatisticasDeModeracao.contar(IntervaloDeDia, Set<ContaId>) -> ContagemDeModeracao(Map<MotivoDenuncia,Integer> denunciasPorMotivo, int banimentos, int resolvidas, int noPrazo)` (exclui denúncias em que denunciante **ou** denunciado é de teste).
  - `EstatisticasDeContas.contasNovas(IntervaloDeDia, Set<ContaId>) -> int`.
  - `EstatisticasDaFila.agora() -> (int aguardandoVaga, Optional<Instant> esperaMaisLongaDesde)` (usado por `EstadoDoPiloto`, T1).
  - Todas: uma consulta agregada por chamada, com `excluir` como `uuid[]` (`<> all(?)`), sem carregar linhas.
- [ ] **Passo 1:** testes (`BancoDeTeste`, dados montados pelas fixtures de cada etapa).
  - `IntervaloDeDiaTest`: 2026-11-22 ⇒ `2026-11-22T03:00Z` a `2026-11-23T03:00Z`.
  - `EstatisticasDeEncontrosTest`: `publicosEPrivadosPorInicio`; `concluidoPorRevelacaoTempoOuAcordo` (saída antes da revelação não conta; saída depois da revelação conta); `encontroQueViraAMeiaNoiteContaUmaVezEmCadaMetrica`; `estreanteERetornoEm14Dias` (segundo encontro no 14º dia conta; no 15º não); `contasDeTesteNaoEntramEmNenhumaMetrica` (encontro com uma conta de teste não conta em nada).
  - `EstatisticasDeConexoesTest`: `decisaoCompletaNoDiaDaSegundaResposta`; `conversaIniciadaPrecisaDosDois` (só uma pessoa escreveu ⇒ não conta; os dois em até 7 dias ⇒ conta; segunda pessoa no 8º dia ⇒ não conta).
  - `EstatisticasDeModeracaoTest`: por motivo; `noPrazoUsaOSla` (resolvida no prazo exato conta; 1 s depois não); exclusão por denunciante ou denunciado de teste.
  - `EstatisticasDeContasTest`: só cadastros concluídos; contas de teste fora.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. A ArchUnit confere que `piloto` só usa a raiz pública desses módulos.
- [ ] **Passo 4:** verde. Commit `feat(piloto): contagens diárias pelos módulos donos dos dados`.

### Task 4: Métricas diárias, critério de sucesso e recomendação de capacidade

**Arquivos:**
- Criar: `src/main/resources/db/migration/V142__metrica_diaria.sql`
- Criar: `piloto/{MetricasDoPiloto,CalculoDiarioDeMetricas,TotaisDoPiloto}.java`, `piloto/dominio/{CriterioDeSucesso,Avaliacao,LimiaresDoCriterio,RecomendacaoDeCapacidade,DiaDeOperacao}.java`
- Modificar: `application.properties` (`duora.piloto.metricas.cron=0 15 4 * * *`, `duora.piloto.criterio.*`, `duora.piloto.capacidade.*`)
- Teste: `piloto/dominio/{CriterioDeSucessoTest,RecomendacaoDeCapacidadeTest}.java`, `piloto/{MetricasDoPilotoTest,CalculoDiarioDeMetricasTest}.java`

**Esquema (V142):** `metrica_diaria(dia date not null, nome text not null check (nome ~ '^[a-z][a-z0-9_]*$'), valor bigint not null check (valor >= 0), calculada_em timestamptz not null, primary key (dia, nome))`.

**Interfaces:**
- Consome: as portas da T3; `Feedbacks` (contagem por dia, T2); `ContasDeTesteDoAmbiente.contasDeTeste()`.
- Produz:
  - `MetricasDoPiloto`: `Map<String, Long> calcular(IntervaloDeDia dia)` (soma das portas, nomes exatos da tabela global); `void gravar(LocalDate dia)` (`insert ... on conflict (dia, nome) do update set valor = excluded.valor, calculada_em = excluded.calculada_em`, numa transação); `List<LinhaDeMetricas> ler(LocalDate de, LocalDate ate)` (máximo 90 dias, senão 422 `INTERVALO_INVALIDO`) com `record LinhaDeMetricas(LocalDate dia, Map<String, Long> valores)`.
  - `CalculoDiarioDeMetricas` (`@Scheduled(cron, zone = "America/Sao_Paulo")`): grava D-1 a D-15; trava com `pg_try_advisory_lock` para não rodar em dobro; métrica `duora.piloto.metricas.ultima.execucao` (gauge com o instante).
  - `TotaisDoPiloto.desde(LocalDate inicio) -> Totais` (somas do período, para o critério).
  - `CriterioDeSucesso.avaliar(Totais t, LimiaresDoCriterio l, LocalDate inicio, LocalDate hoje) -> Avaliacao`: `AMOSTRA_INSUFICIENTE` (menos de `l.semanas` semanas, de `l.encontrosMinimos` concluídos ou de `l.respostasMinimas` respostas); senão `VALIDADO` (desejo ≥ `l.desejo` **e** retorno ≥ `l.retorno`), `PARCIAL` (só um), `NAO_VALIDADO` (nenhum); mais `List<String> alertas` para guarda-corpo violado (`"Denúncias confirmadas acima de 5% dos encontros"`, `"SLA abaixo de 90%"`). Limiares padrão de D1(a): 4 semanas, 40 encontros, 30 respostas, desejo 0,60, retorno 0,30, denúncias 0,05, SLA 0,90. Sugestão exibida: `VALIDADO` ⇒ "Ampliar o piloto"; `PARCIAL` ⇒ "Ajustar o que ficou abaixo e medir de novo"; `NAO_VALIDADO` ⇒ "Ajustar a experiência ou testar novos cenários".
  - `RecomendacaoDeCapacidade.recomendar(int atual, List<DiaDeOperacao> ultimos3) -> OptionalInt`: degraus `3 → 5 → 10`; recomenda o próximo só se, nos 3 dias, `esperaMaximaPorVaga > 10 min`, SLA ≥ 0,90 e nenhuma urgente vencida; no teto (10), vazio. `record DiaDeOperacao(LocalDate dia, Duration esperaMaximaPorVaga, double sla, boolean urgenteVencida)` (a espera máxima por vaga vem de um registro diário que `EstatisticasDaFila` passa a gravar como métrica `espera_vaga_max_segundos`).
- [ ] **Passo 1:** testes.
  - `CriterioDeSucessoTest` (`@ParameterizedTest` com valores literais): 39 concluídos ⇒ `AMOSTRA_INSUFICIENTE`; desejo 0,60 e retorno 0,30 exatos ⇒ `VALIDADO` (on point); 0,59 ⇒ `PARCIAL`; os dois abaixo ⇒ `NAO_VALIDADO`; denúncias 6% ⇒ alerta mesmo com `VALIDADO`; denominador zero não lança.
  - `RecomendacaoDeCapacidadeTest`: três dias com espera de 11 min ⇒ 5; um dia com 9 min ⇒ vazio; SLA 0,89 num dia ⇒ vazio; atual 10 ⇒ vazio; atual 5 ⇒ 10.
  - `MetricasDoPilotoTest` (portas falsas): `somaAsPortasComOsNomesExatos` (conjunto de chaves igual ao da tabela global); `intervaloDe91DiasRecebe422`.
  - `CalculoDiarioDeMetricasTest` (`BancoDeTeste`): `recalcularEIdempotente` (duas execuções ⇒ mesmos valores, nenhuma linha a mais); `decisaoAtrasadaAtualizaODiaAnterior` (a segunda resposta chega no dia seguinte ⇒ o recálculo corrige `decisoes_completas` de D); `duasInstanciasSimultaneasUmaSoRoda`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(piloto): métricas diárias, critério de sucesso e recomendação de capacidade`.

### Task 5: Painel do piloto e de métricas no admin

**Arquivos:**
- Criar: `piloto/web/MetricasAdminController.java`; `frontend/src/admin/{MetricasTela,PilotoTela,TabelaDeMetricas,BarrasPorDia,CartaoDoCriterio}.tsx`
- Modificar: `frontend/src/admin/rotas.tsx` (Etapa 8: `/admin/metricas` e `/admin/piloto`, lazy), `frontend/src/admin/LayoutAdmin.tsx` (links)
- Teste: `piloto/MetricasAdminApiTest.java`, `frontend/src/admin/{MetricasTela,PilotoTela,CartaoDoCriterio}.test.tsx`

**Interfaces:**
- Consome: T1 (`/api/admin/piloto`), T4.
- Produz:
  - `GET /api/admin/metricas?de=AAAA-MM-DD&ate=AAAA-MM-DD` → `{ "dias": [LinhaDeMetricas...], "hoje": LinhaDeMetricas, "totais": {...}, "avaliacao": { "resultado", "sugestao", "alertas" }, "recomendacaoDeCapacidade": int | null }` (`hoje` calculado na hora, P4).
  - `PilotoTela`: estado do cadastro com o botão "Abrir cadastro" / "Fechar cadastro" (confirmação: "Abrir o cadastro para qualquer pessoa?"), capacidade atual com campo numérico 1–50 e "Salvar", encontros ativos, pessoas esperando vaga e há quanto tempo; com recomendação: "Recomendação: subir para {n} encontros simultâneos." (o admin decide).
  - `MetricasTela`: seletor de período (7, 14, 30 dias), `CartaoDoCriterio` (resultado, sugestão, alertas, quanto falta de amostra), `TabelaDeMetricas` (uma linha por dia, colunas com os nomes da tabela global, taxas com "—" para zero) e `BarrasPorDia` em CSS puro (sem biblioteca de gráfico; carregar a skill `dataviz` antes de desenhar). Sem nenhuma chamada para fora da origem.
- [ ] **Passo 1:** testes.
  - `MetricasAdminApiTest`: conta comum ⇒ 403; período de 91 dias ⇒ 422; `hojeNaoEGravado` (após o `GET`, nada novo em `metrica_diaria`); chaves exatas.
  - Vitest: `taxaComDenominadorZeroMostraTraco`; `cartaoMostraAmostraFaltante` ("Faltam 12 encontros concluídos para avaliar."); `recomendacaoAparecesoComValor`; `abrirCadastroPedeConfirmacao`; `capacidadeForaDaFaixaNaoEnvia`.
- [ ] **Passo 2:** RED (Java e Vitest).
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde; `AxeIT` ganha `painelDeMetricasSemViolacoes` e `painelDoPilotoSemViolacoes` (chromium, firefox, webkit). Commit `feat(admin): painel do piloto e métricas internas`.

### Task 6: Rodada de encontros por agentes contra a homologação

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/piloto/{RodadaDeAgentesIT,PerfisDaRodada,RoteiroDePlaytest,RelatorioDaRodada}.java`
- Modificar: `pom.xml` (perfil `piloto`: failsafe só com `**/piloto/*IT.java`, mesma configuração de URL do perfil `smoke` da Etapa 10)

**Interfaces:**
- Consome: `AmbienteDeSmoke`, `ContaDeSmoke` (Etapa 10 T9); `EncontrarPagina`, `EncontroPagina`, `ConexoesPagina`, `VozPagina` (Etapas 4–7); `FeedbackTela` (T2); `scripts/android/`.
- Produz:
  - `PerfisDaRodada`: 24 contas de teste com perfis variados (amizade; namoro com pares de gêneros compatíveis e incompatíveis; faixas etárias que se cruzam e que não se cruzam; raio obrigatório e preferência com células de São Paulo e Campinas), definidos por `PUT /api/perfil*` pelas rotas reais.
  - `RoteiroDePlaytest`: as perguntas do playtest da 1c (primeira ação clara? contribuição do outro visível? negociação? final muda? vontade de repetir?) respondidas por cada agente com evidência (captura e trecho do estado), gravadas por `RelatorioDaRodada` em `target/piloto/rodada-<data>/relatorio.json` + capturas.
- [ ] **Passo 1:** escrever `RodadaDeAgentesIT` (cada teste limpa as contas que criou com `POST /api/conta/exclusao`):
  - `paresCompativeisSaoPareadosEIncompativeisNao` (as 24 entram na fila; em até 60 s, os pares esperados estão em encontro e os incompatíveis seguem esperando);
  - `excedenteEsperaComPosicao` (com a capacidade da homologação em C, `C + 1` pares entram: o último vê "Todos os encontros estão ocupados..." com posição e estimativa; os 9+ pares de fundo são dirigidos por `APIRequestContext`, só 2 pares usam navegador);
  - `jornadaCompletaComFeedback` (2 pares pelo navegador: cenário até a revelação, chat com link e aviso de saída, voz por relay, decisão "Sim" mútua num par e "Agora não" no outro, feedback "Sim" com comentário; o par recusado não vê diferença de tela);
  - `sairEBloquearValemNaHora` (sem denúncia, P2);
  - `conversaDepoisDaConexao` (o par conectado troca mensagens e uma ligação de voz pela lista de conexões);
  - `metricasReaisNaoMudam` (só no ambiente local, `@EnabledIf` URL `localhost`: o `hoje` de `GET /api/admin/metricas`, lido por uma conta admin de desenvolvimento antes e depois da rodada, é idêntico; contra a URL real o agente não é admin, P3, e a exclusão fica provada por este teste local mais o `contasDeTesteNaoEntramEmNenhumaMetrica` da T3);
  - um participante no **emulador Android** (`scripts/android/emulador.sh subir`): instala o PWA da URL, faz um encontro inteiro com um parceiro no Chromium e responde o feedback; capturas em `target/android/piloto-*.png`.
- [ ] **Passo 2:** primeiro **local**: compose da Etapa 10 (`infra/compose.prod.yaml` + `compose.local.yaml`) com capacidade 3; `./mvnw -B -Ppiloto verify -Dduora.url=https://localhost:8443 -Dduora.smoke.inseguro=true -Dduora.teste.segredo=<de infra/local.env>`; aqui o `metricasReaisNaoMudam` roda completo com uma conta admin real de desenvolvimento (e-mail fora do domínio de teste, na lista local).
- [ ] **Passo 3:** contra a homologação (`-Dduora.url=https://<domínio>`, segredo do Keychain), num horário fora de pico, sem tocar na capacidade (o agente não é admin; P3). O `metricasReaisNaoMudam` fica desligado aqui, e o relatório registra que a prova dele é a local.
- [ ] **Passo 4:** resumir no handoff da sessão: pares e tempos de pareamento, falhas, respostas do roteiro (sim/não com evidência), capturas mais relevantes; ao dono vão só escolhas que a rodada levantar. Commit `test(piloto): rodada de encontros por agentes com relatório e prova no Android`.

### Task 7: Abertura do piloto

Executada por agentes; ao dono chega só o clique "Abrir cadastro" (D3), que é a própria decisão.

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/piloto/ProntoParaAbrirIT.java`
- Modificar: `.claude/handoff/_mapa.md` (seção "Piloto": como ler as métricas, critério, regra de capacidade)

- [ ] **Passo 1:** `ProntoParaAbrirIT` (perfil `piloto`, contra a URL), cada item é um teste e falha com a razão:
  - `smokeVerde` (reusa o `SmokeIT`);
  - `cadastroAindaFechado` (e-mail real novo ⇒ resposta padrão; o `GET /api/legal/versoes` traz a versão nova da privacidade da T2);
  - `moderacaoConfigurada` (o e-mail de alerta de teste da Etapa 8 chegou ao admin: verificado pelo último envio registrado na outbox, sem ler a caixa do dono);
  - `backupsEMonitorVerdes` (pela API do monitor escolhido na Etapa 10 D4: heartbeats de backup e host verdes nos últimos 7 dias, drill da semana verde, HTTP sem queda nos últimos 7 dias);
  - `capacidadeInicial`: o `application-prod.properties` define `duora.encontros.maximo-simultaneos=3` (D2) e, pela VPS, `docker compose exec -T postgres psql -U duora -Atc "select count(*) from configuracao_piloto"` devolve `0` (nenhum admin mudou ainda) — o agente lê pelo SSH, sem ser admin.
- [ ] **Passo 2:** com tudo verde, avisar o dono numa linha: "Pronto para abrir: smoke, rodada, backups e monitor verdes há 7 dias. Abrir o cadastro em /admin/piloto?" — e esperar. Depois do clique, conferir a auditoria `CADASTRO_ALTERADO` e um cadastro real de ponta a ponta (o primeiro participante).
- [ ] **Passo 3:** agendar a avaliação: quando o `CartaoDoCriterio` sair de `AMOSTRA_INSUFICIENTE`, levar ao dono o resultado, a sugestão e as escolhas (ajustar, ampliar ou novos cenários). Atualizar o `_mapa.md`. Commit `docs(piloto): abertura verificada e leitura das métricas`.

## Ordem e paralelismo
| Leva | Tasks (worktrees separados) | Depende de | Flyway |
|---|---|---|---|
| A | T1 ∥ T2 ∥ T3 | Etapas 8 (T2 da 8, `Auditoria`) e 10 (T2 da 10, portão e contas de teste) | T1: V140; T2: V141 |
| B | T4 | T2, T3 | V142 |
| C | T5 | T1, T4 | — |
| D | T6 | T5 implantada (Etapa 10 T10 concluída) | — |
| E | T7 | T6, Etapa 9, decisões D1–D3 | — |

- T1 mexe em `sala/` (ciclo e convite) e T3 cria arquivos novos em `encontro/`, `conexao/`, `moderacao/`, `conta/`, `sala/`: sem arquivos em comum, mas não rodar junto de task de outra etapa que edite o `CicloDePareamento`.
- A T6 roda uma vez local e uma vez contra a URL; comandos pesados (compose, Playwright com muitos contextos, emulador) um por vez.
- No máximo 4 agentes ao mesmo tempo.

## Adições ao contrato (propostas, não aplicadas)
- Pacote `piloto/` (configuração do piloto, feedback, métricas) e Flyway **V140–V159** para a Etapa 11.
- `sala.LimiteDeSimultaneidade` substitui a leitura direta de `duora.encontros.maximo-simultaneos`.
- Portas de estatística por módulo (`Estatisticas*`, um método, `Set<ContaId> excluir`) como o único jeito de outro módulo contar dados que não são dele.
- `nucleo.IntervaloDeDia` (dia civil de São Paulo) para tudo que agrupa por dia.
- Contas `@teste.duora.invalid` nunca são admin.
- Retenção (Etapa 9): `feedback-comentarios`, 90 dias.
