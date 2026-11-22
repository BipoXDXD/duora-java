# Etapa 3 — Perfil e preferências: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** cada conta tem perfil (apelido, intenção, bio, foto aprovada), preferências de pareamento, dados de namoro com consentimento e uma célula de localização; o perfil aparece borrado para terceiros e nítido só para quem é dono dele.

> **Emenda 2026-11-22 (paridade com o mercado):** a Task 9 (nova) acrescenta orientação e pronomes granulares com "prefiro descrever", o selo "verificado" e a pausa da conta sem apagá-la. (Ajuste do dono, 2026-11-22: a preferência "só encontros com verificados" saiu, porque a verificação é obrigatória para ATIVA e o filtro não teria efeito.)

**Arquitetura:** o pacote `perfil/` segue as camadas da Etapa 2. A raiz é a API pública e os casos de uso (`Perfis`, projeções, `Localizacoes`, `FilaDeFotos`, `AcessoAFoto`). `perfil/dominio/` é Java puro: tipos que nascem válidos (`Apelido`, `Bio`, `FaixaEtaria`, `DadosDeNamoro`, `CelulaGeohash`), o agregado `Perfil` e as portas de repositório. `perfil/web/` tem os controllers, e `perfil/infra/` os adapters JDBC: Spring Data JDBC com `@Version` para o agregado e `JdbcClient` com `UPDATE` condicional para as partes satélites, todas chaveadas por `conta_id`. A porta `ProcessadorDeImagem` (adapter libvips por processo) e o `ArmazemDeImagens` ficam em `web/`, porque a Etapa 5 reusa os dois para as imagens do chat. Outras etapas leem perfis só pela interface `Perfis`, que devolve projeções prontas (`PerfilPublico`, `PerfilCompleto`, `Preferencias`, `CelulaGeohash`), nunca as tabelas. Os nomes públicos seguem o que o plano da Etapa 4 (`2026-11-22-etapa-4-salas-pareamento.md`) já consome. A decisão de privacidade por projeção (célula, foto borrada, namoro isolado) vira ADR na Task 6.

**Stack:** Java 25, Spring Boot 4.2.0 (webmvc, data-jdbc, multipart), PostgreSQL 18, libvips ≥ 8.15 (CLI `vips`/`vipsheader`), React 19 + TypeScript + Tailwind 4, Vitest, Playwright Java.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 3; "Escolhas minhas": localização, foto, foto pendente, sem imagens sensíveis). **Contratos e princípios de engenharia:** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. Conferir pelo Context7 ou por `vips <operação>` sem argumentos: flags de `thumbnail`, `resize`, `gaussblur`, `webpsave` (`Q`, `keep`), e Spring Data JDBC (`@Version`, `Persistable`).

**Pré-requisitos:** Etapa 2 concluída (`ContaId`, `@ContaAtual`, `ConsultaDeContas`, `AoExcluirConta`, `LimiteDeTaxa`, `Idempotencia`, `GeradorDeIds`, `ErroDeNegocio`, `ArquiteturaTest`, `BancoDeTeste`, `ContaPagina`) e Etapa 9 Task 2 (`PoliticaDeRetencao`). libvips instalado: no Mac, o agente roda `brew install vips`; na CI, a Task 4 acrescenta `sudo apt-get install -y libvips-tools` aos dois jobs.

## Restrições globais (Global Constraints)
- Tudo do arquivo de contratos e das restrições globais da Etapa 2 vale aqui.
- **Flyway:** esta etapa usa só V20–V39, com números fixos por task (ver "Ordem e paralelismo"). Todas as tabelas referenciam `conta(id)` com `on delete cascade` e índice na FK quando ela não é a PK.
- **Apelido:** 1 a 20 caracteres após o `strip`, sem caractere de controle nem marcas de direção (mesma regra de `room/Apelido`, reescrita em `perfil/Apelido` sem tocar em `room/`).
- **Bio:** 0 a 280 caracteres, quebra de linha permitida, outros caracteres de controle proibidos.
- **Intenção:** conjunto não vazio de `AMIZADE` e `NAMORO` (os dois = "ambas"), gravado como duas colunas booleanas.
- **Preferências:** faixa etária de 18 a 99 com mínima ≤ máxima (padrão 18–99); raio `ATE_5_KM`, `ATE_20_KM`, `ATE_50_KM` ou `SEM_LIMITE` (padrão `SEM_LIMITE`); `raioObrigatorio` (padrão `false`). Raio diferente de `SEM_LIMITE` exige localização consentida.
- **Namoro:** gênero próprio e gêneros de interesse da mesma lista; `OUTRO` ("Prefiro descrever") exige descrição de 1 a 40 caracteres e é escolhível também como interesse. Só grava com `NAMORO` entre as intenções **e** consentimento marcado. Esses dados nunca aparecem para outra pessoa (só alimentam o pareamento) e são apagados quando `NAMORO` sai das intenções ou quando o consentimento é revogado.
- **Localização:** só com consentimento explícito. O servidor converte a coordenada em geohash de precisão 5 e descarta a coordenada (nunca grava nem loga). A interface mostra faixas (`até 5 km`, `até 20 km`, `até 50 km`, `mais de 50 km`), nunca a distância exata nem a célula.
- **Foto:**
  - upload de até 5 MB, só JPEG, PNG ou WebP (pelos bytes mágicos, não pelo nome nem pelo `Content-Type`); até 40 megapixels e 12 000 px por lado;
  - o servidor gera a nítida (WebP q88, sem metadados, maior lado ≤ 1600 px, orientação EXIF aplicada) e a borrada (WebP q88, sem metadados, maior lado 400 px, derivada de uma redução a 32 px, então irreversível);
  - toda foto nova entra como pendente e espera aprovação. Pendente não bloqueia nada: no lugar dela aparece uma silhueta. Uma foto aprovada antes continua valendo até a nova ser aprovada.
  - A nítida só vai para o titular (e, a partir da Etapa 6, para conexões mútuas). Terceiros recebem só a borrada de uma foto aprovada. Sem permissão, a resposta é 404, igual à de foto inexistente.
