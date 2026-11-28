package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Varredura do axe-core ({@link Axe}) em cada estado novo das telas de conta, das telas públicas e
 * das telas de perfil, um teste por estado. Os participantes usam movimento reduzido, como o {@link
 * Axe} exige; nas telas que pedem a verificação de segurança, o Turnstile é o {@link
 * TurnstileFalso}. Os outros estados do perfil ficam em {@link AxeMeuPerfilIT}, {@link
 * AxePreferenciasIT} e {@link AxePerfilDeOutraPessoaIT}.
 */
class AxeContaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  /** Data relativa: uma data fixa deixaria de ser de menor com o passar dos anos. */
  private static final LocalDate NASCIMENTO_DE_MENOR =
      LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusYears(10);

  /** 5 minutos: a tela arredonda o {@code Retry-After} para minutos inteiros. */
  private static final String SEGUNDOS_PARA_TENTAR_DE_NOVO = "300";

  /** O corpo do {@code TratamentoDeErros} para um {@code LimiteExcedido}. */
  private static final String CORPO_DO_LIMITE =
      """
      {"type":"about:blank","title":"Limite de taxa","status":429,\
      "detail":"Muitas tentativas. Tente de novo em alguns minutos.",\
      "instance":"/api/conta/entrar","codigo":"LIMITE_DE_TAXA",\
      "mensagem":"Muitas tentativas. Tente de novo em alguns minutos."}\
      """;

  private static final String SITUACAO_SEM_TENTATIVAS =
      """
      {"estado":"AGUARDANDO","tentativasRestantesHoje":0,"enviosDeDocumentoRestantesHoje":2,\
      "modo":"falso"}\
      """;

  private static final String SITUACAO_EM_ANALISE =
      """
      {"estado":"DUVIDA","tentativasRestantesHoje":0,"enviosDeDocumentoRestantesHoje":0,\
      "modo":"falso"}\
      """;

  private static final String RESULTADO_REPETIR =
      """
      {"resultado":"REPETIR","pedeDocumento":false,"tentativasRestantesHoje":1,\
      "enviosDeDocumentoRestantesHoje":2}\
      """;

  private static final String RESULTADO_REPETIR_SEM_TENTATIVAS =
      """
      {"resultado":"REPETIR","pedeDocumento":false,"tentativasRestantesHoje":0,\
      "enviosDeDocumentoRestantesHoje":2}\
      """;

  /** A faixa de dúvida com envios de documento hoje: a página abre no envio das fotos. */
  private static final String SITUACAO_DO_DOCUMENTO =
      """
      {"estado":"DUVIDA","tentativasRestantesHoje":0,"enviosDeDocumentoRestantesHoje":2,\
      "modo":"falso"}\
      """;

  private static final String DOCUMENTO_NAO_LIDO =
      """
      {"resultado":"REPETIR","pedeDocumento":false,"tentativasRestantesHoje":0,\
      "enviosDeDocumentoRestantesHoje":1}\
      """;

  /** O pedaço do detector do modo falso, que a página só baixa quando a captura começa. */
  private static final String PEDACO_DO_DETECTOR_FALSO = "**/app/DetectorFalso-*.js";

  /** O {@code GET /api/perfil} de quem ainda não salvou o perfil. */
  private static final String PERFIL_NAO_SALVO =
      """
      {"versao":null,"apelido":null,"pronomes":null,"descricaoPronomes":null,"intencoes":[],\
      "bio":"","pausada":false,"prontoParaFila":false,"pendencias":["PERFIL"],\
      "foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  /** Perfil salvo só com Amizade: o namoro fica desligado. */
  private static final String PERFIL_SEM_NAMORO =
      """
      {"versao":2,"apelido":"Sol","pronomes":null,"descricaoPronomes":null,\
      "intencoes":["AMIZADE"],"bio":"","pausada":false,"prontoParaFila":true,"pendencias":[],\
      "foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  private static final String PERFIL_COM_NAMORO =
      """
      {"versao":3,"apelido":"Lua","pronomes":null,"descricaoPronomes":null,\
      "intencoes":["AMIZADE","NAMORO"],"bio":"","pausada":false,"prontoParaFila":true,\
      "pendencias":[],"foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  /** O {@code GET /api/perfil/namoro} sem dados gravados. */
  private static final String NAMORO_SEM_DADOS = "{\"genero\":null}";

  private static final String NAMORO_GRAVADO =
      """
      {"genero":"MULHER","descricaoGenero":null,"interesses":["HOMEM","PESSOA_NAO_BINARIA"],\
      "consentidoEm":"2026-11-20T12:00:00Z"}\
      """;

  /** Com Prefiro descrever: a tela mostra a descrição, a caixa de mostrar e o apagar. */
  private static final String ORIENTACAO_GRAVADA =
      """
      {"orientacao":"OUTRA","descricaoOrientacao":"Fluida","mostrarOrientacao":true,\
      "consentidaEm":"2026-11-20T12:00:00Z"}\
      """;

  /** PNG de 1×1: a foto escolhida abre na prévia. */
  private static final byte[] FOTO =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

  /** Base64url com o tamanho de um token de 32 bytes, que nunca foi emitido. */
  private static final String TOKEN_INEXISTENTE = "A".repeat(43);

  @Autowired private ContasDeTeste contas;

  @Test
  void entrar(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes).abrir(raiz() + "entrar");

    assertThat(conta.heading("Entrar no Duora")).isVisible();
    assertThat(botao(conta.page(), "Enviar link")).not().hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "entrar");
  }

  /**
   * O multiplicador 1000 dos limites no failsafe não deixa uma rajada chegar ao 429; a rota do
   * Playwright devolve a recusa como o servidor a escreveria.
   */
  @Test
  void entrarComLimite(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes);
    conta
        .page()
        .route(
            "**/api/conta/entrar",
            rota ->
                rota.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(429)
                        .setContentType("application/problem+json")
                        .setHeaders(Map.of("Retry-After", SEGUNDOS_PARA_TENTAR_DE_NOVO))
                        .setBody(CORPO_DO_LIMITE)));

    conta.abrir(raiz() + "entrar").pedirLink(emailNovo());

    assertThat(conta.page().getByText("Muitas tentativas. Tente de novo em 5 minutos."))
        .isVisible();
    assertSemViolacoes(conta.page(), "entrar com limite");
  }

  @Test
  void confirmarEntrada(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    conta.abrirLink(raiz() + "entrar/confirmar#t=" + TOKEN_INEXISTENTE);

    assertThat(conta.heading("Confirmar entrada")).isVisible();

    assertSemViolacoes(conta.page(), "confirmar entrada");
  }

  @Test
  void confirmarComLinkInvalido(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    conta.abrirLink(raiz() + "entrar/confirmar#t=" + TOKEN_INEXISTENTE);

    conta.confirmarEntrada();

    assertThat(conta.page().getByText("Este link expirou ou já foi usado. Peça um novo."))
        .isVisible();
    assertThat(conta.link("Pedir novo link")).isVisible();
    assertSemViolacoes(conta.page(), "confirmar com link inválido");
  }

  @Test
  void cadastro(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.PENDENTE, null).abrir(raiz() + "cadastro");

    assertThat(conta.heading("Falta pouco")).isVisible();
    assertThat(conta.page().getByLabel("Data de nascimento")).isVisible();
    assertThat(conta.page().getByRole(AriaRole.CHECKBOX)).isVisible();

    assertSemViolacoes(conta.page(), "cadastro");
  }

  @Test
  void cadastroComMenorRecusado(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.PENDENTE, null).abrir(raiz() + "cadastro");

    conta.concluirCadastro(NASCIMENTO_DE_MENOR);

    assertThat(conta.heading("Cadastro não concluído")).isVisible();
    assertThat(conta.page().getByText("O Duora é só para maiores de 18 anos. Não criamos a conta."))
        .isVisible();
    assertSemViolacoes(conta.page(), "cadastro com menor recusado");
  }

  @Test
  void verificacao(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO_DE_ADULTO)
            .abrir(raiz() + "verificacao");

    assertThat(conta.heading("Confirme sua idade")).isVisible();

    assertSemViolacoes(conta.page(), "verificação");
  }

  /**
   * A explicação, com a API da verificação simulada: o aviso de luz traz o e-mail do encarregado.
   */
  @Test
  void verificacaoExplicacao(Participantes participantes) {
    VerificacaoPagina verificacao = verificacaoSimulada(participantes, api -> {});

    assertThat(verificacao.heading("Confirme sua idade")).isVisible();
    assertThat(
            verificacao
                .page()
                .getByRole(
                    AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("encarregado@exemplo.invalid")))
        .hasAttribute("href", "mailto:encarregado@exemplo.invalid");
    verificacao.textoVisivel("Tentativas restantes hoje: 3");

    assertSemViolacoes(verificacao.page(), "verificação: explicação");
  }

  @Test
  void verificacaoLimite(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(participantes, api -> api.comSituacao(SITUACAO_SEM_TENTATIVAS));

    verificacao.textoVisivel("Você chegou ao limite de tentativas de hoje. Tente de novo amanhã.");

    assertSemViolacoes(verificacao.page(), "verificação: limite");
  }

  @Test
  void verificacaoServicoFora(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comSessao(
                    ApiDeVerificacaoSimulada.Resposta.problema(
                        503,
                        "PROVA_DE_VIDA_INDISPONIVEL",
                        "A verificação está fora do ar. Tente de novo em alguns minutos.")));

    verificacao
        .consentirEComecar()
        .textoVisivel("O serviço de verificação não respondeu. Tente de novo em instantes.");
    assertThat(verificacao.botao("Tentar de novo")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: serviço fora");
  }

  @Test
  void verificacaoCapturaSimulada(Participantes participantes) {
    VerificacaoPagina verificacao = verificacaoSimulada(participantes, api -> {});

    verificacao.consentirEComecar().textoVisivel("Modo de teste: nenhuma câmera é usada.");
    assertThat(verificacao.botao("Simular captura")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: captura simulada");
  }

  @Test
  void verificacaoAprovada(Participantes participantes) {
    VerificacaoPagina verificacao = verificacaoSimulada(participantes, api -> {});

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Idade confirmada")).isFocused();
    assertThat(
            verificacao
                .page()
                .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Montar meu perfil")))
        .hasAttribute("href", "/perfil");

    assertSemViolacoes(verificacao.page(), "verificação: aprovada");
  }

  @Test
  void verificacaoRepetir(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comResultado(ApiDeVerificacaoSimulada.Resposta.json(200, RESULTADO_REPETIR)));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Não deu para confirmar")).isVisible();
    verificacao.textoVisivel("Tentativas restantes hoje: 1");
    assertThat(verificacao.botao("Tentar de novo")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: repetir");
  }

  @Test
  void verificacaoRepetirSemTentativas(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comResultado(
                    ApiDeVerificacaoSimulada.Resposta.json(200, RESULTADO_REPETIR_SEM_TENTATIVAS)));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Não deu para confirmar")).isVisible();
    verificacao.textoVisivel("Você chegou ao limite de tentativas de hoje. Tente de novo amanhã.");

    assertSemViolacoes(verificacao.page(), "verificação: repetir sem tentativas");
  }

  @Test
  void verificacaoFalhaDoDetector(Participantes participantes) {
    VerificacaoPagina verificacao = verificacaoSimulada(participantes, api -> {});
    // Sem o pedaço do detector, a captura falha por carregamento, como numa conexão que caiu.
    verificacao.page().route(PEDACO_DO_DETECTOR_FALSO, Route::abort);

    verificacao
        .consentirEComecar()
        .textoVisivel("Não conseguimos carregar a selfie. Confira a conexão e tente de novo.");
    assertThat(verificacao.botao("Tentar de novo")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: falha do detector");
  }

  @Test
  void verificacaoSessaoInvalida(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comResultado(
                    ApiDeVerificacaoSimulada.Resposta.problema(
                        404,
                        "SESSAO_DE_VERIFICACAO_INVALIDA",
                        "A sessão de verificação não vale mais.")));

    verificacao.consentirEComecar().simularCaptura();
    verificacao.textoVisivel("Esta selfie não vale mais. Comece de novo.");
    assertThat(verificacao.botao("Tentar de novo")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: sessão inválida");
  }

  @Test
  void verificacaoMenor(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comResultado(
                    ApiDeVerificacaoSimulada.Resposta.problema(
                        422, "MENOR_DE_IDADE", "O Duora é só para maiores de 18 anos.")));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Cadastro não concluído")).isVisible();
    verificacao.textoVisivel("O Duora é só para maiores de 18 anos. Encerramos o cadastro.");

    assertSemViolacoes(verificacao.page(), "verificação: menor");
  }

  @Test
  void verificacaoDocumento(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(participantes, api -> api.comSituacao(SITUACAO_DO_DOCUMENTO));

    assertThat(verificacao.heading("Falta pouco")).isVisible();
    verificacao.textoVisivel("Escolha as fotos da frente e do verso.");
    verificacao.escolherDocumento(
        DocumentoSintetico.jpeg("frente"), DocumentoSintetico.jpeg("verso"));
    verificacao.textoVisivel("Logo depois da selfie, enviamos tudo de uma vez.");

    assertSemViolacoes(verificacao.page(), "verificação: documento");
  }

  @Test
  void verificacaoFotoIlegivel(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(participantes, api -> api.comSituacao(SITUACAO_DO_DOCUMENTO));

    // Bytes que nenhum navegador decodifica, com o tipo de um JPEG: a redução recusa a foto.
    verificacao
        .escolherFoto("Frente do documento", "isto não é uma imagem".getBytes(UTF_8))
        .textoVisivel("Não conseguimos abrir esta foto. Tire outra.");
    assertThat(verificacao.campoDaFoto("Frente do documento")).hasAttribute("aria-invalid", "true");

    assertSemViolacoes(verificacao.page(), "verificação: foto ilegível");
  }

  @Test
  void verificacaoDocumentoNaoLido(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comSituacao(SITUACAO_DO_DOCUMENTO)
                    .comDocumento(ApiDeVerificacaoSimulada.Resposta.json(200, DOCUMENTO_NAO_LIDO)));

    verificacao
        .escolherDocumento(DocumentoSintetico.jpeg("frente"), DocumentoSintetico.jpeg("verso"))
        .fazerSelfieDoDocumento()
        .simularCaptura()
        .textoVisivel(
            "Não conseguimos ler o documento. Use boa luz, sem reflexo, com as quatro pontas"
                + " aparecendo.")
        .textoVisivel("Envios restantes hoje: 1");
    assertThat(verificacao.heading("Falta pouco")).isFocused();

    assertSemViolacoes(verificacao.page(), "verificação: documento não lido");
  }

  @Test
  void verificacaoSelfieVenceu(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(
            participantes,
            api ->
                api.comSituacao(SITUACAO_DO_DOCUMENTO)
                    .comDocumento(
                        ApiDeVerificacaoSimulada.Resposta.problema(
                            409,
                            "REFAZER_PROVA_DE_VIDA",
                            "A selfie venceu antes do envio. Faça uma nova.")));

    verificacao
        .escolherDocumento(DocumentoSintetico.jpeg("frente"), DocumentoSintetico.jpeg("verso"))
        .fazerSelfieDoDocumento()
        .simularCaptura()
        .textoVisivel("A selfie venceu antes do envio. Faça uma nova; as fotos continuam aqui.");
    assertThat(verificacao.botao("Fazer a selfie")).not().hasAttribute("aria-disabled", "true");

    assertSemViolacoes(verificacao.page(), "verificação: selfie venceu");
  }

  @Test
  void verificacaoEmAnalise(Participantes participantes) {
    VerificacaoPagina verificacao =
        verificacaoSimulada(participantes, api -> api.comSituacao(SITUACAO_EM_ANALISE));

    assertThat(verificacao.heading("Conta em análise")).isVisible();

    assertSemViolacoes(verificacao.page(), "verificação: em análise");
  }

  @Test
  void minhaConta(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);

    assertThat(conta.page().getByText("Falta confirmar sua idade.")).isVisible();
    assertThat(conta.link("Confirmar minha idade")).hasAttribute("href", "/verificacao");
    assertSemViolacoes(conta.page(), "minha conta");
  }

  @Test
  void minhaContaAtivaComLinksDoPerfil(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO).abrir(raiz() + "conta");

    assertThat(conta.heading("Seu perfil")).isVisible();
    assertThat(conta.link("Meu perfil")).hasAttribute("href", "/perfil");
    assertThat(conta.link("Preferências de encontro")).hasAttribute("href", "/perfil/preferencias");
    assertSemViolacoes(conta.page(), "minha conta ativa");
  }

  /**
   * A navegação das telas de perfil. O {@code GET /api/perfil} vem do Playwright (perfil ainda não
   * salvo), para a moldura não depender do back do perfil.
   */
  @Test
  void navegacaoDoPerfil(Participantes participantes) {
    ContaPagina conta = logado(participantes, EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO);
    conta
        .page()
        .route(
            "**/api/perfil",
            rota ->
                rota.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(PERFIL_NAO_SALVO)));

    conta.abrir(raiz() + "perfil");

    Locator navegacao =
        conta
            .page()
            .getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Seu perfil"));
    assertThat(conta.heading("Meu perfil")).isVisible();
    assertThat(
            navegacao.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName("Meu perfil")))
        .hasAttribute("aria-current", "page");
    assertSemViolacoes(conta.page(), "navegação do perfil");
  }

  /** O envio da foto recusado pelo limite: o {@code Retry-After} de 300 s vira "5 minutos". */
  @Test
  void fotoComLimite(Participantes participantes) {
    ContaPagina conta =
        telaDoPerfil(
            participantes, "perfil", "Meu perfil", Map.of("**/api/perfil", PERFIL_SEM_NAMORO));
    Page page = conta.page();
    page.route(
        "**/api/perfil/foto",
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(429)
                    .setContentType("application/problem+json")
                    .setHeaders(Map.of("Retry-After", SEGUNDOS_PARA_TENTAR_DE_NOVO))
                    .setBody(CORPO_DO_LIMITE)));

    page.getByLabel("Escolher foto").setInputFiles(new FilePayload("foto", "image/png", FOTO));
    botao(page, "Enviar foto").click();

    assertThat(page.getByText("Muitas tentativas. Tente de novo em 5 minutos.")).isVisible();
    assertSemViolacoes(page, "meu perfil: foto com limite");
  }

  @Test
  void namoroDesligado(Participantes participantes) {
    ContaPagina conta =
        telaDoPerfil(
            participantes,
            "perfil/namoro",
            "Para namoro",
            Map.of("**/api/perfil", PERFIL_SEM_NAMORO));

    assertThat(
            conta
                .page()
                .getByText("O namoro está desligado. Para usar, marque Namoro em Meu perfil."))
        .isVisible();
    assertThat(conta.link("Ir para Meu perfil")).hasAttribute("href", "/perfil");
    assertSemViolacoes(conta.page(), "namoro: desligado");
  }

  /** Sem dados gravados: o aviso dos dados sensíveis e o campo aberto por Prefiro descrever. */
  @Test
  void namoroComAvisoEPrefiroDescrever(Participantes participantes) {
    ContaPagina conta =
        telaDoPerfil(
            participantes,
            "perfil/namoro",
            "Para namoro",
            Map.of("**/api/perfil", PERFIL_COM_NAMORO, "**/api/perfil/namoro", NAMORO_SEM_DADOS));
    Page page = conta.page();

    page.getByRole(
            AriaRole.RADIO, new Page.GetByRoleOptions().setName("Prefiro descrever").setExact(true))
        .check();

    assertThat(page.getByText("Gênero e interesse são dados sensíveis.")).isVisible();
    assertThat(
            page.getByRole(
                AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Como você se descreve")))
        .isVisible();
    assertThat(botao(page, "Salvar dados de namoro")).hasAttribute("aria-disabled", "true");
    assertSemViolacoes(page, "namoro: aviso e Prefiro descrever");
  }

  @Test
  void dialogoDeApagarNamoro(Participantes participantes) {
    ContaPagina conta =
        telaDoPerfil(
            participantes,
            "perfil/namoro",
            "Para namoro",
            Map.of("**/api/perfil", PERFIL_COM_NAMORO, "**/api/perfil/namoro", NAMORO_GRAVADO));
    Page page = conta.page();

    botao(page, "Apagar dados de namoro").click();

    assertThat(
            page.getByRole(
                AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Apagar dados de namoro?")))
        .isVisible();
    assertSemViolacoes(page, "namoro: diálogo de apagar");
  }

  /** Com a orientação gravada: a descrição, a caixa de mostrar, o consentimento e o apagar. */
  @Test
  void orientacao(Participantes participantes) {
    ContaPagina conta =
        telaDoPerfil(
            participantes,
            "perfil/orientacao",
            "Orientação",
            Map.of(
                "**/api/perfil",
                PERFIL_SEM_NAMORO,
                "**/api/perfil/orientacao",
                ORIENTACAO_GRAVADA));
    Page page = conta.page();

    assertThat(
            page.getByRole(
                AriaRole.TEXTBOX,
                new Page.GetByRoleOptions().setName("Como você descreve sua orientação")))
        .hasValue("Fluida");
    assertThat(
            page.getByRole(
                AriaRole.CHECKBOX,
                new Page.GetByRoleOptions().setName("Mostrar para minhas conexões")))
        .isChecked();
    assertThat(botao(page, "Apagar orientação")).isVisible();
    assertSemViolacoes(page, "orientação");
  }

  @Test
  void dialogoDeExclusaoAberto(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);

    botaoExcluirDaSecao(conta.page()).click();

    assertThat(
            conta
                .page()
                .getByRole(
                    AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Excluir sua conta?")))
        .isVisible();
    assertSemViolacoes(conta.page(), "diálogo de exclusão aberto");
  }

  @Test
  void contaExcluida(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);
    botaoExcluirDaSecao(conta.page()).click();

    conta
        .page()
        .getByRole(AriaRole.DIALOG)
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Excluir conta"))
        .click();

    assertThat(conta.heading("Conta excluída")).isVisible();
    assertSemViolacoes(conta.page(), "conta excluída");
  }

  /** Com o token do Turnstile em mãos: o botão de envio fica disponível. */
  @Test
  void denunciar(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes).abrir(raiz() + "denunciar");

    assertThat(conta.heading("Denunciar um problema")).isVisible();
    assertThat(botao(conta.page(), "Enviar denúncia")).not().hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "denunciar");
  }

  @Test
  void termos(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "termos");

    assertThat(conta.heading("Termos de uso").first()).isVisible();

    assertSemViolacoes(conta.page(), "termos");
  }

  @Test
  void privacidade(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "privacidade");

    assertThat(conta.heading("Política de privacidade").first()).isVisible();

    assertSemViolacoes(conta.page(), "privacidade");
  }

  /** Sem o token do Turnstile: a tela espera a verificação com o envio indisponível. */
  @Test
  void denunciarSemToken(Participantes participantes) {
    ContaPagina conta =
        ContaPagina.de(participantes.novo(Cenarios::turnstileSemToken)).abrir(raiz() + "denunciar");

    assertThat(botao(conta.page(), "Enviar denúncia")).hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "denunciar sem token");
  }

  @Test
  void encarregado(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "encarregado");

    assertThat(conta.heading("Encarregado de dados")).isVisible();
    assertThat(conta.link("encarregado@exemplo.invalid")).isVisible();

    assertSemViolacoes(conta.page(), "encarregado");
  }

  /**
   * O Spring dá 404 a caminho fora das rotas do SPA; a página de não encontrada da casca aparece
   * quando a navegação no cliente sai das rotas dela (o router escuta o {@code popstate}).
   */
  @Test
  void paginaNaoEncontrada(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "encarregado");
    assertThat(conta.heading("Encarregado de dados")).isVisible();

    conta
        .page()
        .evaluate(
            "caminho => { history.pushState({}, '', caminho);"
                + " dispatchEvent(new PopStateEvent('popstate')); }",
            "/rota-inexistente");

    assertThat(conta.heading("Página não encontrada")).isVisible();
    assertSemViolacoes(conta.page(), "página não encontrada");
  }

  /** A /verificacao com a conta e a API da verificação respondidas pelo Playwright. */
  private VerificacaoPagina verificacaoSimulada(
      Participantes participantes, Consumer<ApiDeVerificacaoSimulada> ajuste) {
    Page page = participantes.novo().page();
    ajuste.accept(ApiDeVerificacaoSimulada.instalar(page));
    return VerificacaoPagina.abrir(page, raiz());
  }

  private static ContaPagina semConta(Participantes participantes) {
    return ContaPagina.de(participantes.novo());
  }

  /** Para as telas que pedem a verificação de segurança antes de enviar. */
  private static ContaPagina comTurnstile(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    TurnstileFalso.instalar(conta.page());
    return conta;
  }

  /** Conta no estado pedido com o cookie da sessão no navegador, antes de abrir o site. */
  private ContaPagina logado(
      Participantes participantes, EstadoConta estado, @Nullable LocalDate nascimento) {
    return ContaPagina.de(
        Contas.comConta(participantes.novo(), contas.criar(estado, nascimento), raiz()));
  }

  private ContaPagina minhaContaAberta(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO_DE_ADULTO)
            .abrir(raiz() + "conta");
    assertThat(conta.heading("Minha conta")).isVisible();
    return conta;
  }

  /**
   * Uma tela de perfil da conta ATIVA, com os GETs dados (padrão de URL → corpo JSON) respondidos
   * pelo Playwright: a tela não depende do back do perfil.
   */
  private ContaPagina telaDoPerfil(
      Participantes participantes, String caminho, String titulo, Map<String, String> respostas) {
    ContaPagina conta = logado(participantes, EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO);
    Page page = conta.page();
    respostas.forEach((url, corpo) ->
        page.route(
            url,
            rota ->
                rota.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(corpo))));
    conta.abrir(raiz() + caminho);
    assertThat(conta.heading(titulo)).isVisible();
    return conta;
  }

  /** O botão da seção; com o diálogo aberto há outro com o mesmo nome. */
  private static Locator botaoExcluirDaSecao(Page page) {
    return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Excluir conta"))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Excluir conta"));
  }

  private static Locator botao(Page page, String nome) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }
}
