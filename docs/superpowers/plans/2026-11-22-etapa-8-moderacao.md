# Etapa 8 — Moderação e painel admin: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** uma pessoa com papel ADMIN revisa denúncias (vendo só o conteúdo do denunciado: mensagem, imagem ou link) e fotos de perfil num painel React, confirma ou descarta; confirmar bane a conta (sessões derrubadas, fora da fila, das conexões e do encontro), "parece menor de idade" segue suspendendo na hora e é revertida ou confirmada pela revisão, e toda ação de admin fica numa auditoria imutável. A fila tem SLA, alerta por e-mail e métricas.

**Arquitetura:**
- Um módulo profundo `moderacao.Moderacao` com uma porta de entrada só, `executar(ContaId admin, AcaoDeModeracao acao)`. Ele faz a transição de estado, a sanção, a auditoria e agenda os efeitos, tudo numa transação. Os controllers de `admin/` só traduzem HTTP.
- O banimento é `moderacao.Banimentos`, que chama os ganchos `AoBanirConta` de cada módulo dono (conta, sala, conexão, notificação) na mesma transação. Efeitos em memória (encontro aberto, streams SSE) saem depois do commit e são repetidos por uma varredura de 60 s, então um crash entre o commit e o efeito não deixa ninguém banido dentro de um encontro.
- O conteúdo mostrado ao admin vem da porta `moderacao.ConteudoDenunciado`, implementada pelo módulo dono do conteúdo (`encontro/`, `conexao/`). A moderação nunca lê as tabelas deles.
- O papel ADMIN vem de configuração (`duora.admin.emails`), sem tabela nem tela de concessão.
- Painel em `frontend/src/admin/` com rotas lazy em `/admin/**`.

