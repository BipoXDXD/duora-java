# Etapa 8 — Moderação e painel admin: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** uma pessoa com papel ADMIN revisa denúncias (vendo só o conteúdo do denunciado: mensagem, imagem ou link) e fotos de perfil num painel React, confirma ou descarta; confirmar bane a conta (sessões derrubadas, fora da fila, das conexões e do encontro); "parece menor de idade" continua suspendendo na hora (Etapa 5) e a revisão reativa ou bane; toda ação de admin fica numa auditoria imutável; a fila tem SLA, alerta e métricas.

**Arquitetura:**
- Esta etapa **só acrescenta** sobre o que a Etapa 5 criou em `moderacao/` (`denuncia`, `bloqueio`, `Denuncias`, `DenunciaRegistrada`, `ResolvedorDeContexto`) e reaproveita `conta.SuspensaoDeContas`.
- Um módulo profundo, `moderacao.Moderacao`, com uma porta de entrada só: `executar(ContaId admin, AcaoDeModeracao acao)`. Transição de estado, sanção e auditoria acontecem numa transação; os controllers de `admin/web/` só traduzem HTTP.
- **Banimento por Observer** (contrato: vários efeitos independentes): `conta.BanimentoDeContas.banir` muda o estado, revoga as sessões e bloqueia o e-mail; `moderacao` publica `ContaBanida` **na transação**, e cada módulo dono reage no próprio ouvinte, também na transação: `encontro` encerra o encontro e tira da fila (como já faz na suspensão), `conexao` desfaz as conexões (o `ConexaoDesfeita` da Etapa 6 avisa o outro), `notificacao` apaga as inscrições de push, e `moderacao` grava o evento durável `moderacao.conta` para a pessoa banida. Depois do commit, só o fechamento dos streams SSE (memória).
- **Evidência copiada na hora da denúncia:** o chat e as imagens do encontro expiram em 30 dias (Etapa 5 P1, "a Etapa 8 copia o que precisar"). Um ouvinte de `DenunciaRegistrada` captura, na mesma transação, o conteúdo do denunciado pela porta `moderacao.ConteudoDenunciado` (implementada por `encontro` e `conexao`) e grava em `evidencia_denuncia`; a cópia dos arquivos de imagem sai pela `Outbox`. A moderação nunca lê tabelas de outro módulo.
- Papel ADMIN por configuração (`duora.admin.emails`), sem tabela nem tela de concessão.
- Camadas do contrato: raiz de `moderacao/` e `admin/` = API pública e casos de uso; `dominio/` Java puro; `web/` e `infra/` adapters.
- Painel em `frontend/src/admin/` com rotas lazy em `/admin/**`.