- **Política de imagens (texto exato na tela de upload):** `Sem nudez, sexo, violência explícita ou conteúdo chocante. Toda foto passa por aprovação antes de aparecer.`
- **Concorrência de edição:** `PUT /api/perfil` e `PUT /api/perfil/preferencias` levam `versao` no corpo; versão velha → 409 `VERSAO_OBSOLETA` (contrato: 409 para versão).
- **Códigos novos** (`perfil/CodigoPerfil`): `APELIDO_INVALIDO` 422, `BIO_INVALIDA` 422, `INTENCAO_INVALIDA` 422, `FAIXA_ETARIA_INVALIDA` 422, `LOCALIZACAO_NECESSARIA` 422, `LOCALIZACAO_SEM_CONSENTIMENTO` 422, `COORDENADA_INVALIDA` 422, `INTENCAO_SEM_NAMORO` 422, `CONSENTIMENTO_NECESSARIO` 422, `GENERO_INVALIDO` 422, `VERSAO_OBSOLETA` 409, `FOTO_INEXISTENTE` 404, `PERFIL_INEXISTENTE` 404. Em `web/CodigoWeb`: `IMAGEM_GRANDE_DEMAIS` 413, `FORMATO_NAO_SUPORTADO` 415, `IMAGEM_INVALIDA` 422, `PROCESSAMENTO_INDISPONIVEL` 503.
- **Limite de taxa novo:** `foto-conta`, 10 por `PT1H`, chave `ContaId`.

- **Camadas e fronteiras:** as da Etapa 2, travadas pelo `ArquiteturaTest`. `perfil/dominio/` não importa Spring web, JDBC, Spring Data nem Jackson; outros módulos só usam a raiz de `perfil`. Sem Observer para efeito único: quem muda as intenções chama direto o apagamento do namoro, e quem revoga a localização chama direto a volta do raio.
- **Idempotência:** `POST /api/perfil/foto` aceita `Idempotency-Key` (escopo `foto:<contaId>`, impressão = SHA-256 dos bytes do arquivo).
- **Acúmulos com limpeza e métrica:** temporários de imagem (`imagens-temporarias`, `PT1H`, Task 4).

## Skills por momento
- Antes da Task 1: `mattpocock-skills:domain-modeling` sobre Perfil (agregado, o que é satélite, invariantes de intenção e namoro).
- Antes da Task 6: `mattpocock-skills:codebase-design` com duas alternativas para `Perfis` e `AcessoAFoto`. Uma mudança de interface pública atualiza este plano e o da Etapa 4 antes de codar.
- Execução com `superpowers:test-driven-development` ou `mattpocock-skills:tdd`. Revisão com `superpowers:requesting-code-review` e `mattpocock-skills:code-review`, citando smells pelo nome.
- Bug: `superpowers:systematic-debugging`. Dúvida de flag do libvips: `mattpocock-skills:research` + Context7.
- Antes de dizer "pronto": `superpowers:verification-before-completion`, depois `duora-etapa`.

## Ordem e paralelismo
| Onda | Tasks (worktrees separados) | Depende de | Flyway |
|---|---|---|---|
| A | T1 ∥ T3 ∥ T4 | Etapa 2 | T1: `V20__perfil.sql`; T3: `V22__localizacao.sql`; T4: nenhuma |
| B | T2 ∥ T5 | T1 e T3 (T2); T1 e T4 (T5) | T2: `V21__preferencias_e_namoro.sql`; T5: `V23__fotos.sql` |
| C | T6 | T2, T5 | `V24` reservado (sem migração prevista) |
| D | T7 ∥ T8 | T6 (T7); T2, T3, T6 (T8) | — |
| E | T9 | T6, T8 e Etapa 2 T12 (`ContaResumo.verificada`) | `V25__identidade_e_pausa.sql` |

V25 é da Task 9; V26–V39 ficam reservados para correções. T7 e T8 tocam o roteador do front; quem fizer o merge depois resolve o conflito de uma linha. No máximo 4 agentes ao mesmo tempo.

## Decisões do dono
- **D1. Lista de gêneros** (spec: "lista inclusiva mais 'prefiro descrever'"). Os valores ficam num `check` do banco; mudar depois custa uma migração.
  - (a) **Proposta:** Mulher, Homem, Mulher trans, Homem trans, Pessoa não binária, Travesti, Agênero, Gênero fluido, Prefiro descrever. "Prefiro descrever" pede um texto curto e também aparece como opção de interesse ("pessoas que se descrevem de outra forma"), então o pareamento continua sendo uma checagem simples nos dois sentidos.
  - (b) Lista curta: Mulher, Homem, Pessoa não binária, Prefiro descrever. Mais simples, menos inclusiva.
  - **Recomendo (a).** Não bloqueia: o plano segue com (a).

## Foco de revisão (Review Focus)
1. **Foto do celular em pé com orientação EXIF 6:** a nítida sai em pé e sem EXIF. Coberto por `orientacaoExifAplicada` (Task 4).
2. **Bomba de descompressão** (arquivo pequeno de 30 000 × 30 000): recusada antes de decodificar. Coberto por `dimensaoGiganteRecusada` (Task 4).
3. **Desligar e religar o namoro:** tirar e recolocar `NAMORO` nas intenções não traz de volta os dados antigos; é preciso consentir de novo. Coberto por `religarNamoroExigeNovoConsentimento` (Task 2).
4. **Permissão de localização negada ou sem resposta no navegador:** a tela explica e o raio volta a "Sem limite", sem travar o formulário. Coberto por `localizacaoNegadaVoltaParaSemLimite` (Task 8).
5. **Mesmo perfil editado em dois aparelhos:** o segundo salvamento recebe 409 e a tela recarrega os dados atuais em vez de sobrescrever. Coberto por `edicaoConcorrenteRecusada` (Task 1) e `versaoObsoletaRecarrega` (Task 7).

---

### Task 1: Perfil básico (apelido, intenção, bio)

**Arquivos:**
- Criar: `db/migration/V20__perfil.sql`
- Criar: raiz `perfil/{Intencao,CodigoPerfil,ServicoDePerfil,PerfilAoExcluirConta}.java`; domínio `perfil/dominio/{Apelido,Bio,Perfil,RepositorioDePerfis}.java`; web `perfil/web/PerfilController.java`; infra `perfil/infra/{PerfilRegistro,PerfilRegistroRepositorio,PerfisJdbc}.java`
- Teste: `perfil/{ApelidoTest,BioTest,PerfilApiTest}.java`

**Esquema:** `perfil(conta_id uuid pk references conta(id) on delete cascade, apelido text not null check (char_length(apelido) between 1 and 20), quer_amizade boolean not null, quer_namoro boolean not null, check (quer_amizade or quer_namoro), bio text not null default '' check (char_length(bio) <= 280), versao bigint not null, atualizado_em timestamptz not null)`.

