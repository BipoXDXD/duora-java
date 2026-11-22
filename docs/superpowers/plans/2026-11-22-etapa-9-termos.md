# Etapa 9 — Termos, privacidade e expiração de dados: plano de implementação

> **Para agentes:** sub-skill obrigatória: superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Passos com checkbox (`- [ ]`).

**Objetivo:** termos de uso e política de privacidade curtos, em pt-BR, publicados, versionados e linkados no cadastro; contato por e-mail; um job de expiração em que cada módulo registra o que apaga e quando, com o chat e as imagens do encontro expirando 30 dias depois do fim do encontro.

**Arquitetura:** os textos ficam em Markdown com front matter de versão (`src/main/resources/legal/`). O servidor lê, troca os marcadores, calcula o SHA-256 e renderiza com commonmark (HTML bruto escapado). A versão e o resumo são a fonte de verdade do aceite da Etapa 2. A expiração é um núcleo pequeno no pacote novo `retencao/`: uma interface `PoliticaDeRetencao`, que cada módulo implementa, e um agendador `ExpiracaoDeDados`, que roda todas as políticas uma vez por dia com falhas isoladas.

**Stack:** Java 25, Spring Boot 4.2.0, commonmark-java, `@Scheduled`, React 19 + TypeScript + Tailwind 4, Vitest, Playwright Java.

**Spec:** `docs/superpowers/specs/2026-11-21-duora-social-design.md` (Etapa 9; decisões: LGPD leve; "Escolhas minhas": 18+, localização, foto, imagens, sem imagens sensíveis). **Contratos e princípios de engenharia:** `docs/superpowers/plans/2026-11-22-contratos-entre-etapas.md`. **Modelos:** `../../CSharp/Duora/docs/legal/{terms,privacy}/2026-11-07/pt-BR.md` (somente leitura: adaptar, nunca editar aquele projeto). Conferir pelo Context7 a versão atual do commonmark-java e a API `HtmlRenderer.builder().escapeHtml(...)`.

**Quando roda:** esta etapa é dividida no tempo, de propósito.
- Task 1 roda **durante a Etapa 2**, logo depois da Etapa 2 Task 1 (núcleo de erros, `web/`, `Clock`, ArchUnit). Task 2 roda logo depois da Etapa 2 Task 2 (banco e actuator, que traz o `MeterRegistry`). A Etapa 2 Task 8 (cadastro) consome a Task 1, e a Etapa 2 Task 9 consome a Task 2.
- A Etapa 5 implementa as políticas do chat e das imagens do encontro sobre a interface da Task 2.
- Task 3 roda **depois da Etapa 5** e antes da Etapa 10: confere as políticas reais e fecha a prova.

