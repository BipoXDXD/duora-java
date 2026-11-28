# 0007 — Verificação de idade na AWS

- **Estado:** aceita
- **Data:** 2026-11-27
- **Etapa:** 2b
- **Revisão:** 2026-11-28, DOC-AWS: a conferência do documento sai da Didit e vai para a AWS (Textract e Rekognition `CompareFaces`). Veja "Revisão DOC-AWS".

## Contexto

O piloto é 18+ em todo o produto (spec, "18+ em todo o piloto"). Uma conta nova fica em `AGUARDANDO_VERIFICACAO` até alguém confirmar, sem depender da palavra da pessoa, que ela tem 18 anos ou mais. A data declarada no cadastro não basta. Guardar selfie ou documento criaria um passivo de LGPD (dado biométrico é sensível) que o piloto não precisa ter.

As opções avaliadas:

- **(a) Tudo na AWS:** prova de vida e estimativa de idade no Rekognition, documento no Textract `AnalyzeID`.
- **(b) Tudo na Didit:** prova de vida, estimativa e documento num fornecedor só.
- **(c) AWS para prova de vida e estimativa, Didit só na faixa de dúvida.**
- **(d) gov.br:** login com a conta do governo e a data de nascimento dela.
- **(e) Documento para todos:** RG/CNH e selfie em todo cadastro.

## Decisão

**Opção (c), revista em 2026-11-28 para tudo na AWS.** A selfie com prova de vida resolve a maioria. O documento só entra quando a estimativa não decide.

- **Selfie (AWS, `us-east-1`).** Face Liveness do Rekognition e `DetectFaces` com `AGE_RANGE` sobre a imagem de referência. Os limiares ficam fixos em código (`DecisaoDeIdade`): confiança mínima 90,0, exatamente um rosto e faixa presente, senão `REPETIR`. Com `alta < 18`, `RECUSADA`. Com `baixa ≥ 21`, `APROVADA`. O resto é `DUVIDA`. Nenhuma propriedade leva a conta a `ATIVA` sem aprovação.
- **Recusa:** a conta vira lápide e o e-mail é bloqueado (motivo `MENOR`), com 422 `MENOR_DE_IDADE` e o cookie expirado.
- **D-B, documento primeiro na dúvida.** Frente e verso do RG/CNH e uma selfie nova vão num envio só, dentro dos 3 min da nova sessão de prova de vida (+US$ 0,016: a sessão de documento também roda o `DetectFaces`). O Textract `AnalyzeDocument` (`QUERIES`) lê a data de nascimento na frente e, se ela não trouxer, no verso. O Rekognition `CompareFaces` compara a frente com a selfie nova (limiar de semelhança 90). A ordem da decisão é esta: documento não aceito ou sem data → `REPETIR`. Rosto que não confere → `DUVIDA`. Menor de 18 pela data lida → `RECUSADA`. Data lida diferente da declarada → `DUVIDA`. Senão, `APROVADA`. "Aceito" quer dizer só que a data foi lida com confiança: a AWS não julga a autenticidade do documento.
- **409 `REFAZER_PROVA_DE_VIDA` e `SELFIE_NAO_DEU_CERTO` (D14, revista em 2026-11-28 como D14-bis).** Se a prova da sessão de documento não serve (venceu, falhou, ficou abaixo de 90,0, ainda está criada, ou seja, envio sem selfie, ou não tem referência), o servidor não chama a conferência do documento e responde 409. O front guarda as fotos do documento e pede uma selfie nova, numa sessão nova, porque num 4G o upload pode passar dos 3 min. Só a prova `EXPIRADA` descarta a tentativa e devolve o envio do dia (409 `REFAZER_PROVA_DE_VIDA`): o upload lento não é culpa da pessoa. Nos outros casos, a tentativa é encerrada como `REPETIR` e o envio fica gasto (409 `SELFIE_NAO_DEU_CERTO`), para que uma selfie ruim ou pulada não vire repetição de graça; a pessoa refaz a captura numa tentativa nova, se ainda tiver envio. Nos dois casos, um reenvio com o mesmo `sessaoId` responde 404 `SESSAO_DE_VERIFICACAO_INVALIDA`, porque a tentativa já não existe ou já foi encerrada: não há laço de 409. Isso substitui o "responde igual" do contrato do plano. O front mostra um aviso para cada código e relê as contagens no servidor depois dos dois 409 e do 404.
- **D-C (revista):** ~~Didit em sandbox na 2b~~. A conferência usa a mesma conta, o mesmo usuário IAM e a mesma região da prova de vida.
- **D-D (revista), falha vira 503.** Acesso negado, limitação, 5xx, timeout, rede e qualquer outro erro da AWS na conferência viram 503 `CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL`, e a pessoa reenvia. Documento que o Textract recusa (`BadDocument`, `UnsupportedDocument`, `DocumentTooLarge`) é foto ruim e vira `REPETIR`. A AWS tem até 2 tentativas por chamada (chamadas idempotentes ou baratas), com 2 s por tentativa e 4 s por chamada.
- **Limites (24 h corridas, contados no banco):** 3 sessões `SELFIE` e 2 envios `DOCUMENTO` por conta, com 429 `LIMITE_DE_VERIFICACAO` e `Retry-After`. O Bucket4j `verificacao-conta` (20 por 10 min) só segura rajada.
- **Upload:** JPEG/PNG de até 5 MB por imagem no domínio. O Spring responde 413 para um arquivo acima de 6 MB ou um corpo acima de 13 MB. O `file-size-threshold` igual ao `max-request-size` mantém o multipart em memória.

