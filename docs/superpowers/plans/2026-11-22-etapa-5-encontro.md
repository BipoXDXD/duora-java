# Etapa 5 — Encontro no cenário: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` (ou `mattpocock-skills:tdd`) e fecham cada task com `superpowers:verification-before-completion`. Antes da Task 1, `mattpocock-skills:domain-modeling` para o Encontro; antes da Task 8, para a Conexão.

**Objetivo:** o encontro vira um lugar completo: nasce do pareamento ou do convite com o assento ligado à conta, dura 30 min, tem chat com links, imagens com consentimento, voz, sair, denunciar e bloquear sempre à mão, sobrevive a reinício e termina numa decisão privada em que só o sim mútuo aparece.

> **Emenda 2026-11-22 (paridade com o mercado, spec "Etapa 5"):** Tasks 11–18 novas: ações rápidas dentro da cena; "Tem certeza?" e "Isso te incomodou?"; alertas de troca de contato, link e golpe via PIX; triagem automática das imagens (Falconsai via ONNX Runtime Java); fluxo de abuso infantil com remoção, preservação e reporte; clipe de voz de 60 s só no aparelho; quebra-gelos nos objetos da cena; cartão-postal; feedback pós-encontro (trazido da Etapa 11). As Tasks 5, 6 e 7 ganham ajustes pequenos marcados como "(paridade)".

**Arquitetura:**
- `room/` vira `encontro/`. O **domínio puro** fica em `encontro/dominio/` (sem Spring, JDBC nem Jackson): `Retrato`, `Participante`, a fase do encontro como **State** (`sealed interface FaseDoEncontro` com `switch` exaustivo) e `RegrasDoEncontro`. A aplicação (`Encontro` com o lock, `Encontros`, controllers) e a persistência (`encontro/persistencia/`) ficam em volta.
- O `Retrato` continua imutável e trocado sob o lock do encontro, agora com **write-through**: cada mudança aceita grava `encontro.estado` (`jsonb`) com `UPDATE ... SET versao = versao + 1 ... WHERE id = ? AND versao = ?` antes de ser publicada; 0 linhas ⇒ 409 `VERSAO_OBSOLETA` e releitura. No boot, os encontros abertos voltam do banco.
- Um encontro ativo por conta é **índice único parcial** em `participante_encontro`. `Encontros` implementa as portas da Etapa 4 (`AberturaDeEncontro`, `OcupacaoDeEncontros`) gravando na mesma transação do ciclo, e a porta `AutorizacaoDeVoz` da Etapa 7.
- **Observer** no fim do encontro: `Encontro` publica `EncontroEncerrado`; ouvintes independentes, na transação (veto de saída, abertura da decisão) e depois do commit (encerrar a voz, métrica).
- Chat, imagens e decisões em tabelas próprias; a ordem do chat é a sequência do banco. Eventos `encontro.*` e `chat.*` saem pelo `CanalDeEventos` na transação do efeito; polling com ETag fica como plano B.
- Denúncia e bloqueio **básicos** em `moderacao/` (a Etapa 8, em paralelo, acrescenta fila de admin, banimento e auditoria sobre estas tabelas). Decisão e conexão em `conexao/`, com as regras puras em `conexao/dominio/`.
- ADR `<próximo número livre>-encontro-em-jsonb-com-write-through.md`.

**Stack:** Java 25, Spring Boot 4.2.0, Spring Data JDBC/`JdbcClient`, PostgreSQL 18 (`jsonb`), Flyway, Testcontainers, Bucket4j, ArchUnit, libvips (via `ProcessadorDeImagens` da Etapa 3), React 19 + TypeScript + Tailwind, Vitest, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (decisões "Jornada", "Duração", "Comunicação no encontro", "Reencontro"; Escolhas "Sair é sempre unilateral", "Links", "Imagens", "Sem imagens sensíveis", "Sair, denunciar e bloquear", "Foto pendente não bloqueia"; Arquitetura; Etapa 5). Contratos e princípios: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Planos: Etapas 2, 3, 4, 7 e 9 (`docs/superpowers/plans/2026-11-22-etapa-*.md`).

## Restrições globais
- Valem os contratos entre etapas (inclusive "Princípios de engenharia") e as restrições globais da Etapa 4.
- Migrações: **V80–V99**. Usadas: V80 `encontro` + `participante_encontro`, V81 `mensagem_encontro`, V82 `imagem_encontro` + `imagem_oculta`, V83 `decisao_encontro`, V84 `conexao`, V85 `bloqueio`, V86 `denuncia`, V87 `caso_abuso_infantil` (Task 14), V88 `clipe_de_voz` (Task 15), V89 `feedback_encontro` (Task 18). V90–V99 reservadas.
- **Contas novas (paridade):** mensagem e imagem do encontro consomem `LimitesDeContaNova` (Etapa 2 Task 15) na transação do envio.
- **Triagem de imagem (paridade):** toda imagem do encontro passa pelo classificador antes de ser entregue; pontuação NSFW ≥ `duora.moderacao.nsfw.bloqueio` (0,90) → recusada e explicada; de `duora.moderacao.nsfw.fila` (0,50) até o bloqueio → entregue borrada com aviso reforçado e denúncia automática P1; classificador indisponível → 503, nunca entrega sem triagem.
- **Clipe de voz (spec, "Prova de denúncia de voz"):** só no aparelho, só o áudio **recebido**, só os últimos 60 s, em memória; sai só com denúncia **e** confirmação explícita; aviso no início de todo encontro; descartado ao fim.
- `encontro.dominio` e `conexao.dominio` sem Spring, JDBC nem Jackson (regra na suíte ArchUnit).
- Erros por `ErroDeNegocio` com `CodigoEncontro`, `CodigoModeracao` e `CodigoConexao` (cada um `implements CodigoDeErro`); limites por `LimiteDeTaxa` com `duora.limites.<regra>.capacidade|periodo`.
- Duração: **30 min** contados de quando as duas pessoas entram; aviso aos **5 min finais**; ao fim, os dois vão à decisão privada.
- **Encerrar antes exige os dois** (pedido + aceite; o pedido vence em 60 s). **Sair é sempre unilateral**, a qualquer momento, e leva à decisão privada.
- Convite privado sem convidado vence em **30 min**.
- `Idempotency-Key` aceito em `POST /api/encontro/mensagens`, `POST /api/encontro/encerrar` e `POST /api/encontros/{id}/decisao`.
- Chat: texto de **1 a 500** code points, sem caracteres de controle exceto `\n` e sem controles bidi; regra `chat-encontro` (5 por `PT10S`, chave conta).
- Links: só `http`/`https`, sem userinfo, até 2048 caracteres; **domínio em destaque** (IDN em punycode); `rel="noopener noreferrer nofollow ugc"` e `target="_blank"`; aviso "Você vai sair do Duora" antes de abrir; **sem prévia** (o servidor nunca busca URL de usuário).
- Imagens: só com o **aceite dos dois** depois do aviso explícito, desligáveis por qualquer um; até **5 MB**; reprocessadas pelo `ProcessadorDeImagens` (WebP q88, sem metadados, maior lado 1600 px, mais a borrada); chegam **borradas, com toque para ver**; denunciáveis; regra `imagem-encontro` (5 por `PT10M`); retenção **P30D** pela Etapa 9.
- Aviso de imagens (texto fixo): "Imagens com nudez, sexo, violência explícita ou conteúdo chocante são proibidas e levam ao banimento. Cada imagem chega borrada, e a outra pessoa escolhe se quer ver. Qualquer um de vocês pode desligar as imagens a qualquer momento."
- Decisão: `SIM`, `NAO` e `OUTRO_ENCONTRO` (ver D1); prazo de **24 h**; sem resposta conta como `NAO`. O sim mútuo cria a conexão; **o não nunca é revelado**: resposta da API e tela idênticas para todo desfecho que não seja conexão.
- Quem é deixado (saída, denúncia, bloqueio, suspensão) vê só "O encontro terminou.", nunca o motivo. Só "o tempo acabou" e "vocês encerraram juntos" aparecem como tais.
- Sair, Denunciar e Bloquear visíveis em todas as telas do encontro e da decisão.
- Ouvintes de `EncontroEncerrado`: os que gravam no banco rodam **na** transação; os de memória (voz, métrica) rodam **depois** do commit; nada sai do processo sem outbox.
- Textos em pt-BR, voz ativa, gênero neutro.

## Dependências assumidas
- **Etapa 4:** `CanalDeEventos.publicar(List<Envio>)`/`publicarEfemero`, `Evento`, `Envio`, `EstadoInicial`, `useEvento`, `aoReconectar`; `Idempotencia.executar(...)`; `sala.{AberturaDeEncontro,OcupacaoDeEncontros,EncontroAberto,ConvitePrivado,CodigoSala}`; `pareamento.dominio.{ParDeContas,ParPareado}`; `pareamento.{VetosDePar,MotivoVeto}`; `sala.HistoricoDeCenarios`; `room/PonteDeEncontros` e `room/AssentoController` (substituídos aqui); `Visao.encontroId`; fixture `sala/EncontrosDeTeste.parear(ContaDeTeste, ContaDeTeste)`; `EncontrarPagina`.
- **Etapa 7:** `voz.AutorizacaoDeVoz`, `voz.dominio.{ContextoDeVoz,TipoDeContexto}`, `Chamadas.encerrarDoContexto(ContextoDeVoz)`; `room/VozNaSala` (substituída aqui); `PainelDeVoz`, `VozPagina`.
- **Etapa 2:** `ContaId`, `@ContaAtual`, `ConsultaDeContas`, `Sessoes.revogarTodas`, `AoExcluirConta`, `ErroDeNegocio`/`CodigoDeErro`, `LimiteDeTaxa`, `GeradorDeIds`, `BancoDeTeste`, `RelogioDeTeste`, `ContasDeTeste`, `Participantes.novoComConta`; `LimitesDeContaNova.consumir(ContaId, TipoDeLimiteNovo)` e `RegistroDeAcessos` (Task 15 da Etapa 2, lido pelo pacote de reporte da Task 14).
- **Etapa 7 (paridade):** `useChamadaDeVoz` expõe `fluxoRemoto: MediaStream | null` e `silenciarOutro(boolean)`; se não expuser, a Task 15 acrescenta os dois ao hook num commit de refatoração. **Suspensão:** a Etapa 2 não publica um jeito de suspender. Esta etapa usa `public interface SuspensaoDeContas { void suspender(ContaId conta, String motivo); }` em `conta/`; se não existir na `main`, a Task 7 cria a interface e a implementação mínima em `conta/` (`UPDATE conta SET estado = 'SUSPENSA' WHERE id = ? AND estado = 'ATIVA'` + `Sessoes.revogarTodas`), avisando o orquestrador (a Etapa 8 a reaproveita no banimento).
- **Etapa 3:** `Perfis.publico(ContaId)`; `web.ProcessadorDeImagens.processar(InputStream) -> ImagemProcessada` (já recusa formato com 415, tamanho com 413 e arquivo inválido com 422); `web.ArmazemDeImagens.guardar(String categoria, UUID id, ImagemProcessada)`, `caminho(categoria, id, Variante)`, `apagar(categoria, id)` com a categoria `encontro`; `web.Variante { NITIDA, BORRADA }`; `perfil.AcessoAFotoPadrao` (esta etapa edita: encontro → borrada; bloqueio → nenhum).
- **Etapa 9:** `retencao.PoliticaDeRetencao { String nome(); Duration prazo(); int expirar(Instant limite); }`. O `ExpiracaoDoEncontroIT` da Etapa 9 usa as tabelas `encontro`, `mensagem_encontro` e `imagem_encontro` desta etapa.
- **Etapa 1c:** "Sair" e "Ajustar com aceite" já existem no motor; esta etapa os mantém.

## Decisões de planejamento (o dono pode reverter)
- **P1. O chat do encontro só é visível durante o encontro.** Depois, mensagens e imagens ficam 30 dias só como contexto de denúncia e então expiram. A Etapa 8 copia o que precisar guardar além disso.
- **P2. Quem denuncia ou bloqueia não passa pela decisão:** a resposta dele é `NAO` automática.
- **P3. Foto borrada só para quem divide encontro** (ativo, ou encerrado há até 24 h, para a decisão); bloqueio em qualquer sentido ⇒ nenhuma. Ajusta o `AcessoAFotoPadrao` da Etapa 3, que hoje dá borrada a qualquer conta autenticada.
- **P4. Em encontro privado ainda sem convidado**, só o criador está em `participante_encontro`; o convidado entra na mesma transação do aceite.

