# Etapa 2b — Verificação de idade: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`). Implementadores usam `superpowers:test-driven-development` ou `mattpocock-skills:tdd`.

**Objetivo:** a conta em `AGUARDANDO_VERIFICACAO` passa a `ATIVA` só depois de uma prova de vida com estimativa de idade aprovada (AWS) ou, na faixa de dúvida, de documento e selfie nova conferidos (Didit); menor de 18 é excluído e o e-mail bloqueado; nada de imagem fica guardado.

**Arquitetura:** o domínio puro (`conta/dominio/`) decide a idade (`DecisaoDeIdade`) e define as portas `ProvaDeVida`, `CredencialDoNavegador` e `ConferenciaDeDocumento`; os casos de uso na raiz de `conta/` contam tentativas no banco (V11/V12), usam `web/Idempotencia` com a chave = `sessaoId` e chamam os fornecedores fora de transação. Adapters reais em `conta/infra/` (AWS SDK v2 e `RestClient` da Didit) e falsos de dev/e2e escolhidos por propriedade; o perfil `prod` derruba o boot com falsos. No front, `/verificacao` é uma rota da casca de conta isolada por navegação de documento, com CSP própria servida pelo back e o detector do Amplify carregado por `import()` só depois do consentimento.

**Stack:** Java 25, Spring Boot 4.2, PostgreSQL 18/Flyway, AWS SDK for Java v2 2.59.10 (`rekognition`, `sts`, `url-connection-client`), `RestClient` + JDK `HttpClient` (Didit v3), Micrometer; React 19 + TS 6 + React Router 7.18.6 + TanStack Query, `@aws-amplify/ui-react-liveness` 3.7.0 + `aws-amplify` 6.22.2, Vitest, Playwright Java 1.65.1.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` ("18+ em todo o piloto", linhas 24–31). **Contratos (valem sobre este plano):** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. **Origem:** substitui as Tasks 12, 13, 14 e 17 de `docs/superpowers/plans/2026-11-22-etapa-2-contas.md`. **Base:** `main` com a 2a (4a23a91). Conferir pelo Context7 as APIs externas antes da task que as usa.

## Decisões do dono (2026-11-27)
- **D-B Documento primeiro:** na faixa de dúvida (`Low < 21` e `High ≥ 18`), frente e verso do RG/CNH e uma selfie nova num envio só, dentro dos 3 min da sessão de prova de vida (+US$ 0,015).
- **D-C** Didit sandbox na 2b; chave de produção na Etapa 10. **D-D** Didit sem repetição automática: 5xx/timeout viram 503 e a pessoa tenta de novo.
- **D-E (revista na Rodada 2)** CSP da `/verificacao` = política validada do C# sem o `wss` do hub, sem o hash do `<model-viewer>` e sem `lit-html` (`trusted-types 'none'`); `Permissions-Policy: camera=(self), microphone=()`.
- **D-F2** Detector falso do e2e só quando o servidor manda `modo:"falso"`; nada global no bundle.
- **Rodada 2:** A12 (operação AWS real) autorizada, no fim da 2b e depois do `/mcp` do dono; `/verificacao` é rota da casca com isolamento por documento; aviso de fotossensibilidade com caminho manual pelo e-mail do encarregado; sem interruptor `documento.habilitado`.

## Decisões da consolidação (uma linha cada; o dono pode reverter)
- **Uma exceção de fornecedor:** `FornecedorIndisponivel`; os adapters a lançam em 5xx, timeout, rede, 401/403/429; o caso de uso a traduz em 503 com o código da porta.
- **`resultado` único:** `ProvaDeVida.resultado` sempre devolve a referência (`Optional<Imagem>`); sem `resultadoComReferencia`. A selfie a descarta ao sair do método.
- **`modo` vem do bean:** `ProvaDeVida.modo()` (`AWS` no adapter real, `FALSO` no falso); o `GET` e o `POST /sessao` o expõem. Nenhum segundo interruptor.
- **`criarSessao(UUID tentativa)`:** `ClientRequestToken` = tentativa; `RoleSessionName` = `"duora-" + tentativa` (UUID inteiro: os 8 primeiros hex de um v7 se repetem por ~65 s).
- **Verso obrigatório (D-B):** a porta recebe `Imagem verso`, e a Didit sempre recebe `back_image` (opcional na doc atual).
- **`In Review` da Didit = não aceito:** a liveness do documento ficou inconclusiva; o caso de uso devolve `REPETIR` (novas fotos).
- **`REFAZER_PROVA_DE_VIDA` volta (409):** prova da sessão `DOCUMENTO` vencida ou ruim responde 409 sem concluir nada, para o front manter as fotos (4G pode passar de 3 min). **D14-bis (dono, 2026-11-28):** só a prova `EXPIRADA` descarta a tentativa e devolve o envio, e o reenvio com o mesmo `sessaoId` responde 404 `SESSAO_DE_VERIFICACAO_INVALIDA`; `FALHOU`, confiança < 90,0 e `CRIADA` gastam o envio, e o reenvio responde o mesmo 409.
- **Orçamento de tempo:** `resultado` ≤ 8 s (2 × `apiCallTimeout` 4 s) e `conferir` ≤ 15 s (1+9 s documento, 1+4 s rosto); somados (23 s) cabem na reserva de idempotência de 30 s. `OrcamentoDeTempoTest` trava.
- **Só a aprovação fica em `verificacao_idade`:** a recusa vira lápide + `email_bloqueado` (motivo `MENOR`); `DUVIDA` vive só na tentativa (1 dia).
- **Limite por dia no banco** (linhas de `tentativa_verificacao` em 24 h corridas com a conta travada); Bucket4j `verificacao-conta` só contra rajada.
- **Retry de 2 tentativas só na AWS** (chamadas idempotentes ou baratas); **sem `HealthIndicator`** (sondar custa dinheiro; o Timer basta).
- **Preço da Didit:** ID Verification US$ 0,20 + Face Match US$ 0,05. Faixa de dúvida = US$ 0,282 por pessoa; teto por conta/dia ≈ US$ 0,58.

## Restrições globais
- Tudo do arquivo de contratos e das Restrições globais da 2a (`2026-11-23-etapa-2a-contas.md`) vale aqui: camadas, `Clock`/`GeradorDeIds`, idade em `America/Sao_Paulo`, erros por `CodigoDeErro`, logs sem PII, segredos só por ambiente, Conventional Commits pt-BR com trailer, nunca `git add -A`, comandos pesados um por vez sob a trava.
- **Migrações:** V11 e V12, só na trilha V; nada editado em V1–V10.
- Nenhuma flag leva a conta a `ATIVA` sem verificação aprovada. Limiares fixos em código: aprova `Low ≥ 21`, recusa `High < 18`, confiança mínima 90,0.
- Selfie, documento e credencial curta nunca vão para banco, disco, log ou cache: só memória durante a requisição. Multipart com `file-size-threshold` ≥ `max-request-size`. AWS com `AuditImagesLimit=0` e sem `OutputConfig`; Didit com `save_api_request=false` e sem `vendor_data`.
- Nenhuma chamada a fornecedor dentro de transação de banco.
- **Proibido criar ou alterar recursos na AWS ou na Didit**, salvo na A12. Testes usam dublês, stubs do SDK ou `HttpServer` falso. Smokes reais (A10) ficam fora da CI.
- Região fixa `us-east-1` (SCP). Credencial do back explícita (`StaticCredentialsProvider`), nunca `~/.aws` nem `AWS_*`.
- Front: pt-BR, gênero neutro, voz ativa, axe em toda tela nova, sem `disabled` nativo (`aria-disabled` + `seDisponivel`); skills frontend-design, interaction-design e ux-writing antes de desenhar; accessibility-audit e design-critique antes do commit. JS inicial ≤ 102_400 B; só o chunk `verificacao-amplify-*` passa de 512_000 B.

## Interfaces canônicas
Prefixos: `m/` = `src/main/java/bipotheking/net/duora/`, `t/` = `src/test/java/bipotheking/net/duora/`, `mig/` = `src/main/resources/db/migration/`, `r/` = `src/main/resources/`, `fe/` = `frontend/`.

**Domínio (`m/conta/dominio/`, Java puro):**
| Tipo | Assinatura e regra |
|---|---|
| `record FaixaEstimada(int baixa, int alta)` | `0 ≤ baixa ≤ alta`, senão `IllegalArgumentException` |
| `enum SituacaoDaProva` | `CRIADA, EM_ANDAMENTO, CONCLUIDA, FALHOU, EXPIRADA` (adapter mapeia `CREATED…EXPIRED`; desconhecido → `FALHOU`) |
| `enum ResultadoDaVerificacao` | `APROVADA, DUVIDA, RECUSADA, REPETIR` |
| `enum MetodoDeVerificacao` / `FinalidadeDaTentativa` / `ModoDeCaptura` / `TipoDeImagem` | `ESTIMATIVA_FACIAL, DOCUMENTO` / `SELFIE, DOCUMENTO` / `AWS, FALSO` / `JPEG, PNG` |
| `final class Imagem` | `TAMANHO_MAXIMO = 5_242_880`; `static Optional<Imagem> ler(byte[])` (vazio se vazio, > 5 MB ou sem mágicos `FF D8 FF` / `89 50 4E 47 0D 0A 1A 0A`); `byte[] bytes()` (cópia); `TipoDeImagem tipo()`; `toString()` = `Imagem[JPEG, 1234 bytes]`; sem `equals` por conteúdo |
| `record ResultadoDaProva(SituacaoDaProva situacao, double confianca, Optional<FaixaEstimada> faixa, Optional<Imagem> referencia)` | faixa só com exatamente um rosto |
| `record ResultadoDoDocumento(boolean documentoAceito, Optional<LocalDate> dataDeNascimento, boolean rostoConfere)` | `toString` com `dataDeNascimento=***` |
| `record SessaoDeProva(String id, String regiao)` | `regiao` = `us-east-1` |
| `record CredencialCurta(String accessKeyId, String secretAccessKey, String sessionToken, Instant expiraEm)` | `toString` só com `accessKeyId` e `expiraEm` |
| `interface ProvaDeVida` | `ModoDeCaptura modo()`; `SessaoDeProva criarSessao(UUID tentativa)`; `ResultadoDaProva resultado(String sessaoId)` |
| `interface CredencialDoNavegador` | `CredencialCurta emitir(UUID tentativa)` |
| `interface ConferenciaDeDocumento` | `ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie)` |
| `class FornecedorIndisponivel extends RuntimeException` | `FornecedorIndisponivel(String motivo, @Nullable Throwable causa)`; motivo sem corpo, chave, ARN nem imagem. Outro tipo de exceção é bug (500) |
| `final class DecisaoDeIdade` | `IDADE_DE_APROVACAO = 21`, `CONFIANCA_MINIMA = 90.0`; `pelaSelfie(ResultadoDaProva)`: ≠ `CONCLUIDA`, confiança < 90,0 ou faixa vazia → `REPETIR`; `alta < 18` → `RECUSADA`; `baixa ≥ 21` → `APROVADA`; resto → `DUVIDA`. `provaServeParaDocumento(ResultadoDaProva)`: `CONCLUIDA`, ≥ 90,0, faixa e referência presentes. `peloDocumento(LocalDate declarada, ResultadoDoDocumento r, LocalDate hoje)`, nesta ordem: não aceito ou sem data → `REPETIR`; rosto não confere → `DUVIDA`; lida < 18 anos em `hoje` → `RECUSADA`; lida ≠ declarada → `DUVIDA`; senão `APROVADA` |
| `interface RepositorioDeVerificacoes` | `ReservaDeTentativa reservar(ContaId, FinalidadeDaTentativa, int limitePorDia, Instant agora)`; `associarSessao(UUID, String)`; `descartar(UUID)`; `Optional<Tentativa> porSessao(String)`; `boolean concluir(UUID, ResultadoDaVerificacao, Instant)`; `int contarDesde(ContaId, FinalidadeDaTentativa, Instant)`; `boolean duvidaDeSelfieDesde(ContaId, Instant)`; `gravarAprovacao(ContaId, MetodoDeVerificacao, Instant)`; `boolean aprovada(ContaId)`; `apagarDaConta(ContaId)` |
| `sealed interface ReservaDeTentativa` | `record Feita(UUID tentativa)`, `record Esgotada(Instant liberaEm)` |
| `record Tentativa(UUID id, ContaId conta, FinalidadeDaTentativa finalidade, @Nullable String sessaoId, Instant criadaEm, @Nullable ResultadoDaVerificacao resultado)` | — |

**Raiz (`m/conta/`):** `enum CodigoVerificacao implements CodigoDeErro` (tabela de erros abaixo); `LimiteDeVerificacaoExcedido extends ErroDeNegocio` (`(Duration ateLiberar)`, 429 + `Retry-After`); `@ConfigurationProperties("duora.verificacao") record LimitesDaVerificacao(int tentativasPorDia, int enviosDeDocumentoPorDia)` com `int limite(FinalidadeDaTentativa)`, ambos ≥ 1; `record SessaoDeCaptura(String sessaoId, String regiao, ModoDeCaptura modo, CredencialCurta credencial)`; `record DesfechoDaVerificacao(ResultadoDaVerificacao resultado, boolean pedeDocumento, int tentativasRestantesHoje, int enviosDeDocumentoRestantesHoje)`; `record SituacaoDaVerificacao(EstadoDaVerificacao estado, int tentativasRestantesHoje, int enviosDeDocumentoRestantesHoje, ModoDeCaptura modo)` + `enum EstadoDaVerificacao { AGUARDANDO, DUVIDA, APROVADA }`; `VerificacaoDeIdade` (`iniciar`, `concluirSelfie`, `situacao`); `VerificacaoPorDocumento` (`concluir`); `EfeitosDaVerificacao` (package-private); `boolean RepositorioDeContas.ativar(ContaId, Instant)`.

**Infra (`m/conta/infra/`, package-private):** `VerificacoesJdbc`, `RetencaoDeTentativas` (`tentativas-de-verificacao`, `P1D`), `ProvaDeVidaFalsa implements ProvaDeVida, CredencialDoNavegador`, `ConferenciaDeDocumentoFalsa`, `GuardaDaVerificacaoReal` (V); `@ConfigurationProperties("duora.verificacao.aws") record ConfiguracaoAws`, `@ConfigurationProperties("duora.verificacao.didit") record ConfiguracaoDidit`, `MedicaoDeFornecedor`, `ClientesAws`, `ProvaDeVidaAws implements ProvaDeVida, CredencialDoNavegador, AutoCloseable`, `ConferenciaDeDocumentoDidit`, `FornecedoresDeVerificacaoConfig` (A). Teste: `t/arquitetura/RegrasDeArquitetura.sdkDaAwsSoNaInfra(String raiz)`.

**Propriedades:**
| Propriedade | Padrão (`application.properties`) | `prod` | Dono |
|---|---|---|---|
| `duora.verificacao.provedor` | `falso` (`aws` liga o adapter real) | `aws` | V4 / A3 |
| `duora.verificacao.documento.provedor` | `falso` (`didit` liga o adapter real) | `didit` | V4 / A3 |
| `duora.verificacao.tentativas-por-dia` / `envios-de-documento-por-dia` | `3` / `2` | — | V4 |
| `duora.limites.regras.verificacao-conta.capacidade` / `.periodo` | `20` / `PT10M` | — | V5 |
| `spring.servlet.multipart.max-file-size` / `max-request-size` / `file-size-threshold` | `6MB` / `13MB` / `13MB` | — | V6 |
| `duora.verificacao.aws.access-key-id` / `secret-access-key` / `papel-captura-arn` | sem padrão | `${DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID}` / `${DUORA_AWS_VERIFICACAO_SECRET_ACCESS_KEY}` / `${DUORA_AWS_PAPEL_CAPTURA_ARN}` | A3 |
| `duora.verificacao.aws.conexao` / `tentativa` / `chamada` | `@DefaultValue` `PT1S` / `PT2S` / `PT4S` | — | A3 |
| `duora.verificacao.didit.url` / `api-key` / `limiar-rosto` | `https://verification.didit.me` / sem padrão / `30` | — / `${DUORA_DIDIT_API_KEY}` / — | A3 |
| `duora.verificacao.didit.conexao` / `leitura-do-documento` / `leitura-do-rosto` | `PT1S` / `PT9S` / `PT4S` | — | A3 |
| perfil `verificacao-falsa` (`r/application-verificacao-falsa.properties`) | fixa os dois provedores em `falso` (D-F2) | proibido (a guarda derruba) | V4 |

