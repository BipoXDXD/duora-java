# Etapa 6 — Conexões e chat: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` (ou `mattpocock-skills:tdd`) e fecham cada task com `superpowers:verification-before-completion`. O modelo da Conexão vem do `mattpocock-skills:domain-modeling` feito na Etapa 5 (Task 8); se esta etapa precisar mudar algo nele, repetir a skill antes da Task 1.

**Objetivo:** quem se conectou no encontro vê a lista de conexões, o perfil completo com foto nítida, conversa num chat 1:1 persistente (com não lidas, push opcional e voz), e pode desfazer a conexão ou bloquear com efeito imediato nos dois lados.

**Arquitetura:**
- Tudo em `conexao/`, sobre a tabela `conexao` da Etapa 5. Mensagens e leituras ganham tabelas próprias, ordenadas pela **sequência do banco** (`seq`), que também é o cursor das páginas e das não lidas.
- Tempo real pelo SSE da Etapa 4 (`chat.conexao.*`, `conexao.*`), com eventos gravados na transação do efeito; o cliente busca o que perdeu por `?depois=<seq>` ao reconectar e deduplica por id.
- **Observer** em `ConexaoDesfeita`: desfazer e bloquear publicam o mesmo evento; os ouvintes avisam os dois (na transação, evento durável) e encerram a chamada de voz (depois do commit). Assim quem foi bloqueado não sabe que foi.
- A foto nítida passa a valer para conexões ativas no `AcessoAFotoPadrao` da Etapa 3.
- A voz reaproveita a Etapa 7 no modo com toque: `conexao/VozEntreConexoes implements AutorizacaoDeVoz`.
- Push de mensagem e de chamada só pela outbox da Etapa 4.