## Foco de revisão
1. **O outro descobre a recusa pelo tempo ou pelo corpo da resposta:** `POST /decisao` devolve o mesmo corpo e status para `SIM`, `NAO` e `OUTRO_ENCONTRO`; quem disse sim e não virou conexão não recebe evento nenhum. Teste na Task 8 e no `RecusaInvisivelIT`.
2. **Reinício no meio do encontro:** depois de reiniciar, o encontro volta com peças, versão, tempo restante e chat; o que venceu durante a parada vai à decisão como "tempo acabou". Teste na Task 3.
3. **Duas abas agindo ao mesmo tempo:** a gravação com versão antiga recebe 409 `VERSAO_OBSOLETA` e nada se perde. Teste na Task 3.
4. **Texto com `javascript:`, `data:`, `https://banco.com@golpe.example`, domínio em cirílico ou URL de 3000 caracteres:** só URL `http(s)` válida vira link; o domínio mostrado é o host real. Teste na Task 5.
5. **Mensagem reenviada pelo cliente depois de timeout** (mesma `Idempotency-Key`): uma mensagem só para os dois. Teste na Task 5.
6. **Clipe de voz saindo do aparelho sem a pessoa marcar a opção** (ou depois do fim do encontro): nenhum `POST` de clipe sem a caixa marcada, e o buffer zerado ao encerrar. Teste na Task 15.
7. **Modelo de triagem ausente, lento ou corrompido no servidor:** a imagem não é entregue sem triagem (503) e o resto do encontro segue. Teste na Task 13.

---

### Task 1: `room/` vira `encontro/` com assento ligado à conta

**Arquivos:**
- Mover (`git mv`), num commit só de refatoração: `src/main/java/bipotheking/net/duora/room/` → `.../encontro/`; `Sala` → `Encontro`, `Salas` → `Encontros`, `SalaController` → `EncontroController`; `Retrato`, `Participante`, `Encerramento`, `MotivoEncerramento` → `encontro/dominio/`; `CenariosConfig` vai junto para `encontro/`, mas o `CatalogoDeCenarios` fica em `game/` (ADR 0003: evita o ciclo `sala ↔ encontro`)
- Remover (commit de comportamento): `CookieAssento`, `Convite`, `room/PonteDeEncontros`, `room/AssentoController`, `room/VozNaSala`, rotas `/api/salas*` e `/api/sala*`
- Criar: `encontro/{VozNoEncontro,CodigoEncontro}.java`, `docs/adr/<próximo>-encontro-em-jsonb-com-write-through.md` (rascunho; completado na Task 3)
- Modificar: `encontro/{Visao,LimitesSala→LimitesDoEncontro,Varredor}.java`, `perfil/AcessoAFotoPadrao.java` (P3), a suíte ArchUnit
- Teste: `room/{SalaApiTest,ConcorrenciaTest,SalasTest,CenarioApiTest}` → `encontro/{EncontroApiTest,ConcorrenciaTest,EncontrosTest,CenarioApiTest}`; `sala/*Test` passam a usar a implementação real; testes de foto do `perfil/` (ajuste de P3)

**Interfaces:**
- Consome: portas das Etapas 4 e 7.
- Produz:
  - Passo 0 (antes de codar): `mattpocock-skills:domain-modeling` para o Encontro (fases, invariantes, eventos, linguagem); o resultado vira a seção "Modelo" do ADR.
  - `encontro.dominio.Participante(Assento assento, ContaId conta, Apelido apelido, Optional<String> fotoBorradaUrl)`.
  - `Encontros implements AberturaDeEncontro, OcupacaoDeEncontros`, mais `Optional<Encontro> ativoDe(ContaId)` e `Optional<Encontro> porId(String)`.
  - Rotas (`@ContaAtual`): `GET /api/encontro` (com ETag), `POST /api/encontro/pecas`, `/sinal`, `/pronto`, `/ajustar`, `/ajustar/recusar`, `/abertura` (as duas últimas vêm da 1c). Sem encontro ativo, o `GET /api/encontro` responde 204 sem corpo, como o `GET /api/sala` sem cookie desde a 1c, porque o `PrimeiraCargaIT` não aceita resposta ≥ 400 na primeira carga. Já as mutações respondem 404 `FORA_DE_ENCONTRO`. `POST /api/encontros/privados` e `/entrar` continuam, sem `Set-Cookie`.
  - `Visao` ganha `encontroId` e `parceiro.fotoBorradaUrl` (nula ⇒ silhueta).
  - `VozNoEncontro implements AutorizacaoDeVoz` (contexto `ENCONTRO`, id = `encontroId`, as duas contas no encontro em andamento).
  - `LimitesDoEncontro(Duration duracao, Duration aviso, Duration conviteSemConvidado, Duration pedidoDeEncerrar, Duration presenca, Duration lapide)` com `PADRAO = (30 min, 5 min, 30 min, 60 s, 15 s, 5 min)`; sai a expiração por inatividade.
  - `AcessoAFotoPadrao` (P3): titular ⇒ `NITIDA`; bloqueio em qualquer sentido ⇒ `NENHUM` (a partir da Task 7); encontro em comum ativo ou encerrado há até 24 h ⇒ `BORRADA`; resto ⇒ `NENHUM`.

- [ ] **Passo 1:** commit de refatoração: `git mv` e renomeações, sem mudar comportamento; suíte verde. Commit `refactor(encontro): room vira encontro com domínio em encontro/dominio`.
- [ ] **Passo 2:** migrar os testes de API: criar o par com `EncontrosDeTeste.parear(ana, bia)` ou o convite privado, trocar o cookie de assento pelo de sessão e `/api/sala` por `/api/encontro`. As asserções de peças, versões, fases, sinais e resultado **não mudam**. Testes que só existiam para o fluxo anônimo são apagados (lista na mensagem de commit). Acrescentar: `semSessaoRecebe401`; `contaSemEncontroRecebe204NoGet`; `mutacaoSemEncontroRecebe404`; `terceiraContaNaoVeOEncontro` (204 no GET, 404 nas mutações, nenhuma resposta com apelidos); `fotoPendenteViraSilhueta`; e, em `perfil/`, `estranhoNaoVeFotoBorrada` e `parDoEncontroVeABorrada`.
- [ ] **Passo 3:** RED; ligar à conta, remover a ponte e o `VozNaSala`, registrar `VozNoEncontro`, ajustar o `AcessoAFotoPadrao`; verde (um Docker por vez). Commit `feat(encontro): assento ligado à conta e foto borrada só para o par`.

### Task 2: Front e e2e no encontro com conta

**Arquivos:**
- Modificar: `frontend/src/` (cliente do cenário: `/api/sala*` → `/api/encontro*`; sem criação anônima; `#convite=` leva à `EntrarPorConviteTela`)
- Modificar: `src/test/java/bipotheking/net/duora/e2e/{TerracoPagina,JornadaDoisNavegadoresIT,PollingIT,RevelacaoIT,AcessibilidadeLayoutIT,AcessibilidadeFocoIT,AxeIT,LayoutCelularIT,ImagensIT,CenarioFrontIT,PrimeiraCargaIT,AberturaIT}.java`

**Interfaces:**
- Produz: `TerracoPagina.entrarEmParComContas(ContaDeTeste ana, ContaDeTeste bia)` no lugar de `criarTerraco` + `entrarNoTerraco` (convite privado, cenário `terraco`).
- Mantém: a suíte e2e herda `duora.cenarios.abertura.maximo=PT0S` do perfil `e2e` do `pom.xml` (a abertura conclui apagada já na entrada), e só o `AberturaIT` liga a abertura (`duora.cenarios.abertura.maximo=PT90S`). O `PrimeiraCargaIT` continua sem erro no console e sem resposta ≥ 400.

- [ ] **Passo 1:** trocar os passos de criação no page object; as asserções dos ITs não mudam.
- [ ] **Passo 2:** ajustar o front até a suíte e2e passar em chromium, firefox e webkit, sem skip. Commit `refactor(web): cenário só com conta e convite privado`.

### Task 3: Persistência do encontro em `jsonb` com versão

**Arquivos:**
- Criar: `src/main/resources/db/migration/V80__encontro.sql`
- Criar: `encontro/{RepositorioDeEncontros,ConflitoDePersistencia,CarregadorDeEncontros}.java`, `encontro/persistencia/{RetratoJson,RepositorioDeEncontrosJdbc}.java`
- Modificar: `encontro/{Encontro,Encontros}.java`, o ADR do encontro
- Teste: `encontro/persistencia/{RetratoJsonTest,RepositorioDeEncontrosJdbcTest}.java`, `encontro/ReinicioDoEncontroTest.java`

**Interfaces:**
- Produz:
  - `V80`:
    - `encontro(id uuid primary key, origem text not null check (origem in ('PUBLICO','PRIVADO')), cenario_id text not null, convite_resumo text unique, versao bigint not null, estado jsonb not null, aberto_em timestamptz not null, termina_em timestamptz, encerrado_em timestamptz, motivo text)`; índice parcial `on encontro (termina_em) where encerrado_em is null`.
    - `participante_encontro(encontro_id uuid not null references encontro(id), conta_id uuid not null references conta(id), assento char(1) not null check (assento in ('A','B')), ativo boolean not null, primary key (encontro_id, conta_id), unique (encontro_id, assento))`; `create unique index um_encontro_ativo_por_conta on participante_encontro (conta_id) where ativo`.
  - Porta `RepositorioDeEncontros` (adapter `RepositorioDeEncontrosJdbc`): `void inserir(String id, Origem origem, String cenarioId, Optional<String> conviteResumo, RetratoGravado retrato)`; `void salvar(String id, long versaoLida, RetratoGravado novo)` (UPDATE condicional; 0 linhas ⇒ `ConflitoDePersistencia`); `void entrar(String id, ContaId conta, Assento assento)` (violação do índice parcial ⇒ `ErroDeNegocio` 409 `JA_EM_ENCONTRO`); `void sairTodos(String id)` (`ativo = false`); `List<EncontroGravado> abertos()`.
  - `RetratoJson` (só na persistência): DTO explícito com `int formato` (= 1); fase do encontro, estado do jogo (fase, versão, composição por ids, prontos, sinal), participantes (assento, conta, apelido), pedido de encerrar e consentimento de imagens. `ultimoContato` **não** entra. Leitor tolerante a campo desconhecido.
  - `Encontro`: toda mudança aceita (jogada, entrada, início, pedido, consentimento, encerramento) grava antes de publicar o `Retrato`; consulta e presença não gravam. Falha de banco ⇒ 503 `INDISPONIVEL` e retrato intacto. `ConflitoDePersistencia` ⇒ relê e responde 409 `VERSAO_OBSOLETA` com a `Visao` relida.
  - `abrirPareado`/`entrarPrivado` gravam o encontro e as participações na transação de quem chama (o ciclo da Etapa 4), dispensando a compensação em memória.
  - `CarregadorDeEncontros` (`ApplicationRunner`): recarrega os abertos; os que passaram de `termina_em` são encerrados com `TEMPO_ESGOTADO`.

- [ ] **Passo 1:** testes.
  - `RetratoJsonTest`: `idaEVoltaPreservaOEstado` (3 peças, sinal ativo, um pronto); `campoDesconhecidoEIgnorado`; `formatoDiferenteDe1Falha`.
  - `RepositorioDeEncontrosJdbcTest`: `salvarComVersaoCertaIncrementa`; `salvarComVersaoAntigaLancaConflito` (Foco 3); `segundoEncontroAtivoDaMesmaContaFalha`; `sairTodosLiberaParaOutroEncontro`.
  - `ReinicioDoEncontroTest` (fecha o contexto e sobe outro sobre o mesmo contêiner) (Foco 2): `encontroVoltaComPecasVersaoETempo`; `encontroVencidoDuranteAParadaVaiADecisao` (410 com `TEMPO_ESGOTADO`); `consultaNaoGrava` (dez `GET` não mudam `versao`).
  - `EncontroApiTest.falhaDoBancoNaoMudaOEstado` (503 e `Visao` seguinte igual à anterior).
  - `CicloDePareamentoTest.falhaAoAbrirRevertETudo` (da Etapa 4) continua verde, agora sem compensação.
