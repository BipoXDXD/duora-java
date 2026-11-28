# Etapa 3 — Perfil e preferências: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` ou `mattpocock-skills:tdd`; antes de "pronto", `superpowers:verification-before-completion` e `duora-etapa`.

**Objetivo:** cada conta `ATIVA` tem perfil (apelido, pronomes, intenção, bio, foto aprovada), ajustes de busca, dados de namoro e orientação com consentimento próprio, uma célula de localização e a pausa; o perfil aparece borrado para terceiros e nítido só para o titular.

**Arquitetura:** o módulo `perfil/` segue as camadas da 2a: raiz = API pública e casos de uso (`Perfis` com as projeções, `Localizacoes`, `FotosDoPerfil`, `FilaDeFotos`, `AcessoAFoto`); `perfil/dominio/` = Java puro (tipos que nascem válidos, agregado `Perfil`, portas); `perfil/infra/` = `JdbcClient` com `UPDATE` condicional, sem Spring Data JDBC; `perfil/web/` = controllers. O agregado `Perfil` é versionado; os satélites (ajustes, namoro, orientação, localização, pausa) têm escrita por comando. O processamento por libvips (CLI, fora de transação) e o armazém em disco ficam em `web/`, porque as Etapas 5 e 8 reusam. A exclusão da conta apaga tudo pelos ganchos `AoExcluirConta`, porque a conta vira lápide. No front, quatro rotas `lazy` sob `/perfil` e a rota dinâmica `/perfis/:contaId`.