**Contrato REST (2b).** Regras da 2a: problem+json com `codigo`/`mensagem`, `X-Duora: 1` e mesma origem em mutação, `Cache-Control: no-store`, 401 `NAO_AUTENTICADO`, 403 `CONTA_INDISPONIVEL`. Todas com `@ContaAtual(cadastroConcluido = false)`; os três `POST` consomem `verificacao-conta` antes de tudo (429 `LIMITE_DE_TAXA`).
| Rota | Corpo | Sucesso | Erros | Task |
|---|---|---|---|---|
| `GET /api/conta/verificacao` | — | 200 `{"estado":"AGUARDANDO"\|"DUVIDA"\|"APROVADA","tentativasRestantesHoje":n,"enviosDeDocumentoRestantesHoje":n,"modo":"aws"\|"falso"}`; `DUVIDA` = tentativa `SELFIE` com `DUVIDA` nas últimas 24 h e conta em `AGUARDANDO_VERIFICACAO`; conta `ATIVA` → `APROVADA` | 409 `VERIFICACAO_FORA_DE_ORDEM` (`PENDENTE`) | V5 |
| `POST /api/conta/verificacao/sessao` | `{"consentimentoBiometrico":true,"finalidade":"SELFIE"\|"DOCUMENTO"}` (estrito; finalidade ausente → `SELFIE`) | 201 `{"sessaoId","regiao":"us-east-1","modo":"aws"\|"falso","credencial":{"accessKeyId","secretAccessKey","sessionToken","expiraEm"}}` + cabeçalho `Date` (do `Clock`) | 422 `CONSENTIMENTO_BIOMETRICO_NECESSARIO`; 409 `VERIFICACAO_FORA_DE_ORDEM` (conta ≠ `AGUARDANDO_VERIFICACAO`; `DOCUMENTO` sem `DUVIDA` de selfie em 24 h); 429 `LIMITE_DE_VERIFICACAO` + `Retry-After` (3 `SELFIE` ou 2 `DOCUMENTO` em 24 h); 503 `PROVA_DE_VIDA_INDISPONIVEL` (tentativa não gasta); 400 `CORPO_INVALIDO` | V5 |
| `POST /api/conta/verificacao/sessao/{sessaoId}/resultado` | — | 200 `{"resultado":"APROVADA"\|"DUVIDA"\|"REPETIR","pedeDocumento":bool,"tentativasRestantesHoje":n,"enviosDeDocumentoRestantesHoje":n}`; `APROVADA` ativa a conta; repetição devolve o mesmo corpo; `pedeDocumento` = `DUVIDA` e envios > 0 | 422 `MENOR_DE_IDADE` + `Set-Cookie` expirado; 404 `SESSAO_DE_VERIFICACAO_INVALIDA` (inexistente, de outra conta ou `DOCUMENTO`); 409 `REQUISICAO_EM_ANDAMENTO` + `Retry-After: 1`; 409 `VERIFICACAO_FORA_DE_ORDEM`; 503 `PROVA_DE_VIDA_INDISPONIVEL` (nada gasto; mesmo `sessaoId` pode repetir) | V5 |
| `POST /api/conta/verificacao/documento` | `multipart/form-data`: `sessaoId` (sessão `DOCUMENTO`), `frente`, `verso` (JPEG/PNG ≤ 5 MB) | 200 corpo do resultado, `pedeDocumento:false` | 422 `DOCUMENTO_INVALIDO`; 422 `MENOR_DE_IDADE` + cookie expirado; 409 `REFAZER_PROVA_DE_VIDA` (prova da sessão não `CONCLUIDA`, < 90,0 ou sem referência; nada concluído, conferência não chamada; só `EXPIRADA` devolve o envio, D14-bis); 404 `SESSAO_DE_VERIFICACAO_INVALIDA`; 409 `REQUISICAO_EM_ANDAMENTO`, `VERIFICACAO_FORA_DE_ORDEM`; 503 `PROVA_DE_VIDA_INDISPONIVEL`, `CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL` (sem repetição; reenvio com o mesmo `sessaoId`); 400 `REQUISICAO_INVALIDA` (parte ausente); 413 (> 6 MB/13 MB, do Spring) | V6 |
| `GET /api/conta` (2a) | — | passa a ter `verificada:true` com aprovação | — | V3 |
| `GET /api/legal/versoes` (2a) | — | `encarregado.email` (texto de análise e fotossensibilidade) | — | — |

**`CodigoVerificacao` (tipo → mensagem):** `CONSENTIMENTO_BIOMETRICO_NECESSARIO` (`REGRA_DE_NEGOCIO`) "Concorde com o uso da sua imagem para confirmar sua idade." · `VERIFICACAO_FORA_DE_ORDEM` (`CONFLITO`) "Esta etapa da verificação não está disponível agora. Recarregue a página." · `SESSAO_DE_VERIFICACAO_INVALIDA` (`NAO_ENCONTRADO`) "Esta verificação expirou. Comece de novo." · `REFAZER_PROVA_DE_VIDA` (`CONFLITO`) "A selfie venceu antes do envio. Faça uma nova." · `DOCUMENTO_INVALIDO` (`REGRA_DE_NEGOCIO`) "Envie fotos JPEG ou PNG de até 5 MB, frente e verso." · `LIMITE_DE_VERIFICACAO` (`LIMITE`) "Você chegou ao limite de tentativas de hoje. Tente de novo amanhã." · `PROVA_DE_VIDA_INDISPONIVEL` (`INDISPONIVEL`) "A verificação está fora do ar. Tente de novo em alguns minutos." · `CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL` (`INDISPONIVEL`) "A conferência do documento está fora do ar. Tente de novo em alguns minutos." · `MENOR_DE_IDADE` reaproveita `CodigoCadastro.MENOR_DE_IDADE`.

**Web (F1):** `CabecalhosDeSeguranca.CSP_VERIFICACAO` = `default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; img-src 'self'; font-src 'self'; media-src 'self'; connect-src 'self' blob: https://cdn.liveness.rekognition.amazonaws.com wss://streaming-rekognition.us-east-1.amazonaws.com; manifest-src 'self'; worker-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; require-trusted-types-for 'script'; trusted-types 'none'`; `PERMISSIONS_POLICY_VERIFICACAO` = `camera=(self), microphone=()`; `PERMISSIONS_POLICY` = `camera=(), microphone=()`. Só `getRequestURI()` exatamente `/verificacao` recebe as da verificação. `RotasDoSpa.CAMINHOS` e `fe/src/rotas/caminhosDaCasca.ts`: entra `/verificacao`, sai `/aguardando`.

**Front (`fe/src/`):**
```ts
// api/verificacao.ts
export type ModoDaVerificacao = "aws" | "falso";
export type Finalidade = "SELFIE" | "DOCUMENTO";
export interface SituacaoDaVerificacao { estado: "AGUARDANDO" | "DUVIDA" | "APROVADA"; tentativasRestantesHoje: number; enviosDeDocumentoRestantesHoje: number; modo: ModoDaVerificacao }
export interface CredencialCurta { accessKeyId: string; secretAccessKey: string; sessionToken: string; expiration: Date }
export interface SessaoDeCaptura { sessaoId: string; regiao: "us-east-1"; modo: ModoDaVerificacao; credencial: CredencialCurta; desvioDoRelogioMs: number | undefined }
export interface Desfecho { resultado: "APROVADA" | "DUVIDA" | "REPETIR"; pedeDocumento: boolean; tentativasRestantesHoje: number; enviosDeDocumentoRestantesHoje: number }
export function obterVerificacao(): Promise<SituacaoDaVerificacao>;
export function iniciarSessao(finalidade: Finalidade): Promise<SessaoDeCaptura>; // corpo {consentimentoBiometrico:true, finalidade}
export function concluirSelfie(sessaoId: string): Promise<Desfecho>;
export function enviarDocumento(envio: { sessaoId: string; frente: Blob; verso: Blob }): Promise<Desfecho>;
export function desvioDoRelogio(dataDoServidor: string | null, agoraMs: number): number | undefined; // |delta| ≥ 300_000 → delta
// telas/verificacao/CapturaDeVida.tsx
export type FalhaDoDetector = "camera" | "tempo" | "rosto" | "outraPessoa" | "conexao" | "servico" | "aparelho" | "orientacao" | "carregamento";
export interface PropsDoDetector { sessao: SessaoDeCaptura; aoConcluir: () => void; aoFalhar: (falha: FalhaDoDetector) => void; aoCancelar: () => void }
```
Resposta fora do formato ou `regiao` ≠ `us-east-1` → `ErroHttp{codigo:"RESPOSTA_INVALIDA"}`. `api/http.ts`: `OpcoesDaRequisicao.formulario?: FormData` (exclusivo com `corpo`; sem `Content-Type`, com `X-Duora: 1`). `rotas/rotas.tsx`: `ContextoDaCasca.caminhoDaCarga`, `isolarDocumentoDaVerificacao(filhas: RouteObject[], caminhoDaCarga: string): RouteObject[]`, `CAMINHO_DA_VERIFICACAO = "/verificacao"`. `rotas/consultas.ts`: `consultaDaVerificacao = queryOptions({ queryKey: ["conta","verificacao"], queryFn: obterVerificacao })`. `pwa.ts`: `registrarServiceWorker(caminho = location.pathname)` não registra em `/verificacao`.

**Marcadores de e-mail dos falsos** (parte local, V4; usados por `ContasDeTeste` e `ContaE2eBase.emailCom`): `+idade15` faixa 12–17; `+idade19` 17–24; `+vidafalha` `FALHOU`; `+docmenor` data = hoje − 15 anos; `+docoutro` rosto não confere; `+docilegivel` `(false, vazio, false)`; sem marcador: 30–40, confiança 99,0, documento com a data declarada.

## Ordem e paralelismo: três trilhas em worktrees
Uma fila por trilha, um implementador por vez (Opus · High, worktree próprio a partir de `etapa-2b`); no máximo 3 implementadores e 1 revisor. Uma task começa quando seus portões estão na `etapa-2b`; telas podem começar contra o contrato com `fetch` falso, e o portão vale para o merge.

| Trilha | Fila | Portões de outras trilhas |
|---|---|---|
| V (domínio, API, migrações) | V1 → V2 → V3 → V4 → V5 → V6 | nenhum |
| A (adapters, ADR) | A1 → A2 → A3 → A4 → A5 → A6 → A7 → A8 → A9 → A10 → A11 | A5: V2 · A11: V6, F7 (números finais) |
| F (front, CSP, e2e) | F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 | F3 (merge): V5 · F8: V4, V5, V6 |
| Fim | A12 (manual, depois de tudo na `etapa-2b`) | todas |

**Posse de arquivos (nenhum arquivo em duas trilhas):**
- **V:** `m/conta/**` exceto `m/conta/infra/{Configuracao*,MedicaoDeFornecedor,ClientesAws,ProvaDeVidaAws,ConferenciaDeDocumentoDidit,FornecedoresDeVerificacaoConfig}.java`; `mig/V11*`, `mig/V12*`; `r/application.properties` (bloco `# verificacao`); `r/application-verificacao-falsa.properties`; `t/conta/**` exceto os testes dos adapters; `t/retencao/PrazosDaPlataformaTest.java`.
- **A:** os arquivos de infra excluídos acima e seus testes; `pom.xml`; `r/application-prod.properties`; `t/arquitetura/**`, `t/ArquiteturaTest.java`; `docs/adr/0007-verificacao-de-idade.md`.
- **F:** `fe/**`; `m/web/{CabecalhosDeSeguranca,RotasDoSpa}.java`; `t/web/CspDaVerificacaoTest.java`; `t/pwa/**`; `t/e2e/**`.

## Foco de revisão
1. **Reserva de idempotência vence antes da resposta da Didit** (reenvio paralelo paga duas vezes): os timeouts somados têm de caber em 30 s. Teste: A3 `OrcamentoDeTempoTest.padroesCabemNaReserva`.
2. **Selfie de D-B vence entre a captura e o multipart em 4G:** a pessoa não pode perder as fotos. Testes: V6 `sessaoExpiradaPedeSelfieNovaSemConcluir` e F7 `refazerProvaDeVidaVoltaParaASelfieComAsFotos`.
3. **Chamada a fornecedor dentro de transação** (prende o pool de 10 e estoura `idle_in_transaction_session_timeout`). Testes: V5 `fornecedorNuncaRodaEmTransacao` e V6 `conferenciaNuncaRodaEmTransacao`.
4. **CSP da verificação sobra depois de navegar** (uma saída pelo roteador sem recarga quebra o Turnstile do `/entrar`). Teste: F8 `saidaPorCadaLinkDoRodapeRecarregaODocumento`.
5. **Falso em produção aprovaria todo mundo** (perfil `verificacao-falsa` ou provedor `falso` com `prod`). Testes: V4 `perfilVerificacaoFalsaFixaOsDoisEmFalso` + `GuardaDaVerificacaoRealTest`.

