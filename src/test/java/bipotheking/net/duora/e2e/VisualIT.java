package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.ReducedMotion;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotCaret;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Capturas de referência dos estados do terraço, para comparar o visual antes e depois de uma
 * mudança no front ({@code ComparacaoVisualTest}). Não é jornada: fica fora da rodada e2e padrão e
 * roda só no Chromium, quando pedida:
 *
 * <pre>
 * ./mvnw -q -Pe2e verify -Dit.test=VisualIT -Dvisual.saida=&lt;dir&gt;
 * </pre>
 *
 * Grava {@code <dir>/<estado>-<largura>x<altura>.png} em 1440×900 e 390×844, com densidade 1 e
 * movimento reduzido.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
@EnabledIfSystemProperty(named = "visual.saida", matches = ".+")
@EnabledIf("noChromium")
class VisualIT {

  /** O sinal dura 20 s no servidor; a folga cobre o recuo do polling até a visão sem ele. */
  private static final double ESPERA_FIM_DO_SINAL_MS = 45_000;

  /**
   * Carrega as três faces dos {@code @font-face} do app, mesmo as que o estado ainda não usa, e
   * devolve as que o navegador não encontrou. Um arquivo que não baixa rejeita a promessa.
   */
  private static final String CARREGAR_FONTES =
      """
      () => Promise.all([
          '400 1em "Atkinson Hyperlegible Next"',
          '600 1em "Atkinson Hyperlegible Next"',
          '600 1em "Fraunces"',
        ].map((fonte) => document.fonts.load(fonte).then((faces) => (faces.length ? null : fonte))))
        .then((faltando) => faltando.filter(Boolean))
      """;

  /**
   * Pronto para a captura: imagens e o céu pintado carregados (as fontes vêm de CARREGAR_FONTES).
   */
  private static final String PRONTO =
      """
      () => Array.from(document.images).every((img) => img.complete)
        && document.querySelector(".app.ceu-pintado") !== null
      """;

  @Value("${local.server.port}")
  private int porta;

  static boolean noChromium() {
    return "chromium".equals(Navegador.NOME);
  }

  @ParameterizedTest(name = "{0}x{1}")
  @CsvSource({"1440, 900", "390, 844"})
  void capturaOsEstadosDoTerraco(int largura, int altura, Participantes participantes)
      throws IOException {
    Path saida = Files.createDirectories(Path.of(System.getProperty("visual.saida")));
    Capturas capturas = new Capturas(saida, largura, altura);
    TerracoPagina ana = participantes.novo(largura, altura, ReducedMotion.REDUCE);
    TerracoPagina bruno = participantes.novo(largura, altura, ReducedMotion.REDUCE);

    ana.abrir(raiz(porta));
    assertThat(ana.botao("Criar terraço")).isVisible();
    capturas.gravar(ana, "entrada");

    ana.criarTerraco(ANA);
    String convite = ana.linkDoConvite();
    bruno.abrir(convite);
    assertThat(bruno.botao("Entrar no terraço")).isVisible();
    capturas.gravar(bruno, "entrada-convite");
    capturas.gravar(ana, "convite");

    bruno.entrarNoTerraco(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    capturas.gravar(ana, "montagem");

    ana.selecionar(TELESCOPIO);
    assertThat(ana.lugarLivre(TELESCOPIO, C1)).isVisible();
    capturas.gravar(ana, "selecionando");

    // Uma peça por vez, cada uma vista pelo outro antes da seguinte.
    ana.lugarLivre(TELESCOPIO, C1).click();
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();
    bruno.colocar(LANTERNA, R2, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R2)).isVisible();
    capturas.gravar(ana, "com-pecas");

    bruno.pecaNoTerraco(MANTA, ANA, L2).click();
    assertThat(bruno.destinoSugerivel(MANTA, R1)).isVisible();
    capturas.gravar(bruno, "sugerindo");
    bruno.destinoSugerivel(MANTA, R1).click();
    Locator manta = ana.pecaNoTerraco(MANTA, ANA, L2);
    assertThat(manta).hasAttribute("data-sinalizada", "true");
    capturas.gravar(ana, "sinal");

    // O sinal expira em 20 s: esperar o fim dele tira o relógio das capturas seguintes.
    assertThat(manta)
        .not()
        .hasAttribute(
            "data-sinalizada",
            "true",
            new LocatorAssertions.HasAttributeOptions().setTimeout(ESPERA_FIM_DO_SINAL_MS));
    ana.botaoSair().click();
    assertThat(ana.confirmacaoDeSaida()).isVisible();
    capturas.gravar(ana, "confirmar-saida");
    ana.botaoContinuarNoTerraco().click();
    assertThat(ana.confirmacaoDeSaida()).hasCount(0);

    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(ana.tituloDoResultado()).hasText(Pattern.compile(".+"));
    assertThat(ana.page().locator("#meteoros .meteoro-parado")).hasCount(4);
    capturas.gravar(ana, "revelado");

    ana.sairDoTerraco();
    assertThat(bruno.tituloEncerrado()).isVisible();
    capturas.gravar(bruno, "encerrado");
  }

  /** Grava cada estado num tamanho, com a página parada e o link do convite mascarado. */
  private record Capturas(Path saida, int largura, int altura) {

    void gravar(TerracoPagina pagina, String estado) {
      Page page = pagina.page();
      Object faltando = page.evaluate(CARREGAR_FONTES);
      if (!(faltando instanceof List<?> lista) || !lista.isEmpty()) {
        throw new IllegalStateException("faces sem @font-face na página: " + faltando);
      }
      page.waitForFunction(PRONTO);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setFullPage(true)
              .setAnimations(ScreenshotAnimations.DISABLED)
              .setCaret(ScreenshotCaret.HIDE)
              // O token do convite muda a cada execução.
              .setMask(List.of(page.getByLabel("Link para a outra pessoa")))
              .setPath(saida.resolve(estado + "-" + largura + "x" + altura + ".png")));
    }
  }
}