- [ ] **Passo 2:** RED; implementar; verde. Completar o ADR. Commit `feat(encontro): estado em jsonb com UPDATE condicional e um encontro ativo por conta`.

### Task 4: Fases, duração, aviso final, encerrar por acordo e sair

**Arquivos:**
- Criar: `encontro/dominio/{FaseDoEncontro,PedidoDeEncerrar,RegrasDoEncontro,DecisaoDoEncontro,EncontroEncerrado}.java`, `encontro/{RelogioDosEncontros,EstadoInicialDoEncontro,MetricasDoEncontro}.java`
- Modificar: `encontro/dominio/{Retrato,MotivoEncerramento}.java`, `encontro/persistencia/RetratoJson.java`, `encontro/{Encontro,Visao,EncontroController}.java`
- Criar: `frontend/src/encontro/{Cronometro,PedidoDeEncerrar,SairDoEncontro}.tsx`
- Teste: `encontro/dominio/RegrasDoEncontroTest.java`, `encontro/EncerramentoApiTest.java`, `frontend/src/encontro/{Cronometro,PedidoDeEncerrar}.test.tsx`

**Interfaces:**
- Produz:
  - State: `public sealed interface FaseDoEncontro permits AguardandoConvidado, AguardandoPresenca, EmAndamento, Encerrado` com `record AguardandoConvidado(Instant abertoEm)`, `record AguardandoPresenca(Instant pareadoEm, Set<Assento> presentes)`, `record EmAndamento(Instant iniciadoEm, Optional<PedidoDeEncerrar> pedido)`, `record Encerrado(MotivoEncerramento motivo, Instant em, Optional<Assento> quem)`. Toda decisão sobre a fase é `switch` exaustivo, sem `default`. Substitui `Optional<Encerramento>` no `Retrato`.
  - `enum MotivoEncerramento { TEMPO_ESGOTADO, ACORDO, SAIU, DENUNCIA, BLOQUEIO, SUSPENSAO, CONVITE_EXPIROU, CANCELADO }` com `String paraQuemFicou()`: `TEMPO_ESGOTADO` e `ACORDO` como são; `SAIU`, `DENUNCIA`, `BLOQUEIO`, `SUSPENSAO` ⇒ `TERMINOU`.
  - `record PedidoDeEncerrar(Assento de, Instant em)`.
  - `RegrasDoEncontro` (puras; devolvem `DecisaoDoEncontro`, selada como `game/Decisao`: `Aceita(Retrato)` ou `Recusada(RecusaDoEncontro)`): `Optional<Instant> terminaEm(FaseDoEncontro, LimitesDoEncontro)`, `boolean emAvisoFinal(FaseDoEncontro, Instant, LimitesDoEncontro)`, `pedirEncerramento(Retrato, Assento, Instant)`, `responderPedido(Retrato, Assento, boolean aceito, Instant)`, `sair(Retrato, Assento, Instant)`, `vencer(Retrato, Instant, LimitesDoEncontro)`, `registrarPresenca(Retrato, Assento, Instant)` (os dois presentes ⇒ `EmAndamento`).
  - `public record EncontroEncerrado(String encontroId, ParDeContas par, MotivoEncerramento motivo, Optional<ContaId> quem, Instant em)`, publicado pelo `ApplicationEventPublisher` dentro da transação do encerramento. Ouvintes: `pareamento` veta o par com `SAIDA` quando o motivo é `SAIU` (na transação); `voz` chama `Chamadas.encerrarDoContexto` (depois do commit); `MetricasDoEncontro` incrementa `duora.encontros.encerrados{motivo}` (depois do commit). A Task 8 acrescenta o ouvinte da decisão.
  - `Encontro.encerrarPor(ContaId quem, MotivoEncerramento motivo)` (usado pela moderação e pela saída).
  - Rotas: `POST /api/encontro/encerrar` (pedido, com `Idempotency-Key`), `POST /api/encontro/encerrar/resposta` `{ "aceito": bool }`, `POST /api/encontro/sair` (204, idempotente). Ação fora da fase ⇒ 409 `ACAO_DE_ENCONTRO_INVALIDA`.
  - `Visao.encontro`: `{ "id", "terminaEm", "avisoFinal", "pedidoDeEncerrar": { "deVoce": bool } | null }`.
  - Eventos (duráveis): `encontro.estado` (`Visao` de cada um) a cada mudança gravada; `encontro.aviso` `{ "restanteSegundos": 300 }` uma vez; `encontro.encerrado` `{ "encontroId", "motivo": "TEMPO_ESGOTADO"|"ACORDO"|"TERMINOU", "decisao": bool }` (`decisao` falso para quem denunciou ou bloqueou, P2).
  - `RelogioDosEncontros` (`@Scheduled` a cada 5 s, com o `Clock`): aviso final, vencimento, pedido vencido (some), convite sem convidado vencido (`CONVITE_EXPIROU`), pareado sem presença depois de 60 s (`CANCELADO`, sem decisão).
  - Front: `Cronometro` mostra "Faltam {m} min" só nos 5 min finais (`role="timer"`) e anuncia uma vez "Faltam 5 minutos." (`role="status"`); `PedidoDeEncerrar` mostra a quem recebeu "{apelido} quer encerrar o encontro agora." com "Encerrar" e "Continuar"; `SairDoEncontro` confirma no painel: "Sair agora? Você vai direto para a sua decisão.".

- [ ] **Passo 1:** testes.
  - `RegrasDoEncontroTest`: `doisPresentesIniciam`; `terminaEm30MinDepoisDoInicio`; `semInicioNaoHaTermino`; `avisoFinalAos25Min` (24:59 não, 25:00 sim); `pedidoEAceiteEncerramPorAcordo`; `pedidoRecusadoSome`; `quemPediuNaoAceitaOProprio`; `pedidoVence60s`; `sairEncerraComOQuem`; `vencerAos30Min`; `encerradoNaoAceitaNada` (cada ação ⇒ recusa); `paraQuemFicouEscondeSaidaDenunciaBloqueioESuspensao`.
  - `EncerramentoApiTest`: `sairAvisaOOutroComTerminou`; `sairVetaOParNaMesmaTransacao`; `sairDuasVezesE204`; `acordoLevaOsDoisADecisao`; `tempoEsgotadoLevaOsDoisADecisao`; `avisoChegaUmaVez`; `fimEncerraAChamadaDeVoz`; `conviteSemConvidadoVenceEm30Min`; `pedidoRepetidoComAMesmaChaveNaoDuplica`; `metricaContaPorMotivo`.
  - Vitest: `cronometroSoNosUltimos5Min`; `pedidoRecebidoMostraOsDoisBotoes`; `sairPedeConfirmacao`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): fases seladas, 30 minutos, encerrar por acordo e sair sozinho`.

### Task 5: Chat do encontro com links seguros

**Arquivos:**
- Criar: `src/main/resources/db/migration/V81__mensagem_encontro.sql`
- Criar: `src/main/java/bipotheking/net/duora/web/texto/{Links,Segmento,TextoDeUsuario}.java`, `encontro/{ChatDoEncontro,MensagemVisao,SegmentoVisao,ChatDoEncontroController,RetencaoDoChatDoEncontro}.java`
- Criar: `frontend/src/encontro/{ChatDoEncontro,MensagemComLinks,AvisoDeSaida}.tsx`
- Teste: `web/texto/{LinksTest,TextoDeUsuarioTest}.java`, `encontro/{ChatDoEncontroApiTest,RetencaoDoChatDoEncontroTest}.java`, `frontend/src/encontro/{MensagemComLinks,AvisoDeSaida,ChatDoEncontro}.test.tsx`

**Interfaces:**
- Produz:
  - `V81`: `mensagem_encontro(id uuid primary key, seq bigint generated always as identity unique, encontro_id uuid not null references encontro(id) on delete cascade, autor uuid not null references conta(id), tipo text not null check (tipo in ('TEXTO','IMAGEM')), texto text check (char_length(texto) between 1 and 500), imagem_id uuid, sinalizada boolean not null default false, criada_em timestamptz not null, check ((tipo = 'TEXTO') = (texto is not null)), check ((tipo = 'IMAGEM') = (imagem_id is not null)))`; índice `(encontro_id, seq)`. A FK de `imagem_id` entra na V82. `sinalizada` é usada pela Task 12.
  - (paridade) O envio consome `LimitesDeContaNova.consumir(autor, MENSAGEM)` na transação da mensagem.
  - `TextoDeUsuario.validar(String bruto, int maximo) -> String`: `strip()`, 1..máximo code points, recusa `U+0000`–`U+001F` (exceto `\n`), `U+007F`–`U+009F`, `U+202A`–`U+202E`, `U+2066`–`U+2069` ⇒ 422 `TEXTO_INVALIDO`.
  - `Links.segmentar(String texto) -> List<Segmento>`; `sealed interface Segmento` com `record Texto(String texto)` e `record Link(String url, String dominio)`. Candidatos `(?i)https?://\S+` sem `.,;:!?` final e sem `)` final desbalanceado; vira `Link` só se `URI` válida, esquema `http`/`https`, host presente, sem userinfo, até 2048 caracteres e `IDN.toASCII` do host sem erro (`dominio` em minúsculas). O resto é `Texto`.
  - `record MensagemVisao(UUID id, long seq, boolean minha, String tipo, List<SegmentoVisao> segmentos, @Nullable ImagemVisao imagem, Instant criadaEm)`; `record SegmentoVisao(String tipo, String texto, @Nullable String url, @Nullable String dominio)`.
  - Rotas: `POST /api/encontro/mensagens` `{ "texto" }` com `Idempotency-Key` ⇒ 201; `GET /api/encontro/mensagens?depois=<seq>` (até 100, crescente). Fora de `EmAndamento` ⇒ 409 `ENCONTRO_SEM_CHAT`.
  - Evento durável `chat.encontro.mensagem` (`MensagemVisao` com `minha` de cada destinatário), gravado na transação da mensagem.
  - `RetencaoDoChatDoEncontro implements PoliticaDeRetencao`: `chat-do-encontro`, `P30D`, referência `encontro.encerrado_em`; apaga em lotes de até 1000; encontro aberto nunca expira.
  - Front: `MensagemComLinks` mostra cada link como botão com o `dominio` em `<strong>`; o clique abre `AvisoDeSaida` (`role="dialog"`, `aria-modal`, "Você vai sair do Duora", "Este link leva para {dominio}.", `<a href target="_blank" rel="noopener noreferrer nofollow ugc">Abrir {dominio}</a>` e "Voltar"). Campo com `maxLength` 500 e contador; cada envio gera uma `Idempotency-Key` (`crypto.randomUUID()`) reaproveitada nas novas tentativas do mesmo envio; em `aoReconectar`, busca `?depois=<maior seq>` e deduplica por `id`.