**Stack:** Java 25, Spring Boot 4.2.0, `JdbcClient`, PostgreSQL 18 + Flyway, Testcontainers, Bucket4j, `com.interaso:webpush` (Etapa 4), React 19 + TypeScript + Tailwind, TanStack Query, Vitest, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (decisões "Jornada", "Reencontro"; Escolhas "Foto", "Voz", "Links", "Sair, denunciar e bloquear"; Etapa 6). Contratos e princípios: `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Planos: Etapas 2, 3, 4, 5, 7 e 9 (`docs/superpowers/plans/2026-11-22-etapa-*.md`).

## Restrições globais
- Valem os contratos entre etapas (inclusive "Princípios de engenharia") e as restrições globais da Etapa 4.
- Migrações: **V100–V119**. Usadas: V100 `mensagem_conexao`, V101 `leitura_conexao`, V102 `preferencia_conversa` e convite na conversa (Task 8). V103–V119 reservadas.
- **Paridade (2026-11-22):** próximo passo depois do sim, lembrete gentil contra o sumiço, ocultar conversa e avisos por conversa (Tasks 8 e 9). Nada punitivo: sem limite de conversas, sem rebaixar ninguém, sem contagem de dias seguidos.
- Erros por `ErroDeNegocio` com `CodigoConexao`; limites por `LimiteDeTaxa`.
- Chat 1:1: texto de **1 a 1000** code points por `TextoDeUsuario`; links pelas regras da Etapa 5 (`Links.segmentar`, aviso de saída, `rel="noopener noreferrer nofollow ugc"`, sem prévia). Sem imagens no chat entre conexões.
- Regra `chat-conexao`: 10 por `PT10S`, chave conta. `Idempotency-Key` aceito em `POST /api/conexoes/{id}/mensagens`.
- Ordem das mensagens, páginas e não lidas: sempre pelo `seq`, nunca por instante.
- Foto nítida: só para a própria pessoa e conexões **ativas**; servida a terceiros com `Cache-Control: private, no-store`; permissão conferida a cada pedido.
- Desfazer e bloquear valem **na hora** nos dois lados: conversa, perfil, foto nítida e chamada somem; a outra pessoa vê só "Esta conexão não está mais disponível.", igual nos dois casos.
- Push: só para quem ativou e está sem stream SSE aberto na hora do despacho; corpo genérico, sem apelido nem trecho: "Você tem uma mensagem nova no Duora." e "Alguém está te chamando no Duora."; no máximo 1 push de mensagem por conexão e destino a cada 10 min.
- Textos em pt-BR, voz ativa, gênero neutro. Estado vazio: "Você ainda não tem conexões. Elas aparecem aqui quando o sim é mútuo."

## Dependências assumidas
- **Etapa 5:** `conexao.{Conexoes,Conexao,CodigoConexao}` (`criar`, `ativaEntre`, `ativasDe`, `ativaPorId(UUID, ContaId)`, `desfazer(UUID, ContaId)`), tabela `conexao` (V84); `moderacao.{Bloqueios,BloqueioRegistrado,ResolvedorDeContexto,ContextoResolvido,TipoDeContextoDenuncia}` com `CONEXAO` e `MENSAGEM_CONEXAO` aceitos na `denuncia`; `web.texto.{TextoDeUsuario,Links,Segmento}`, `encontro.SegmentoVisao` (mover para `web/texto/` se ainda estiver em `encontro/`, num commit de refatoração); `MensagemComLinks`, `AvisoDeSaida`, `AcoesDeSeguranca`.
- **Etapa 7:** `voz.AutorizacaoDeVoz`, `voz.dominio.{ContextoDeVoz,TipoDeContexto}`, `Chamadas.encerrarDoContexto`, `useChamadaDeVoz`, `PainelDeVoz`, `VozPagina`, `Navegador.abrirComMidiaFalsa`.
- **Etapa 4:** `CanalDeEventos` (`publicar`, `conectada`), `useEvento`, `aoReconectar`; `Outbox`, `ManipuladorDeOutbox`, `Idempotencia`; `notificacao.{EnvioDePush,InscricoesDePush}`; `pareamento.VetosDePar`; `sala.EncontrosPrivadosController`.
- **Etapa 3:** `Perfis.completo(ContaId) -> Optional<PerfilCompleto>` com `PerfilCompleto(ContaId conta, String apelido, int idade, Set<Intencao> intencao, String bio, Optional<String> fotoUrl)` (`fotoUrl` aponta para a nítida); `perfil.AcessoAFotoPadrao` e o controller de fotos (`GET /api/fotos/{fotoId}/{variante}`, hoje com `Cache-Control: private, max-age=300`).
- **Etapa 2:** `ContaId`, `@ContaAtual`, `AoExcluirConta`, `LimiteDeTaxa`, `GeradorDeIds`, `BancoDeTeste`, `RelogioDeTeste`, `ContasDeTeste`, `Participantes.novoComConta`.
- **Etapa 9:** `retencao.PoliticaDeRetencao`.

## Decisões de planejamento (o dono pode reverter)
- **P1. Sem confirmação de leitura para o outro.** As não lidas são só de quem recebe.
- **P2. Conversa de conexão desfeita fica 30 dias**, invisível para os dois, só como contexto de denúncia; depois expira (`conversa-desfeita`, `P30D`). As mensagens de quem exclui a conta saem na hora.
- **P3. Depois de desfeita, o convite privado entre as duas pessoas deixa de valer:** o veto `CONECTADOS` só libera o convite enquanto há conexão ativa.
- **P4. Ao reconectar o SSE, o cliente busca `?depois=<maior seq>`** de cada conversa aberta e deduplica por id.

## Foco de revisão
1. **Conta B pede conversa, perfil, foto nítida ou mensagens de uma conexão entre A e C:** 404 com corpo sem apelido, texto ou URL; banco intacto. Teste nas Tasks 1 e 2.
2. **Aba aberta de quem foi bloqueado continua enviando:** a primeira mensagem depois do bloqueio recebe 404 `CONEXAO_INEXISTENTE`, e o evento que essa aba recebeu é idêntico ao de um desfazer. Teste na Task 3.
3. **Reinício no meio da conversa:** nenhuma mensagem perdida nem duplicada; o contador de não lidas volta certo. Teste na Task 2 e no `ConversaSobreviveReinicioIT`.
4. **Foto nítida em cache do navegador depois de desfazer:** a resposta veio com `no-store`, e a URL dá 404 no pedido seguinte. Teste na Task 1.
5. **Mensagem reenviada depois de timeout** (mesma `Idempotency-Key`): uma mensagem só e um push no máximo. Teste na Task 2.

---

### Task 1: Lista de conexões e perfil completo com foto nítida

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/conexao/{ConexoesController,ConexaoResumo,PerfilDaConexao,ContextoDeDenunciaConexao}.java`
- Modificar: `perfil/AcessoAFotoPadrao.java` (conexão ativa ⇒ `NITIDA`), o controller de fotos da Etapa 3 (`no-store` para quem não é o titular)
- Teste: `conexao/ConexoesApiTest.java`, `perfil/AcessoAFotoPadraoTest.java` (casos novos), fixture `conexao/ConexoesDeTeste.java`

