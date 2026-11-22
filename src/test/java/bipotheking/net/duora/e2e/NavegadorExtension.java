package bipotheking.net.duora.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Navegador das jornadas: abre o motor de {@link Navegador} uma vez por classe, entrega {@link
 * Participantes} a testes e métodos {@code @BeforeEach} e fecha os contextos ao fim de cada teste.
 */
final class NavegadorExtension
    implements BeforeAllCallback, AfterAllCallback, AfterEachCallback, ParameterResolver {

  private static final ExtensionContext.Namespace ESPACO =
      ExtensionContext.Namespace.create(NavegadorExtension.class);

  @Override
  public void beforeAll(ExtensionContext contexto) {
    // O polling consulta a cada 2 s; a folga cobre máquinas lentas sem mascarar falhas.
    PlaywrightAssertions.setDefaultAssertionTimeout(10_000);
    Playwright playwright = Playwright.create();
    try {
      contexto
          .getStore(ESPACO)
          .put(Participantes.class, new Participantes(playwright, Navegador.abrir(playwright)));
    } catch (RuntimeException falha) {
      playwright.close();
      throw falha;
    }
  }

  @Override
  public void afterEach(ExtensionContext contexto) {
    participantes(contexto).fecharContextos();
  }

  @Override
  public void afterAll(ExtensionContext contexto) {
    Participantes participantes =
        contexto.getStore(ESPACO).remove(Participantes.class, Participantes.class);
    // Nulo só se o beforeAll falhou ao abrir o navegador; aí não há o que fechar.
    if (participantes != null) {
      participantes.fechar();
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parametro, ExtensionContext contexto) {
    return parametro.getParameter().getType() == Participantes.class;
  }

  @Override
  public Participantes resolveParameter(ParameterContext parametro, ExtensionContext contexto) {
    return participantes(contexto);
  }

  private static Participantes participantes(ExtensionContext contexto) {
    return contexto.getStore(ESPACO).get(Participantes.class, Participantes.class);
  }

  /**
   * Cria as pessoas de um teste, cada uma no próprio BrowserContext (cookies separados), como duas
   * pessoas em navegadores diferentes.
   */
  static final class Participantes {

    private static final int LARGURA = 1440;
    private static final int ALTURA = 900;
    private static final int LARGURA_CELULAR = 390;
    private static final int ALTURA_CELULAR = 844;
    private static final double DENSIDADE_CELULAR = 3;

    private final Playwright playwright;
    private final Browser browser;
    private final List<BrowserContext> contextos = new ArrayList<>();

    private Participantes(Playwright playwright, Browser browser) {
      this.playwright = playwright;
      this.browser = browser;
    }

    /** Tela de 1440×900 com movimento reduzido: nada fica no meio de uma transição. */
    TerracoPagina novo() {
      return novo(LARGURA, ALTURA, ReducedMotion.REDUCE);
    }

    TerracoPagina novo(int largura, int altura, ReducedMotion movimento) {
      return novo(largura, altura, movimento, page -> {});
    }

    /** Como {@link #novo()}, com a página preparada antes de navegar (sondas, relógio, rotas). */
    TerracoPagina novo(Consumer<Page> preparar) {
      return novo(LARGURA, ALTURA, ReducedMotion.REDUCE, preparar);
    }

    TerracoPagina novo(int largura, int altura, ReducedMotion movimento, Consumer<Page> preparar) {
      return novo(
          new Browser.NewContextOptions()
              .setViewportSize(largura, altura)
              .setReducedMotion(movimento),
          preparar);
    }

    /**
     * Contexto com as opções como vêm (aparelho, densidade, toque); só põe movimento reduzido e
     * service worker bloqueado se elas não disserem nada sobre isso.
     */
    TerracoPagina novo(Browser.NewContextOptions opcoes) {
      return novo(opcoes, page -> {});
    }

    /** Celular em pé: 390×844, densidade 3, tela de toque e layout de celular (meta viewport). */
    TerracoPagina novoCelular() {
      return novo(
          new Browser.NewContextOptions()
              .setViewportSize(LARGURA_CELULAR, ALTURA_CELULAR)
              .setDeviceScaleFactor(DENSIDADE_CELULAR)
              .setIsMobile(true)
              .setHasTouch(true)
              .setReducedMotion(ReducedMotion.REDUCE));
    }

    /**
     * Como {@link #novo()}, com o service worker liberado. Nos outros contextos ele fica bloqueado:
     * com um worker no controle, o WebKit deixa de passar pelo {@code page.route} das sondas.
     */
    TerracoPagina novoComServiceWorker() {
      return novo(
          new Browser.NewContextOptions()
              .setViewportSize(LARGURA, ALTURA)
              .setServiceWorkers(ServiceWorkerPolicy.ALLOW));
    }

    private TerracoPagina novo(Browser.NewContextOptions opcoes, Consumer<Page> preparar) {
      if (opcoes.reducedMotion == null) {
        opcoes.setReducedMotion(ReducedMotion.REDUCE);
      }
      if (opcoes.serviceWorkers == null) {
        opcoes.setServiceWorkers(ServiceWorkerPolicy.BLOCK);
      }
      BrowserContext contexto = browser.newContext(opcoes);
      contextos.add(contexto);
      Page page = contexto.newPage();
      preparar.accept(page);
      return new TerracoPagina(page);
    }

    private void fecharContextos() {
      contextos.forEach(BrowserContext::close);
      contextos.clear();
    }

    private void fechar() {
      browser.close();
      playwright.close();
    }
  }
}