**Interfaces (produz):**
- `public enum Intencao { AMIZADE, NAMORO }`; o perfil guarda `Set<Intencao>` (não vazio, senão 422 `INTENCAO_INVALIDA`).
- `perfil.dominio.Perfil` (record puro: conta, apelido, intenções, bio, versão) e a porta `RepositorioDePerfis { Optional<Perfil> buscar(ContaId); Perfil salvar(Perfil perfil, @Nullable Long versaoEsperada); }`. O adapter usa `PerfilRegistro` (Spring Data JDBC, `Persistable<UUID>`, novo quando a versão é nula, `@Version Long versao`) e traduz `OptimisticLockingFailureException` em `VERSAO_OBSOLETA`.
- `GET /api/perfil` (`@ContaAtual`) → `{"versao": null|n, "apelido": null|"...", "intencoes": [] | ["AMIZADE","NAMORO"], "bio": "", "foto": {...}}`. O campo `foto` entra na Task 5; até lá, `null`.
- `PUT /api/perfil` `{ "versao", "apelido", "intencoes", "bio" }`: `versao` nulo cria o perfil (se já existir → 409); versão diferente da atual → 409 `VERSAO_OBSOLETA`. Resposta: o mesmo corpo do GET.
- `PerfilAoExcluirConta implements AoExcluirConta` (apaga o perfil).

- [ ] **Passo 1:** testes.
  - `ApelidoTest` / `BioTest` `@ParameterizedTest`: apelido `""`, `"   "`, 21 caracteres, `"a‮b"` → `APELIDO_INVALIDO`; 20 caracteres (contando emoji como um) aceito. Bio com 281 → `BIO_INVALIDA`; 280 aceita; `"a\nb"` aceita; `"a\u0007"` recusada.
  - `PerfilApiTest`: `perfilNovoComecaVazio` (versão nula); `criarEDepoisEditar` (versão sobe); `edicaoConcorrenteRecusada` (dois PUT com a mesma versão → o segundo 409, banco com o valor do primeiro); `campoExtraRecusado` (`"contaId"` no corpo → 400, nada gravado); `semSessao401`; `intencoesVaziasRecusadas` (422 `INTENCAO_INVALIDA`); `intencoesGravadasComoConjunto`; `exclusaoDaContaApagaPerfil`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. `OptimisticLockingFailureException` vira `VERSAO_OBSOLETA`.
- [ ] **Passo 4:** testes verdes. Commit `feat(perfil): apelido, intenção e bio com controle de versão`.

### Task 2: Preferências e dados de namoro

**Arquivos:**
- Criar: `db/migration/V21__preferencias_e_namoro.sql`
- Criar: raiz `perfil/{FaixaEtaria,Raio,Genero,ServicoDePreferencias}.java`; domínio `perfil/dominio/{AjustesDeBusca,DadosDeNamoro,RepositorioDeAjustes,RepositorioDeNamoro}.java`; web `perfil/web/PreferenciasController.java`; infra `perfil/infra/{AjustesJdbc,NamoroJdbc}.java`
- Modificar: `perfil/ServicoDePerfil.java` (ao tirar `NAMORO` das intenções, chama `ServicoDePreferencias.apagarNamoro` na mesma transação), `perfil/Localizacoes.java` (`revogar` chama `ServicoDePreferencias.voltarParaSemLimite`)
- Teste: `perfil/{FaixaEtariaTest,DadosDeNamoroTest,PreferenciasApiTest,NamoroApiTest}.java`

**Esquema:**
- `preferencias(conta_id uuid pk references conta(id) on delete cascade, idade_minima smallint not null check (idade_minima between 18 and 99), idade_maxima smallint not null check (idade_maxima between 18 and 99), raio text not null check (raio in ('ATE_5_KM','ATE_20_KM','ATE_50_KM','SEM_LIMITE')), raio_obrigatorio boolean not null, versao bigint not null, check (idade_minima <= idade_maxima))`.
- `namoro(conta_id uuid pk references conta(id) on delete cascade, genero text not null check (genero in (<lista D1>)), descricao_genero text check (char_length(descricao_genero) between 1 and 40), consentido_em timestamptz not null, check ((genero = 'OUTRO') = (descricao_genero is not null)))`.
- `namoro_interesse(conta_id uuid not null references namoro(conta_id) on delete cascade, genero text not null check (genero in (<lista D1>)), primary key (conta_id, genero))`.

**Interfaces:**
- Consome: `Intencao`, `ServicoDePerfil` (T1); `Localizacoes.consentida(ContaId)`, `Localizacoes.revogar` (T3).
- Produz:
  - `public record FaixaEtaria(int minima, int maxima)` (18 ≤ mínima ≤ máxima ≤ 99, senão `FAIXA_ETARIA_INVALIDA`; `boolean contem(int idade)`).
  - `public enum Raio { ATE_5_KM, ATE_20_KM, ATE_50_KM, SEM_LIMITE; public OptionalInt km(); }`
  - `public record AjustesDeBusca(FaixaEtaria faixa, Raio raio, boolean raioObrigatorio)` com `static AjustesDeBusca PADRAO` (18–99, `SEM_LIMITE`, `false`). É o que a tela edita; o pareamento lê a projeção `Preferencias` da Task 6.
  - `public enum Genero { MULHER, HOMEM, MULHER_TRANS, HOMEM_TRANS, PESSOA_NAO_BINARIA, TRAVESTI, AGENERO, GENERO_FLUIDO, OUTRO }` (enum, então `equals` por valor)
  - `public record DadosDeNamoro(Genero genero, Optional<String> descricao, Set<Genero> interesses)` (interesses não vazio).
  - `GET /api/perfil/preferencias` → `{versao, idadeMinima, idadeMaxima, raio, raioObrigatorio}` (padrões quando não há linha, `versao` nulo).
  - `PUT /api/perfil/preferencias` (mesmo corpo): raio ≠ `SEM_LIMITE` sem localização consentida → 422 `LOCALIZACAO_NECESSARIA` (`Ative a localização para usar um raio.`); com `SEM_LIMITE`, `raioObrigatorio` é gravado como `false`.
  - `GET /api/perfil/namoro` → `{genero, descricaoGenero, interesses, consentidoEm}` ou 404 `PERFIL_INEXISTENTE` quando não há dados.
  - `PUT /api/perfil/namoro` `{genero, descricaoGenero, interesses, consentimento}`: intenções sem `NAMORO` → 422 `INTENCAO_SEM_NAMORO`; `consentimento` ≠ `true` → 422 `CONSENTIMENTO_NECESSARIO`; descrição fora da regra → 422 `GENERO_INVALIDO`. Substitui tudo e grava `consentido_em`.
  - `DELETE /api/perfil/namoro` → revoga: apaga os dados e troca as intenções por `{AMIZADE}`. 204.
  - `ServicoDePreferencias.apagarNamoro(ContaId)` e `voltarParaSemLimite(ContaId)` (raio `SEM_LIMITE`, `raioObrigatorio` `false`, por `UPDATE` condicional), chamados direto pela T1 e pela T3.
  - Gravação de ajustes por `UPDATE ... WHERE conta_id = :c AND versao = :v` (zero linhas → `VERSAO_OBSOLETA`), sem read-modify-write.