**Interfaces:**
- Produz:
  - `ConexoesDeTeste.conectar(ContaDeTeste a, ContaDeTeste b) -> UUID` (pela porta, com veto `CONECTADOS`).
  - `GET /api/conexoes` ⇒ lista de `record ConexaoResumo(UUID id, String apelido, @Nullable String fotoUrl, int naoLidas, @Nullable Instant ultimaMensagemEm, @Nullable String ultimaMensagem)`; `ultimaMensagem` cortada em 80 caracteres; ordem pela última mensagem (`seq` decrescente, sem mensagem por último) e depois `criada_em` decrescente. Uma consulta só (`lateral` para a última mensagem e contagem agregada por conexão; até a Task 2, `naoLidas` 0 e nulos).
  - `GET /api/conexoes/{id}/perfil` ⇒ `record PerfilDaConexao(String apelido, int idade, Set<Intencao> intencao, String bio, @Nullable String fotoUrl)`. Nada de célula, distância ou `ContaId`.
  - `AcessoAFotoPadrao`: conexão ativa ⇒ `NITIDA` (antes da regra de encontro; bloqueio continua vencendo).
  - `ContextoDeDenunciaConexao implements ResolvedorDeContexto` para `CONEXAO` (a outra pessoa da conexão ativa, ou desfeita há até 30 dias).
  - `CONEXAO_INEXISTENTE` (404) para conexão alheia, desfeita ou inexistente.

- [ ] **Passo 1:** testes.
  - `ConexoesApiTest`: `listaTrazSoAsMinhas`; `chavesDoResumoExatas`; `semConexoesListaVazia`; `perfilDaConexaoTemFotoNitida`; `perfilDeConexaoAlheiaRecebe404SemDados` (Foco 1); `perfilDeConexaoDesfeitaRecebe404`; `listaSemNMaisUm` (20 conexões, um comando SQL só, contado por um proxy de `DataSource` no teste).
  - `AcessoAFotoPadraoTest`: `conexaoAtivaVeANitida`; `estranhoNaoVe`; `depoisDeDesfazerNaoVeNoPedidoSeguinte` (Foco 4); `nitidaParaTerceiroVemComNoStore`; `titularContinuaComMaxAge300`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): lista de conexões e perfil completo com foto nítida`.

### Task 2: Chat 1:1 persistente com não lidas

**Arquivos:**
- Criar: `src/main/resources/db/migration/V100__mensagem_conexao.sql`, `V101__leitura_conexao.sql`
- Criar: `conexao/{ChatDaConexao,MensagemDaConexaoVisao,ChatDaConexaoController,ContextoDeDenunciaMensagemConexao,ChatDaConexaoAoExcluirConta}.java`
- Modificar: `conexao/ConexoesController.java` (`naoLidas` e última mensagem reais)
- Teste: `conexao/ChatDaConexaoApiTest.java`

**Interfaces:**
- Produz:
  - `V100`: `mensagem_conexao(id uuid primary key, seq bigint generated always as identity unique, conexao_id uuid not null references conexao(id) on delete cascade, autor uuid not null references conta(id), texto text not null check (char_length(texto) between 1 and 1000), criada_em timestamptz not null)`; índice `(conexao_id, seq)`.
  - `V101`: `leitura_conexao(conexao_id uuid not null references conexao(id) on delete cascade, conta_id uuid not null references conta(id), ultima_lida_seq bigint not null, atualizada_em timestamptz not null, primary key (conexao_id, conta_id))`.
  - `record MensagemDaConexaoVisao(UUID id, long seq, boolean minha, List<SegmentoVisao> segmentos, Instant criadaEm)`.
  - Rotas (conexão ativa da conta, senão 404 `CONEXAO_INEXISTENTE`):
    - `GET /api/conexoes/{id}/mensagens?antes=<seq>&limite=<1..50>` (padrão 30, decrescente) ou `?depois=<seq>` (até 100, crescente, P4); os dois juntos ⇒ 400 `CORPO_INVALIDO`.
    - `POST /api/conexoes/{id}/mensagens` `{ "texto" }` com `Idempotency-Key` ⇒ 201 com a visão.
    - `POST /api/conexoes/{id}/lidas` `{ "ate": <seq> }` ⇒ 204; só avança (`... set ultima_lida_seq = greatest(ultima_lida_seq, :ate)`); `seq` que não é desta conexão ⇒ 422 `MENSAGEM_INVALIDA`.
  - Eventos duráveis: `chat.conexao.mensagem` `{ "conexaoId", "mensagem" }` para os dois (com `minha` de cada um); `chat.conexao.lida` `{ "conexaoId", "naoLidas": 0 }` só para a própria conta (P1).
  - `ContextoDeDenunciaMensagemConexao` para `MENSAGEM_CONEXAO` (denunciado = autor; a própria ⇒ 404).
  - `ChatDaConexaoAoExcluirConta implements AoExcluirConta`: apaga as mensagens e leituras da conta (P2).

- [ ] **Passo 1:** `ChatDaConexaoApiTest`: `mensagemChegaAoOutroPeloSse`; `naoLidasContaSoAsDoOutro`; `marcarLidaZeraEAvisaAsOutrasAbas` (duas streams de B recebem `chat.conexao.lida`; A nada); `lidaNaoVoltaParaTras`; `antesPaginaPraTrasEDepoisPraFrente` (50 mensagens: `antes` do `seq` 31 traz do 30 ao 1, limite 30; `depois` do 40 traz 10); `conversaSobreviveAoReinicio` (Foco 3: reiniciar o contexto sobre o mesmo banco; mesmas mensagens e `naoLidas`); `conexaoAlheiaRecebe404SemDados` (Foco 1) em `GET`, `POST` e `lidas`; `textoCom1001Recebe422`; `textoComBidiRecebe422`; `decimaPrimeiraEm10sRecebe429`; `mesmaChaveUmaMensagemSo` (Foco 5); `denunciarMensagemDoOutroRegistra`; `denunciarAPropriaRecebe404`; `chavesDaMensagemExatas` (sem `autor` nem `ContaId`); `excluirContaApagaAsMensagensDela`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): chat 1:1 persistente com não lidas pela sequência do banco`.

