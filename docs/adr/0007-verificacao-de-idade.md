# 0007 — Verificação de idade com AWS e Didit

- **Estado:** aceita
- **Data:** 2026-11-27
- **Etapa:** 2b

## Contexto

O piloto é 18+ em todo o produto (spec, "18+ em todo o piloto"). Uma conta nova fica em `AGUARDANDO_VERIFICACAO` até alguém confirmar, sem depender da palavra da pessoa, que ela tem 18 anos ou mais. A data declarada no cadastro não basta. Guardar selfie ou documento criaria um passivo de LGPD (dado biométrico é sensível) que o piloto não precisa ter.

As opções avaliadas:

- **(a) Tudo na AWS:** prova de vida e estimativa de idade no Rekognition, documento no Textract `AnalyzeID`.
- **(b) Tudo na Didit:** prova de vida, estimativa e documento num fornecedor só.
- **(c) AWS para prova de vida e estimativa, Didit só na faixa de dúvida.**
- **(d) gov.br:** login com a conta do governo e a data de nascimento dela.
- **(e) Documento para todos:** RG/CNH e selfie em todo cadastro.

## Decisão

**Opção (c).** A selfie com prova de vida resolve a maioria. O documento só entra quando a estimativa não decide.

- **Selfie (AWS, `us-east-1`).** Face Liveness do Rekognition e `DetectFaces` com `AGE_RANGE` sobre a imagem de referência. Os limiares ficam fixos em código (`DecisaoDeIdade`): confiança mínima 90,0, exatamente um rosto e faixa presente, senão `REPETIR`. Com `alta < 18`, `RECUSADA`. Com `baixa ≥ 21`, `APROVADA`. O resto é `DUVIDA`. Nenhuma propriedade leva a conta a `ATIVA` sem aprovação.
- **Recusa:** a conta vira lápide e o e-mail é bloqueado (motivo `MENOR`), com 422 `MENOR_DE_IDADE` e o cookie expirado.
- **D-B, documento primeiro na dúvida.** Frente e verso do RG/CNH e uma selfie nova vão num envio só, dentro dos 3 min da nova sessão de prova de vida (+US$ 0,016: a sessão de documento também roda o `DetectFaces`). A Didit lê o documento (com a prova de vida do documento) e compara o retrato com a selfie nova (limiar de rosto 30). A ordem da decisão é esta: documento não aceito ou sem data → `REPETIR`. Rosto que não confere → `DUVIDA`. Menor de 18 pela data lida → `RECUSADA`. Data lida diferente da declarada → `DUVIDA`. Senão, `APROVADA`. O `In Review` da Didit conta como não aceito.
- **409 `REFAZER_PROVA_DE_VIDA` (D14, revista em 2026-11-28 como D14-bis).** Se a prova da sessão de documento venceu, falhou, ficou abaixo de 90,0, ainda está criada (envio sem selfie) ou não tem referência, o servidor não chama a Didit, não conclui nada e responde 409. O front guarda as fotos do documento e reenvia com uma sessão nova, porque num 4G o upload pode passar dos 3 min. Só a prova `EXPIRADA` descarta a tentativa e devolve o envio do dia: o upload lento não é culpa da pessoa. Nos outros casos, o envio fica gasto, para que uma selfie ruim ou pulada não vire repetição de graça. Depois de uma prova expirada, um reenvio com o mesmo `sessaoId` responde 404 `SESSAO_DE_VERIFICACAO_INVALIDA`, porque a tentativa já não existe; depois dos outros casos, responde o mesmo 409. Isso substitui o "responde igual" do contrato do plano. O front relê as contagens no servidor depois do 409 e do 404, porque não sabe qual dos casos aconteceu.
- **D-C:** Didit em sandbox na 2b. A chave de produção entra na Etapa 10.
- **D-D, Didit sem repetição automática.** 5xx, timeout, rede, 401, 403 e 429 viram 503 `CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL`, e a pessoa reenvia. Leitura de 9 s no documento e de 4 s no rosto, com 1 s de conexão em cada. A AWS tem até 2 tentativas por chamada (chamadas idempotentes ou baratas), com 2 s por tentativa e 4 s por chamada.
- **Limites (24 h corridas, contados no banco):** 3 sessões `SELFIE` e 2 envios `DOCUMENTO` por conta, com 429 `LIMITE_DE_VERIFICACAO` e `Retry-After`. O Bucket4j `verificacao-conta` (20 por 10 min) só segura rajada.
- **Upload:** JPEG/PNG de até 5 MB por imagem no domínio. O Spring responde 413 para um arquivo acima de 6 MB ou um corpo acima de 13 MB. O `file-size-threshold` igual ao `max-request-size` mantém o multipart em memória.

