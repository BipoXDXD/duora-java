package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * O app instalável: o manifest tem o que o Android exige, o service worker assume a página, guarda
 * o shell sem nunca guardar a API e abre o app sem rede.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class PwaIT {

  private static final String TITULO = "Antes da primeira estrela";
  private static final String COR = "#132B40";

  /**
   * Espera o service worker ficar {@code activated}. No Firefox e no WebKit, o {@code ready} pode
   * resolver com o worker ainda em {@code activating}. O {@code evaluate} não tem prazo, então a
   * espera falha sozinha em 10 s se nenhum worker for registrado.
   */
  private static final String ESPERAR_SERVICE_WORKER =
      """
      async () => {
        const ativo = (async () => {
          const { active } = await navigator.serviceWorker.ready;
          if (active.state === "activated") return;
          await new Promise((pronto) =>
            active.addEventListener("statechange", () => {
              if (active.state === "activated") pronto();
            }));
        })();
        const prazo = new Promise((_, falhar) =>
          setTimeout(() => falhar(new Error("nenhum service worker ativo em 10 s")), 10_000));
        await Promise.race([ativo, prazo]);
      }
      """;

  private static final String URLS_EM_CACHE =
      """
      async () => {
        const urls = [];
        for (const nome of await caches.keys()) {
          const cache = await caches.open(nome);
          for (const pedido of await cache.keys()) urls.push(pedido.url);
        }
        return urls;
      }
      """;

  @Value("${local.server.port}")
  private int porta;

  @Test
  void manifestDeclaraOQueOAndroidExigeParaInstalar(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta)).page();
    String href = page.locator("link[rel=manifest]").getAttribute("href");
    assertThat(href).isNotBlank();

    APIResponse resposta =
        page.context().request().get(URI.create(page.url()).resolve(href).toString());
    assertThat(resposta.ok()).isTrue();
    DocumentContext manifest = JsonPath.parse(resposta.text());

    assertThat(manifest.<String>read("$.id")).isEqualTo("/");
    assertThat(manifest.<String>read("$.scope")).isEqualTo("/");
    assertThat(manifest.<String>read("$.start_url")).isEqualTo("/");
    assertThat(manifest.<String>read("$.name")).isEqualTo("Duora");
    assertThat(manifest.<String>read("$.short_name")).isEqualTo("Duora");
    assertThat(manifest.<String>read("$.display")).isEqualTo("standalone");
    assertThat(manifest.<String>read("$.background_color")).isEqualTo(COR);
    assertThat(manifest.<String>read("$.theme_color")).isEqualTo(COR);
    assertThat(manifest.<String>read("$.lang")).isEqualTo("pt-BR");
    List<Map<String, String>> icones = manifest.read("$.icons");
    assertThat(icones)
        .extracting(icone -> icone.get("sizes") + " " + icone.get("purpose"))
        .containsExactlyInAnyOrder(
            "192x192 any", "512x512 any", "192x192 maskable", "512x512 maskable");
  }

  @Test
  void serviceWorkerAssumeAPaginaDepoisDeRecarregar(Participantes participantes) {
    Page page = participantes.novoComServiceWorker().abrir(raiz(porta)).page();
    page.evaluate(ESPERAR_SERVICE_WORKER);

    page.reload();

    assertThat(page.evaluate("() => navigator.serviceWorker.controller !== null")).isEqualTo(true);
  }

  @Test
  void apiNuncaEntraNoCache(Participantes participantes) {
    TerracoPagina ana = participantes.novoComServiceWorker().abrir(raiz(porta));
    ana.page().evaluate(ESPERAR_SERVICE_WORKER);

    entrarEmPar(ana, participantes.novo(), raiz(porta));

    Page page = ana.page();
    // Sem controle, o teste passaria à toa: nada teria passado pelo service worker.
    assertThat(page.evaluate("() => navigator.serviceWorker.controller !== null")).isEqualTo(true);
    @SuppressWarnings("unchecked") // evaluate devolve o array do JavaScript como List.
    List<String> urls = (List<String>) page.evaluate(URLS_EM_CACHE);
    assertThat(urls).isNotEmpty().noneMatch(url -> url.contains("/api/"));
  }

  @Test
  void abertoSemRedeMostraOApp(Participantes participantes) {
    Page page = participantes.novoComServiceWorker().abrir(raiz(porta)).page();
    page.evaluate(ESPERAR_SERVICE_WORKER);
    page.reload();
    assertThat(page.evaluate("() => navigator.serviceWorker.controller !== null")).isEqualTo(true);

    page.context().setOffline(true);
    page.reload();

    assertThat(page).hasTitle(TITULO);
  }
}