### Task 3: Desfazer conexão e bloquear com efeito imediato

**Arquivos:**
- Criar: `conexao/{ConexaoDesfeita,AvisaConexaoDesfeita,EncerraVozDaConexao,DesfazConexaoAoBloquear,RetencaoDeConversaDesfeita}.java`
- Modificar: `conexao/{Conexoes,ConexoesController}.java` (`DELETE`; `desfazer` publica `ConexaoDesfeita`), `sala/EncontrosPrivadosController.java` (P3), `moderacao/ModeracaoController.java` (contexto `CONEXAO` no bloqueio, se a Etapa 5 não aceitou)
- Teste: `conexao/{DesfazerEBloquearApiTest,RetencaoDeConversaDesfeitaTest}.java`, `sala/EncontroPrivadoApiTest.java` (casos novos)

**Interfaces:**
- Produz:
  - `public record ConexaoDesfeita(UUID conexaoId, ParDeContas par, Instant em)`, publicado por `Conexoes.desfazer` na transação. Ouvintes: `AvisaConexaoDesfeita` (na transação: evento durável `conexao.desfeita` `{ "conexaoId" }` para os dois) e `EncerraVozDaConexao` (depois do commit: `Chamadas.encerrarDoContexto(new ContextoDeVoz(CONEXAO, id))`).
  - `DELETE /api/conexoes/{id}` ⇒ 204 (idempotente para quem já desfez; conexão alheia ⇒ 404).
  - `DesfazConexaoAoBloquear` (ouvinte de `BloqueioRegistrado`, na transação do bloqueio): desfaz a conexão ativa do par, se houver.
  - P3: `EncontrosPrivadosController` aceita o veto `CONECTADOS` só com `Conexoes.ativaEntre` presente; senão, 404 `CONVITE_INVALIDO`.
  - `RetencaoDeConversaDesfeita implements PoliticaDeRetencao`: `conversa-desfeita`, `P30D`, referência `conexao.desfeita_em`; apaga mensagens e leituras em lotes de até 1000.

