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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.ReducedMotion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Revelação vista por Ana: meteoros do lado do telescópio (com e sem movimento reduzido) e o som
 * opcional, que precisa parar quando a sessão termina. O relógio do Playwright adianta a sequência
 * de meteoros sem sleep real; o servidor é o de verdade.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RevelacaoIT {

  private static final String ANA = "Ana";
  private static final String BRUNO = "Bruno";

  /**
   * Sonda na página: conta meteoros animados lançados (nós {@code .meteoro} acrescentados) e
   * registra cada fonte de áudio (loop, iniciada, parada) por trás do AudioContext real.
   */
  private static final String SONDA =
      """
      (() => {
        const r = { lancados: 0, fontes: [] };
        window.__sonda = r;
        new MutationObserver((mudancas) => {
          for (const m of mudancas) {
            for (const n of m.addedNodes) {
              if (n.classList?.contains("meteoro") && !n.classList.contains("meteoro-parado")) {
                r.lancados += 1;
              }
            }
          }
        }).observe(document, { childList: true, subtree: true });
        const Original = window.AudioContext;
        window.AudioContext = class extends Original {
          createBufferSource() {
            const fonte = super.createBufferSource();
            const registro = { iniciada: false, parada: false };
            Object.defineProperty(registro, "loop", { get: () => fonte.loop });
            r.fontes.push(registro);
            const iniciar = fonte.start.bind(fonte);
            const parar = fonte.stop.bind(fonte);
            fonte.start = (...a) => { registro.iniciada = true; return iniciar(...a); };
            fonte.stop = (...a) => { registro.parada = true; return parar(...a); };
            return fonte;
          }
        };
      })();
      """;

  private static Playwright playwright;
  private static Browser browser;

  @Value("${local.server.port}")
  private int porta;

  private final List<BrowserContext> contextos = new ArrayList<>();

  @BeforeAll
  static void abrirNavegador() {
    playwright = Playwright.create();
    browser = Navegador.abrir(playwright);
    PlaywrightAssertions.setDefaultAssertionTimeout(10_000);
  }

  @AfterAll
  static void fecharNavegador() {
    browser.close();
    playwright.close();
  }

  @AfterEach
  void fecharContextos() {
    contextos.forEach(BrowserContext::close);
    contextos.clear();
  }

  @Test
  void meteorosCruzamOLadoDoTelescopioUmaVezSemRecomecarComOPolling() {
    TerracoPagina ana = participante(ReducedMotion.NO_PREFERENCE);
    ana.page().clock().install();
    TerracoPagina bruno = participante(ReducedMotion.REDUCE);

    revelarComTelescopioEmC1(ana, bruno);

    // Telescópio em C1 aponta para o lado esquerdo do céu.
    assertThat(camada(ana)).hasAttribute("data-lado", "esquerdo");
    // 3 s até o primeiro e 5–7 s entre os seguintes: 60 s cobrem a sequência inteira (6 a 8).
    ana.page().clock().runFor(60_000);
    int lancados = lancados(ana);
    assertTrue(lancados >= 6 && lancados <= 8, "lançados: " + lancados);

    // Consultas seguintes (200 ou 304) mantêm a revelação sem relançar a sequência.
    for (int i = 0; i < 5; i++) {
      ana.page().clock().runFor(2_000);
      ana.page().waitForTimeout(150);
    }
    assertEquals(lancados, lancados(ana));
    assertThat(camada(ana)).hasAttribute("data-lado", "esquerdo");
  }

  @Test
  void movimentoReduzidoMostraRiscosParadosQueSomemAoAjustar() {
    TerracoPagina ana = participante(ReducedMotion.REDUCE);
    TerracoPagina bruno = participante(ReducedMotion.REDUCE);

    revelarComTelescopioEmC1(ana, bruno);

    assertThat(camada(ana)).hasAttribute("data-lado", "esquerdo");
    assertThat(camada(ana).locator(".meteoro-parado")).hasCount(4);
    assertEquals(0, lancados(ana));

    ana.botao("Ajustar de novo").click();
    assertThat(camada(ana).locator(".meteoro")).hasCount(0);
    assertThat(camada(ana)).not().hasAttribute("data-lado", "esquerdo");
  }

  @Test
  void somLigadoParaQuandoAnaSaiDoTerraco() {
    TerracoPagina ana = participante(ReducedMotion.REDUCE);
    TerracoPagina bruno = participante(ReducedMotion.REDUCE);
    entrarEmPar(ana, bruno);

    Locator som = ana.botao("Som");
    som.click();
    assertThat(som).hasAttribute("aria-pressed", "true");
    ana.page().waitForFunction("() => window.__sonda.fontes.some((f) => f.loop && f.iniciada)");

    ana.sairDoTerraco();
    assertThat(ana.tituloEncerrado()).isVisible();
    ana.page()
        .waitForFunction(
            "() => window.__sonda.fontes.filter((f) => f.loop).every((f) => f.parada)");
    // O botão fica oculto com a montagem (fora da árvore acessível): confere pelo id.
    assertThat(ana.page().locator("#som")).hasAttribute("aria-pressed", "false");
  }

  private TerracoPagina participante(ReducedMotion movimento) {
    BrowserContext contexto =
        browser.newContext(
            new Browser.NewContextOptions().setViewportSize(1440, 900).setReducedMotion(movimento));
    contextos.add(contexto);
    Page page = contexto.newPage();
    page.addInitScript(SONDA);
    return new TerracoPagina(page);
  }

  private String raiz() {
    return "http://127.0.0.1:" + porta + "/";
  }

  private void entrarEmPar(TerracoPagina ana, TerracoPagina bruno) {
    ana.abrir(raiz()).criarTerraco(ANA);
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
  }

  private void revelarComTelescopioEmC1(TerracoPagina ana, TerracoPagina bruno) {
    entrarEmPar(ana, bruno);
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();
    bruno.colocar(LANTERNA, R1, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
  }

  private static Locator camada(TerracoPagina pagina) {
    return pagina.page().locator("#meteoros");
  }

  private static int lancados(TerracoPagina pagina) {
    return ((Number) pagina.page().evaluate("() => window.__sonda.lancados")).intValue();
  }
}
