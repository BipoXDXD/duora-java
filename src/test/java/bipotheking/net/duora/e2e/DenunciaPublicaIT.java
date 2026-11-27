package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A denúncia pública de ponta a ponta, sem conta: o formulário de {@code /denunciar} chega ao
 * servidor de verdade (Turnstile do {@link TurnstileFalso}). O caminho pelo rodapé fica com o
 * {@code LegalIT}.
 */
class DenunciaPublicaIT extends ContaE2eBase {

  private static final String COOKIE_DA_SESSAO = "duora_sessao";

  /** 20 caracteres: acima do mínimo de 10 do servidor. */
  private static final String DESCRICAO = "Pediu Pix adiantado.";

  @Test
  void denunciarSemConta(Participantes participantes) {
    TerracoPagina pagina = participantes.novo();
    TurnstileFalso.instalar(pagina.page());
    Page page = pagina.page();
    assertThat(page.context().cookies()).isEmpty();
    ContaPagina.de(pagina).abrir(raiz() + "denunciar");

    page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("Golpe").setExact(true))
        .check();
    page.getByLabel("Conte o que aconteceu", new Page.GetByLabelOptions().setExact(true))
        .fill(DESCRICAO);
    Locator enviar =
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Enviar denúncia").setExact(true));
    assertThat(enviar).not().hasAttribute("aria-disabled", "true");
    enviar.click();

    assertThat(page.getByRole(AriaRole.STATUS))
        .hasText(Pattern.compile("^Recebemos sua denúncia\\."));
    assertThat(page.context().cookies())
        .extracting(cookie -> cookie.name)
        .doesNotContain(COOKIE_DA_SESSAO);
  }
}
