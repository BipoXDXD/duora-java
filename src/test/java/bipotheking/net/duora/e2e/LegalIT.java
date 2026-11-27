package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * O rodapé da entrada do terraço leva aos documentos legais, que a casca de conta mostra com o
 * texto que o servidor gera do Markdown versionado, e às telas públicas de denúncia e do
 * encarregado de dados.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class LegalIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void rodapeAbreTermosEPrivacidade(Participantes participantes) {
    TerracoPagina ana = participantes.novo().abrir(raiz(porta));
    Page page = ana.page();

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Termos de uso")).click();

    assertThat(page).hasURL(raiz(porta) + "termos");
    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Termos de uso").setLevel(1)))
        .isVisible();
    assertThat(page.getByText(Pattern.compile("^Versão de \\d{2}/\\d{2}/\\d{4}$"))).isVisible();
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(2)).first())
        .isVisible();
    assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Contato")))
        .hasAttribute("href", "mailto:support@meetduora.com");
    assertSemViolacoes(page, "termos");

    ana.abrir(raiz(porta));
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Privacidade")).click();

    assertThat(page).hasURL(raiz(porta) + "privacidade");
    assertThat(
            page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Política de privacidade").setLevel(1)))
        .isVisible();
    assertSemViolacoes(page, "privacidade");
  }

  @Test
  void rodapeAbreDenunciarEEncarregado(Participantes participantes) {
    TerracoPagina ana = participantes.novo(Cenarios::turnstileSemToken).abrir(raiz(porta));
    Page page = ana.page();

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Denunciar")).click();

    assertThat(page).hasURL(raiz(porta) + "denunciar");
    assertThat(
            page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Denunciar um problema").setLevel(1)))
        .isVisible();

    ana.abrir(raiz(porta));
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Encarregado de dados"))
        .click();

    assertThat(page).hasURL(raiz(porta) + "encarregado");
    assertThat(
            page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Encarregado de dados").setLevel(1)))
        .isVisible();
    assertThat(
            page.getByRole(
                AriaRole.LINK, new Page.GetByRoleOptions().setName("encarregado@exemplo.invalid")))
        .hasAttribute("href", "mailto:encarregado@exemplo.invalid");
  }
}
