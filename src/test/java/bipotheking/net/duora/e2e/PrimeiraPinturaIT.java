package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Num 4G lento a página mostra o título e o céu antes de o JS chegar, não uma tela em branco: o
 * esqueleto estático do index.html pinta sozinho, e o React o substitui depois.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class PrimeiraPinturaIT {

  private static final double ATRASO_DO_JS_MS = 3_000;
  private static final double PRIMEIRA_PINTURA_MS = 1_000;

  @Value("${local.server.port}")
  private int porta;

  @Test
  void tituloApareceAntesDoJs(Participantes participantes) {
    // O Playwright Java atende rotas na mesma thread do teste: um sleep no handler travaria a
    // asserção. Os pedidos de JS ficam presos e só seguem depois do atraso.
    List<Route> presos = new ArrayList<>();
    TerracoPagina ana = participantes.novo(page -> page.route("**/app/*.js", presos::add));
    Page page = ana.page();

    page.navigate(raiz(porta), new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT));

    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Antes da primeira estrela")))
        .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(PRIMEIRA_PINTURA_MS));
    page.waitForTimeout(ATRASO_DO_JS_MS - PRIMEIRA_PINTURA_MS);
    presos.forEach(Route::resume);
  }
}