- [ ] **Passo 1:** testes.
  - `LinksTest` (Foco 4, valores literais): `"veja https://exemplo.com/a."` ⇒ texto, link (`exemplo.com`), `"."`; `"HTTP://EXEMPLO.COM"` ⇒ `dominio` `exemplo.com`; `"javascript:alert(1)"`, `"data:text/html,<b>"`, `"www.exemplo.com"`, `"ftp://x.com"` ⇒ só texto; `"https://banco.com@golpe.example/"` ⇒ só texto; `"https://пример.рф/a"` ⇒ `dominio` `xn--e1afmkfd.xn--p1ai`; URL de 3000 caracteres ⇒ texto; `"(https://exemplo.com)"` ⇒ `"("`, link, `")"`; dois links.
  - `TextoDeUsuarioTest`: vazio e só espaços ⇒ 422; 500 passa, 501 não; emoji conta 1; `\u0000` e `‮` ⇒ 422; `\n` passa.
  - `ChatDoEncontroApiTest`: `mensagemChegaAoOutroPeloSse`; `minhaSoParaQuemEnviou`; `terceiroRecebe404`; `antesDoInicioRecebe409`; `depoisDoFimRecebe409` (P1); `sextaEm10sRecebe429`; `depoisPaginaPeloSeq`; `mesmaChaveUmaMensagemSo` (Foco 5: duas chamadas com a mesma chave ⇒ uma linha e um evento por destinatário); `corpoComCampoExtraRecebe400`; `chavesDoJsonExatas` (sem `autor` nem `ContaId`); `contaNovaNaDuzentesimaPrimeiraRecebe429` (paridade).
  - `RetencaoDoChatDoEncontroTest`: 30 dias + 1 s sai; 29 dias e 23 h fica; encontro aberto fica; 2500 mensagens saem inteiras.
  - Vitest: `linkMostraDominioEmDestaque`; `cliqueAbreAvisoENaoNavega`; `linkDoAvisoTemRelETarget`; `textoComHtmlApareceComoTexto`; `reconexaoDeduplicaPorId`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): chat com texto e links seguros`.

### Task 6: Imagens com consentimento dos dois

**Arquivos:**
- Criar: `src/main/resources/db/migration/V82__imagem_encontro.sql`
- Criar: `encontro/{ImagensDoEncontro,ImagensDoEncontroController,ImagemVisao,RetencaoDasImagensDoEncontro}.java`
- Modificar: `encontro/dominio/Retrato.java`, `encontro/persistencia/RetratoJson.java`, `encontro/{Encontro,Visao,MensagemVisao}.java`
- Criar: `frontend/src/encontro/{ImagensDoEncontro,AvisoDeImagens,ImagemBorrada}.tsx`
- Teste: `encontro/{ImagensDoEncontroApiTest,RetencaoDasImagensDoEncontroTest}.java`, `frontend/src/encontro/{AvisoDeImagens,ImagemBorrada}.test.tsx`; fixtures em `src/test/resources/imagens/`

**Interfaces:**
- Produz:
  - `V82`: `imagem_encontro(id uuid primary key, encontro_id uuid not null references encontro(id) on delete cascade, autor uuid not null references conta(id), largura int not null, altura int not null, criada_em timestamptz not null)`; `imagem_oculta(imagem_id uuid not null references imagem_encontro(id) on delete cascade, conta_id uuid not null references conta(id), primary key (imagem_id, conta_id))`; FK `mensagem_encontro.imagem_id → imagem_encontro(id) on delete cascade`.
  - `Retrato` ganha `Map<Assento, Boolean> imagensAceitas`; `Visao.imagens` = `{ "voce", "parceiro", "habilitadas" }`.
  - Rotas: `POST /api/encontro/imagens/consentimento` `{ "aceito": bool }` ⇒ 200 `Visao`; `POST /api/encontro/imagens` multipart `imagem` ⇒ 201 `MensagemVisao` tipo `IMAGEM`; `GET /api/encontro/imagens/{id}/{variante}` com `borrada|nitida` (`image/webp`, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`).
  - Envio: exige `habilitadas` (senão 409 `IMAGENS_DESLIGADAS`), consome `imagem-encontro`, chama `ProcessadorDeImagens.processar` (os 413/415/422 vêm dele), grava a linha e a mensagem numa transação e chama `ArmazemDeImagens.guardar("encontro", id, imagem)` antes do commit (falha no commit ⇒ `apagar` no `afterCompletion`). (paridade) Consome `LimitesDeContaNova.consumir(autor, IMAGEM)` na mesma transação; a triagem da Task 13 entra entre o processamento e a gravação.
  - Leitura: só participante, só em `EmAndamento` (P1), nunca imagem oculta para quem pede ⇒ senão 404 `IMAGEM_INEXISTENTE`.
  - `record ImagemVisao(UUID id, String urlBorrada, String urlNitida, int largura, int altura)`.
  - `RetencaoDasImagensDoEncontro implements PoliticaDeRetencao`: `imagens-do-encontro`, `P30D`, referência `encontro.encerrado_em`; apaga as linhas em lotes de até 1000 e os arquivos com `ArmazemDeImagens.apagar("encontro", id)` depois do commit de cada lote.
  - Front: "Permitir imagens" abre `AvisoDeImagens` (texto fixo, caixa "Entendi as regras", "Permitir"); "Desligar imagens" sempre visível quando ligadas; `ImagemBorrada` com "Ver imagem" (troca para a nítida) e "Denunciar imagem".

- [ ] **Passo 1:** testes.
  - `ImagensDoEncontroApiTest`: `semOsDoisAceitaremRecebe409`; `osDoisAceitamEAImagemChegaComoMensagem`; `qualquerUmDesligaNaHora`; `jpegComGpsSaiSemMetadados` (o arquivo gravado não contém `Exif` nem `GPS`); `htmlComoPngRecebe415ENadaNoDisco`; `cincoMbMaisUmByteRecebe413`; `sextaImagemEm10MinRecebe429`; `terceiroRecebe404`; `depoisDoFimRecebe404`; `cabecalhosDaImagem`; `falhaNoCommitApagaOsArquivos`; `contaNovaNaDecimaPrimeiraImagemDoDiaRecebe429` (paridade).
  - `RetencaoDasImagensDoEncontroTest`: 30 dias + 1 s ⇒ linhas e arquivos somem; 29 dias e 23 h ficam; encontro aberto fica.
  - Vitest: `permitirExigeMarcarEntendi`; `imagemChegaBorradaEToqueMostraNitida`; `desligarSempreVisivelComImagensLigadas`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): imagens com consentimento dos dois, borradas, com retenção de 30 dias`.

### Task 7: Denúncia e bloqueio básicos

**Arquivos:**
- Criar: `src/main/resources/db/migration/V85__bloqueio.sql`, `V86__denuncia.sql`
- Criar: `src/main/java/bipotheking/net/duora/moderacao/{Bloqueios,Denuncias,NovaDenuncia,MotivoDenuncia,ContextoDenuncia,TipoDeContextoDenuncia,ResolvedorDeContexto,ContextoResolvido,BloqueioRegistrado,DenunciaRegistrada,ModeracaoController,CodigoModeracao}.java`, `conexao/RegistroDeDecisaoAutomatica.java`, `encontro/{EfeitosDaModeracaoNoEncontro,ContextosDeDenunciaDoEncontro}.java`; `conta/SuspensaoDeContas.java` só se não existir (ver Dependências)
- Criar: `frontend/src/moderacao/{AcoesDeSeguranca,DenunciarDialogo,BloquearDialogo}.tsx`
- Teste: `moderacao/{DenunciaApiTest,BloqueioApiTest}.java`, `frontend/src/moderacao/AcoesDeSeguranca.test.tsx`

**Interfaces:**
- Produz:
  - `V85`: `bloqueio(quem uuid not null references conta(id), alvo uuid not null references conta(id), criado_em timestamptz not null, primary key (quem, alvo), check (quem <> alvo))`; índice em `alvo`.
  - `V86`: `denuncia(id uuid primary key, origem text not null default 'PESSOA' check (origem in ('PESSOA','TRIAGEM')), denunciante uuid references conta(id), denunciado uuid not null references conta(id), motivo text not null check (motivo in ('PARECE_MENOR','IMAGEM_SENSIVEL','ASSEDIO','SPAM_OU_GOLPE','OUTRO')), detalhe text check (char_length(detalhe) <= 500), contexto_tipo text not null check (contexto_tipo in ('ENCONTRO','MENSAGEM_ENCONTRO','IMAGEM_ENCONTRO','CONEXAO','MENSAGEM_CONEXAO')), contexto_ref text not null, situacao text not null default 'PENDENTE' check (situacao in ('PENDENTE','CONFIRMADA','DESCARTADA')), criada_em timestamptz not null, check ((origem = 'PESSOA') = (denunciante is not null)), unique (denunciante, contexto_tipo, contexto_ref))`; índices em `denunciado` e `situacao`; índice único parcial `(contexto_tipo, contexto_ref) where origem = 'TRIAGEM'`. `origem = 'TRIAGEM'` é a denúncia automática da Task 13 (sem denunciante).
  - `Bloqueios`: `void bloquear(ContaId quem, ContaId alvo)` (idempotente); `boolean bloqueado(ContaId a, ContaId b)` (qualquer sentido).
  - `Denuncias.registrar(NovaDenuncia) -> UUID` (idempotente pela `unique`).
  - `record NovaDenuncia(ContaId denunciante, ContaId denunciado, MotivoDenuncia motivo, Optional<String> detalhe, ContextoDenuncia contexto)`; `record ContextoDenuncia(TipoDeContextoDenuncia tipo, String referencia)`.
  - `public interface ResolvedorDeContexto { TipoDeContextoDenuncia tipo(); Optional<ContextoResolvido> resolver(ContaId quem, String referencia); }` com `record ContextoResolvido(ContaId outro, Optional<String> encontroId)`: o denunciado sai **do servidor**, e quem denuncia precisa participar (senão 404 `CONTEXTO_INEXISTENTE`). Esta task registra `ENCONTRO`, `MENSAGEM_ENCONTRO` (autor ≠ quem denuncia) e `IMAGEM_ENCONTRO`; a Etapa 6 registra `CONEXAO` e `MENSAGEM_CONEXAO`.
  - `public interface RegistroDeDecisaoAutomatica { void registrarAutomatica(String encontroId, ContaId quem); }` em `conexao/`, implementada pela Task 8 (até lá, falso nos testes).
  - Rotas: `POST /api/denuncias` `{ "motivo", "detalhe"?, "contexto": { "tipo", "id" } }` ⇒ 201 `{ "registrada": true, "id": "<uuid>" }` (o `id` só serve para o clipe da Task 15); `POST /api/bloqueios` `{ "contexto": { "tipo": "ENCONTRO"|"CONEXAO", "id" } }` ⇒ 204. Regra `denuncia`: 10 por `PT1H`.
  - Na mesma transação: grava; `VetosDePar.vetar(par, DENUNCIA|BLOQUEIO)`; `registrarAutomatica` de quem agiu, se houver encontro (P2); `imagem_oculta` para quem denunciou imagem; `SuspensaoDeContas.suspender(denunciado, "parece menor de idade")` para `PARECE_MENOR`; publica `BloqueioRegistrado(ContaId quem, ContaId alvo)` ou `DenunciaRegistrada(UUID id, ContaId denunciante, ContaId denunciado, MotivoDenuncia motivo, ContextoDenuncia contexto)`.
  - `EfeitosDaModeracaoNoEncontro` (ouvinte na transação): encerra o encontro ativo do par com `DENUNCIA` ou `BLOQUEIO`, e o do suspenso com `SUSPENSAO` (mais a saída da fila).
  - `AcessoAFotoPadrao`: bloqueio ⇒ `NENHUM` (P3).
  - Front: `AcoesDeSeguranca` com "Sair", "Denunciar" e "Bloquear" sempre visíveis no topo do encontro e da decisão. Motivos: "Parece menor de idade", "Imagem sensível", "Assédio ou ofensa", "Spam ou golpe", "Outro". Bloquear confirma com "Bloquear {apelido}? Vocês não vão se encontrar de novo."

- [ ] **Passo 1:** testes.
  - `DenunciaApiTest`: `denunciarEncerraParaQuemDenunciou` (quem denunciou recebe `decisao` falso; o outro recebe `TERMINOU`, idêntico ao de uma saída); `denunciarVetaOPar`; `denunciadoVemDoServidor` (corpo com `"denunciado"` ⇒ 400 e nada gravado); `contextoDeOutroParRecebe404`; `denunciarMensagemPropriaRecebe404`; `denunciarImagemEscondeSoParaQuemDenunciou`; `pareceMenorSuspendeNaHora` (conta `SUSPENSA`, sessões revogadas, encontro com `SUSPENSAO`); `repetirNaoDuplica`; `decimaPrimeiraEm1hRecebe429`; `detalheCom501Recebe422`; `falhaNaSuspensaoDesfazADenuncia` (tudo na mesma transação).
  - `BloqueioApiTest`: `bloquearEncerraEVetaEOBloqueadoVeTerminou`; `bloqueioValeNosDoisSentidos`; `bloquearDuasVezesE204`; `bloqueadoNaoVeAFoto`.
  - Vitest: `acoesSempreVisiveis`; `denunciarExigeMotivo`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(moderacao): denúncia e bloqueio básicos com efeito imediato no encontro`.

### Task 8: Decisão privada e conexão