- [ ] **Passo 1:** testes.
  - `FaixaEtariaTest`: (18, 99) ok; (17, 30), (30, 29), (18, 100) recusadas; (25, 25) ok; `contem` em 24, 25 e 26 com a faixa (25, 30).
  - `DadosDeNamoroTest`: `OUTRO` sem descrição, descrição com 41 caracteres e descrição com gênero ≠ `OUTRO` → `GENERO_INVALIDO`; interesses vazios recusados.
  - `PreferenciasApiTest`: `padroesSemLinha`; `raioSemLocalizacaoRecusado`; `raioComLocalizacaoAceito`; `semLimiteZeraObrigatorio`; `revogarLocalizacaoVoltaParaSemLimite`; `edicaoConcorrenteRecusada`.
  - `NamoroApiTest`: `gravaComConsentimento` (interesses no banco exatamente iguais aos enviados); `semConsentimentoRecusado`; `intencaoAmizadeRecusada`; `trocarParaAmizadeApagaDados` (zero linhas em `namoro` e `namoro_interesse`); `revogarApagaEMudaIntencao` (`{AMIZADE, NAMORO}` vira `{AMIZADE}`); `religarNamoroExigeNovoConsentimento` ({NAMORO} → {AMIZADE} → {NAMORO}: o GET responde 404 até um PUT novo com consentimento); `outraContaNaoLe` (o GET de B nunca devolve dados de A).
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** testes verdes. Commit `feat(perfil): preferências de pareamento e namoro com consentimento`.

### Task 3: Localização por célula

**Arquivos:**
- Criar: `db/migration/V22__localizacao.sql`
- Criar: raiz `perfil/{CelulaGeohash,FaixaDeDistancia,Localizacoes,LocalizacaoAoExcluirConta}.java`; domínio `perfil/dominio/RepositorioDeLocalizacao.java`; web `perfil/web/LocalizacaoController.java`; infra `perfil/infra/LocalizacaoJdbc.java`
- Teste: `perfil/{CelulaGeohashTest,FaixaDeDistanciaTest,LocalizacaoApiTest}.java`

**Esquema:** `localizacao(conta_id uuid pk references conta(id) on delete cascade, celula char(5) not null check (celula ~ '^[0-9b-hjkmnp-z]{5}$'), consentida_em timestamptz not null, atualizada_em timestamptz not null)`.

**Interfaces (produz):**
- `public record CelulaGeohash(String valor)` (precisão 5, validada pelo mesmo padrão do `check`): `public static CelulaGeohash de(double latitude, double longitude)`; `public double distanciaKm(CelulaGeohash outra)` (haversine entre os centros das células, raio da Terra 6371,0088 km); `public FaixaDeDistancia faixaAte(CelulaGeohash outra)`.
- `public enum FaixaDeDistancia { ATE_5_KM, ATE_20_KM, ATE_50_KM, MAIS_DE_50_KM; public static FaixaDeDistancia de(double km); }` (≤ 5, ≤ 20, ≤ 50, acima).
- `public class Localizacoes`: `public void consentirEAtualizar(ContaId conta, double latitude, double longitude)`; `public void atualizar(ContaId conta, double latitude, double longitude)` (exige consentimento prévio, senão `LOCALIZACAO_SEM_CONSENTIMENTO`; a Etapa 4 chama a cada entrada na fila); `public boolean consentida(ContaId conta)`; `public Optional<CelulaGeohash> celula(ContaId conta)`; `public void revogar(ContaId conta)` (apaga a linha; a T2 acrescenta a volta do raio).
- `PUT /api/perfil/localizacao` `{latitude, longitude}` → consente e grava; 204. Latitude fora de [-90, 90], longitude fora de [-180, 180] ou valor não finito → 422 `COORDENADA_INVALIDA`.
- `DELETE /api/perfil/localizacao` → revoga; 204.
- `GET /api/perfil/localizacao` → `{"consentida": bool, "atualizadaEm": ...}`, nunca a célula.

- [ ] **Passo 1:** testes.
  - `CelulaGeohashTest`: (-23.5505, -46.6333) → `6gyf4`; (-22.9068, -43.1729) → `75cm9`; (0, 0) → `s0000`; (90, 180) → `zzzzz`; (-90, -180) → `00000`. Distâncias entre células: `6gyf4`–`6gycf` ≈ 4,89 km; `6gyf4`–`6gyft` ≈ 16,62 km; `6gyf4`–`6gysh` ≈ 47,46 km; `6gyf4`–`6gxp9` ≈ 58,03 km (tolerância 0,05). Mesma célula → 0.
  - `FaixaDeDistanciaTest`: 5,0 → `ATE_5_KM`; 5,01 → `ATE_20_KM`; 20,0 → `ATE_20_KM`; 50,0 → `ATE_50_KM`; 50,01 → `MAIS_DE_50_KM`.
  - `LocalizacaoApiTest`: `gravaSoACelula` (a tabela tem só `6gyf4`; nenhuma coluna guarda a coordenada); `coordenadaNaoApareceNoLog` (latitude canária `-23.987654321` não aparece na saída capturada, em nenhuma forma truncada com 6 ou mais casas); `coordenadaInvalidaRecusada` (91, -181, `NaN` → 422; corpo `"latitude":"x"` → 400); `getNaoDevolveCelula` (as chaves do JSON são exatamente `consentida` e `atualizadaEm`); `atualizarSemConsentimentoRecusado`; `revogarApaga`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. O codificador de geohash é próprio (algoritmo público de ~30 linhas, sem dependência).
- [ ] **Passo 4:** testes verdes. Commit `feat(perfil): localização guardada só como célula geohash`.

### Task 4: Pipeline de imagens com libvips

**Arquivos:**
- Criar: `web/{ProcessadorDeImagem,ProcessadorDeImagemVips,ImagemProcessada,ArmazemDeImagens,Variante,ConfigDeImagens,RetencaoDeTemporarios}.java`
- Modificar: `web/TratamentoDeErros.java` (`MaxUploadSizeExceededException` → 413 `IMAGEM_GRANDE_DEMAIS`, `A imagem pode ter até 5 MB.`), `web/CodigoWeb.java`, `application.properties` (`duora.imagens.dir=${DUORA_IMAGENS_DIR:./dados/imagens}`, `spring.servlet.multipart.max-file-size=5MB`, `spring.servlet.multipart.max-request-size=6MB`), `.gitignore` (`/dados/`), `.github/workflows/ci.yml` (`sudo apt-get install -y libvips-tools` antes do Maven nos dois jobs)
- Criar: `src/test/resources/imagens/{retrato-exif-orientacao-6.jpg,paisagem.png,quadrado.webp,texto.txt}` (gerados uma vez e versionados, cada um com menos de 60 KB; o JPEG traz EXIF com GPS e `Orientation=6`) e `src/test/resources/imagens/README.md` com o comando que os gerou
- Teste: `web/{ProcessadorDeImagemVipsTest,ArmazemDeImagensTest,RetencaoDeTemporariosTest}.java`

