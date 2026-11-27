package bipotheking.net.duora.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;

/**
 * Troca o script do Cloudflare Turnstile por uma API {@code window.turnstile} local: o e2e não
 * depende da rede e o widget entrega na hora o token fictício que o back de teste aceita.
 */
final class TurnstileFalso {

  /** O {@code Turnstile.tsx} pede {@code api.js?render=explicit}; o glob cobre a consulta. */
  private static final String SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js*";

  /**
   * Cada {@code render} e cada {@code reset} entregam o token depois do retorno, como o widget de
   * verdade: o componente só guarda o id do widget quando {@code render} volta.
   */
  private static final String API_FALSA =
      """
      (() => {
        const TOKEN = "XXXX.DUMMY.TOKEN.XXXX";
        const widgets = new Map();
        let ultimo = 0;
        const entregar = (id) =>
          setTimeout(() => widgets.get(id)?.callback(TOKEN), 0);
        window.turnstile = {
          render(_elemento, opcoes) {
            const id = "turnstile-falso-" + ++ultimo;
            widgets.set(id, opcoes);
            entregar(id);
            return id;
          },
          reset(id) {
            entregar(id);
          },
          remove(id) {
            widgets.delete(id);
          },
        };
      })();
      """;

  private TurnstileFalso() {}

  /** Vale para a página toda; instale antes de abrir a tela de entrada. */
  static void instalar(Page page) {
    page.route(
        SCRIPT,
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("text/javascript")
                    .setBody(API_FALSA)));
  }
}
