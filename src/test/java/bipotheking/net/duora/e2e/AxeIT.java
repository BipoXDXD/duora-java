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
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
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
 * Varredura automática do axe-core (WCAG 2.0, 2.1 e 2.2, níveis A e AA) em quatro estados da
 * jornada. Complementa os ITs de acessibilidade escritos à mão; o céu, os meteoros e a cidade são
 * decorativos ({@code aria-hidden}) e ficam fora da análise.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AxeIT {

  private static final String ANA = "Ana";
  private static final String BRUNO = "Bruno";
  private static final List<String> TAGS =
      List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
  private static final List<String> DECORATIVOS = List.of(".ceu", "#meteoros", ".cidade");

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
  void entradaSemViolacoes() {
    TerracoPagina ana = participante().abrir(raiz());
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertSemViolacoes(ana);
  }

  @Test
  void conviteSemViolacoes() {
    TerracoPagina ana = participante().abrir(raiz());
    ana.criarTerraco(ANA);
    ana.linkDoConvite();

    assertSemViolacoes(ana);
  }

  @Test
  void montagemComPecaSelecionadaSemViolacoes() {
    TerracoPagina ana = participante();
    entrarEmPar(ana, participante());
    ana.selecionar(TELESCOPIO);

    assertSemViolacoes(ana);
  }

  @Test
  void reveladoSemViolacoes() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
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

    assertSemViolacoes(ana);
  }

  private static void assertSemViolacoes(TerracoPagina pagina) {
    List<Rule> violacoes =
        new AxeBuilder(pagina.page()).withTags(TAGS).exclude(DECORATIVOS).analyze().getViolations();
    assertEquals(List.of(), violacoes, () -> descrever(violacoes));
  }

  private static String descrever(List<Rule> violacoes) {
    return violacoes.stream()
        .map(regra ->
            regra.getId()
                + " -> "
                + regra.getNodes().stream()
                    .map(no -> String.valueOf(no.getTarget()))
                    .collect(joining(", ")))
        .collect(joining("; ", "violações do axe: ", ""));
  }

  // Movimento reduzido: a análise de contraste não pega elementos no meio de uma transição.
  private TerracoPagina participante() {
    BrowserContext contexto =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(1440, 900)
                .setReducedMotion(ReducedMotion.REDUCE));
    contextos.add(contexto);
    return new TerracoPagina(contexto.newPage());
  }

  private String raiz() {
    return "http://127.0.0.1:" + porta + "/";
  }

  private void entrarEmPar(TerracoPagina ana, TerracoPagina bruno) {
    ana.abrir(raiz()).criarTerraco(ANA);
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
  }
}
