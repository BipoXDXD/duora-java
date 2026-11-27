package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;

/** Passos comuns das jornadas: Ana cria o terraço, Bruno entra e os dois o compõem. */
final class Cenarios {

  static final String ANA = "Ana";
  static final String BRUNO = "Bruno";

  /** O script do Turnstile sem rede: um widget que não desenha nada nem entrega token. */
  private static final String TURNSTILE_SEM_TOKEN =
      "window.turnstile = { render: () => 'widget-falso', reset() {}, remove() {} };";

  private Cenarios() {}

  /**
   * Troca o script do Turnstile (challenges.cloudflare.com) por um widget falso que nunca entrega
   * token: a tela fica no estado de espera da verificação, sem depender da rede da Cloudflare.
   */
  static void turnstileSemToken(Page page) {
    page.route(
        "https://challenges.cloudflare.com/**",
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setContentType("text/javascript")
                    .setBody(TURNSTILE_SEM_TOKEN)));
  }

  static String raiz(int porta) {
    return "http://127.0.0.1:" + porta + "/";
  }

  /** Ana cria o terraço e Bruno entra pelo convite; espera as peças aparecerem para Ana. */
  static void entrarEmPar(TerracoPagina ana, TerracoPagina bruno, String raiz) {
    ana.abrir(raiz).criarTerraco(ANA);
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
  }

  /**
   * Composição completa, uma peça por vez: telescópio de Ana em C1, banco de Bruno em C2, manta de
   * Ana em L2 e lanterna de Bruno em R1. Cada peça aparece para o outro antes da seguinte.
   */
  static void comporTerraco(TerracoPagina ana, TerracoPagina bruno) {
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();
    bruno.colocar(LANTERNA, R1, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
  }
}