**Interfaces (produz):**
- `public enum Variante { NITIDA, BORRADA }`
- `public record ImagemProcessada(Path nitida, Path borrada, int largura, int altura)` (arquivos temporários; quem chama move pelo armazém).
- `public interface ProcessadorDeImagem { ImagemProcessada processar(InputStream entrada); }` (porta do contrato); `ProcessadorDeImagemVips` é o adapter. Passos:
  1. copia para um temporário com nome UUID dentro de `<dir>/tmp`, contando os bytes (mais de 5 MB → `IMAGEM_GRANDE_DEMAIS`);
  2. confere os bytes mágicos (JPEG `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`; WebP `RIFF....WEBP`); outro formato → 415 `FORMATO_NAO_SUPORTADO` (`Envie uma foto em JPEG, PNG ou WebP.`);
  3. `vipsheader -f width` / `-f height`: falha → 422 `IMAGEM_INVALIDA`; mais de 12 000 px num lado ou mais de 40 000 000 px → 422 `IMAGEM_INVALIDA`;
  4. nítida: `vips thumbnail <in> <out>.webp[Q=88,keep=none] 1600 --size down`;
  5. borrada: `vips thumbnail <in> <t1>.v 32`, depois `vips resize <t1>.v <t2>.v 12.5 --kernel linear`, depois `vips gaussblur <t2>.v <out>.webp[Q=88,keep=none] 6`.
  - Cada comando roda por `ProcessBuilder` com lista de argumentos (sem shell), timeout de 20 s (estourou → mata o processo e responde 503 `PROCESSAMENTO_INDISPONIVEL`) e no máximo 2 processamentos simultâneos (`Semaphore`; fila cheia por mais de 10 s → 503). Os temporários intermediários são apagados no `finally`.
- `public class ArmazemDeImagens`: `public void guardar(String categoria, UUID id, ImagemProcessada imagem)` (move atômico para `<dir>/<categoria>/<2 primeiros hex do id>/<id>-nitida.webp` e `-borrada.webp`); `public Optional<Path> caminho(String categoria, UUID id, Variante variante)`; `public void apagar(String categoria, UUID id)`. A categoria segue `^[a-z]+$`. A Etapa 5 usa a categoria `encontro`.

- [ ] **Passo 1:** testes (`vipsheader` lê as saídas).
  - `ProcessadorDeImagemVipsTest`: `jpegViraWebpSemMetadados` (saída `webp`; `vipsheader -a` não lista `exif-data`, `xmp-data` nem `icc-profile-data`); `orientacaoExifAplicada` (entrada 1200 × 800 com orientação 6 → nítida 800 × 1200); `maiorLadoLimitadoA1600`; `imagemPequenaNaoAmpliada` (400 × 300 continua 400 × 300); `borradaTemMaiorLado400`; `pngEWebpAceitos`; `textoComExtensaoJpgRecusado` (415); `dimensaoGiganteRecusada` (PNG de 30 000 × 30 000 gerado no teste por `vips black` → 422 em menos de 2 s); `arquivoTruncadoRecusado` (os primeiros 2 KB de um JPEG válido → 422); `seisMegabytesRecusados` (413); `temporariosApagados` (depois de sucesso e de falha, `<dir>/tmp` vazio).
  - `ArmazemDeImagensTest`: `guardarECaminho`; `apagarRemoveAsDuas`; `categoriaInvalidaRecusada` (`"../x"` → `IllegalArgumentException`).
  - `RetencaoDeTemporariosTest` (política `imagens-temporarias`, prazo `PT1H`, sobre `PoliticaDeRetencao` da Etapa 9): arquivo de `<dir>/tmp` com 61 min some, o de 59 min fica; `tamanho()` = arquivos em `tmp`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. Conferir `keep=none` com `vips webpsave` (libvips ≥ 8.15; abaixo disso, falhar no boot com mensagem clara). Gerar as fixtures com `vips` e um script Python temporário (Pillow num venv descartável) para o EXIF; registrar o comando no README das fixtures.
- [ ] **Passo 4:** testes verdes localmente e na CI. Commit `feat(web): pipeline de imagens com libvips (webp q88 e borrada)`.

### Task 5: Foto de perfil e fila de aprovação

**Arquivos:**
- Criar: `db/migration/V23__fotos.sql`
- Criar: raiz `perfil/{FotoId,FotoPendente,FotosDePerfil,FilaDeFotos,AcessoAFoto,AcessoAFotoPadrao,NivelDeAcesso,FotoAoExcluirConta}.java`; domínio `perfil/dominio/{FotoDePerfil,RepositorioDeFotos}.java`; web `perfil/web/FotoController.java`; infra `perfil/infra/FotosJdbc.java`
- Modificar: `perfil/PerfilController.java` (campo `foto` no GET), `application.properties` (regra `foto-conta`)
- Teste: `perfil/{FotoApiTest,FilaDeFotosTest}.java`

**Esquema:** `foto(id uuid pk, conta_id uuid not null references conta(id) on delete cascade, estado text not null check (estado in ('PENDENTE','APROVADA')), largura int not null, altura int not null, enviada_em timestamptz not null, aprovada_em timestamptz)` + índices únicos parciais `(conta_id) where estado = 'PENDENTE'` e `(conta_id) where estado = 'APROVADA'`; `alter table perfil add column foto_recusada_em timestamptz`.

**Interfaces:**
- Consome: `ProcessadorDeImagem`, `ArmazemDeImagens` (T4, categoria `perfil`); `LimiteDeTaxa` (regra `foto-conta`); `Idempotencia` (Etapa 2).
- Produz:
  - `public record FotoId(UUID valor)`; `public record FotoPendente(FotoId id, ContaId conta, Instant enviadaEm)`.
  - `public enum NivelDeAcesso { NENHUM, BORRADA, NITIDA }`; `public interface AcessoAFoto { NivelDeAcesso nivel(ContaId quemVe, ContaId titular); }`. `AcessoAFotoPadrao`: titular → `NITIDA`; outra conta autenticada → `BORRADA`. As Etapas 5, 6 e 8 editam esta classe (encontro → borrada, conexão mútua → nítida, bloqueio ou banimento → nenhum).
  - `POST /api/perfil/foto` (multipart, campo `arquivo`, `Idempotency-Key` opcional) → processa, grava como PENDENTE (apaga a pendente anterior e os arquivos dela) e responde 202 `{"estado":"PENDENTE"}`. Com chave: `respostaGravada` antes de processar; a gravação final usa `Idempotencia.executar` na mesma transação da linha (se outra requisição venceu, descarta os arquivos processados).
  - `GET /api/perfil` passa a trazer `"foto": {"aprovada": {"url": "/api/fotos/<id>/nitida"} | null, "pendente": {"url": ...} | null, "recusadaEm": null | instante}`.
  - `GET /api/fotos/{fotoId}/{variante}` (`nitida` | `borrada`): sem permissão, foto inexistente ou pendente vista por terceiro → 404 `FOTO_INEXISTENTE`; senão `image/webp` com `Cache-Control: private, max-age=300`.
  - `public class FilaDeFotos` (a Etapa 8 monta a tela em cima): `public List<FotoPendente> pendentes(int limite)` (mais antigas primeiro, limite ≤ 50); `public void aprovar(FotoId id)` (a pendente vira aprovada; a aprovada anterior é apagada com os arquivos depois do commit); `public void recusar(FotoId id)` (apaga a linha e os arquivos depois do commit e grava `perfil.foto_recusada_em`).
  - `FotoAoExcluirConta implements AoExcluirConta`: apaga linhas e arquivos.