- [ ] **Passo 1:** testes.
  - `DesfazerEBloquearApiTest`: `desfazerTiraDaListaDosDois`; `eventoDeDesfazerEDeBloqueioSaoIdenticos` (Foco 2: o `data` recebido por B nos dois cenários é igual byte a byte); `bloqueadoEnviandoRecebe404NaHora` (Foco 2); `fotoNitidaSomeNaHora` (Foco 4); `chamadaAtivaEEncerrada`; `desfazerDuasVezesE204`; `desfazerConexaoAlheiaRecebe404`; `bloqueioNaTransacaoQueFalhaNaoDesfaz` (rollback ⇒ conexão intacta e nenhum evento).
  - `EncontroPrivadoApiTest`: `conviteEntreConexaoDesfeitaRecebe404`; `conviteEntreConexaoAtivaContinuaValendo`.
  - `RetencaoDeConversaDesfeitaTest`: 30 dias + 1 s sai; 29 dias fica; conversa ativa fica.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): desfazer e bloquear com efeito imediato nos dois lados`.

### Task 4: Voz entre conexões

**Arquivos:**
- Criar: `conexao/VozEntreConexoes.java`, `frontend/src/voz/ChamadaRecebida.tsx` (montada na raiz do app)
- Teste: `conexao/VozEntreConexoesApiTest.java`, `frontend/src/voz/ChamadaRecebida.test.tsx`

**Interfaces:**
- Produz:
  - `VozEntreConexoes implements AutorizacaoDeVoz`: contexto `CONEXAO` com `id` = conexão ativa entre as duas contas ⇒ a outra conta; senão vazio.
  - `ChamadaRecebida`: em qualquer tela, com `voz.chamada` em `TOCANDO` e `voceIniciou` falso, mostra `role="alertdialog"` com "{apelido} está chamando." (apelido pela lista de conexões em cache do TanStack Query), "Aceitar" e "Recusar"; some em `PERDIDA`, `RECUSADA` ou `ENCERRADA`; `PERDIDA` vira "Chamada perdida." na conversa.

- [ ] **Passo 1:** testes: `ligarParaConexaoTocaNoOutro`; `ligarParaQuemNaoEConexaoRecebe404`; `toqueSemRespostaViraPerdidaEm45s`; `recusarAvisaQuemLigou`; `desfazerNoMeioEncerraAChamada`; Vitest `chamadaRecebidaMostraApelidoEBotoes`, `somemQuandoPerdida`, `aceitarChamaAApi`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(voz): chamadas entre conexões com toque`.

### Task 5: Push opcional de mensagem e chamada pela outbox

**Arquivos:**
- Criar: `notificacao/{AvisoDeConversa,ManipuladorDeAvisoDeConversa}.java`
- Modificar: `conexao/ChatDaConexao.java` e a abertura de chamada `COM_TOQUE` (registram na outbox na mesma transação), o service worker (clique abre a `url`)
- Teste: `notificacao/AvisoDeConversaTest.java`

**Interfaces:**
- Produz:
  - `AvisoDeConversa.mensagemNova(ContaId destino, UUID conexaoId)` e `AvisoDeConversa.chamada(ContaId destino, UUID conexaoId)`: só registram `outbox.registrar("aviso-de-conversa", {destino, conexaoId, tipo})` na transação de quem chama.
  - `ManipuladorDeAvisoDeConversa` (tipo `aviso-de-conversa`): no despacho, envia só se o destino tem inscrição e **não** está conectado ao SSE; mensagem com no máximo 1 push por (destino, conexão) a cada 10 min (mapa limitado a 10 000 chaves, com expiração); `url` `/conexoes/{conexaoId}`; textos fixos. Idempotente: reprocessar a mesma linha da outbox não manda outro push dentro da janela.

- [ ] **Passo 1:** `AvisoDeConversaTest` (`EnvioDePush` falso): `conectadoNaoRecebePush`; `desconectadoComInscricaoRecebe`; `segundaMensagemEm10MinNaoRepete` (9:59 não, 10:00 sim); `corpoNaoTemApelidoNemTexto`; `chamadaSempreAvisaQuemEstaDesconectado`; `mensagemQueReverteuNaoGeraPush`; `reprocessarALinhaNaoDuplica`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(notificacao): push opcional de mensagem nova e chamada pela outbox`.

### Task 6: Telas de conexões, conversa e perfil

**Arquivos:**
- Criar: `frontend/src/conexao/{ConexoesTela,ConversaTela,PerfilDaConexaoTela,useConversa,api}.tsx|ts`
- Modificar: o roteador (`/conexoes`, `/conexoes/:id`, `/conexoes/:id/perfil`), a `DecisaoTela` da Etapa 5 (link "Ver conexões"), a navegação principal (item "Conexões" com o total de não lidas)
- Teste: `frontend/src/conexao/{ConexoesTela,ConversaTela,useConversa}.test.tsx`

**Interfaces:**
- Produz:
  - `useConversa(conexaoId)`: páginas por `antes=<seq>` no TanStack Query; `useEvento('chat.conexao.mensagem')` com deduplicação por id; em `aoReconectar`, `?depois=<maior seq>` (P4); `POST /lidas` com o maior `seq` visível (IntersectionObserver); cada envio com `Idempotency-Key` (`crypto.randomUUID()`) reaproveitada nas novas tentativas.
  - `ConexoesTela`: foto, apelido, trecho e não lidas (texto acessível "{n} mensagens não lidas"); estado vazio fixo.
  - `ConversaTela`: mensagens com links seguros, campo com `maxLength` 1000 e contador, "Ligar" (abre o `PainelDeVoz` com toque) e, no topo, "Ver perfil", "Desfazer conexão", "Denunciar" e "Bloquear" sempre visíveis. "Desfazer conexão" confirma no painel: "Desfazer a conexão com {apelido}? A conversa some para vocês dois."
  - Com `conexao.desfeita`, qualquer tela da conexão troca para "Esta conexão não está mais disponível." com "Voltar às conexões".

- [ ] **Passo 1:** testes: `listaVaziaMostraTextoFixo`; `naoLidasComTextoAcessivel`; `mensagemRepetidaPeloSseApareceUmaVez`; `reconexaoBuscaOQueFaltou`; `reenvioUsaAMesmaChave`; `desfeitaTrocaATela`; `acoesSempreVisiveis`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(web): telas de conexões, conversa e perfil completo`.