---

## Trilha V — domínio, fluxo e API

### Task V1: Regra pura da decisão de idade e tipos de valor
**Files:** Create `m/conta/dominio/{FaixaEstimada,SituacaoDaProva,ResultadoDaVerificacao,MetodoDeVerificacao,TipoDeImagem,Imagem,ResultadoDaProva,ResultadoDoDocumento,DecisaoDeIdade}.java`. Test `t/conta/dominio/{DecisaoDeIdadeTest,ImagemTest,FaixaEstimadaTest}.java`.
**Interfaces:** Consumes `Idade.maiorDeIdade` (2a). Produces os tipos e `DecisaoDeIdade` das Interfaces canônicas.
- [ ] **Testes que falham.**
  - `DecisaoDeIdadeTest` `@ParameterizedTest pelaSelfie` (referência JPEG de 4 bytes): (`CONCLUIDA`, 95,0, 21–29) → `APROVADA`; (95,0, 20–28) → `DUVIDA`; (95,0, 12–17) → `RECUSADA`; (95,0, 13–18) → `DUVIDA`; (90,0, 30–40) → `APROVADA`; (89,9, 30–40) → `REPETIR`; (95,0, vazio) → `REPETIR`; `FALHOU`, `EXPIRADA`, `EM_ANDAMENTO` com 30–40 → `REPETIR`.
  - `provaServeParaDocumento`: (`CONCLUIDA`, 95,0, 17–24, com referência) → `true`; 89,9, sem referência, sem faixa ou `EXPIRADA` → `false`.
  - `peloDocumento` (hoje 2026-11-22, declarada 1990-05-10): (true, 1990-05-10, true) → `APROVADA`; (true, 1990-05-11, true) → `DUVIDA`; (true, 2008-11-23, true) → `RECUSADA`; declarada 2008-11-22 com (true, 2008-11-22, true) → `APROVADA`; (true, 2008-11-23, false) → `DUVIDA`; (false, 1990-05-10, true) → `REPETIR`; (true, vazio, true) → `REPETIR`; `toStringEscondeAData` (não contém `1990`).
  - `ImagemTest`: `jpegValido`, `pngValido`, `pdfRecusado` (`%PDF-1.7`), `vazioRecusado`, `cincoMbExatosAceitos` (5 242 880), `umByteAMaisRecusado` (5 242 881), `bytesDevolveCopia`, `toStringSemConteudo` (= `Imagem[JPEG, 4 bytes]`).
  - `FaixaEstimadaTest`: `baixaNegativaRecusada` (−1, 5); `altaMenorQueBaixaRecusada` (30, 29); `faixaDeUmAnoAceita` (20, 20).
- [ ] `./mvnw -q test -Dtest='DecisaoDeIdadeTest,ImagemTest,FaixaEstimadaTest'` → FAIL.
- [ ] Implementar (construtores compactos validam; `Imagem` guarda cópia defensiva).
- [ ] Os mesmos e `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): decisão pura de idade pela selfie e pelo documento`

### Task V2: Portas dos fornecedores, códigos de erro e limites
**Files:** Create `m/conta/dominio/{ProvaDeVida,SessaoDeProva,ModoDeCaptura,CredencialDoNavegador,CredencialCurta,ConferenciaDeDocumento,FornecedorIndisponivel,FinalidadeDaTentativa}.java`, `m/conta/{CodigoVerificacao,LimiteDeVerificacaoExcedido,LimitesDaVerificacao}.java`. Test `t/conta/dominio/CredencialCurtaTest.java`, `t/conta/CodigoVerificacaoTest.java`.
**Interfaces:** Consumes `nucleo/{CodigoDeErro,TipoDeErro,ErroDeNegocio}`. Produces as portas, records, `CodigoVerificacao` (padrão do `CodigoCadastro`), `LimiteDeVerificacaoExcedido` (como `LimiteDeContaNovaExcedido`) e `LimitesDaVerificacao`, todos das Interfaces canônicas.
- [ ] **Testes que falham.** `CredencialCurtaTest.toStringSemSegredoNemToken` (`("AKIA-TESTE","segredo-x","token-y",2026-11-22T12:15Z)`: contém `AKIA-TESTE`, não contém `segredo-x` nem `token-y`). `CodigoVerificacaoTest` `@ParameterizedTest` cada código → tipo e mensagem exatos; `limiteViraRetryAfter` (`tentarDeNovoEm()` = 2 h); `zeroEnviosRecusado` (`LimitesDaVerificacao(3, 0)` → `IllegalArgumentException`); `limitePorFinalidade` (`SELFIE` 3, `DOCUMENTO` 2).
- [ ] `./mvnw -q test -Dtest='CredencialCurtaTest,CodigoVerificacaoTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos e `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): portas da prova de vida, credencial curta e conferência de documento`

### Task V3: Esquema V11/V12, repositório, retenção de 1 dia e selo `verificada`
**Files:**
- Create `mig/V11__verificacao_idade.sql`, `mig/V12__tentativa_verificacao.sql`, `m/conta/dominio/{RepositorioDeVerificacoes,ReservaDeTentativa,Tentativa}.java`, `m/conta/infra/{VerificacoesJdbc,RetencaoDeTentativas}.java`.
- Modify `m/conta/dominio/Conta.java` (`resumo()` → `resumo(boolean verificada)`), `m/conta/ContaResumo.java` (javadoc), `m/conta/infra/ContaConfig.java` (`consultaDeContas` usa `aprovada`), `m/conta/LimpezaDeConta.java` (`apagar` chama `verificacoes.apagarDaConta(id)` depois de `contas.excluir` verdadeiro), `t/retencao/PrazosDaPlataformaTest.java` (`POLITICAS_POR_LINHA` ganha `"Tentativas de verificação de idade", List.of("tentativas-de-verificacao")`; a linha `| Tentativas de verificação de idade | 1 dia |` já está em `r/legal/privacidade.md`).
- Test `t/conta/infra/{VerificacoesJdbcTest,RetencaoDeTentativasTest}.java`; Modify `t/conta/ExclusaoApiTest.java` e os testes que chamam `resumo`.

**Esquema:**
- V11 `verificacao_idade(conta_id uuid primary key references conta(id) on delete cascade, metodo text not null check (metodo in ('ESTIMATIVA_FACIAL','DOCUMENTO')), aprovada_em timestamptz not null)`.
- V12 `tentativa_verificacao(id uuid primary key, conta_id uuid not null references conta(id) on delete cascade, finalidade text not null check (finalidade in ('SELFIE','DOCUMENTO')), sessao_id text unique, resultado text check (resultado in ('APROVADA','DUVIDA','RECUSADA','REPETIR')), criada_em timestamptz not null, concluida_em timestamptz, check ((resultado is null) = (concluida_em is null)), check (concluida_em is null or sessao_id is not null))` + índices `(conta_id, criada_em)` e `(criada_em)`.

**Interfaces:** Consumes `retencao/{PoliticaDeRetencao,Lotes}`, `GeradorDeIds`, `Clock`. Produces `RepositorioDeVerificacoes` (canônico): `reservar` trava `select … from conta where id = :id for update` na transação do chamador e conta as tentativas da finalidade com `criada_em > agora − P1D` (reservas sem sessão inclusive); abaixo do limite insere e devolve `Feita`, senão `Esgotada(min(criada_em) + P1D)`; `concluir` é `UPDATE … WHERE id = :id AND concluida_em IS NULL`. `RetencaoDeTentativas`: `nome()` `tentativas-de-verificacao`, prazo `P1D` sobre `criada_em`, lotes de 1000.
- [ ] **Testes que falham.**
  - `VerificacoesJdbcTest extends BancoDeTeste`: `reservaAbaixoDoLimiteGravaTentativaSemSessao`; `reservaNoLimiteDevolveEsgotada` (três `SELFIE` em `INICIO − 2h`, `− 1h`, `INICIO`, limite 3 → `Esgotada(INICIO + 22h)`); `tentativaDeMaisDe24hNaoConta` (`INICIO − 24h − 1s` → `Feita`); `finalidadesContamSeparado`; `concluirSoUmaVez`; `duvidaDeSelfieIgnoraDocumento`; `gravarAprovacaoMarcaAprovada`; `apagarDaContaLevaTentativasEAprovacao`; `descartarApagaReserva`; `consultaDeContasMostraVerificada` (`false` antes, `true` depois de `gravarAprovacao`); `colunasDaAprovacaoSaoSoTres` (`conta_id, metodo, aprovada_em`).
  - `RetencaoDeTentativasTest`: 25 h some; 23 h fica; `tamanho()` = 1 antes de expirar.
  - `ExclusaoApiTest.exclusaoApagaVerificacaoETentativas` (zero linhas nas duas tabelas).
  - `PrazosDaPlataformaTest` com a linha nova.
- [ ] `./mvnw -q test -Dtest='VerificacoesJdbcTest,RetencaoDeTentativasTest,ExclusaoApiTest,PrazosDaPlataformaTest'` → FAIL.
- [ ] Implementar; `backend-design:review-migration` antes do commit.
- [ ] Os mesmos, `ContaTest`, `ContaResumoTest`, `ContasJdbcTest`, `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): esquema da verificação de idade, retenção de tentativas e selo`

### Task V4: Falsos de dev/e2e, perfil `verificacao-falsa` e guarda de produção
**Files:** Create `m/conta/infra/{ProvaDeVidaFalsa,ConferenciaDeDocumentoFalsa,GuardaDaVerificacaoReal}.java`, `r/application-verificacao-falsa.properties`. Modify `r/application.properties` (bloco `# verificacao`: provedores `falso` e limites por dia). Test `t/conta/infra/{FalsosDeVerificacaoTest,GuardaDaVerificacaoRealTest}.java`; Modify `t/conta/ContasDeTeste.java` (`ContaDeTeste criarAguardando(String marcador, LocalDate nascimento)` → `<id>+<marcador>@teste.duora.invalid`).
**Interfaces:** Consumes V2, V3 (`porSessao`), `RepositorioDeContas.porId`, `Clock`. Produces:
- `ProvaDeVidaFalsa` (`@ConditionalOnProperty(name="duora.verificacao.provedor", havingValue="falso")`): `modo()` `FALSO`; `criarSessao(t)` → `SessaoDeProva("falsa-" + t, "us-east-1")`; `emitir(t)` → `CredencialCurta("FALSA","falsa","falsa", agora + 15 min)`; `resultado(id)` acha a conta por `porSessao` e aplica os marcadores (canônicos), `CONCLUIDA`, referência = `FF D8 FF E0` + `"selfie-falsa:" + id`; sessão desconhecida → `EXPIRADA` e vazios.
- `ConferenciaDeDocumentoFalsa` (`havingValue="falso"` em `duora.verificacao.documento.provedor`): lê o `sessaoId` da referência falsa, acha a conta e aplica os marcadores; referência que não é falsa → `(false, vazio, false)`.
- `GuardaDaVerificacaoReal` (`@Profile("prod")`, `@Value` dos dois provedores): algum `falso` → `IllegalStateException("A verificação falsa não roda em produção")`.
- [ ] **Testes que falham.**
  - `FalsosDeVerificacaoTest extends BancoDeTeste`: `padraoAprova` (30–40); `idade15Recusa`; `idade19CaiNaDuvida`; `vidafalhaFalha`; `sessaoDesconhecidaExpira`; `credencialVence15MinDepois` (`2026-11-22T12:15:00Z`); `modoEhFalso`; `documentoPadraoDevolveADeclarada` (1990-05-10); `docmenorTem15Anos` (2011-11-22); `docoutroNaoConfereORosto`; `docilegivelNaoAceita`; `selfieDeVerdadeNaoAceita`.
  - `GuardaDaVerificacaoRealTest`: `provaFalsaEmProdDerrubaOBoot` (`falso`,`didit`); `documentoFalsoEmProdDerrubaOBoot` (`aws`,`falso`); `provedoresReaisPassam`; `perfilVerificacaoFalsaFixaOsDoisEmFalso` (carrega `application-verificacao-falsa.properties` por `PropertiesLoaderUtils`: os dois valem `falso`, logo `prod` + esse perfil cai na guarda).
