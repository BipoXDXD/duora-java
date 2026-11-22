package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Densidade das imagens: o terraço e os sprites vêm na largura que a tela pede (900, 1800 ou 2700
 * px), da mesma família. Cada teste abre um contexto novo, porque o cache de outro teste esconderia
 * a escolha.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class ImagensIT {

  private static final double ESPERA_MS = 10_000;
  private static final String TERRACO = ".terraco-crepusculo";
  private static final String SPRITE_DO_TELESCOPIO =
      ".peca[data-peca='telescopio'] .sprite-crepusculo";

  @Value("${local.server.port}")
  private int porta;

  @Test
  void celularComumCarregaATerracoNa900(Participantes participantes) {
    TerracoPagina ana = participantes.novo(tela(390, 844, 1));
    ana.abrir(raiz(porta));

    esperarFonte(ana.page(), TERRACO, "-900.webp");
  }

  @Test
  void celularComumNaoBaixaAVariante1800(Participantes participantes) {
    TerracoPagina ana = participantes.novo(tela(390, 844, 1));
    List<String> pedidos = new CopyOnWriteArrayList<>();
    ana.page().onRequest(pedido -> pedidos.add(pedido.url()));
    ana.abrir(raiz(porta));

    esperarFonte(ana.page(), TERRACO, "-900.webp");
    assertEquals(
        List.of(),
        pedidos.stream().filter(url -> url.matches(".*/terraco-[a-z]+-1800\\.webp$")).toList());
  }

  @Test
  void celularDeAltaDensidadeCarregaATerracoNa1800(Participantes participantes) {
    TerracoPagina ana = participantes.novo(tela(390, 844, 3));
    ana.abrir(raiz(porta));

    esperarFonte(ana.page(), TERRACO, "-1800.webp");
  }

  @Test
  void desktopRetinaCarregaATerracoNa2700(Participantes participantes) {
    TerracoPagina ana = participantes.novo(tela(1440, 900, 2));
    ana.abrir(raiz(porta));

    esperarFonte(ana.page(), TERRACO, "-2700.webp");
  }

  @Test
  void propsUsamAMesmaFamiliaDoTerraco(Participantes participantes) {
    TerracoPagina ana = participantes.novo(tela(390, 844, 3));
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.colocar(TELESCOPIO, C1, ANA);

    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    esperarFonte(ana.page(), SPRITE_DO_TELESCOPIO, "-1800.webp");
  }

  /** Opções novas a cada contexto: {@code Participantes.novo} preenche o movimento nelas. */
  private static Browser.NewContextOptions tela(int largura, int altura, double densidade) {
    return new Browser.NewContextOptions()
        .setViewportSize(largura, altura)
        .setDeviceScaleFactor(densidade);
  }

  /** Espera a imagem do seletor terminar de carregar com a fonte que termina no sufixo. */
  private static void esperarFonte(Page page, String seletor, String sufixo) {
    try {
      page.waitForFunction(
          """
          ([seletor, sufixo]) => {
            const img = document.querySelector(seletor);
            return img !== null && img.complete && img.naturalWidth > 0
                && img.currentSrc.endsWith(sufixo);
          }
          """,
          List.of(seletor, sufixo),
          new Page.WaitForFunctionOptions().setTimeout(ESPERA_MS));
    } catch (TimeoutError tempoEsgotado) {
      Object atual =
          page.evaluate("seletor => document.querySelector(seletor)?.currentSrc ?? null", seletor);
      fail(
          "%s devia carregar uma imagem terminada em %s, mas carregou %s"
              .formatted(seletor, sufixo, atual));
    }
  }
}