**Arquivos:**
- Criar: `src/main/resources/db/migration/V83__decisao_encontro.sql`, `V84__conexao.sql`
- Criar: `src/main/java/bipotheking/net/duora/conexao/dominio/{Resposta,DesfechoDaDecisao,RegrasDeDecisao}.java`, `conexao/{Decisoes,DecisaoController,PrazoDeDecisao,Conexoes,Conexao,AbreDecisaoAoEncerrar,ConexaoAoExcluirConta,CodigoConexao}.java`
- Criar: `frontend/src/conexao/DecisaoTela.tsx`
- Teste: `conexao/dominio/RegrasDeDecisaoTest.java`, `conexao/DecisaoApiTest.java`, `frontend/src/conexao/DecisaoTela.test.tsx`

**Interfaces:**
- Produz:
  - Passo 0: `mattpocock-skills:domain-modeling` para a Conexão (decisão, desfechos, vetos, desfazer); se mudar algo abaixo, ajustar as Interfaces antes de codar.
  - `V83`: `decisao_encontro(encontro_id uuid not null references encontro(id) on delete cascade, conta_id uuid not null references conta(id), resposta text not null check (resposta in ('SIM','NAO','OUTRO_ENCONTRO')), automatica boolean not null default false, respondida_em timestamptz not null, primary key (encontro_id, conta_id))`.
  - `V84`: `conexao(id uuid primary key, conta_menor uuid not null references conta(id), conta_maior uuid not null references conta(id), encontro_id uuid references encontro(id) on delete set null, criada_em timestamptz not null, desfeita_em timestamptz, check (conta_menor < conta_maior))`; `create unique index uma_conexao_ativa_por_par on conexao (conta_menor, conta_maior) where desfeita_em is null`; índice em `conta_maior`.
  - `conexao.dominio`: `enum Resposta { SIM, NAO, OUTRO_ENCONTRO }`; `enum DesfechoDaDecisao { PENDENTE, CONEXAO, VETO, REENCONTRO_LIBERADO }` (nome distinto de `game.Desfecho`, a Strategy dos cenários; ADR 0003); `RegrasDeDecisao.desfecho(Optional<Resposta> a, Optional<Resposta> b) -> DesfechoDaDecisao` (qualquer `NAO` ⇒ `VETO`; dois `SIM` ⇒ `CONEXAO`; alguma ausente ⇒ `PENDENTE`; resto ⇒ `REENCONTRO_LIBERADO`).
  - `Decisoes implements RegistroDeDecisaoAutomatica`: `void responder(String encontroId, ContaId quem, Resposta r)` e `void registrarAutomatica(String encontroId, ContaId quem)`. Numa transação que começa com `SELECT ... FROM encontro WHERE id = ? FOR UPDATE`: grava (a PK impede a 2ª resposta ⇒ 409 `DECISAO_JA_REGISTRADA`); calcula o desfecho; `CONEXAO` ⇒ `Conexoes.criar` + `vetar(par, CONECTADOS)` + evento durável `conexao.criada` `{ "conexaoId", "apelido", "fotoBorradaUrl" }` para os dois; `VETO` ⇒ `vetar(par, NAO)`, ou `SEM_DECISAO` quando veio do prazo. Nenhum evento para os outros desfechos.
  - `AbreDecisaoAoEncerrar` (ouvinte de `EncontroEncerrado`, na transação): encontro que chegou a `EmAndamento` abre a decisão; `CANCELADO` e `CONVITE_EXPIROU` não.
  - `Conexoes`: `UUID criar(ParDeContas par, String encontroId)`; `Optional<Conexao> ativaEntre(ContaId, ContaId)`; `List<Conexao> ativasDe(ContaId)`; `Optional<Conexao> ativaPorId(UUID id, ContaId participante)`; `boolean desfazer(UUID id, ContaId quem)` (`UPDATE ... SET desfeita_em = :agora WHERE id = :id AND desfeita_em IS NULL AND :quem IN (conta_menor, conta_maior)`); `record Conexao(UUID id, ParDeContas par, Instant criadaEm)`.
  - Rotas: `POST /api/encontros/{id}/decisao` `{ "resposta" }` com `Idempotency-Key` ⇒ sempre 200 `{ "registrada": true }` (Foco 1); de quem não participou ou encontro sem decisão ⇒ 404 `DECISAO_INEXISTENTE`. `GET /api/encontros/{id}/decisao` ⇒ `{ "minhaResposta": "SIM"|null, "prazo" }`, nunca a do outro.
  - `PrazoDeDecisao` (`@Scheduled` a cada 10 min): 24 h depois do fim, a resposta que falta vira `NAO` automática.
  - `ConexaoAoExcluirConta implements AoExcluirConta`: desfaz as conexões e apaga as decisões da conta.
  - Front `DecisaoTela`: "Quer continuar a conversa com {apelido}?" com "Sim, quero me conectar", "Agora não" e "Topo outro encontro antes de decidir"; depois, o mesmo texto para qualquer resposta: "Resposta registrada. Se for mútuo, {apelido} aparece nas suas conexões."; ao receber `conexao.criada`, "Vocês se conectaram." `AcoesDeSeguranca` no topo.

- [ ] **Passo 1:** testes.
  - `RegrasDeDecisaoTest` (tabela com `Optional.empty()`, valores literais): inclui `SIM`+`OUTRO_ENCONTRO` ⇒ `REENCONTRO_LIBERADO` e `NAO`+vazio ⇒ `VETO`.
  - `DecisaoApiTest`: `simMutuoCriaConexaoEAvisaOsDois`; `simENaoNaoCriaNadaENaoAvisa` (A sem evento em 2 s); `respostaIdenticaParaSimNaoEOutro` (Foco 1: corpos e status iguais byte a byte); `getNuncaMostraARespostaDoOutro` (chaves exatas); `semDecisaoEm24hContaComoNao` (23:59 pendente; 24:00 + ciclo ⇒ `SEM_DECISAO`); `outroEncontroDosDoisNaoVeta`; `simSimultaneoCriaUmaConexaoSo` (duas threads com latch); `segundaRespostaRecebe409`; `mesmaChaveDevolveAMesmaResposta`; `terceiroRecebe404`; `encontroCanceladoNaoTemDecisao`; `excluirContaDesfazConexoes`.
  - Vitest: `mesmaMensagemParaQualquerResposta`; `conexaoCriadaMostraVocesSeConectaram`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): decisão privada com sim mútuo e recusa invisível`.

### Task 9: Tela do encontro montada e exclusão de conta

**Arquivos:**
- Criar: `frontend/src/encontro/{TelaDoEncontro,CabecalhoDoEncontro,useEncontro}.tsx|ts`, `src/main/java/bipotheking/net/duora/encontro/EncontroAoExcluirConta.java`
- Modificar: a tela do cenário (filha da `TelaDoEncontro`), o roteador (`/encontro`, `/encontros/:id/decisao`)
- Teste: `frontend/src/encontro/{TelaDoEncontro,useEncontro}.test.tsx`, `encontro/EncontroAoExcluirContaTest.java`

**Interfaces:**
- Produz:
  - `useEncontro()`: a `Visao` vem de `encontro.estado`; com o SSE caído (o `canal` em reconexão por mais de 10 s), faz polling com ETag de `GET /api/encontro` a cada 2 s até voltar; `encontro.encerrado` com `decisao` verdadeiro ⇒ `/encontros/:id/decisao`; com falso ⇒ `/encontrar` com "Denúncia enviada." ou "Pessoa bloqueada.".
  - `CabecalhoDoEncontro`: foto borrada (ou silhueta) e apelido do outro, `Cronometro`, `PainelDeVoz` (contexto `{ tipo: 'ENCONTRO', id: encontroId }`) e `AcoesDeSeguranca`.
  - Layout: até 640 px, o chat fica na aba "Conversa" ao lado de "Cenário", com contador de não lidas; acima disso, ao lado do cenário. Voz e ações sempre visíveis.
  - `EncontroAoExcluirConta implements AoExcluirConta`: encerra o encontro ativo com `SAIU` (o outro vê `TERMINOU`), apaga mensagens e imagens da conta (arquivos depois do commit).

- [ ] **Passo 1:** testes: `estadoChegaPeloSse`; `sseCaidoVoltaAoPolling`; `encerradoComDecisaoNavega`; `acoesEVozSempreVisiveisNoCelular`; `EncontroAoExcluirContaTest.excluirNoMeioDoEncontroEncerraEApagaOProprioConteudo`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(web): tela do encontro com cenário, chat, voz e ações de segurança`.

### Task 10: E2E da jornada completa

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/{JornadaDoEncontroIT,RecusaInvisivelIT,SaidaEModeracaoIT,ImagensDoEncontroIT,EncontroPagina}.java`
- Modificar: `e2e/AxeIT.java`, `.claude/handoff/_mapa.md` (`room/` → `encontro/`, `moderacao/`, `conexao/`)

**Interfaces:**
- Produz: `EncontroPagina` com `enviarMensagem(String)`, `ultimaMensagem()`, `abrirLink(String dominio)`, `permitirImagens()`, `enviarImagem(Path)`, `verImagem()`, `pedirEncerramento()`, `aceitarEncerramento()`, `sair()`, `denunciar(String motivo)`, `bloquear()`, `decidir(String botao)`.

- [ ] **Passo 1:** ITs.
  - `JornadaDoEncontroIT.doPareamentoAConexao`: fila pública ⇒ pareadas ⇒ uma peça cada ⇒ mensagens (uma com `https://exemplo.com`; o aviso mostra `exemplo.com` e o `<a>` tem o `rel` exato) ⇒ Ana pede para encerrar, Bia aceita ⇒ duas vezes "Sim" ⇒ "Vocês se conectaram." nas duas.
  - `RecusaInvisivelIT.simDeUmENaoDoOutroParecemEspera`: Ana "Sim", Bia "Agora não"; a tela de Ana fica idêntica (texto e captura do painel) à de quando Bia ainda não respondeu; Ana não recebe `conexao.criada`.
  - `SaidaEModeracaoIT`: `sairLevaADecisaoEOOutroVeTerminou`; `denunciarEncerraEOOutroVeOMesmoTexto`; `bloquearEncerraENaoPareiaDeNovo` (4 ciclos sem par).
  - `ImagensDoEncontroIT.imagemSoComOsDoisEChegaBorrada`: envio indisponível até as duas permitirem; chega borrada; "Ver imagem" mostra a nítida; Bia denuncia e a imagem some só para ela.
  - `AxeIT`: `encontroComChatSemViolacoes`, `avisoDeSaidaSemViolacoes`, `avisoDeImagensSemViolacoes`, `decisaoSemViolacoes`.
- [ ] **Passo 2:** rodar em chromium, firefox e webkit: verde, sem skip. `_mapa.md` atualizado. Commit `test(e2e): jornada do pareamento à decisão mútua com recusa invisível`.

### Task 11: Ações rápidas dentro da cena (paridade)

**Arquivos:**
- Criar: `frontend/src/encontro/AcoesRapidasNaCena.tsx` + teste
- Modificar: a tela do cenário (o componente da cena da 1a/1c recebe a barra como filho sobreposto, inclusive no modo tela cheia da revelação da 1c), `frontend/src/moderacao/AcoesDeSeguranca.tsx` (extrai os diálogos para reuso, num commit de refatoração)
- Criar: `src/test/java/bipotheking/net/duora/e2e/AcoesRapidasIT.java`; Modificar: `AxeIT`

**Interfaces:**
- Consome: `DenunciarDialogo`, `BloquearDialogo` (Task 7), `SairDoEncontro` (Task 4), `useChamadaDeVoz().silenciarOutro` (Etapa 7).
- Produz: `AcoesRapidasNaCena` (`role="toolbar"`, `aria-label="Ações rápidas"`), quatro botões dentro da área da cena, sempre visíveis em montagem e revelação:
  - `Sair`: o primeiro toque troca o rótulo para `Confirmar saída` por 3 s no mesmo lugar (dois toques no mesmo ponto, sem diálogo); o segundo chama `POST /api/encontro/sair`;
  - `Silenciar` / `Ouvir de novo` (`aria-pressed`), só com voz ativa; silencia o áudio recebido na hora, sem avisar o outro;
  - `Denunciar` e `Bloquear`: um toque abre o diálogo da Task 7 já com o contexto do encontro.
  - Alvos de no mínimo 44 × 44 px; no celular, a barra fica no canto inferior da cena, acima da área segura (`env(safe-area-inset-bottom)`); com movimento reduzido, sem animação.