**Stack:** Java 25, Spring Boot 4.2.0, `JdbcClient`, PostgreSQL 18 (trigger para a auditoria append-only), Flyway, Testcontainers, Micrometer (gauges), React 19 + TypeScript + Tailwind 4, React Router (lazy), TanStack Query, Vitest, Playwright Java.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 8; "Escolhas minhas": 18+ com "parece menor" suspendendo na hora, "Sem imagens sensíveis", "Imagens", "Links", "Sair, denunciar e bloquear"). **Contratos (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Nomes alinhados com os planos das Etapas 2–7 e 9 (`docs/superpowers/plans/2026-11-22-etapa-*.md`); em divergência, vale o que estiver na `main`, sem mudar comportamento.

**Pré-requisitos:** Etapa 5 Task 7 (V85/V86, `SuspensaoDeContas`) e Etapa 3 Task 5 (`FilaDeFotos`). As partes de conexão (T3, T4) precisam da Etapa 6 Task 3 (`ConexaoDesfeita`); sem ela, ficam como última sub-task delas.

## Restrições globais (Global Constraints)
- Tudo do contrato e das restrições globais das Etapas 2, 4 e 5 vale aqui.
- **Flyway:** só **V120–V139**. Usadas: V120 (resolução e evidência da denúncia), V121 (auditoria), V122 (banimento), V123 (alerta da fila). V124–V139 reservadas.
- **Só acrescentar** sobre `denuncia` e `bloqueio`: colunas novas e `check` ampliado; nada removido nem renomeado.
- **Papel ADMIN:** `duora.admin.emails` (lista separada por vírgula, normalizada como `Email.chaveDeBloqueio()`), vazia por padrão; em `prod`, `${DUORA_ADMIN_EMAILS}`. Toda rota `/api/admin/**` exige sessão válida, conta ATIVA, e-mail na lista e sessão aberta há no máximo **12 h** (`duora.admin.sessao-maxima=PT12H`). Sem sessão ⇒ 401 `NAO_AUTENTICADO`; sem papel ⇒ 403 `ACESSO_NEGADO`; sessão velha ⇒ 403 `REAUTENTICACAO_NECESSARIA`.
- **Conteúdo limitado ao denunciado:** o admin vê só o que o denunciado escreveu ou enviou. Nunca a mensagem nem o perfil de quem denunciou (só o apelido), nunca e-mail, data de nascimento ou célula. Links aparecem como texto com o host real em destaque, **nunca clicáveis**. Imagens chegam borradas, com "Ver imagem".
- **SLA (proposta, ver D2):** `URGENTE` (`PARECE_MENOR`, `IMAGEM_SENSIVEL`) **12 h**; `NORMAL` (`ASSEDIO`, `SPAM_OU_GOLPE`, `OUTRO`) **48 h**; foto de perfil **24 h**. Propriedades `duora.moderacao.sla.{urgente,normal,foto}`.
- **Confirmar bane** (P1); banimento definitivo no piloto (sem desbanir pela API).
- **Auditoria:** uma linha por ação, gravada na transação da ação; `UPDATE` e `DELETE` recusados por trigger. Nota até 500 code points, sem controle exceto `\n`.
- **Retenção da evidência:** pendente nunca expira; resolvida expira **30 dias** depois de `resolvida_em` (`PoliticaDeRetencao` `evidencias-de-denuncia`, com `tamanho()`); imagem de denúncia `IMAGEM_SENSIVEL` confirmada é apagada na hora (original e cópia).
- **Códigos novos** (`moderacao/CodigoModeracao`, `admin/CodigoAdmin`, como `CodigoDeErro` do `nucleo/`): `ACESSO_NEGADO` 403, `REAUTENTICACAO_NECESSARIA` 403, `DENUNCIA_INEXISTENTE` 404, `DENUNCIA_JA_RESOLVIDA` 409, `NOTA_INVALIDA` 422 (e `FOTO_INEXISTENTE` da Etapa 3).
- **Textos (pt-BR, gênero neutro, exatos):**
  - suspensa: "Sua conta está suspensa enquanto analisamos uma denúncia. Se tiver dúvidas, escreva para {contato}.";
  - banida: "Sua conta foi encerrada por violar os termos de uso. Se achar que foi um engano, escreva para {contato}.";
  - quem estava com a pessoa banida vê o mesmo texto de uma saída ou de um desfazer ("O encontro terminou." / "Esta conexão não está mais disponível.").
- **Logs:** nunca texto denunciado, nota de admin, e-mail ou caminho de imagem; só ids, tipo da ação e contagens.

## Glossário do domínio (proposto para `GLOSSARY.md`, ver "Adições ao contrato")
- **Denúncia:** pedido de uma conta para a moderação olhar o comportamento de outra num contexto (encontro, conexão, mensagem ou imagem). Situação: **pendente**, **confirmada**, **descartada** ou **encerrada** (fechada sem análise porque a conta já foi banida por outra denúncia).
- **Evidência:** cópia, feita no momento da denúncia, do conteúdo denunciado. É o que o admin analisa; sobrevive à expiração do chat.
- **Conteúdo denunciado:** o que o denunciado escreveu ou enviou no contexto, e só isso.
- **Prioridade:** urgente ou normal, derivada do motivo; ninguém a escolhe.
- **Prazo (SLA):** instante até o qual uma denúncia pendente deve ser analisada; depois dele, ela está **vencida**.
- **Suspensão:** bloqueio automático e temporário da conta pela denúncia "parece menor de idade". Termina em **reativação** (descartada) ou **banimento** (confirmada).
- **Banimento:** encerramento definitivo da conta por decisão de admin. Diferente de **exclusão** (pedida pela pessoa) e de **bloqueio** (decisão de uma pessoa sobre outra, sem moderação).
- **Ação de moderação:** decisão de admin sobre denúncia ou foto; toda ação gera um **registro de auditoria**.

## Desenho do módulo (duas alternativas, `mattpocock-skills:codebase-design`)
- **A. Serviços por ação** (`ServicoDeDenuncias.confirmar`, `ServicoDeFotos.recusar`, `Banimentos.banir`, `Auditoria.registrar`), cada controller orquestrando e lembrando de auditar. Interface larga (4 módulos, ~8 métodos combinados na ordem certa); um controller novo que esquece a auditoria passa nos testes dos serviços. Rasa.
- **B. Fachada profunda** `Moderacao.executar(ContaId admin, AcaoDeModeracao acao) -> ResultadoDaAcao`, com `AcaoDeModeracao` selada (`switch` exaustivo). Não existe ação sem auditoria nem auditoria sem ação. `Banimentos` e `Auditoria` são seams internos; `ConteudoDenunciado` e os ouvintes de `ContaBanida` são seams reais (uma implementação por módulo dono).
- **Escolha: B.** Pelo teste de deleção, apagar `Moderacao` espalharia "audita e sanciona na mesma transação" por todos os controllers. As leituras (fila, detalhe, resumo) ficam em `FilaDeDenuncias` e `ResumoDaFila`, separadas porque não mudam estado.

## STRIDE (ameaça → mitigação → teste)
| Ameaça | Mitigação | Teste |
|---|---|---|
| S: sessão de admin roubada usada por dias | sessão de admin vale 12 h desde a abertura | `sessaoDeAdminComMaisDe12hPedeReautenticacao` (T1) |
| T: apagar ou editar o rastro de uma ação | trigger recusa `UPDATE`/`DELETE` em `acao_admin` | `auditoriaRecusaUpdateEDelete` (T2) |
| R: admin nega ter banido | auditoria na transação da ação | `acaoQueFalhaNaoDeixaAuditoriaEAuditoriaQueFalhaDesfazAcao` (T2) |
| I: admin vê mais do que o denunciado fez | captura filtra por autor = denunciado; DTO com chaves fixas | `evidenciaNuncaTrazConteudoDeQuemDenunciou` (T4) |
| I: link de golpe clicado pelo admin | link como texto com host real, sem `href` | `linkNoPainelNaoEClicavel` (T7) |
| D: rajada de denúncias contra alguém | limite `denuncia` (Etapa 5); `PARECE_MENOR` repetida não muda nada | `segundaPareceMenorNaoMudaNada` (T5) |
| E: conta comum chamando `/api/admin/**`, ou admin decidindo sobre si | `ExigeAdmin`; recusa de decisão sobre a própria conta | `todaRotaAdminRecusaQuemNaoEAdmin` (T1), `adminNaoDecideDenunciaContraSiMesmo` (T5) |

## Decisões de planejamento (o dono pode reverter)
- **P1. Confirmar sempre bane** (sem advertência no piloto): advertência exigiria estado, texto e regra de reincidência novos; a regra da spec para imagem sensível já é de um golpe só.
- **P2. Denúncia de encontro ou conexão inteiros** (sem item) captura as **últimas 20 mensagens do denunciado** naquele contexto, nunca as de quem denunciou. Denúncia de item captura só o item.
- **P3. Banir encerra as outras denúncias pendentes** contra a mesma conta como `ENCERRADA` (não contam como confirmadas nas métricas).
- **P4. Imagem confirmada como sensível é apagada na hora** (cópia da evidência e original do encontro). Descartada, continua escondida só para quem denunciou.
- **P5. Descartar "parece menor" reativa** só se a conta estiver SUSPENSA e não houver outra `PARECE_MENOR` pendente contra ela.
- **P6. Banido não se cadastra de novo com o mesmo e-mail:** HMAC em `email_bloqueado` com motivo `BANIMENTO` (mesma regra do menor, Etapa 2 T7).
- **P7. "Denunciar link"** é a denúncia da mensagem que contém o link, aberta no aviso "Você vai sair do Duora" com "Spam ou golpe" pré-selecionado.

## Decisões do dono
| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| D1 | Quem modera no piloto (denúncia contra pessoa real precisa de decisão humana; agente não bane) | (a) só o dono, pelo e-mail dele em `DUORA_ADMIN_EMAILS`; (b) o dono e mais uma pessoa de confiança | (a) para abrir; (b) se a fila passar de ~10 denúncias por semana. Trocar é editar uma variável |
| D2 | SLA de análise | (a) urgente 12 h, normal 48 h, foto 24 h; (b) urgente 24 h, normal 72 h, foto 48 h; (c) urgente 4 h | (a): a suspensão automática já protege no caso "menor", e 12 h cabe em olhar o painel de manhã e à noite; (c) não se sustenta com uma pessoa |
| D3 | Canal do alerta da fila | (a) e-mail aos admins pelo Resend (via `Outbox`); (b) Web Push no PWA do admin; (c) os dois | (a): já existe e não depende de o admin ter instalado o PWA |

Nenhuma bloqueia: tudo vem de propriedades, com (a) como padrão.

## Foco de revisão (Review Focus)
1. **Ouvinte de um módulo falha no meio do banimento** (ex.: o encontro não encerra): nada fica pela metade; a conta segue como estava e o admin recebe erro. Coberto por `ouvinteQueFalhaDesfazOBanimentoInteiro` (T3).
2. **Dois admins (ou duas abas) decidindo a mesma denúncia:** um vence, o outro recebe 409; uma auditoria e um banimento. Coberto por `decisoesSimultaneasTemUmVencedor` (T5).
3. **Denúncia analisada depois que o chat expirou, ou com o denunciado excluído:** a evidência copiada continua lá; com a conta excluída, a decisão ainda funciona. Coberto por `evidenciaSobreviveAExpiracaoDoChat` (T4) e `denunciadoExcluidoAindaPodeSerDescartado` (T5).
4. **Fila vazia ou com 500 denúncias:** estado vazio honesto; 50 por página, índice parcial. Coberto por `filaPaginaDe50EmOrdemDePrioridade` (T4) e `filaVaziaMostraTextoHonesto` (T7).
5. **Suspensa ou banida com a aba aberta, ou tentando entrar pelo link:** vê o próprio texto, sem laço de redirecionamento, nos três navegadores. Coberto por `suspensaVeOTextoAoEntrar` e `banidaComAbaAbertaVeOTextoNaHora` (T8).

---

### Task 1: Papel ADMIN e rotas `/api/admin`

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/admin/{Administradores,CodigoAdmin}.java`, `admin/web/{ExigeAdmin,AdminWebConfig,AdminController}.java`
- Modificar: `conta/SessaoAtiva.java` (ganha `Instant abertaEm` se não tiver), `application.properties` (`duora.admin.emails=`, `duora.admin.sessao-maxima=PT12H`), `application-prod.properties` (`duora.admin.emails=${DUORA_ADMIN_EMAILS}`)
- Teste: `admin/{AdministradoresTest,RotasAdminTest,AdminApiTest}.java`; `ContasDeTeste` ganha `ContaDeTeste criarAdmin()` (e-mail entra na lista por `@DynamicPropertySource` na base de teste)

**Interfaces:**
- Consome: `Email.de`, `Email.chaveDeBloqueio()`; `Sessoes`, `SessaoAtiva`, `ExigeSessao`, `@ContaAtual`, `ConsultaDeContas` (Etapa 2).
- Produz:
  - `public class Administradores`: `boolean eAdmin(ContaId conta)` (compara a chave normalizada do e-mail da conta com a lista; conta sem e-mail ⇒ falso).
  - `ExigeAdmin` (interceptor em `/api/admin/**`, depois do `ExigeSessao`): sem papel ⇒ 403 `ACESSO_NEGADO` (`Você não tem acesso a esta área.`); `abertaEm + 12 h ≤ agora` ⇒ 403 `REAUTENTICACAO_NECESSARIA` (`Entre de novo para continuar.`).
  - `GET /api/admin/eu` → 200 `{"admin": true}`. Convenção: todo handler de `admin/web/` recebe `@ContaAtual ContaId admin`.
- [ ] **Passo 1:** testes.
  - `AdministradoresTest` (`@ParameterizedTest`): lista `Ana@Exemplo.com, b+x@gmail.com` reconhece `ana@exemplo.com` e `b@gmail.com`; não reconhece `c@exemplo.com`; lista vazia não reconhece ninguém.
  - `RotasAdminTest.todaRotaAdminRecusaQuemNaoEAdmin`: percorre o `RequestMappingHandlerMapping`; para cada `/api/admin/**`: sem cookie ⇒ 401; conta comum ⇒ 403 `ACESSO_NEGADO` com corpo só do problem+json; admin ⇒ nunca 401/403. Falha se achar zero rotas admin.
  - `AdminApiTest`: `adminRecebeEu`; `sessaoDeAdminComMaisDe12hPedeReautenticacao` (11 h 59 min ⇒ 200; 12 h ⇒ 403); `adminSuspensoRecebe403DaSessao` (`CONTA_INDISPONIVEL`, antes do interceptor de admin).
- [ ] **Passo 2:** `./mvnw -q verify -Dtest='AdministradoresTest,RotasAdminTest,AdminApiTest'`; falha.
- [ ] **Passo 3:** implementar. ArchUnit: `admin` pode depender de `moderacao`, `conta`, `perfil`; ninguém depende de `admin`.
- [ ] **Passo 4:** verde. Commit `feat(admin): papel ADMIN por configuração e rotas /api/admin protegidas`.

### Task 2: Auditoria imutável

**Arquivos:**
- Criar: `src/main/resources/db/migration/V121__acao_admin.sql`
- Criar: `moderacao/{Auditoria,RegistroDeAuditoria,PaginaDeAuditoria}.java`, `moderacao/dominio/TipoDeAcao.java`, `moderacao/infra/AuditoriaJdbc.java`, `admin/web/AuditoriaController.java`
- Teste: `moderacao/AuditoriaTest.java`, `admin/AuditoriaApiTest.java`

**Esquema (V121):** `acao_admin(seq bigint generated always as identity primary key, id uuid not null unique, admin uuid not null references conta(id), tipo text not null check (tipo in ('DENUNCIA_CONFIRMADA','DENUNCIA_DESCARTADA','FOTO_APROVADA','FOTO_RECUSADA','FOTO_RECUSADA_E_BANIDA','CONTA_BANIDA','CONTA_REATIVADA','CADASTRO_ALTERADO','CAPACIDADE_ALTERADA')), alvo_conta uuid references conta(id), alvo_ref text, nota text check (char_length(nota) <= 500), criada_em timestamptz not null)`; índice em `alvo_conta`; função `acao_admin_imutavel()` com `raise exception 'acao_admin é somente inclusão'` e trigger `before update or delete for each row`. Sem cascata: conta excluída vira lápide (Etapa 2 T9). Os dois últimos tipos são da Etapa 11.

**Interfaces:**
- Produz:
  - `public enum TipoDeAcao` com os valores do `check`.
  - `Auditoria`: `@Transactional(propagation = MANDATORY) public void registrar(ContaId admin, TipoDeAcao tipo, Optional<ContaId> alvo, Optional<String> referencia, Optional<Nota> nota)` (fora de transação ⇒ `IllegalTransactionStateException`; a Etapa 11 usa); `public PaginaDeAuditoria pagina(Optional<Long> antesDeSeq, int limite)` (keyset por `seq desc`, limite ≤ 50).
  - `record RegistroDeAuditoria(long seq, String adminApelido, TipoDeAcao tipo, Optional<String> alvoApelido, Optional<String> referencia, Optional<String> nota, Instant criadaEm)`; `record PaginaDeAuditoria(List<RegistroDeAuditoria> itens, Optional<Long> proximaAntesDe)`.
  - `GET /api/admin/auditoria?antesDe=<seq>` (apelido por `Perfis.publico`; conta excluída ⇒ `"Conta excluída"`).
  - `Nota` é criada na T5 (`moderacao/dominio/Nota`); até lá, esta task a cria com a regra da T5.
- [ ] **Passo 1:** testes.
  - `AuditoriaTest` (`BancoDeTeste`): `registraNaTransacaoCorrente`; `foraDeTransacaoFalha`; `auditoriaRecusaUpdateEDelete` (as duas lançam com a mensagem do trigger; linha intacta); `acaoQueFalhaNaoDeixaAuditoriaEAuditoriaQueFalhaDesfazAcao` (bean de teste: auditoria + `UPDATE conta` + exceção ⇒ zero linhas e conta intacta); `paginaPorKeysetSemRepetir` (120 ⇒ 50/50/20, ordem decrescente, sem repetição).
  - `AuditoriaApiTest`: chaves do JSON exatamente as do record; conta comum ⇒ 403.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): auditoria imutável das ações de admin`.

### Task 3: Banimento com efeito em todos os módulos

**Arquivos:**
- Criar: `src/main/resources/db/migration/V122__banimento.sql`
- Criar: `conta/{BanimentoDeContas,BanimentoDeContasJdbc}.java`; `moderacao/{Banimentos,ContaBanida,AvisoDeBanimento,ModeracaoAoExcluirConta}.java`, `moderacao/dominio/MotivoBanimento.java`; `conexao/ConexoesAoBanir.java`; `notificacao/PushAoBanir.java`
- Modificar: `encontro/EfeitosDaModeracaoNoEncontro.java` (ouve `ContaBanida`), `encontro/dominio/MotivoEncerramento.java` (+`BANIMENTO`, que para quem ficou é `TERMINOU`), `conta/web/ExigeSessao.java` e a confirmação de entrada (403 de conta própria ganha `situacao`), `web/eventos/{CanalDeEventos,Assinaturas}.java` (`encerrarStreams`), o adapter de `SuspensaoDeContas` (publica `ContaSuspensa` para o mesmo aviso)
- Teste: `moderacao/BanimentosTest.java`, `conta/{BanimentoDeContasTest,ContaIndisponivelApiTest}.java`

**Esquema (V122):** `banimento(conta_id uuid primary key references conta(id), motivo text not null check (motivo in ('DENUNCIA_CONFIRMADA','FOTO_SENSIVEL')), denuncia_id uuid references denuncia(id), admin uuid not null references conta(id), banida_em timestamptz not null)`; índice em `banida_em` (métricas da Etapa 11). E `email_bloqueado`: `check` ampliado para `('MENOR','BANIMENTO')`.

**Interfaces:**
- Consome: `SuspensaoDeContas`, `Sessoes.revogarTodas`, `EmailsBloqueados.bloquear(Email, String)` (Etapa 2/5); `Encontro.encerrarPor(ContaId, MotivoEncerramento)` e a saída da fila já usadas por `EfeitosDaModeracaoNoEncontro` (Etapa 5); `Conexoes.ativasDe`, `Conexoes.desfazer(UUID, ContaId)` + `ConexaoDesfeita` (Etapas 5/6); `InscricoesDePush` (Etapa 4); `CanalDeEventos.publicar(List<Envio>)`, `Evento`, `Envio` (Etapa 4).
- Produz:
  - `public interface BanimentoDeContas { boolean banir(ContaId conta); boolean reativar(ContaId conta); }` em `conta/`, implementada pela mesma classe que implementa `SuspensaoDeContas`. `banir`: `update conta set estado = 'BANIDA' where id = ? and estado in ('ATIVA','SUSPENSA','PENDENTE')` + `Sessoes.revogarTodas` + `EmailsBloqueados.bloquear(email, "BANIMENTO")`; devolve se mudou. `reativar`: só `SUSPENSA → ATIVA`.
  - `public record ContaBanida(ContaId conta)` e (se a Etapa 5 não publicou) `public record ContaSuspensa(ContaId conta)`.
  - `moderacao.Banimentos` (pacote, usado só por `Moderacao`): `void banir(ContaId alvo, MotivoBanimento motivo, Optional<UUID> denuncia, ContaId admin)`: `BanimentoDeContas.banir` (falso ⇒ já banida ou excluída: não grava nem publica nada); insere `banimento`; encerra as outras pendentes contra a conta como `ENCERRADA` (P3); publica `ContaBanida` pelo `ApplicationEventPublisher`.
  - Ouvintes **na transação** (`@EventListener`, falha desfaz tudo): `EfeitosDaModeracaoNoEncontro` (encontro ativo encerrado com `BANIMENTO`; sai da fila); `ConexoesAoBanir` (`Conexoes.desfazer` de cada ativa; o `AvisaConexaoDesfeita` da Etapa 6 avisa o outro com o evento de sempre); `PushAoBanir` (apaga as inscrições); `AvisoDeBanimento` (grava `moderacao.conta` `{ "situacao": "BANIDA" }` para a própria conta pelo `CanalDeEventos.publicar`; o mesmo com `SUSPENSA` para `ContaSuspensa`).
  - Depois do commit (`AvisoDeBanimento`, `@TransactionalEventListener(AFTER_COMMIT)`): `CanalDeEventos.encerrarStreams(ContaId)` (método novo, só memória: completa e remove os emitters da conta). Se o processo cair antes, a sessão já foi revogada: a reconexão recebe 403.
  - 403 de conta própria (`ExigeSessao` e `POST /api/conta/confirmar`): problem+json ganha `"situacao": "SUSPENSA"|"BANIDA"`; o código continua `CONTA_INDISPONIVEL`.
  - `ModeracaoAoExcluirConta implements AoExcluirConta`: zera `detalhe` das denúncias feitas pela conta; as contra ela e a auditoria ficam (só ids).
- [ ] **Passo 1:** testes.
  - `BanimentoDeContasTest`: `banirAtivaMudaERevogaSessoes`; `banirExcluidaNaoMuda`; `reativarSoDeSuspensa`; `banidoNaoRecebeLinkDeEntrada` (`POST /api/conta/entrar` ⇒ o mesmo 202, aviso sem `#t=`).
  - `BanimentosTest` (`BancoDeTeste`, `CanalDeEventos` real): `banirDerrubaSessoes` (cookie antigo ⇒ 403 com `situacao` `BANIDA`); `banirTiraDaFila`; `banirDesfazConexoesEOOutroRecebeOEventoDoDesfazer` (o `dados` de `conexao.desfeita` é igual ao de um desfazer comum); `banirEncerraOEncontroEOOutroVeTerminou`; `banidoRecebeModeracaoContaDuravel` (linha `moderacao.conta` em `evento_conta`; um stream reaberto com `Last-Event-ID` anterior a recebe); `streamDoBanidoEFechadoDepoisDoCommit`; `banirEncerraOutrasDenunciasPendentes`; `banirDuasVezesNaoDuplica`; `ouvinteQueFalhaDesfazOBanimentoInteiro` (ouvinte de teste lança ⇒ conta ATIVA, sessões, conexões e encontro intactos, zero `banimento`).
  - `ContaIndisponivelApiTest`: suspensa ⇒ `situacao` `SUSPENSA`; banida ⇒ `BANIDA`; ATIVA nunca recebe `situacao`.
  - `ExclusaoApiTest` (Etapa 2) ganha `exclusaoZeraDetalheDasDenunciasFeitas`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** `./mvnw -q verify` inteiro verde (ouvintes em quatro módulos; a ArchUnit confere que ninguém importa classe interna de outro). Commit `feat(moderacao): banimento derruba sessões e tira a conta da fila, das conexões e do encontro`.

### Task 4: Evidência da denúncia e fila do admin

**Arquivos:**
- Criar: `src/main/resources/db/migration/V120__denuncia_resolucao_e_evidencia.sql`
- Criar: `moderacao/{ConteudoDenunciado,ConteudoParaRevisao,ItemRevisado,CapturaDeEvidencia,CopiaDeImagensDaEvidencia,RetencaoDeEvidencias,FilaDeDenuncias,DenunciaNaFila,DetalheDaDenuncia}.java`, `moderacao/dominio/{Prioridade,PrazosDeModeracao}.java`, `admin/web/{DenunciasAdminController,ImagensAdminController}.java`
- Criar: `encontro/ConteudoDenunciadoDoEncontro.java`, `conexao/ConteudoDenunciadoDaConexao.java`
- Teste: `moderacao/dominio/PrazosDeModeracaoTest.java`, `moderacao/{CapturaDeEvidenciaTest,FilaDeDenunciasTest}.java`, `admin/DenunciasAdminApiTest.java`, `encontro/ConteudoDenunciadoDoEncontroTest.java`, `conexao/ConteudoDenunciadoDaConexaoTest.java`

**Esquema (V120):** em `denuncia`: `check` de `situacao` passa a `('PENDENTE','CONFIRMADA','DESCARTADA','ENCERRADA')`; colunas `resolvida_em timestamptz`, `resolvida_por uuid references conta(id)`; `check ((situacao = 'PENDENTE') = (resolvida_em is null))`; índice parcial `denuncia_pendente_idx on denuncia (criada_em) where situacao = 'PENDENTE'`. Tabela `evidencia_denuncia(denuncia_id uuid primary key references denuncia(id) on delete cascade, disponivel boolean not null, contexto text not null, cenario text, itens jsonb not null, capturada_em timestamptz not null)`.

**Interfaces:**
- Consome: `DenunciaRegistrada`, `MotivoDenuncia`, `ContextoDenuncia`, `TipoDeContextoDenuncia` (Etapa 5); `web.texto.Links.segmentar`/`Segmento` (Etapa 5); `web.ArmazemDeImagens.caminho/apagar`, `web.Variante` (Etapa 3); `notificacao.Outbox.registrar`, `ManipuladorDeOutbox` (Etapa 4); `Perfis.publico`; `retencao.PoliticaDeRetencao` (com `tamanho()`).
- Produz:
  - `Prioridade { URGENTE, NORMAL }` com `static Prioridade de(MotivoDenuncia)`; `PrazosDeModeracao(Duration urgente, Duration normal, Duration foto)`: `Instant prazo(MotivoDenuncia, Instant criadaEm)`, `Instant prazoDaFoto(Instant enviadaEm)`, `boolean vencida(Instant prazo, Instant agora)` (vencida só **depois** do prazo).
  - `public interface ConteudoDenunciado { Set<TipoDeContextoDenuncia> tipos(); ConteudoParaRevisao capturar(ContextoDenuncia contexto, ContaId denunciado); }` — item: só se o autor for o denunciado; contexto inteiro: as últimas 20 mensagens do denunciado (P2); nada ⇒ `ConteudoParaRevisao.indisponivel()`. `ConteudoDenunciadoDoEncontro` cobre `ENCONTRO`, `MENSAGEM_ENCONTRO`, `IMAGEM_ENCONTRO`; `ConteudoDenunciadoDaConexao` cobre `CONEXAO`, `MENSAGEM_CONEXAO`. Tipo sem implementação ⇒ falha no boot.
  - `record ItemRevisado(UUID id, String tipo /* TEXTO|IMAGEM */, List<Segmento> segmentos, Optional<UUID> imagemId, Instant criadoEm)`; `record ConteudoParaRevisao(boolean disponivel, String contexto /* "Mensagem no encontro", "Imagem no encontro", "Encontro", "Mensagem na conversa", "Conversa" */, Optional<String> cenario, List<ItemRevisado> itens)`.
  - `CapturaDeEvidencia` (`@EventListener` de `DenunciaRegistrada`, **na transação** da denúncia): grava `evidencia_denuncia`; para cada imagem, `Outbox.registrar("copiar-imagem-de-evidencia", {denunciaId, imagemId})`.
  - `CopiaDeImagensDaEvidencia implements ManipuladorDeOutbox` (idempotente): copia as duas variantes de `caminho("encontro", id, v)` para `caminho("moderacao", id, v)` (acrescentar `ArmazemDeImagens.copiar(String origem, String destino, UUID id)` se não existir); original já apagada ⇒ marca o item como indisponível e termina sem erro.
  - `RetencaoDeEvidencias implements PoliticaDeRetencao`: `evidencias-de-denuncia`, `P30D`, referência `denuncia.resolvida_em`; apaga linhas e arquivos da categoria `moderacao` em lotes de 1000; pendente nunca expira.
  - `FilaDeDenuncias`: `List<DenunciaNaFila> pendentes(int pagina)` (50 por página; `URGENTE` antes, depois `criada_em`, depois `id`); `Optional<DetalheDaDenuncia> detalhe(UUID id)` (lê só `denuncia` e `evidencia_denuncia`).
  - `record DenunciaNaFila(UUID id, MotivoDenuncia motivo, Prioridade prioridade, String denunciadoApelido, Instant criadaEm, Instant prazo, boolean vencida, String contexto)`; `record DetalheDaDenuncia(UUID id, MotivoDenuncia motivo, Prioridade prioridade, Optional<String> detalhe, String denunciadoApelido, Optional<String> denunciadoFotoBorrada, String denuncianteApelido, String situacaoDoDenunciado, int outrasPendentesContraODenunciado, Instant criadaEm, Instant prazo, boolean vencida, ConteudoParaRevisao conteudo)`.
  - Rotas: `GET /api/admin/denuncias?pagina=0` (0 a 100, senão 422); `GET /api/admin/denuncias/{id}` (404 `DENUNCIA_INEXISTENTE`); `GET /api/admin/denuncias/{id}/imagens/{imagemId}/{borrada|nitida}` → `image/webp`, `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`; imagem fora da evidência daquela denúncia ⇒ 404.
- [ ] **Passo 1:** testes.
  - `PrazosDeModeracaoTest` (`@ParameterizedTest`): `PARECE_MENOR` às 10:00 ⇒ 22:00; `ASSEDIO` ⇒ +48 h; foto ⇒ +24 h; `vencida` falsa no prazo exato e verdadeira 1 s depois.
  - `ConteudoDenunciadoDoEncontroTest`: `itemDeOutroAutorEIndisponivel`; `encontroInteiroTrazSoAs20UltimasDoDenunciado` (25 do denunciado + 10 de quem denunciou ⇒ 20, todas do denunciado, da mais nova para a mais antiga); `linkVemSegmentadoComHostReal` (`https://banco.com@golpe.example/x` ⇒ host `golpe.example`); `imagemDenunciadaTrazImagemId`. `ConteudoDenunciadoDaConexaoTest`: os três primeiros para `MENSAGEM_CONEXAO`/`CONEXAO`.
  - `CapturaDeEvidenciaTest` (`BancoDeTeste`): `denunciaGravaEvidenciaNaMesmaTransacao`; `falhaNaCapturaDesfazADenuncia`; `imagemVaiParaAOutboxEECopiada` (rodar o despachante ⇒ arquivos em `moderacao/`); `copiaRepetidaNaoFalha`; `evidenciaSobreviveAExpiracaoDoChat` (rodar `RetencaoDoChatDoEncontro` e `RetencaoDasImagensDoEncontro` 31 dias depois ⇒ o detalhe continua com texto e imagem); `retencaoApagaResolvidaDepoisDe30DiasEMantemPendente`; `evidenciaNuncaTrazConteudoDeQuemDenunciou` (canário `CANARY-<uuid>` escrito por quem denunciou não aparece em `itens`).
  - `FilaDeDenunciasTest`: `filaPaginaDe50EmOrdemDePrioridade` (60 normais antigas + 3 urgentes novas ⇒ página 0 começa pelas 3 urgentes e tem 50; página 1 tem 13); `resolvidasNaoAparecem`; `consultaUsaOIndiceParcial` (`EXPLAIN` menciona `denuncia_pendente_idx` com 5000 linhas e `ANALYZE`).
  - `DenunciasAdminApiTest`: `chavesDoDetalheSaoExatamenteAsDoRecord`; `detalheNaoTrazEmailNemNascimento` (canários); `imagemDeOutraDenunciaRecebe404`; `cabecalhosDaImagemDoAdmin`; `paginaNegativaRecebe422`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): evidência copiada na denúncia e fila do admin`.

### Task 5: Decisões do admin (`Moderacao.executar`)

**Arquivos:**
- Criar: `moderacao/{Moderacao,ResultadoDaAcao}.java`, `moderacao/dominio/{AcaoDeModeracao,Nota}.java`, `admin/web/{DecisoesAdminController,FotosAdminController}.java`
- Modificar: `encontro/ConteudoDenunciadoDoEncontro.java` (método público `apagarImagem(UUID imagemId)`: linha e arquivos de `encontro`, arquivos depois do commit)
- Teste: `moderacao/dominio/NotaTest.java`, `moderacao/ModeracaoTest.java`, `admin/{DecisoesAdminApiTest,FotosAdminApiTest}.java`

**Interfaces:**
- Consome: `Auditoria` (T2), `Banimentos`, `BanimentoDeContas.reativar` (T3), `FilaDeDenuncias`, `PrazosDeModeracao`, evidência (T4), `FilaDeFotos.pendentes/aprovar/recusar`, `FotoId`, `FotoPendente` (Etapa 3), `ArmazemDeImagens.apagar("moderacao", id)`.
- Produz:
  - `record Nota(String valor)`: 1 a 500 code points, sem controle exceto `\n`; `static Optional<Nota> de(@Nullable String)` (nulo ou só espaços ⇒ vazio; inválida ⇒ `ErroDeNegocio` `NOTA_INVALIDA`, `A nota pode ter até 500 caracteres.`).
  - `public sealed interface AcaoDeModeracao`: `ConfirmarDenuncia(UUID denuncia, Optional<Nota> nota)`, `DescartarDenuncia(UUID denuncia, Optional<Nota> nota)`, `AprovarFoto(FotoId foto)`, `RecusarFoto(FotoId foto, Optional<Nota> nota)`, `RecusarFotoEBanir(FotoId foto, Optional<Nota> nota)`.
  - `Moderacao`: `@Transactional public ResultadoDaAcao executar(ContaId admin, AcaoDeModeracao acao)`, `switch` exaustivo:
    - denúncia contra o próprio admin ⇒ 403 `ACESSO_NEGADO`;
    - Confirmar: `update denuncia set situacao = 'CONFIRMADA', resolvida_em = ?, resolvida_por = ? where id = ? and situacao = 'PENDENTE'` (0 linhas ⇒ 404 se não existe, 409 `DENUNCIA_JA_RESOLVIDA` se resolvida); `IMAGEM_SENSIVEL` ⇒ apaga a imagem da evidência e a original (P4); `Banimentos.banir(denunciado, DENUNCIA_CONFIRMADA, ...)`; auditoria `DENUNCIA_CONFIRMADA` (+ `CONTA_BANIDA` se o estado mudou);
    - Descartar: mesma escrita condicional com `DESCARTADA`; `PARECE_MENOR` ⇒ `reativar` se não houver outra `PARECE_MENOR` pendente (P5), com auditoria `CONTA_REATIVADA`; auditoria `DENUNCIA_DESCARTADA`;
    - Fotos: `FilaDeFotos.aprovar/recusar` + auditoria; `RecusarFotoEBanir` ⇒ recusa + `Banimentos.banir(titular, FOTO_SENSIVEL, ...)` + auditoria `FOTO_RECUSADA_E_BANIDA`.
  - `record ResultadoDaAcao(String situacao /* CONFIRMADA|DESCARTADA|APROVADA|RECUSADA */, boolean contaBanida, boolean contaReativada)`.
  - Rotas (corpo estrito `{ "nota"? }`; campo desconhecido ⇒ 400): `POST /api/admin/denuncias/{id}/confirmar`, `/descartar`; `GET /api/admin/fotos?pagina=0` (`FotoPendente` + apelido + `prazo` + `vencida`); `POST /api/admin/fotos/{id}/aprovar`, `/recusar`, `/recusar-e-banir`; foto para o admin por `GET /api/admin/fotos/{id}/nitida` (`no-store`). Respostas 200 com `ResultadoDaAcao`.
- [ ] **Passo 1:** testes.
  - `NotaTest`: 500 code points com emoji passa; 501 falha; `\u0000` falha; `\n` passa; só espaços ⇒ vazio.
  - `ModeracaoTest` (`BancoDeTeste`): `confirmarImagemSensivelBaneEApagaAImagem` (arquivos de `encontro` e `moderacao` somem depois do commit; `banimento.motivo` = `DENUNCIA_CONFIRMADA`; duas auditorias); `confirmarPareceMenorBaneSuspensa`; `descartarPareceMenorReativa`; `descartarPareceMenorComOutraPendenteNaoReativa`; `segundaPareceMenorNaoMudaNada`; `descartarAssedioNaoMexeNaConta`; `confirmarResolvidaRecebe409ENaoAudita`; `decisoesSimultaneasTemUmVencedor` (duas threads ⇒ um 200, um 409, uma `DENUNCIA_CONFIRMADA`, um `banimento`); `denunciadoExcluidoAindaPodeSerDescartado`; `recusarFotoEBanirBaneOTitular`; `aprovarFotoAudita`; `adminNaoDecideDenunciaContraSiMesmo`.
  - `DecisoesAdminApiTest`: `corpoComCampoExtraRecebe400ENadaMuda` (`{"situacao":"CONFIRMADA"}` ⇒ 400, denúncia PENDENTE, zero auditoria); `contaComumRecebe403` em todas as rotas de decisão.
  - `FotosAdminApiTest`: `listaComPrazoEVencida`; `recusarEBanirDerrubaSessaoDoTitular`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): decisões do admin com sanção e auditoria na mesma transação`.

