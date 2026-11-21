package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ReducedMotion;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@ExtendWith(NavegadorExtension.class)
class RevelacaoIT {

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

  private static final Consumer<Page> COM_SONDA = page -> page.addInitScript(SONDA);

  @Value("${local.server.port}")
  private int porta;

  @Test
  void meteorosCruzamOLadoDoTelescopioUmaVezSemRecomecarComOPolling(Participantes participantes) {
    TerracoPagina ana =
        participantes.novo(
            1440,
            900,
            ReducedMotion.NO_PREFERENCE,
            COM_SONDA.andThen(page -> page.clock().install()));
    TerracoPagina bruno = participantes.novo(COM_SONDA);

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
  void movimentoReduzidoMostraRiscosParadosQueSomemAoAjustar(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);

    revelarComTelescopioEmC1(ana, bruno);

    assertThat(camada(ana)).hasAttribute("data-lado", "esquerdo");
    assertThat(camada(ana).locator(".meteoro-parado")).hasCount(4);
    assertEquals(0, lancados(ana));

    ana.botao("Ajustar de novo").click();
    assertThat(camada(ana).locator(".meteoro")).hasCount(0);
    assertThat(camada(ana)).not().hasAttribute("data-lado", "esquerdo");
  }

  @Test
  void somLigadoParaQuandoAnaSaiDoTerraco(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));

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

  private void revelarComTelescopioEmC1(TerracoPagina ana, TerracoPagina bruno) {
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
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