- [ ] `./mvnw -q test -Dtest='FalsosDeVerificacaoTest,GuardaDaVerificacaoRealTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos e `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): prova de vida e documento falsos para dev e e2e, barrados em produção`

### Task V5: Caso de uso da selfie, ativação e API
**Files:** Create `m/conta/{VerificacaoDeIdade,EfeitosDaVerificacao,SessaoDeCaptura,DesfechoDaVerificacao,SituacaoDaVerificacao,EstadoDaVerificacao}.java`, `m/conta/web/VerificacaoController.java`, `t/conta/{ProvaDeVidaDeTeste,ConferenciaDeDocumentoDeTeste}.java` (`@Component @Primary`, padrão do `VerificacaoHumanaDeTeste`). Modify `RepositorioDeContas` + `ContasJdbc` (`ativar`), bloco `# verificacao` (regra `verificacao-conta`). Test `t/conta/VerificacaoApiTest.java`, `t/conta/web/PedidoDeSessaoJsonTest.java`; Modify `t/conta/ContasJdbcTest.java`.
**Interfaces:** Consumes V1–V4, `LimiteDeTaxa`, `Idempotencia`, `LimpezaDeConta.excluirEBloquear(ContaId, Email, String, Instant)`, `CookieSessao.expirar()`, `CodigoCadastro.MENOR_DE_IDADE`, `TransactionTemplate`, `Clock`. Produces:
- `ativar`: `update conta set estado='ATIVA', ativada_em=:agora where id=:id and estado='AGUARDANDO_VERIFICACAO'`.
- `EfeitosDaVerificacao.aplicar(Conta, ResultadoDaVerificacao, MetodoDeVerificacao, Instant)` (transação do chamador): `APROVADA` → `ativar` (falso → `VERIFICACAO_FORA_DE_ORDEM`) + `gravarAprovacao`; `RECUSADA` → `excluirEBloquear(id, email, "MENOR", agora)`.
- `SessaoDeCaptura iniciar(ContaId, FinalidadeDaTentativa, boolean consentimento)`: limite de taxa → consentimento (422) → estado (409) → `DOCUMENTO` exige `duvidaDeSelfieDesde(agora − P1D)` (409) → `reservar` em transação curta (`Esgotada` → `LimiteDeVerificacaoExcedido`) → fora de transação `criarSessao(tentativa)` + `emitir(tentativa)` → `associarSessao`; `FornecedorIndisponivel` → `descartar` + 503 `PROVA_DE_VIDA_INDISPONIVEL`. `modo` = `provaDeVida.modo()`.
- `DesfechoDaVerificacao concluirSelfie(ContaId, String sessaoId)`: limite → `porSessao` da conta e `SELFIE` (senão 404) → `idempotencia.reservar("verificacao-resultado", sessaoId, conta.toString(), DesfechoDaVerificacao.class)` (`JaRespondida` → devolve) → estado (409) → `resultado` fora de transação → `pelaSelfie` → uma transação: `concluir` (falso → 404), `aplicar(…, ESTIMATIVA_FACIAL, …)`, `idempotencia.concluir`. Exceção antes do commit → `liberar` e relança; `FornecedorIndisponivel` → 503.
- `SituacaoDaVerificacao situacao(ContaId)` (com `modo`). Log INFO só com id da conta, resultado e método.
- `VerificacaoController` (`/api/conta/verificacao`): `GET`, `POST /sessao` (201, `modo` minúsculo, cabeçalho `Date` = `clock.millis()`), `POST /sessao/{sessaoId}/resultado`; `RECUSADA` → `Set-Cookie` de `cookie.expirar()` + `MENOR_DE_IDADE.erro()`. `record PedidoDeSessaoJson(@Nullable Boolean consentimentoBiometrico, @Nullable FinalidadeDaTentativa finalidade)`.
- Dublês: `ProvaDeVidaDeTeste` (implementa as duas portas; `programar`, `programarPorSessao`, `falharNaCriacao`, `falharNoResultado`, `segurar/liberar`, `chamadasDeResultado`, `transacaoAtivaNasChamadas(): List<Boolean>`, `zerar`; padrão `CONCLUIDA`, 99,0, 30–40, referência `FF D8 FF E0` + `selfie-de-teste`; segredo `SEGREDO-CANARIO-NAO-LOGAR`; `modo()` `FALSO`); `ConferenciaDeDocumentoDeTeste` (`programar`, `falhar`, `segurar/liberar`, `chamadas`, `ultimaChamada()`, `transacaoAtivaNasChamadas()`, `zerar`). As listas de transação gravam `TransactionSynchronizationManager.isActualTransactionActive()`.
- [ ] **Testes que falham.**
  - `ContasJdbcTest.ativarSoSaiDeAguardando` (`PENDENTE` → `false`; `AGUARDANDO_VERIFICACAO` → `true`, `ativada_em` = `INICIO`; de novo → `false`).
  - `PedidoDeSessaoJsonTest`: campo extra → 400 `CORPO_INVALIDO`; finalidade ausente → `SELFIE`.
  - `VerificacaoApiTest extends BancoDeTeste`: `iniciarSemConsentimentoRecebe422`; `iniciarComContaPendenteRecebe409`; `iniciarComContaAtivaRecebe409`; `iniciarDevolveSessaoCredencialEModo` (201, `us-east-1`, `modo` `falso`, `expiraEm` `2026-11-22T12:15:00Z`, `no-store`, tentativa com `sessao_id`); `iniciarTrazCabecalhoDate` (`Sun, 22 Nov 2026 12:00:00 GMT`); `quartaSessaoDoDiaRecebe429` (`Retry-After: 86400`; +24 h → 201); `sessoesSimultaneasNaoPassamDoLimite` (6 threads → três 201, três 429); `awsForaDoArNaCriacaoRecebe503` (zero tentativas; `GET` → 3); `sessaoDeDocumentoSemDuvidaRecebe409`; `aprovadaAtivaAConta` (200 `{"resultado":"APROVADA","pedeDocumento":false,"tentativasRestantesHoje":2,"enviosDeDocumentoRestantesHoje":2}`, conta `ATIVA`, aprovação `ESTIMATIVA_FACIAL`, `GET /api/conta` → `verificada:true`); `recusadaSegueOFluxoDoMenor` (12–17 → 422 `MENOR_DE_IDADE`, `Max-Age=0`, conta `EXCLUIDA`, `email_bloqueado` `MENOR`, zero sessões, tentativas e aprovações); `duvidaPedeDocumento` (`GET` → `DUVIDA`); `confiancaBaixaPedeRepetir` (89,9 → `REPETIR`, 2 restantes); `resultadoRepetidoDevolveAMesmaResposta` (`chamadasDeResultado()` = 1); `resultadosSimultaneosDecidemUmaVez` (um 200, um 409 `REQUISICAO_EM_ANDAMENTO`); `sessaoDeOutraContaRecebe404`; `sessaoInexistenteRecebe404`; `awsForaDoArNoResultadoRecebe503` (depois o mesmo `sessaoId` → 200); `situacaoInicial` (`{"estado":"AGUARDANDO","tentativasRestantesHoje":3,"enviosDeDocumentoRestantesHoje":2,"modo":"falso"}`); `situacaoDaContaAtiva`; `situacaoDaContaPendenteRecebe409`; `semSessaoRecebe401`; `vigesimaPrimeiraChamadaEm10MinRecebe429`; `fornecedorNuncaRodaEmTransacao` (depois de iniciar e concluir, `transacaoAtivaNasChamadas()` só tem `false`); `logNaoTrazCredencialNemImagem` (sem `SEGREDO-CANARIO-NAO-LOGAR` e `selfie-de-teste`).
- [ ] `./mvnw -q test -Dtest='VerificacaoApiTest,ContasJdbcTest,PedidoDeSessaoJsonTest'` → FAIL.
- [ ] Implementar (`backend-design:idempotency-and-side-effects`).
- [ ] Os mesmos, `RotasProtegidasTest`, `SessaoApiTest`, `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): verificação de idade pela selfie leva a conta a ativa`

### Task V6: Envio do documento na faixa de dúvida (D-B)
**Files:** Create `m/conta/VerificacaoPorDocumento.java`, `m/conta/web/DocumentoController.java`. Modify bloco `# verificacao` (`spring.servlet.multipart.*`). Test `t/conta/VerificacaoPorDocumentoApiTest.java`, `t/conta/web/MultipartEmMemoriaTest.java`.
**Interfaces:** Consumes V1–V5. Produces `DesfechoDaVerificacao VerificacaoPorDocumento.concluir(ContaId, String sessaoId, byte[] frente, byte[] verso)`: limite → `Imagem.ler` das duas (vazio → 422 `DOCUMENTO_INVALIDO`, antes de qualquer chamada) → `porSessao` da conta e `DOCUMENTO` (senão 404) → `idempotencia.reservar("verificacao-documento", sessaoId, conta.toString(), DesfechoDaVerificacao.class)` → estado (409) → `resultado(sessaoId)`; `!provaServeParaDocumento` → `liberar` + 409 `REFAZER_PROVA_DE_VIDA` (nada concluído) → `conferir(frente, verso, referencia)` → `peloDocumento(declarada, r, hoje SP)` → transação: `concluir`, `aplicar(…, DOCUMENTO, …)`, `idempotencia.concluir`. `FornecedorIndisponivel` da prova → 503 `PROVA_DE_VIDA_INDISPONIVEL`, da conferência → 503 `CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL`; nos dois, `liberar`. `DocumentoController`: `POST /api/conta/verificacao/documento` com `@RequestParam String sessaoId`, `@RequestPart MultipartFile frente`, `@RequestPart MultipartFile verso`.
- [ ] **Testes que falham.**
  - `VerificacaoPorDocumentoApiTest extends BancoDeTeste` (preparo: nascida 1990-05-10, selfie 17–24 → `DUVIDA`, `POST /sessao` `DOCUMENTO`; frente/verso = JPEG de 4 bytes com marcadores `frente-de-teste`/`verso-de-teste`): `documentoConferidoAprova` (200 `{"resultado":"APROVADA","pedeDocumento":false,"tentativasRestantesHoje":2,"enviosDeDocumentoRestantesHoje":1}`, conta `ATIVA`, aprovação `DOCUMENTO`, `ultimaChamada()` com os bytes enviados e a referência da sessão `DOCUMENTO`); `dataDiferenteFicaEmDuvida`; `rostoQueNaoConfereFicaEmDuvida` (`(true, 2008-11-23, false)`, zero `email_bloqueado`); `documentoDeMenorSegueOFluxoDoMenor`; `documentoIlegivelPedeRepetir` (envios 1); `confiancaBaixaNaoChamaADiditEGastaOEnvio` (80,0 → 409 `REFAZER_PROVA_DE_VIDA`, `chamadas()` = 0, tentativa sem resultado, envios 1); `provaQueFalhouGastaOEnvio` e `provaAindaCriadaGastaOEnvio` (idem, D14-bis); `sessaoExpiradaPedeSelfieNovaSemConcluir` (`EXPIRADA` → 409 `REFAZER_PROVA_DE_VIDA`, tentativa descartada, envios 2; o mesmo envio repetido → 404); `sessaoDeSelfieNoDocumentoRecebe404`; `pdfNoLugarDaImagemRecebe422` (dublês não chamados); `imagemAcimaDe5MbRecebe422`; `semVersoRecebe400` (`REQUISICAO_INVALIDA`); `arquivoAcimaDe6MbRecebe413`; `diditForaDoArDeixaReenviar` (503, depois o mesmo `sessaoId` → 200); `awsForaDoArNoDocumentoRecebe503`; `terceiraSessaoDeDocumentoNoDiaRecebe429`; `enviosSimultaneosChamamADiditUmaVez`; `contaAtivaNaoEnviaDocumento` (409); `conferenciaNuncaRodaEmTransacao`; `logNaoTrazImagemDoDocumento` (sem os marcadores nem `1990-05-10`).
  - `MultipartEmMemoriaTest`: `MultipartProperties` com 6 MB, 13 MB e `fileSizeThreshold` ≥ `maxRequestSize`.
- [ ] `./mvnw -q test -Dtest='VerificacaoPorDocumentoApiTest,MultipartEmMemoriaTest'` → FAIL.
- [ ] Implementar.
- [ ] Os mesmos, `VerificacaoApiTest`, `ArquiteturaTest` → PASS.
- [ ] Commit `feat(conta): documento e selfie nova num envio só na faixa de dúvida`

---

## Trilha A — adapters AWS e Didit, ADR e operação

APIs conferidas em 2026-11-27 (refazer na implementação): AWS SDK v2 BOM 2.59.10 (Context7 `/aws/aws-sdk-java-v2`: arquétipo `urlhttpclient`, `apiCallAttemptTimeout`/`apiCallTimeout` desligados por padrão, `retryStrategy(b -> b.maxAttempts(n))`, `endpointOverride`); Rekognition (`CreateFaceLivenessSession` com `ClientRequestToken` 1–64 `^[a-zA-Z0-9-_]+$` idempotente, `AuditImagesLimit` 0–4, sessão vence em 3 min; `GetFaceLivenessSessionResults` com `Status`, `Confidence` 0–100, `ReferenceImage.Bytes`, `SessionNotFoundException`; `DetectFaces` `AGE_RANGE`, JPEG/PNG ≤ 5 MB); STS `AssumeRole` (`DurationSeconds` ≥ 900, `Policy` = interseção); Didit v3 (`POST /v3/id-verification/` multipart, `x-api-key`, `front_image`, `back_image`, `perform_document_liveness`, `save_api_request`; resposta `id_verification.status ∈ {Approved, Declined, In Review}`, `date_of_birth`, `portrait_image`; erros 400 `COULD_NOT_RECOGNIZE_DOCUMENT`, 401/403 chave ou saldo, 429, 503; `POST /v3/face-match/` com `user_image`, `ref_image`, `face_match_score_decline_threshold` (padrão 30), `face_match.status`; sandbox devolve `Approved` fixo).

**Teste da AWS:** stubs de `RekognitionClient`/`StsClient` (os `default` lançam `UnsupportedOperationException`; o stub sobrescreve só o usado e guarda o request tipado) para comportamento; `t/web/ServidorHttpLento` com `endpointOverride` só para timeout. **Didit:** `t/conta/infra/ServidorDiditFalso` (`HttpServer` em `127.0.0.1:0`, respostas por caminho com status/corpo/atraso; grava método, caminho, cabeçalhos e corpo cru; `AutoCloseable`).

**Regra de erro de todos os adapters:** `SdkException`, 5xx, 429, 401/403, outro 4xx inesperado, timeout e rede → `FornecedorIndisponivel` com motivo fixo (`"aws/<operacao>"`, `"didit/<operacao>"`). Log WARN para timeout/rede/5xx/throttling, ERROR para 4xx de configuração (com sufixo `": confira IAM e duora.verificacao.aws"` ou `"…didit"`); só fornecedor, operação, classe e status — nunca mensagem da exceção, corpo, chave, `sessionId` ou imagem.

### Task A1: SDK da AWS no `pom.xml` e regra "SDK só na infra"
**Files:** Modify `pom.xml` (`<aws-sdk.version>2.59.10</aws-sdk.version>`; BOM `software.amazon.awssdk:bom` `import`; `rekognition` e `sts` com exclusões de `netty-nio-client` e `apache-client`; `url-connection-client`; nada de `textract`), `t/arquitetura/RegrasDeArquitetura.java`, `t/ArquiteturaTest.java`. Create `t/arquitetura/exemplos/moduloc/web/UsaAws.java` (campo `Region`). Test `t/arquitetura/RegrasDeArquiteturaTest.java`, `t/conta/infra/ClasspathDaAwsTest.java`.
**Interfaces:** Produces `public static ArchRule sdkDaAwsSoNaInfra(String raiz)`: nada em `raiz..` fora de `..infra..` depende de `software.amazon..`.
- [ ] **Testes que falham:** `sdkDaAwsSoNaInfraMorde` (violações contêm `UsaAws` e `Region`); `semClienteApache` e `semClienteNetty` (`Class.forName` → `ClassNotFoundException`); `comClienteUrlConnection`.
- [ ] `./mvnw -q test -Dtest='RegrasDeArquiteturaTest,ClasspathDaAwsTest'` → FAIL.
- [ ] Implementar (formato de `webSemJdbc`).
- [ ] Os mesmos + `ArquiteturaTest` → PASS; `./mvnw -q dependency:tree -Dincludes=software.amazon.awssdk:apache-client,software.amazon.awssdk:netty-nio-client` vazio.
- [ ] Commit `build(conta): SDK da AWS 2.59.10 só com rekognition, sts e url-connection`