### Task 6: SLA, alerta da fila e métricas

**Arquivos:**
- Criar: `src/main/resources/db/migration/V123__alerta_da_fila.sql`
- Criar: `moderacao/{ResumoDaFila,EstadoDaFila,AlertaDaFila,EmailDeAlertaDaFila,MetricasDaFila}.java`, `admin/web/ResumoAdminController.java`
- Modificar: `pom.xml` (métricas do Micrometer: conferir no Context7 o artefato do Boot 4.2; se a Etapa 10 já trouxe o actuator, nada a fazer), `application.properties` (`duora.moderacao.alerta.cron=0 */15 * * * *`)
- Teste: `moderacao/{ResumoDaFilaTest,AlertaDaFilaTest,MetricasDaFilaTest}.java`

**Esquema (V123):** `alerta_fila(janela timestamptz not null, motivo text not null check (motivo in ('URGENTE','VENCIDA')), enviado_em timestamptz not null, primary key (janela, motivo))`.

**Interfaces:**
- Consome: `FilaDeDenuncias`, `PrazosDeModeracao` (T4); `FilaDeFotos`; `Outbox`, `ManipuladorDeOutbox`, `EnvioDeEmail`; `Administradores` (T1); `MeterRegistry`.
- Produz:
  - `ResumoDaFila.agora() -> EstadoDaFila`; `record EstadoDaFila(int urgentesPendentes, int normaisPendentes, int fotosPendentes, int vencidas, Optional<Instant> maisAntigaPendente, Optional<Instant> proximoPrazo)` (consultas agregadas, sem carregar linhas). `GET /api/admin/resumo` → `EstadoDaFila`.
  - `AlertaDaFila` (`@Scheduled(cron)`): se há urgente criada depois do último alerta `URGENTE`, ou vencida sem alerta `VENCIDA` nas últimas 6 h, numa transação insere `alerta_fila` (janela = início do quarto de hora; conflito ⇒ nada) e `Outbox.registrar("email-alerta-fila", {urgentes, vencidas})`. `EmailDeAlertaDaFila implements ManipuladorDeOutbox` envia a cada admin: assunto `Duora: denúncias aguardando análise`; corpo `Há {u} denúncias urgentes e {v} vencidas na fila. Abra {url-publica}/admin para analisar.` Só contagens.
  - `MetricasDaFila`: gauges `duora.moderacao.pendentes{prioridade=urgente|normal}`, `duora.moderacao.fotos.pendentes`, `duora.moderacao.vencidas`, `duora.moderacao.idade.maxima.segundos`, com cache de 30 s sobre o `ResumoDaFila`.