- [ ] **Passo 1:** testes.
  - `FotoApiTest`: `uploadFicaPendente`; `titularVeAPendenteNitida`; `terceiroNaoVePendente` (404 nas duas variantes); `terceiroVeSoABorradaDaAprovada` (borrada 200; nítida 404 com corpo sem dados da foto); `idInexistenteEProibidoRespondemIgual` (mesmo status e corpo); `fotoNovaNaoDerrubaAAprovada` (com uma aprovada e uma pendente nova, terceiros seguem vendo a borrada da antiga); `rajadaDeUploadsBarrada` (11º → 429); `mesmaChaveNaoCriaSegundaPendente` (dois POST com a mesma `Idempotency-Key` e o mesmo arquivo → uma linha, mesma resposta); `mesmaChaveOutroArquivoRecusada` (409); `semSessao401`; `exclusaoApagaArquivos` (o diretório da conta fica vazio depois do commit).
  - `FilaDeFotosTest`: `pendentesEmOrdemDeEnvio`; `aprovarTrocaAAtual`; `recusarApagaEMarca`; `aprovarIdInexistente` → `FOTO_INEXISTENTE`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. Processar fora da transação; gravar a linha e mover os arquivos numa transação só, com limpeza no `afterCompletion` se der rollback.
- [ ] **Passo 4:** testes verdes. Commit `feat(perfil): foto com versão borrada e fila de aprovação`.

### Task 6: Consulta de perfis para as outras etapas

**Arquivos:**
- Criar: raiz `perfil/{Perfis,ConsultaDePerfis,PerfilPublico,PerfilCompleto,Preferencias}.java`; web `perfil/web/PerfisController.java`; `docs/adr/NNNN-privacidade-por-projecao.md` (próximo número livre no merge: célula geohash no lugar da coordenada, foto borrada derivada de 32 px, namoro nunca projetado para terceiros); teste: `perfil/PerfisDeTeste.java` e um método novo em `conta/ContasDeTeste.java`
- Teste: `perfil/{ConsultaDePerfisTest,PerfisApiTest}.java`

**Interfaces:**
- Consome: T1–T5, `ConsultaDeContas.porId` (Etapa 2) para a idade, `AcessoAFoto` (T5).
- Produz:
  - `public record PerfilPublico(ContaId conta, String apelido, Optional<String> fotoBorradaUrl)` (a Task 9 acrescenta `pronomes` e `verificado`; o mesmo para `PerfilCompleto`): o que se vê no encontro (Etapas 4 e 5). Sem foto aprovada → vazio (a interface desenha a silhueta).
  - `public record PerfilCompleto(ContaId conta, String apelido, int idade, Set<Intencao> intencao, String bio, Optional<String> fotoUrl)`: o que uma conexão vê (Etapa 6). A URL aponta para a nítida.
  - `public record Preferencias(Set<Intencao> intencao, FaixaEtaria faixaEtaria, Raio raio, boolean raioObrigatorio, Optional<Genero> generoProprio, Set<Genero> generosBuscados)`: projeção para o pareamento da Etapa 4. `generoProprio` e `generosBuscados` vêm preenchidos só quando há dados de namoro consentidos; senão, vazio e conjunto vazio. Nunca vai para o cliente.
  - `public interface Perfis`: `Optional<PerfilPublico> publico(ContaId conta)`; `Optional<PerfilCompleto> completo(ContaId conta)`; `Optional<Preferencias> preferencias(ContaId conta)`; `Optional<CelulaGeohash> celula(ContaId conta)`; `boolean prontoParaFila(ContaId conta)` (perfil com apelido e intenções, e conta ATIVA). Só contas ATIVA aparecem; as outras dão vazio. A idade vem de `ConsultaDeContas` (Etapa 2).
  - Testes das outras etapas: bean `PerfisDeTeste` com `void definir(ContaId conta, String apelido, Preferencias preferencias, Optional<CelulaGeohash> celula)` (grava perfil, ajustes, namoro com consentimento e célula); `ContasDeTeste` ganha `ContaDeTeste criar(String apelido, LocalDate nascimento)` (conta ATIVA + perfil com `{AMIZADE}`).
  - `GET /api/perfis/{contaId}` → `PerfilPublico` em JSON `{"apelido", "foto": null | "/api/fotos/<id>/borrada"}`, para qualquer conta autenticada (a Etapa 5 restringe ao par do encontro via `AcessoAFoto`). Conta inexistente, excluída ou sem perfil → 404 `PERFIL_INEXISTENTE`.

- [ ] **Passo 1:** testes.
  - `ConsultaDePerfisTest`: `preferenciasMontaTudo` (intenções, faixa, raio, obrigatório, gênero e buscados iguais aos gravados); `preferenciasSemNamoroVemVazias` (com `{AMIZADE}`, `generoProprio` vazio e `generosBuscados` vazio); `celulaDaLocalizacao`; `completoTrazIdade` (36 com nascimento 1990-05-10 e hoje 2026-11-22); `contaSuspensaFicaDeFora`; `semPerfilNaoFicaProntoParaFila`; `perfisDeTesteDefineTudo` (o que `PerfisDeTeste.definir` grava volta igual por `preferencias` e `celula`).
  - `PerfisApiTest`: `terceiroVePerfilPublico` (as chaves do JSON são exatamente `apelido` e `foto`, e a URL termina em `/borrada`); `perfilPublicoNuncaTrazBioNemNamoro` (o corpo não contém a bio canária nem `genero`); `contaExcluida404`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar com uma consulta por método (join pelas chaves; sem N+1).
- [ ] **Passo 4:** testes verdes. Commit `feat(perfil): consulta de perfis em projeções para as outras etapas`.