## Restrições globais (Global Constraints)
- Tudo do arquivo de contratos e das restrições globais da Etapa 2 vale aqui.
- **Pacote novo `retencao/`:** proposto como adição ao contrato (lista de pacotes). Se o contrato não for atualizado antes da Task 2, o orquestrador atualiza o contrato primeiro.
- **Flyway:** V40–V49 reservados para esta etapa; nenhuma migração está prevista (as tabelas expiradas pertencem às etapas donas).
- **Textos:** pt-BR, voz ativa, gênero neutro, frases curtas. Termos com até 600 palavras, privacidade com até 900. Sem juridiquês que o modelo do C# não tenha. Nada de selfie, senha nem tradução (recursos do C# que o Java não tem).
- **Versão:** front matter `versao: AAAA-MM-DD` (a data em que o texto foi escrito). Mudar o texto exige versão nova. O aceite (Etapa 2) grava versão e SHA-256 do texto já com os marcadores trocados.
- **Marcadores:** só `{{contato}}`, trocado por `duora.contato.email` (padrão `support@meetduora.com`, conforme a decisão D1 da Etapa 2). Marcador desconhecido, arquivo ausente, front matter ausente ou versão fora do formato fazem o boot falhar.
- **Renderização:** commonmark com HTML bruto escapado e URLs saneadas; o front injeta o HTML só a partir de `/api/legal/*` (conteúdo do próprio repositório).
- **Expiração:** cron `duora.retencao.cron=0 30 3 * * *`, fuso `America/Sao_Paulo`. Cada política apaga em lotes de até 1000 linhas por transação até acabar, é idempotente e nunca loga dado pessoal (só nome da política e contagem). Cada execução tem `correlationId` próprio no MDC (log JSON da Etapa 2).
- **Métricas:** todo acúmulo expõe tamanho (gauge `duora.retencao.tamanho{politica}`) e o job conta o que apagou (contador `duora.retencao.removidos{politica}`) e as falhas (`duora.retencao.falhas{politica}`).
- **Camadas:** `retencao/` é só raiz (interface + agendador), sem web nem JDBC; as políticas concretas ficam no `infra/` de cada módulo dono. `DocumentosLegais` fica na raiz de `conta/` e o controller em `conta/web/`. O `ArquiteturaTest` da Etapa 2 vale.
- **Prazos que o texto de privacidade promete e o código cumpre** (tabela exata da seção "Por quanto tempo guardamos"):

  | Dado | Prazo | Política |
  |---|---|---|
  | Link de entrada | 15 minutos de validade; apagado 1 dia depois de vencer | `links-de-entrada` (Etapa 2) |
  | Sessão | 30 dias sem uso; apagada ao vencer | `sessoes-expiradas` (Etapa 2) |
  | Mensagens do chat do encontro | 30 dias após o fim do encontro | `chat-do-encontro` (Etapa 5) |
  | Imagens do chat do encontro | 30 dias após o fim do encontro | `imagens-do-encontro` (Etapa 5) |
  | E-mails na fila de envio | 7 dias após o envio (30 dias se o envio falhar) | `outbox-enviada`, `outbox-falhada` (Etapa 2) |
  | Chaves de repetição de pedidos | 1 dia | `chaves-de-idempotencia` (Etapa 2) |
  | Arquivos temporários de imagem | 1 hora | `imagens-temporarias` (Etapa 3) |
  | Conta, perfil e foto | até a exclusão da conta | exclusão (Etapas 2 e 3) |

## Ordem e paralelismo
| Task | Quando | Depende de | Paralela a |
|---|---|---|---|
| T1 Documentos legais | durante a Etapa 2 | Etapa 2 T1 | T2, Etapa 2 T2–T7 |
| T2 Núcleo de retenção | durante a Etapa 2 | Etapa 2 T2 | T1, Etapa 2 T3–T7 |
| T3 Expiração do encontro verificada | depois da Etapa 5 | T1, T2, Etapa 2 T10, Etapa 5 | Etapa 8 |

T1 e T2 não compartilham arquivos com a Etapa 2 (T1 cria `conta/DocumentoLegal*` e `conta/LegalController`; a Etapa 2 não cria arquivos com esses nomes). A rota `/api/legal/**` já está na allowlist da Etapa 2 T6.

## Skills por momento
- Antes da Task 2: `mattpocock-skills:codebase-design` com duas alternativas para `PoliticaDeRetencao`; ela é consumida pelas Etapas 2, 3 e 5, então uma mudança atualiza aqueles planos antes de codar.
- Textos legais: `design-skills:ux-writing` para clareza e tom (frases curtas, voz ativa).
- Execução com `superpowers:test-driven-development` ou `mattpocock-skills:tdd`; revisão com `superpowers:requesting-code-review` e `mattpocock-skills:code-review`.
- Antes de dizer "pronto": `superpowers:verification-before-completion`, depois `duora-etapa`.