**Stack:** Java 25, Spring Boot 4.2.0, `JdbcClient`, PostgreSQL 18 (trigger para a auditoria append-only), Flyway, Testcontainers, Micrometer (gauges), React 19 + TypeScript + Tailwind 4, React Router (lazy), TanStack Query, Vitest, Playwright Java.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 8; "Escolhas minhas": 18+ com "parece menor" suspendendo na hora, "Sem imagens sensíveis", "Imagens", "Links", "Sair, denunciar e bloquear"). **Contratos (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Planos com que os nomes se alinham: Etapas 2, 3, 4, 5, 6 e 9 (`docs/superpowers/plans/2026-11-22-etapa-*.md`).

**Pré-requisitos:** Etapa 5 Task 7 (denúncia e bloqueio básicos, V85/V86) e Etapa 3 Task 5 (`FilaDeFotos`) no branch. A Etapa 6 entra antes das Tasks 3 e 4 (conexões e conteúdo `MENSAGEM_CONEXAO`); se ainda não estiver, as partes de conexão destas tasks ficam como a última sub-task delas.

## Restrições globais (Global Constraints)
- Tudo do arquivo de contratos e das restrições globais das Etapas 2, 4 e 5 vale aqui; em conflito, o contrato vence.
- **Flyway:** só **V120–V139**. Usadas: V120 (resolução da denúncia), V121 (auditoria), V122 (banimento), V123 (alerta da fila). V124–V139 reservadas para correções.
- **Nomes da Etapa 2:** onde este plano diz `ErroDeNegocio`, `LimiteDeTaxa`, `CodigoWeb`, use o que estiver na `main` (a Etapa 5 os chama de `web.ErroApi`/`web.LimitesDeTaxa`); o comportamento não muda.
- **Papel ADMIN:** `duora.admin.emails` (lista separada por vírgula, normalizada como `Email.chaveDeBloqueio()`), vazia por padrão. Em `prod` vem de `${DUORA_ADMIN_EMAILS}`. Toda rota `/api/admin/**` exige: sessão válida, conta ATIVA, e-mail na lista e sessão aberta há no máximo **12 h** (`duora.admin.sessao-maxima=PT12H`). Sem sessão → 401 `NAO_AUTENTICADO`; com sessão e sem papel → 403 `ACESSO_NEGADO`; papel com sessão velha → 403 `REAUTENTICACAO_NECESSARIA`.
- **Contexto limitado ao denunciado:** o admin só vê o que o denunciado escreveu ou enviou. Nunca a mensagem de quem denunciou, nunca o perfil de quem denunciou (só o apelido), nunca e-mail, data de nascimento ou célula. Links aparecem como texto com o host real em destaque, **nunca clicáveis** no painel. Imagens chegam borradas, com "Ver imagem".
- **SLA (proposta, ver D2):** prioridade `URGENTE` (`PARECE_MENOR`, `IMAGEM_SENSIVEL`) em **12 h**; `NORMAL` (`ASSEDIO`, `SPAM_OU_GOLPE`, `OUTRO`) em **48 h**; foto de perfil pendente em **24 h**. Propriedades `duora.moderacao.sla.urgente`, `.normal`, `.foto`.
- **Confirmar bane** (P1). O banimento é definitivo no piloto: não há desbanir pela API.
- **Auditoria:** uma linha por ação de admin, gravada **na mesma transação** da ação; a tabela recusa `UPDATE` e `DELETE` por trigger. Nota livre até 500 caracteres, sem caracteres de controle exceto `\n`.
- **Códigos novos** (`moderacao/CodigoModeracao`, `admin/CodigoAdmin`): `ACESSO_NEGADO` 403, `REAUTENTICACAO_NECESSARIA` 403, `DENUNCIA_INEXISTENTE` 404, `DENUNCIA_JA_RESOLVIDA` 409, `FOTO_INEXISTENTE` 404 (Etapa 3), `NOTA_INVALIDA` 422.
- **Textos (pt-BR, gênero neutro, exatos):**
  - suspensa: "Sua conta está suspensa enquanto analisamos uma denúncia. Se tiver dúvidas, escreva para {contato}.";
  - banida: "Sua conta foi encerrada por violar os termos de uso. Se achar que foi um engano, escreva para {contato}.";
  - quem estava com a pessoa banida no encontro ou na conversa vê o mesmo texto de uma saída ou de um desfazer ("O encontro terminou." / "Esta conexão não está mais disponível.").
- **Logs:** nunca o texto denunciado, a nota do admin, e-mail ou URL de imagem. Só ids, tipo da ação e contagens.

## Glossário do domínio (proposto para um `GLOSSARY.md` na raiz, ver "Adições ao contrato")
- **Denúncia:** pedido de uma conta para a moderação olhar o comportamento de outra num contexto (encontro, conexão, mensagem ou imagem). Situação: **pendente**, **confirmada**, **descartada** ou **encerrada** (fechada sem análise porque a conta denunciada já foi banida por outra).
- **Prioridade:** urgente ou normal, derivada do motivo. Não é escolhida por ninguém.
- **Prazo (SLA):** instante até o qual uma denúncia pendente deve ser analisada. Passado o prazo, ela está **vencida**.
- **Suspensão:** bloqueio temporário e automático da conta, sem admin, pela denúncia "parece menor de idade". Termina em **reativação** (denúncia descartada) ou **banimento** (confirmada).
- **Banimento:** encerramento definitivo da conta por decisão de admin. Diferente de **exclusão**, que é pedida pela própria pessoa.
- **Bloqueio:** decisão de uma pessoa de não ver mais outra. Não passa pela moderação.
- **Conteúdo denunciado:** o que o denunciado escreveu ou enviou no contexto da denúncia, e só isso.
- **Ação de moderação:** decisão de admin sobre uma denúncia ou foto. Toda ação vira um **registro de auditoria**.

## Desenho do módulo (duas alternativas, `mattpocock-skills:codebase-design`)
- **A. Serviços por ação** (`ServicoDeDenuncias.confirmar`, `ServicoDeFotos.recusar`, `Banimentos.banir`, `Auditoria.registrar`), cada controller orquestrando e chamando a auditoria. Interface larga (4 módulos, ~8 métodos que o caller precisa combinar na ordem certa); esquecer `Auditoria.registrar` num controller novo passa nos testes do serviço. Rasa.
- **B. Fachada profunda** `Moderacao.executar(ContaId admin, AcaoDeModeracao acao) -> ResultadoDaAcao`, com `AcaoDeModeracao` selado (`switch` exaustivo). Transição, sanção, auditoria e efeitos ficam atrás de uma interface de um método; não existe caminho de ação sem auditoria. `Banimentos` e `Auditoria` viram seams internos (testados pelos próprios testes), e `AoBanirConta`/`ConteudoDenunciado` são seams reais (várias implementações, uma por módulo dono).
- **Escolha: B.** Teste de deleção: apagar `Moderacao` espalharia a regra "toda ação audita e sanciona na mesma transação" pelos controllers. A leitura (filas, detalhe, resumo) fica em `FilaDeDenuncias` e `ResumoDaFila`, separadas porque não mudam estado.

## STRIDE (ameaça → mitigação → teste)
| Ameaça | Mitigação | Teste |
|---|---|---|
| S: alguém com sessão roubada de admin age por dias | sessão de admin vale 12 h desde a abertura | `sessaoDeAdminComMaisDe12hPedeReautenticacao` (T1) |
| T: apagar ou editar o rastro de uma ação | trigger recusa `UPDATE`/`DELETE` em `acao_admin` | `auditoriaRecusaUpdateEDelete` (T2) |
| R: admin nega ter banido | auditoria na mesma transação da ação | `acaoQueFalhaNaoDeixaAuditoriaEAuditoriaQueFalhaDesfazAcao` (T2) |
| I: admin vê mais do que o denunciado fez | `ConteudoDenunciado` filtra por autor = denunciado; DTO com chaves fixas | `detalheNuncaTrazConteudoDeQuemDenunciou` (T4) |
| I: link de golpe clicado pelo admin | link como texto com host real, sem `href` | `linkNoPainelNaoEClicavel` (T7) |
| D: rajada de denúncias falsas contra alguém | limite `denuncia` (Etapa 5, 10/h por conta); `PARECE_MENOR` repetida não duplica | já coberto na Etapa 5; `segundaPareceMenorNaoMudaNada` (T5) |
| E: conta comum chamando `/api/admin/**` | interceptor `ExigeAdmin` | `todaRotaAdminRecusaQuemNaoEAdmin` (T1) |

## Decisões de planejamento (o dono pode reverter)
- **P1. Confirmar sempre bane** (sem advertência no piloto). Uma advertência exigiria estado novo, texto e regra de reincidência; o piloto é pequeno e a regra "imagem sensível confirmada → banimento" da spec já é de um golpe só.
- **P2. Denúncia de encontro ou conexão inteiros** (sem item) mostra as **últimas 20 mensagens do denunciado** naquele contexto (texto e imagens), nunca as de quem denunciou. Denúncia de item mostra só o item.
- **P3. Banir encerra as outras denúncias pendentes** contra a mesma conta como `ENCERRADA` (não contam como confirmadas nas métricas).
- **P4. Imagem confirmada como sensível é apagada na hora** (linha e arquivos, depois do commit). Descartada, ela continua escondida só para quem denunciou e volta a expirar com o chat.
- **P5. Descartar "parece menor" reativa a conta** só se ela estiver SUSPENSA e não houver outra `PARECE_MENOR` pendente contra ela.
- **P6. Banido não pode se cadastrar de novo com o mesmo e-mail:** o banimento grava o HMAC em `email_bloqueado` com motivo `BANIMENTO` (mesma regra do menor).
- **P7. "Denunciar link"** é a denúncia da mensagem que contém o link (`MENSAGEM_ENCONTRO`/`MENSAGEM_CONEXAO`), aberta no aviso "Você vai sair do Duora" com o motivo "Spam ou golpe" pré-selecionado.

## Decisões do dono
| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| D1 | Quem modera no piloto. Denúncia de pessoa real precisa de decisão humana; agente não decide banimento. | (a) só o dono, pelo e-mail dele em `DUORA_ADMIN_EMAILS`; (b) o dono e mais uma pessoa de confiança | (a) para abrir; (b) quando a fila passar de ~10 denúncias por semana. Trocar é editar uma variável |
| D2 | SLA de análise | (a) urgente 12 h, normal 48 h, foto 24 h; (b) urgente 24 h, normal 72 h, foto 48 h; (c) urgente 4 h | (a): a suspensão automática já protege no caso "menor", e 12 h cabe numa rotina de olhar o painel de manhã e à noite; (c) não é sustentável para uma pessoa só |
| D3 | Canal do alerta da fila | (a) e-mail pelo Resend para os admins; (b) Web Push no PWA do admin; (c) os dois | (a): já existe, não depende de o admin ter instalado o PWA |

Nenhuma bloqueia a etapa: o código lê tudo de propriedades, com (a) como padrão.

## Foco de revisão (Review Focus)
1. **Pessoa banida com o encontro aberto no celular e o servidor reiniciando entre o commit e o efeito:** a outra pessoa vê "O encontro terminou." em até 60 s, e o banido não consegue mais nenhuma ação. Coberto por `varreduraEncerraEncontroDeContaBanidaSeOEfeitoSePerdeu` (T3).
2. **Dois admins (ou duas abas) decidindo a mesma denúncia ao mesmo tempo:** um vence, o outro recebe 409 e só há uma linha de auditoria e um banimento. Coberto por `decisoesSimultaneasTemUmVencedor` (T5).
3. **Denúncia sobre conteúdo que já expirou ou conta que se excluiu:** o detalhe abre com "Conteúdo indisponível" em vez de 500, e a decisão ainda funciona. Coberto por `conteudoExpiradoMostraIndisponivel` e `denunciadoExcluidoAindaPodeSerDescartado` (T4, T5).
4. **Admin abre o painel com a fila vazia, ou com 500 denúncias:** estado vazio honesto; a lista pagina (50 por página) e a consulta usa o índice parcial. Coberto por `filaPaginaDe50EmOrdemDePrioridade` (T4) e `filaVaziaMostraTextoHonesto` (T7).
5. **Suspensa tenta entrar de novo pelo link mágico ou com a aba aberta:** vê o texto da suspensão, sem laço de redirecionamento, nos três navegadores. Coberto por `suspensaVeOTextoAoEntrar` (T8).

---

### Task 1: Papel ADMIN e rotas `/api/admin`

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/admin/{Administradores,ExigeAdmin,AdminWebConfig,AdminController,CodigoAdmin}.java`
- Modificar: `conta/SessaoAtiva.java` (ganha `Instant abertaEm` se ainda não tiver), `src/main/resources/application.properties` (`duora.admin.emails=`, `duora.admin.sessao-maxima=PT12H`), `application-prod.properties` (`duora.admin.emails=${DUORA_ADMIN_EMAILS}`)
- Teste: `admin/{AdministradoresTest,RotasAdminTest,AdminApiTest}.java`; `ContasDeTeste` ganha `ContaDeTeste criarAdmin()` (conta ATIVA cujo e-mail entra na lista pelo `@DynamicPropertySource` da base de teste)

**Interfaces:**
- Consome: `Email.de`, `Email.chaveDeBloqueio()` (Etapa 2 T5); `Sessoes`, `SessaoAtiva`, `ExigeSessao`, `ContaAtual` (Etapa 2 T6); `ConsultaDeContas` (Etapa 2).
- Produz:
  - `public class Administradores`: `boolean eAdmin(ContaId conta)` (lê o e-mail da conta e compara a chave normalizada com a lista; conta sem e-mail ⇒ falso).
  - `ExigeAdmin` (interceptor em `/api/admin/**`, registrado depois do `ExigeSessao`): sem papel ⇒ 403 `ACESSO_NEGADO` (`Você não tem acesso a esta área.`); `abertaEm` + 12 h ≤ agora ⇒ 403 `REAUTENTICACAO_NECESSARIA` (`Entre de novo para continuar.`).
  - `GET /api/admin/eu` → 200 `{"admin": true}`.
  - Convenção: todo handler de `admin/` recebe o admin por `@ContaAtual ContaId admin`.

- [ ] **Passo 1:** testes.
  - `AdministradoresTest` (`@ParameterizedTest`): lista `Ana@Exemplo.com, b+x@gmail.com` reconhece `ana@exemplo.com` e `b@gmail.com`; não reconhece `c@exemplo.com`; lista vazia não reconhece ninguém.
  - `RotasAdminTest` (`todaRotaAdminRecusaQuemNaoEAdmin`): percorre o `RequestMappingHandlerMapping`; para cada padrão `/api/admin/**`, sem cookie ⇒ 401; conta ATIVA comum ⇒ 403 `ACESSO_NEGADO` e corpo sem outra chave além das do problem+json; admin ⇒ nunca 401/403. A lista de rotas admin não pode ficar vazia (o teste falha se achar zero).
  - `AdminApiTest`: `adminRecebeEu`; `sessaoDeAdminComMaisDe12hPedeReautenticacao` (relógio avança 12 h ⇒ 403 `REAUTENTICACAO_NECESSARIA`; 11 h 59 min ⇒ 200); `adminSuspensoRecebe403DaSessao` (conta SUSPENSA na lista ⇒ 403 `CONTA_INDISPONIVEL`, antes do interceptor de admin).
- [ ] **Passo 2:** `./mvnw -q verify -Dtest='AdministradoresTest,RotasAdminTest,AdminApiTest'`; falha.
- [ ] **Passo 3:** implementar. A Etapa 2 já tem a `ArchUnit`: `admin` pode depender de `moderacao`, `conta`, `perfil`; nada depende de `admin`.
- [ ] **Passo 4:** verde. Commit `feat(admin): papel ADMIN por configuração e rotas /api/admin protegidas`.

### Task 2: Auditoria imutável

**Arquivos:**
- Criar: `src/main/resources/db/migration/V121__acao_admin.sql`
- Criar: `moderacao/{Auditoria,RegistroDeAuditoria,TipoDeAcao,PaginaDeAuditoria}.java`, `admin/AuditoriaController.java`
- Teste: `moderacao/AuditoriaTest.java`, `admin/AuditoriaApiTest.java`

**Esquema (V121):** `acao_admin(seq bigint generated always as identity primary key, id uuid not null unique, admin uuid not null references conta(id), tipo text not null check (tipo in ('DENUNCIA_CONFIRMADA','DENUNCIA_DESCARTADA','FOTO_APROVADA','FOTO_RECUSADA','FOTO_RECUSADA_E_BANIDA','CONTA_BANIDA','CONTA_REATIVADA','CADASTRO_ALTERADO','CAPACIDADE_ALTERADA')), alvo_conta uuid references conta(id), alvo_ref text, nota text check (char_length(nota) <= 500), criada_em timestamptz not null)`; índice em `alvo_conta`; função `acao_admin_imutavel()` que faz `raise exception 'acao_admin é somente inclusão'` e trigger `before update or delete ... for each row`. Sem `on delete cascade`: contas não são apagadas (a exclusão vira lápide, Etapa 2 T9). Os dois últimos tipos são usados pela Etapa 11.

**Interfaces:**
- Produz:
  - `public enum TipoDeAcao` com os valores do `check`.
  - `public class Auditoria`: `@Transactional(propagation = MANDATORY) public void registrar(ContaId admin, TipoDeAcao tipo, Optional<ContaId> alvo, Optional<String> referencia, Optional<String> nota)`; fora de transação ⇒ `IllegalTransactionStateException`. A Etapa 11 usa este método.
  - `public PaginaDeAuditoria pagina(Optional<Long> antesDeSeq, int limite)` (keyset por `seq desc`, limite ≤ 50); `record RegistroDeAuditoria(long seq, String adminApelido, TipoDeAcao tipo, Optional<String> alvoApelido, Optional<String> referencia, Optional<String> nota, Instant criadaEm)`; `record PaginaDeAuditoria(List<RegistroDeAuditoria> itens, Optional<Long> proximaAntesDe)`.
  - `GET /api/admin/auditoria?antesDe=<seq>` → `PaginaDeAuditoria` (apelido vem de `Perfis.publico`; conta excluída ⇒ `"Conta excluída"`).

- [ ] **Passo 1:** testes.
  - `AuditoriaTest` (`BancoDeTeste`): `registraNaTransacaoCorrente`; `foraDeTransacaoFalha`; `auditoriaRecusaUpdateEDelete` (`UPDATE` e `DELETE` por `JdbcClient` lançam `DataAccessException` com a mensagem do trigger; a linha continua igual); `acaoQueFalhaNaoDeixaAuditoriaEAuditoriaQueFalhaDesfazAcao` (bean de teste que grava auditoria + `UPDATE conta` e lança no fim ⇒ zero linhas e conta intacta; nota com 501 caracteres ⇒ a ação também não fica); `paginaPorKeysetSemRepetir` (120 registros ⇒ três páginas de 50/50/20, sem repetição, ordem decrescente).
  - `AuditoriaApiTest`: chaves do JSON exatamente as do record; conta comum ⇒ 403.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): auditoria imutável das ações de admin`.

### Task 3: Banimento com efeito em todos os módulos

**Arquivos:**
- Criar: `src/main/resources/db/migration/V122__banimento.sql`
- Criar: `moderacao/{Banimentos,MotivoBanimento,AoBanirConta,ContaBanida,EfeitosDoBanimento,VarreduraDeContasInativas,ModeracaoAoExcluirConta}.java`
- Criar (implementações dos ganchos, cada uma no módulo dono): `conta/ContaAoBanir.java`, `sala/FilaAoBanir.java`, `conexao/ConexoesAoBanir.java`, `notificacao/PushAoBanir.java`
- Modificar: `conta/Contas.java` (`banir`, `reativar`; `suspender` se a Etapa 5 não o criou), `conta/ExigeSessao.java` (403 de conta própria ganha a extensão `situacao`), `web/eventos/CanalDeEventos.java` + `Assinaturas.java` (`encerrar`), `encontro/EfeitosDaModeracaoNoEncontro.java` (motivo `BANIMENTO`)
- Teste: `moderacao/{BanimentosTest,VarreduraDeContasInativasTest}.java`, `conta/ContaIndisponivelApiTest.java`

**Esquema (V122):** `banimento(conta_id uuid primary key references conta(id), motivo text not null check (motivo in ('DENUNCIA_CONFIRMADA','FOTO_SENSIVEL')), denuncia_id uuid references denuncia(id), admin uuid not null references conta(id), banida_em timestamptz not null)`; índice em `banida_em` (métricas da Etapa 11). Mais: `alter table email_bloqueado drop constraint ..., add check (motivo in ('MENOR','BANIMENTO'))`.

**Interfaces:**
- Consome: `Sessoes.revogarTodas`, `EmailsBloqueados.bloquear(Email, String)` (Etapa 2); `Fila.removerTodos` (Etapa 4); `Conexoes.ativasDe`, `Conexoes.desfazer` (Etapa 5/6); `InscricoesDePush` (Etapa 4); `CanalDeEventos`; `Encontros`/`EfeitosDaModeracaoNoEncontro` (Etapa 5); `Contas.ativa`.
- Produz:
  - `Contas`: `boolean banir(ContaId)` (`update conta set estado = 'BANIDA' where id = ? and estado in ('ATIVA','SUSPENSA','PENDENTE')`, devolve se mudou); `boolean reativar(ContaId)` (só `SUSPENSA → ATIVA`); `void suspender(ContaId, String motivo)` (só `ATIVA → SUSPENSA`, idempotente).
  - `public interface AoBanirConta { void aoBanir(ContaId conta); }` — roda dentro da transação do banimento, na ordem do `@Order`; não pode chamar rede. Implementações: `ContaAoBanir` (revoga sessões, bloqueia o HMAC do e-mail com `BANIMENTO`), `FilaAoBanir` (sai da fila), `ConexoesAoBanir` (desfaz todas as conexões ativas; os vetos `CONECTADOS` ficam), `PushAoBanir` (apaga inscrições).
  - `moderacao.Banimentos` (pacote, usado só por `Moderacao`): `void banir(ContaId alvo, MotivoBanimento motivo, Optional<UUID> denuncia, ContaId admin)`: `Contas.banir` (falso ⇒ já banida ou excluída: grava nada e não falha); insere `banimento`; ganchos; encerra as outras denúncias pendentes contra a conta como `ENCERRADA` (P3); publica `ContaBanida(ContaId conta, List<ContaId> conexoesDesfeitasCom)`.
  - `EfeitosDoBanimento` (`@TransactionalEventListener(AFTER_COMMIT)` sobre `ContaBanida` e sobre a suspensão da Etapa 5): publica `moderacao.conta` `{ "situacao": "BANIDA"|"SUSPENSA" }` para a própria conta e chama `CanalDeEventos.encerrar(conta)`; publica `conexao.desfeita` `{ "conexaoId" }` para cada outra pessoa (idêntico ao desfazer da Etapa 6); encerra o encontro ativo com motivo `BANIMENTO` (a outra pessoa vê `TERMINOU`, igual à saída).
  - `CanalDeEventos.encerrar(ContaId)`: completa e remove todos os emitters da conta.
  - `VarreduraDeContasInativas` (`@Scheduled(fixedDelay = 60 s, initialDelay` com jitter de até 10 s`)`): para cada conta não ativa com encontro ativo ou stream aberto, aplica os mesmos efeitos (idempotentes). Métrica `duora.moderacao.varredura.efeitos` (contador).
  - 403 de conta própria (`ExigeSessao` e `POST /api/conta/confirmar`): problem+json ganha `"situacao": "SUSPENSA"|"BANIDA"`. O código continua `CONTA_INDISPONIVEL`.
  - `ModeracaoAoExcluirConta implements AoExcluirConta` (Etapa 2 T9): zera `detalhe` das denúncias feitas pela conta. Denúncias contra ela e a auditoria ficam (referem só ids).

- [ ] **Passo 1:** testes.
  - `BanimentosTest` (`BancoDeTeste`, `CanalDeEventos` real, relógio fixo): `banirDerrubaSessoes` (o cookie antigo ⇒ 403 com `situacao` `BANIDA`); `banirTiraDaFila`; `banirDesfazConexoesEAvisaOOutroComOMesmoEventoDoDesfazer` (o `data` recebido é igual byte a byte ao de um desfazer da Etapa 6); `banirEncerraOEncontroEOOutroVeTerminou`; `banidoNaoRecebeLinkDeEntrada` (`POST /api/conta/entrar` com o e-mail ⇒ mesmo 202, aviso sem `#t=`); `banirEncerraOutrasDenunciasPendentes` (`ENCERRADA`); `banirDuasVezesNaoDuplica` (uma linha em `banimento`); `ganchoQueFalhaDesfazTudo` (conta segue ATIVA, sessões intactas); `streamDoBanidoEFechado` (o `SseEmitter` completa).
  - `VarreduraDeContasInativasTest`: `varreduraEncerraEncontroDeContaBanidaSeOEfeitoSePerdeu` (banir com o listener desligado por propriedade de teste; um `executar()` ⇒ encontro encerrado com `BANIMENTO`, o outro recebe `TERMINOU`); `segundaVarreduraNaoFazNada`.
  - `ContaIndisponivelApiTest`: suspensa ⇒ 403 com `situacao` `SUSPENSA`; banida ⇒ `BANIDA`; conta ATIVA nunca recebe `situacao`.
  - `ExclusaoApiTest` (Etapa 2) ganha `exclusaoZeraDetalheDasDenunciasFeitas`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde com `./mvnw -q verify` inteiro (os ganchos tocam quatro módulos; a ArchUnit confere que `moderacao` não importa classe interna deles). Commit `feat(moderacao): banimento derruba sessões e tira a conta da fila, das conexões e do encontro`.

### Task 4: Fila de denúncias com conteúdo limitado ao denunciado

**Arquivos:**
- Criar: `src/main/resources/db/migration/V120__denuncia_resolucao.sql`
- Criar: `moderacao/{Prioridade,PrazosDeModeracao,FilaDeDenuncias,DenunciaNaFila,DetalheDaDenuncia,ConteudoDenunciado,ConteudoParaRevisao,ItemRevisado}.java`, `admin/{DenunciasAdminController,ImagensAdminController}.java`
- Criar: `encontro/ConteudoDenunciadoDoEncontro.java`, `conexao/ConteudoDenunciadoDaConexao.java`
- Teste: `moderacao/{PrazosDeModeracaoTest,FilaDeDenunciasTest}.java`, `admin/DenunciasAdminApiTest.java`, `encontro/ConteudoDenunciadoDoEncontroTest.java`, `conexao/ConteudoDenunciadoDaConexaoTest.java`

**Esquema (V120):** em `denuncia`: troca o `check` de `situacao` para `('PENDENTE','CONFIRMADA','DESCARTADA','ENCERRADA')`; colunas `resolvida_em timestamptz`, `resolvida_por uuid references conta(id)`, `check ((situacao = 'PENDENTE') = (resolvida_em is null))`; índice parcial `on denuncia (criada_em) where situacao = 'PENDENTE'`.

**Interfaces:**
- Consome: `denuncia` (Etapa 5 V86), `MotivoDenuncia`, `TipoDeContextoDenuncia`, `ContextoDenuncia`; `web.texto.Links.segmentar` (Etapa 5); `Perfis.publico` (Etapa 3); `CatalogoDeCenarios`.
- Produz:
  - `public enum Prioridade { URGENTE, NORMAL }` com `static Prioridade de(MotivoDenuncia)`.
  - `PrazosDeModeracao` (bean, propriedades do SLA): `Instant prazo(MotivoDenuncia motivo, Instant criadaEm)`, `Instant prazoDaFoto(Instant enviadaEm)`, `boolean vencida(Instant prazo, Instant agora)` (vencida quando `agora` é **depois** do prazo).
  - `public interface ConteudoDenunciado { Set<TipoDeContextoDenuncia> tipos(); ConteudoParaRevisao carregar(ContextoDenuncia contexto, ContaId denunciado); Optional<Path> arquivoDeImagem(UUID imagemId, String versao); }` — `versao` em `borrada|nitida`. `ConteudoDenunciadoDoEncontro` cobre `ENCONTRO`, `MENSAGEM_ENCONTRO`, `IMAGEM_ENCONTRO`; `ConteudoDenunciadoDaConexao` cobre `CONEXAO`, `MENSAGEM_CONEXAO`. Item: só se o autor for o denunciado; contexto inteiro: as últimas 20 mensagens do denunciado (P2). Nada encontrado ⇒ `ConteudoParaRevisao.indisponivel()`.
  - `record ItemRevisado(UUID id, String tipo /* TEXTO|IMAGEM */, List<Segmento> segmentos, Optional<UUID> imagemId, Instant criadoEm)`; `record ConteudoParaRevisao(boolean disponivel, String contexto /* rótulo: "Mensagem no encontro" etc. */, Optional<String> cenario, List<ItemRevisado> itens)`.
  - `FilaDeDenuncias`: `List<DenunciaNaFila> pendentes(int pagina)` (50 por página, ordem: `URGENTE` antes, depois `criada_em`, depois `id`); `Optional<DetalheDaDenuncia> detalhe(UUID id)`.
  - `record DenunciaNaFila(UUID id, MotivoDenuncia motivo, Prioridade prioridade, String denunciadoApelido, Instant criadaEm, Instant prazo, boolean vencida, String contexto)`; `record DetalheDaDenuncia(UUID id, MotivoDenuncia motivo, Prioridade prioridade, Optional<String> detalhe, String denunciadoApelido, Optional<String> denunciadoFotoBorrada, String denuncianteApelido, String situacaoDoDenunciado /* ATIVA|SUSPENSA|BANIDA|EXCLUIDA */, int outrasPendentesContraODenunciado, Instant criadaEm, Instant prazo, boolean vencida, ConteudoParaRevisao conteudo)`.
  - Rotas: `GET /api/admin/denuncias?pagina=0` (pagina ≥ 0, ≤ 100, senão 422); `GET /api/admin/denuncias/{id}` (404 `DENUNCIA_INEXISTENTE`); `GET /api/admin/denuncias/{id}/imagens/{imagemId}/{versao}` → `image/webp` com `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`; imagem que não pertence ao conteúdo daquela denúncia ⇒ 404.

- [ ] **Passo 1:** testes.
  - `PrazosDeModeracaoTest` (`@ParameterizedTest`): `PARECE_MENOR` criada 10:00 ⇒ prazo 22:00; `ASSEDIO` ⇒ +48 h; foto ⇒ +24 h; `vencida` falsa exatamente no prazo e verdadeira 1 s depois.
  - `FilaDeDenunciasTest` (`BancoDeTeste`): `filaPaginaDe50EmOrdemDePrioridade` (60 normais antigas + 3 urgentes novas ⇒ a página 0 começa pelas 3 urgentes e tem 50; a página 1 tem 13); `resolvidasNaoAparecem`; `consultaUsaOIndiceParcial` (`EXPLAIN` da consulta da fila menciona `denuncia_criada_em_idx` ou o nome dado na V120, com 5000 linhas e `ANALYZE`).
  - `ConteudoDenunciadoDoEncontroTest`: `itemDeOutroAutorEIndisponivel`; `encontroInteiroTrazSoAs20UltimasDoDenunciado` (25 do denunciado + 10 do denunciante ⇒ 20, todas do denunciado, da mais nova para a mais antiga); `linkVemSegmentadoComHostReal` (`https://banco.com@golpe.example/x` ⇒ segmento de link com host `golpe.example`); `imagemDenunciadaTrazImagemId`; `conteudoExpiradoMostraIndisponivel` (mensagens apagadas pela expiração ⇒ `disponivel` falso).
  - `ConteudoDenunciadoDaConexaoTest`: os mesmos três primeiros casos para `MENSAGEM_CONEXAO`/`CONEXAO`.
  - `DenunciasAdminApiTest`: `detalheNuncaTrazConteudoDeQuemDenunciou` (texto canário `CANARY-<uuid>` escrito pelo denunciante não aparece no corpo); `chavesDoDetalheSaoExatamenteAsDoRecord`; `detalheNaoTrazEmailNemNascimento` (canários de e-mail e data); `imagemDeOutraDenunciaRecebe404`; `cabecalhosDaImagemDoAdmin`; `paginaNegativaRecebe422`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar. A moderação descobre a implementação por `tipos()` (mapa montado no boot; tipo sem implementação ⇒ falha no boot).