### Dados que não ficam

- AWS: `AuditImagesLimit=0` e sem `OutputConfig` (nada vai para o S3).
- Didit: `save_api_request=false` nas duas chamadas, sem `vendor_data`.
- Selfie, documento e credencial curta vivem só na memória da requisição: nunca vão para banco, disco, log ou cache.
- Fica só a aprovação em `verificacao_idade`: resultado, método (`ESTIMATIVA_FACIAL` ou `DOCUMENTO`) e data. A `DUVIDA` vive só na tentativa, e as tentativas saem em 1 dia (`tentativas-de-verificacao`, `P1D`, pela política de retenção do ADR 0006).

### Transferência internacional

- AWS só em `us-east-1`, a única região que a SCP da conta de workload libera. A região é fixa no código.
- Didit conforme a política de privacidade e o DPA dela. A reconferir na Etapa 10, junto da chave de produção.

### Credenciais

- **Back:** usuário IAM `duora-java-verificacao`, com chaves por variável de ambiente em produção e no Keychain em desenvolvimento. A credencial é explícita (`StaticCredentialsProvider`), nunca de `~/.aws` nem de `AWS_*`. A chave da Didit segue o mesmo caminho.
- **Navegador:** credencial curta de 900 s do papel `duora-java-liveness-captura`, emitida por `AssumeRole` com `RoleSessionName = "duora-" + tentativa` e uma política de sessão que só permite `rekognition:StartFaceLivenessSession`.

### Custos (preços de 2026-11-27)

- **Selfie:** US$ 0,016 (Face Liveness US$ 0,015 + `DetectFaces` US$ 0,001).
- **Faixa de dúvida:** US$ 0,282 no total por pessoa (selfie US$ 0,016 + sessão de documento US$ 0,016 + Didit ID Verification US$ 0,20 + Face Match US$ 0,05).
- **Teto por conta e por dia:** 3 × 0,016 + 2 × 0,266 ≈ US$ 0,58.
- O orçamento `duora-rekognition-5usd` avisa se a AWS passar do previsto.

### Orçamento de tempo

O `resultado` da AWS leva no máximo 8 s (duas chamadas de até 4 s), e o `conferir` da Didit no máximo 15 s (1 + 9 s no documento, 1 + 4 s no rosto). Os dois somados (23 s) cabem na reserva de idempotência de 30 s, e o `OrcamentoDeTempoTest` trava essa conta com os padrões das configurações.

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

- A política de privacidade passa a citar dois fornecedores de biometria (AWS e Didit), a transferência internacional e o prazo de 1 dia das tentativas.
- Fornecedor fora do ar responde 503 sem gastar tentativa nem envio, e a pessoa tenta de novo. Na Didit não há repetição automática, então um soluço vira um reenvio manual.
- Trocar de fornecedor é trocar o adapter: o domínio só conhece as portas `ProvaDeVida`, `CredencialDoNavegador` e `ConferenciaDeDocumento`, e o SDK da AWS fica preso em `conta/infra/` por regra de arquitetura.
- O perfil `prod` derruba o boot com qualquer adapter falso. Os falsos (dev e e2e) respondem por marcadores no e-mail e só valem fora de produção.
- A estimativa erra para os dois lados perto dos 18 anos. A margem (aprova só com `baixa ≥ 21`) empurra esses casos para o documento, que custa mais e pede mais da pessoa.
- Quem não tem RG/CNH à mão, ou gasta os 2 envios do dia, só tenta de novo depois de 24 h.

## Alternativas consideradas

- **(a) Tudo na AWS:** um fornecedor só, mas a AWS documenta o `AnalyzeID` só para documentos dos EUA. Não serve para RG/CNH. Rejeitada.
- **(b) Tudo na Didit:** um fornecedor só, mas com o fluxo inteiro dependendo dela e com o documento, o passo mais caro, mais perto de todo cadastro. Rejeitada.
- **(d) gov.br:** a data viria de fonte oficial. Premissa, pela documentação pública do gov.br em 2026-11 (a conferir antes de reabrir a opção): a integração pede adesão formal, e nem toda pessoa tem conta com nível de confiança suficiente. Rejeitada para o piloto.
- **(e) Documento para todos:** mais preciso, mas custa cerca de 17 vezes mais por pessoa, pede mais dado sensível de todo mundo e afasta quem só queria entrar. Rejeitada.