### Task A2: `MedicaoDeFornecedor`
**Files:** Create `m/conta/infra/MedicaoDeFornecedor.java`. Test `t/conta/infra/MedicaoDeFornecedorTest.java` (`SimpleMeterRegistry`).
**Interfaces:** Produces `MedicaoDeFornecedor(MeterRegistry)` e `<T> T medir(String fornecedor, String operacao, Supplier<T> chamada)`: Timer `duora.verificacao.fornecedor{fornecedor, operacao, desfecho=sucesso|falha}`; relança a mesma instância; não loga.
- [ ] **Testes que falham:** `sucessoContaComDesfechoSucesso` (`medir("aws","resultado",() -> 7)` = 7, timer `sucesso` `count()` 1); `falhaContaERelancaAMesmaExcecao` (`isSameAs(e)`, `falha` 1, `sucesso` ausente).
- [ ] Rodar → FAIL. Implementar (`Timer.Sample`). Rodar → PASS.
- [ ] Commit `feat(conta): medição de chamadas aos fornecedores da verificação`

### Task A3: Configuração dos fornecedores, `prod` e orçamento de tempo
**Files:** Create `m/conta/infra/{ConfiguracaoAws,ConfiguracaoDidit}.java`. Modify `r/application-prod.properties` (bloco `# verificacao`: os dois provedores reais e as quatro variáveis da tabela de propriedades). Test `t/conta/infra/{ConfiguracaoDosFornecedoresTest,OrcamentoDeTempoTest}.java`.
**Interfaces:** Produces
- `record ConfiguracaoAws(@Nullable String accessKeyId, @Nullable String secretAccessKey, @Nullable String papelCapturaArn, @DefaultValue("PT1S") Duration conexao, @DefaultValue("PT2S") Duration tentativa, @DefaultValue("PT4S") Duration chamada)` com `void exigirCompleta()` (as três chaves pela regra `exigir` do `TurnstileConfig`: nulo, branco ou `${…`; ARN casa `^arn:aws:iam::\d{12}:role/[\w+=,.@-]+$`; mensagem cita só a propriedade).
- `record ConfiguracaoDidit(@DefaultValue("https://verification.didit.me") URI url, @Nullable String apiKey, @DefaultValue("30") double limiarRosto, @DefaultValue("PT1S") Duration conexao, @DefaultValue("PT9S") Duration leituraDoDocumento, @DefaultValue("PT4S") Duration leituraDoRosto)` com `void exigirCompleta()` (chave; `url` em `https`).
- `toString` dos dois sem segredo nem chave.
- [ ] **Testes que falham.**
  - `ConfiguracaoDosFornecedoresTest`: `awsSemChaveRecusada` (`@ParameterizedTest`: cada uma das três propriedades nula, `""` e `"${DUORA_AWS_PAPEL_CAPTURA_ARN}"` → `IllegalArgumentException` cuja mensagem tem o nome `duora.verificacao.aws.<…>` e não o valor); `arnForaDoFormatoRecusado` (`arn:aws:iam::123:role/x`); `diditSemChaveRecusada` (`duora.verificacao.didit.api-key`); `diditSemHttpsRecusada`; `toStringNaoMostraSegredos` (`segredo-canario`, `chave-canario`); `padroesDoBinder` (`Binder` vazio → os padrões da tabela).
  - `OrcamentoDeTempoTest.padroesCabemNaReserva`: com os padrões do Binder e `duora.idempotencia.reserva-maxima` lido de `application.properties` (`PropertiesLoaderUtils`): `2 × aws.chamada` ≤ `PT10S`; `(conexao + leituraDoDocumento) + (conexao + leituraDoRosto)` ≤ `PT15S`; a soma das duas < `reserva-maxima` (`PT30S`).
- [ ] Rodar → FAIL. Implementar e editar `application-prod.properties`. Rodar → PASS.
- [ ] Commit `feat(conta): configuração dos fornecedores da verificação dentro do orçamento de tempo`

### Task A4: `ClientesAws`
**Files:** Create `m/conta/infra/ClientesAws.java`. Test `t/conta/infra/ClientesAwsTest.java`.
**Interfaces:** Produces `static final Region REGIAO = Region.US_EAST_1`; `static RekognitionClient rekognition(ConfiguracaoAws c, Optional<URI> endpoint)`; `static StsClient sts(ConfiguracaoAws c, Optional<URI> endpoint)`: `region(REGIAO)`, `StaticCredentialsProvider.create(AwsBasicCredentials.create(…))`, `UrlConnectionHttpClient.builder().connectionTimeout(c.conexao()).socketTimeout(c.tentativa())`, `overrideConfiguration(o -> o.apiCallAttemptTimeout(c.tentativa()).apiCallTimeout(c.chamada()).retryStrategy(r -> r.maxAttempts(2)))`, `endpointOverride` só com `Optional` preenchido.
- [ ] **Testes que falham:** `rekognitionAssinaEmUsEast1`; `stsAssinaEmUsEast1`; `credencialVemSoDaConfiguracao` (com `System.setProperty("aws.accessKeyId","AKIAOUTRA")`, o provider resolve `AKIACONFIG`); `rekognitionLentoFalhaPeloTimeout` (`ServidorHttpLento` 3 s, `tentativa=PT0.2S`, `chamada=PT0.5S` → `SdkClientException` em < 1 s); `stsLentoFalhaPeloTimeout`.
- [ ] Rodar → FAIL. Implementar. Rodar → PASS.
- [ ] Commit `feat(conta): clientes da AWS em us-east-1 com timeouts e duas tentativas`

### Task A5: `ProvaDeVidaAws`: sessão e credencial curta
**Files:** Create `m/conta/infra/ProvaDeVidaAws.java`. Test `t/conta/infra/{ProvaDeVidaAwsSessaoTest,RekognitionFalso,StsFalso}.java`.
**Interfaces:** Consumes V2 (portas, `FornecedorIndisponivel`), A2, A4. Produces `ProvaDeVidaAws(RekognitionClient, StsClient, String papelCapturaArn, MedicaoDeFornecedor)`, `close()` fecha os dois clientes:
- `modo()` → `AWS`.
- `criarSessao(t)`: `CreateFaceLivenessSession` com `clientRequestToken = t.toString()`, `settings(s -> s.auditImagesLimit(0))`, sem `OutputConfig` nem `KmsKeyId` (`aws/criar-sessao`) → `SessaoDeProva(sessionId, "us-east-1")`.
- `emitir(t)`: `AssumeRole` com `roleArn`, `roleSessionName = "duora-" + t`, `durationSeconds = 900`, `policy = POLITICA_DE_SESSAO` = `{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"rekognition:StartFaceLivenessSession","Resource":"*"}]}` (`aws/credencial`).
- [ ] **Testes que falham:** `criarSessaoPedeZeroImagensDeAuditoriaESemS3`; `tokenDeIdempotenciaEOIdDaTentativa` (`0193f5c2-7a10-7000-8000-000000000001`); `credencialVemDoAssumeRoleCom900s` (`roleSessionName` = `duora-0193f5c2-7a10-7000-8000-000000000001`, `policy` exata); `devolveSessaoECredencial` (campo a campo, `expiraEm` `2026-11-27T12:15:00Z`); `falhaDoCreateViraIndisponivel` (`SdkClientException` → `FornecedorIndisponivel`); `falhaDoAssumeRoleViraIndisponivel` (`StsException` 500); `acessoNegadoLogaErroSemSegredo` (`AccessDeniedException` com `arn:aws:iam::123456789012:user/canario`: saída com `ERROR` e `confira IAM`, sem `123456789012`); `metricaContaSucessoDasDuasOperacoes`.
- [ ] Rodar → FAIL. Implementar. Rodar → PASS.
- [ ] Commit `feat(conta): sessão de prova de vida e credencial curta pela AWS`

### Task A6: `ProvaDeVidaAws.resultado` e faixa pelo `DetectFaces`
**Files:** Modify `m/conta/infra/ProvaDeVidaAws.java`. Test `t/conta/infra/ProvaDeVidaAwsResultadoTest.java`.
**Interfaces:** Produces `resultado(sessaoId)`: `GetFaceLivenessSessionResults` (`aws/resultado`) → situação mapeada (desconhecido → `FALHOU` + WARN), confiança (nula → 0,0); `SessionNotFoundException` → `(EXPIRADA, 0.0, vazio, vazio)` com INFO. Só com `CONCLUIDA` e `ReferenceImage.Bytes`: `referencia = Imagem.ler(bytes)` e `DetectFaces` com `AGE_RANGE` (`aws/estimativa`); exatamente um rosto → `FaixaEstimada(low, high)`; zero, vários, `InvalidImageFormatException` ou `ImageTooLargeException` → faixa vazia (WARN). `CONCLUIDA` sem referência → faixa e referência vazias.
- [ ] **Testes que falham:** `umRostoViraFaixa` (97,5 e 23–31 → `(CONCLUIDA, 97.5, 23–31, referência)`; request com `[AGE_RANGE]` e os bytes da referência); `doisRostosViramVazio`; `nenhumRostoViraVazio`; `sessaoNaoConcluidaNaoChamaDetectFaces` (`CREATED`, `IN_PROGRESS`, `FAILED`, `EXPIRED` → situação mapeada, zero `detectFaces`, referência vazia); `concluidaSemReferenciaViraVazio`; `sessaoInexistenteViraExpirada`; `imagemRecusadaPeloDetectFacesViraFaixaVazia`; `referenciaVemComOsBytes` (`{FF,D8,FF,1,2,3}` iguais em `referencia().bytes()`); `falhaNoGetViraIndisponivel`; `throttlingNoDetectFacesViraIndisponivel`; `logNaoTrazBytesNemSessao`.
- [ ] Rodar → FAIL. Implementar. Rodar → PASS.
- [ ] Commit `feat(conta): resultado da prova de vida e faixa de idade pelo DetectFaces`

### Task A7: `ConferenciaDeDocumentoDidit`: ID Verification
**Files:** Create `m/conta/infra/ConferenciaDeDocumentoDidit.java`. Test `t/conta/infra/{ConferenciaDeDocumentoDiditTest,ServidorDiditFalso}.java`.
**Interfaces:** Produces `static ConferenciaDeDocumentoDidit comTimeouts(RestClient.Builder, ConfiguracaoDidit, MedicaoDeFornecedor)` (JDK `HttpClient` como no `TurnstileVerificacao`; um `RestClient` por passo, leitura `leituraDoDocumento`/`leituraDoRosto`; sem repetição, D-D). Passo 1 `POST {url}/v3/id-verification/` (`didit/documento`), multipart por `MultipartBodyBuilder`: `front_image` (filename `frente`), `back_image` (filename `verso`), `perform_document_liveness=true`, `save_api_request=false`, cabeçalho `x-api-key`, sem `vendor_data`/`metadata`. DTO `@JsonIgnoreProperties(ignoreUnknown = true)`. `documentoAceito = "Approved".equals(status)` (`Declined`, `In Review`, outro → não aceito; fora dos três → WARN); data por `LocalDate.parse` (nula ou inválida → vazio); retrato base64 (nulo/inválido → ausente, WARN). 400 com `COULD_NOT_RECOGNIZE_DOCUMENT` → `(false, vazio, false)` (INFO); o resto → `FornecedorIndisponivel`. Neste passo `rostoConfere=false`.
- [ ] **Testes que falham:** `enviaSaveApiRequestFalse` (corpo tem `name="save_api_request"\r\n\r\nfalse` e `name="perform_document_liveness"\r\n\r\ntrue`); `enviaChaveNoCabecalhoXApiKey` (`chave-de-teste`, `/v3/id-verification/`, `POST`, `multipart/form-data`); `naoEnviaVendorData`; `enviaFrenteEVerso`; `aprovadoLeData` (`Approved`, `1990-05-10`, retrato, campos extras → aceito com a data); `documentoRecusado`; `emAnaliseContaComoNaoAceito`; `dataInvalidaViraVazia` (`10/05/1990`); `naoReconhecidoViraNaoAceito`; `chaveInvalidaOuSemSaldoViraIndisponivel` (401, 403; log `ERROR`); `erro500NaoRepete` (1 requisição); `respostaLentaFalhaPeloTimeoutSemRepetir` (atraso 3 s, leitura `PT0.2S` → < 1 s, 1 requisição); `logNaoTrazCorpoChaveNemImagem` (sem `corpo-canario`, `chave-de-teste`, base64 da frente).
- [ ] Rodar → FAIL. Implementar. Rodar → PASS.
- [ ] Commit `feat(conta): leitura do documento pela Didit sem guardar nada`

### Task A8: `ConferenciaDeDocumentoDidit`: Face Match
**Files:** Modify `m/conta/infra/ConferenciaDeDocumentoDidit.java`, `t/conta/infra/ConferenciaDeDocumentoDiditTest.java`.
**Interfaces:** Passo 2 `POST {url}/v3/face-match/` (`didit/rosto`), só com documento aceito, data e retrato: `user_image` = `selfie.bytes()`, `ref_image` = retrato, `face_match_score_decline_threshold` = `limiarRosto` (`Locale.ROOT`), `save_api_request=false`; `rostoConfere = "Approved".equals(face_match.status)`. Com data de menor o passo roda (rosto vem antes da idade em `peloDocumento`). Qualquer erro → `FornecedorIndisponivel`. Só o `ResultadoDoDocumento` sai do método.
- [ ] **Testes que falham:** `aprovadoComRostoConferido` (`(true, 1990-05-10, true)`); `faceMatchEnviaSelfieRetratoLimiarESaveFalse` (`30.0`; `ref_image` = `{1,2,3}`; `user_image` = bytes da selfie); `rostoAbaixoDoLimiarViraNaoConfere`; `documentoRecusadoNaoChamaFaceMatch`; `semRetratoNaoChamaFaceMatch` (`(true, data, false)`); `semDataNaoChamaFaceMatch`; `dataDeMenorAindaChamaFaceMatch`; `faceMatch500ViraIndisponivelSemRepetir` (2 requisições no total); `metricaSeparaDocumentoERosto`.
- [ ] Rodar → FAIL. Implementar. Rodar → PASS.
- [ ] Commit `feat(conta): comparação do rosto do documento com a selfie pela Didit`