- [ ] **Passo 4:** verde. Commit `feat(moderacao): fila de denúncias com conteúdo limitado ao denunciado`.

### Task 5: Decisões do admin (`Moderacao.executar`)

**Arquivos:**
- Criar: `moderacao/{Moderacao,AcaoDeModeracao,ResultadoDaAcao,Nota}.java`, `admin/{DecisoesAdminController,FotosAdminController}.java`
- Modificar: `encontro/` (método de pacote público `apagarImagemDenunciada(UUID imagemId)` exposto por `ConteudoDenunciadoDoEncontro`, arquivos apagados no `afterCommit`)
- Teste: `moderacao/{ModeracaoTest,NotaTest}.java`, `admin/{DecisoesAdminApiTest,FotosAdminApiTest}.java`

**Interfaces:**
- Consome: `Auditoria` (T2), `Banimentos` (T3), `FilaDeDenuncias`/`ConteudoDenunciado`/`PrazosDeModeracao` (T4), `FilaDeFotos.pendentes/aprovar/recusar` (Etapa 3 T5), `Contas.reativar/suspender`.
- Produz:
  - `record Nota(String valor)`: 1 a 500 code points, sem controle exceto `\n`; `static Optional<Nota> de(@Nullable String)` (nulo ou só espaços ⇒ vazio; inválida ⇒ `ErroDeNegocio` 422 `NOTA_INVALIDA`, `A nota pode ter até 500 caracteres.`).
  - `public sealed interface AcaoDeModeracao` com `record ConfirmarDenuncia(UUID denuncia, Optional<Nota> nota)`, `record DescartarDenuncia(UUID denuncia, Optional<Nota> nota)`, `record AprovarFoto(FotoId foto)`, `record RecusarFoto(FotoId foto, Optional<Nota> nota)`, `record RecusarFotoEBanir(FotoId foto, Optional<Nota> nota)`.
  - `public class Moderacao`: `@Transactional public ResultadoDaAcao executar(ContaId admin, AcaoDeModeracao acao)` com `switch` exaustivo:
    - Confirmar: `update denuncia set situacao = 'CONFIRMADA', resolvida_em, resolvida_por where id = ? and situacao = 'PENDENTE'` (0 linhas ⇒ 404 se não existe, 409 `DENUNCIA_JA_RESOLVIDA` se resolvida); `IMAGEM_ENCONTRO` ⇒ apaga a imagem (P4); `Banimentos.banir(denunciado, DENUNCIA_CONFIRMADA, ...)`; auditoria `DENUNCIA_CONFIRMADA` (+ `CONTA_BANIDA` se banir mudou o estado).
    - Descartar: mesma escrita condicional com `DESCARTADA`; `PARECE_MENOR` ⇒ `Contas.reativar` se não houver outra `PARECE_MENOR` pendente contra a conta (P5), com auditoria `CONTA_REATIVADA`; auditoria `DENUNCIA_DESCARTADA`.
    - Fotos: `FilaDeFotos.aprovar/recusar` + auditoria; `RecusarFotoEBanir` ⇒ recusa, `Banimentos.banir(titular, FOTO_SENSIVEL, ...)`, auditoria `FOTO_RECUSADA_E_BANIDA`.
  - `record ResultadoDaAcao(String situacao /* CONFIRMADA|DESCARTADA|APROVADA|RECUSADA */, boolean contaBanida, boolean contaReativada)`.
  - Rotas (corpo estrito `{ "nota"? }`, campo desconhecido ⇒ 400): `POST /api/admin/denuncias/{id}/confirmar`, `POST /api/admin/denuncias/{id}/descartar`, `GET /api/admin/fotos?pagina=0` (`FotoPendente` + `prazo` + `vencida`), `POST /api/admin/fotos/{id}/aprovar`, `/recusar`, `/recusar-e-banir`. Respostas 200 com `ResultadoDaAcao`.

