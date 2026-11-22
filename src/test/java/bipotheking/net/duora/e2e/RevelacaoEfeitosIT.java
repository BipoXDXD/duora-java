package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.SondaDeAudio.COM_SONDA;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.RADIO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import bipotheking.net.duora.e2e.TerracoPagina.Lugar;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.BoundingBox;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Efeitos noturnos por peça na revelação, lidos do {@code revelacao} do cenario.json: cada peça
 * colocada acende o seu à noite, o rádio toca as notas na passagem ao vivo (mesmo com o som
 * desligado) e o texto do resultado ganha as frases das peças que ficaram perto. Os efeitos são
 * decorativos ({@code aria-hidden}), então o teste os acha pelo {@code data-efeito}.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class RevelacaoEfeitosIT {

  /** Anel de foco (tokens.css, --foco): 3 px de noite-900 colados na peça e 3 px de âmbar. */
  private static final int[] NOITE_900 = {0x0d, 0x1f, 0x30};

  private static final int[] AMBAR = {0xf2, 0xbd, 0x70};

  private static final int TOLERANCIA = 24;

  @Value("${local.server.port}")
  private int porta;

  @Autowired private JsonMapper json;

  @Test
  void cadaPecaColocadaTemSeuEfeitoNaNoite(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));

    // Telescópio (meteoros, sem elemento por peça), banco e manta (brilho), lanterna (luz e chama).
    comporTerraco(ana, bruno);
    revelar(ana, bruno);

    Page pagina = ana.page();
    assertThat(pagina.locator("[data-efeito=brilho]")).hasCount(2);
    assertThat(pagina.locator("[data-efeito=tremular]")).hasCount(1);
    assertThat(pagina.locator("[data-efeito=brilho-piso]")).hasCount(1);
    assertThat(pagina.locator("[data-efeito=halo]")).hasCount(1);
  }

  @Test
  void radioTocaNotasComSomDesligado(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporComRadio(ana, bruno);
    assertThat(ana.page().locator("#som")).hasAttribute("aria-pressed", "false");

    revelar(ana, bruno);

    // Com o som desligado, a única fonte possível é a das notas do rádio.
    ana.page().waitForFunction("() => window.__sonda.fontes.some((f) => f.iniciada)");
  }

  @Test
  void recarregarNaNoiteNaoTocaNotas(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporComRadio(ana, bruno);
    revelar(ana, bruno);
    ana.page().waitForFunction("() => window.__sonda.fontes.some((f) => f.iniciada)");

    ana.page().reload();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(ana.tituloDoResultado()).isVisible();
    // Duas consultas do polling depois da noite: tempo de sobra para as notas, se fossem tocar.
    ana.page().waitForTimeout(2_500);

    assertEquals(
        Boolean.FALSE, ana.page().evaluate("() => window.__sonda.fontes.some((f) => f.iniciada)"));
  }

  @Test
  void textoDoResultadoMudaComAPosicao(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    compor(ana, bruno, MANTA, L1, L2);
    revelar(ana, bruno);
    Locator texto = ana.page().locator("#resultado-texto");
    assertThat(texto).containsText("A lanterna ilumina o telescópio de perto.");
    String comALanternaPertoDoTelescopio = texto.textContent();

    ana.botao("Ajustar de novo").click();
    bruno.botao("Ajustar de novo").click();
    assertThat(ana.app()).hasAttribute("data-momento", "crepusculo");
    bruno.colocar(LANTERNA, R2, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R2)).isVisible();
    revelar(ana, bruno);

    assertThat(texto).containsText("A lanterna clareia o banco.");
    assertNotEquals(comALanternaPertoDoTelescopio, texto.textContent());
  }

  @Test
  void meteorosSoComTelescopioColocado(Participantes participantes) {
    // O telescópio é obrigatório no terraço: aqui o efeito "meteoros" sai dele e vai para a
    // garrafa, que fica fora da composição. Nenhuma peça colocada traz meteoros.
    TerracoPagina ana =
        participantes.novo(
            page -> page.route("**/assets/cenarios/terraco/cenario.json", this::meteorosNaGarrafa));
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);

    revelar(ana, bruno);

    // A cena já é a da noite (efeitos acesos); com movimento reduzido, os meteoros seriam riscos
    // parados no mesmo desenho.
    assertThat(ana.page().locator("[data-efeito=brilho]")).hasCount(2);
    assertThat(ana.page().locator("#meteoros .meteoro")).hasCount(0);
    assertThat(ana.page().locator("#meteoros")).not().hasAttribute("data-lado", "esquerdo");
  }

  @Test
  void focoDaLanternaVisivelSobOHaloEOTremular(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    revelar(ana, bruno);
    Locator lanterna = bruno.pecaNoTerraco(LANTERNA, BRUNO, R1);
    assertThat(bruno.page().locator("[data-efeito=tremular]")).hasCount(1);

    bruno.tabularAte(lanterna);

    // As luzes da frente clareiam em "screen": sobre o anel, o desbotariam.
    BoundingBox caixa = lanterna.boundingBox();
    BufferedImage tela = captura(bruno.page());
    int y = (int) (caixa.y + caixa.height * 0.4);
    assertCor(NOITE_900, tela.getRGB((int) Math.floor(caixa.x - 1.5), y), "faixa escura do anel");
    assertCor(AMBAR, tela.getRGB((int) Math.floor(caixa.x - 4.5), y), "faixa âmbar do anel");
  }

  /** Ana confirma primeiro; a noite chega a ela pelo polling, como no uso real. */
  private static void revelar(TerracoPagina ana, TerracoPagina bruno) {
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(bruno.app()).hasAttribute("data-momento", "noite");
  }

  /** Telescópio em C1 e banco em C2, com o rádio de Ana em L2 e a lanterna de Bruno em R1. */
  private static void comporComRadio(TerracoPagina ana, TerracoPagina bruno) {
    compor(ana, bruno, RADIO, L2, R1);
  }

  /** Telescópio em C1 e banco em C2, mais a secundária de Ana e a lanterna de Bruno. */
  private static void compor(
      TerracoPagina ana,
      TerracoPagina bruno,
      TerracoPagina.Peca secundariaDeAna,
      Lugar lugarDaSecundaria,
      Lugar lugarDaLanterna) {
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(secundariaDeAna, lugarDaSecundaria, ANA);
    assertThat(bruno.pecaNoTerraco(secundariaDeAna, ANA, lugarDaSecundaria)).isVisible();
    bruno.colocar(LANTERNA, lugarDaLanterna, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, lugarDaLanterna)).isVisible();
  }

  /** Serve o cenario.json de verdade com os meteoros na garrafa, não no telescópio. */
  private void meteorosNaGarrafa(Route rota) {
    APIResponse original = rota.fetch();
    ObjectNode cenario = (ObjectNode) json.readTree(original.text());
    ObjectNode revelacao = (ObjectNode) cenario.get("revelacao");
    revelacao.putArray("telescopio");
    ((ArrayNode) revelacao.get("garrafa")).addObject().put("tipo", "meteoros");
    rota.fulfill(
        new Route.FulfillOptions().setResponse(original).setBody(json.writeValueAsString(cenario)));
  }

  private static BufferedImage captura(Page pagina) {
    try {
      return ImageIO.read(new ByteArrayInputStream(pagina.screenshot()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void assertCor(int[] esperada, int rgb, String onde) {
    int[] cor = {(rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff};
    for (int i = 0; i < 3; i++) {
      int canal = i;
      assertTrue(
          Math.abs(cor[canal] - esperada[canal]) <= TOLERANCIA,
          () ->
              onde + ": esperado ~" + Arrays.toString(esperada) + ", veio " + Arrays.toString(cor));
    }
  }
}