### Task 7: Telas de perfil e foto, e a prova

**Arquivos:**
- Criar: `frontend/src/telas/perfil/{MeuPerfil,EnviarFoto,PerfilDeOutraPessoa}.tsx` com `*.test.tsx`, `frontend/src/componentes/{FotoOuSilhueta}.tsx`, `frontend/src/api/perfil.ts`
- Modificar: o roteador (`/perfil`, `/perfis/:contaId`; link "Meu perfil" no topo para conta ativa)
- Criar: `src/test/.../e2e/{PerfilIT,PerfilPagina}.java`; Modificar: `AxeIT`

**Interfaces:**
- Consome: `GET/PUT /api/perfil` (T1, T5), `POST /api/perfil/foto` (T5), `GET /api/perfis/{id}` (T6), `ContaPagina.cadastrarContaNova` (Etapa 2), `FilaDeFotos` (bean, no e2e).
- Textos: título `Meu perfil`; rótulos `Apelido`, `Como você quer usar o Duora` (caixas `Amizade` e `Namoro`, ao menos uma), `Sobre você` (contador `n/280`); botão `Salvar`; foto: botão `Escolher foto`, a política de imagens exata da seção global, `Aguardando aprovação` para pendente, `Sua última foto não foi aprovada.` quando houver `recusadaEm`. `FotoOuSilhueta` usa `alt` com o apelido (`Foto de {apelido}`) ou `Sem foto` na silhueta.

- [ ] **Passo 1:** Vitest: `MeuPerfil` envia `versao` no PUT; `versaoObsoletaRecarrega` (409 → refaz o GET e mostra `Seu perfil mudou em outro aparelho. Confira e salve de novo.`); `EnviarFoto` recusa no cliente arquivo acima de 5 MB sem enviar; `FotoOuSilhueta` desenha a silhueta sem URL.
- [ ] **Passo 2:** `PerfilIT`: `perfilBorradoParaTerceirosENitidoParaOTitular` (A cadastra, preenche o perfil e envia `retrato-exif-orientacao-6.jpg`; antes da aprovação, B abre `/perfis/{A}` e vê a silhueta; o teste aprova pela `FilaDeFotos`; B passa a ver uma imagem cuja `src` termina em `/borrada`, e `request.get` de B na `/nitida` dá 404; A vê a nítida em `/perfil`). Rodar e ver falhar.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** Vitest, `PerfilIT` e `AxeIT` verdes nos três navegadores. Commit `feat(front): perfil, envio de foto e perfil de outra pessoa`.

### Task 8: Telas de preferências, namoro e localização

**Arquivos:**
- Criar: `frontend/src/telas/perfil/{Preferencias,Namoro,Localizacao}.tsx` com `*.test.tsx`, `frontend/src/geo/obterPosicao.ts` (envolve `navigator.geolocation.getCurrentPosition`, timeout 10 s, `maximumAge` 5 min, `enableHighAccuracy: false`)
- Modificar: o roteador (`/perfil/preferencias`)
- Criar: `src/test/.../e2e/PreferenciasIT.java`; Modificar: `AxeIT`

**Interfaces:**
- Consome: `GET/PUT /api/perfil/preferencias`, `GET/PUT/DELETE /api/perfil/namoro` (T2), `GET/PUT/DELETE /api/perfil/localizacao` (T3).
- Textos:
  - Preferências: `Idade de quem você quer conhecer` (dois campos numéricos `De` e `Até`); `Distância` (opções `Até 5 km`, `Até 20 km`, `Até 50 km`, `Sem limite`); `Como usar a distância` (opções `Só pessoas dentro do raio` e `Prefiro quem está mais perto, mas aceito mais longe`).
  - Localização: texto `Guardamos só uma área de cerca de 5 km, nunca o ponto exato.`; botão `Usar minha localização`; ativa: `Localização ativa (área aproximada)` + botão `Desativar localização`; negada ou sem resposta: `Não conseguimos sua localização. A distância fica em "Sem limite".`
  - Namoro (só com `Namoro` marcado nas intenções): título `Para namoro`; aviso `Gênero e interesse são dados sensíveis. Usamos só para encontrar pessoas compatíveis e nunca mostramos a ninguém. Você pode apagar quando quiser.`; `Seu gênero` (lista D1, `Prefiro descrever` abre `Como você se descreve`); `Quem você quer conhecer` (caixas, as mesmas opções); caixa `Concordo com o uso desses dados para o pareamento`; botão `Apagar dados de namoro`.
- O front nunca mostra célula nem coordenada; a coordenada sai do navegador direto no `PUT` e não fica em estado nem em log do front.

- [ ] **Passo 1:** Vitest: `localizacaoNegadaVoltaParaSemLimite` (erro `PERMISSION_DENIED` ou timeout → mensagem e raio `SEM_LIMITE` selecionado); `raioDesabilitadoSemLocalizacao`; `namoroSoComIntencaoDeNamoro`; `salvarNamoroExigeConsentimento` (botão desabilitado sem a caixa); `prefiroDescreverPedeTexto`.
- [ ] **Passo 2:** `PreferenciasIT` (contexto com `geolocation` (-23.5505, -46.6333) e permissão concedida): `ativarLocalizacaoEUsarRaio` (ativa, escolhe `Até 20 km` + `Só pessoas dentro do raio`, recarrega e os valores persistem); `namoroGravadoEApagadoAoMudarParaAmizade`. Rodar e ver falhar.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** Vitest, `PreferenciasIT` e `AxeIT` verdes nos três navegadores. Captura no emulador Android das telas de perfil e preferências com o `conta-check.mjs` estendido (passo opcional `--perfil`). Commit `feat(front): preferências, namoro com consentimento e localização`.

**Prova da etapa:** `PerfilIT.perfilBorradoParaTerceirosENitidoParaOTitular` verde nos três navegadores, mais `LocalizacaoApiTest.gravaSoACelula` e `NamoroApiTest.trocarParaAmizadeApagaDados`.

### Task 9: Orientação, pronomes, selo "verificado" e pausa (paridade, 2026-11-22)

**Arquivos:**
- Criar: `db/migration/V25__identidade_e_pausa.sql`
- Criar: raiz `perfil/{Orientacao,Pronomes,Identidade,ServicoDeIdentidade,PausaDaConta}.java`; domínio `perfil/dominio/{DadosDeIdentidade,RepositorioDeIdentidade}.java`; web `perfil/web/{IdentidadeController,PausaController}.java`; infra `perfil/infra/IdentidadeJdbc.java`
- Modificar: `perfil/{PerfilPublico,PerfilCompleto,ConsultaDePerfis}.java`, `frontend/src/telas/perfil/MeuPerfil.tsx`, `frontend/src/componentes/FotoOuSilhueta.tsx` (selo)
- Criar: `frontend/src/telas/perfil/{Identidade,PausarConta}.tsx` + testes, `frontend/src/componentes/SeloVerificado.tsx`
- Teste: `perfil/{IdentidadeApiTest,PausaApiTest,SeloVerificadoApiTest}.java`, `perfil/ConsultaDePerfisTest.java` (casos novos), `e2e/IdentidadeEPausaIT.java`; Modificar: `AxeIT`