### Dados que não ficam

- AWS: `AuditImagesLimit=0` e sem `OutputConfig` (nada vai para o S3).
- Documento: `Bytes` inline no Textract e no `CompareFaces`, nunca S3. Essas APIs síncronas não guardam a imagem.
- Selfie, documento e credencial curta vivem só na memória da requisição: nunca vão para banco, disco, log ou cache.
- Fica só a aprovação em `verificacao_idade`: resultado, método (`ESTIMATIVA_FACIAL` ou `DOCUMENTO`) e data. A `DUVIDA` vive só na tentativa, e as tentativas saem em 1 dia (`tentativas-de-verificacao`, `P1D`, pela política de retenção do ADR 0006).

### Transferência internacional

- AWS só em `us-east-1`, a única região que a SCP da conta de workload libera. A região é fixa no código.

### Credenciais

- **Back:** usuário IAM `duora-java-verificacao`, com chaves por variável de ambiente em produção e no Keychain em desenvolvimento. A credencial é explícita (`StaticCredentialsProvider`), nunca de `~/.aws` nem de `AWS_*`. O mesmo usuário chama `textract:AnalyzeDocument` e `rekognition:CompareFaces`.
- **Navegador:** credencial curta de 900 s do papel `duora-java-liveness-captura`, emitida por `AssumeRole` com `RoleSessionName = "duora-" + tentativa` e uma política de sessão que só permite `rekognition:StartFaceLivenessSession`. A política usa `Resource: "*"` porque o Rekognition não aceita o ARN da sessão nessa ação (ela não tem recurso no nível de ARN); quem limita a credencial é a ação única, os 900 s e o `RoleSessionName` ligado à tentativa.

### Custos (preços de 2026-11-27)

- **Selfie:** US$ 0,016 (Face Liveness US$ 0,015 + `DetectFaces` US$ 0,001).
- **Faixa de dúvida (revista):** de US$ 0,048 a US$ 0,063 no total por pessoa (selfie US$ 0,016 + sessão de documento US$ 0,016 + Textract Queries US$ 0,015 por lado lido, 1 ou 2 + `CompareFaces` US$ 0,001). Antes, com a Didit: US$ 0,282.
- **Teto por conta e por dia (revisto):** 3 × 0,016 + 2 × 0,047 ≈ US$ 0,15. Antes: US$ 0,58.
- O orçamento `duora-rekognition-5usd` avisa se a AWS passar do previsto.

### Orçamento de tempo

O `resultado` da AWS leva no máximo 8 s (duas chamadas de até 4 s), e o `conferir` no máximo 12 s (frente, verso e rosto, três chamadas de até 4 s). Os dois somados (20 s) cabem na reserva de idempotência de 30 s, e o `OrcamentoDeTempoTest` trava essa conta com os padrões das configurações.

### Navegador

- A `/verificacao` tem CSP própria (trusted-types `'none'`, `worker-src 'none'`, `wasm-unsafe-eval` e `connect-src` só com o CDN e o streaming da AWS) e `Permissions-Policy: camera=(self), microphone=()`.
- O detector oficial do Amplify carrega por `import()` só depois do consentimento. A trava no `ChunksDoFrontTest` (sem ciclo entre os chunks do Amplify) e o `VerificacaoCspIT` provam que ele roda sob a CSP real sem violação. O WASM e o modelo vindos do CDN só são provados no smoke real da A12.

### Fotossensibilidade