- [ ] **Passo 1:** testes. Vitest: `sairPedeSegundoToqueNoMesmoLugar` (3 s sem o segundo toque → volta a `Sair`, nada enviado); `silenciarSoComVoz`; `denunciarAbreDialogoComContextoDoEncontro`. `AcoesRapidasIT` (360 × 640 e 1280 × 800): `quatroAcoesVisiveisSemRolar` (caixas dentro da janela, ≥ 44 × 44); `acoesNaRevelacaoEmTelaCheia`; `silenciarCortaOAudioRecebido` (energia do áudio recebido cai a zero com o fake media do Chromium). `AxeIT`: `acoesRapidasSemViolacoes`.
- [ ] **Passo 2:** RED; implementar; verde nos três navegadores (no WebKit, `silenciarCortaOAudioRecebido` afirma só o `aria-pressed`, como a Etapa 7). Commit `feat(encontro): sair, silenciar, denunciar e bloquear a um toque dentro da cena`.

### Task 12: "Tem certeza?", "Isso te incomodou?" e alertas de contato, link e PIX (paridade)

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/web/texto/{AnaliseDeMensagem,Analise,SinalDeRisco,TermosOfensivos,Normalizacao}.java`, `src/main/resources/moderacao/termos-ofensivos-pt-br.txt`
- Modificar: `encontro/{ChatDoEncontro,MensagemVisao,ChatDoEncontroController}.java`, `frontend/src/encontro/ChatDoEncontro.tsx`
- Criar: `frontend/src/encontro/{TemCerteza,IssoTeIncomodou,AlertaDeRisco}.tsx` + testes
- Teste: `web/texto/{AnaliseDeMensagemTest,NormalizacaoTest,TermosOfensivosTest}.java`, `encontro/ChatDoEncontroApiTest.java` (casos novos)

**Interfaces:**
- Produz:
  - `Normalizacao.normalizar(String) -> String`: minúsculas; NFD sem marcas (`ç` → `c`); `0→o 1→i 3→e 4→a 5→s 7→t @→a $→s`; letra repetida 3+ vezes vira uma; pontuação vira espaço.
  - `TermosOfensivos` (bean; carrega o arquivo na construção, uma entrada por linha, `#` comenta, termos com mais de uma palavra aceitos; arquivo ausente ou vazio → falha no boot): `boolean contem(String textoNormalizado)` por palavra inteira. O arquivo começa com pelo menos 80 entradas em três blocos comentados (`# xingamentos`, `# assédio sexual`, `# ódio e discriminação`), escritas pelo implementador e revisáveis no playtest; é dado, não código.
  - `enum SinalDeRisco { CONTATO, LINK, DINHEIRO }`; `record Analise(boolean ofensiva, Set<SinalDeRisco> sinais)`; `AnaliseDeMensagem.analisar(String texto) -> Analise`:
    - `CONTATO`: telefone brasileiro (10 a 13 dígitos com DDD/+55, separadores opcionais), e-mail, `@usuario` (3+ caracteres) ou as palavras `whatsapp`, `zap`, `wpp`, `telegram`, `insta`, `instagram`, `snap`, `discord`;
    - `LINK`: algum `Segmento.Link` de `Links.segmentar`;
    - `DINHEIRO`: `pix`, `chave pix`, `transferir`, `transferência`, `empréstimo`, `emprestar`, `deposita`, `boleto`, `cripto`, `bitcoin`, `investimento`, `R$` seguido de número, ou um CPF (`000.000.000-00` ou 11 dígitos).
  - Envio: `POST /api/encontro/mensagens` aceita `"confirmarEnvio": true`. Texto ofensivo sem a confirmação → 422 `CONFIRMAR_ENVIO` (`Tem certeza? Essa mensagem pode magoar quem recebe.`), nada gravado e a `Idempotency-Key` não é consumida (o reenvio confirmado usa uma chave nova). Com a confirmação, grava com `sinalizada = true`.
  - `MensagemVisao` ganha `List<String> alertas` (só para quem recebe; vazia em `minha`) e `boolean perguntarSeIncomodou` (= `sinalizada` e não `minha`). Nenhum dos dois revela o termo encontrado.
  - Front:
    - `TemCerteza` (`role="alertdialog"`): título `Tem certeza?`, o texto do servidor, botões `Editar` (foco inicial) e `Enviar mesmo assim`;
    - `IssoTeIncomodou` abaixo da mensagem sinalizada: `Isso te incomodou?` com `Sim, denunciar` (abre o `DenunciarDialogo` com "Assédio ou ofensa" e o contexto `MENSAGEM_ENCONTRO`) e `Não` (esconde só nesta tela);
    - `AlertaDeRisco` abaixo da mensagem recebida: `CONTATO` → `Compartilhar contato cedo expõe você. Passe o seu só quando se sentir à vontade.`; `DINHEIRO` → `Atenção: pedido de PIX ou dinheiro é o golpe mais comum em apps de encontro. Nunca envie dinheiro para quem você conheceu aqui.` com `Denunciar golpe` (motivo "Spam ou golpe"); `LINK` → selo `Link externo` (o aviso de saída da Task 5 continua).
- [ ] **Passo 1:** testes.
  - `NormalizacaoTest`: `"Ç@F4JJJESTE"` → `"cafajeste"`; acentos somem; pontuação vira espaço.
  - `TermosOfensivosTest` (arquivo de teste com 3 termos): palavra inteira casa; termo dentro de outra palavra não casa (`"computador"` não casa `"puta"`); termo de duas palavras casa; arquivo vazio falha no boot.
  - `AnaliseDeMensagemTest` `@ParameterizedTest`: `"me chama no zap"` → `CONTATO`; `"(11) 98765-4321"` → `CONTATO`; `"+55 11 987654321"` → `CONTATO`; `"ana@exemplo.com"` → `CONTATO`; `"vi 12 filmes"` → nenhum; `"me empresta 50 reais no pix?"` → `DINHEIRO`; `"R$ 200"` → `DINHEIRO`; `"123.456.789-09"` → `DINHEIRO`; `"https://exemplo.com"` → `LINK`; texto limpo → `Analise(false, [])`.
  - `ChatDoEncontroApiTest`: `ofensivaSemConfirmacaoRecebe422ENadaGrava`; `ofensivaConfirmadaChegaSinalizada` (o outro recebe `perguntarSeIncomodou: true`; quem enviou recebe `false`); `alertasSoParaQuemRecebe`; `alertaNaoRevelaOTermo` (o corpo não contém o termo canário).
  - Vitest: `temCertezaFocaEditar`; `issoTeIncomodouAbreDenunciaComAssedio`; `alertaDeDinheiroOfereceDenunciarGolpe`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): avisos antes e depois de mensagem ofensiva e alertas de contato, link e PIX`.

### Task 13: Triagem automática das imagens (Falconsai via ONNX Runtime Java) (paridade)

**Arquivos:**
- Modificar: `pom.xml` (`com.microsoft.onnxruntime:onnxruntime`, versão estável atual conferida no Maven Central/Context7, só CPU)
- Criar: `src/main/java/bipotheking/net/duora/web/imagens/{ClassificadorDeImagem,Classificacao,TriagemDeImagem,Triagem,LimiaresDeTriagem,ClassificadorOnnx,ClassificadorIndisponivel}.java`, `encontro/TriagemDasImagensDoEncontro.java`, `docs/adr/<próximo número livre>-triagem-de-imagens-com-onnx.md`, `docs/modelos/nsfw.md` (origem, licença Apache-2.0, comando de export, SHA-256)
- Modificar: `web/ProcessadorDeImagens.java` (variante temporária `MINIATURA_TRIAGEM`: 224 × 224 sRGB PNG sem metadados, `vips thumbnail ... 224 --height 224 --size force`, nunca guardada), `encontro/ImagensDoEncontro.java`, `moderacao/Denuncias.java` (`registrarDaTriagem`), `moderacao/OrigemDaDenuncia.java` (novo: `enum OrigemDaDenuncia { PESSOA, TRIAGEM }`, lido pela Etapa 8), `frontend/src/encontro/ImagemBorrada.tsx`, `application.properties` (`duora.moderacao.nsfw.*`)
- Teste: `web/imagens/{TriagemDeImagemTest,ClassificadorOnnxTest}.java`, `encontro/TriagemDasImagensDoEncontroApiTest.java`, `frontend/src/encontro/ImagemBorrada.test.tsx` (casos novos); fixtures seguras em `src/test/resources/imagens/seguras/` (renders do projeto e rostos gerados por IA pela skill `duora-assets`)

**Interfaces:**
- Produz:
  - Porta `public interface ClassificadorDeImagem { Classificacao classificar(Path miniaturaPng); }` com `record Classificacao(double nsfw)` (0..1).
  - `record LimiaresDeTriagem(double fila, double bloqueio)` (`PADRAO = (0.50, 0.90)`, de `duora.moderacao.nsfw.fila|bloqueio`); `enum Triagem { LIBERADA, ZONA_CINZENTA, BLOQUEADA }`; `TriagemDeImagem.decidir(Classificacao, LimiaresDeTriagem) -> Triagem` (pura; `fila` e `bloqueio` inclusivos).
  - `ClassificadorOnnx` (bean com `duora.moderacao.nsfw.modelo` apontando para o `.onnx` fora do Git; confere o SHA-256 de `duora.moderacao.nsfw.sha256` no boot): um `OrtSession` só (2 threads intra-op), entrada `float[1][3][224][224]` com `(pixel/255 − 0,5)/0,5` por canal, saída por softmax; o índice da classe `nsfw` vem de `duora.moderacao.nsfw.indice` (conferido no `config.json` do modelo no export). Execução num executor limitado (2 threads, fila de 16) com timeout de 5 s.
  - `ClassificadorIndisponivel` (sem modelo configurado, padrão em `dev`/`test`): lança → o envio de imagem responde 503 `TRIAGEM_INDISPONIVEL` (`Não foi possível enviar imagens agora. Tente de novo em alguns minutos.`). Os testes de API usam um classificador falso com pontuação controlada.
  - Export (uma vez, documentado em `docs/modelos/nsfw.md`): `optimum-cli export onnx --model Falconsai/nsfw_image_detection` e quantização dinâmica int8 (`onnxruntime.quantization.quantize_dynamic`); o arquivo vai para o volume `duora.imagens.dir/../modelos/` na VPS (Etapa 10) e para o cache da CI só no job que roda o `ClassificadorOnnxTest`.
  - `TriagemDasImagensDoEncontro` (no envio da Task 6, depois de `processar` e antes de gravar):
    - `BLOQUEADA` → 422 `IMAGEM_RECUSADA` (`Esta imagem parece violar as regras e não foi enviada.`), nada gravado, contador `duora.moderacao.triagem{resultado=bloqueada}`;
    - `ZONA_CINZENTA` → grava, entrega com `ImagemVisao.sensivelProvavel = true` e chama `Denuncias.registrarDaTriagem(ContaId denunciado, MotivoDenuncia motivo, ContextoDenuncia contexto)` com o autor, `IMAGEM_SENSIVEL` e o contexto `IMAGEM_ENCONTRO` (origem `TRIAGEM`, sem denunciante; a Etapa 8 a põe como P1);
    - `LIBERADA` → segue a Task 6.
  - `ImagemVisao` ganha `boolean sensivelProvavel`; `ImagemBorrada` com ele mostra `Esta imagem pode ter conteúdo sensível. Ver mesmo assim?` antes de `Ver imagem`.
- [ ] **Passo 1:** testes.
  - `TriagemDeImagemTest`: 0,4999 → `LIBERADA`; 0,50 → `ZONA_CINZENTA`; 0,8999 → `ZONA_CINZENTA`; 0,90 → `BLOQUEADA`.
  - `TriagemDasImagensDoEncontroApiTest` (classificador falso): `bloqueadaRecebe422ENadaNoDiscoNemNoBanco`; `zonaCinzentaChegaBorradaComAvisoEDenunciaAutomatica` (uma `denuncia` com `origem = 'TRIAGEM'` e `denunciante` nulo); `liberadaSegueNormal`; `classificadorQueLancaRecebe503ENadaEntregue` (Foco 7); `classificadorLentoRecebe503EmMenosDe6s` (Foco 7); `segundaZonaCinzentaDaMesmaImagemNaoDuplicaDenuncia`.
  - `ClassificadorOnnxTest` (`@EnabledIfSystemProperty(named = "duora.moderacao.nsfw.modelo", matches = ".+")`, roda no job da CI com o modelo em cache e localmente pelo agente): `modeloComHashErradoNaoSobe`; `imagensSegurasFicamAbaixoDaFila` (≥ 98 % das fixtures seguras abaixo de 0,50; a lista das pontuações vai para o relatório da task e calibra os limiares; se falhar, o agente propõe novos valores ao controlador, sem mudar o teste).
  - Vitest: `sensivelProvavelPedeConfirmacaoAntesDeVer`.
- [ ] **Passo 2:** RED; exportar e conferir o modelo; implementar; verde. ADR (Falconsai Apache-2.0 + ONNX Runtime Java; alternativas Bumble Private Detector, NSFWJS só no cliente, Sightengine pago como reserva da Etapa 8). Commit `feat(encontro): triagem automática das imagens antes da entrega`.

### Task 14: Fluxo de abuso infantil — remoção, preservação e reporte (paridade, ECA Digital art. 27)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V87__caso_abuso_infantil.sql`
- Criar: `src/main/java/bipotheking/net/duora/moderacao/{CasosDeAbusoInfantil,CasoDeAbusoInfantil,OrigemDoSinal,PacoteDeReporte,RetencaoDeCasos}.java`, `encontro/{RemocaoDeImagem,MetadadosDaImagem}.java`
- Modificar: `encontro/ImagensDoEncontro.java` (imagem removida some para todos), `web/ArmazemDeImagens.java` (categoria `preservacao` com permissão `rw-------` e método `copiar(String origem, String destino, UUID id)`, se a Etapa 8 ainda não o criou)
- Criar: `encontro/VarreduraNaBorda.java`, `encontro/web/VarreduraController.java` (D2a); Modificar: a allowlist de rotas públicas (`/varredura/**`)
- Teste: `moderacao/CasosDeAbusoInfantilTest.java`, `moderacao/RetencaoDeCasosTest.java`, `encontro/VarreduraNaBordaTest.java`