**Esquema (V25):**
- `identidade(conta_id uuid pk references conta(id) on delete cascade, orientacao text check (orientacao in ('HETEROSSEXUAL','GAY','LESBICA','BISSEXUAL','PANSSEXUAL','ASSEXUAL','DEMISSEXUAL','QUEER','QUESTIONANDO','OUTRA')), descricao_orientacao text check (char_length(descricao_orientacao) between 1 and 40), mostrar_orientacao boolean not null default false, orientacao_consentida_em timestamptz, pronomes text check (pronomes in ('ELA_DELA','ELE_DELE','ELU_DELU','OUTROS')), descricao_pronomes text check (char_length(descricao_pronomes) between 1 and 20), check ((orientacao = 'OUTRA') = (descricao_orientacao is not null)), check ((pronomes = 'OUTROS') = (descricao_pronomes is not null)), check (orientacao is null or orientacao_consentida_em is not null))`.
- `alter table perfil add column pausada_em timestamptz`.

**Interfaces:**
- Consome: `ContaResumo.verificada()` (Etapa 2 Task 12), `Perfis` e projeções (T6), `ServicoDePreferencias` (T2).
- Produz:
  - `public enum Orientacao { HETEROSSEXUAL, GAY, LESBICA, BISSEXUAL, PANSSEXUAL, ASSEXUAL, DEMISSEXUAL, QUEER, QUESTIONANDO, OUTRA }` (rótulos: Heterossexual, Gay, Lésbica, Bissexual, Pansexual, Assexual, Demissexual, Queer, Questionando, Prefiro descrever).
  - `public enum Pronomes { ELA_DELA, ELE_DELE, ELU_DELU, OUTROS }` (rótulos: ela/dela, ele/dele, elu/delu, Prefiro descrever).
  - Regras: orientação é dado sensível — opcional, só com consentimento marcado, nunca usada no pareamento (o pareamento continua por gênero × quem quer conhecer) e mostrada só a conexões ativas quando `mostrar_orientacao` é `true`; pronomes são opcionais e públicos (aparecem no encontro). Gênero segue a lista da D1 (Task 2). "Prefiro descrever" pede texto curto (40 para orientação, 20 para pronomes).
  - `GET/PUT /api/perfil/identidade` `{ "orientacao"?, "descricaoOrientacao"?, "mostrarOrientacao", "consentimentoOrientacao", "pronomes"?, "descricaoPronomes"? }`; orientação sem consentimento → 422 `CONSENTIMENTO_NECESSARIO`; descrição fora da regra → 422 `IDENTIDADE_INVALIDA` (código novo em `CodigoPerfil`). `DELETE /api/perfil/identidade/orientacao` apaga orientação, descrição e consentimento.
  - Projeções (assinaturas finais):
    - `PerfilPublico(ContaId conta, String apelido, Optional<String> fotoBorradaUrl, Optional<String> pronomes, boolean verificado)` — `pronomes` já como texto de exibição (`ela/dela` ou a descrição);
    - `PerfilCompleto(ContaId conta, String apelido, int idade, Set<Intencao> intencao, String bio, Optional<String> fotoUrl, Optional<String> pronomes, Optional<String> orientacao, boolean verificado)` — `orientacao` só com `mostrar_orientacao`;
    - `Preferencias` não muda nesta task (sem filtro "só verificados": toda conta ATIVA é verificada, Etapa 2).
  - Pausa: `POST /api/perfil/pausa` → 204 (grava `pausada_em` se nulo); `DELETE /api/perfil/pausa` → 204; `GET /api/perfil` ganha `"pausada": bool`. `Perfis.prontoParaFila` passa a exigir `pausada_em is null`; `public boolean pausada(ContaId)`. Conexões, conversas e a conta seguem intactas; a Etapa 4 tira a conta pausada da fila e não manda o push "alguém compatível". Texto da tela: título `Pausar a conta`, `Enquanto a conta estiver pausada, você não entra na fila nem recebe avisos de pessoas compatíveis. Suas conexões e conversas continuam.`, botões `Pausar` e `Voltar a aparecer`.
  - `SeloVerificado`: ícone + texto `Verificado` (nunca só cor), no encontro, no perfil e nas conexões.
- [ ] **Passo 1:** testes.
  - `IdentidadeApiTest`: `pronomesOpcionaisEPublicos` (o `GET /api/perfis/{id}` de terceiro traz `pronomes`); `orientacaoSemConsentimentoRecebe422`; `orientacaoNuncaApareceParaTerceiro` (canário no corpo de `/api/perfis/{id}`); `orientacaoSoParaConexaoComMostrar` (via `Perfis.completo`: vazia com `mostrar=false`, presente com `true`); `prefiroDescreverExigeTexto`; `descricaoDe21CaracteresNosPronomesRecebe422`; `apagarOrientacaoLimpaOConsentimento`; `exclusaoApagaIdentidade`.
  - `SeloVerificadoApiTest`: `publicoTrazVerificadoDaConta` (conta com `verificacao_idade` `APROVADA` → `verificado: true`); `semAprovacaoNaoTrazSelo` (linha `DUVIDA` → `verificado: false`).
  - `PausaApiTest`: `pausadaNaoFicaProntaParaFila`; `pausarDuasVezesE204`; `voltarAAparecerReabre`; `pausaNaoMexeNasConexoes` (sem conexões nesta etapa: confere só que a conta segue ATIVA e o perfil intacto).
  - `ConsultaDePerfisTest`: `completoTrazPronomesEOrientacaoSoComMostrar`.
  - Vitest: `orientacaoPedeConsentimento`; `seloTemTextoAlemDoIcone`; `pausarMostraOTextoExato`.
  - `IdentidadeEPausaIT.pausarEVoltar` (pausa pela tela, `GET /api/perfil` traz `pausada: true`, volta a aparecer e traz `false`; o efeito na fila é testado na Etapa 4 Task 5); `AxeIT`: `identidadeSemViolacoes`, `pausaSemViolacoes`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar. As Etapas 4, 5 e 6 consomem as projeções com os campos novos; se já estiverem na `main`, ajustar os usos no mesmo commit, sem mudar comportamento.
- [ ] **Passo 4:** testes verdes nos três navegadores. Commit `feat(perfil): orientação e pronomes, selo verificado e pausa`.