### Task 7: E2E e provas

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/{ConversaSobreviveReinicioIT,BloqueioCortaNaHoraIT,VozEntreConexoesIT,ConexoesPagina,ServidorReiniciavel}.java`
- Modificar: `e2e/AxeIT.java`, `.claude/handoff/_mapa.md` (rotas e telas de `conexao/`)

**Interfaces:**
- Produz:
  - `ServidorReiniciavel`: sobe a aplicação com `SpringApplicationBuilder` numa porta livre fixa e no mesmo contêiner `postgres:18` (`start()`, `stop()`, `url()`); os contextos do navegador mantêm o cookie de sessão entre reinícios.
  - `ConexoesPagina`: `abrirConversa(String apelido)`, `enviar(String)`, `mensagens()`, `naoLidas(String apelido)`, `bloquear()`, `desfazer()`, `ligar()`, `aceitarChamada()`.

- [ ] **Passo 1:** ITs.
  - `ConversaSobreviveReinicioIT.conversaContinuaDepoisDoReinicio` (prova, Foco 3): Ana e Bia conectadas trocam 3 mensagens; `stop()` e `start()`; sem recarregar, Bia manda a 4ª; Ana a vê em até 10 s; as duas listas têm exatamente as 4, na ordem, sem duplicata; as não lidas de Ana batem.
  - `BloqueioCortaNaHoraIT.bloqueioCortaOAcessoNaHora` (prova, Foco 2): Bia na conversa; Ana bloqueia; em até 3 s Bia vê "Esta conexão não está mais disponível."; um `POST` de Bia pelo `APIRequestContext` do mesmo contexto recebe 404; a lista de Bia fica vazia.
  - `BloqueioCortaNaHoraIT.desfazerMostraOMesmoTextoAoOutro`.
  - `VozEntreConexoesIT.ligarAceitarEFalar`: Ana liga; Bia vê "Ana está chamando." e aceita; microfones começam desligados; Ana liga o dela e a energia recebida por Bia cresce; Bia encerra e os painéis somem.
  - `ProximoPassoIT.convidarParaSegundoEncontro` (paridade): Ana convida pelo cartão; Bia vê `Ana convidou você para um encontro no ...` e entra; as duas chegam ao cenário sugerido.
  - `AxeIT`: `conexoesSemViolacoes`, `conversaSemViolacoes`, `perfilDaConexaoSemViolacoes`, `chamadaRecebidaSemViolacoes`. E (paridade) `conversasOcultasSemViolacoes`.
- [ ] **Passo 2:** rodar em chromium, firefox e webkit (no webkit sem captura, o `VozEntreConexoesIT` afirma o aviso de microfone, como na Etapa 7). Verde, sem skip.
- [ ] **Passo 3:** `_mapa.md`. Commit `test(e2e): conversa sobrevive a reinício e bloqueio corta na hora`.

### Task 8: Próximo passo depois do sim e lembrete gentil contra o sumiço (paridade)

**Arquivos:**
- Criar: `src/main/resources/db/migration/V102__preferencia_e_convite_na_conversa.sql`
- Criar: `conexao/dominio/{LembreteDeConversa,Lembrete}.java`, `conexao/{ProximoPasso,ConviteNaConversa,LembretesDeConversa,ManipuladorDeLembrete}.java`
- Modificar: `conexao/{ChatDaConexao,MensagemDaConexaoVisao,ConexoesController,ConexaoResumo}.java`; `encontro/Encontros.java` (`entrarPrivadoPorId`); `frontend/src/conexao/{ConversaTela,ConexoesTela}.tsx`
- Criar: `frontend/src/conexao/{ProximoPassoCartao,CartaoDeConvite,LembreteGentil}.tsx` + testes
- Teste: `conexao/dominio/LembreteDeConversaTest.java`, `conexao/{ProximoPassoApiTest,LembretesDeConversaTest}.java`

**Esquema (V102):**
- `preferencia_conversa(conexao_id uuid not null references conexao(id) on delete cascade, conta_id uuid not null references conta(id) on delete cascade, oculta_em timestamptz, avisos boolean not null default true, lembrete_em timestamptz, primary key (conexao_id, conta_id))` (a Task 9 usa `oculta_em` e `avisos`).
- `alter table mensagem_conexao add column tipo text not null default 'TEXTO' check (tipo in ('TEXTO','CONVITE'))`, `add column encontro_id uuid references encontro(id) on delete set null`, `alter column texto drop not null`, `add check ((tipo = 'TEXTO') = (texto is not null))`.

**Interfaces:**
- Consome: `sala.HistoricoDeCenarios` e `CatalogoDeCenarios.sorteaveis()` (Etapa 4), `Encontros.abrirPrivado` (Etapa 5), `AvisoDeConversa`/outbox (Task 5), `Perfis.pausada` (Etapa 3).
- Produz:
  - `enum Lembrete { PRIMEIRA_MENSAGEM, SUA_VEZ }`; `LembreteDeConversa.pendencia(Instant conexaoCriadaEm, Optional<UltimaMensagem> ultima, ContaId eu, Instant agora) -> Optional<Lembrete>` (pura): sem mensagens e conexão com 24 h ou mais → `PRIMEIRA_MENSAGEM`; última do outro há 48 h ou mais → `SUA_VEZ`; resto → vazio. `record UltimaMensagem(ContaId autor, Instant em)`.
  - `GET /api/conexoes/{id}/proximo-passo` → `{ "cenarioSugerido": "<id>" | null, "lembrete": "PRIMEIRA_MENSAGEM" | "SUA_VEZ" | null }`; `cenarioSugerido` = o sorteável que nenhum dos dois jogou (empate pela ordem de `sorteaveis()`), ou `null`.
  - `POST /api/conexoes/{id}/convites` `{ "cenario" }` → 201: abre um encontro privado do criador e grava, na mesma transação, uma mensagem `CONVITE` com o `encontroId` (evento `chat.conexao.mensagem` de sempre). `POST /api/conexoes/{id}/convites/{encontroId}/aceitar` → 200 com a `Visao`: só o outro da conexão ativa, só com o encontro aberto e sem convidado; usa `Encontros.entrarPrivadoPorId(String encontroId, ContaId convidado)` (novo, na Etapa 5: mesmo efeito de `entrarPrivado`, sem token, exigindo conexão ativa). Encontro vencido ou usado → 404 `CONVITE_INVALIDO`.
  - `ConexaoResumo` ganha `lembrete` (`PRIMEIRA_MENSAGEM` | `SUA_VEZ` | `null`).
  - `LembretesDeConversa` (`@Scheduled(cron = "0 0 19 * * *", zone = "America/Sao_Paulo")`): para cada conta com inscrição de push, não pausada, com alguma conversa pendente não oculta, com `avisos` ligados e cujo `lembrete_em` é anterior ao início da pendência, registra **um** push por conta por dia pela outbox (`tipo` `lembrete-de-conversa`, corpo fixo `Uma conversa está esperando você no Duora.`, `url` `/conexoes`) e grava `lembrete_em` nas conversas cobertas. Uma pendência gera no máximo um lembrete.
  - Front:
    - `ProximoPassoCartao` no topo da conversa enquanto não houver convite nem chamada: `Que tal um segundo encontro no {cenario}?` com `Convidar` e `Agora não` (esconde por 7 dias, só neste aparelho);
    - `CartaoDeConvite` para a mensagem `CONVITE`: para quem recebeu, `{apelido} convidou você para um encontro no {cenario}.` com `Entrar`; para quem enviou, `Convite enviado.`;
    - `LembreteGentil`: `SUA_VEZ` → `{apelido} respondeu há {n} dias. Quer continuar? Se não quiser, tudo bem: você pode desfazer a conexão.`; `PRIMEIRA_MENSAGEM` → `Vocês se conectaram. Que tal dizer oi?`; na `ConexoesTela`, os selos `Sua vez` e `Diga oi`.
- [ ] **Passo 1:** testes.
  - `LembreteDeConversaTest`: 23 h 59 sem mensagens → vazio; 24 h → `PRIMEIRA_MENSAGEM`; última minha há 5 dias → vazio; última do outro há 47 h 59 → vazio; 48 h → `SUA_VEZ`.
  - `ProximoPassoApiTest`: `sugereCenarioQueNenhumJogou`; `semCenarioNovoSugereNull`; `convidarCriaEncontroEMensagemNaMesmaTransacao`; `outroAceitaEOsDoisEntram`; `quemConvidouNaoAceitaOProprio` (404); `conviteDeConexaoDesfeitaRecebe404`; `terceiroRecebe404`.
  - `LembretesDeConversaTest` (`EnvioDePush` falso): `umPushPorContaPorDia` (3 conversas pendentes → 1 push); `mesmaPendenciaNaoRelembra`; `conversaOcultaOuSemAvisosNaoConta`; `contaPausadaNaoRecebe`; `corpoNaoTemApelido`.
  - Vitest: `cartaoDeConviteEntra`; `agoraNaoEscondeOProximoPasso`; `lembreteOfereceDesfazerSemCulpa` (texto exato).
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): próximo encontro sugerido e lembrete gentil contra o sumiço`.