## Decisões do dono
- **D1. Quem aparece como controlador dos dados.** A LGPD pede que o controlador seja identificável.
  - (a) **Como no C#:** "o responsável pelo Duora" + e-mail de contato. Não expõe nome pessoal num repositório público; atende o modo "LGPD leve".
  - (b) Nome civil (ou razão social, se houver empresa) + e-mail. Mais formal; põe dado pessoal do dono no repositório público e no site.
  - **Recomendo (a)** no piloto, revendo se o piloto crescer. Não bloqueia: o texto segue (a).
- O fornecedor de hospedagem citado na privacidade (Hostinger, como no C#) é confirmado na Etapa 10. Se mudar, a Etapa 10 publica uma versão nova da política.

## Foco de revisão (Review Focus)
1. **Uma política falha** (banco fora, bug): as outras rodam mesmo assim e a falha aparece no log com o nome da política. Coberto por `falhaDeUmaPoliticaNaoParaAsOutras` (Task 2).
2. **Execução agendada e manual ao mesmo tempo:** nada roda em dobro em paralelo. Coberto por `execucoesSimultaneasSaoSerializadas` (Task 2).
3. **Acúmulo grande** (job parado por dias): tudo sai em lotes, sem transação gigante. Coberto por `acumuloDe2500MensagensSaiInteiro` (Task 3).
4. **Markdown com HTML ou script embutido:** sai escapado na página. Coberto por `htmlBrutoEscapado` (Task 1).
5. **Texto e código divergindo** (alguém muda um prazo só num lado): o build quebra. Coberto por `privacidadePrometeOsPrazosDoCodigo` (Task 3).

---

### Task 1: Termos, privacidade e contato

**Arquivos:**
- Criar: `src/main/resources/legal/termos.md`, `src/main/resources/legal/privacidade.md`
- Criar: `src/main/java/bipotheking/net/duora/conta/{DocumentoLegal,VersaoDeDocumento,DocumentosLegais}.java` e `conta/web/LegalController.java`
- Modificar: `pom.xml` (`org.commonmark:commonmark`), `application.properties` (`duora.contato.email=support@meetduora.com`)
- Criar: `src/test/resources/legal-teste/<cenario>/{termos,privacidade}.md`, um diretório por cenário: `valido`, `sem-front-matter`, `marcador-desconhecido`, `com-html`, `sem-privacidade`
- Criar: `frontend/src/telas/legal/DocumentoLegalPagina.tsx` + teste, `frontend/src/componentes/Rodape.tsx` + teste; Modificar: o roteador (`/termos`, `/privacidade`) e o layout raiz (rodapé em todas as telas)
- Teste: `conta/{DocumentosLegaisTest,LegalApiTest,ConteudoLegalTest}.java`, `e2e/LegalIT.java`; Modificar: `AxeIT`

**Interfaces (produz):**
- `public enum DocumentoLegal { TERMOS, PRIVACIDADE }` (caminhos `termos` e `privacidade`).
- `public record VersaoDeDocumento(String versao, String resumoSha256)` (hex minúsculo de 64 caracteres).
- `public class DocumentosLegais`: `public VersaoDeDocumento atual(DocumentoLegal documento)`; `public String html(DocumentoLegal documento)`. Carrega na construção a partir de `duora.legal.dir` (padrão `classpath:legal/`).
- `GET /api/legal/versoes` → `{"termos":{"versao":"..."},"privacidade":{"versao":"..."},"contato":"..."}`.
- `GET /api/legal/{documento}` → `{"versao":"...","html":"..."}`; documento desconhecido → 404 `DOCUMENTO_INEXISTENTE`. Público, `Cache-Control: no-cache`.
- Rodapé (todas as telas): links `Termos de uso` (`/termos`), `Privacidade` (`/privacidade`) e `Contato` (`mailto:` com o campo `contato` de `/api/legal/versoes`).
- Página: título vindo do `#` do Markdown, linha `Versão de DD/MM/AAAA` abaixo dele.

**Conteúdo obrigatório** (adaptar a estrutura e o tom do modelo do C#):
- `termos.md`: O serviço (encontros a dois em cenários, sem garantia de afinidade) · Idade mínima (18 anos ou mais, data de nascimento declarada no cadastro e sem edição; declarar idade falsa encerra a conta; a denúncia "parece menor de idade" suspende a conta até a revisão) · Conta (entrada por link enviado ao e-mail, sem senha; não use a conta de outra pessoa) · Encontros (30 minutos; qualquer pessoa sai quando quiser; encerrar antes exige acordo dos dois; a decisão de continuar é privada e um "não" nunca é revelado) · Conteúdo proibido (nudez, sexo, violência explícita e conteúdo chocante em qualquer imagem; assédio, ameaça, se passar por outra pessoa, links enganosos ou maliciosos) · Moderação (denúncia, bloqueio bilateral sem aviso ao bloqueado, banimento por imagem sensível confirmada, com sessões encerradas) · Exclusão de conta (botão em Minha conta) · Versão e aceite · Contato (`{{contato}}`).
- `privacidade.md`: Controlador e contato (decisão D1, `{{contato}}`) · O que tratamos e por quê: e-mail (entrada e avisos); data de nascimento (só para confirmar 18+; não aparece para ninguém); aceites com versão; apelido, intenção, bio; foto (reprocessada sem metadados, versão borrada para quem ainda não é conexão, nítida só para conexões, aprovada pela moderação); gênero e quem quer conhecer (dado sensível, só com consentimento, só para o pareamento, nunca mostrado, apagado ao desligar o namoro); localização (só com consentimento, guardada como uma área de cerca de 5 km, ponto exato descartado, outras pessoas veem só faixas); chat e imagens do encontro; voz (não é gravada); denúncias e bloqueios · Por quanto tempo guardamos (a tabela de prazos da seção global, linha por linha, nestes formatos exatos: `| Mensagens do chat do encontro | 30 dias após o fim do encontro |` e `| Imagens do chat do encontro | 30 dias após o fim do encontro |`) · Menores (cadastro recusado; guardamos só um código derivado do e-mail com chave secreta, que não permite recuperar o endereço, para impedir nova tentativa) · Fornecedores (Resend para e-mail, sem rastreamento de abertura e clique; Cloudflare Turnstile contra robôs; hospedagem em servidor contratado da Hostinger; tratamento fora do Brasil quando o fornecedor estiver fora) · Cookies (só o de sessão e o do terraço, essenciais; sem cookies de publicidade nem de análise) · Seus direitos (Excluir conta em Minha conta apaga os dados pessoais na hora; o que precisa ficar para moderação fica sem identificação direta; pedidos de acesso e correção por e-mail) · Alterações (versão nova por data).

- [ ] **Passo 1:** testes.
  - `DocumentosLegaisTest` (constrói `DocumentosLegais` apontando para `classpath:legal-teste/<cenario>/`): `leVersaoEResumo` (versão `2026-01-02`; resumo igual ao literal calculado uma vez com `shasum -a 256` sobre o texto com `{{contato}}` trocado por `teste@exemplo.com`, gravado no próprio teste); `trocaMarcadorDeContato`; `htmlBrutoEscapado` (`<script>alert(1)</script>` no Markdown vira `&lt;script&gt;` no HTML); `linkJavascriptSaneado` (`[x](javascript:alert(1))` não gera `href="javascript:`); `semFrontMatterFalha`, `marcadorDesconhecidoFalha`, `arquivoAusenteFalha` (cenário `sem-privacidade`) (todos com `IllegalStateException` citando o arquivo).
  - `LegalApiTest`: `versoesSemSessao` (200 sem cookie; chaves exatas); `documentoSemSessao`; `documentoDesconhecido404`.
  - `ConteudoLegalTest` (sobre os arquivos reais): termos contém `18 anos`, `nudez`, `violência explícita`, o e-mail de contato e tem até 600 palavras; privacidade contém `cerca de 5 km`, `30 dias após o fim do encontro`, `Resend`, `Cloudflare Turnstile`, `Excluir conta` e tem até 900 palavras; nenhum dos dois contém `selfie`, `senha` nem `{{`.
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** escrever os textos e o código. Front: `DocumentoLegalPagina` busca `/api/legal/{documento}` pelo TanStack Query; `Rodape` em todas as rotas.
- [ ] **Passo 4:** Vitest (`DocumentoLegalPagina` mostra título e `Versão de 22/11/2026` para `versao: 2026-11-22`; `Rodape` tem os três links) e e2e: `LegalIT.rodapeAbreTermosEPrivacidade` (do terraço, clica `Termos de uso`, vê o título `Termos de uso`; idem `Privacidade`); `AxeIT` cobre `/termos` e `/privacidade`. Tudo verde nos três navegadores.
- [ ] **Passo 5:** commit `feat(legal): termos de uso e política de privacidade versionados`.

### Task 2: Núcleo de retenção

**Arquivos:**
- Criar: `src/main/java/bipotheking/net/duora/retencao/{PoliticaDeRetencao,ExpiracaoDeDados,ResultadoDaExpiracao}.java`, `docs/adr/NNNN-retencao-por-politica.md` (próximo número livre no merge: cada módulo registra o que apaga; job diário único; falha isolada; métrica por política)
- Modificar: `application.properties` (`duora.retencao.cron=0 30 3 * * *`)
- Teste: `retencao/ExpiracaoDeDadosTest.java`

**Interfaces (produz; as Etapas 2, 3, 5 e 6 implementam):**

```java
/** O que um módulo apaga e depois de quanto tempo. Um bean por tipo de dado. */
public interface PoliticaDeRetencao {
  /** Nome único em kebab-case, usado em log e nos testes de contrato. */
  String nome();
  /** Quanto tempo o dado vive depois do instante de referência da política. */
  Duration prazo();
  /**
   * Apaga tudo cujo instante de referência é anterior a {@code limite}, em lotes de até 1000
   * linhas por transação, até não sobrar nada; arquivos só depois do commit de cada lote.
   * Idempotente. Devolve quantos itens apagou.
   */
  int expirar(Instant limite);
  /** Quantos itens acumulados existem agora (vira gauge). */
  long tamanho();
}
```

- `public record ResultadoDaExpiracao(String politica, int removidos, boolean falhou)`.
- `public class ExpiracaoDeDados`: recebe `List<PoliticaDeRetencao>` e `Clock`; nomes repetidos → `IllegalStateException` na construção. `public synchronized List<ResultadoDaExpiracao> executar()` roda as políticas em ordem de nome com `limite = clock.instant().minus(prazo())`; exceção numa política vira `falhou = true`, log ERROR com o nome e a próxima segue. `@Scheduled(cron = "${duora.retencao.cron}", zone = "America/Sao_Paulo") void agendada()` chama `executar()`. `public Map<String, Duration> prazos()` (para testes de contrato). Registra no `MeterRegistry`, para cada política, o gauge de `tamanho()` e os contadores de removidos e de falhas.

- [ ] **Passo 1:** `ExpiracaoDeDadosTest` com políticas falsas e relógio fixo em `2026-11-22T06:30:00Z`:
  - `limiteEAgoraMenosOPrazo` (política com `P30D` recebe `2026-10-23T06:30:00Z`);
  - `rodaEmOrdemDeNome`;
  - `falhaDeUmaPoliticaNaoParaAsOutras` (a do meio lança; as outras duas rodam; resultado com `falhou = true` só nela; o log tem o nome dela);
  - `nomesRepetidosFalhamNaConstrucao`;
  - `execucoesSimultaneasSaoSerializadas` (duas threads chamam `executar()`; uma política falsa que detecta entrada concorrente nunca a vê);
  - `prazosExpostos`;
  - `metricasPorPolitica` (`SimpleMeterRegistry`: depois de uma execução, `duora.retencao.removidos{politica=a}` = o que a política falsa devolveu, o gauge reflete `tamanho()` e a falha incrementa `duora.retencao.falhas`);
  - `logSoTemNomeEContagem` (o resultado de uma política com e-mail canário no estado interno não leva o canário ao log).
- [ ] **Passo 2:** rodar; falha.
- [ ] **Passo 3:** implementar.
- [ ] **Passo 4:** testes verdes. Commit `feat(retencao): núcleo de expiração diária por política`.

### Task 3: Expiração do encontro verificada e prova da etapa

**Arquivos:**
- Criar: `src/test/java/bipotheking/net/duora/retencao/{RetencaoContratoTest,ExpiracaoDoEncontroIT,PrivacidadeCoerenteTest}.java`
- Modificar: `e2e/LegalIT.java` (cadastro linka os documentos)

**Interfaces:**
- Consome: `ExpiracaoDeDados.executar()` e `prazos()` (T2); `DocumentosLegais` (T1); `ContaPagina` (Etapa 2 T10); da Etapa 5, as políticas `chat-do-encontro` e `imagens-do-encontro` e os serviços/repositórios que o plano da Etapa 5 declarar nas suas Interfaces para criar um encontro encerrado com mensagens e imagens. Se a Etapa 5 não expuser criação direta, inserir pelo `JdbcClient` nas tabelas que ela criou.
- O que a Etapa 5 precisa entregar (copiar para o plano dela): `chat-do-encontro`, prazo `P30D`, instante de referência = fim do encontro, apaga as mensagens; `imagens-do-encontro`, prazo `P30D`, mesma referência, apaga as linhas e os arquivos pela `ArmazemDeImagens.apagar("encontro", id)` depois do commit. Encontro em andamento nunca expira.

- [ ] **Passo 1:** testes.
  - `RetencaoContratoTest` (`@SpringBootTest`): `prazos()` contém pelo menos estes pares: `links-de-entrada`=`P1D`, `sessoes-expiradas`=`PT0S`, `outbox-enviada`=`P7D`, `outbox-falhada`=`P30D`, `chaves-de-idempotencia`=`P1D`, `imagens-temporarias`=`PT1H`, `chat-do-encontro`=`P30D`, `imagens-do-encontro`=`P30D`.
  - `ExpiracaoDoEncontroIT` (banco real, relógio de teste): `encontroEncerradoHa30DiasESegundoExpira` (3 mensagens e 1 imagem somem; os dois arquivos somem do disco); `encontroEncerradoHa29Dias23hFica`; `encontroEmAndamentoFica`; `acumuloDe2500MensagensSaiInteiro` (`removidos` = 2500 e a tabela fica vazia para aquele encontro); `outraContaIntacta`.
  - `PrivacidadeCoerenteTest`: `privacidadePrometeOsPrazosDoCodigo` (o Markdown da privacidade contém as linhas exatas da tabela de prazos para o chat e as imagens do encontro e para a fila de e-mails, e `prazos()` traz os prazos correspondentes).
  - `LegalIT.cadastroLinkaTermosEPrivacidade`: na tela `Falta pouco`, os links `Termos de uso` e `Política de privacidade` apontam para `/termos` e `/privacidade` e abrem as páginas.
- [ ] **Passo 2:** rodar. Se algum falhar por defeito da Etapa 5 (prazo, referência ou arquivo esquecido), corrigir na política da Etapa 5 com teste antes, mantendo estes testes.
- [ ] **Passo 3:** `./mvnw -q verify` (com `ArquiteturaTest`) e `-Pe2e -Dit.test=LegalIT` verdes nos três navegadores; `superpowers:verification-before-completion` e `duora-etapa`. Commit `test(retencao): expiração do chat e das imagens do encontro`.

**Prova da etapa:** `/termos` e `/privacidade` publicadas e linkadas no cadastro (`LegalIT`), e o job de expiração do chat testado (`ExpiracaoDoEncontroIT`).
