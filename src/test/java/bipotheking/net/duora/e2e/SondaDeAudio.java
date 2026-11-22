package bipotheking.net.duora.e2e;

import com.microsoft.playwright.Page;
import java.util.function.Consumer;

/**
 * Sonda na página, instalada antes do app: conta meteoros animados lançados (nós {@code .meteoro}
 * acrescentados) e registra cada fonte de áudio (loop, iniciada, parada) por trás do AudioContext
 * real, em {@code window.__sonda}.
 */
final class SondaDeAudio {

  private static final String SCRIPT =
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

  /** Prepara a página com a sonda (vale também depois de recarregar). */
  static final Consumer<Page> COM_SONDA = page -> page.addInitScript(SCRIPT);

  private SondaDeAudio() {}

  static int lancados(Page pagina) {
    return ((Number) pagina.evaluate("() => window.__sonda.lancados")).intValue();
  }
}
