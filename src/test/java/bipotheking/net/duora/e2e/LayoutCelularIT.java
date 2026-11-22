package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Layout no celular, em pé e deitado, e na tela larga: nenhuma fase rola para o lado, e a
 * confirmação de saída aberta solta o topo sem depender de {@code :has()}.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class LayoutCelularIT {

  /** Abaixo disso o contexto é de celular: meta viewport, toque e densidade 3. */
  private static final int LARGURA_DE_MESA = 1000;

  @Value("${local.server.port}")
  private int porta;

  @ParameterizedTest(name = "{0}×{1} em {2}")
  @CsvSource({
    "390, 844, entrada",
    "390, 844, montagem",
    "390, 844, revelado",
    "844, 390, entrada",
    "844, 390, montagem",
    "844, 390, revelado",
    "1440, 900, entrada",
    "1440, 900, montagem",
    "1440, 900, revelado"
  })
  void semRolagemLateral(int largura, int altura, String fase, Participantes participantes) {
    TerracoPagina ana = participantes.novo(opcoes(largura, altura));
    levarAte(fase, ana, () -> participantes.novo(opcoes(largura, altura)));
    Page page = ana.page();

    // Sem a meta viewport, o celular desenha a página com 980 px e a conta abaixo passaria à toa.
    assertEquals(largura, ((Number) page.evaluate("() => innerWidth")).intValue());
    String excesso =
        (String)
            page.evaluate(
                """
                () => {
                  const raiz = document.documentElement;
                  return raiz.scrollWidth <= raiz.clientWidth
                      ? "" : `scrollWidth ${raiz.scrollWidth} > clientWidth ${raiz.clientWidth}`;
                }
                """);
    assertEquals("", excesso);
  }

  @Test
  void confirmacaoAbertaNoCelularSoltaOTopo(Participantes participantes) {
    TerracoPagina ana =
        participantes.novo(new Browser.NewContextOptions().setViewportSize(390, 860));
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.botaoSair().click();
    assertThat(ana.confirmacaoDeSaida()).isVisible();

    assertThat(ana.app()).containsClass("confirmando-saida");
    assertEquals(
        "static",
        ana.page().evaluate("() => getComputedStyle(document.querySelector('.topo')).position"));
  }

  private static Browser.NewContextOptions opcoes(int largura, int altura) {
    Browser.NewContextOptions opcoes =
        new Browser.NewContextOptions().setViewportSize(largura, altura);
    if (largura < LARGURA_DE_MESA) {
      opcoes.setDeviceScaleFactor(3).setIsMobile(true).setHasTouch(true);
    }
    return opcoes;
  }

  /** Leva Ana até a fase; Bruno só nasce nas fases que precisam do par. */
  private void levarAte(String fase, TerracoPagina ana, Supplier<TerracoPagina> bruno) {
    switch (fase) {
      case "entrada" -> {
        ana.abrir(raiz(porta));
        assertThat(ana.page().getByLabel("Seu apelido")).isVisible();
      }
      case "montagem" -> {
        entrarEmPar(ana, bruno.get(), raiz(porta));
        ana.colocar(TELESCOPIO, C1, ANA);
      }
      case "revelado" -> {
        TerracoPagina par = bruno.get();
        entrarEmPar(ana, par, raiz(porta));
        comporTerraco(ana, par);
        ana.confirmar();
        par.botaoPronto().click();
        assertThat(ana.app()).hasAttribute("data-momento", "noite");
      }
      default -> throw new IllegalArgumentException("Fase desconhecida: " + fase);
    }
  }
}
