package bipotheking.net.duora.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.Locale;

/**
 * Motor de navegador das jornadas, escolhido por {@code -Dnavegador=chromium|firefox|webkit}
 * (padrão chromium). Único lugar que decide qual navegador o Playwright abre.
 */
final class Navegador {

  static final String NOME =
      System.getProperty("navegador", "chromium").strip().toLowerCase(Locale.ROOT);

  /**
   * Tecla que avança o foco por todos os elementos focáveis. O WebKit no macOS segue a preferência
   * do Safari ("Tab só percorre campos de formulário") e pula botões e links com Tab simples;
   * Option+Tab é o equivalente do sistema. No Linux (CI) e nos outros motores, Tab basta.
   */
  static final String TAB =
      "webkit".equals(NOME) && System.getProperty("os.name", "").startsWith("Mac")
          ? "Alt+Tab"
          : "Tab";

  private Navegador() {}

  static Browser abrir(Playwright playwright) {
    BrowserType tipo =
        switch (NOME) {
          case "chromium" -> playwright.chromium();
          case "firefox" -> playwright.firefox();
          case "webkit" -> playwright.webkit();
          default ->
              throw new IllegalArgumentException(
                  "navegador desconhecido: '" + NOME + "' (use chromium, firefox ou webkit)");
        };
    return tipo.launch(new BrowserType.LaunchOptions().setHeadless(true));
  }
}