- [ ] **Passo 1:** testes.
  - `ResumoDaFilaTest`: 2 urgentes, 3 normais, 1 vencida, 4 fotos ⇒ contagens; vazia ⇒ zeros.
  - `AlertaDaFilaTest` (`EnvioDeEmail` falso, despachante da outbox executado à mão): `urgenteNovaDisparaUmEmailPorAdmin`; `segundaExecucaoNaMesmaJanelaNaoReenvia`; `vencidaRealertaSoDepoisDe6h` (5 h 59 min não, 6 h sim); `semPendenciaNaoEnvia`; `corpoSoTemContagens` (nem apelido canário nem id); `rollbackNaoDeixaEmailNaOutbox`.
  - `MetricasDaFilaTest`: os gauges com os valores do resumo; duas coletas em 10 s ⇒ uma consulta.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): SLA da fila com alerta por e-mail e métricas`.

### Task 7: Painel admin em React (rotas lazy) e "Denunciar link"

**Arquivos:**
- Criar: `frontend/src/admin/{rotas.tsx,api.ts,LayoutAdmin.tsx,ResumoTela.tsx,DenunciasTela.tsx,DenunciaTela.tsx,ConteudoRevisado.tsx,FotosTela.tsx,AuditoriaTela.tsx,DialogoDeDecisao.tsx}`, `frontend/src/conta/ContaIndisponivelTela.tsx`
- Modificar: o roteador da Etapa 2 (`/admin/*` com `lazy: () => import('./admin/rotas')`), o tratamento global de 403 com `situacao` e o ouvinte de `moderacao.conta` (fecha o `EventSource` e vai para `/conta/indisponivel`), `frontend/src/encontro/AvisoDeSaida.tsx` (Etapa 5: "Denunciar link")
- Teste: `frontend/src/admin/{DenunciasTela,DenunciaTela,ConteudoRevisado,FotosTela}.test.tsx`, `frontend/src/conta/ContaIndisponivelTela.test.tsx`, `frontend/src/encontro/AvisoDeSaida.test.tsx` (caso novo)

**Interfaces:**
- Consome: rotas das Tasks 1–6; TanStack Query; `useEvento` (Etapa 4); `Segmento` (Etapa 5); `DenunciarDialogo` (Etapa 5).
- Produz:
  - Rotas: `/admin` (`ResumoTela`: contagens, "Mais antiga há {n} h", vencidas em destaque), `/admin/denuncias`, `/admin/denuncias/:id`, `/admin/fotos`, `/admin/auditoria`. `LayoutAdmin` chama `GET /api/admin/eu`: 403 `ACESSO_NEGADO` ⇒ "Você não tem acesso a esta área." com link para o início; `REAUTENTICACAO_NECESSARIA` ⇒ `/entrar`. A Etapa 11 acrescenta `/admin/metricas` e `/admin/piloto` em `rotas.tsx`.
  - `DenunciasTela`: motivo, prioridade, apelido do denunciado, "há {n} h", prazo, selo "Vencida"; vazia: "Nenhuma denúncia aguardando análise."; "Mais antigas" pagina.
  - `DenunciaTela`: motivo, detalhe, apelido e foto borrada do denunciado, situação da conta, "Outras denúncias pendentes contra esta conta: {n}", `ConteudoRevisado`, "Confirmar e banir" e "Descartar". `DialogoDeDecisao` com nota opcional e os textos: "Confirmar a denúncia bane {apelido} na hora: as sessões caem, as conexões são desfeitas e o e-mail não pode criar outra conta." / "Descartar a denúncia?" (com `PARECE_MENOR`: "A conta volta a funcionar se não houver outra denúncia desse tipo."). 409 ⇒ "Outra pessoa já decidiu esta denúncia." e recarrega.
  - `ConteudoRevisado`: link como `<span>` com o host em `<strong>`, **sem `<a>`**; imagem borrada com "Ver imagem" (troca pela nítida da rota admin); indisponível ⇒ "Conteúdo indisponível (expirou ou foi apagado)."
  - `FotosTela`: grade com prazo e "Aprovar", "Recusar", "Recusar e banir".
  - `ContaIndisponivelTela`: texto exato de suspensa ou banida; sem botão para rota protegida.
  - `AvisoDeSaida`: "Denunciar link" abre o `DenunciarDialogo` com o contexto da mensagem e "Spam ou golpe" marcado (P7).
- [ ] **Passo 1:** testes Vitest (`fetch` falso): `filaVaziaMostraTextoHonesto`; `vencidaTemSelo`; `linkNoPainelNaoEClicavel` (nenhum `a[href]` em `ConteudoRevisado`; host em `<strong>`); `confirmarPedeConfirmacaoComTextoExato`; `conflitoMostraMensagemERecarrega`; `naoAdminVeTextoDeAcesso`; `suspensaEBanidaTemTextosDiferentes`; `eventoModeracaoContaLevaATelaIndisponivel`; `avisoDeSaidaDenunciaComSpamMarcado`.
- [ ] **Passo 2:** `pnpm --dir frontend exec vitest run src/admin src/conta src/encontro/AvisoDeSaida.test.tsx`; falha.
- [ ] **Passo 3:** implementar. Conferir no relatório do Vite que `admin` sai em chunk próprio e o JS inicial segue na meta da 1a.
- [ ] **Passo 4:** verde. Commit `feat(admin): painel de moderação com rotas lazy e denúncia de link`.

### Task 8: Prova ponta a ponta e acessibilidade

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/{ModeracaoIT,AdminPagina}.java`
- Modificar: `e2e/AxeIT.java`, `.claude/handoff/_mapa.md` (`moderacao/`, `admin/`)

**Interfaces:**
- Consome: `Participantes.novoComConta`, `ContasDeTeste.criarAdmin` (T1), `EncontroPagina` (Etapa 5), `ConexoesPagina` (Etapa 6), `EmailNoLog`.
- Produz: `AdminPagina` com `abrirFila()`, `abrirDenuncia(String apelido)`, `confirmarEBanir()`, `descartar()`, `textoDoConteudo()`, `abrirFotos()`, `aprovarFoto(String apelido)`.
- [ ] **Passo 1:** IT.
  - `ModeracaoIT.denunciaAnaliseBanimento` (prova da etapa): Ana e Bia pareadas; as duas permitem imagens; Bia envia a imagem de teste; Ana denuncia como "Imagem sensível"; a imagem some para Ana; a admin (terceiro contexto) vê a denúncia urgente, abre, vê a borrada e "Ver imagem" mostra a nítida; confirma; em até 3 s o contexto de Bia mostra o texto de banida (`banidaComAbaAbertaVeOTextoNaHora`); Ana vê "O encontro terminou."; `POST /api/fila` de Bia pelo `APIRequestContext` ⇒ 403; a auditoria lista "Denúncia confirmada" e "Conta banida".
  - `ModeracaoIT.pareceMenorSuspendeERevisaoReativa`: Ana denuncia Bia como "Parece menor de idade"; Bia vê o texto da suspensão na hora; a admin descarta; Bia entra de novo pelo link e volta à fila.
  - `ModeracaoIT.suspensaVeOTextoAoEntrar`: suspensa abre `/` e `/entrar/confirmar#t=...` ⇒ texto da suspensão; a URL estabiliza em até 2 s.
  - `ModeracaoIT.denunciaDeLinkChegaComHostReal`: Bia manda `https://banco.com@golpe.example/x`; Ana clica, vê o aviso e "Denunciar link"; a admin vê `golpe.example` em destaque e nenhum link clicável.
  - `AxeIT`: `painelAdminResumoSemViolacoes`, `filaDeDenunciasSemViolacoes`, `detalheDaDenunciaSemViolacoes`, `filaDeFotosSemViolacoes`, `contaIndisponivelSemViolacoes`.
- [ ] **Passo 2:** `./mvnw -q -Pe2e verify -Dit.test='ModeracaoIT,AxeIT'` em chromium, depois `-Dnavegador=firefox` e `webkit`. Verde, sem skip.
- [ ] **Passo 3:** Android (`scripts/android/`): a admin abre `/admin/denuncias` em retrato no emulador; captura em `target/android/admin-fila.png`; sem rolagem horizontal (largura do documento ≤ largura da janela).
- [ ] **Passo 4:** `_mapa.md`. Commit `test(e2e): denúncia, análise e banimento de ponta a ponta`.

## Ordem e paralelismo
| Leva | Tasks (worktrees separados) | Depende de | Flyway |
|---|---|---|---|
| 1 | T1 ∥ T2 | Etapa 5 T7 | T2: V121 |
| 2 | T3 ∥ T4 | T2; Etapa 6 T3 para as partes de conexão | T3: V122; T4: V120 |
| 3 | T5 | T2, T3, T4 | — |
| 4 | T6 ∥ T7 | T4, T5 (T7 pode começar na Leva 2 contra as Interfaces, com `fetch` falso) | T6: V123 |
| 5 | T8 | todas | — |

- Sem out-of-order: se a T3 (V122) entrar antes da T4 (V120), a T4 renumera para V124 antes do merge (e a T6 segue com V123 só se V123 ainda for maior que tudo no branch; senão, o próximo livre).
- No máximo 4 agentes ao mesmo tempo, contando as Etapas 5 e 6 que correm junto. A T3 edita `encontro/`, `conexao/`, `notificacao/` e `web/eventos/`: não rodar junto de task das Etapas 5/6 que edite os mesmos arquivos.
- Revisão final com `superpowers:requesting-code-review`, focada no STRIDE acima e na concorrência (T3, T5).

## Adições ao contrato (propostas, não aplicadas)
- `GLOSSARY.md` na raiz com os termos de moderação acima (e os de encontro e conexão).
- `conta.BanimentoDeContas` ao lado de `SuspensaoDeContas`; evento `ContaBanida` (Observer na transação) como o jeito de os módulos reagirem ao banimento.
- `CanalDeEventos.encerrarStreams(ContaId)` (só memória, depois do commit).
- `moderacao.conta` `{ "situacao" }` como único evento SSE de moderação.
- Categoria `moderacao` no `ArmazemDeImagens` e o método `copiar(origem, destino, id)`.
- Retenção (Etapa 9): evidência de denúncia resolvida expira em 30 dias; pendente fica até a análise; auditoria e `banimento` ficam enquanto o serviço existir (registro de segurança). A política de privacidade precisa citar isso numa versão nova.
