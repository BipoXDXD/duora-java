package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ForcedColors;
import com.microsoft.playwright.options.ReducedMotion;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Achados de acessibilidade (WCAG 2.2 AA) de layout e estilo: foco não coberto em telas baixas,
 * anel de foco e estados em alto contraste (forced-colors), sinal estático com movimento reduzido e
 * rótulo visível contido no nome acessível.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class AcessibilidadeLayoutIT {

  /**
   * Rola o elemento até o meio da tela e, no mesmo passo (sem a tela redesenhar no meio), devolve
   * vazio se o ponto central pertence a ele; senão, descreve o que o cobre.
   */
  private static final String CENTRO_VISIVEL =
      """
      (el) => {
        el.scrollIntoView({ block: "center" });
        const r = el.getBoundingClientRect();
        const x = r.left + r.width / 2;
        const y = r.top + r.height / 2;
        if (x < 0 || y < 0 || x > innerWidth || y > innerHeight) return `fora da tela em ${x},${y}`;
        const topo = document.elementFromPoint(x, y);
        if (topo !== null && (topo === el || el.contains(topo))) return "";
        return topo === null ? "nada" : `${topo.tagName}.${topo.className} em ${x},${y}`;
      }
      """;

  @Value("${local.server.port}")
  private int porta;

  // 640×360 = 1280×720 com zoom de 200%; 844×390 = celular deitado.
  @ParameterizedTest(name = "{0}×{1}")
  @CsvSource({"640, 360", "844, 390"})
  void primeiroItemDasPecasFocadoNaoFicaCobertoEmTelaBaixa(
      int largura, int altura, Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.page().setViewportSize(largura, altura);

    Locator item = ana.itemDoInventario(TELESCOPIO);
    item.focus();

    assertEquals("", oQueCobre(item));
  }

  @ParameterizedTest(name = "{0}×{1}")
  @CsvSource({"640, 360", "844, 390"})
  void botaoProntoFocadoNaoFicaCobertoEmTelaBaixa(
      int largura, int altura, Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.page().setViewportSize(largura, altura);

    Locator pronto = ana.botaoPronto();
    pronto.focus();

    assertEquals("", oQueCobre(pronto));
  }

  @Test
  void anelDeFocoContinuaVisivelEmAltoContraste(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    ana.page().emulateMedia(new Page.EmulateMediaOptions().setForcedColors(ForcedColors.ACTIVE));
    ana.abrir(raiz(porta));
    ana.page().getByLabel("Seu apelido").fill(ANA);

    ana.page().keyboard().press(Navegador.TAB);

    String estilo =
        (String) ana.page().evaluate("() => getComputedStyle(document.activeElement).outlineStyle");
    assertNotEquals("none", estilo);
  }

  @Test
  void tituloDoEncerradoFocadoPeloTecladoMantemOAnelDeFoco(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.botaoSair().focus();
    ana.page().keyboard().press("Enter");
    Locator encerrar = ana.botao("Encerrar para os dois");
    ana.tabularAte(encerrar);
    assertThat(encerrar).isFocused();
    ana.page().keyboard().press("Enter");

    assertThat(ana.tituloEncerrado()).isFocused();
    assertNotEquals(
        "none", ana.page().evaluate("() => getComputedStyle(document.activeElement).boxShadow"));
  }

  @ParameterizedTest(name = "{0}×{1}")
  @CsvSource({"390, 844", "1440, 900"})
  void confirmacaoDeSaidaNaoCobreCenaNemPainel(
      int largura, int altura, Participantes participantes) {
    TerracoPagina ana = participantes.novo(largura, altura, ReducedMotion.REDUCE);
    entrarEmPar(ana, participantes.novo(largura, altura, ReducedMotion.REDUCE), raiz(porta));

    ana.botaoSair().click();
    Locator confirmacao = ana.confirmacaoDeSaida();
    assertThat(confirmacao).isVisible();

    assertEquals("", sobreposicao(confirmacao), "no topo da página");
    // No celular, a cena fica presa ao rolar: o bloco aberto não pode ficar por cima dela.
    ana.page().evaluate("() => window.scrollTo(0, document.documentElement.scrollHeight)");
    assertEquals("", sobreposicao(confirmacao), "com a página rolada até o fim");
  }

  @Test
  void itemPressionadoSeDistingueAlemDaCorEmAltoContraste(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    ana.page().emulateMedia(new Page.EmulateMediaOptions().setForcedColors(ForcedColors.ACTIVE));
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.selecionar(TELESCOPIO);

    assertNotEquals(
        bordaSemCor(ana.itemDoInventario(MANTA)), bordaSemCor(ana.itemDoInventario(TELESCOPIO)));
  }

  @Test
  void pecaSinalizadaTemSeloEstaticoENomeComMovimentoReduzido(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(LANTERNA, R2, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R2)).isVisible();

    ana.pecaNoTerraco(LANTERNA, BRUNO, R2).click();
    ana.destinoSugerivel(LANTERNA, R1).click();

    Locator lanterna = bruno.pecaNoTerraco(LANTERNA, BRUNO, R2);
    assertThat(lanterna).hasAccessibleName(Pattern.compile("Sugerido mover"));
    assertThat(lanterna.getByText("Sugerido mover")).isVisible();
  }

  @Test
  void pecaPropriaTemOSeloVoceNoNomeAcessivel(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.colocar(TELESCOPIO, C1, ANA);

    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).hasAccessibleName(Pattern.compile("Você"));
  }

  @Test
  void rotuloVisivelDoLugarEstaContidoNoNomeAcessivel(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    ana.selecionar(TELESCOPIO);

    Locator lugar = ana.lugarLivre(TELESCOPIO, C1);
    String visivel = lugar.locator(".rotulo-lugar").innerText().strip();
    String nome = lugar.getAttribute("aria-label");
    assertTrue(nome.contains(visivel), "'" + visivel + "' fora de '" + nome + "'");
  }

  @Test
  void itemBloqueadoEsmaeceSoAMiniatura(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.colocar(TELESCOPIO, C1, ANA);

    // Com uma peça no terraço, Ana espera a primeira de Bruno: a manta fica bloqueada.
    Locator item = ana.itemDoInventario(MANTA);
    assertThat(item).hasAttribute("aria-disabled", "true");

    assertEquals("1", opacidade(item));
    assertNotEquals("1", opacidade(item.locator(".miniatura")));
  }

  @Test
  void botaoIndisponivelUsaGrayTextEmAltoContraste(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    ana.page().emulateMedia(new Page.EmulateMediaOptions().setForcedColors(ForcedColors.ACTIVE));
    entrarEmPar(ana, participantes.novo(), raiz(porta));

    Locator pronto = ana.botaoPronto();
    assertThat(pronto).hasAttribute("aria-disabled", "true");

    String grayText =
        (String)
            ana.page()
                .evaluate(
                    """
                    () => {
                      const sonda = document.createElement("span");
                      sonda.style.color = "GrayText";
                      document.body.append(sonda);
                      const cor = getComputedStyle(sonda).color;
                      sonda.remove();
                      return cor;
                    }
                    """);
    assertEquals(grayText, pronto.evaluate("(el) => getComputedStyle(el).color"));
  }

  /** Vazio se o elemento não cruza a cena nem o painel; senão, diz com qual dos dois cruza. */
  private static String sobreposicao(Locator elemento) {
    return (String)
        elemento.evaluate(
            """
            (el) => {
              const a = el.getBoundingClientRect();
              const cruza = (b) => a.left < b.right && b.left < a.right
                  && a.top < b.bottom && b.top < a.bottom;
              return [".cena", ".painel"]
                  .filter((s) => cruza(document.querySelector(s).getBoundingClientRect()))
                  .join(" ");
            }
            """);
  }

  private static String oQueCobre(Locator elemento) {
    return (String) elemento.evaluate(CENTRO_VISIVEL);
  }

  private static String bordaSemCor(Locator elemento) {
    return (String)
        elemento.evaluate(
            "(el) => { const s = getComputedStyle(el);"
                + " return s.borderTopWidth + ' ' + s.borderTopStyle; }");
  }

  private static String opacidade(Locator elemento) {
    return (String) elemento.evaluate("(el) => getComputedStyle(el).opacity");
  }
}