### Task A9: Beans reais condicionais
**Files:** Create `m/conta/infra/FornecedoresDeVerificacaoConfig.java`. Test `t/conta/infra/FornecedoresDeVerificacaoConfigTest.java` (`ApplicationContextRunner`, modelo do `TurnstileComBuilderDoBootTest`).
**Interfaces:** `@EnableConfigurationProperties({ConfiguracaoAws.class, ConfiguracaoDidit.class})`; `@Bean MedicaoDeFornecedor` sempre; `@Bean @ConditionalOnProperty(name="duora.verificacao.provedor", havingValue="aws") ProvaDeVidaAws` (chama `exigirCompleta()` e monta os clientes com `Optional.empty()`); `@Bean @ConditionalOnProperty(name="duora.verificacao.documento.provedor", havingValue="didit") ConferenciaDeDocumentoDidit` (chama `exigirCompleta()`).
- [ ] **Testes que falham:** `falsoNaoCriaBeansReais`; `awsCriaProvaDeVidaECredencial` (chaves fictícias; um bean que serve `ProvaDeVida` e `CredencialDoNavegador`; sem rede); `diditCriaConferencia`; `awsSemChaveDerrubaOContexto` (`getStartupFailure()` com `duora.verificacao.aws.access-key-id`); `prodSemVariaveisDerrubaOContexto` (carrega `application-prod.properties`; falha cita `duora.verificacao.aws`).
- [ ] Rodar → FAIL. Implementar. Rodar o teste e depois `./mvnw -q verify` sob a trava → PASS.
- [ ] Commit `feat(conta): liga AWS e Didit por duora.verificacao.provedor`

### Task A10: Smokes manuais (fora da CI)
**Files:** Create `t/conta/infra/VerificacaoAwsSmokeIT.java` (`@EnabledIfEnvironmentVariable(named="DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID", matches=".+")`), `t/conta/infra/DiditSmokeIT.java` (`DUORA_DIDIT_API_KEY`) . As imagens não são versionadas: os smokes as leem de `DUORA_SMOKE_ROSTO` e `DUORA_SMOKE_DOCUMENTO` (caminhos locais, JPEG ou PNG) e são pulados se faltarem.
- [ ] `VerificacaoAwsSmokeIT.sessaoRealCredencialCurtaEFaixaDeAdulto`: `criarSessao` → `sessionId` de 36 caracteres; `emitir` → `expiraEm` entre +14 e +16 min; `GetCallerIdentity` com a credencial curta → `arn` contém `assumed-role/duora-java-liveness-captura/duora-`; a mesma credencial em `CreateFaceLivenessSession` → 400 `AccessDeniedException`; `resultado` de sessão não iniciada → `CRIADA`; `DetectFaces` no rosto sintético → um rosto, `low ≥ 18`. Custo < US$ 0,02.
- [ ] `DiditSmokeIT.sandboxAceitaFormatoEChave`: `conferir(documento-neutro, documento-neutro, rosto)` sem exceção, `documentoAceito`; anota a latência.
- [ ] `./mvnw -q verify` sem as variáveis: os dois aparecem pulados.
- [ ] Commit `test(conta): smokes manuais da AWS e da Didit (sandbox)`

### Task A11: ADR 0007
**Files:** Create `docs/adr/0007-verificacao-de-idade.md` (formato do 0006: Estado `aceita`, Data, Etapa `2b`, Contexto, Decisão, Consequências).
**Conteúdo obrigatório:** opções (a) tudo AWS com Textract `AnalyzeID` (só documentos dos EUA), (b) tudo Didit, (c) **AWS para prova de vida e estimativa + Didit só na dúvida**, (d) gov.br, (e) documento para todos; decisão (c) com os limiares, D-B, D-C, D-D e o 409 `REFAZER_PROVA_DE_VIDA`; dados não guardados (`AuditImagesLimit=0`, sem `OutputConfig`, `save_api_request=false`, só memória, fica resultado/método/data, tentativas 1 dia); transferência internacional (AWS `us-east-1` pela SCP; Didit conforme a política de privacidade e o DPA, a reconferir na Etapa 10); credenciais (`duora-java-verificacao` por ambiente/Keychain; navegador com 900 s do papel `duora-java-liveness-captura` e política de sessão só `StartFaceLivenessSession`); custos de 2026-11-27 (selfie US$ 0,016; dúvida US$ 0,282 no total; teto por conta/dia 3 × 0,016 + 2 × 0,266 ≈ US$ 0,58; orçamento `duora-rekognition-5usd`); orçamento de tempo (8 s + 15 s < 30 s); fotossensibilidade (caminho manual pelo encarregado); observabilidade (Timer, log sem PII, sem health ativo); consequências (dois fornecedores de biometria na política; 503 sem gastar tentativa; trocar de fornecedor = trocar o adapter).
- [ ] Escrever; conferir cada número contra as Interfaces canônicas e o `OrcamentoDeTempoTest`.
- [ ] Commit `docs(adr): 0007 verificação de idade com AWS e Didit`

---

## Trilha F — front, CSP e e2e

Conferido em 2026-11-27: Context7 `/aws-amplify/amplify-ui` (`FaceLivenessDetectorCore`, `credentialProvider` chamado uma vez, `systemClockOffset`, `onError({ state })`); npm `@aws-amplify/ui-react-liveness` 3.7.0 (`sha512-dIxvhFGowfhsW0YMVntzhx624W2kSvfw2mVt8ZAr14x/MM8qyD8iOw5vRH3dRmgnuVqUG7H9/Q6et+pV1xnd4w==`, peer React 19 ok, TensorFlow.js 4.11.0); CDN padrão `https://cdn.liveness.rekognition.amazonaws.com/face-detection/tensorflow/tfjs-backend-wasm/4.11.0/` e `…/tensorflow-models/blazeface/1.0.2/model/model.json`. Referência do C#: `../../CSharp/Duora/infra/caddy/Caddyfile:102-103`, `frontend/e2e/selfie-csp.spec.ts`, `frontend/config/liveness-supply-chain.test.ts`.