**Esquema (V87):** `alter table imagem_encontro add column removida_em timestamptz`; `caso_abuso_infantil(id uuid primary key, imagem_id uuid not null unique, encontro_id uuid not null, conta_suspeita uuid references conta(id), origem text not null check (origem in ('CLOUDFLARE','ADMIN','DENUNCIA')), metadados jsonb not null, aberto_em timestamptz not null, aberto_por uuid references conta(id), reportado_em timestamptz, protocolo text check (char_length(protocolo) <= 100), material_apagado_em timestamptz, check (material_apagado_em is null or reportado_em is not null))`. Sem FK para `imagem_encontro`: a retenção do encontro não pode apagar o caso.

**Interfaces:**
- Consome: `SuspensaoDeContas.suspender` (Task 7), `EfeitosDaModeracaoNoEncontro` (Task 7, encerra o encontro do suspenso), `ArmazemDeImagens`, `RegistroDeAcessos` (Etapa 2, só leitura dos registros da conta suspeita nas 24 h em volta do envio).
- Produz:
  - `enum OrigemDoSinal { CLOUDFLARE, ADMIN, DENUNCIA }`.
  - `CasosDeAbusoInfantil` (a Etapa 8 liga as rotas e o painel; esta task entrega a API Java e os testes):
    - `UUID abrir(UUID imagemId, OrigemDoSinal origem, Optional<ContaId> admin)`, numa transação: `removida_em` na imagem (some para os dois na hora, com o evento `encontro.estado`); copia nítida e borrada para `preservacao/` (cópia síncrona antes do commit; rollback apaga a cópia no `afterCompletion`); grava o caso com `metadados` = conta do autor, encontro, cenário, instante do envio, largura, altura, SHA-256 do arquivo e os registros de acesso da conta nas 24 h em volta; suspende a conta (`"suspeita de abuso infantil"`). Idempotente pela `unique (imagem_id)`.
    - `void registrarReporte(UUID caso, String protocolo, ContaId admin)` (reporte manual à Polícia Federal pela Denúncia Online/SaferNet até haver canal automatizado; grava `reportado_em` e o protocolo).
    - `void apagarMaterial(UUID caso, ContaId admin)`: só depois do reporte (senão 409 `REPORTE_PENDENTE`); apaga os arquivos de `preservacao/` depois do commit e grava `material_apagado_em` (Decreto 12.880/2026 art. 39 §2º: apagar após a confirmação da autoridade).
    - `PacoteDeReporte pacote(UUID caso)`: os metadados e os caminhos internos dos arquivos, para o admin montar o reporte; nunca sai por log.
  - `RetencaoDeCasos implements PoliticaDeRetencao`: `casos-de-abuso-infantil`, apaga a linha 183 dias depois de `material_apagado_em`; caso sem material apagado nunca expira.
  - A cópia em `preservacao/` é independente: o original do encontro pode expirar pela retenção da Task 6 sem afetar o caso.
  - `VarreduraNaBorda` (D2a; `duora.csam.varredura.habilitada`, `false` por padrão e `true` em `prod`): depois do commit da imagem aprovada pela triagem (Task 13), monta `https://{duora.dominio}/varredura/{imagemId}/{expiraEm}/{assinatura}.webp` (`expiraEm` = agora + 10 min em segundos; `assinatura` = HMAC-SHA256 em base64url de `imagemId|expiraEm` com `DUORA_SEGREDO_VARREDURA`) e faz um `GET` nela pela internet, para a nítida entrar no cache da Cloudflare e ser varrida. A rota (allowlist pública) confere assinatura e prazo em tempo constante (senão 404), devolve a nítida com `Cache-Control: public, max-age=600`, `X-Robots-Tag: noindex` e 404 para imagem removida. A URL nunca vai para cliente, log nem `MensagemVisao`; falha do `GET` vira só log WARN (nunca bloqueia a entrega).
- [ ] **Passo 1:** testes `CasosDeAbusoInfantilTest` (`BancoDeTeste`): `abrirRemoveParaOsDoisNaHora` (o `GET` da imagem dá 404 para os dois); `abrirPreservaCopiaRestrita` (arquivo em `preservacao/` com permissão `rw-------` e SHA-256 igual ao dos metadados); `abrirSuspendeEEncerraOEncontro`; `abrirDuasVezesNaoDuplica`; `falhaNaSuspensaoNaoDeixaCopia`; `apagarAntesDoReporteRecebe409`; `apagarDepoisDoReporteRemoveSoOsArquivos` (a linha fica); `expiracaoDoChatNaoApagaAPreservacao` (rodar a retenção de imagens 31 dias depois: a cópia continua); `logNaoTrazCaminhoNemHash`. `RetencaoDeCasosTest`: 183 dias + 1 s depois do apagamento sai; sem apagamento fica.
  - `VarreduraNaBordaTest`: `assinaturaValidaServeANitidaComCachePublico`; `assinaturaAlteradaDa404`; `prazoVencidoDa404` (10 min + 1 s); `imagemRemovidaDa404NaVarredura`; `aquecimentoSoDepoisDoCommit` (rollback → nenhum `GET`); `desligadaNaoAquece`; `falhaDoAquecimentoNaoBloqueiaAEntrega`; `urlNuncaApareceNaVisaoNemNoLog` (canário da assinatura ausente do JSON da `MensagemVisao` e da saída capturada).
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(moderacao): abuso infantil com remoção na hora, preservação restrita e registro do reporte`.

### Task 15: Clipe de voz de 60 s só no aparelho, para denúncia (paridade)

**Arquivos:**
- Criar: `frontend/src/voz/clipe/{gravadorCircular.worklet.ts,BufferCircular.ts,ClipeDeVoz.ts,wav.ts,AvisoDoClipe.tsx}` + testes
- Modificar: `frontend/src/encontro/TelaDoEncontro.tsx` (liga o clipe ao `fluxoRemoto` e o descarta ao encerrar ou desmontar), `frontend/src/moderacao/DenunciarDialogo.tsx` (opção do clipe), o hook da Etapa 7 (só se faltar `fluxoRemoto`, em commit de refatoração)
- Criar: `src/main/resources/db/migration/V88__clipe_de_voz.sql`, `src/main/java/bipotheking/net/duora/moderacao/{ClipesDeVoz,ClipeDeVozController}.java`
- Teste: `frontend/src/voz/clipe/{BufferCircular,wav,ClipeDeVoz}.test.ts`, `frontend/src/moderacao/DenunciarDialogo.test.tsx` (casos novos), `moderacao/ClipeDeVozApiTest.java`, `e2e/ClipeDeVozIT.java`

**Esquema (V88):** `clipe_de_voz(denuncia_id uuid primary key references denuncia(id) on delete cascade, duracao_ms int not null check (duracao_ms between 1000 and 61000), bytes int not null check (bytes between 1 and 2000000), recebido_em timestamptz not null)`. O arquivo fica em `moderacao/clipes/<denuncia_id>.wav` (`ArmazemDeImagens` ou um `ArmazemDeArquivos` irmão com a mesma regra de permissão), servido só ao admin (Etapa 8).

**Interfaces:**
- Produz:
  - `BufferCircular(int capacidadeAmostras)` com `escrever(Int16Array)`, `ler(): Int16Array` (em ordem cronológica), `duracaoMs(taxa)`, `zerar()`; capacidade = 60 s × 16 000 Hz = 960 000 amostras (≈ 1,9 MB).
  - `gravadorCircular.worklet.ts` (`AudioWorkletProcessor`, carregado de `/app/` pela CSP atual): mixa para mono, reamostra para 16 kHz e manda blocos `Int16Array` ao `BufferCircular`. Fonte: **só** o `fluxoRemoto` (nunca o microfone próprio).
  - `ClipeDeVoz`: `ligar(fluxoRemoto: MediaStream)`, `temAudio(): boolean` (≥ 1 s gravado), `exportarWav(): Blob` (PCM 16 bits, 16 kHz, mono, cabeçalho RIFF), `descartar()` (zera o buffer, fecha o `AudioContext`). Nada é persistido no navegador (nem IndexedDB, nem `localStorage`).
  - `AvisoDoClipe` (`role="note"`) no início de todo encontro, fechável: `Para sua segurança, seu aparelho guarda só os últimos 60 segundos do áudio que você recebe. Nada sai dele, a não ser que você denuncie e escolha enviar.`
  - `DenunciarDialogo`: com `temAudio()`, caixa desmarcada `Enviar os últimos 60 segundos do áudio que recebi` com a ajuda `O áudio sai do seu aparelho só se você marcar esta opção, e vai só para a moderação.`; marcada, depois do `201` da denúncia, envia o clipe.
  - `POST /api/denuncias/{id}/clipe` (multipart `audio`, `audio/wav`): só quem denunciou, só denúncia de contexto `ENCONTRO` ou `MENSAGEM_ENCONTRO`, só até 10 min depois da denúncia, uma vez (PK) → 201; bytes mágicos `RIFF....WAVE`, formato 16 kHz mono 16 bits, até 2 MB e 61 s, senão 422 `CLIPE_INVALIDO`; outra conta → 404. O clipe é evidência da denúncia (retenção da Etapa 8: 30 dias depois de resolvida), não um asset; por isso fica em WAV, fora da regra de formato dos assets.
- [ ] **Passo 1:** testes.
  - Vitest: `bufferGuardaSoOsUltimos60s` (escrever 70 s → `ler` tem 60 s, começando no segundo 10); `zerarLimpaTudo`; `wavTemCabecalhoCerto` (`RIFF`, `WAVE`, 16000, 1 canal, 16 bits); `descartarAoEncerrarOEncontro` (evento `encontro.encerrado` → buffer zerado e `AudioContext` fechado) (Foco 6); `semCaixaMarcadaNenhumPostDeClipe` (Foco 6); `clipeSoComAudioDeVerdade` (menos de 1 s → a caixa nem aparece); `avisoApareceNoInicioDeTodoEncontro`; `nadaNoStorage`.
  - `ClipeDeVozApiTest`: `denuncianteEnviaUmaVez`; `segundoEnvioRecebe409`; `outraContaRecebe404`; `depoisDe10MinRecebe422`; `mp3OuEstereoRecebe422`; `maisDe2MbRecebe413`; `denunciaDeConexaoNaoAceitaClipe`.
  - `ClipeDeVozIT` (Chromium com fake media; Firefox e WebKit afirmam só a UI): Ana e Bia falando; Ana denuncia marcando a caixa → a evidência tem um `clipe_de_voz` com 1–61 s; Bia denuncia sem marcar → nenhum.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(voz): clipe de 60 s só no aparelho, enviado apenas com denúncia confirmada`.