- [ ] **Passo 1:** testes.
  - `NotaTest`: 500 code points (com emoji) passa; 501 falha; `\u0000` falha; `\n` passa; só espaços ⇒ vazio.
  - `ModeracaoTest` (`BancoDeTeste`): `confirmarImagemSensivelBaneEApagaAImagem` (arquivos somem depois do commit; `banimento.motivo` = `DENUNCIA_CONFIRMADA`; duas linhas de auditoria); `confirmarPareceMenorBaneSuspensa`; `descartarPareceMenorReativa`; `descartarPareceMenorComOutraPendenteNaoReativa`; `segundaPareceMenorNaoMudaNada` (já SUSPENSA; nada muda até a revisão); `descartarAssedioNaoMexeNaConta`; `confirmarResolvidaRecebe409ENaoAudita`; `decisoesSimultaneasTemUmVencedor` (duas threads confirmam a mesma ⇒ um 200, um 409, uma linha `DENUNCIA_CONFIRMADA`, um `banimento`); `denunciadoExcluidoAindaPodeSerDescartado`; `recusarFotoEBanirBaneOTitular`; `aprovarFotoAudita`.
  - `DecisoesAdminApiTest`: `corpoComCampoExtraRecebe400ENadaMuda` (`{"situacao":"CONFIRMADA"}` ⇒ 400, denúncia segue PENDENTE, zero auditoria); `contaComumRecebe403` em todas as rotas de decisão; `adminNaoPodeDecidirDenunciaContraSiMesmo` (⇒ 403 `ACESSO_NEGADO`, para não se desbanir nem se reativar).
  - `FotosAdminApiTest`: `listaComPrazoEVencida`; `recusarEBanirDerrubaSessaoDoTitular`.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): decisões do admin com sanção e auditoria na mesma transação`.

### Task 6: SLA, alerta da fila e métricas

**Arquivos:**
- Criar: `src/main/resources/db/migration/V123__alerta_da_fila.sql`
- Criar: `moderacao/{ResumoDaFila,EstadoDaFila,AlertaDaFila,MetricasDaFila}.java`, `admin/ResumoAdminController.java`
- Modificar: `pom.xml` (métricas do Micrometer: conferir pelo Context7 o artefato do Boot 4.2; se a Etapa 10 já trouxe o actuator, nada a fazer), `application.properties` (`duora.moderacao.alerta.cron=0 */15 * * * *`)
- Teste: `moderacao/{ResumoDaFilaTest,AlertaDaFilaTest,MetricasDaFilaTest}.java`

**Esquema (V123):** `alerta_fila(janela timestamptz primary key, motivo text not null check (motivo in ('URGENTE','VENCIDA')), enviado_em timestamptz not null)`.

**Interfaces:**
- Consome: `FilaDeDenuncias`, `PrazosDeModeracao` (T4), `FilaDeFotos` (Etapa 3), `EnvioDeEmail` (Etapa 2 T4), `Administradores` (T1), `MeterRegistry`.
- Produz:
  - `ResumoDaFila.agora() -> EstadoDaFila`; `record EstadoDaFila(int urgentesPendentes, int normaisPendentes, int fotosPendentes, int vencidas, Optional<Instant> maisAntigaPendente, Optional<Instant> proximoPrazo)` (uma consulta agregada por tabela, sem carregar linhas).
  - `GET /api/admin/resumo` → `EstadoDaFila`.
  - `AlertaDaFila` (`@Scheduled(cron)`): se há urgente pendente criada depois do último alerta `URGENTE`, ou vencida sem alerta `VENCIDA` nas últimas 6 h, insere `alerta_fila` (janela = início do quarto de hora; `on conflict do nothing` ⇒ não envia) e **depois do commit** envia um e-mail a cada admin. Assunto `Duora: denúncias aguardando análise`; corpo só com contagens: `Há {u} denúncias urgentes e {v} vencidas na fila. Abra {url-publica}/admin para analisar.` Sem apelido, texto ou id.
  - `MetricasDaFila`: gauges `duora.moderacao.pendentes{prioridade=urgente|normal}`, `duora.moderacao.fotos.pendentes`, `duora.moderacao.vencidas`, `duora.moderacao.idade.maxima.segundos`, lidos de `ResumoDaFila` com cache de 30 s (não consultam o banco a cada coleta).

- [ ] **Passo 1:** testes.
  - `ResumoDaFilaTest`: contagens com 2 urgentes, 3 normais, 1 vencida, 4 fotos; fila vazia ⇒ zeros e vazios.
  - `AlertaDaFilaTest` (`EnvioDeEmail` falso): `urgenteNovaDisparaUmEmailPorAdmin`; `segundaExecucaoNaMesmaJanelaNaoReenvia`; `vencidaRealertaSoDepoisDe6h` (5 h 59 min não, 6 h sim); `semPendenciaNaoEnvia`; `corpoSoTemContagens` (nem apelido canário nem id).
  - `MetricasDaFilaTest`: os quatro gauges com os valores do resumo; duas coletas em 10 s ⇒ uma consulta.
- [ ] **Passo 2:** RED.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** verde. Commit `feat(moderacao): SLA da fila com alerta por e-mail e métricas`.

### Task 7: Painel admin em React (rotas lazy) e "Denunciar link"

**Arquivos:**
- Criar: `frontend/src/admin/{rotas.tsx,api.ts,LayoutAdmin.tsx,ResumoTela.tsx,DenunciasTela.tsx,DenunciaTela.tsx,ConteudoRevisado.tsx,FotosTela.tsx,AuditoriaTela.tsx,DialogoDeDecisao.tsx}`
- Criar: `frontend/src/conta/ContaIndisponivelTela.tsx`
- Modificar: o roteador da Etapa 2 (registra `/admin/*` com `lazy: () => import('./admin/rotas')`), o tratamento global de 403 com `situacao` (vai para `/conta/indisponivel`), `frontend/src/encontro/AvisoDeSaida.tsx` (Etapa 5: botão "Denunciar link")
- Teste: `frontend/src/admin/{DenunciasTela,DenunciaTela,ConteudoRevisado,FotosTela}.test.tsx`, `frontend/src/conta/ContaIndisponivelTela.test.tsx`, `frontend/src/encontro/AvisoDeSaida.test.tsx` (caso novo)

**Interfaces:**
- Consome: rotas das Tasks 1–6; `useQuery`/`useMutation` (TanStack Query); `Segmento` (Etapa 5).
- Produz:
  - Rotas: `/admin` (`ResumoTela`: contagens, "Mais antiga há {n} h", vencidas em destaque), `/admin/denuncias`, `/admin/denuncias/:id`, `/admin/fotos`, `/admin/auditoria`. `LayoutAdmin` chama `GET /api/admin/eu`; 403 ⇒ "Você não tem acesso a esta área." com link para o início; `REAUTENTICACAO_NECESSARIA` ⇒ vai para `/entrar`. A Etapa 11 acrescenta `/admin/metricas` e `/admin/piloto` em `rotas.tsx`.
  - `DenunciasTela`: tabela com motivo, prioridade, apelido do denunciado, "há {n} h", prazo e selo "Vencida"; estado vazio "Nenhuma denúncia aguardando análise."; paginação "Mais antigas".
  - `DenunciaTela`: motivo, detalhe, apelido e foto borrada do denunciado, situação da conta, "Outras denúncias pendentes contra esta conta: {n}", `ConteudoRevisado`, e os botões "Confirmar e banir" e "Descartar". `DialogoDeDecisao` pede confirmação com nota opcional: "Confirmar a denúncia bane {apelido} na hora: as sessões caem, as conexões são desfeitas e o e-mail não pode criar outra conta." / "Descartar a denúncia?" (com `PARECE_MENOR`: "A conta volta a funcionar se não houver outra denúncia desse tipo."). 409 ⇒ "Outra pessoa já decidiu esta denúncia." e recarrega.
  - `ConteudoRevisado`: link como `<span>` com o host em `<strong>` e o resto do URL em texto, **sem `<a>`**; imagem borrada com o botão "Ver imagem" (troca para a nítida da rota admin); indisponível ⇒ "Conteúdo indisponível (expirou ou foi apagado)."
  - `FotosTela`: grade com a foto nítida da rota da Etapa 3 para admin, prazo e os botões "Aprovar", "Recusar" e "Recusar e banir".
  - `ContaIndisponivelTela`: texto exato de suspensa ou banida (restrições globais), com o contato de `GET /api/legal/versoes` ou da configuração; sem botão que leve de volta a uma rota protegida.
  - `AvisoDeSaida`: botão "Denunciar link" abre o `DenunciarDialogo` da Etapa 5 com o contexto da mensagem e o motivo "Spam ou golpe" marcado (P7).
- [ ] **Passo 1:** testes Vitest (fetch falso): `filaVaziaMostraTextoHonesto`; `vencidaTemSelo`; `linkNoPainelNaoEClicavel` (nenhum `a[href]` dentro de `ConteudoRevisado`; o host aparece em `<strong>`); `confirmarPedeConfirmacaoComTextoExato`; `conflitoMostraMensagemERecarrega`; `naoAdminVeTextoDeAcesso`; `suspensaEBanidaTemTextosDiferentes`; `avisoDeSaidaDenunciaComSpamMarcado`.
- [ ] **Passo 2:** `pnpm --dir frontend exec vitest run src/admin src/conta src/encontro/AvisoDeSaida.test.tsx`; falha.
- [ ] **Passo 3:** implementar. Conferir no build que o chunk de `admin/` não entra no JS inicial (o relatório do Vite lista `admin` em chunk próprio; o JS inicial continua dentro da meta da 1a).
- [ ] **Passo 4:** verde. Commit `feat(admin): painel de moderação com rotas lazy e denúncia de link`.

### Task 8: Prova ponta a ponta e acessibilidade

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/e2e/{ModeracaoIT,AdminPagina}.java`
- Modificar: `e2e/AxeIT.java`, `.claude/handoff/_mapa.md` (`moderacao/`, `admin/`, comandos)

**Interfaces:**
- Consome: `Participantes.novoComConta`, `ContasDeTeste.criarAdmin` (T1), `EncontroPagina` (Etapa 5), `ConexoesPagina` (Etapa 6), `EmailNoLog`.
- Produz: `AdminPagina` com `abrirFila()`, `abrirDenuncia(String apelido)`, `confirmarEBanir()`, `descartar()`, `textoDoConteudo()`, `abrirFotos()`, `aprovarFoto(String apelido)`.
- [ ] **Passo 1:** escrever os IT.
  - `ModeracaoIT.denunciaAnaliseBanimento` (prova da etapa): Ana e Bia pareadas; as duas permitem imagens; Bia envia a imagem de teste; Ana denuncia como "Imagem sensível"; a imagem some para Ana; a admin (terceiro contexto) vê a denúncia urgente na fila, abre, vê a imagem borrada e "Ver imagem" mostra a nítida; confirma e bane; em até 3 s o contexto de Bia mostra o texto de banida; o contexto de Ana vê "O encontro terminou."; um `POST /api/fila` de Bia pelo `APIRequestContext` ⇒ 403; a auditoria lista "Denúncia confirmada" e "Conta banida".
  - `ModeracaoIT.pareceMenorSuspendeERevisaoReativa`: Ana denuncia Bia como "Parece menor de idade"; Bia vê o texto da suspensão na hora; a admin descarta; Bia entra de novo pelo link e volta à fila.
  - `ModeracaoIT.suspensaVeOTextoAoEntrar` (Foco 5): suspensa abre `/` e depois `/entrar/confirmar#t=...` ⇒ texto da suspensão, sem laço (a URL estabiliza em até 2 s).
  - `ModeracaoIT.denunciaDeLinkChegaComHostReal`: Bia manda `https://banco.com@golpe.example/x`; Ana clica, vê o aviso de saída e "Denunciar link"; a admin vê `golpe.example` em destaque e nenhum link clicável.
  - `AxeIT`: `painelAdminResumoSemViolacoes`, `filaDeDenunciasSemViolacoes`, `detalheDaDenunciaSemViolacoes`, `filaDeFotosSemViolacoes`, `contaIndisponivelSemViolacoes`.
- [ ] **Passo 2:** `./mvnw -q -Pe2e verify -Dit.test='ModeracaoIT,AxeIT'` em chromium, depois `-Dnavegador=firefox` e `webkit`. Verde, sem skip.
- [ ] **Passo 3:** prova no Android (`scripts/android/`): a admin no emulador abre `/admin/denuncias` em retrato; captura em `target/android/admin-fila.png`; a tabela não rola na horizontal (largura do documento ≤ largura da janela).
- [ ] **Passo 4:** atualizar `_mapa.md`. Commit `test(e2e): denúncia, análise e banimento de ponta a ponta`.

## Ordem e paralelismo
| Leva | Tasks (worktrees separados) | Depende de | Flyway |
|---|---|---|---|
| 1 | T1 ∥ T2 | Etapa 5 T7 | T2: V121 |
| 2 | T3 ∥ T4 | T2 (T3 audita pela T5, mas não chama `Auditoria`); Etapa 6 para as partes de conexão | T3: V122; T4: V120 |
| 3 | T5 | T2, T3, T4 | — |
| 4 | T6 ∥ T7 | T4, T5 (T7 roda contra as Interfaces com `fetch` falso desde a Leva 2) | T6: V123 |
| 5 | T8 | todas | — |

- V120 é da T4 e V122 da T3: na Leva 2, quem terminar primeiro faz merge primeiro; se a T3 (V122) chegar antes da T4 (V120), a T4 renumera para V124 antes do merge (sem out-of-order).
- No máximo 4 agentes ao mesmo tempo, contando outras etapas em paralelo (a 8 corre junto da 5 e da 6). A T3 toca arquivos de `conexao/`, `sala/`, `notificacao/` e `web/eventos/`: não rodar junto de uma task da Etapa 6 que edite os mesmos arquivos.
- Revisão final da etapa com `superpowers:requesting-code-review`, foco em segurança (o STRIDE acima) e concorrência (T5, T3).

## Adições ao contrato (propostas, não aplicadas)
- Nomes: `GLOSSARY.md` na raiz com os termos de moderação acima (skill `domain-modeling`), mais os de encontro e conexão.
- Pacotes: `admin/` passa a ter o interceptor `ExigeAdmin` e a convenção `/api/admin/**`; `AoBanirConta` entra na lista de ganchos, ao lado de `AoExcluirConta`.
- Eventos SSE: `moderacao.conta` `{ "situacao" }` é o único evento de moderação.
- Retenção: a Etapa 9 deve citar que denúncias e auditoria ficam enquanto o serviço existir (são registro de segurança) e que a imagem confirmada como sensível é apagada na hora.
