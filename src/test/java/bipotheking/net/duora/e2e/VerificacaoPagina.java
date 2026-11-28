package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;

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

  /** A página já está no documento da /verificacao (a jornada de cadastro termina nele). */
  static VerificacaoPagina de(Page page) {
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

  /**
   * Escolhe as fotos da frente e do verso pelas entradas de arquivo e espera as duas reduzidas (o
   * botão do campo vira "Trocar foto").
   */
  VerificacaoPagina escolherDocumento(byte[] frente, byte[] verso) {
    // O Playwright casa cada elemento do aria-labelledby à parte: o lado acha a entrada certa.
    page.getByLabel("Frente do documento")
        .setInputFiles(new FilePayload("frente.jpg", "image/jpeg", frente));
    page.getByLabel("Verso do documento")
        .setInputFiles(new FilePayload("verso.jpg", "image/jpeg", verso));
    assertThat(page.getByText("Trocar foto", new Page.GetByTextOptions().setExact(true)))
        .hasCount(2);
    return this;
  }

  /** A entrada de arquivo do lado ("Frente do documento" ou "Verso do documento"). */
  Locator campoDaFoto(String lado) {
    return page.getByLabel(lado);
  }

  /** Escolhe um arquivo qualquer para o lado, como JPEG, sem esperar a redução terminar. */
  VerificacaoPagina escolherFoto(String lado, byte[] bytes) {
    campoDaFoto(lado).setInputFiles(new FilePayload("foto.jpg", "image/jpeg", bytes));
    return this;
  }

  /** Toca em "Fazer a selfie", já disponível com as duas fotos: abre a selfie do documento. */
  VerificacaoPagina fazerSelfieDoDocumento() {
    Locator fazer = botao("Fazer a selfie");
    assertThat(fazer).not().hasAttribute("aria-disabled", "true");
    fazer.click();
    return this;
  }

  /** Termina a selfie do detector falso (modo de teste): a página pede o resultado ao servidor. */
  VerificacaoPagina simularCaptura() {
    botao("Simular captura").click();
    return this;
  }
}