### Task 16: Quebra-gelos nos objetos da cena (paridade)

**Arquivos:**
- Modificar: `game/Cenario.java` (lista `quebraGelos`), `game/Terraco.java`, `static/assets/cenarios/terraco/cenario.json` (textos), o leitor e o validador do `cenario.json` da 1b (valibot), `game/CenarioJsonTest.java`
- Criar: `encontro/dominio/{QuebraGelo,Tom,RespostasDoQuebraGelo}.java`, `frontend/src/encontro/QuebraGelo.tsx` + teste
- Modificar: `encontro/dominio/Retrato.java`, `encontro/persistencia/RetratoJson.java`, `encontro/{Encontro,Visao,EncontroController}.java`, a cena (objetos tocáveis)
- Teste: `encontro/dominio/RespostasDoQuebraGeloTest.java`, `encontro/QuebraGeloApiTest.java`, `game/CatalogoTemQuebraGelosTest.java`

**Interfaces:**
- Produz:
  - `record QuebraGelo(String objeto, int perguntasAmizade, int perguntasNamoro)` no `Cenario` (forma só; os textos vivem no `cenario.json` em `quebraGelos: [{ "objeto", "rotulo", "amizade": [{ "pergunta", "opcoes": [3 textos] }], "namoro": [...] }]`, e o `CenarioJsonTest` confere a forma).
  - `enum Tom { AMIZADE, NAMORO }`: `NAMORO` se as intenções em comum do par (Etapa 4, `ParPareado.intencoes()`; no convite privado, `Compatibilidade.intencoesEmComum` dos dois) incluem `NAMORO`; senão `AMIZADE`. Gravado no `Retrato` ao abrir o encontro.
  - `RespostasDoQuebraGelo` (puro, no `Retrato`): `responder(String objeto, int pergunta, Assento quem, int opcao)` — objeto desconhecido, pergunta fora do tom ou opção fora de 0..2 → recusa `QUEBRA_GELO_INVALIDO` (422); segunda resposta da mesma pessoa → recusa (409); `VisaoDaPergunta visao(String objeto, int pergunta, Assento quem)` com `minha`, `doOutro` (só quando os dois responderam) e `ambos`.
  - `POST /api/encontro/quebra-gelo` `{ "objeto", "pergunta", "opcao" }` (só em `EmAndamento`) → 200 `Visao`; `Visao.quebraGelo` = `{ "tom", "respostas": [{ "objeto", "pergunta", "minha", "doOutro", "ambos" }] }`.
  - Front: tocar um objeto marcado (ícone de balão, com rótulo acessível `Pergunta: {rotulo}`) abre `QuebraGelo` com a próxima pergunta ainda não respondida pelo par daquele objeto e as 3 opções; quando os dois respondem: `Vocês escolheram "{a}" e "{b}".` Sem cronômetro, sem obrigação: a cena funciona sem tocar nenhum.
  - Conteúdo do terraço: objetos `telescopio`, `radio`, `garrafa`; 3 perguntas por tom em cada, 3 opções cada. Fixas: telescópio/amizade `Se pudesse viajar para um lugar agora, seria...` (`Praia`, `Montanha`, `Cidade grande`); telescópio/namoro `Um bom encontro começa com...` (`Um café`, `Uma caminhada`, `Um show`). As outras 16 o implementador escreve com estas regras: pt-BR, gênero neutro, nada sexual, nada que peça dado pessoal (onde mora, trabalho, contato), tom leve; vão para o playtest da 1c/9b.
- [ ] **Passo 1:** testes.
  - `RespostasDoQuebraGeloTest`: `respostaDoOutroSoApareceQuandoOsDoisResponderem`; `segundaRespostaRecusada`; `opcaoTresRecusada`; `perguntaDeNamoroEmEncontroDeAmizadeRecusada`.
  - `QuebraGeloApiTest`: `tomDeNamoroSoComNamoroEmComum`; `respostaChegaAoOutroPeloSse`; `sobreviveAoReinicio` (o `RetratoJson` grava as respostas); `foraDeEmAndamentoRecebe409`.
  - `CatalogoTemQuebraGelosTest` `@ParameterizedTest` sobre todo cenário de composição do catálogo: ≥ 3 objetos, 3 perguntas por tom, 3 opções por pergunta, objetos existentes na cena (vale para os cenários da 9b).
  - Vitest: `perguntaAbreComTresOpcoes`; `respostaDoOutroEscondidaAteOsDoisResponderem`; `objetoTemRotuloAcessivel`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): quebra-gelos nos objetos da cena com tom por intenção`.

### Task 17: Cartão-postal do encontro (paridade)

**Arquivos:**
- Criar: `encontro/{CartaoPostal,CartaoPostalController}.java`, `frontend/src/conexao/GuardarCartaoPostal.tsx` + teste
- Modificar: `frontend/src/conexao/DecisaoTela.tsx` (o botão aparece para qualquer resposta, antes e depois de responder)
- Teste: `encontro/{CartaoPostalTest,CartaoPostalApiTest}.java`

**Interfaces:**
- Consome: `ProcessadorDeImagens`/libvips (Etapa 3), o render `noite` e os sprites do cenário (`static/assets/cenarios/<cenario>/`), `cena.json` (posições), `Retrato` (composição final).
- Produz:
  - `CartaoPostal.compor(Cenario cenario, Map<String, String> composicaoPorLugar, LocalDate dia) -> Path` (temporário WebP q88, 1200 × 800): fundo do momento `noite`, sprites nas posições do `cena.json`, faixa inferior com o título do cenário e a data `DD/MM/AAAA` (`vips text` + `composite`). **A assinatura não recebe apelido, foto nem conta**: o cartão não tem dado pessoal. Timeout de 10 s; no máximo 2 composições ao mesmo tempo (semáforo); o temporário cai na política `imagens-temporarias` (Etapa 3).
  - `GET /api/encontros/{id}/cartao-postal` → `image/webp`, `Content-Disposition: attachment; filename="duora-<cenario>-<AAAA-MM-DD>.webp"`, `Cache-Control: private, no-store`; só os dois participantes de um encontro que chegou a `EmAndamento` e enquanto o encontro existir (Etapa 9); senão 404 `CARTAO_INEXISTENTE`.
  - `GuardarCartaoPostal`: botão `Guardar cartão-postal` (download), com a ajuda `Uma lembrança do que vocês montaram, sem nomes nem fotos.`
- [ ] **Passo 1:** testes. `CartaoPostalTest`: `cartaoTem1200Por800EmWebp`; `composicoesDiferentesGeramCartoesDiferentes`; `mesmaComposicaoMesmoCartao` (bytes iguais); `semDadoPessoal` (a assinatura é a única entrada; teste de arquitetura: `CartaoPostal` não depende de `conta`, `perfil` nem `Participante`). `CartaoPostalApiTest`: `osDoisBaixam`; `terceiroRecebe404`; `encontroCanceladoRecebe404`; `cabecalhosDeDownload`. Vitest: `botaoApareceParaQualquerResposta`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(encontro): cartão-postal do encontro para guardar`.

### Task 18: Feedback pós-encontro (trazido da Etapa 11)

A spec de 2026-11-22 põe o feedback pós-encontro na Etapa 5. Esta task implementa **exatamente** a Etapa 11 Task 2 (`2026-11-22-etapa-11-piloto.md`: esquema, interfaces, textos, testes e o ajuste da política de privacidade), com estas trocas:
- migração `V89__feedback_encontro.sql` em vez de `V141`;
- pacote `encontro/` (`encontro/{Feedbacks,FeedbackController,RetencaoDeComentarios}.java`, `encontro/dominio/{RespostaDeFeedback,Comentario}.java`) em vez de `piloto/`; `ParticipacaoEmEncontros` fica em `encontro/` como lá;
- front em `frontend/src/encontro/FeedbackTela.tsx`, aberto pela `DecisaoTela` depois da resposta (e para quem saiu antes);
- commit `feat(encontro): feedback opcional depois do encontro com comentário que expira em 90 dias`.

A Etapa 11 passa a só consumir `encontro.Feedbacks` nas métricas.

## Ordem e paralelismo
- **Leva 1:** Task 1 sozinha (movimento de pacote).
- **Leva 2 (paralela):** Task 2 (front e e2e), Task 3 (persistência) e Task 5 (chat: tabela e pacote próprios; só lê `Encontros.ativoDe`).
- **Leva 3:** Task 4 (depois da 3; muda `Retrato` e `RetratoJson`).
- **Leva 4 (paralela):** Task 6 (depois da 4 e da 5; única que mexe no `Retrato` nesta leva), Task 7 e Task 8 (as duas só dependem da 4; a ligação 7 → 8 é a interface `RegistroDeDecisaoAutomatica`, criada pela 7).
- **Leva 5:** Task 9, depois Task 10.
- **Leva 6 (paridade, paralela, no máximo 4):** Task 11 (front da cena, depois da 9), Task 12 (chat, depois da 5), Task 13 (triagem, depois da 6 e da 7) e Task 16 (quebra-gelo, depois da 4; mexe no `Retrato`, então não corre junto de outra task que o mude).
- **Leva 7:** Task 14 (depois da 13; V87), Task 15 (depois da 7 e da 9; V88), Task 17 (cartão-postal, depois da 8) e Task 18 (feedback, depois da 8; V89). Merge das migrações na ordem V87 → V88 → V89.
- Revisão final (`superpowers:requesting-code-review`), merge, push e CI.
- A Etapa 8 pode correr em paralelo desde a Leva 4, desde que só **acrescente** colunas e tabelas sobre `bloqueio` e `denuncia` em migrações da faixa dela, e reaproveite `SuspensaoDeContas`.

## Decisões do dono
| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| D1 | Terceira resposta na decisão. A spec diz "reencontro só se ninguém disse não", mas com só "sim" e "não" o reencontro nunca acontece (sim mútuo vira conexão; qualquer não veta). | (a) três respostas: "Sim", "Agora não" e "Topo outro encontro antes de decidir" (sem não de ninguém e sem sim mútuo ⇒ o par pode ser pareado de novo); (b) só "Sim" e "Não", e o reencontro fica restrito a encontros cancelados antes de começar | (a): dá sentido à regra de reencontro e tira pressão da decisão; custa um botão. O plano implementa (a); reverter é tirar um valor do enum e um botão |
| D2 | Como o Cloudflare CSAM Scanning Tool enxerga as imagens. A ferramenta varre o que passa **pelo cache** do proxy da Cloudflare; as imagens do encontro saem com `Cache-Control: private, no-store` por um subdomínio que hoje não está atrás da Cloudflare (Etapa 10: Caddy na VPS da Hostinger). Sem cache, nada é varrido. | (a) pôr o domínio atrás do proxy da Cloudflare e servir só a variante **borrada** com URL não adivinhável e cache curto, para que a varredura aconteça sem expor a nítida; (b) manter sem Cloudflare e depender da triagem (Task 13), das denúncias e da marcação manual do admin (`OrigemDoSinal.ADMIN`); (c) avaliar o ROOST Coop/hash próprio depois do piloto | **Decidida (dono, 2026-11-22): (a), com proxy no plano Free.** A doc confirma que a ferramenta compara "content served for your website through the Cloudflare cache" (https://developers.cloudflare.com/cache/reference/csam-scanning/). Como a nítida é a que casa com as listas de hash, quem passa pelo cache é a **nítida**, por uma rota de varredura assinada que só o servidor conhece (Task 14, `VarreduraNaBorda`); o cliente continua recebendo `private, no-store`. Ligada só em `prod` (Etapa 10). Um alerta da Cloudflare traz a URL bloqueada, que contém o id da imagem; o admin abre o caso com `OrigemDoSinal.CLOUDFLARE` |
| D3 | Peso do modelo de triagem na VPS. O ViT do Falconsai tem cerca de 86 M parâmetros (~330 MB em fp32, ~90 MB em int8) e usa CPU e memória da VPS. | (a) int8 com 2 threads; (b) fp32; (c) só Sightengine pago | (a): cabe na VPS e a precisão é conferida no `ClassificadorOnnxTest`; se a calibração reprovar, (b) |
