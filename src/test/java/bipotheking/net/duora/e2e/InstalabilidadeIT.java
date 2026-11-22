package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * O próprio Chrome diz se o app pode ser instalado. Sempre usa o Chromium completo, qualquer que
 * seja o {@code -Dnavegador}: o headless shell padrão do Playwright devolve a lista vazia para
 * qualquer página, e um contexto anônimo acusa {@code in-incognito}, por isso o contexto é
 * persistente.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InstalabilidadeIT {

  private static final String SEM_MANIFEST =
      """
      <!doctype html>
      <html lang="pt-BR"><head><meta charset="utf-8"><title>Sem manifest</title></head>
      <body><p>Página sem manifest.</p></body></html>
      """;

  @Value("${local.server.port}")
  private int porta;

  private Playwright playwright;
  private BrowserContext contexto;

  @BeforeEach
  void abrirChromium(@TempDir Path perfil) {
    playwright = Playwright.create();
    contexto =
        playwright
            .chromium()
            .launchPersistentContext(
                perfil,
                new BrowserType.LaunchPersistentContextOptions()
                    .setChannel("chromium")
                    .setHeadless(true));
  }

  @AfterEach
  void fecharChromium() {
    contexto.close();
    playwright.close();
  }

  @Test
  void chromeNaoApontaErroDeInstalacao() {
    Page page = contexto.newPage();
    page.navigate(raiz(porta));

    assertThat(errosDeInstalacao(page)).isEmpty();
  }

  /** Controle: prova que a lista vazia do teste acima vem de uma checagem que de fato acontece. */
  @Test
  void paginaSemManifestTemErro() {
    Page page = contexto.newPage();
    page.route(
        "**/sem-manifest",
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setContentType("text/html; charset=utf-8")
                    .setBody(SEM_MANIFEST)));
    page.navigate(raiz(porta) + "sem-manifest");

    assertThat(errosDeInstalacao(page)).isNotEmpty();
  }

  private JsonArray errosDeInstalacao(Page page) {
    JsonObject resposta = contexto.newCDPSession(page).send("Page.getInstallabilityErrors");
    return resposta.getAsJsonArray("installabilityErrors");
  }
}