### Task 9: Ocultar conversa e avisos por conversa (paridade)

**Arquivos:**
- Criar: `conexao/{PreferenciasDeConversa,PreferenciasDeConversaController}.java`
- Modificar: `conexao/ConexoesController.java` (`?ocultas=true`; total de não lidas sem as ocultas), `notificacao/ManipuladorDeAvisoDeConversa.java` (respeita `avisos`), `frontend/src/conexao/{ConexoesTela,ConversaTela}.tsx`, a navegação principal (total sem as ocultas)
- Teste: `conexao/PreferenciasDeConversaApiTest.java`, `notificacao/AvisoDeConversaTest.java` (casos novos), `frontend/src/conexao/{ConexoesTela,ConversaTela}.test.tsx` (casos novos)

**Interfaces:**
- Consome: `preferencia_conversa` (V102, Task 8).
- Produz:
  - `POST /api/conexoes/{id}/ocultar` e `DELETE /api/conexoes/{id}/ocultar` → 204 (idempotentes; conexão alheia → 404). Ocultar é só para quem oculta: o outro não fica sabendo, a conexão e a conversa seguem, e mensagem nova **não** desoculta (aparece em "Ocultas" com o contador).
  - `PUT /api/conexoes/{id}/avisos` `{ "ativos": bool }` → 204. Com `false`, nem push de mensagem, nem de chamada, nem lembrete dessa conversa; a chamada continua tocando dentro do app aberto.
  - `GET /api/conexoes` exclui as ocultas; `GET /api/conexoes?ocultas=true` lista só elas. `ConexaoResumo` ganha `oculta` e `avisos`.
  - Front: no menu da conversa, `Ocultar conversa` / `Mostrar conversa` e o interruptor `Avisos desta conversa`; na `ConexoesTela`, o link `Conversas ocultas ({n})` quando houver.