### Task F1: CSP e Permissions-Policy da `/verificacao`
**Files:** Modify `m/web/CabecalhosDeSeguranca.java`, `m/web/RotasDoSpa.java` (+ `/verificacao`), `fe/src/rotas/caminhosDaCasca.ts` (+ `/verificacao`). Test `t/web/CspDaVerificacaoTest.java` (`@SpringBootTest @AutoConfigureMockMvc`).
**Interfaces:** Produces `CSP_VERIFICACAO`, `PERMISSIONS_POLICY_VERIFICACAO`, `PERMISSIONS_POLICY` (canônicos).
- [ ] **Testes que falham** (literais no teste, nunca lidas da constante): `verificacaoTrazAPoliticaPropriaInteira`; `verificacaoTrazUmSoCabecalhoDeCsp`; `verificacaoLiberaSoACamera` (`Permissions-Policy` = `camera=(self), microphone=()`); `origensExternasSaoSoOCdnEOStreaming` (os `https://`/`wss://` do `connect-src` são exatamente os dois); `@ValueSource({"/","/entrar","/conta","/api/conta","/verificacao/","/verificacao.html","/app/qualquer.js"}) outraRotaFicaComAPoliticaDoApp` (CSP = `CSP`, sem `wasm-unsafe-eval`/`amazonaws`; `Permissions-Policy` = `camera=(), microphone=()`); `verificacaoServeOIndexSemCache` (`/index.html`, `no-cache`).
- [ ] `./mvnw -q test -Dtest='CspDaVerificacaoTest,RotasDoSpaTest,CspTest'` → FAIL.
- [ ] Implementar (`"/verificacao".equals(request.getRequestURI())`; javadoc com os três desvios do C# e "mudou aqui, mude `CspDaVerificacaoTest` e `cadeiaDeSuprimentos.test.ts`").
- [ ] Os mesmos → PASS.
- [ ] Commit `feat(web): CSP e Permissions-Policy próprias da rota /verificacao`

### Task F2: Cliente da API da verificação e envio multipart
**Files:** Modify `fe/src/api/{http.ts,http.test.ts}`. Create `fe/src/api/{verificacao.ts,verificacao.test.ts}`.
**Interfaces:** Consumes `requisitarHttp`, `ErroHttp`, contrato REST. Produces `api/verificacao.ts` e `formulario` (canônicos).
- [ ] **Testes que falham:** `formularioVaiSemContentTypeEComXDuora`; `obterVerificacaoLeOModo`; `obterVerificacaoRecusaModoDesconhecido` (`RESPOSTA_INVALIDA`); `iniciarSessaoMandaConsentimentoEFinalidade` (corpo `{"consentimentoBiometrico":true,"finalidade":"DOCUMENTO"}`); `iniciarSessaoConverteExpiraEmEmDate` (`2026-11-27T12:15:00.000Z`); `iniciarSessaoRecusaOutraRegiao` (`sa-east-1`); `desvioDoRelogioSoAcimaDeCincoMinutos` (−299 s → `undefined`; −300 s → `-300000`; +600 s → `600000`; `null` → `undefined`); `iniciarSessaoCalculaODesvioPeloCabecalhoDate`; `concluirSelfiePostaNoCaminhoDaSessao` (`/api/conta/verificacao/sessao/abc/resultado`); `desfechoTrazAsContagens`; `enviarDocumentoMandaSessaoFrenteEVerso` (chaves `["sessaoId","frente","verso"]`, `image/jpeg`); `erroDoServidorViraErroHttpComCodigo` (503 → `transitorio`).
- [ ] `pnpm test -- src/api/verificacao.test.ts src/api/http.test.ts` → FAIL.
- [ ] Implementar (`corpo` + `formulario` juntos → `TypeError`; validação manual como `estadoDaConta.ts`; credencial só no objeto devolvido).
- [ ] → PASS; `pnpm lint`.
- [ ] Commit `feat(front): cliente da API de verificação de idade com envio multipart`

### Task F3: Rota isolada por documento, destino pós-cadastro e selo em Minha conta
**Files:** Modify `fe/src/rotas/{rotas.tsx,rotas.test.tsx,montar.tsx,montar.test.tsx,guardas.ts,guardas.test.ts,consultas.ts,caminhosDaCasca.ts}`, `fe/src/telas/conta/{Cadastro,ConfirmarEntrada,MinhaConta}.tsx` e testes, `fe/src/telas/conta/{textos.ts,textos.test.ts}`, `fe/src/{pwa.ts,pwa.test.ts}`, `m/web/RotasDoSpa.java` (sai `/aguardando`), `t/e2e/{ContaPagina,ContaIT,AxeContaIT}.java`. Create `fe/src/telas/verificacao/{VerificacaoPagina.tsx,VerificacaoPagina.test.tsx,textos.ts}` (só título, carregamento e erro). Delete `fe/src/telas/conta/Aguardando{.tsx,.test.tsx}`.
**Interfaces:** Consumes `exigirConta`, `consultaDaConta`, `consultaDasVersoesLegais`, F2, `redirectDocument` (react-router 7.18.6). Produces `isolarDocumentoDaVerificacao`, `CAMINHO_DA_VERIFICACAO`, `caminhoDaCarga`, `consultaDaVerificacao`; loader da rota `verificacao` = `{ conta: await exigirConta(cliente, ["AGUARDANDO_VERIFICACAO"]), situacao: await cliente.fetchQuery(consultaDaVerificacao), legal: await cliente.fetchQuery(consultaDasVersoesLegais) }`; `TELA_DO_ESTADO.AGUARDANDO_VERIFICACAO = "/verificacao"`.
- [ ] **Testes que falham.** Conferir no pacote instalado o cabeçalho do `redirectDocument` (`X-Remix-Reload-Document`) e citá-lo numa constante do teste.
  - `rotas.test.tsx`: `verificacaoAbertaPeloRoteadorRecarregaODocumento` (`caminhoDaCarga "/conta"` → 302 `/verificacao` + recarga); `saidaDaVerificacaoPeloRoteadorRecarregaODocumento` (`/termos?x=1`); `rotaSemLoaderTambemSaiPorDocumento` (`*`); `verificacaoNoDocumentoProprioChamaOLoaderOriginal`; `foraDaVerificacaoNadaMuda`.
  - `guardas.test.ts.aguardandoVaiParaAVerificacao`; `montar.test.tsx.passaOCaminhoDaPrimeiraCarga`; `Cadastro.test.tsx.cadastroConcluidoAbreAVerificacaoPorPaginaInteira` e `cadastroJaConcluidoAbreAVerificacaoPorPaginaInteira` (`navegarParaPagina("/verificacao")`); `ConfirmarEntrada.test.tsx.aguardandoVerificacaoAbreAVerificacaoPorPaginaInteira`.
  - `MinhaConta.test.tsx`: `aguardandoMostraLinkParaConfirmarIdade` (`<a href="/verificacao">` "Confirmar minha idade", não `Link`); `verificadaMostraOSelo` ("Conta verificada" + "Confirmamos que você tem 18 anos ou mais."); `naoVerificadaNaoMostraOSelo`.
  - `textos.test.ts.nenhumTextoPrometeEtapaFutura` (sem "chega em breve"); `pwa.test.ts.naoRegistraServiceWorkerNaVerificacao`; `VerificacaoPagina.test.tsx`: `mostraOTituloEAAba` (`h1` "Confirme sua idade", `<title>` "Confirmar idade · Duora"), `falhaNaLeituraVaiParaOErroDaCasca`.
- [ ] `pnpm test -- src/rotas src/telas/conta src/telas/verificacao src/pwa.test.ts` → FAIL.
- [ ] Implementar: o embrulho lança `redirectDocument(pathname + search)` na filha `verificacao` com `caminhoDaCarga !== "/verificacao"` e em qualquer outra com `caminhoDaCarga === "/verificacao"`; textos `faltaConfirmar` "Falta confirmar sua idade.", `confirmarIdade`, `selo`, `descricaoDoSelo`; saem `AGUARDANDO` e `PROXIMO_PASSO_DA_VERIFICACAO`.
- [ ] → PASS. `./mvnw -q verify -Dtest='RotasDoSpaTest,TamanhoDoJsInicialTest,ChunksDoFrontTest'` (anotar o JS inicial no commit); depois, um navegador por vez, `./mvnw -q -Pe2e verify -Dit.test='ContaIT,MinhaContaIT,AxeContaIT' -Dnavegador=chromium`.
- [ ] Commit `feat(front): rota /verificacao isolada por documento e selo de conta verificada`

### Task F4: Explicação, consentimento, fotossensibilidade e início da sessão
**Files:** Create `fe/src/telas/verificacao/{Explicacao,camera,etapas}.ts(x)` e testes. Modify `VerificacaoPagina.tsx`, `VerificacaoPagina.test.tsx`, `textos.ts`, `t/e2e/AxeContaIT.java`. Create `t/e2e/{VerificacaoPagina,ApiDeVerificacaoSimulada}.java`.
**Interfaces:** Consumes `iniciarSessao`, `consultaDaVerificacao`, `consultaDasVersoesLegais` (`encarregado.email`), `textoDeEspera`, `BOTAO_PRIMARIO`, `seDisponivel`. Produces `proximaEtapa(etapa: Etapa, evento: Evento): Etapa` com as etapas `explicacao | checandoCamera | semCamera{motivo} | iniciando{finalidade} | captura{sessao, finalidade} | conferindo{sessaoId} | falhaDoDetector{falha} | desfecho{tipo:"aprovada"|"repetir"|"analise"|"menor"|"sessaoInvalida"} | documento{frente?, verso?, aviso?} | enviandoDocumento | limite{segundos:number|null} | indisponivel{repetir:Evento}`. Inicial: `AGUARDANDO` com tentativas > 0 → `explicacao`, com 0 → `limite{null}`; `DUVIDA` com envios > 0 → `documento{}`, com 0 → `desfecho{analise}`; `APROVADA` → `desfecho{aprovada}`. `navegadorTemSuporte(janela?)`, `checarCamera(midia?): Promise<"ok"|"negada"|"ausente"|"ocupada">`.
**Textos:** título "Confirme sua idade"; parágrafos "O Duora é só para maiores de 18 anos. Para confirmar, pedimos uma selfie em vídeo de poucos segundos." e "A AWS, nos Estados Unidos, confere se há uma pessoa real diante da câmera e estima a idade. Usamos a imagem só para isso e a apagamos logo depois. Guardamos só o resultado, a data e o método."; dicas "Procure um lugar claro." · "Tire óculos escuros, boné ou máscara." · "Fique sem outra pessoa no quadro."; `avisoDeLuz` "Durante a selfie, a tela pisca em cores. Se luzes piscando fazem mal a você, não comece: escreva para {email} e combinamos outro jeito de confirmar sua idade." (`mailto:`); `consentimento` "Concordo com o uso da minha imagem para confirmar minha idade"; `comecar` "Começar a selfie"; `semConsentimento` "Marque a caixa para começar."; `semSuporte` "Este navegador não abre a câmera. Abra o Duora no Chrome, no Safari ou no Firefox atualizado."; `tentativas(n)` "Tentativas restantes hoje: {n}"; `abrindoCamera` "Abrindo a câmera…"; `preparando` "Preparando a selfie…"; `cameraNegada` "Não conseguimos usar a câmera. Libere o acesso nas configurações do navegador e toque em Tentar de novo."; `cameraAusente` "Não encontramos uma câmera neste aparelho. Use um celular ou computador com câmera."; `cameraOcupada` "Outro app está usando a câmera. Feche esse app e tente de novo."; `limiteDoDia` "Você usou as tentativas de hoje. Volte amanhã para tentar de novo."; `servicoFora` "O serviço de verificação não respondeu. Tente de novo em instantes."; `tentarDeNovo` "Tentar de novo".
- [ ] **Testes que falham.**
  - `etapas.test.ts` (tabela): etapa inicial por linha; `consentimentoComCameraOkIniciaSessao`; `cameraNegadaNaoGastaTentativa`; `servico503VaiParaIndisponivelComRepeticao`; `limite429GuardaOsSegundos` (`LIMITE_DE_TAXA` e `LIMITE_DE_VERIFICACAO`, `Retry-After: 120` → `limite{120}`).
  - `camera.test.ts`: `checarCameraParaAsTrilhas`; `erroDaCameraViraMotivo` (`NotAllowedError`/`SecurityError` → `negada`; `NotFoundError`/`OverconstrainedError` → `ausente`; `NotReadableError` → `ocupada`); `semMediaDevicesNaoTemSuporte`; `semWebAssemblyNaoTemSuporte`; `contextoInseguroNaoTemSuporte`.
  - `Explicacao.test.tsx`: `comecarIndisponivelSemConsentimento`; `comecarIndisponivelSemSuporte`; `mostraAsTentativasRestantes`; `explicaAwsEUsoDaImagem`; `avisoDeLuzOfereceOCaminhoManual` (texto exato com `encarregado@exemplo.invalid` e `href="mailto:encarregado@exemplo.invalid"`).
  - `VerificacaoPagina.test.tsx`: `modoAwsChecaACameraAntesDeCriarASessao`; `modoFalsoNaoChecaCamera`; `cameraNegadaMostraComoReativarETentaDeNovo`; `cliqueDuploIniciaUmaSessaoSo`; `servicoForaMostraTentarDeNovoQueRepete`; `limiteMostraTextoDeEspera` ("Muitas tentativas. Tente de novo em 2 minutos."); `zeroTentativasAbreNoLimite`; `foraDeOrdemRecarregaPelaConta` (`navegarParaPagina("/conta")`); `focoVaiParaOTituloDeCadaEtapa`; `credencialNuncaVaiParaOStorage`; `credencialNaoApareceNoDom`.
- [ ] Rodar → FAIL. Implementar (`useReducer(proximaEtapa)`, `useMutation` com trava `useRef`, `aria-busy`/`role="status"`, sessão só no redutor). → PASS.
- [ ] **e2e:** `VerificacaoPagina.java` (`consentirEComecar()`, `heading`, `textoVisivel`); `ApiDeVerificacaoSimulada.java` (`instalar(Page)` com `**/api/conta` `AGUARDANDO_VERIFICACAO` e `**/api/conta/verificacao` `{"estado":"AGUARDANDO","tentativasRestantesHoje":3,"enviosDeDocumentoRestantesHoje":2,"modo":"falso"}`; `comSituacao`, `comSessao`, `comResultado`, `comDocumento`); `AxeContaIT`: `verificacaoExplicacao`, `verificacaoLimite`, `verificacaoServicoFora`. Três navegadores, um por vez.
- [ ] Commit `feat(front): explicação e consentimento da verificação de idade`

### Task F5: Detectores (Amplify sob demanda e falso), chunk próprio e cadeia de suprimentos
**Files:** Modify `fe/package.json`, `fe/pnpm-lock.yaml` (`pnpm add --save-exact @aws-amplify/ui-react-liveness@3.7.0 aws-amplify@6.22.2`), `fe/vite.config.ts`, `fe/eslint.config.js`, `fe/avisos-do-build.test.ts` (timeout 180_000). Create `fe/src/telas/verificacao/{CapturaDeVida,DetectorAmplify,DetectorFalso,textosDoDetector}.ts(x)` e testes, `cadeiaDeSuprimentos.test.ts`. Modify `t/pwa/{ChunksDoFrontTest,ShellDoServiceWorkerTest}.java`.
**Interfaces:** Produces `CapturaDeVida(props: PropsDoDetector)` (escolhe por `sessao.modo` com `React.lazy(() => import("./DetectorAmplify"))` ou `import("./DetectorFalso")`, `<section aria-labelledby>` "Selfie em vídeo", `Suspense` "Abrindo a câmera…", error boundary → `aoFalhar("carregamento")`); `DetectorAmplify` (`FaceLivenessDetectorCore` com `sessionId`, `region="us-east-1"`, `disableStartScreen`, `displayText={TEXTOS_DO_DETECTOR}`, `config={{ credentialProvider: () => Promise.resolve(sessao.credencial), systemClockOffset: sessao.desvioDoRelogioMs }}` sem `binaryPath`/`faceModelUrl`, `onAnalysisComplete` → `aoConcluir`, `onUserCancel`, `onError={({ state }) => aoFalhar(falhaDoEstado(state))}`, importa o `styles.css`); `falhaDoEstado(ErrorState): FalhaDoDetector` exaustivo (`CAMERA_ACCESS_ERROR` → `camera`; `TIMEOUT` e afins → `tempo`; `FACE_DISTANCE_ERROR` → `rosto`; `MULTIPLE_FACES_ERROR` → `outraPessoa`; `CONNECTION_TIMEOUT` → `conexao`; `SERVER_ERROR` → `servico`; `CAMERA_FRAMERATE_ERROR`/`RUNTIME_ERROR` → `aparelho`; `MOBILE_LANDSCAPE_ERROR` → `orientacao`; lista exata do `.d.ts`); `DetectorFalso` ("Modo de teste: nenhuma câmera é usada.", "Simular captura" → `aoConcluir`, "Cancelar"; nenhum global); `TEXTOS_DO_DETECTOR` = a lista do C# (`SelfieCapture.tsx:27-80`) com "Só uma pessoa por vez", "Fique sem outra pessoa no quadro.", "Conferindo a selfie…" e `a11yVideoLabelText` "Prévia da câmera para a selfie".
- [ ] **Testes que falham.**
  - `CapturaDeVida.test.tsx`: `amplifySoCarregaComModoAws`; `falsoSoCarregaComModoFalso`; `falhaNoDownloadViraCarregamento`; `regiaoComTituloSelfieEmVideo`.
  - `DetectorAmplify.test.tsx`: `credentialProviderDevolveACredencialDaSessao`; `configSoTemCredencialEDesvio` (`Object.keys(config)` = `["credentialProvider","systemClockOffset"]`); `regiaoFixa`; `semTelaInicialDoAmplify`; `estadoDeErroViraFalha` (tabela); `cancelarChamaAoCancelar`.
  - `textosDoDetector.test.ts`: `cobreTodasAsChavesDoDisplayText` (`satisfies Required<LivenessDisplayText>`); `semFlexaoDeGenero` (nada casa `/\b(sozinh[oa]|pront[oa]|obrigad[oa])\b/i`).
  - `DetectorFalso.test.tsx`: `simularCapturaConclui`; `naoTocaNoWindow`.
  - `cadeiaDeSuprimentos.test.ts` (`@vitest-environment node`): `versaoExataDoPacote`; `lockfileTravaVersaoEIntegridade` (as integridades de `@aws-amplify/ui-react-liveness@3.7.0` e de `@tensorflow/tfjs-backend-wasm@4.11.0` `sha512-ubv+5cXux8al/ccZvmDpuw5PNm8FAj57GHzC0nDkX6lHJ0iXNklzUPK5SoTYnU7eYwXXzv8synjnqiqnWvfDEA==`); `instaladoNaVersaoFixadaComOsTresWasm`; `detectorCarregaDasUrlsFixadas` (origem = a literal do `CspDaVerificacaoTest`). Comentário: "falhar aqui é o objetivo: subir o pacote ou trocar a origem exige rever a CSP (F1) e este pino".
  - `ChunksDoFrontTest`: `amplifyNumChunkProprioForaDoJsInicial` (`app/verificacao-amplify-*.js`; nada no `jsInicial()`; `casca-de-conta-*` sem `FaceLivenessDetector`); `soOChunkDoAmplifyPassaDe500KB` (resto ≤ 512_000 B); `nenhumGanchoGlobalNoBundle` (sem `__duora`); `detectorFalsoSoPorImportDinamico`. `ShellDoServiceWorkerTest.shellNaoGuardaAVerificacao`.
- [ ] Vitest e `./mvnw -q verify -Dtest='ChunksDoFrontTest,ShellDoServiceWorkerTest,TamanhoDoJsInicialTest'` → FAIL.
- [ ] Implementar: grupo `verificacao-amplify` em `codeSplitting.groups` (`/node_modules\/(@aws-amplify|aws-amplify|@aws-sdk|@aws-crypto|@smithy|@tensorflow|@tensorflow-models|@mediapipe|xstate|@xstate)\//`, `includeDependenciesRecursively: false`); `chunkSizeWarningLimit` = tamanho medido × 1,1 arredondado à centena; ESLint `no-restricted-imports` com `AMPLIFY` (mensagem "Amplify só em src/telas/verificacao/DetectorAmplify.tsx (carregado sob demanda)") e exceção só para `DetectorAmplify.tsx`; conferir à mão que importar o Amplify em `Explicacao.tsx` quebra o `pnpm lint`.
- [ ] → PASS (zero avisos de build; JS inicial anotado no commit).
- [ ] Commit `feat(front): detector de prova de vida do Amplify sob demanda e cadeia de suprimentos fixada`

### Task F6: Conferência da selfie e desfechos
**Files:** Create `fe/src/telas/verificacao/{Desfecho.tsx,Desfecho.test.tsx}`. Modify `VerificacaoPagina.tsx`, `etapas.ts`, `textos.ts` e testes, `t/e2e/{AxeContaIT,VerificacaoPagina}.java`.
**Interfaces:** Consumes `concluirSelfie`, `CapturaDeVida`, `consultaDasVersoesLegais`. Produces as transições `captura{selfie}` + `concluiu` → `conferindo`; daí `APROVADA` → `desfecho{aprovada}`; `REPETIR` → `desfecho{repetir}`; `DUVIDA` com `pedeDocumento` → `documento{}`, sem → `desfecho{analise}`; 422 `MENOR_DE_IDADE` → `desfecho{menor}` (consulta da conta sai do cache); 404 → `desfecho{sessaoInvalida}`; 503 ou 409 `REQUISICAO_EM_ANDAMENTO` → `indisponivel{repetir: conferir(sessaoId)}`; `captura` + `falhou(f)` → `falhaDoDetector{f}`; `cancelou` → `explicacao`. As contagens vêm do `Desfecho`; depois de cada desfecho, `invalidateQueries(consultaDaVerificacao)`.
**Textos:** `aprovada` "Idade confirmada" / "Confirmamos que você tem 18 anos ou mais. Sua conta está ativa." / `<a href="/">Continuar</a>`; `repetir` "Não deu para confirmar" / "Procure um lugar claro, tire óculos escuros e tente de novo." + tentativas + "Tentar de novo" (0 → `limiteDoDia`); `analise` "Conta em análise" / "Precisamos de mais uma checagem. Se quiser contestar, escreva para {email}." (`mailto:`) + "Voltar ao terraço"; `menor` "Cadastro não concluído" / "O Duora é só para maiores de 18 anos. Encerramos o cadastro." + "Voltar ao terraço"; `sessaoInvalida` "Esta selfie não vale mais. Comece de novo."; falhas: `camera` = `cameraNegada`; `tempo` "O tempo da selfie acabou. Comece de novo quando puder."; `rosto` "Não conseguimos ver seu rosto direito. Centralize o rosto, com boa luz, e tente de novo."; `outraPessoa` "Só você pode aparecer na selfie. Peça para a outra pessoa sair do quadro."; `conexao` "A conexão caiu durante a selfie. Confira a internet e tente de novo."; `servico` = `servicoFora`; `aparelho` "Esta câmera ou este navegador não deu conta da selfie. Tente em outro aparelho."; `orientacao` "Deixe o celular em pé e tente de novo."; `carregamento` "Não conseguimos carregar a selfie. Confira a conexão e tente de novo." (cada uma com "Tentar de novo" e tentativas).
- [ ] **Testes que falham:** `etapas.test.ts` uma linha por transição (inclui `cancelarVoltaParaExplicacao`, `servico503RepeteComOMesmoSessaoId`, `emAndamentoRepeteComOMesmoSessaoId`); `VerificacaoPagina.test.tsx`: `aprovadaMostraContinuarParaOTerraco` (`href="/"`); `repetirMostraTentativasDoDesfecho` ("Tentativas restantes hoje: 1"); `menorMostraTextoFinalEEsqueceAConta`; `analiseMostraOEmailDoEncarregado`; `duvidaComDocumentoVaiParaOEnvio` ("Falta pouco"); `sessaoInvalidaVoltaParaExplicacao`; `servicoForaRepeteAConferenciaSemNovaSessao` (`concluirSelfie("abc")` 2×, `iniciarSessao` 1×); `falhaDoDetectorMostraTexto` (tabela); `conferindoAnunciaEspera`; `Desfecho.test.tsx.focoNoTituloDoDesfecho`.
- [ ] Rodar → FAIL. Implementar. → PASS.
- [ ] **e2e:** `VerificacaoPagina.simularCaptura()`; `AxeContaIT`: `verificacaoCapturaSimulada`, `verificacaoAprovada`, `verificacaoRepetir`, `verificacaoMenor`, `verificacaoEmAnalise` (situação `DUVIDA` com `enviosDeDocumentoRestantesHoje:0`). Três navegadores.
- [ ] Commit `feat(front): conferência da selfie e desfechos da verificação de idade`

### Task F7: Documento na faixa de dúvida (D-B)
**Files:** Create `fe/src/telas/verificacao/{EnvioDeDocumento.tsx,EnvioDeDocumento.test.tsx,fotos.ts,fotos.test.ts}`, `t/e2e/DocumentoSintetico.java` (`static byte[] jpeg(String rotulo)`, 1200×760, "ESPÉCIME SINTÉTICO" por `ImageIO`). Modify `VerificacaoPagina.tsx`, `etapas.ts`, `textos.ts` e testes, `t/e2e/{AxeContaIT,VerificacaoPagina}.java`.
**Interfaces:** Consumes `enviarDocumento`, `iniciarSessao`, `CapturaDeVida`. Produces `reduzirFoto(arquivo: File, ladoMaximo = 2000, qualidade = 0.85): Promise<Blob>` (`createImageBitmap(arquivo, { imageOrientation: "from-image" })` → canvas → `toBlob("image/jpeg", 0.85)`; falha → `Error("FOTO_ILEGIVEL")`); prévia em `<canvas aria-hidden>` sem URL `blob:`. Fluxo: `documento{frente, verso}` (as duas obrigatórias) → "Fazer a selfie" → `checandoCamera` → `iniciando{documento}` (`iniciarSessao("DOCUMENTO")`) → `captura{documento}` → `concluiu` → `enviandoDocumento` (`POST /documento` imediato com o `sessaoId` novo). Desfechos: `APROVADA` → `desfecho{aprovada}`; `DUVIDA` → `desfecho{analise}`; `REPETIR` → `documento{}` + `naoLeu` (fotos apagadas); 409 `REFAZER_PROVA_DE_VIDA` → `documento{frente, verso, aviso: selfieVenceu}`; 422 `DOCUMENTO_INVALIDO` ou 413 → `documento{}` + `fotosIlegiveis`; 422 `MENOR_DE_IDADE` → `desfecho{menor}`; 429 → `limite`; 503 ou 409 `REQUISICAO_EM_ANDAMENTO` → `indisponivel{repetir: reenviar}` (mesmo multipart). Fotos só no redutor; somem em todo desfecho.
**Textos:** `titulo` "Falta pouco"; `texto` "A selfie não bastou para confirmar sua idade. Envie fotos do seu RG ou da sua CNH, frente e verso, e faça uma selfie nova. A Didit, empresa de verificação de identidade, confere a data de nascimento e se o rosto do documento é o seu. A Didit apaga as imagens logo depois; guardamos só o resultado, a data e o método."; rótulos "Frente do documento" / "Verso do documento" (`accept="image/jpeg,image/png" capture="environment"`); dica "Com boa luz, sem reflexo e com as quatro pontas aparecendo."; "Trocar foto"; `fazerSelfie` "Fazer a selfie" (motivo `faltamFotos` "Escolha as fotos da frente e do verso."; dica "Logo depois da selfie, enviamos tudo de uma vez."); `enviando` "Conferindo o documento… Pode levar até 30 segundos."; `naoLeu` "Não conseguimos ler o documento. Use boa luz, sem reflexo, com as quatro pontas aparecendo." + "Envios restantes hoje: {n}"; `selfieVenceu` "A selfie venceu antes do envio. Faça uma nova; as fotos continuam aqui."; `fotosIlegiveis` "Não conseguimos abrir as fotos. Tire as fotos de novo."; `fotoIlegivel` "Não conseguimos abrir esta foto. Tire outra."; `conferenciaFora` "O serviço que confere o documento não respondeu. Tente de novo em instantes.".
- [ ] **Testes que falham:** `fotos.test.ts`: `ladoMaiorFicaEm2000` (4000×3000 → 2000×1500; 1200×800 igual); `saiJpegComQualidade085`; `pedeAOrientacaoDoExif`; `fotoIlegivelRejeita`. `etapas.test.ts` uma linha por transição (inclui `selfieDoDocumentoUsaFinalidadeDocumento`, `refazerProvaDeVidaMantemAsFotos`). `EnvioDeDocumento.test.tsx`: `fazerSelfieIndisponivelSemAsDuasFotos`; `mencionaADiditEOApagamento`; `fotoIlegivelMostraErroNoCampo` (`aria-invalid`, `aria-describedby`); `inputsAceitamJpegPngComCameraTraseira`. `VerificacaoPagina.test.tsx`: `envioSaiLogoDepoisDaSelfieComASessaoNova` (`enviarDocumento({sessaoId:"s2"})`, sem `concluirSelfie("s2")`); `refazerProvaDeVidaVoltaParaASelfieComAsFotos`; `naoLeuApagaAsFotosEMostraEnvios` ("Envios restantes hoje: 1"); `cliqueDuploEnviaUmaVez`; `voltaNoMesmoDiaAbreNoDocumento`.
- [ ] Rodar → FAIL. Implementar. → PASS.
- [ ] **e2e:** `VerificacaoPagina.escolherDocumento(byte[], byte[])` (`setInputFiles(FilePayload)`) e `fazerSelfieDoDocumento()`; `AxeContaIT`: `verificacaoDocumento`, `verificacaoDocumentoNaoLido`, `verificacaoSelfieVenceu` (documento 409 `REFAZER_PROVA_DE_VIDA`). Três navegadores.
- [ ] Commit `feat(front): documento e selfie nova num envio só na faixa de dúvida`

### Task F8: Jornadas e2e (servidor real, perfil `verificacao-falsa`)
**Files:** Create `t/e2e/VerificacaoIT.java` (`extends ContaE2eBase`, `@ActiveProfiles("verificacao-falsa")`). Modify `t/e2e/ContaE2eBase.java` (`protected static String emailCom(String... marcadores)` → `"conta-" + UUID + "+" + String.join("+", marcadores) + "@exemplo.com"`).
**Interfaces:** Consumes V4–V6, F3–F7, `ContaPagina.cadastrarContaNova`, `TurnstileFalso`. Nenhuma mudança de produção: defeito volta para a task de origem.
- [ ] **Testes que falham** (cada um cadastra pela jornada real e chega a "Confirme sua idade"): `aprovadaPelaSelfieChegaAoTerracoComSelo` ("Idade confirmada" → "Continuar" → raiz → `/conta` com "Conta verificada" e sem "Confirmar minha idade"); `menorPelaSelfieERecusado` (`idade15` → texto do menor; `/conta` → `/entrar`); `duvidaComDocumentoAprova` (`idade19` → "Falta pouco" → fotos → selfie → "Idade confirmada"; resposta de `POST /api/conta/verificacao/documento` 200 com pedido multipart); `documentoDeOutraPessoaFicaEmAnalise` (`idade19`, `docoutro` → "Conta em análise" com `mailto:`); `verificacaoAbertaPeloLinkDeMinhaContaRecarregaODocumento` (resposta de documento de `/verificacao` com `wasm-unsafe-eval`; "Continuar" → resposta de `/` com a CSP do app); `saidaPorCadaLinkDoRodapeRecarregaODocumento` (`@ParameterizedTest` sobre os links do `Rodape` a partir de `/verificacao`: cada clique gera resposta de documento com a CSP do app, sem `wasm-unsafe-eval`).
- [ ] `./mvnw -q -Pe2e verify -Dit.test=VerificacaoIT -Dnavegador=chromium` → FAIL até as telas e o perfil estarem na `etapa-2b`.
- [ ] Ajustar só page objects e base. Chromium, Firefox e WebKit, um por vez → PASS.
- [ ] Commit `test(e2e): jornadas da verificação de idade nos três navegadores`

### Task F9: O Amplify real sob a CSP servida pelo back
**Files:** Create `t/e2e/VerificacaoCspIT.java` (`extends ContaE2eBase`, sem perfil extra).
**Interfaces:** Consumes a CSP real do `CabecalhosDeSeguranca` e `ApiDeVerificacaoSimulada` com `modo:"aws"` e sessão sintética `{sessaoId:"0199f1a2-3b4c-7d5e-8f60-718293a4b5c6", regiao:"us-east-1", modo:"aws", credencial:{…"synthetic"…, expiraEm:"2099-01-01T00:00:00Z"}}`.
- [ ] **Testes que falham** (`addInitScript` grava cada `securitypolicyviolation` em `document.documentElement.dataset.violacoesDeCsp`; `page.onPageError` guarda as mensagens): `explicacaoSemViolacaoDeCsp` (três navegadores; `modo:"falso"`; marcar a caixa → `violacoesDeCsp` `null`, nenhum `pageerror` casa `/Content Security Policy|TrustedHTML|TrustedScript/`); `detectorOficialAbreCameraEStreamingSobACspReal` (só Chromium: `--use-fake-device-for-media-stream`, `--use-fake-ui-for-media-stream`, `grantPermissions(["camera"])`, `routeWebSocket` mudo para `wss://streaming-rekognition.us-east-1.amazonaws.com/`, CDN abortado; em 15 s os hosts = `["streaming-rekognition.us-east-1.amazonaws.com"]`, o `video` da região "Selfie em vídeo" tem `srcObject.active`, nenhuma violação; navegador próprio fechado no `finally`).
- [ ] Rodar com a CSP do app no lugar (ou antes de F1) → FAIL por `connect-src`.
- [ ] Com F1–F5 → PASS em Chromium; `explicacaoSemViolacaoDeCsp` também em Firefox e WebKit.
- [ ] Commit `test(e2e): detector oficial do Amplify sob a CSP real da verificação`

---

## Task A12: Operação na AWS (manual com aval já dado; precisa do `/mcp` do dono)
Última task da etapa, depois de tudo na `etapa-2b`. Sem arquivo versionado. Única task autorizada a criar ou alterar recursos. Pelo MCP da AWS (`run_script`), nunca pelo CLI:
- [ ] `iam:GetUser`/`GetRole`: `duora-java-verificacao` e `duora-java-liveness-captura` existem, `MaxSessionDuration` ≥ 900, a confiança do papel aceita o usuário.
- [ ] Tirar `textract:AnalyzeID` da política do usuário; `iam:SimulatePrincipalPolicy` dá `allowed` para `rekognition:CreateFaceLivenessSession`, `GetFaceLivenessSessionResults`, `DetectFaces` e `sts:AssumeRole`, e `implicitDeny` para `textract:AnalyzeID`.
- [ ] `iam:CreateAccessKey` (uma só ativa), guardada no Keychain (`security add-generic-password -s duora-java-verificacao -a <access key id> -w`) sem ecoar o segredo.
- [ ] Rodar o `VerificacaoAwsSmokeIT` uma vez com as chaves exportadas só na sessão do comando (custo < US$ 0,02); anexar a saída, sem chaves, ao relatório. O `DiditSmokeIT` roda quando o dono tiver a chave sandbox (D-C).

## Riscos aceitos
- **Formato do 400 da Didit:** só `COULD_NOT_RECOGNIZE_DOCUMENT` vira "não aceito"; outro código transforma foto ruim em 503. Rever no primeiro uso com chave de produção (Etapa 10).
- **Latência da Didit real:** o sandbox não processa; se o ID Verification passar de 9 s, a pessoa vê 503. Medir na Etapa 10 e, se preciso, subir `reserva-maxima` junto com os timeouts (o `OrcamentoDeTempoTest` obriga a mexer nos dois).
- **Amplify real em Firefox/WebKit e celular:** só o Chromium tem câmera sintética; checagem manual no emulador Android (`scripts/android/`) e no Safari antes da Etapa 10.
- **Bytes do CDN sem integridade:** versão, lockfile e URLs fixados; os arquivos do CDN não. Como no C# (`docs/security/liveness-supply-chain.md`), até o dono decidir espelhá-los.
- **Log de framework em DEBUG** pode mostrar corpo multipart ou credencial; `logging.level` de `prod` fica no nível padrão (revisar na Etapa 10).

## Dúvidas para o dono
Nenhuma em aberto. **Imagem do smoke (A10), decidida pelo coordenador:** a imagem não é versionada. O smoke a lê de um caminho local dado por variável de ambiente (`DUORA_SMOKE_ROSTO`, JPEG ou PNG, como o `DetectFaces` exige). Assim o repositório público não guarda rosto, e a regra "um formato por tipo" fica sem exceção.