A prova de vida pisca cores na tela. A tela avisa antes, e quem não pode se expor tem um caminho manual pelo e-mail do encarregado (`GET /api/legal/versoes`).

### Observabilidade

- Timer `duora.verificacao.fornecedor` com as tags `fornecedor`, `operacao` e `desfecho`.
- Logs só com passo, status e ID da conta: nunca corpo de resposta, imagem, chave, ARN ou data de nascimento.
- Sem `HealthIndicator` ativo: sondar o fornecedor custa dinheiro, e o Timer já mostra falha e lentidão.

## Consequências

- A política de privacidade cita um fornecedor de biometria (AWS, nos Estados Unidos), a transferência internacional e o prazo de 1 dia das tentativas.
- Fornecedor fora do ar responde 503 sem gastar tentativa nem envio, e a pessoa tenta de novo.
- Trocar de fornecedor é trocar o adapter: o domínio só conhece as portas `ProvaDeVida`, `CredencialDoNavegador` e `ConferenciaDeDocumento`, e o SDK da AWS fica preso em `conta/infra/` por regra de arquitetura.
- O perfil `prod` derruba o boot com qualquer adapter falso. Os falsos (dev e e2e) respondem por marcadores no e-mail e só valem fora de produção.
- A estimativa erra para os dois lados perto dos 18 anos. A margem (aprova só com `baixa ≥ 21`) empurra esses casos para o documento, que custa mais e pede mais da pessoa.
- Quem não tem RG/CNH à mão, ou gasta os 2 envios do dia, só tenta de novo depois de 24 h.

## Alternativas consideradas

- **(a) Tudo na AWS com `AnalyzeID`:** a AWS documenta o `AnalyzeID` só para documentos dos EUA. Não serve para RG/CNH. Rejeitada; a revisão DOC-AWS usa o `AnalyzeDocument` com `QUERIES`, que lê qualquer documento.
- **(b) Tudo na Didit:** um fornecedor só, mas com o fluxo inteiro dependendo dela e com o documento, o passo mais caro, mais perto de todo cadastro. Rejeitada.
- **(d) gov.br:** a data viria de fonte oficial. Premissa, pela documentação pública do gov.br em 2026-11 (a conferir antes de reabrir a opção): a integração pede adesão formal, e nem toda pessoa tem conta com nível de confiança suficiente. Rejeitada para o piloto.
- **(e) Documento para todos:** mais preciso, mas custa cerca de 17 vezes mais por pessoa, pede mais dado sensível de todo mundo e afasta quem só queria entrar. Rejeitada.

## Revisão DOC-AWS (2026-11-28)

**Decisão do dono:** a conferência do documento sai da Didit e vai para a AWS.

- **Motivo, custo.** O modo live da Didit não cabe na verba do piloto, e o crédito grátis de US$ 10/mês não basta. A conta, o usuário IAM e a região da AWS já existem.
- **Como.** `ConferenciaDeDocumentoAws`, na mesma porta `ConferenciaDeDocumento`. O Textract `AnalyzeDocument` com `QUERIES` ("Qual é a data de nascimento?" e "Date of birth?") lê a frente e, sem data, o verso. Vale a resposta de maior confiança com pelo menos 80 e em `dd/MM/aaaa` (aceita `.` ou `-` no lugar da barra), com data que existe. O `CompareFaces` compara a frente com a selfie nova, com `SimilarityThreshold` 90. Sem rosto na frente (`InvalidParameterException`) ou com imagem recusada, o rosto não confere.
- **Limiar 90.** O padrão da AWS é 80, e ela recomenda 99 quando um acerto errado tem consequência grave sem revisão humana. Aqui, o rosto que não confere vai para a `DUVIDA` (revisão), não para a recusa, e o retrato do documento costuma ser antigo e impresso. Com 90, quem usa o documento de outra pessoa é cortado sem empurrar o dono do documento para a dúvida a cada RG antigo.
- **Perda aceita no piloto.** A Didit detectava adulteração do documento (prova de vida do documento, `perform_document_liveness`). O Textract só lê o texto, e o `CompareFaces` só compara rostos: um documento adulterado com a foto da própria pessoa e uma data falsa passa, desde que a data bata com a declarada. A rede contra isso fica na denúncia e na moderação. Reavaliar antes de abrir o produto além do piloto.
- **Removido.** `ConferenciaDeDocumentoDidit`, `ConfiguracaoDidit`, as propriedades `duora.verificacao.didit.*` e a variável `DUORA_DIDIT_API_KEY`. O perfil `prod` exige só as três variáveis da AWS.
