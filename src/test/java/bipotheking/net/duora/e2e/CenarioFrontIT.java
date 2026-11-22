package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * O front espera o cenário antes de montar: se o {@code cenario.json} não chega, a pessoa vê a
 * mensagem na tela, nunca uma tela branca nem o app quebrado no meio.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class CenarioFrontIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void cenarioInexistenteMostraErroNaTela(Participantes participantes) {
    TerracoPagina ana =
        participantes.novo(page ->
            page.route(
                "**/assets/cenarios/terraco/cenario.json",
                r -> r.fulfill(new Route.FulfillOptions().setStatus(404))));
    Page page = ana.page();

    ana.abrir(raiz(porta));

    assertThat(page.getByRole(AriaRole.ALERT))
        .hasText("Não foi possível carregar o cenário. Recarregue a página.");
    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Antes da primeira estrela")))
        .isVisible();
  }
}