**Stack:** Java 25, Spring Boot 4.2, PostgreSQL 18/Flyway, `JdbcClient`, libvips ≥ 8.15 por processo (8.15.1 na CI/Ubuntu 24.04, 8.18.7 no Mac), Micrometer; React 19 + TS + React Router 7.18.6 + TanStack Query 5.108.1 + valibot 1.5.0, Tailwind 4, Vitest, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 3, linhas 171–176; "Paridade com o mercado", linhas 58–61; "Escolhas minhas": localização, foto, foto pendente, sem imagens sensíveis). **Contratos (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. **Origem:** substitui `docs/superpowers/plans/2026-11-22-etapa-3-perfil.md`. **Base:** `main` com a 2b mergeada. Conferir pelo Context7 as APIs externas antes da task que as usa.

## Decisões do dono (2026-11-28)
- **G1 Gêneros amplos:** `MULHER` Mulher, `HOMEM` Homem, `PESSOA_NAO_BINARIA` Pessoa não binária, `OUTRO` Prefiro descrever (texto 1–40). Motivo: não permitir filtrar pessoas trans por rótulo.
- **G2 Pausa = sair da vitrine:** sai da fila e dos avisos proativos; conexões, chat, encontros em andamento e convites continuam; sem prazo.
- **G3 Fotos em disco local** num volume (`ArmazemEmDisco`); sem R2/S3/`bytea`.
- **G4 libvips ≥ 8.15 por processo (CLI)**, com timeout; não `vips-ffm`.
- **G5 Aprovação até a Etapa 8:** `FilaDeFotos` só como bean Java (testes e e2e); sem rota de admin, sem auto-aprovação.
- **G6 Perfil de terceiro** em `/perfis/:contaId` (prefixo no `RotasDoSpa` e nos cabeçalhos).
- **Da 2a/2b, valendo aqui:** só conta `ATIVA` (verificada) tem perfil — `@ContaAtual` padrão dá 403 `CADASTRO_INCOMPLETO` às outras; a preferência "só verificados" saiu (2026-11-22); só a aprovação fica em `verificacao_idade` (selo = `ContaResumo.verificada()`); reserva de idempotência de 30 s (orçamentos de tempo cabem nela); multipart `max-file-size` 6MB / `max-request-size` 13MB; `PERMISSIONS_POLICY_VERIFICACAO` só na `/verificacao`; front sem `disabled` nativo (`aria-disabled` + `seDisponivel`).

## Decisões da consolidação (uma linha cada; o dono pode reverter)
1. **Foto (forma única):** `AcessoAFoto.nivel(quemVe, titular) -> NivelDeAcesso` (D e I já coincidiam); `FotosDoPerfil.urlDaAprovada(ContaId, Variante)` substitui `borradaAprovada`/`nitidaAprovada`; `GET /api/perfil.foto` = `{aprovada: {nitida, borrada}|null, pendente: {nitida, borrada}|null, recusadaEm}` (forma da F, com `UrlsDaFoto` também na pendente); quem escreve o campo é a D5.
2. **Cache:** nítida sempre `Cache-Control: no-store` (o padrão do filtro, inclusive para o titular); borrada `private, max-age=300` + `Vary: Cookie`. A nítida nunca fica em cache compartilhado nem persistente.
3. **Rotas:** pronomes entram no `PUT /api/perfil` (colunas de `perfil`, dentro da versão); orientação em `GET/PUT/DELETE /api/perfil/orientacao` (some `/api/perfil/identidade`); `GET /api/perfil` traz `pronomes`, `descricaoPronomes`, `pausada`, `prontoParaFila`, `pendencias`, `foto`; `DELETE /api/perfil/localizacao` apaga célula e consentimento e volta o raio a `SEM_LIMITE` (versão +1) na mesma transação; namoro e orientação sem dados respondem 200 com nulos (não 404).
4. **Exclusão de conta (conferido em `conta/ExclusaoDeConta.java:56-77` e `conta/AoExcluirConta.java`):** a conta vira lápide, o `cascade` em `conta(id)` não dispara; cada tabela nova sai por gancho (`PerfilAoExcluirConta` apaga `perfil` e, por cascata de `perfil`, `preferencias`/`namoro`/`orientacao`; `LocalizacaoAoExcluirConta`; `FotoAoExcluirConta`), com teste por tabela e um teste-guarda em D6. Brief corrigido.
5. **Gêneros:** D, F e banco usam os 4 valores de G1; `PANSEXUAL` (não `PANSSEXUAL`) em D, F e banco.
6. **`privacidade.md`:** D3 e I5 só acrescentam linhas no corpo; o único aumento de `versao:` do front matter é em **D6**, que vem depois de D3, D4 e I5.
7. **libvips:** I1 instala na CI (jobs `verify` e `e2e`, no mesmo commit) e confere/instala no Mac (`brew install vips`, sob a trava de comando pesado) antes do primeiro teste; o último passo de I1 acrescenta uma linha ao `_mapa.md` do repositório principal avisando as outras sessões.
8. **Pronomes no agregado:** `PronomesEscolhidos` e `DescricaoCurta` nascem em D1 (D2 e D4 reusam), e o `IDENTIDADE_INVALIDA` cobre pronomes e orientação.
9. **Orientação em tabela própria (`orientacao`)** com linha só quando consentida: `orientacao`, `consentida_em` e `mostrar` são `not null`, sem o `is not distinct from`.
10. **Flyway na ordem de merge** (`out-of-order=false`, contratos: "sempre crescentes"): D1 V20, D3 V21, D2 V22, I3 V23, D4 V24; V25–V39 para correções. Renumerado do plano original para casar com a fila.
11. **`ContasDeTeste`:** D5 cria `criar(String apelido, LocalDate nascimento)` (Etapa 4) e `criarVerificada()` (F10); F não edita o arquivo.
12. **`FotosDeTeste`** (I3): `enviar(ContaId)` e `enviarAprovada(ContaId)` (aprova por `RepositorioDeFotos.decidir`, porque `FilaDeFotos` só chega em I5).
13. **`@ContaAtual` em fotos:** conta aguardando verificação → 403 `CADASTRO_INCOMPLETO` (código real em `ExigeSessao`).
14. **Mantidas das seções:** localização expira 90 dias após a última atualização; revogação e expiração sobem a versão das preferências; namoro e orientação sem versão (último vence); sem limite de taxa novo além de `foto-conta`; foto não consome `LimitesDeContaNova(IMAGEM)`; prévia por `data:`; coordenada arredondada a 2 casas no navegador; pausa sem diálogo, apagar namoro com diálogo, apagar orientação sem diálogo.

## Restrições globais
- Tudo dos contratos e das Restrições globais da 2a/2b: camadas travadas pelo `ArquiteturaTest` (`perfil/dominio/` sem Spring web, JDBC nem Jackson; outros módulos só usam a raiz de `perfil` e `web`), `Clock`/`GeradorDeIds`, idade em `America/Sao_Paulo`, erros por `CodigoDeErro`, logs sem PII, segredos só por ambiente, `-Xlint:all -Werror`, Spotless, ESLint + Prettier, um comportamento por teste, Conventional Commits pt-BR com trailer, nunca `git add -A`, comandos pesados um por vez sob a trava.
- **Flyway:** só V20–V39, números fixos (decisão 10); nada editado em V1–V12; uma task com `Vn` só mergeia depois de todas as de número menor.
- **Exclusão:** toda tabela nova referencia `conta(id)` ou `perfil(conta_id)` com `on delete cascade` (rede de segurança) **e** sai por um `AoExcluirConta` com teste; arquivos só no `afterCommit`.
- **LGPD:** gênero, interesse e orientação são sensíveis (art. 11): consentimento explícito e específico, apagamento ao desligar, nunca visíveis a terceiros (orientação só no `PerfilCompleto` com `mostrar`). Toda tabela nova tem linha na `privacidade.md` e política de retenção ou entrada em `LINHAS_SEM_POLITICA_DO_PERFIL`, travadas pelo `PrazosDaPlataformaTest`.
- **Localização:** só com consentimento; o servidor converte em geohash 5 e descarta a coordenada (nunca grava, loga nem ecoa); a tela mostra faixas, nunca distância nem célula.
- **Foto:** até 5 MB (5_242_880 B); JPEG/PNG/WebP pelos bytes mágicos; ≤ 12_000 px por lado e ≤ 40_000_000 px; nítida WebP q88 sem metadados, maior lado ≤ 1600, EXIF aplicado, sRGB; borrada WebP q88 sem metadados, maior lado 400, derivada de 32 px. Toda foto nova entra pendente; pendente não bloqueia (silhueta); a aprovada anterior vale até a nova ser aprovada. Sem permissão → 404 igual ao de inexistente.
- **Política de imagens (texto exato):** `Sem nudez, sexo, violência explícita ou conteúdo chocante. Toda foto passa por aprovação antes de aparecer.`
- **Proibido** criar recursos na AWS, Cloudflare, Resend ou Didit; nenhum segredo versionado.
- **Front:** pt-BR na voz da casa, gênero neutro; telas novas `lazy`; JS inicial ≤ 102_400 B gzip; nenhum chunk novo > 512_000 B; CSP inalterada; axe em todo estado visível novo; skills `frontend-design`, `interaction-design`, `ux-writing` antes de desenhar e `accessibility-audit`, `design-critique` antes do commit. Contagem de caracteres por code point nos dois lados.
- **Agentes:** implementador Opus · Medium; revisor (task e final) Opus · Medium; no máximo 3 implementadores + 1 revisor.

## Interfaces canônicas
Prefixos: `m/` = `src/main/java/bipotheking/net/duora/`, `t/` = `src/test/java/bipotheking/net/duora/`, `mig/` = `src/main/resources/db/migration/`, `r/` = `src/main/resources/`, `fe/` = `frontend/src/`.

**Raiz `m/perfil/` (API pública):**
| Tipo | Assinatura e regra | Task |
|---|---|---|
| `enum Intencao` | `AMIZADE, NAMORO` | D1 |
| `enum Pronomes` | `ELA_DELA, ELE_DELE, ELU_DELU, OUTROS`; `String rotulo()` = `ela/dela`, `ele/dele`, `elu/delu`, `Prefiro descrever` | D1 |
| `enum CodigoPerfil implements CodigoDeErro` | tabela "Códigos" (D1 cria todos) | D1 |
| `class ServicoDePerfil` | `Optional<Perfil> meu(ContaId)`; `Perfil salvar(ContaId, @Nullable Long versaoEsperada, EdicaoDoPerfil)` (tirar `NAMORO` apaga o namoro na mesma transação, D2) | D1 |
| `record FaixaEtaria(int minima, int maxima)` | `18 ≤ minima ≤ maxima ≤ 99`, senão `FAIXA_ETARIA_INVALIDA`; `boolean contem(int)`; `static TODAS` = (18, 99) | D2 |
| `enum Raio` | `ATE_5_KM, ATE_20_KM, ATE_50_KM, SEM_LIMITE`; `OptionalInt km()` = 5, 20, 50, vazio | D2 |
| `enum Genero` | `MULHER, HOMEM, PESSOA_NAO_BINARIA, OUTRO` (G1) | D2 |
| `class ServicoDePreferencias` | `AjustesVersionados ajustes(ContaId)`; `AjustesVersionados salvar(ContaId, @Nullable Long versaoEsperada, AjustesDeBusca)`; `void voltarParaSemLimite(ContaId)` (versão +1) | D2 |
| `class ServicoDeNamoro` | `Optional<DadosDeNamoro> meus(ContaId)`; `Optional<Instant> consentidoEm(ContaId)`; `DadosDeNamoro salvar(ContaId, DadosDeNamoro, boolean consentimento)`; `void revogar(ContaId)` (apaga e troca intenções por `{AMIZADE}`, versão +1) | D2 |
| `record CelulaGeohash(String valor)` | `^[0-9b-hjkmnp-z]{5}$`; `static de(double lat, double lon)`; `double distanciaKm(CelulaGeohash)` (haversine entre centros, R = 6371,0088); `FaixaDeDistancia faixaAte(CelulaGeohash)` | D3 |
| `enum FaixaDeDistancia` | `ATE_5_KM, ATE_20_KM, ATE_50_KM, MAIS_DE_50_KM`; `static de(double km)` (≤ 5, ≤ 20, ≤ 50, acima) | D3 |
| `enum CodigoLocalizacao implements CodigoDeErro` | `COORDENADA_INVALIDA`, `LOCALIZACAO_SEM_CONSENTIMENTO` | D3 |
| `class Localizacoes` | `void consentirEAtualizar(ContaId, double, double)`; `void atualizar(ContaId, double, double)` (sem consentimento → `LOCALIZACAO_SEM_CONSENTIMENTO`); `boolean consentida(ContaId)`; `Optional<CelulaGeohash> celula(ContaId)`; `Optional<Instant> atualizadaEm(ContaId)`; `void revogar(ContaId)` (chama `voltarParaSemLimite`, mesma transação) | D3 (+D2) |
| `enum Orientacao` | `HETEROSSEXUAL, GAY, LESBICA, BISSEXUAL, PANSEXUAL, ASSEXUAL, DEMISSEXUAL, QUEER, QUESTIONANDO, OUTRA`; `String rotulo()` | D4 |
| `class ServicoDeOrientacao` | `Optional<DadosDeOrientacao> minha(ContaId)`; `DadosDeOrientacao salvar(ContaId, @Nullable Orientacao, @Nullable String descricao, boolean mostrar, boolean consentimento)`; `void apagar(ContaId)` | D4 |
| `class PausaDaConta` | `void pausar(ContaId)` (idempotente, guarda o primeiro instante); `void retomar(ContaId)`; `boolean pausada(ContaId)`; sem perfil → `PERFIL_NECESSARIO`; não mexe em `versao` | D4 |
| `record PerfilPublico(ContaId conta, String apelido, Optional<String> fotoBorradaUrl, Optional<String> pronomes, boolean verificado)` | o que se vê no encontro | D5 |
| `record PerfilCompleto(ContaId conta, String apelido, int idade, Set<Intencao> intencao, String bio, Optional<String> fotoUrl, Optional<String> pronomes, Optional<String> orientacao, boolean verificado)` | o que uma conexão vê; `orientacao` só com `mostrar` | D5 |
| `record Preferencias(Set<Intencao> intencao, FaixaEtaria faixaEtaria, Raio raio, boolean raioObrigatorio, Optional<Genero> generoProprio, Set<Genero> generosBuscados)` | só para o pareamento; nunca serializada | D5 |
| `enum Pendencia` | `PERFIL, DADOS_DE_NAMORO` | D5 |
| `class Perfis` | `Optional<PerfilPublico> publico(ContaId)`; `Optional<PerfilCompleto> completo(ContaId)`; `Optional<Preferencias> preferencias(ContaId)`; `Optional<CelulaGeohash> celula(ContaId)`; `boolean prontoParaFila(ContaId)`; `boolean pausada(ContaId)`; `Set<Pendencia> pendencias(ContaId)`. Só conta `ATIVA` aparece (senão vazio/`false`); `publico`/`completo` não escondem a pausada; `prontoParaFila` = `ATIVA` + perfil + não pausada + (sem `NAMORO` ou com namoro); foto não conta | D5 |
| `record FotoId(UUID valor)`; `record FotoPendente(FotoId id, ContaId conta, Instant enviadaEm)` | — | I3 |
| `record UrlsDaFoto(String nitida, String borrada)`; `record FotoDoTitular(@Nullable UrlsDaFoto aprovada, @Nullable UrlsDaFoto pendente, @Nullable Instant recusadaEm)`; `record FotoEnviada(String estado, String url)` | `FotoDoTitular` vira `"foto"` do `GET /api/perfil` | I3 |
| `final class UrlDaFoto` | `static String de(FotoId, Variante)` = `/api/fotos/<uuid>/<sufixo>` | I3 |
| `enum CodigoFoto implements CodigoDeErro` | `FOTO_INEXISTENTE` (`NAO_ENCONTRADO`) "Foto não encontrada." | I3 |
| `class FotosDoPerfil` | `FotoEnviada enviar(ContaId, byte[] arquivo, @Nullable String chaveDeIdempotencia)`; `FotoDoTitular doTitular(ContaId)` (I3); `Optional<String> urlDaAprovada(ContaId titular, Variante)`; `Optional<Resource> abrir(ContaId quemVe, FotoId, Variante)` (I4); `void removerTodas(ContaId)` (I5, na transação do chamador) | I3–I5 |
| `enum NivelDeAcesso` / `interface AcessoAFoto` | `NENHUM, BORRADA, NITIDA` / `NivelDeAcesso nivel(ContaId quemVe, ContaId titular)` | I4 |
| `class AcessoAFotoPadrao implements AcessoAFoto` | titular → `NITIDA`; titular não `ATIVA` → `NENHUM`; outra conta → `BORRADA` (Etapas 5, 6, 8 editam). Também decide `GET /api/perfis/{id}` (`NENHUM` → 404) | I4 |
| `class FilaDeFotos` | `List<FotoPendente> pendentes(int limite)` (1..50, mais antigas primeiro); `void aprovar(FotoId)`; `void recusar(FotoId)`; inexistente ou não pendente → `FOTO_INEXISTENTE` | I5 |

**Domínio `m/perfil/dominio/` (Java puro):** `Apelido`, `Bio`, `Intencoes`, `DescricaoCurta`, `PronomesEscolhidos`, `EdicaoDoPerfil`, `Perfil` (D1); `AjustesDeBusca`, `AjustesVersionados`, `DadosDeNamoro` (D2); `Coordenada` (D3); `DadosDeOrientacao` (D4); `FichaDoPerfil` (D5); `EstadoDaFoto`, `FotoDePerfil` (I3); portas `RepositorioDePerfis`, `RepositorioDeAjustes`, `RepositorioDeNamoro`, `RepositorioDeLocalizacao`, `RepositorioDeOrientacao`, `RepositorioDePausa`, `LeituraDePerfis`, `RepositorioDeFotos` (assinaturas na task).

**`m/web/` (infra compartilhada, I1/I2):** `enum Variante { NITIDA, BORRADA }` com `String sufixo()`; `record ImagemProcessada(Path nitida, Path borrada, int largura, int altura) implements AutoCloseable`; `interface ProcessadorDeImagem { ImagemProcessada processar(InputStream) }` (413 `IMAGEM_GRANDE_DEMAIS`, 415 `FORMATO_NAO_SUPORTADO`, 422 `IMAGEM_INVALIDA`, 503 `PROCESSAMENTO_INDISPONIVEL`); `@ConfigurationProperties("duora.imagens") record ConfigDeImagens(Path dir, String vips, String vipsheader, Duration prazo, Duration espera, int simultaneos)` (padrões `vips`, `vipsheader`, `PT15S`, `PT5S`, `2`); `interface ArmazemDeImagens` com `void guardar(String categoria, UUID id, ImagemProcessada)`, `Optional<Resource> ler(String categoria, UUID id, Variante)`, `void apagar(String categoria, UUID id)`, `List<UUID> guardadasAntesDe(String categoria, Instant limite, int maximo)` (categoria `^[a-z]+$`); `ArmazemEmDisco`, `ProcessadorDeImagemVips`, `RetencaoDeTemporarios` package-private.

**Teste (para outras etapas):** `t/perfil/PerfisDeTeste.definir(ContaId, String apelido, Preferencias, Optional<CelulaGeohash>)`; `t/perfil/FotosDeTeste.enviar(ContaId) -> FotoId`, `enviarAprovada(ContaId) -> FotoId`; `t/conta/ContasDeTeste.criar(String apelido, LocalDate nascimento) -> ContaDeTeste` (`ATIVA` + `verificacao_idade` + perfil `{AMIZADE}`) e `criarVerificada() -> ContaDeTeste` (`ATIVA` + `verificacao_idade` `ESTIMATIVA_FACIAL`, sem perfil).

**Propriedades:** `duora.imagens.dir=${DUORA_IMAGENS_DIR:./dados/imagens}` (prod `${DUORA_IMAGENS_DIR}`; testes `target/dados-teste/imagens`); `duora.limites.regras.foto-conta.capacidade=10`, `.periodo=PT1H`. Métricas: Timer `duora.imagens.processamento{resultado=ok|recusada|indisponivel}`, gauge `duora.imagens.fila`.

**Contrato REST.** Regras da 2a: problem+json com `codigo`/`mensagem`; mutação com `X-Duora: 1` e mesma origem; `Cache-Control: no-store` (exceção: borrada); 401 `NAO_AUTENTICADO`; 403 `CONTA_INDISPONIVEL` (suspensa, banida) e `CADASTRO_INCOMPLETO` (pendente, aguardando); corpo estrito (campo desconhecido, tipo errado, enum desconhecido, JSON inválido, `NaN` → 400 `CORPO_INVALIDO`, nada gravado); instantes `Instant.toString()`; listas de enum na ordem de declaração.
| Rota | Corpo | Sucesso | Erros | Task |
|---|---|---|---|---|
| `GET /api/perfil` | — | 200 `{"versao":null\|n,"apelido":null\|s,"pronomes":null\|P,"descricaoPronomes":null\|s,"intencoes":[...],"bio":s,"pausada":b,"prontoParaFila":b,"pendencias":[...],"foto":{"aprovada":null\|{"nitida","borrada"},"pendente":null\|{"nitida","borrada"},"recusadaEm":null\|instante}}`; sem perfil: nulos, `[]`, `""`, `false`, `false`, `["PERFIL"]` | — | D1 (`pausada` D4; `prontoParaFila`, `pendencias`, `foto` D5) |
| `PUT /api/perfil` | `{"versao","apelido","pronomes","descricaoPronomes","intencoes","bio"}` (`bio` ausente = `""`; pronomes ausentes = nulos) | 200, corpo do GET; criar dá `versao` 1; cada edição +1 | 422 `APELIDO_INVALIDO`, `BIO_INVALIDA`, `INTENCAO_INVALIDA` (ausente ou `[]`), `IDENTIDADE_INVALIDA` (`OUTROS` sem descrição 1–20, ou descrição sem `OUTROS`); 409 `VERSAO_OBSOLETA` | D1 |
| `GET` / `PUT /api/perfil/preferencias` | `{"versao","idadeMinima","idadeMaxima","raio","raioObrigatorio"}` | 200; sem linha: `null`, 18, 99, `SEM_LIMITE`, `false`; `SEM_LIMITE` grava `raioObrigatorio` `false` | 422 `PERFIL_NECESSARIO`, `FAIXA_ETARIA_INVALIDA`, `LOCALIZACAO_NECESSARIA`; 409 `VERSAO_OBSOLETA` | D2 |
| `GET /api/perfil/namoro` | — | 200 `{"genero":null\|G,"descricaoGenero":null\|s,"interesses":G[],"consentidoEm":null\|instante}` | — | D2 |
| `PUT /api/perfil/namoro` | `{"genero","descricaoGenero","interesses","consentimento":true}` | 200, corpo do GET; substitui tudo; `consentidoEm` = agora | 422 nesta ordem: `PERFIL_NECESSARIO`, `INTENCAO_SEM_NAMORO`, `CONSENTIMENTO_NECESSARIO`, `GENERO_INVALIDO` | D2 |
| `DELETE /api/perfil/namoro` | — | 204; apaga e troca intenções por `["AMIZADE"]` (versão +1); idempotente, 204 sem perfil | — | D2 |
| `GET /api/perfil/localizacao` | — | 200 `{"consentida":b,"atualizadaEm":null\|instante}` (nunca a célula) | — | D3 |
| `PUT /api/perfil/localizacao` | `{"latitude","longitude"}` | 204; consente (ou mantém) e grava só a célula | 422 `COORDENADA_INVALIDA`; 400 `CORPO_INVALIDO` | D3 |
| `DELETE /api/perfil/localizacao` | — | 204; apaga célula e consentimento; raio → `SEM_LIMITE`, `false`, versão +1, mesma transação; idempotente | — | D3 (+D2) |
| `GET /api/perfil/orientacao` | — | 200 `{"orientacao":null\|O,"descricaoOrientacao":null\|s,"mostrarOrientacao":b,"consentidaEm":null\|instante}` | — | D4 |
| `PUT /api/perfil/orientacao` | `{"orientacao","descricaoOrientacao","mostrarOrientacao","consentimento":true}` | 200, corpo do GET; `consentidaEm` = agora | 422 nesta ordem: `PERFIL_NECESSARIO`, `CONSENTIMENTO_NECESSARIO`, `IDENTIDADE_INVALIDA` (orientação ausente; `OUTRA` ⇔ descrição 1–40) | D4 |
| `DELETE /api/perfil/orientacao` | — | 204; idempotente | — | D4 |
| `POST` / `DELETE /api/perfil/pausa` | — | 204; idempotentes | 422 `PERFIL_NECESSARIO` | D4 |
| `GET /api/perfis/{contaId}` | — | 200 exatamente `{"apelido","foto":null\|"/api/fotos/<id>/borrada","pronomes":null\|s,"verificado":b}` | 404 `PERFIL_INEXISTENTE` (inexistente, não `ATIVA`, sem perfil ou `AcessoAFoto` = `NENHUM`; status e corpo iguais); 400 `REQUISICAO_INVALIDA` (UUID malformado) | D5 |
| `POST /api/perfil/foto` | multipart `arquivo`; `Idempotency-Key` opcional (escopo `foto:<conta>`, impressão SHA-256 dos bytes) | 202 `{"estado":"PENDENTE","url":"/api/fotos/<id>/nitida"}`; repetição com a mesma chave e arquivo → mesmo corpo | 429 `LIMITE_DE_TAXA` + `Retry-After`; 413/415/422/503 do processador; 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA`, `REQUISICAO_EM_ANDAMENTO`; 400 `REQUISICAO_INVALIDA` (parte ausente) | I3 |
| `DELETE /api/perfil/foto` | — | 204; apaga aprovada, pendente e recusa | — | I5 |
| `GET /api/fotos/{fotoId}/{nitida\|borrada}` | — | 200 `image/webp`; nítida `no-store`; borrada `private, max-age=300` + `Vary: Cookie` | 404 `FOTO_INEXISTENTE`, corpo idêntico para inexistente, pendente de outro, recusada, sem permissão e variante desconhecida | I4 |

**Códigos** (`CodigoPerfil`, D1; tipo → mensagem): `APELIDO_INVALIDO` (`REGRA_DE_NEGOCIO`) "O apelido precisa ter de 1 a 20 caracteres." · `BIO_INVALIDA` "A bio pode ter até 280 caracteres." · `INTENCAO_INVALIDA` "Escolha amizade, namoro ou os dois." · `PERFIL_NECESSARIO` "Salve seu perfil antes de continuar." · `FAIXA_ETARIA_INVALIDA` "Escolha idades de 18 a 99, com a primeira até a segunda." · `LOCALIZACAO_NECESSARIA` "Ative a localização para usar um raio." · `INTENCAO_SEM_NAMORO` "Marque Namoro em Como você quer usar o Duora para preencher estes dados." · `CONSENTIMENTO_NECESSARIO` "Marque a caixa de consentimento para salvar estes dados." · `GENERO_INVALIDO` "Escolha seu gênero e ao menos uma opção de quem conhecer. Em Prefiro descrever, use de 1 a 40 caracteres." · `IDENTIDADE_INVALIDA` "Em Prefiro descrever, use de 1 a 40 caracteres na orientação e de 1 a 20 nos pronomes." (todos `REGRA_DE_NEGOCIO`, 422) · `VERSAO_OBSOLETA` (`CONFLITO`) "Seu perfil mudou enquanto você editava. Confira e salve de novo." · `PERFIL_INEXISTENTE` (`NAO_ENCONTRADO`) "Não encontramos este perfil." `CodigoLocalizacao` (D3): `COORDENADA_INVALIDA` "Não conseguimos usar essa localização. Tente de novo." · `LOCALIZACAO_SEM_CONSENTIMENTO` "Ative a localização antes de atualizá-la." `CodigoWeb` (I1): `IMAGEM_GRANDE_DEMAIS` (`GRANDE_DEMAIS`) "A imagem pode ter até 5 MB." · `IMAGEM_INVALIDA` "Não conseguimos abrir esta imagem. Envie outra foto." · `PROCESSAMENTO_INDISPONIVEL` (`INDISPONIVEL`) "Não conseguimos processar a foto agora. Tente de novo em alguns minutos." · `FORMATO_NAO_SUPORTADO` (existe) passa a "Envie uma foto em JPEG, PNG ou WebP."

**Front (`fe/`):**
```ts
// api/perfil.ts (respostas lidas com valibot; fora do formato → ErroHttp{codigo:"RESPOSTA_INVALIDA"})
export type Intencao = "AMIZADE" | "NAMORO";
export type Pronomes = "ELA_DELA" | "ELE_DELE" | "ELU_DELU" | "OUTROS";
export type Raio = "ATE_5_KM" | "ATE_20_KM" | "ATE_50_KM" | "SEM_LIMITE";
export type Genero = "MULHER" | "HOMEM" | "PESSOA_NAO_BINARIA" | "OUTRO";
export type Orientacao = "HETEROSSEXUAL" | "GAY" | "LESBICA" | "BISSEXUAL" | "PANSEXUAL" | "ASSEXUAL" | "DEMISSEXUAL" | "QUEER" | "QUESTIONANDO" | "OUTRA";
export type Pendencia = "PERFIL" | "DADOS_DE_NAMORO";
export interface UrlsDaFoto { nitida: string; borrada: string }
export interface FotoDoPerfil { aprovada: UrlsDaFoto | null; pendente: UrlsDaFoto | null; recusadaEm: string | null }
export interface MeuPerfil { versao: number | null; apelido: string | null; pronomes: Pronomes | null; descricaoPronomes: string | null; intencoes: Intencao[]; bio: string; pausada: boolean; prontoParaFila: boolean; pendencias: Pendencia[]; foto: FotoDoPerfil }
export interface EdicaoDoPerfil { versao: number | null; apelido: string; pronomes: Pronomes | null; descricaoPronomes: string | null; intencoes: Intencao[]; bio: string }
export interface PreferenciasDeEncontro { versao: number | null; idadeMinima: number; idadeMaxima: number; raio: Raio; raioObrigatorio: boolean }
export interface SituacaoDaLocalizacao { consentida: boolean; atualizadaEm: string | null }
export interface Coordenada { latitude: number; longitude: number }
export interface DadosDeNamoro { genero: Genero; descricaoGenero: string | null; interesses: Genero[]; consentidoEm: string }
export interface DadosDeOrientacao { orientacao: Orientacao; descricaoOrientacao: string | null; mostrarOrientacao: boolean; consentidaEm: string }
export interface PerfilPublico { apelido: string; foto: string | null; pronomes: string | null; verificado: boolean }
export function obterMeuPerfil(): Promise<MeuPerfil>;
export function salvarPerfil(e: EdicaoDoPerfil): Promise<MeuPerfil>;
export function enviarFoto(arquivo: Blob, chave: string): Promise<void>;            // 202, corpo ignorado
export function pausarConta(): Promise<void>; export function voltarAAparecer(): Promise<void>;
export function obterPreferencias(): Promise<PreferenciasDeEncontro>;
export function salvarPreferencias(p: PreferenciasDeEncontro): Promise<PreferenciasDeEncontro>;
export function obterLocalizacao(): Promise<SituacaoDaLocalizacao>;
export function ativarLocalizacao(c: Coordenada): Promise<void>; export function desativarLocalizacao(): Promise<void>;
export function obterNamoro(): Promise<DadosDeNamoro | null>;                         // 200 com genero null → null
export function salvarNamoro(d: { genero: Genero; descricaoGenero: string | null; interesses: Genero[]; consentimento: true }): Promise<DadosDeNamoro>;
export function apagarNamoro(): Promise<void>;
export function obterOrientacao(): Promise<DadosDeOrientacao | null>;                 // 200 com orientacao null → null
export function salvarOrientacao(d: { orientacao: Orientacao; descricaoOrientacao: string | null; mostrarOrientacao: boolean; consentimento: true }): Promise<DadosDeOrientacao>;
export function apagarOrientacao(): Promise<void>;
export function obterPerfilPublico(contaId: string): Promise<PerfilPublico | null>;   // 404 PERFIL_INEXISTENTE → null
export function versaoObsoleta(erro: unknown): boolean;                               // 409 + VERSAO_OBSOLETA
// geo/obterPosicao.ts (a Etapa 4 reusa)
export type FalhaDePosicao = "negada" | "semResposta" | "indisponivel";
export type ResultadoDePosicao = { ok: true; coordenada: Coordenada } | { ok: false; falha: FalhaDePosicao };
export function obterPosicao(geo?: Geolocation, relogio?: { setTimeout: typeof setTimeout; clearTimeout: typeof clearTimeout }): Promise<ResultadoDePosicao>;
export const VIGIA_DA_PERMISSAO_MS = 20_000;
export function arredondar(c: Coordenada): Coordenada; // 2 casas
```
`api/http.ts`: `requisitarHttp` aceita `"GET" | "POST" | "PUT" | "DELETE"`. `rotas/consultas.ts`: `consultaDoMeuPerfil` `["perfil"]`, `consultaDasPreferencias` `["perfil","preferencias"]`, `consultaDaLocalizacao` `["perfil","localizacao"]`, `consultaDoNamoro` `["perfil","namoro"]`, `consultaDaOrientacao` `["perfil","orientacao"]`, `consultaDoPerfilPublico(contaId)` `["perfis", contaId]`, sem `staleTime`. `rotas/caminhosDaCasca.ts`: `CAMINHOS_DA_CASCA` + `/perfil`, `/perfil/preferencias`, `/perfil/namoro`, `/perfil/orientacao`; `PREFIXOS_DA_CASCA = ["/perfis/"] as const`, espelhado em `RotasDoSpa.PREFIXOS`. `rotas/guardas.ts`: `comContaAtiva<T>(cliente: QueryClient, carregar: () => Promise<T>): Promise<T>`. Componentes: `FotoOuSilhueta({url, alt, tamanho: "grande"|"medio"})`, `SeloVerificado()`, `MarcaDoSelo()`, `CartaoDoPerfil({perfil: PerfilPublico})` (a Etapa 5 reusa).

## Ordem e paralelismo: três trilhas em worktrees
Uma fila por trilha, um implementador por vez (Opus · Medium, worktree próprio a partir da branch `etapa-3`), mais 1 revisor (Opus · Medium): no máximo 4 agentes. Uma task começa quando seus portões estão na `etapa-3`; telas começam contra o contrato com `fetch` falso, e o portão vale para o merge.
| Trilha | Fila | Portões de outras trilhas |
|---|---|---|
| D (domínio, API) | D1 → D3 → D2 → D4 → D5 → D6 | D4 (merge): I3 (V23 antes de V24) · D5: I4 · D6: I5 |
| I (imagens, fotos) | I1 → I2 → I3 → I4 → I5 | I3 (merge): D1 (pacote `perfil/`) e D2 (V22 antes de V23) |
| F (front, e2e) | F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10 → F11 | F10: D6, I5 (back inteiro) |

**Flyway:** D1 `V20__perfil.sql` · D3 `V21__localizacao.sql` · D2 `V22__preferencias_e_namoro.sql` · I3 `V23__fotos.sql` · D4 `V24__orientacao_e_pausa.sql` · V25–V39 correções.

**Posse de arquivos:**
- **D:** `m/perfil/**` exceto os da I; `mig/V20–V22,V24`; `t/perfil/**` exceto os da I; `t/conta/ContasDeTeste*.java`; `docs/adr/0008-*`; `CONTEXT.md`; front matter da `privacidade.md` (D6).
- **I:** `m/web/{Variante,ImagemProcessada,ProcessadorDeImagem*,ConfigDeImagens,ArmazemDeImagens,ArmazemEmDisco,RetencaoDeTemporarios,CodigoWeb,TratamentoDeErros}.java`; `m/perfil/{FotoId,FotoPendente,UrlsDaFoto,FotoDoTitular,FotoEnviada,UrlDaFoto,CodigoFoto,FotosDoPerfil,FilaDeFotos,NivelDeAcesso,AcessoAFoto,AcessoAFotoPadrao,FotoAoExcluirConta}.java`; `m/perfil/dominio/{EstadoDaFoto,FotoDePerfil,RepositorioDeFotos}.java`; `m/perfil/infra/{FotosJdbc,RetencaoDeFotosOrfas}.java`; `m/perfil/web/{FotoController,FotosController}.java`; `mig/V23`; `r/application*.properties`; `.github/workflows/ci.yml`; `.gitignore`; `src/test/resources/imagens/**`.
- **F:** `fe/**`; `m/web/{RotasDoSpa,CabecalhosDeSeguranca}.java`; `t/web/{RotasDoSpaTest,CspDaVerificacaoTest}.java`; `t/pwa/**`; `t/e2e/**`; `scripts/android/**`.
- **Compartilhados (uma linha cada; quem mergeia depois resolve):** `t/retencao/PrazosDaPlataformaTest.java` (D3, I2, I5, D6) e o corpo da `privacidade.md` (D3, I5, D6).

## Foco de revisão
1. **Nítida em cache persistente ou de outra conta** (aparelho compartilhado). Testes: I4 `nitidaSemCache` (`Cache-Control: no-store`) e `borradaComCachePrivado` (`private, max-age=300`, `Vary` contém `Cookie`).
2. **Exclusão deixa linha órfã** (lápide não dispara `cascade`; tabela nova sem gancho). Teste: D6 `ExclusaoApagaTudoDoPerfilTest.nenhumaLinhaNemArquivoSobra`.
3. **Corrida intenção × namoro** (namoro gravado com `quer_namoro` falso). Teste: D2 `trocaDeIntencaoConcorrenteNuncaDeixaNamoroOrfao`.
4. **Nome da constraint some com `logServerErrorDetail=false`** e `INTENCAO_SEM_NAMORO` vira 500. Teste: D2 `nomeDaConstraintChegaComDetalheDesligado`.
5. **Disco cheio no `guardar`** prende a chave de idempotência e devolve 500. Teste: I3 `discoCheioVira503ELiberaAChave`.

---

## Trilha D — domínio e API do perfil

### Task D1: Perfil básico (apelido, pronomes, intenção, bio, versão)
**Files:** Create `mig/V20__perfil.sql`; `m/perfil/{Intencao,Pronomes,CodigoPerfil,ServicoDePerfil}.java`; `m/perfil/dominio/{Apelido,Bio,Intencoes,DescricaoCurta,PronomesEscolhidos,EdicaoDoPerfil,Perfil,RepositorioDePerfis}.java`; `m/perfil/web/PerfilController.java`; `m/perfil/infra/{PerfisJdbc,PerfilAoExcluirConta}.java`. Test `t/perfil/dominio/{ApelidoTest,BioTest,IntencoesTest,DescricaoCurtaTest,PronomesEscolhidosTest}.java`; `t/perfil/{CodigoPerfilTest,PerfilApiTest}.java`.
**Esquema V20:** `perfil(conta_id uuid primary key references conta(id) on delete cascade, apelido text not null check (char_length(apelido) between 1 and 20), pronomes text check (pronomes in ('ELA_DELA','ELE_DELE','ELU_DELU','OUTROS')), descricao_pronomes text check (char_length(descricao_pronomes) between 1 and 20), quer_amizade boolean not null, quer_namoro boolean not null, bio text not null default '' check (char_length(bio) <= 280), versao bigint not null check (versao >= 1), criado_em timestamptz not null, atualizado_em timestamptz not null, check (quer_amizade or quer_namoro), check ((pronomes is not distinct from 'OUTROS') = (descricao_pronomes is not null)))`.
**Interfaces:** Consumes `ContaId`, `@ContaAtual`, `AoExcluirConta`, `ErroDeNegocio`, `Clock`, `TransactionTemplate`. Produces:
- `Apelido.de(@Nullable String)`: strip; 1–20 code points; recusa controle ISO, U+200E, U+200F, U+061C, U+2028, U+2029, U+202A–U+202E, U+2066–U+2069 → `APELIDO_INVALIDO`.
- `Bio.de(@Nullable String)`: nulo → `""`; `\r\n` → `\n`; strip; ≤ 280 code points; outro controle → `BIO_INVALIDA`.
- `Intencoes.de(@Nullable Collection<Intencao>)` (vazio/nulo → `INTENCAO_INVALIDA`; `EnumSet` imutável); `boolean querNamoro()`; `static SO_AMIZADE`.
- `DescricaoCurta.de(String bruto, int maximo, CodigoDeErro codigo)`: strip; 1..`maximo` code points; mesmos proibidos do `Apelido`.
- `PronomesEscolhidos(Pronomes pronomes, Optional<DescricaoCurta> descricao)`: `static Optional<PronomesEscolhidos> de(@Nullable Pronomes, @Nullable String descricao)` (ambos nulos → vazio; `OUTROS` ⇔ descrição ≤ 20; violação → `IDENTIDADE_INVALIDA`); `String paraExibir()` (rótulo ou descrição).
- `EdicaoDoPerfil(Apelido apelido, Optional<PronomesEscolhidos> pronomes, Intencoes intencoes, Bio bio)`; `Perfil(ContaId conta, EdicaoDoPerfil dados, long versao)`.
- `RepositorioDePerfis`: `Optional<Perfil> buscar(ContaId)`; `boolean criar(Perfil, Instant)` (`on conflict do nothing`); `Optional<Perfil> atualizar(Perfil, Instant)` (`set versao = versao + 1 where conta_id = :c and versao = :v`); `boolean existe(ContaId)`.
- `ServicoDePerfil.salvar`: versão nula → `criar` (falso → `VERSAO_OBSOLETA`); versão n → `atualizar` (vazio → `VERSAO_OBSOLETA`).
- `PerfilController` (`GET`/`PUT /api/perfil`): `record EdicaoDoPerfilJson(@Nullable Long versao, @Nullable String apelido, @Nullable Pronomes pronomes, @Nullable String descricaoPronomes, @Nullable List<Intencao> intencoes, @Nullable String bio)`; `record PerfilJson(...)` com as chaves do contrato (D4/D5 acrescentam).
- `PerfilAoExcluirConta implements AoExcluirConta` (package-private): `delete from perfil where conta_id = :c`.
- [ ] **Testes que falham.**
  - `ApelidoTest` (`@ParameterizedTest`): `""`, `"   "`, 21 × `"a"`, `"a‮b"`, `"a\u0007b"`, `null` → `APELIDO_INVALIDO`; 19 × `"a"` + `"😀"` aceito; `"  Bia  "` → `"Bia"`.
  - `BioTest`: 281 recusada; 280 aceita; `"a\nb"` aceita; `"a\r\nb"` → `"a\nb"`; `"a\u0007"` recusada; `null` → `""`.
  - `IntencoesTest`: `[]`/`null` → `INTENCAO_INVALIDA`; `[NAMORO, AMIZADE, AMIZADE]` → `EnumSet.of(AMIZADE, NAMORO)`; `querNamoro()` falso com `[AMIZADE]`.
  - `DescricaoCurtaTest`: 40 code points aceitos com máximo 40; 41 e `"  "` recusados com o código passado.
  - `PronomesEscolhidosTest`: `OUTROS` sem descrição; `ELU_DELU` com descrição; `OUTROS` com 21; descrição sem pronomes → `IDENTIDADE_INVALIDA`; `OUTROS` + `"ile/dile"` → `paraExibir()` `"ile/dile"`; `ELU_DELU` → `"elu/delu"`; ambos nulos → vazio.
  - `CodigoPerfilTest`: cada código com tipo e mensagem exatos.
  - `PerfilApiTest` (`BancoDeTeste`): `perfilNovoComecaVazio`; `criarDaVersao1` (`{"versao":null,"apelido":"Bia","intencoes":["AMIZADE"],"bio":"oi"}` → 200, `versao` 1); `editarSobeAVersao`; `edicaoConcorrenteRecusada` (dois PUT com `versao` 1 → o segundo 409, banco com o primeiro e `versao` 2); `versaoNulaComPerfilExistente409`; `versaoComPerfilInexistente409`; `campoExtraRecusado` (`"contaId"` → 400, zero linhas); `intencaoDesconhecida400` (`["CASUAL"]`); `intencoesVaziasRecusadas`; `apelidoAusente422`; `bioAusenteViraVazia`; `apelidoGravadoSemEspacos`; `intencoesNaOrdemDoEnum`; `pronomesGravadosEDevolvidos` (`ELU_DELU`); `pronomesOutrosSemDescricao422` (`IDENTIDADE_INVALIDA`); `semSessao401`; `contaAguardandoRecebe403` (`CADASTRO_INCOMPLETO`); `exclusaoDaContaApagaPerfil` (`ExclusaoDeConta.excluir(conta, true)` → zero linhas).
- [ ] `./mvnw -q test -Dtest='ApelidoTest,BioTest,IntencoesTest,DescricaoCurtaTest,PronomesEscolhidosTest,CodigoPerfilTest,PerfilApiTest'` → FAIL.
- [ ] Implementar as assinaturas e a V20.
- [ ] Os mesmos e `ArquiteturaTest` → PASS.
- [ ] Commit `feat(perfil): apelido, pronomes, intenção e bio com controle de versão`

### Task D3: Localização por célula e retenção
**Files:** Create `mig/V21__localizacao.sql`; `m/perfil/{CelulaGeohash,FaixaDeDistancia,CodigoLocalizacao,Localizacoes}.java`; `m/perfil/dominio/{Coordenada,RepositorioDeLocalizacao}.java`; `m/perfil/web/LocalizacaoController.java`; `m/perfil/infra/{LocalizacaoJdbc,LocalizacaoAoExcluirConta,RetencaoDeLocalizacoes}.java`. Modify `r/legal/privacidade.md` (só corpo), `t/retencao/PrazosDaPlataformaTest.java`. Test `t/perfil/{CelulaGeohashTest,FaixaDeDistanciaTest,LocalizacaoApiTest}.java`, `t/perfil/dominio/CoordenadaTest.java`, `t/perfil/infra/RetencaoDeLocalizacoesTest.java`.
**Esquema V21:** `localizacao(conta_id uuid primary key references conta(id) on delete cascade, celula text not null check (celula ~ '^[0-9b-hjkmnp-z]{5}$'), consentida_em timestamptz not null, atualizada_em timestamptz not null, check (atualizada_em >= consentida_em))` + índice em `atualizada_em`. Referencia `conta` (não `perfil`) para não depender do perfil.
**Interfaces:** Consumes `AoExcluirConta`, `PoliticaDeRetencao`, `Lotes`, `Clock`. Produces as da raiz e:
- `Coordenada.de(double, double)` (não finita ou fora de [-90, 90]/[-180, 180] → `COORDENADA_INVALIDA`); `toString()` = `"Coordenada[***]"`; nunca persiste. Codificador geohash próprio, sem dependência.
- `RepositorioDeLocalizacao`: `void consentirEGravar(ContaId, CelulaGeohash, Instant)` (upsert que mantém `consentida_em`); `boolean atualizar(ContaId, CelulaGeohash, Instant)`; `Optional<CelulaGeohash> celula(ContaId)`; `Optional<Instant> atualizadaEm(ContaId)`; `void apagar(ContaId)`.
- `RetencaoDeLocalizacoes`: `localizacoes-paradas`, `P90D`, referência `atualizada_em`. `LocalizacaoAoExcluirConta`: `delete from localizacao where conta_id = :c`.
- `privacidade.md`: linha `| Localização (área de cerca de 5 km) | até você desativar a localização ou excluir a conta; apagada 90 dias depois da última atualização |`; o item "Localização" ganha `Se você não usar o Duora por 90 dias, apagamos essa área.` Sem mexer na `versao:`.
- Controller: `record CoordenadaJson(@Nullable Double latitude, @Nullable Double longitude)` (nulo → 400); nenhum `toString`, log ou exceção carrega os números.
- [ ] **Testes que falham.**
  - `CelulaGeohashTest`: (-23.5505, -46.6333) → `6gyf4`; (-22.9068, -43.1729) → `75cm9`; (0, 0) → `s0000`; (90, 180) → `zzzzz`; (-90, -180) → `00000`; `distanciaKm` de `6gyf4` a `6gycf` 4,887, `6gyft` 16,621, `6gysh` 47,462, `6gxp9` 58,034, `75cm9` 361,276 (±0,05); mesma célula → 0; `new CelulaGeohash("6gyfa")` → `IllegalArgumentException`.
  - `FaixaDeDistanciaTest`: 5,0 → `ATE_5_KM`; 5,01 → `ATE_20_KM`; 20,0 → `ATE_20_KM`; 50,0 → `ATE_50_KM`; 50,01 → `MAIS_DE_50_KM`.
  - `CoordenadaTest`: (90, 180) e (-90, -180) aceitas; 90,0000001, -180,0000001, `NaN`, `Infinity` → `COORDENADA_INVALIDA`; `toString()` sem dígitos.
  - `LocalizacaoApiTest` (`OutputCaptureExtension`): `gravaSoACelula` (colunas exatas `conta_id`, `celula`, `consentida_em`, `atualizada_em`); `coordenadaNaoApareceNoLog` (canário `-23.987654321`); `corpoMalformadoNaoLogaNemEcoaCoordenada`; `coordenadaInvalidaRecusada` (91, -181 → 422 sem `"91"` no corpo; `"x"` e `NaN` → 400); `getNaoDevolveCelula` (chaves exatas); `segundoPutMantemConsentimento`; `atualizarSemConsentimentoRecusado`; `revogarApaga` (204, repetir 204); `exclusaoDaContaApagaLocalizacao`.
  - `RetencaoDeLocalizacoesTest`: `vencidaSome` (91 dias some, 89 fica); `tamanhoContaSoVencidas`; `nomeEPrazo`.
  - `PrazosDaPlataformaTest`: entrada `"Localização (área de cerca de 5 km)" → List.of("localizacoes-paradas")`.
- [ ] `./mvnw -q test -Dtest='CelulaGeohashTest,FaixaDeDistanciaTest,CoordenadaTest,LocalizacaoApiTest,RetencaoDeLocalizacoesTest,PrazosDaPlataformaTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos → PASS.
- [ ] Commit `feat(perfil): localização guardada só como célula geohash`

### Task D2: Ajustes de busca e dados de namoro
**Files:** Create `mig/V22__preferencias_e_namoro.sql`; `m/perfil/{FaixaEtaria,Raio,Genero,ServicoDePreferencias,ServicoDeNamoro}.java`; `m/perfil/dominio/{AjustesDeBusca,AjustesVersionados,DadosDeNamoro,RepositorioDeAjustes,RepositorioDeNamoro}.java`; `m/perfil/web/{PreferenciasController,NamoroController}.java`; `m/perfil/infra/{AjustesJdbc,NamoroJdbc}.java`. Modify `ServicoDePerfil` (tirar `NAMORO` chama `RepositorioDeNamoro.apagar` antes do `atualizar`, mesma transação), `RepositorioDePerfis`/`PerfisJdbc` (`void trocarIntencoesPorAmizade(ContaId, Instant)`), `Localizacoes.revogar` (chama `voltarParaSemLimite`), `RetencaoDeLocalizacoes` (o lote volta o raio). Test `t/perfil/{FaixaEtariaTest,RaioTest,PreferenciasApiTest,NamoroApiTest}.java`, `t/perfil/dominio/DadosDeNamoroTest.java`, `t/perfil/infra/{RetencaoDeLocalizacoesTest,NamoroConcorrenciaTest}.java`.
**Esquema V22:** `preferencias(conta_id uuid primary key references perfil(conta_id) on delete cascade, idade_minima smallint not null check (idade_minima between 18 and 99), idade_maxima smallint not null check (idade_maxima between 18 and 99), raio text not null check (raio in ('ATE_5_KM','ATE_20_KM','ATE_50_KM','SEM_LIMITE')), raio_obrigatorio boolean not null, versao bigint not null check (versao >= 1), atualizado_em timestamptz not null, check (idade_minima <= idade_maxima), check (raio <> 'SEM_LIMITE' or not raio_obrigatorio))`; `alter table perfil add constraint perfil_conta_namoro_uk unique (conta_id, quer_namoro)`; `namoro(conta_id uuid primary key, quer_namoro boolean not null default true check (quer_namoro), genero text not null check (genero in ('MULHER','HOMEM','PESSOA_NAO_BINARIA','OUTRO')), descricao_genero text check (char_length(descricao_genero) between 1 and 40), interesses text[] not null check (cardinality(interesses) >= 1 and interesses <@ array['MULHER','HOMEM','PESSOA_NAO_BINARIA','OUTRO']::text[]), consentido_em timestamptz not null, check ((genero = 'OUTRO') = (descricao_genero is not null)), constraint namoro_exige_intencao_fk foreign key (conta_id, quer_namoro) references perfil (conta_id, quer_namoro) on delete cascade)` (update `NO ACTION`: desligar o namoro com linha em `namoro` falha no próprio `UPDATE`).
**Interfaces:** Consumes D1, D3. Produces os da raiz e:
- `AjustesDeBusca(FaixaEtaria faixa, Raio raio, boolean raioObrigatorio)` (`SEM_LIMITE` força `false`); `static PADRAO` = (`TODAS`, `SEM_LIMITE`, `false`). `AjustesVersionados(AjustesDeBusca ajustes, @Nullable Long versao)`.
- `DadosDeNamoro(Genero genero, Optional<DescricaoCurta> descricao, Set<Genero> interesses)`: `OUTRO` ⇔ descrição ≤ 40; interesses não vazios; violação → `GENERO_INVALIDO`.
- `RepositorioDeAjustes`: `Optional<AjustesVersionados> buscar(ContaId)`; `boolean criar(ContaId, AjustesDeBusca, Instant)` e `Optional<AjustesVersionados> atualizar(ContaId, long, AjustesDeBusca, Instant)`, ambos com `(:raio = 'SEM_LIMITE' or exists (select 1 from localizacao where conta_id = :c))`; zero linhas → o serviço relê: versão diferente → `VERSAO_OBSOLETA`, senão `LOCALIZACAO_NECESSARIA`; `void voltarParaSemLimite(ContaId, Instant)` (`... versao = versao + 1 where raio <> 'SEM_LIMITE'`).
- `RepositorioDeNamoro`: `buscar`, `consentidoEm`, `substituir(ContaId, DadosDeNamoro, Instant)` (upsert; violação de `namoro_exige_intencao_fk`, lida por `PSQLException.getServerErrorMessage().getConstraint()` → `INTENCAO_SEM_NAMORO`), `apagar`.
- `ServicoDePerfil.salvar`: violação da FK no `atualizar` → `VERSAO_OBSOLETA`.
- Lote da retenção numa instrução: `with apagadas as (delete from localizacao where conta_id in (select conta_id from localizacao where atualizada_em < :limite limit 1000) returning conta_id), reajustadas as (update preferencias set raio = 'SEM_LIMITE', raio_obrigatorio = false, versao = versao + 1, atualizado_em = :agora where conta_id in (select conta_id from apagadas) and raio <> 'SEM_LIMITE' returning 1) select count(*) from apagadas`.
- [ ] **Testes que falham.**
  - `FaixaEtariaTest`: (18, 99), (25, 25) aceitas; (17, 30), (30, 29), (18, 100) recusadas; com (25, 30): `contem` 24 falso, 25 e 30 verdadeiros, 31 falso. `RaioTest`: 5, 20, 50, vazio.
  - `DadosDeNamoroTest`: `OUTRO` sem descrição, `MULHER` com descrição, interesses vazios → `GENERO_INVALIDO`; `OUTRO` + `"agênero"` + `{OUTRO, MULHER}` aceito.
  - `PreferenciasApiTest`: `padroesSemLinha`; `semPerfil422`; `raioSemLocalizacaoRecusado` (zero linhas); `raioComLocalizacaoAceito` (célula `6gyf4` antes); `semLimiteZeraObrigatorio`; `faixaInvertida422`; `edicaoConcorrenteRecusada`; `revogarLocalizacaoVoltaParaSemLimite` (`ATE_5_KM` → `DELETE /api/perfil/localizacao` → `SEM_LIMITE`, `false`, `versao` 2); `formularioAbertoDepoisDaRevogacao409`.
  - `NamoroApiTest`: `gravaComConsentimento` (`{"genero":"MULHER","interesses":["HOMEM","OUTRO"],"consentimento":true}` → banco `{HOMEM,OUTRO}`, `consentido_em` = relógio); `semConsentimentoRecusado` (`false` e ausente, zero linhas); `intencaoAmizadeRecusada`; `generoAusente422`; `descricaoDe41Recusada`; `generoGranularRecusado` (`"MULHER_TRANS"` → 400); `getSemDadosDevolveNulos` (chaves exatas); `trocarParaAmizadeApagaDados`; `revogarApagaEMudaIntencao` (versão +1); `revogarSemPerfil204`; `religarNamoroExigeNovoConsentimento`; `bancoRecusaNamoroSemIntencao`; `bancoRecusaDesligarComDados` (`DataIntegrityViolationException`); `outraContaNaoLe`; `exclusaoDaContaApagaPreferenciasENamoro`.
  - `NamoroConcorrenciaTest` (Foco 3 e 4): `trocaDeIntencaoConcorrenteNuncaDeixaNamoroOrfao` (duas threads com latch entre o `apagar` e o `update` do perfil: no fim, nenhuma linha em `namoro` com `perfil.quer_namoro` falso, e um lado recebe 409 ou 422); `nomeDaConstraintChegaComDetalheDesligado` (com `logServerErrorDetail=false`, a violação chega com `getConstraint()` = `namoro_exige_intencao_fk`).
  - `RetencaoDeLocalizacoesTest.localizacaoVencidaVoltaORaio` (91 dias e `ATE_20_KM` → `SEM_LIMITE`, versão +1; 89 dias intacta).
- [ ] `./mvnw -q test -Dtest='FaixaEtariaTest,RaioTest,DadosDeNamoroTest,PreferenciasApiTest,NamoroApiTest,NamoroConcorrenciaTest,RetencaoDeLocalizacoesTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos, mais `PerfilApiTest` e `LocalizacaoApiTest` → PASS.
- [ ] Commit `feat(perfil): ajustes de busca e namoro com consentimento`

### Task D4: Orientação e pausa
**Files:** Create `mig/V24__orientacao_e_pausa.sql`; `m/perfil/{Orientacao,ServicoDeOrientacao,PausaDaConta}.java`; `m/perfil/dominio/{DadosDeOrientacao,RepositorioDeOrientacao,RepositorioDePausa}.java`; `m/perfil/web/{OrientacaoController,PausaController}.java`; `m/perfil/infra/{OrientacaoJdbc,PausaJdbc}.java`. Modify `PerfilController` (`pausada`). Test `t/perfil/dominio/DadosDeOrientacaoTest.java`, `t/perfil/{OrientacaoApiTest,PausaApiTest}.java`.
**Esquema V24:** `orientacao(conta_id uuid primary key references perfil(conta_id) on delete cascade, orientacao text not null check (orientacao in ('HETEROSSEXUAL','GAY','LESBICA','BISSEXUAL','PANSEXUAL','ASSEXUAL','DEMISSEXUAL','QUEER','QUESTIONANDO','OUTRA')), descricao text check (char_length(descricao) between 1 and 40), mostrar boolean not null, consentida_em timestamptz not null, check ((orientacao = 'OUTRA') = (descricao is not null)))`; `alter table perfil add column pausada_em timestamptz`.
**Interfaces:** Consumes D1 (`existe`, `DescricaoCurta`). Produces:
- `DadosDeOrientacao(Orientacao orientacao, Optional<DescricaoCurta> descricao, boolean mostrar, Instant consentidaEm)`: `OUTRA` ⇔ descrição ≤ 40, senão `IDENTIDADE_INVALIDA`; `String paraExibir()` (rótulo ou descrição).
- `RepositorioDeOrientacao`: `Optional<DadosDeOrientacao> buscar(ContaId)`; `void substituir(ContaId, DadosDeOrientacao)` (upsert); `void apagar(ContaId)`.
- `ServicoDeOrientacao.salvar`, nesta ordem: sem perfil → `PERFIL_NECESSARIO`; `consentimento` falso → `CONSENTIMENTO_NECESSARIO`; orientação nula ou descrição fora da regra → `IDENTIDADE_INVALIDA`; grava `consentida_em` = agora.
- `RepositorioDePausa`: `boolean pausar(ContaId, Instant)` (`set pausada_em = coalesce(pausada_em, :agora)`; falso sem perfil); `boolean retomar(ContaId)`; `boolean pausada(ContaId)`.
- [ ] **Testes que falham.**
  - `DadosDeOrientacaoTest`: `OUTRA` sem descrição e `GAY` com descrição → `IDENTIDADE_INVALIDA`; `OUTRA` com 41 recusada; `LESBICA` → `paraExibir()` `"Lésbica"`; `PANSEXUAL` → `"Pansexual"`.
  - `OrientacaoApiTest`: `getVazio` (nulos, `mostrarOrientacao` `false`); `semConsentimento422` (zero linhas); `comConsentimentoGravaOInstante`; `prefiroDescreverExigeTexto`; `orientacaoAusente422` (`IDENTIDADE_INVALIDA`); `mostrarGravado`; `apagarLimpaTudo` (204, GET nulo; repetir 204); `semPerfil422`; `pronomesNaoMudamAoApagarOrientacao`; `exclusaoApagaOrientacao`; `rotaIdentidadeNaoExiste` (`GET /api/perfil/identidade` → 404).
  - `PausaApiTest`: `pausarApareceNoGet`; `pausarDuasVezesMantemOPrimeiroInstante`; `voltarAAparecer`; `retomarSemPausa204`; `pausaNaoMudaAVersao`; `pausaSemPerfil422`; `pausaNaoMexeNaConta` (`conta.estado` `ATIVA`, apelido e bio intactos).
- [ ] `./mvnw -q test -Dtest='DadosDeOrientacaoTest,OrientacaoApiTest,PausaApiTest'` → FAIL.
- [ ] Implementar (merge só depois de I3: V23 antes de V24).
- [ ] Os mesmos e `PerfilApiTest` → PASS.
- [ ] Commit `feat(perfil): orientação com consentimento próprio e pausa da conta`

### Task D5: `Perfis`, projeções, perfil de outra pessoa e ADR 0008
**Files:** Create `m/perfil/{Perfis,PerfilPublico,PerfilCompleto,Preferencias,Pendencia}.java`; `m/perfil/dominio/{FichaDoPerfil,LeituraDePerfis}.java`; `m/perfil/infra/FichasJdbc.java`; `m/perfil/web/PerfisController.java`; `docs/adr/0008-privacidade-por-projecao.md`; `t/perfil/PerfisDeTeste.java`. Modify `PerfilController` (`prontoParaFila`, `pendencias`, `foto`); `t/conta/ContasDeTeste.java` (`criar`, `criarVerificada`). Test `t/perfil/{PerfisTest,PerfisApiTest}.java`, `t/perfil/PerfilApiTest.java` (casos novos), `t/conta/ContasDeTesteTest.java`.
**Interfaces:** Consumes D1–D4; `ConsultaDeContas.porId` (`estado`, `verificada`, `idadeEm`); `Clock`; I3 `FotosDoPerfil.doTitular`; I4 `FotosDoPerfil.urlDaAprovada`, `AcessoAFoto.nivel`; `FotosDeTeste.enviarAprovada`. Produces:
- `FichaDoPerfil(Perfil perfil, AjustesDeBusca ajustes, Optional<DadosDeNamoro> namoro, Optional<DadosDeOrientacao> orientacao, Optional<CelulaGeohash> celula, boolean pausada)`; `LeituraDePerfis.ficha(ContaId) -> Optional<FichaDoPerfil>`: uma consulta (`perfil` ⟕ `preferencias` ⟕ `namoro` ⟕ `orientacao` ⟕ `localizacao`); sem ajustes → `PADRAO`.
- `Perfis` (raiz): `ConsultaDeContas.porId` + `ficha` (+ `urlDaAprovada` só em `publico`/`completo`); `pendencias`: sem perfil `{PERFIL}`, `NAMORO` sem dados `{DADOS_DE_NAMORO}`.
- `PerfisController` `GET /api/perfis/{contaId}`: `record PerfilPublicoJson(String apelido, @Nullable String foto, @Nullable String pronomes, boolean verificado)`; acesso pela mesma decisão do `AcessoAFoto`.
- `PerfisDeTeste.definir`: perfil (intenções da `Preferencias`), ajustes, namoro consentido quando `generoProprio` presente (`"teste"` para `OUTRO`), célula.
- ADR 0008 (formato da 0006): célula geohash 5 e coordenada descartada; projeções por audiência; gênero, interesse e orientação nunca a terceiros; acesso a foto e a perfil público pela mesma decisão com 404 igual; borrada de 32 px; alternativas recusadas (distância exata arredondada, coordenada cifrada, projeção única com nulos).
- [ ] **Testes que falham.**
  - `PerfisTest` (relógio 2026-11-22T12:00Z): `publicoTrazApelidoPronomesEVerificado` (`ELE_DELE` → `"ele/dele"`); `semVerificacaoNaoTrazSelo` (`criarAtiva()`); `publicoSemFotoVemVazio`; `publicoComFotoAprovadaApontaParaBorrada`; `completoTrazIdade` (1990-05-10 → 36); `completoApontaParaNitida`; `completoSoTrazOrientacaoComMostrar` (`LESBICA`: vazio com `mostrar` falso, `"Lésbica"` com verdadeiro); `publicoNuncaTrazOrientacao`; `preferenciasMontaTudo`; `preferenciasSemNamoroVemVazias`; `preferenciasSemAjustesUsaPadrao`; `celulaDaLocalizacao`; `contaSuspensaFicaDeFora`; `semPerfilNaoFicaProntoParaFila`; `namoroSemDadosNaoFicaProntoParaFila`; `pausadaNaoFicaProntaParaFila`; `pausadaContinuaComPerfilPublico`; `semFotoFicaProntoParaFila`; `perfisDeTesteDefineTudo`.
  - `ContasDeTesteTest`: `criarComApelidoDeixaProntoParaFila` (`criar("Ana", 1990-05-10)`); `criarVerificadaTemSeloESemPerfil`.
  - `PerfisApiTest`: `terceiroVePerfilPublico` (chaves exatas); `perfilPublicoNuncaTrazBioNemNamoro` (bio canária `"canario-bio-7f3"`, sem `"genero"`); `contaExcluida404`, `contaSuspensa404`, `semPerfil404` (corpos iguais); `acessoNenhum404IgualAInexistente` (`@MockitoBean AcessoAFoto` → `NENHUM`); `idMalformado400`; `semSessao401`.
  - `PerfilApiTest`: `getTrazPendencias` (sem perfil `["PERFIL"]`, `false`; `{AMIZADE}` `[]`, `true`); `getTrazFotoDoTitular` (pendente por `FotosDeTeste.enviar` → `foto.pendente.nitida` termina em `/nitida`, `aprovada` nula); `fotoEPausaNaoMudamAVersao` (envio de foto, pausa e retomada → `versao` igual).
- [ ] `./mvnw -q test -Dtest='PerfisTest,PerfisApiTest,PerfilApiTest,ContasDeTesteTest'` → FAIL.
- [ ] Implementar; escrever a ADR 0008.
- [ ] Os mesmos, `ArquiteturaTest` e os testes de D1–D4 → PASS.
- [ ] Commits `feat(perfil): consulta de perfis em projeções para as outras etapas` e `docs(adr): 0008 privacidade por projeção`

### Task D6: Privacidade (único aumento de versão), glossário e guarda da exclusão
**Files:** Modify `r/legal/privacidade.md`, `t/retencao/PrazosDaPlataformaTest.java`, `CONTEXT.md` (`### Perfil`). Create `t/perfil/ExclusaoApagaTudoDoPerfilTest.java`.
**Interfaces:** Consumes D2–D5, I5. Produces:
- Front matter `versao:` = data do merge (AAAA-MM-DD): **o único aumento da etapa**.
- "O que tratamos": item `**Orientação:** opcional. É dado sensível: só com seu consentimento, nunca entra no pareamento e só aparece para suas conexões se você escolher mostrar.`; item `**Pronomes:** opcionais; aparecem para quem encontra você.`; "Gênero e quem você quer conhecer" ganha `Você pode apagar esses dados quando quiser.`
- Tabela, linhas novas: `| Preferências de pareamento (idade e distância) | até a exclusão da conta |`; `| Gênero e quem você quer conhecer | até você desligar o namoro, revogar o consentimento ou excluir a conta |`; `| Orientação | até você apagar ou excluir a conta |`. `LINHAS_SEM_POLITICA_DO_PERFIL` com as três.
- `CONTEXT.md`: **Perfil**, **Intenção**, **Ajustes de busca** (_evitar_ filtros), **Preferências de pareamento**, **Dados de namoro**, **Orientação**, **Pronomes**, **Célula**, **Faixa de distância**, **Pausa** (_evitar_ desativar, ocultar), **Selo verificado**, **Projeção**, **Foto pendente**, **Borrada**.
- [ ] **Testes que falham.**
  - `PrazosDaPlataformaTest.linhasDoPerfilSemPoliticaEstaoNaTabela` (as três chaves, cada texto contém `"até"`).
  - `ExclusaoApagaTudoDoPerfilTest.nenhumaLinhaNemArquivoSobra` (Foco 2): conta com perfil, pronomes, preferências, namoro, orientação, localização, foto aprovada e pendente → `ExclusaoDeConta.excluir(conta, true)` → zero linhas em `perfil`, `preferencias`, `namoro`, `orientacao`, `localizacao`, `foto` e nenhum arquivo dos ids em `perfil/**`; a linha de `conta` continua (lápide).
- [ ] `./mvnw -q test -Dtest='PrazosDaPlataformaTest,ConteudoLegalTest,DocumentosLegaisTest,ExclusaoApagaTudoDoPerfilTest'` → FAIL só nos casos novos (o guarda pode passar de primeira: então conferir que ele falha removendo um gancho localmente, sem commitar).
- [ ] Editar `privacidade.md` e `CONTEXT.md`.
- [ ] Os mesmos → PASS.
- [ ] Commit `docs(legal): privacidade dos dados de perfil, namoro e orientação`

## Trilha I — imagens e foto de perfil

### Task I1: Processador de imagem com libvips (e a instalação)
**Files:** Create `m/web/{Variante,ImagemProcessada,ProcessadorDeImagem,ProcessadorDeImagemVips,ConfigDeImagens}.java`; `src/test/resources/imagens/{retrato-exif-orientacao-6.jpg,paisagem.png,quadrado.webp,texto.txt,vips-lento.sh,README.md}` (cada imagem < 60 KB; JPEG 1200 × 800 com GPS e `Orientation=6`; `vips-lento.sh` executável, `sleep 30`). Modify `m/web/CodigoWeb.java`, `m/web/TratamentoDeErros.java` (`case 413 -> IMAGEM_GRANDE_DEMAIS`), `r/application.properties` (bloco `# imagens`), `r/application-prod.properties`, `src/test/resources/config/application.properties`, `.gitignore` (`/dados/`), `.github/workflows/ci.yml`. Test `t/web/{ProcessadorDeImagemVipsTest,OrcamentoDeTempoDaFotoTest,TratamentoDeErrosTest}.java`, `t/conta/web/MultipartEmMemoriaTest.java`.
**Interfaces:** Produces os de `m/web/` (processador e config). Consumes `MeterRegistry`.
**Algoritmo do `processar`** (`ProcessBuilder` com lista de argumentos, sem shell; `VIPS_BLOCK_UNTRUSTED=1`, `VIPS_CONCURRENCY=1`; stderr `Redirect.DISCARD`):
1. `Semaphore(simultaneos).tryAcquire(espera)`, senão 503; o `prazo` conta daqui.
2. Copia para `<dir>/tmp/<uuid>.entrada` contando bytes; > 5_242_880 → 413.
3. Mágicos: JPEG `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`; WebP `52 49 46 46` + 4 bytes + `57 45 42 50`; outro → 415.
4. `vipsheader -f width`/`-f height`; não numérico ou código ≠ 0 → 422; lado > 12_000 ou área > 40_000_000 → 422 antes de decodificar.
5. Nítida: `vips thumbnail <in> <tmp>/<uuid>-n.webp[Q=88,keep=none] 1600 --size down --export-profile srgb`, com `fail_on=truncated` na entrada (conferir no 8.15.1 e no 8.18.7 se a forma é `--fail-on truncated` ou `--option-string`; `arquivoTruncadoRecusado` trava).
6. Borrada: `vips thumbnail <n>.webp <t>-32.v 32` → `vips resize <t>-32.v <t>-400.v 12.5 --kernel linear` → `vips gaussblur <t>-400.v <tmp>/<uuid>-b.webp[Q=88,keep=none] 6`.
7. Dimensões da nítida por `vipsheader`; `finally` apaga entrada e `.v`; prazo estourado → `destroyForcibly()` e 503.
- Construtor roda `vips --version`: sem `vips-(\d+)\.(\d+)` ou < 8.15 → `IllegalStateException("libvips 8.15 ou mais novo é necessário: brew install vips (Mac) ou apt-get install libvips-tools")` (falha de boot proposital).
- [ ] **Passo 0 — instalação antes de qualquer teste:** no Mac, `vips --version`; se ausente ou < 8.15, `brew install vips` sob a trava de comando pesado (~1–2 GB, uma vez). Em `ci.yml`, nos jobs `verify` e `e2e`, antes do Maven: `sudo apt-get update && sudo apt-get install -y --no-install-recommends libvips-tools` (8.15.1).
- [ ] **Testes que falham** (`ProcessadorDeImagemVipsTest`, unitário com `@TempDir`, saídas lidas por `vipsheader`): `jpegViraWebpSemMetadados` (`RIFF…WEBP`; sem `exif-data`, `xmp-data`, `icc-profile-data`); `orientacaoExifAplicada` (800 × 1200); `maiorLadoLimitadoA1600` (3200 × 1600 → 1600 × 800); `imagemPequenaNaoAmpliada` (400 × 300); `borradaTemMaiorLado400` (400 × 267 ±1); `borradaNaoGuardaDetalhe` (desvio padrão < 25% do da nítida); `pngEWebpAceitos`; `textoComExtensaoJpgRecusado` (415); `dimensaoGiganteRecusada` (30_000 × 30_000 → 422 em < 2 s); `areaAcimaDe40MegapixelsRecusada` (8_000 × 5_001); `arquivoTruncadoRecusado` (2_048 primeiros bytes); `seisMegabytesRecusados` (6_291_456 B → 413); `processoLentoViraIndisponivel` (`prazo` 1 s → 503 em < 3 s, nenhum `vips-lento.sh` vivo); `filaCheiaViraIndisponivel`; `temporariosApagados`; `versaoAntigaDerrubaOBoot` (`vips-8.14.0` → mensagem com `8.15`). `OrcamentoDeTempoDaFotoTest`: `prazo + espera` < `reserva-maxima` (30 s). `TratamentoDeErrosTest`: `MaxUploadSizeExceededException` → 413 `IMAGEM_GRANDE_DEMAIS`; `MultipartEmMemoriaTest.arquivoAcimaDe6MbRecebe413` confere o `codigo`.
- [ ] `./mvnw -q test -Dtest='ProcessadorDeImagemVipsTest,OrcamentoDeTempoDaFotoTest,TratamentoDeErrosTest,MultipartEmMemoriaTest'` → FAIL.
- [ ] Implementar; gerar as fixtures com `vips` (EXIF por Pillow num venv descartável no scratchpad) e registrar os comandos no README.
- [ ] Os mesmos → PASS; `./mvnw -q verify` local; CI verde nos 4 jobs.
- [ ] **Avisar as outras sessões:** acrescentar ao `.claude/handoff/_mapa.md` do repositório principal (raiz = pasta pai de `git rev-parse --path-format=absolute --git-common-dir`), seção de comandos, a linha `Desde a Etapa 3 I1: todo teste Spring exige libvips ≥ 8.15 (Mac: brew install vips; CI: libvips-tools nos jobs verify e e2e).`
- [ ] Commit `feat(web): processador de imagem com libvips (webp q88 e borrada de 32 px)`

### Task I2: Armazém de imagens e limpeza dos temporários
**Files:** Create `m/web/{ArmazemDeImagens,ArmazemEmDisco,RetencaoDeTemporarios}.java`. Modify `t/retencao/PrazosDaPlataformaTest.java` (`"Arquivos temporários de imagem" → List.of("imagens-temporarias")`; a linha já existe na `privacidade.md`). Test `t/web/{ArmazemEmDiscoTest,RetencaoDeTemporariosTest}.java`.
**Interfaces:** Produces `ArmazemDeImagens` (canônica) e `RetencaoDeTemporarios` (`imagens-temporarias`, `PT1H`, mtime em `<dir>/tmp`; também esvazia `<dir>/tmp` no `ApplicationReadyEvent`). `ArmazemEmDisco`: `<dir>/<categoria>/<2 últimos hex do id>/<id>-nitida.webp` e `-borrada.webp`, arquivos 0600, diretórios 0700, `Files.move(..., ATOMIC_MOVE)`.
- [ ] **Testes que falham.** `ArmazemEmDiscoTest`: `guardarELer`; `subpastaPelosUltimosHex` (`…ab` → `perfil/ab/`); `arquivoSoLegivelPeloDono` (`rw-------`); `apagarRemoveAsDuas`; `apagarAusenteNaoFalha`; `lerAusenteVazio`; `categoriaInvalidaRecusada` (`"../x"`, `"Perfil"`, `""`); `guardadasAntesDeFiltraPorMtime`. `RetencaoDeTemporariosTest`: `nomeEPrazo`; `apagaSoOVencido` (61 min some, 59 fica, `expirar` = 1); `tamanhoContaSoOVencido`; `bootEsvaziaTmp`.
- [ ] `./mvnw -q test -Dtest='ArmazemEmDiscoTest,RetencaoDeTemporariosTest,PrazosDaPlataformaTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos → PASS.
- [ ] Commit `feat(web): armazém de imagens em disco e retenção dos temporários`

### Task I3: Foto de perfil (esquema e envio)
**Files:** Create `mig/V23__fotos.sql`; `m/perfil/dominio/{EstadoDaFoto,FotoDePerfil,RepositorioDeFotos}.java`; `m/perfil/{FotoId,FotoPendente,UrlsDaFoto,FotoEnviada,FotoDoTitular,UrlDaFoto,CodigoFoto,FotosDoPerfil}.java`; `m/perfil/infra/FotosJdbc.java`; `m/perfil/web/FotoController.java`; `t/perfil/FotosDeTeste.java`. Modify `r/application.properties` (regra `foto-conta`). Test `t/perfil/{FotosDoPerfilTest,FotoApiTest}.java`.
**Esquema V23:** `foto(id uuid primary key, conta_id uuid not null references conta(id) on delete cascade, estado text not null check (estado in ('PENDENTE','APROVADA','RECUSADA')), largura int not null check (largura between 1 and 1600), altura int not null check (altura between 1 and 1600), enviada_em timestamptz not null, decidida_em timestamptz, check ((estado = 'PENDENTE') = (decidida_em is null)))`; `create unique index foto_uma_por_estado on foto (conta_id, estado)`; `create index foto_fila on foto (enviada_em, id) where estado = 'PENDENTE'`. Sem coluna em `perfil`; upload não exige perfil.
**Interfaces:** Consumes `ProcessadorDeImagem`, `ArmazemDeImagens` (categoria `perfil`), `LimiteDeTaxa`, `Idempotencia`, `GeradorDeIds`, `Clock`, `Resumos.sha256Hex`. Produces:
- `RepositorioDeFotos`: `void travarConta(ContaId)` (`pg_advisory_xact_lock(hashtext('foto:' || :conta))`); `Optional<FotoDePerfil> porId(FotoId)`; `List<FotoDePerfil> daConta(ContaId)`; `Optional<FotoId> apagarDoEstado(ContaId, EstadoDaFoto)`; `void inserirPendente(FotoDePerfil)`; `boolean decidir(FotoId, EstadoDaFoto, Instant)` (`where estado = 'PENDENTE'`); `List<FotoPendente> pendentes(int)`; `List<FotoId> apagarDaConta(ContaId)`; `Set<UUID> existentes(Collection<UUID>)`.
- `FotosDoPerfil.enviar`: (1) `consumir("foto-conta", conta)`; (2) com chave, `reservar("foto:" + conta, chave, sha256Hex(arquivo), FotoEnviada.class)` (`JaRespondida` → devolve); (3) `processar` fora de transação, try-with-resources; (4) transação: `travarConta`, `apagarDoEstado(PENDENTE)`, `apagarDoEstado(RECUSADA)`, `inserirPendente`, `guardar`, `afterCompletion` (commit → apaga arquivos da pendente antiga; rollback → do novo), `concluir` por último; (5) falha depois de (2) → `liberar` e relança; `IOException` do armazém → 503 `PROCESSAMENTO_INDISPONIVEL`.
- `FotosDoPerfil.doTitular` → `FotoDoTitular` com `UrlsDaFoto` da aprovada e da pendente.
- `FotosDeTeste.enviar(ContaId)` (pelo próprio `enviar`, fixture `retrato-exif-orientacao-6.jpg`); `enviarAprovada(ContaId)` (envia e decide por `apagarDoEstado(APROVADA)` + `decidir(id, APROVADA, agora)`).
- [ ] **Testes que falham.**
  - `FotosDoPerfilTest`: `envioGravaPendente` (800 × 1200, dois arquivos); `segundoEnvioTrocaAPendente`; `envioApagaMarcaDeRecusa`; `falhaNoCommitApagaArquivosNovos`; `enviosSimultaneosDaMesmaContaDeixamUmaPendente`; `doTitularSemFoto` (três nulos); `doTitularComPendenteTrazAsDuasUrls`; `discoCheioVira503ELiberaAChave` (Foco 5: armazém que lança `IOException` → 503, nenhuma linha, e um segundo envio com a mesma chave não recebe `REQUISICAO_EM_ANDAMENTO`).
  - `FotoApiTest`: `uploadResponde202Pendente`; `mesmaChaveNaoCriaSegundaPendente` (`Idempotency-Key: foto-teste-0001`); `mesmaChaveOutroArquivoRecusada` (409); `rajadaDeUploadsBarrada` (11º → 429 com `Retry-After`); `semSessao401`; `contaAguardandoVerificacao403` (`CADASTRO_INCOMPLETO`); `textoRecebe415`; `semParteArquivo400`.
- [ ] `./mvnw -q test -Dtest='FotosDoPerfilTest,FotoApiTest'` → FAIL.
- [ ] Implementar (merge depois de D2: V22 antes de V23).
- [ ] Os mesmos, `ArquiteturaTest` e `PrazosDaPlataformaTest` → PASS.
- [ ] Commit `feat(perfil): envio da foto de perfil como pendente`

### Task I4: Acesso à foto (404 sem permissão) e cache
**Files:** Create `m/perfil/{NivelDeAcesso,AcessoAFoto,AcessoAFotoPadrao}.java`; `m/perfil/web/FotosController.java`. Modify `FotosDoPerfil` (`abrir`, `urlDaAprovada`). Test `t/perfil/{AcessoAFotoPadraoTest,AcessoAFotoApiTest}.java`.
**Interfaces:** Consumes `ConsultaDeContas.porId`, `RepositorioDeFotos.porId`, `ArmazemDeImagens.ler`. Produces `AcessoAFoto`, `urlDaAprovada`, a rota. Regra do `abrir`: ausente ou `RECUSADA` → vazio; titular → qualquer variante de `PENDENTE` ou `APROVADA`; outro → só `APROVADA`, `BORRADA` com `nivel ≥ BORRADA`, `NITIDA` com `nivel == NITIDA`; vazio → `FOTO_INEXISTENTE`. Cabeçalhos: `Content-Type: image/webp`; borrada sobrescreve o `no-store` do filtro com `private, max-age=300` e `Vary: Cookie`; nítida mantém `no-store`. CSP inalterada (`img-src 'self' data:`; `Cross-Origin-Resource-Policy: same-origin` impede hotlink).
- [ ] **Testes que falham.** `AcessoAFotoPadraoTest`: `titularVeNitida`; `outraContaVeBorrada`; `titularSuspensoNinguemVe`; `titularExcluidoNinguemVe`. `AcessoAFotoApiTest`: `titularVeAPendenteNitida` (`RIFF`); `terceiroNaoVePendente`; `terceiroVeSoABorradaDaAprovada` (nítida 404 sem o id no corpo); `idInexistenteEProibidoRespondemIgual` (byte a byte); `fotoNovaNaoDerrubaAAprovada` (`urlDaAprovada(A, BORRADA)` aponta para a antiga); `nitidaSemCache` e `borradaComCachePrivado` (Foco 1); `varianteDesconhecida404` (`/original`); `semSessao401`.
- [ ] `./mvnw -q test -Dtest='AcessoAFotoPadraoTest,AcessoAFotoApiTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos → PASS.
- [ ] Commit `feat(perfil): acesso à foto, borrada para terceiros e nítida para o titular`

### Task I5: Fila de aprovação, remoção, exclusão e órfãos
**Files:** Create `m/perfil/{FilaDeFotos,FotoAoExcluirConta}.java`; `m/perfil/infra/RetencaoDeFotosOrfas.java`. Modify `FotosDoPerfil` (`removerTodas`), `FotoController` (`DELETE /api/perfil/foto`), `r/legal/privacidade.md` (só corpo), `t/retencao/PrazosDaPlataformaTest.java`. Test `t/perfil/{FilaDeFotosTest,ExclusaoComFotoTest,RetencaoDeFotosOrfasTest}.java`, `t/perfil/FotoApiTest.java`.
**Interfaces:** Consumes `RepositorioDeFotos`, `ArmazemDeImagens`, `AoExcluirConta`, `PoliticaDeRetencao`, `Lotes`. Produces:
- `FilaDeFotos` (G5, sem HTTP): `aprovar` = `porId` → `travarConta` → `apagarDoEstado(APROVADA)` → `decidir(APROVADA)` → `apagarDoEstado(RECUSADA)`, arquivos da anterior no `afterCommit`; `recusar` = `apagarDoEstado(RECUSADA)` → `decidir(RECUSADA)`, arquivos no `afterCommit`.
- `removerTodas` = `travarConta` → `apagarDaConta`; arquivos no `afterCommit`; falha vira WARN `foto <id>` e fica para o varredor. Ordem das travas: advisory `foto:<conta>` → linhas de `foto` → linha de `conta`.
- `RetencaoDeFotosOrfas`: `fotos-orfas`, `P1D`; lotes de 1000 por `guardadasAntesDe("perfil", limite, 1000)` menos `existentes`.
- `privacidade.md`: `| Arquivo de foto trocada, recusada ou removida | apagado na hora; o que sobrar de uma falha sai em 1 dia |`; `PrazosDaPlataformaTest`: essa chave → `List.of("fotos-orfas")`.
- [ ] **Testes que falham.** `FilaDeFotosTest`: `pendentesEmOrdemDeEnvio`; `limiteForaDaFaixaRecusado` (0 e 51); `aprovarTrocaAAtual`; `recusarApagaArquivosEMarca` (`recusadaEm` = relógio); `aprovarIdInexistente`, `aprovarJaAprovada` → `FOTO_INEXISTENTE`; `aprovarERecusarSimultaneosTemUmVencedor`. `ExclusaoComFotoTest`: `exclusaoApagaLinhasEArquivos`; `exclusaoDesfeitaMantemArquivos`; `envioDuranteExclusaoNaoDeixaFoto`. `FotoApiTest`: `deleteRemoveTudo` (nítida → 404, `doTitular` nulo); `deleteSemFoto204`. `RetencaoDeFotosOrfasTest`: `arquivoSemLinhaVencidoSome`; `arquivoComLinhaFica`; `orfaoRecenteFica` (23 h); `tamanhoContaOrfaosVencidos`.
- [ ] `./mvnw -q test -Dtest='FilaDeFotosTest,ExclusaoComFotoTest,RetencaoDeFotosOrfasTest,FotoApiTest,PrazosDaPlataformaTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos e a suíte de `perfil` e `retencao` → PASS.
- [ ] Commit `feat(perfil): fila de aprovação, remoção e limpeza dos arquivos da foto`

## Trilha F — front e e2e
Skills de tela: `frontend-design`, `interaction-design`, `ux-writing` antes; `accessibility-audit`, `design-critique` antes do commit. F1–F9 rodam com `fetch` falso. Rótulos e frases ficam em `fe/telas/perfil/textos.ts`.

### Task F1: Transporte e cliente da API de perfil
**Files:** Modify `fe/api/http.ts`, `fe/api/http.test.ts`, `fe/rotas/consultas.ts`. Create `fe/api/perfil.ts`, `fe/api/perfil.test.ts`.
**Interfaces:** Consumes o contrato REST. Produces `api/perfil.ts` e as consultas canônicas.
- [ ] **Testes que falham.** `http.test.ts`: `putEnviaJsonComXDuora`; `deleteSemCorpo`. `perfil.test.ts`: `obterMeuPerfilLeFotoComNitidaEBorrada`; `perfilSemFotoTemAprovadaEPendenteNulas`; `pronomesForaDaListaViramRespostaInvalida`; `generoGranularViraRespostaInvalida` (`"MULHER_TRANS"`); `salvarPerfilMandaVersaoNulaEPronomes`; `versaoObsoletaReconhece409` (falso para `CHAVE_DE_IDEMPOTENCIA_REUSADA`); `enviarFotoMandaMultipartComChave` (campo `arquivo`, sem `Content-Type` à mão); `obterNamoroSemDadosViraNull` (200 com `genero` nulo); `obterOrientacaoSemDadosViraNull`; `obterPerfilPublico404ViraNull`; `perfilPublicoComChaveExtraContinuaValido`; `ativarLocalizacaoMandaSoLatitudeELongitude`; `apagarNamoroUsaDelete`; `orientacaoUsaRotaOrientacao` (`/api/perfil/orientacao`).
- [ ] `pnpm test -- api/perfil api/http` → FAIL.
- [ ] Implementar (schemas valibot com `v.picklist` dos enums canônicos; `null` só onde o contrato diz).
- [ ] PASS; `pnpm lint`.
- [ ] Commit `feat(front): cliente da API de perfil e consultas`

### Task F2: Rotas, guarda, navegação e cabeçalhos
**Files:** Modify `fe/rotas/{caminhosDaCasca.ts,guardas.ts,rotas.tsx,rotas.test.tsx,guardas.test.ts}`, `fe/telas/conta/{MinhaConta.tsx,MinhaConta.test.tsx,textos.ts}`, `fe/telas/verificacao/{Desfecho.tsx,Desfecho.test.tsx,textos.ts}`, `fe/sw/rotas.test.ts`, `m/web/{RotasDoSpa,CabecalhosDeSeguranca}.java`, `t/web/{RotasDoSpaTest,CspDaVerificacaoTest}.java`, `t/pwa/ChunksDoFrontTest.java`. Create `fe/telas/perfil/{LayoutDoPerfil.tsx,LayoutDoPerfil.test.tsx,textos.ts}`.
**Interfaces:** Consumes `exigirConta`, `consultaDoMeuPerfil`. Produces `comContaAtiva`, `PREFIXOS_DA_CASCA`, `RotasDoSpa.PREFIXOS = List.of("/perfis/")` (registrado como `/perfis/{contaId}`), `PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(self)"`, `PERMISSIONS_POLICY_VERIFICACAO = "camera=(self), microphone=(), geolocation=()"`, `no-cache` para caminho com prefixo. Árvore `lazy`: `perfil` → `LayoutDoPerfil` (`index` `MeuPerfil`; `preferencias`; `namoro`; `orientacao`), `perfis/:contaId` → `PerfilDeOutraPessoa`; todo loader passa por `comContaAtiva`. `LayoutDoPerfil`: `<nav aria-label="Seu perfil">` com `Meu perfil`, `Preferências`, `Namoro`, `Orientação` (`aria-current="page"`), `Minha conta` e `Voltar ao terraço` (`<a href="/">`). `MinhaConta` (só `ATIVA`, antes de "Sair"): `h2` `Seu perfil` com `Meu perfil` e `Preferências de encontro`. `Desfecho` aprovada: link principal `Montar meu perfil` (`/perfil`).
- [ ] **Testes que falham.** `rotas.test.tsx`: `caminhosDaCascaSaoOsDasRotas`; `perfilComContaAguardandoVaiParaVerificacao` (`obterMeuPerfil` não chamado); `perfilAnonimoVaiParaEntrar`; `perfisComIdAbreATelaDeOutraPessoa`. `LayoutDoPerfil.test.tsx`: `marcaAPaginaAtual`; `voltarAoTerracoEPaginaInteira`. `MinhaConta.test.tsx`: `contaAtivaTemLinksDoPerfil`; `contaAguardandoNaoTemLinksDoPerfil`. `Desfecho.test.tsx`: `aprovadaLevaAoPerfil`. `sw/rotas.test.ts`: `fotoDePerfilNuncaPassaPeloWorker`; `navegacaoParaPerfisVemDaRede`. Java: `RotasDoSpaTest.prefixoDePerfisEncaminhaParaOIndex`, `prefixosIguaisAosDoFront`, `perfisSemIdContinua404`; `CspDaVerificacaoTest.permissionsPolicyFechaGeolocalizacaoNaVerificacao`, `permissionsPolicyDoSiteLiberaGeolocalizacaoDaOrigem`, `perfisComIdTemNoCache`; `ChunksDoFrontTest.telasDoPerfilForaDoJsInicial` (JS inicial sem `/perfil/localizacao`; um chunk `app/MeuPerfil-*.js`; nenhum chunk novo > 512_000 B; roda depois do build da F4).
- [ ] `pnpm test` e `./mvnw -q test -Dtest='RotasDoSpaTest,CspDaVerificacaoTest'` → FAIL.
- [ ] Implementar.
- [ ] PASS; `pnpm lint`; `./mvnw -q spotless:apply`.
- [ ] Commit `feat(front): rotas do perfil, navegação a partir da conta e Permissions-Policy`

### Task F3: Foto ou silhueta, selo e cartão do perfil
**Files:** Create `fe/componentes/{FotoOuSilhueta,SeloVerificado,CartaoDoPerfil}.tsx` + testes; `fe/telas/perfil/{contar.ts,contar.test.ts}`. Modify `fe/telas/conta/MinhaConta.tsx` (importa `MarcaDoSelo`).
**Interfaces:** Produces os componentes canônicos e `contarCaracteres(texto: string): number` (code points). Desenho: foto quadrada `--raio`, borda `noite-700`; silhueta `ceu-400` sobre `noite-800`, busto sem gênero, `role="img"` `aria-label="Sem foto"`; `object-fit: cover`, `loading="lazy"` (`eager` no cartão principal); selo `MarcaDoSelo` + `Verificado` em `verde-agua`, `forced-colors:text-[CanvasText]`.
- [ ] **Testes que falham.** `semUrlDesenhaSilhueta`; `comUrlUsaOAlt`; `erroAoCarregarViraSilhueta`; `seloTemTextoAlemDoIcone` (SVG `aria-hidden="true"`); `mostraPronomesQuandoHa`; `semPronomesNaoDeixaEspacoVazio`; `seloSoComVerificado`; `contar`: `"🙂"` → 1, `"a\nb"` → 3, 20 emojis → 20.
- [ ] `pnpm test -- componentes telas/perfil/contar` → FAIL.
- [ ] Implementar.
- [ ] PASS; `MinhaConta.test.tsx` verde.
- [ ] Commit `feat(front): foto ou silhueta, selo verificado e cartão do perfil`

### Task F4: Meu perfil (apelido, pronomes, intenções, bio, versão obsoleta)
**Files:** Create `fe/telas/perfil/{MeuPerfil.tsx,MeuPerfil.test.tsx,formularioDoPerfil.ts,formularioDoPerfil.test.ts}`. Modify `textos.ts`.
**Interfaces:** Consumes F1, F3. Produces `type ErrosDoPerfil = Partial<Record<"apelido" | "descricaoPronomes" | "intencoes" | "bio", string>>`; `validar(e: EdicaoDoPerfil): ErrosDoPerfil`; `deMeuPerfil(p: MeuPerfil): EdicaoDoPerfil`; `tiraNamoro(salvo: MeuPerfil, e: EdicaoDoPerfil): boolean`; `MeuPerfil()` (compõe `EnviarFoto` e `PausaDaConta` da F5).
**Tela (coluna única):** faixa da pausa (F5); `h1` `Meu perfil` e, com `versao === null`, `Seu perfil é o que as pessoas veem quando encontram você.`; seção `Foto` (F5); formulário: `Apelido` (dica `Até 20 caracteres.`), fieldset `Pronomes (opcional)` (`Não mostrar`, `ela/dela`, `ele/dele`, `elu/delu`, `Prefiro descrever` → `Seus pronomes`, `Até 20 caracteres.`), fieldset `Como você quer usar o Duora` (`Amizade`, `Namoro`), `Sobre você` (`Opcional.`, contador `n/280` por `aria-describedby`), botão `Salvar perfil` / `Salvando…`; seção `Como os outros veem você` (`Quem encontra você vê a foto borrada. A nítida fica para suas conexões.` + `CartaoDoPerfil` com a borrada da aprovada ou silhueta); seção `Pausar a conta` (F5). Validação no envio, `aria-invalid` + `aria-describedby`, foco no primeiro inválido; `tiraNamoro` mostra `Ao salvar sem Namoro, apagamos seu gênero e quem você quer conhecer.`; sucesso `setQueryData(["perfil"])`, invalida `["perfil","namoro"]` `exact`, status `Perfil salvo.`; 409 → novo GET, formulário trocado, alerta com foco `Seu perfil mudou em outro aparelho. Confira e salve de novo.`; 422 → `mensagem` no campo do código (`IDENTIDADE_INVALIDA` → pronomes); outro erro `Não conseguimos salvar seu perfil. Tente de novo em instantes.` Mensagens: `Escreva um apelido.`, `Use até 20 caracteres.`, `Escreva seus pronomes com até 20 caracteres.`, `Marque Amizade, Namoro ou as duas.`, `Use até 280 caracteres.`
- [ ] **Testes que falham.** `formularioDoPerfil.test.ts`: `apelidoSoComEspacosRecusado`; `apelidoCom21CaracteresRecusado`; `apelidoCom20EmojisAceito`; `pronomesOutrosSemDescricaoRecusados`; `descricaoDePronomesCom21Recusada`; `intencoesVaziasRecusadas`; `bioCom281Recusada`; `tiraNamoroSoQuandoOSalvoTinha`. `MeuPerfil.test.tsx`: `enviaVersaoNoPut` (3); `primeiraVezEnviaVersaoNula`; `versaoObsoletaRecarrega`; `avisoAoTirarNamoro`; `erroDoServidorVaiParaOCampo`; `contadorDaBio` (`3/280`); `salvoMostraStatus`; `prefiroDescreverAbreCampo`; `cartaoMostraABorradaDaAprovada`.
- [ ] `pnpm test -- telas/perfil` → FAIL.
- [ ] Implementar.
- [ ] PASS; `pnpm build`; `./mvnw -q test -Dtest=ChunksDoFrontTest`.
- [ ] Commit `feat(front): meu perfil com pronomes, intenções e versão obsoleta`

### Task F5: Envio de foto e pausa da conta
**Files:** Create `fe/telas/perfil/{etapasDaFoto.ts,etapasDaFoto.test.ts,EnviarFoto.tsx,EnviarFoto.test.tsx,lerPrevia.ts,lerPrevia.test.ts,PausaDaConta.tsx,PausaDaConta.test.tsx}`. Modify `MeuPerfil.tsx`, `textos.ts`.
**Interfaces:** Consumes `enviarFoto`, `pausarConta`, `voltarAAparecer`, `novaChaveDeIdempotencia`, `textoDeEspera`. Produces (`etapasDaFoto.ts`, puro): `TAMANHO_MAXIMO = 5_242_880`; `TIPOS_ACEITOS = ["image/jpeg","image/png","image/webp"]`; `type AvisoDaFoto = {tipo: "grande"|"formato"|"invalida"|"indisponivel"} | {tipo: "limite"; segundos: number} | {tipo: "servidor"; mensagem: string}`; `EtapaDaFoto` = `parada{aviso?}` | `previa{arquivo, previa, chave}` | `enviando{…}` | `falhou{…, aviso}` | `enviada`; `EventoDaFoto` = `escolheu{arquivo, previa, chave}` | `recusadaNoCliente{aviso}` | `previaFalhou` | `enviar` | `enviado` | `envioRecusado{erro}` | `descartar`; `conferirNoCliente(f: File): AvisoDaFoto | null`; `avisoDoErro(e: unknown): AvisoDaFoto` (413 grande; 415 formato; 422 `IMAGEM_INVALIDA` invalida; 429 limite com `Retry-After` ou 60; 409 `REQUISICAO_EM_ANDAMENTO`, 503, rede → indisponivel; 409 `CHAVE_DE_IDEMPOTENCIA_REUSADA` → indisponivel com chave nova na próxima; outro com `mensagem` → servidor); `transicao(e, ev)` (`falhou` + `enviar` mantém a chave; `escolheu` fora de `enviando` troca prévia e chave). `lerPrevia(arquivo: Blob): Promise<string>` (`FileReader.readAsDataURL`, nunca `blob:`).
**`EnviarFoto` (`h2` `Foto`):** `FotoOuSilhueta` grande com a pendente, senão a aprovada (`alt` `Foto de {apelido}` ou `Sua foto`); legendas: `Você ainda não tem foto. Até ter uma aprovada, os outros veem uma silhueta.` · `Aguardando aprovação. Até lá, os outros veem uma silhueta.` · `Foto nova aguardando aprovação. Até lá, vale a anterior.` · `Sua última foto não foi aprovada.`; a política exata; dica `JPEG, PNG ou WebP, até 5 MB.`; `<input type="file" accept="image/jpeg,image/png,image/webp" class="peer sr-only">` com `<label>` botão `Escolher foto`; prévia `Prévia da foto escolhida` com `Enviar foto` / `Enviando…` e `Escolher outra`; `enviada` invalida `["perfil"]` e mostra `Foto enviada. Ela aparece depois da aprovação.` com foco; avisos (`role="alert"`): `A foto pode ter até 5 MB. Escolha uma menor.` · `Envie uma foto em JPEG, PNG ou WebP.` · `Não conseguimos abrir esta foto. Escolha outra.` · `Não conseguimos enviar a foto agora. Tente de novo em instantes.` (+ `Tentar de novo`) · `textoDeEspera(segundos)` · a `mensagem`.
**`PausaDaConta`:** `h2` `Pausar a conta`, texto `Enquanto a conta estiver pausada, você não entra na fila nem recebe avisos de pessoas compatíveis. Suas conexões e conversas continuam.` (G2), botão `Pausar` / `Pausando…`; pausada: faixa `role="status"` `Sua conta está pausada.` no topo e botão `Voltar a aparecer` / `Voltando…`; sem diálogo; invalida `["perfil"]`, foco na faixa ou no botão; falha `Não conseguimos mudar a pausa. Tente de novo em instantes.`
- [ ] **Testes que falham.** `etapasDaFoto.test.ts`: `arquivoDe5MBMais1Recusado`; `arquivoDeExatos5MBAceito`; `gifRecusadoNoCliente`; `tipoVazioVaiAoServidor`; `retentativaMantemAChave`; `chaveReusadaGeraChaveNova`; `limiteSemRetryAfterEspera60`; `escolherDuranteEnvioNaoTroca`. `lerPrevia.test.ts`: `previaEDataUrl`. `EnviarFoto.test.tsx`: `recusaAcimaDe5MBSemEnviar`; `previaAntesDeEnviar`; `enviaComIdempotencyKey`; `pendenteSemAprovadaMostraLegenda`; `recusadaMostraAviso`; `politicaComTextoExato`; `acceptListaSoJpegPngWebp`; `indisponivelPermiteTentarDeNovoComAMesmaChave`. `PausaDaConta.test.tsx`: `pausarMostraOTextoExato`; `pausarChamaPost`; `pausadaMostraFaixaEVoltarAAparecer`; `voltarChamaDelete`.
- [ ] `pnpm test -- telas/perfil` → FAIL.
- [ ] Implementar.
- [ ] PASS.
- [ ] Commit `feat(front): envio de foto com prévia e pausa da conta`

### Task F6: Perfil de outra pessoa
**Files:** Create `fe/telas/perfil/{PerfilDeOutraPessoa.tsx,PerfilDeOutraPessoa.test.tsx}`.
**Interfaces:** Consumes `consultaDoPerfilPublico`, `CartaoDoPerfil`. Desenho: `h1` com o apelido, `CartaoDoPerfil` grande, `Minha conta` e `Voltar ao terraço`; `null`: `h1` `Perfil indisponível`, `Este perfil não existe ou não está disponível.`, link `Minha conta`; título `{apelido} · Duora` ou `Perfil indisponível · Duora`.
- [ ] **Testes que falham.** `mostraABorrada` (`alt` `Foto de Ana`); `semFotoMostraSilhueta`; `perfilInexistenteMostraIndisponivel`; `nuncaPedeANitida`; `seloVerificadoVisivel`.
- [ ] `pnpm test -- PerfilDeOutraPessoa` → FAIL.
- [ ] Implementar.
- [ ] PASS.
- [ ] Commit `feat(front): perfil de outra pessoa com foto borrada ou silhueta`

### Task F7: Localização e preferências
**Files:** Create `fe/geo/{obterPosicao.ts,obterPosicao.test.ts}`, `fe/telas/perfil/{etapasDaLocalizacao.ts,etapasDaLocalizacao.test.ts,Localizacao.tsx,Localizacao.test.tsx,Preferencias.tsx,Preferencias.test.tsx}`. Modify `textos.ts`.
**Interfaces:** Consumes F1. Produces `obterPosicao`, `arredondar`, `VIGIA_DA_PERMISSAO_MS` (canônicos; `getCurrentPosition` com `{enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000}`; sem geolocation/`TIMEOUT`/`POSITION_UNAVAILABLE` → `indisponivel`; `PERMISSION_DENIED` → `negada`; sem callback em 20 s → `semResposta`; nunca loga) e `etapasDaLocalizacao.ts` puro: `EtapaDaLocalizacao` = `inativa{falha?: FalhaDePosicao | "servidor"}` | `pedindo` | `gravando` | `ativa` | `desativando`; eventos `pedir`, `posicaoObtida`, `posicaoFalhou{falha}`, `gravada`, `gravacaoFalhou`, `desativar`, `desativada`; `transicao`; `raioDisponivel(e)` só em `ativa`. A coordenada nunca entra em estado: `obterPosicao()` e `ativarLocalizacao()` na mesma função assíncrona.
**`Localizacao`** (`h2` dentro de Preferências, antes de `Distância`): `Guardamos só uma área de cerca de 5 km, nunca o ponto exato.` + `Usar minha localização` (o clique é o consentimento); `Esperando a permissão do navegador…`; `Localização ativa (área aproximada)` + `Desativar localização`; falha `Não conseguimos sua localização. A distância fica em "Sem limite".` (+ com `negada`: `Para tentar de novo, libere a localização nas configurações do navegador.`); `Localização desativada. A distância voltou para "Sem limite".`; falha e desativação marcam `SEM_LIMITE` e invalidam `["perfil","preferencias"]`.
**`Preferencias`** (`h1` `Preferências de encontro`): fieldset `Idade de quem você quer conhecer` (`De`, `Até`, `number`, 18–99, `inputMode="numeric"`); fieldset `Distância` (`Até 5 km`, `Até 20 km`, `Até 50 km`, `Sem limite`; sem localização os três primeiros com `aria-disabled="true"` e `Ative a localização para escolher uma distância.`, mudança ignorada); fieldset `Como usar a distância` só com raio: `Só pessoas dentro do raio` / `Prefiro quem está mais perto, mas aceito mais longe`; `Salvar preferências` / `Salvando…` → `Preferências salvas.`; 409 → `Suas preferências mudaram em outro aparelho. Confira e salve de novo.`; 422 `LOCALIZACAO_NECESSARIA` → `mensagem` sob `Distância` e volta a `Sem limite`; idade `Use idades de 18 a 99, com "De" menor ou igual a "Até".`
- [ ] **Testes que falham.** `obterPosicao.test.ts`: `negadaViraNegada`; `timeoutViraIndisponivel`; `semCallbackEm20sViraSemResposta`; `sucessoArredondaADuasCasas` (-23.550512, -46.633308 → -23.55, -46.63); `semGeolocationViraIndisponivel`; `pedeComAsOpcoesDoContrato`. `etapasDaLocalizacao.test.ts`: `raioSoComAtiva`; `falhaVoltaParaInativaComMotivo`; `desativarVoltaParaInativa`. Telas: `localizacaoNegadaVoltaParaSemLimite`; `semRespostaVoltaParaSemLimite`; `raioDesabilitadoSemLocalizacao`; `ativarMandaPutComCoordenadaArredondada`; `coordenadaNuncaVaiAoDomNemAoConsole`; `comoUsarSoComRaio`; `semLimiteMandaObrigatorioFalso`; `versaoObsoletaRecarregaAsPreferencias`; `idadeDeMaiorQueAteRecusada`.
- [ ] `pnpm test -- geo telas/perfil` → FAIL.
- [ ] Implementar.
- [ ] PASS.
- [ ] Commit `feat(front): preferências de encontro e localização por área`

### Task F8: Namoro com aviso e consentimento
**Files:** Create `fe/telas/perfil/{Namoro.tsx,Namoro.test.tsx,formularioDoNamoro.ts,formularioDoNamoro.test.ts}`. Modify `textos.ts`.
**Interfaces:** Consumes `consultaDoMeuPerfil`, `consultaDoNamoro`, `salvarNamoro`, `apagarNamoro`, `DialogoDeConfirmacao`. Produces `validarNamoro(d: {genero: Genero | null; descricaoGenero: string; interesses: Genero[]}): Partial<Record<"genero" | "descricaoGenero" | "interesses", string>>`; `ROTULO_DO_GENERO` = Mulher, Homem, Pessoa não binária, Prefiro descrever; `ROTULO_DO_INTERESSE` = Mulheres, Homens, Pessoas não binárias, Pessoas que se descrevem de outra forma (G1).
**Tela (`h1` `Para namoro`):** sem `NAMORO`: só `O namoro está desligado. Para usar, marque Namoro em Meu perfil.` + `Ir para Meu perfil`, e o loader não pede `/api/perfil/namoro`. Com `NAMORO`: aviso `Gênero e interesse são dados sensíveis. Usamos só para encontrar pessoas compatíveis e nunca mostramos a ninguém. Você pode apagar quando quiser.`; fieldset `Seu gênero` com a ajuda `Escolha a opção com que você se identifica.`, `Prefiro descrever` abre `Como você se descreve` (`Até 40 caracteres.`); fieldset `Quem você quer conhecer`; caixa `Concordo com o uso desses dados para o pareamento` (desmarcada sem dados; marcada com `consentidoEm` + `Você concordou em {d de mês de aaaa}.` por `Intl.DateTimeFormat("pt-BR", {dateStyle: "long", timeZone: "America/Sao_Paulo"})`); sem a caixa, `Salvar dados de namoro` com `aria-disabled` e `Marque a caixa de consentimento para salvar.`; `Apagar dados de namoro` abre o diálogo `Apagar dados de namoro?` / `Apagamos seu gênero e quem você quer conhecer, e Namoro sai das suas intenções. Para voltar, preencha e concorde de novo.` / `Apagar dados`, sucesso invalida `["perfil"]` (prefixo) e `Dados de namoro apagados.` Validação: `Escolha seu gênero.`, `Escreva como você se descreve, com até 40 caracteres.`, `Marque ao menos uma opção.`; sucesso `Dados de namoro salvos.`
- [ ] **Testes que falham.** `formularioDoNamoro.test.ts`: `outroSemDescricaoRecusado`; `descricaoCom41Recusada`; `interessesVaziosRecusados`; `descricaoSoVaiComOutro`. `Namoro.test.tsx`: `namoroSoComIntencaoDeNamoro`; `salvarNamoroExigeConsentimento`; `consentimentoNovoNuncaVemMarcado`; `prefiroDescreverPedeTexto`; `avisoComTextoExato`; `quatroOpcoesDeGenero` (exatamente os 4 rótulos); `apagarPedeConfirmacaoEChamaDelete`; `salvoMandaConsentimentoTrue`.
- [ ] `pnpm test -- Namoro formularioDoNamoro` → FAIL.
- [ ] Implementar.
- [ ] PASS.
- [ ] Commit `feat(front): dados de namoro com aviso e consentimento`

### Task F9: Orientação
**Files:** Create `fe/telas/perfil/{Orientacao.tsx,Orientacao.test.tsx}`. Modify `textos.ts`.
**Interfaces:** Consumes `consultaDaOrientacao`, `salvarOrientacao`, `apagarOrientacao`. Produces `ROTULO_DA_ORIENTACAO` (Heterossexual, Gay, Lésbica, Bissexual, Pansexual, Assexual, Demissexual, Queer, Questionando, Prefiro descrever). Tela (`h1` `Orientação`): aviso `Orientação é um dado sensível. É opcional, nunca entra no pareamento e só aparece para suas conexões se você quiser.`; fieldset `Sua orientação`, `Prefiro descrever` abre `Como você descreve sua orientação` (`Até 40 caracteres.`); caixa `Mostrar para minhas conexões`; consentimento `Concordo em guardar minha orientação no Duora` (regra de marcação da F8); `Salvar orientação` (`aria-disabled` sem consentimento) → `Orientação salva.`; `Apagar orientação` sem diálogo → `Orientação apagada.`
- [ ] **Testes que falham.** `orientacaoPedeConsentimento`; `prefiroDescreverPedeTextoDeAte40`; `mostrarVaiNoCorpo`; `apagarChamaDeleteEMostraStatus`; `semDadosComecaVazia`.
- [ ] `pnpm test -- Orientacao` → FAIL.
- [ ] Implementar.
- [ ] PASS.
- [ ] Commit `feat(front): orientação opcional com consentimento`

### Task F10: Page Object e jornadas e2e (a prova da etapa)
**Files:** Create `t/e2e/{PerfilPagina.java,PerfilIT.java,PreferenciasIT.java,OrientacaoEPausaIT.java}`.
**Interfaces:** Consumes `ContaE2eBase`, `Participantes.novo(Browser.NewContextOptions)`, `ContasDeTeste.criarVerificada()`/`criarAtiva()` (D5), `FilaDeFotos` (I5), fixture `retrato-exif-orientacao-6.jpg` (I1). Produces `final class PerfilPagina` (só papéis, rótulos e textos): `static de(TerracoPagina)`; `Page page()`; `abrirMeuPerfil/abrirPreferencias/abrirNamoro/abrirOrientacao(String raiz)`; `abrirPerfilDe(String raiz, ContaId)`; `preencherPerfil(String apelido, Set<String> intencoes, String bio)`; `escolherPronomes(String)`; `salvarPerfil()` (espera `Perfil salvo.`); `escolherFoto(Path)`; `enviarFoto()`; `Locator foto(String apelido)`; `Locator silhueta()`; `usarMinhaLocalizacao()`; `escolherDistancia(String)`; `escolherUsoDaDistancia(String)`; `salvarPreferencias()`; `preencherNamoro(String genero, Set<String> interesses)`; `consentir(String rotulo)`; `salvarNamoro()`; `pausar()`; `voltarAAparecer()`; `static List<String> violacoesDeCsp(Page)`; `static int[] dimensoes(Locator)`.
- [ ] **Testes que falham** (três navegadores).
  - `PerfilIT.perfilBorradoParaTerceirosENitidoParaOTitular`: A (`criarVerificada`) salva `Ana`, `{Amizade}`, `Gosto de céu limpo.`; escolhe a fixture (prévia `data:image/`); envia e vê `Aguardando aprovação. Até lá, os outros veem uma silhueta.`; B (`criarAtiva`) em `/perfis/{A}`: `h1` `Ana`, silhueta, nenhum `img[src*="/api/fotos/"]`; o teste aprova por `FilaDeFotos` (`pendentes(10)` da conta de A → `aprovar`); B recarrega: `foto("Ana")` termina em `/borrada`, largura > 0, a mesma URL com `/nitida` → 404, `Verificado` visível; A recarrega `/perfil`: `/nitida`, altura > largura; `violacoesDeCsp` vazio nas duas.
  - `PerfilIT.versaoObsoletaRecarregaNoSegundoAparelho` (bio `um` × `dois` → alerta exato, campo com `um`).
  - `PerfilIT.trocaDeContaNoMesmoContextoNaoServeANitidaDoCache`: A abre a própria nítida, sai; B entra no mesmo contexto e pede a URL da nítida de A → 404.
  - `PreferenciasIT`: `ativarLocalizacaoEUsarRaio` (`setGeolocation(-23.5505, -46.6333)`, `setPermissions(List.of("geolocation"))`; `Até 20 km` + `Só pessoas dentro do raio` seguem marcados ao recarregar); `localizacaoNegadaVoltaParaSemLimite` (`addInitScript` com erro `{code: 1, PERMISSION_DENIED: 1}`); `namoroGravadoEApagadoAoMudarParaAmizade` (depois de salvar sem Namoro, a tela mostra o texto de desligado e `page.request().get("/api/perfil/namoro")` traz `"genero":null`).
  - `OrientacaoEPausaIT`: `pausarEVoltar` (faixa e `pausada` no GET); `pronomesAparecemParaTerceiroEOrientacaoNao` (A: `elu/delu` e `Bissexual` com mostrar e consentimento; B em `/perfis/{A}` vê `elu/delu` e não `Bissexual`).
- [ ] `./mvnw -q -Pe2e verify -Dit.test='PerfilIT,PreferenciasIT,OrientacaoEPausaIT' -Dnavegador=chromium` → FAIL (um comando pesado por vez).
- [ ] Implementar o Page Object; tela faltando se corrige na task dela.
- [ ] PASS em `chromium`, `firefox` e `webkit`.
- [ ] Commit `test(e2e): perfil borrado para terceiros e nítido para o titular`

### Task F11: Axe e checagem no Android
**Files:** Modify `t/e2e/AxeContaIT.java`, `scripts/android/{conta-check.mjs,conta-check.test.mjs}`.
- [ ] **Testes que falham** (`AxeContaIT`, movimento reduzido, nenhuma regra desligada): `minhaContaAtivaComLinksDoPerfil`; `meuPerfilVazio`; `meuPerfilComErrosDeValidacao`; `meuPerfilComAvisoDeNamoro`; `meuPerfilVersaoObsoleta`; `meuPerfilPausado`; `fotoComPrevia`; `fotoRecusadaNoCliente`; `fotoPendente`; `fotoRecusada`; `fotoComLimite` (429 + `Retry-After: 300`); `perfilDeOutraPessoaComSilhueta`; `perfilDeOutraPessoaComBorrada`; `perfilIndisponivel`; `preferenciasSemLocalizacao`; `preferenciasComLocalizacaoAtiva`; `localizacaoNegada`; `namoroDesligado`; `namoroComAvisoEPrefiroDescrever`; `dialogoDeApagarNamoro`; `orientacao`. `conta-check.test.mjs`: `perfilAbreAsTelasDoPerfil` (`--perfil` captura `/perfil` e `/perfil/preferencias`).
- [ ] Rodar e ver falhar onde houver violação; corrigir na tela.
- [ ] Implementar `--perfil`; capturar no emulador (`scripts/android/emulador.sh subir`, `node scripts/android/conta-check.mjs --porta 8080 --perfil`, `emulador.sh descer`): alvo ≥ 44 px, teclado numérico em `De`/`Até`, e a falha da localização depois de 20 s sem resposta.
- [ ] `./mvnw -q -Pe2e verify -Dit.test=AxeContaIT` nos três navegadores; `pnpm test`, `pnpm lint`, `./mvnw -q verify` (JS inicial ≤ 102_400 B) → PASS.
- [ ] Commit `test(e2e): axe nas telas de perfil e captura no Android`

**Prova da etapa:** `PerfilIT.perfilBorradoParaTerceirosENitidoParaOTitular` verde nos três navegadores, com `AcessoAFotoApiTest`, `PerfisApiTest` e `ExclusaoApagaTudoDoPerfilTest`.

## Pendências entre etapas (só listar; os planos são editados depois)
- **Etapa 4:** `Localizacoes.atualizar` lança `LOCALIZACAO_SEM_CONSENTIMENTO` sem consentimento (chamar `consentida` antes ou tratar); `Genero` tem 4 valores (G1); pausa = G2 (sai da fila e do push "alguém compatível"; convite privado continua); `obterPosicao` vem de `fe/geo/obterPosicao.ts` (vigia de 20 s, 2 casas); `Perfis` faz 2 consultas por chamada: medir no ciclo com 200 candidatos e, se pesar, criar `Map<ContaId, Preferencias> preferencias(Set<ContaId>)`; `CONTA_PAUSADA` aponta para `Meu perfil` em `/perfil`.
- **Etapa 5:** `ProcessadorDeImagens` → `web.ProcessadorDeImagem`; `ArmazemDeImagens.caminho(...)` → `ler(...) -> Optional<Resource>`; o orçamento (`prazo` 15 s + `espera` 5 s) vale para as imagens do encontro; a P3 no `AcessoAFotoPadrao` também fecha `GET /api/perfis/{id}` (404) e quebra o passo de B no `PerfilIT` (reescrever com `EncontrosDeTeste.parear`); `MINIATURA_TRIAGEM` muda o `enum Variante`; decidir apelido ao vivo × fotografia no início do encontro; `CartaoDoPerfil` serve para o par.
- **Etapa 8:** `caminho` → `ler`, e `copiar(String origem, String destino, UUID id)` entra na interface `ArmazemDeImagens`; `Perfis.publico` esconde suspensa e banida, então a auditoria precisa de leitura própria da moderação (teste: auditoria de conta banida mostra o apelido); `FilaDeFotos` já existe só como bean (G5), e a rota de admin é dela; o pré-requisito "Etapa 3 Task 5" passa a ser I5.
- **Também (fora das três):** Etapa 6 — a nítida já sai sempre com `no-store` (o teste `titularContinuaComMaxAge300` vira `nitidaSempreNoStore`), e `PerfilCompleto` traz também `pronomes`, `orientacao` e `verificado`. Etapa 10 — `DUORA_IMAGENS_DIR` no compose; o restic guarda fotos apagadas por até 8 semanas: texto na `privacidade.md` e teste do prazo.

## Riscos aceitos
- Prompt real de permissão (Firefox "Agora não") não é coberto por Playwright: conferência manual na F11.
- HEIC do iPhone depende de o Safari converter pelo `accept`: teste manual em aparelho iOS no piloto.
- libvips 8.15 (CI) × 8.18 (Mac) no `fail_on` e no `--export-profile`: só a CI pega a diferença; `arquivoTruncadoRecusado` precisa passar nas duas.
