package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Page Object da verificação de idade: fala com a página só por papéis, rótulos e textos. */
final class VerificacaoPagina {

  private static final String CONSENTIMENTO =
      "Concordo com o uso da minha imagem para confirmar minha idade";

  private final Page page;

  private VerificacaoPagina(Page page) {
    this.page = page;
  }

  /** Abre a /verificacao num documento novo, como a casca sempre faz. */
  static VerificacaoPagina abrir(Page page, String raiz) {
    page.navigate(raiz + "verificacao");
    return new VerificacaoPagina(page);
  }

  Page page() {
    return page;
  }

  Locator heading(String nome) {
    return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(nome));
  }

  Locator botao(String nome) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }

  /** Confere que o texto exato está visível; o Playwright espera por ele. */
  VerificacaoPagina textoVisivel(String texto) {
    assertThat(page.getByText(texto, new Page.GetByTextOptions().setExact(true))).isVisible();
    return this;
  }

  /** Marca o consentimento e toca em "Começar a selfie", já disponível. */
  VerificacaoPagina consentirEComecar() {
    page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(CONSENTIMENTO)).check();
    Locator comecar = botao("Começar a selfie");
    assertThat(comecar).not().hasAttribute("aria-disabled", "true");
    comecar.click();
    return this;
  }
}
