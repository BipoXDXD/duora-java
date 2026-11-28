package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import bipotheking.net.duora.web.CabecalhosDeSeguranca;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ActiveProfiles;

/**
 * As jornadas da verificação de idade com o servidor de verdade: cada teste cadastra uma conta pelo
 * caminho real (link mágico, cadastro) e chega à /verificacao. A prova de vida e a conferência do
 * documento são as falsas do perfil {@code verificacao-falsa}, guiadas pelos marcadores do e-mail
 * ({@link ContaE2eBase#emailCom}); o detector é o falso, que o servidor liga com {@code
 * modo:"falso"}. Os dois últimos testes provam o isolamento do documento: entrar ou sair da
 * /verificacao recarrega a página, e cada documento chega com a CSP do seu caminho.
 */
@ActiveProfiles("verificacao-falsa")
class VerificacaoIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  private static final String ENVIO_DO_DOCUMENTO = "/api/conta/verificacao/documento";

  /** Só a CSP do documento da verificação compila o WASM do detector. */
  private static final String WASM = "'wasm-unsafe-eval'";

  /** O {@code duora.encarregado.email} padrão, que o texto da análise traz como link. */
  private static final String ENCARREGADO = "encarregado@exemplo.invalid";

  @Test
  void aprovadaPelaSelfieChegaAoTerracoComSelo(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    VerificacaoPagina verificacao = verificacaoDeContaNova(conta, emailNovo());

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Idade confirmada")).isVisible();
    conta.link("Continuar").click();
    assertThat(conta.page()).hasURL(raiz());

    conta.abrir(raiz() + "conta");
    assertThat(
            conta.page().getByText("Conta verificada", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    assertThat(conta.link("Confirmar minha idade")).hasCount(0);
  }

  @Test
  void menorPelaSelfieERecusado(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    VerificacaoPagina verificacao = verificacaoDeContaNova(conta, emailCom("idade15"));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Cadastro não concluído")).isVisible();
    verificacao.textoVisivel("O Duora é só para maiores de 18 anos. Encerramos o cadastro.");

    conta.abrir(raiz() + "conta");
    assertThat(conta.page()).hasURL(raiz() + "entrar");
  }

  @Test
  void duvidaComDocumentoAprova(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    VerificacaoPagina verificacao = verificacaoDeContaNova(conta, emailCom("idade19"));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Falta pouco")).isVisible();
    verificacao
        .escolherDocumento(DocumentoSintetico.jpeg("frente"), DocumentoSintetico.jpeg("verso"))
        .fazerSelfieDoDocumento();
    Response envio = envioDoDocumento(verificacao);

    assertThat(envio.status()).isEqualTo(200);
    assertThat(envio.request().headerValue("content-type")).startsWith("multipart/form-data");
    assertThat(verificacao.heading("Idade confirmada")).isVisible();
  }

  @Test
  void documentoDeOutraPessoaFicaEmAnalise(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    VerificacaoPagina verificacao = verificacaoDeContaNova(conta, emailCom("idade19", "docoutro"));

    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Falta pouco")).isVisible();
    verificacao
        .escolherDocumento(DocumentoSintetico.jpeg("frente"), DocumentoSintetico.jpeg("verso"))
        .fazerSelfieDoDocumento()
        .simularCaptura();

    // O rodapé também tem um mailto (Contato): o link procurado é o do texto da análise.
    Locator analise =
        conta
            .page()
            .getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Conta em análise"));
    assertThat(analise).isVisible();
    assertThat(
            analise.getByRole(
                AriaRole.LINK, new Locator.GetByRoleOptions().setName(ENCARREGADO).setExact(true)))
        .hasAttribute("href", "mailto:" + ENCARREGADO);
  }

  @Test
  void verificacaoAbertaPeloLinkDeMinhaContaRecarregaODocumento(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    verificacaoDeContaNova(conta, emailNovo());
    conta.abrir(raiz() + "conta");
    assertThat(conta.heading("Minha conta")).isVisible();

    Response daVerificacao =
        respostaDeDocumento(
            conta.page(), "verificacao", () -> conta.link("Confirmar minha idade").click());
    assertThat(daVerificacao.headerValue("content-security-policy")).contains(WASM);

    VerificacaoPagina verificacao = VerificacaoPagina.de(conta.page());
    verificacao.consentirEComecar().simularCaptura();
    assertThat(verificacao.heading("Idade confirmada")).isVisible();
    Response daRaiz = respostaDeDocumento(conta.page(), "", () -> conta.link("Continuar").click());
    assertThat(daRaiz.headerValue("content-security-policy")).isEqualTo(CabecalhosDeSeguranca.CSP);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({
    "Termos de uso, termos",
    "Privacidade, privacidade",
    "Denunciar, denunciar",
    "Encarregado de dados, encarregado"
  })
  void saidaPorCadaLinkDoRodapeRecarregaODocumento(
      String link, String caminho, Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    verificacaoDeContaNova(conta, emailNovo());

    Response saida = respostaDeDocumento(conta.page(), caminho, () -> conta.link(link).click());

    String csp = saida.headerValue("content-security-policy");
    assertThat(csp).isEqualTo(CabecalhosDeSeguranca.CSP);
    assertThat(csp).doesNotContain(WASM);
  }

  /** Cadastra pela jornada real; ela termina no documento da /verificacao. */
  private VerificacaoPagina verificacaoDeContaNova(ContaPagina conta, String email) {
    conta.cadastrarContaNova(raiz(), email, NASCIMENTO_DE_ADULTO, this::linkRecebido);
    assertThat(conta.page()).hasURL(raiz() + "verificacao");
    return VerificacaoPagina.de(conta.page());
  }

  /**
   * Toca em "Simular captura" na selfie do documento e devolve a resposta do envio, que a página
   * faz logo depois da selfie.
   */
  private static Response envioDoDocumento(VerificacaoPagina verificacao) {
    return verificacao
        .page()
        .waitForResponse(
            resposta ->
                resposta.url().endsWith(ENVIO_DO_DOCUMENTO)
                    && "POST".equals(resposta.request().method()),
            verificacao::simularCaptura);
  }

  /** A resposta da navegação de documento inteiro para o caminho, disparada pela ação. */
  private Response respostaDeDocumento(Page page, String caminho, Runnable acao) {
    String url = raiz() + caminho;
    return page.waitForResponse(
        resposta ->
            resposta.request().isNavigationRequest()
                && "document".equals(resposta.request().resourceType())
                && url.equals(resposta.url()),
        acao);
  }

  private static ContaPagina novaConta(Participantes participantes) {
    TerracoPagina pagina = participantes.novo();
    TurnstileFalso.instalar(pagina.page());
    return ContaPagina.de(pagina);
  }
}
