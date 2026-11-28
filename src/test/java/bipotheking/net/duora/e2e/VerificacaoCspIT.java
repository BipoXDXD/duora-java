package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A /verificacao sob a CSP e a Permissions-Policy que o {@code CabecalhosDeSeguranca} serve de
 * verdade: o documento vem do servidor do teste, e só a API da verificação é respondida pelo
 * Playwright ({@link ApiDeVerificacaoSimulada}). O detector oficial do Amplify roda no Chromium com
 * câmera sintética e o WebSocket do streaming interceptado, aberto e mudo; o CDN da Rekognition
 * fica abortado. Nenhum byte vai à AWS: a prova é que o detector monta (script, estilo, WASM e
 * trusted types), abre a câmera e chega ao streaming sem violação. O socket roteado abre dentro da
 * página, sem passar pelo {@code connect-src}; a literal dessa diretiva fica no {@code
 * CspDaVerificacaoTest}. O WASM e o modelo só baixam depois da SessionInformation, que o socket
 * mudo nunca manda.
 */
class VerificacaoCspIT extends ContaE2eBase {

  private static final String CONSENTIMENTO =
      "Concordo com o uso da minha imagem para confirmar minha idade";

  private static final String SITUACAO_AWS =
      """
      {"estado":"AGUARDANDO","tentativasRestantesHoje":3,"enviosDeDocumentoRestantesHoje":2,\
      "modo":"aws"}\
      """;

  /** Sessão sintética: a credencial nunca chega a uma conta da AWS. */
  private static final String SESSAO_SINTETICA =
      """
      {"sessaoId":"0199f1a2-3b4c-7d5e-8f60-718293a4b5c6","regiao":"us-east-1","modo":"aws",\
      "credencial":{"accessKeyId":"synthetic","secretAccessKey":"synthetic",\
      "sessionToken":"synthetic","expiraEm":"2099-01-01T00:00:00Z"}}\
      """;

  private static final String HOST_DO_STREAMING = "streaming-rekognition.us-east-1.amazonaws.com";

  private static final Pattern STREAMING =
      Pattern.compile("^wss://streaming-rekognition\\.us-east-1\\.amazonaws\\.com/");

  private static final String CDN_DA_REKOGNITION =
      "https://cdn.liveness.rekognition.amazonaws.com/**";

  private static final Pattern ERRO_DE_POLITICA =
      Pattern.compile(
          "Content Security Policy|TrustedHTML|TrustedScript", Pattern.CASE_INSENSITIVE);

  /** Cada violação vira uma linha "diretiva endereço" no dataset, que o teste lê no fim. */
  private static final String GRAVAR_VIOLACOES =
      """
      (() => {
        const violacoes = [];
        document.addEventListener("securitypolicyviolation", (evento) => {
          violacoes.push(evento.effectiveDirective + " " + evento.blockedURI);
          document.documentElement.dataset.violacoesDeCsp = violacoes.join("\\n");
        });
      })();
      """;

  private static final double ESPERA_DO_STREAMING_MS = 15_000;

  /** Câmera sintética do Chromium, já autorizada, sem a pergunta ao usuário. */
  private static final List<String> CAMERA_SINTETICA =
      List.of("--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream");

  @Test
  void explicacaoSemViolacaoDeCsp(Participantes participantes) {
    List<String> erros = new CopyOnWriteArrayList<>();
    Page page =
        participantes
            .novo(pagina -> {
              pagina.addInitScript(GRAVAR_VIOLACOES);
              pagina.onPageError(erros::add);
              ApiDeVerificacaoSimulada.instalar(pagina);
            })
            .page();
    VerificacaoPagina.abrir(page, raiz());

    Locator consentimento = consentimento(page);
    consentimento.check();
    assertThat(consentimento).isChecked();

    assertThat(violacoesDeCsp(page)).isNull();
    assertThat(erros).noneMatch(erro -> ERRO_DE_POLITICA.matcher(erro).find());
  }

  /**
   * Sempre no Chromium, qualquer que seja o {@code -Dnavegador}: só ele tem a câmera sintética por
   * linha de comando. Abre o próprio navegador e o fecha no fim.
   */
  @Test
  void detectorOficialAbreCameraEStreamingSobACspReal() {
    Playwright playwright = Playwright.create();
    try {
      Browser chromium =
          playwright
              .chromium()
              .launch(new BrowserType.LaunchOptions().setHeadless(true).setArgs(CAMERA_SINTETICA));
      BrowserContext contexto =
          chromium.newContext(
              new Browser.NewContextOptions()
                  .setViewportSize(1440, 900)
                  .setReducedMotion(ReducedMotion.REDUCE)
                  .setServiceWorkers(ServiceWorkerPolicy.BLOCK));
      contexto.grantPermissions(
          List.of("camera"), new BrowserContext.GrantPermissionsOptions().setOrigin(origem()));
      Page page = contexto.newPage();
      List<String> erros = new CopyOnWriteArrayList<>();
      List<String> hosts = new CopyOnWriteArrayList<>();
      page.onPageError(erros::add);
      page.addInitScript(GRAVAR_VIOLACOES);
      // Sem connectToServer, o socket fica aberto e mudo: nada sai do navegador.
      page.routeWebSocket(STREAMING, socket -> hosts.add(URI.create(socket.url()).getHost()));
      page.route(CDN_DA_REKOGNITION, Route::abort);
      ApiDeVerificacaoSimulada.instalar(page)
          .comSituacao(SITUACAO_AWS)
          .comSessao(ApiDeVerificacaoSimulada.Resposta.json(201, SESSAO_SINTETICA));

      VerificacaoPagina.abrir(page, raiz()).consentirEComecar();
      esperarStreaming(page, hosts, erros);

      assertThat(hosts).containsExactly(HOST_DO_STREAMING);
      // Vídeo vivo da câmera sintética: a Permissions-Policy liberou a câmera para a própria
      // origem.
      Object videoAtivo =
          selfieEmVideo(page)
              .locator("video")
              .evaluate(
                  "video => video.srcObject instanceof MediaStream && video.srcObject.active");
      assertThat(videoAtivo).isEqualTo(true);
      assertThat(violacoesDeCsp(page)).isNull();
      assertThat(erros).noneMatch(erro -> ERRO_DE_POLITICA.matcher(erro).find());
    } finally {
      playwright.close();
    }
  }

  /**
   * No tempo esgotado, a falha mostra as violações, os erros e o texto da página, que dizem o
   * porquê.
   */
  private static void esperarStreaming(Page page, List<String> hosts, List<String> erros) {
    try {
      page.waitForCondition(
          () -> !hosts.isEmpty(),
          new Page.WaitForConditionOptions().setTimeout(ESPERA_DO_STREAMING_MS));
    } catch (TimeoutError tempoEsgotado) {
      throw new AssertionError(
          "o streaming não abriu em 15 s; violações de CSP: "
              + violacoesDeCsp(page)
              + "; erros da página: "
              + erros
              + "; texto da página: "
              + page.locator("body").innerText(),
          tempoEsgotado);
    }
  }

  private String origem() {
    URI raiz = URI.create(raiz());
    return raiz.getScheme() + "://" + raiz.getAuthority();
  }

  private static Locator consentimento(Page page) {
    return page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(CONSENTIMENTO));
  }

  private static Locator selfieEmVideo(Page page) {
    return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Selfie em vídeo"));
  }

  private static @Nullable Object violacoesDeCsp(Page page) {
    return page.evaluate("() => document.documentElement.dataset.violacoesDeCsp");
  }
}
