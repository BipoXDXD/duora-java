package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
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
 * confirmação de saída aberta solta o topo sem depender de {@code :has()}, e fechada tira a classe
 * que o solta.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class LayoutCelularIT {

  /** Vazio se a caixa do seletor cabe inteira na janela; senão, diz onde ela vaza. */
  private static final String FORA_DA_JANELA =
      """
      (s) => {
        const c = document.querySelector(s).getBoundingClientRect();
        const dentro = c.width > 0 && c.height > 0 && c.left >= 0 && c.top >= 0
            && c.right <= innerWidth + 1 && c.bottom <= innerHeight + 1;
        return dentro ? "" : `${c.left},${c.top} até ${c.right},${c.bottom} em ${innerWidth}×${innerHeight}`;
      }
      """;

  /** Vazio se o elemento está inteiro na parte visível do painel e da janela. */
  private static final String DENTRO_DO_PAINEL =
      """
      (el) => {
        const a = el.getBoundingClientRect();
        const p = document.querySelector(".painel").getBoundingClientRect();
        const dentro = a.top >= p.top && a.bottom <= p.bottom && a.top >= 0 && a.bottom <= innerHeight;
        return dentro ? "" : `título em ${a.top}..${a.bottom}, painel em ${p.top}..${p.bottom}`;
      }
      """;

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

  @Test
  void continuarTiraAClasseDaConfirmacao(Participantes participantes) {
    TerracoPagina ana = confirmacaoAbertaNoCelular(participantes);

    ana.botaoContinuarNoTerraco().click();

    assertThat(ana.confirmacaoDeSaida()).isHidden();
    assertThat(ana.app()).not().containsClass("confirmando-saida");
  }

  @Test
  void escTiraAClasseDaConfirmacao(Participantes participantes) {
    TerracoPagina ana = confirmacaoAbertaNoCelular(participantes);

    ana.page().keyboard().press("Escape");

    assertThat(ana.confirmacaoDeSaida()).isHidden();
    assertThat(ana.app()).not().containsClass("confirmando-saida");
  }

  @Test
  void deitadoMostraCenaEPainelLadoALado(Participantes participantes) {
    TerracoPagina ana = participantes.novo(opcoes(844, 390));
    levarAte("montagem", ana, () -> participantes.novo(opcoes(844, 390)));
    Page page = ana.page();

    assertEquals("", page.evaluate(FORA_DA_JANELA, ".cena"), "cena");
    assertEquals("", page.evaluate(FORA_DA_JANELA, ".painel"), "painel");
    double direitaDaCena = borda(page, ".cena", "right");
    double esquerdaDoPainel = borda(page, ".painel", "left");
    assertTrue(
        direitaDaCena <= esquerdaDoPainel + 1,
        "cena termina em " + direitaDaCena + ", painel começa em " + esquerdaDoPainel);

    // Rolar até "Suas peças" mexe só no painel: a página fica parada e a cena continua à vista.
    Locator titulo =
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Suas peças"));
    titulo.scrollIntoViewIfNeeded();
    assertEquals(0, ((Number) page.evaluate("() => scrollY")).intValue(), "a página rolou");
    assertEquals("", titulo.evaluate(DENTRO_DO_PAINEL));
    assertEquals("", page.evaluate(FORA_DA_JANELA, ".cena"), "cena depois de rolar");
  }

  @Test
  void deitadoSemAreaVaziaADireita(Participantes participantes) {
    TerracoPagina ana = participantes.novo(opcoes(844, 390));
    levarAte("montagem", ana, () -> participantes.novo(opcoes(844, 390)));
    Page page = ana.page();

    double direitaDoPainel = borda(page, ".painel", "right");
    assertTrue(direitaDoPainel >= 0.95 * 844, "painel termina em " + direitaDoPainel);
  }

  private static double borda(Page page, String seletor, String lado) {
    return ((Number)
            page.evaluate(
                "([s, lado]) => document.querySelector(s).getBoundingClientRect()[lado]",
                List.of(seletor, lado)))
        .doubleValue();
  }

  private TerracoPagina confirmacaoAbertaNoCelular(Participantes participantes) {
    TerracoPagina ana = participantes.novoCelular();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.botaoSair().click();
    assertThat(ana.app()).containsClass("confirmando-saida");
    return ana;
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