- [ ] **Passo 1:** testes. `PreferenciasDeConversaApiTest`: `ocultarTiraDaListaSoParaQuemOcultou`; `mensagemNovaNaoDesoculta`; `totalDeNaoLidasIgnoraOcultas`; `outroNaoVeDiferencaNenhuma` (o `GET` do outro é igual antes e depois, byte a byte); `conexaoAlheiaRecebe404`. `AvisoDeConversaTest`: `avisosDesligadosNaoMandaPushDeMensagemNemDeChamada`. Vitest: `ocultasEmSecaoPropria`; `interruptorDeAvisosChamaAApi`.
- [ ] **Passo 2:** RED; implementar; verde. Commit `feat(conexao): ocultar conversa e avisos por conversa`.

## Ordem e paralelismo
- **Leva 1 (paralela):** Task 1 (lista e perfil), Task 4 (voz; só depende de `Conexoes` e da Etapa 7) e Task 5 (push; o manipulador e o teste, com os pontos de registro ligados depois).
- **Leva 2:** Task 2 (depois da 1, mesmo controller); a Task 6 começa junto, contra as Interfaces das Tasks 1–4, com `fetch` e `canal` falsos.
- **Leva 3:** Task 3 (depois da 2), o fim da Task 5 (registrar na outbox no chat e na chamada) e o fim da Task 6.
- **Leva 4 (paridade):** Task 8 (depois da 2, 3 e 5; V102), depois Task 9 (mesmas telas e a tabela da 8).
- **Por último:** Task 7 (com os ITs das Tasks 8 e 9), revisão final (`superpowers:requesting-code-review`), merge, push e CI.

## Decisões do dono
Nenhuma bloqueia a etapa. P1–P4 acima são escolhas de planejamento reversíveis.
