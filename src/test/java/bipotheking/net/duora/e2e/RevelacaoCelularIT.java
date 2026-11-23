package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ReducedMotion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * A noite revelada no celular em pé (R6): a cena ocupa a tela, o resultado fica numa folha embaixo
 * e "Ver o painel" devolve o resto. Ana está no celular e confirma primeiro, então a noite chega a
 * ela pelo polling, sem gesto, como no uso real.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class RevelacaoCelularIT {

  private static final int ALTURA_DO_CELULAR = 844;

  @Value("${local.server.port}")
  private int porta;

  @Test
  void revelacaoOcupaATela(Participantes participantes) {
    TerracoPagina ana = participantes.novoCelular();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    // Foco num controle do painel, que vai sumir da vista: tem de ir para o resultado.
    ana.botao("Som").focus();

    bruno.botaoPronto().click();

    assertThat(ana.app()).hasAttribute("data-tela-cheia", "true");
    BoundingBox cena = ana.page().locator(".cena").boundingBox();
    assertEquals(0, cena.y, 0.5);
    assertTrue(
        cena.height >= 0.9 * ALTURA_DO_CELULAR, () -> "a cena tem só " + cena.height + " px");
    assertThat(ana.tituloDoResultado()).isVisible();
    assertThat(ana.tituloDoResultado()).isInViewport();
    assertThat(ana.tituloDoResultado()).isFocused();
    assertThat(ana.botao("Som")).not().isVisible();
  }

  @Test
  void tituloFocadoJaEstaVisivelComMovimento(Participantes participantes) {
    TerracoPagina ana =
        participantes.novo(
            new Browser.NewContextOptions()
                .setViewportSize(390, ALTURA_DO_CELULAR)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true)
                .setReducedMotion(ReducedMotion.NO_PREFERENCE));
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();

    bruno.botaoPronto().click();

    // WCAG 2.4.7: a folha sobe só pelo deslocamento; no instante do foco, o título já se vê.
    assertThat(ana.tituloDoResultado()).isFocused();
    assertEquals(
        "1", ana.page().locator("#resultado").evaluate("el => getComputedStyle(el).opacity"));
  }

  @Test
  void verOPainelEVoltar(Participantes participantes) {
    TerracoPagina ana = revelarComAnaNoCelular(participantes);

    ana.botao("Ver o painel").click();

    assertThat(ana.app()).hasAttribute("data-tela-cheia", "false");
    assertThat(ana.botao("Som")).isVisible();
    Locator verOCeu = ana.botao("Ver o céu");
    assertThat(verOCeu).isFocused();

    verOCeu.click();

    assertThat(ana.app()).hasAttribute("data-tela-cheia", "true");
    assertThat(ana.botao("Som")).not().isVisible();
    assertThat(ana.botao("Ver o painel")).isFocused();
  }

  @Test
  void avisoContinuaAnunciandoEmTelaCheia(Participantes participantes) {
    TerracoPagina ana = revelarComAnaNoCelular(participantes);

    // A noite chegou pelo polling: o aviso conta, e continua à vista sobre o céu.
    assertThat(ana.aviso()).hasText("Os dois confirmaram. Olhem o céu.");
    assertThat(ana.aviso()).isVisible();
    assertThat(ana.aviso()).isInViewport();
  }

  @Test
  void sairAlcancavelDepoisDeVerOPainel(Participantes participantes) {
    TerracoPagina ana = revelarComAnaNoCelular(participantes);
    assertThat(ana.botaoSair()).not().isVisible();

    ana.botao("Ver o painel").click();
    ana.botaoSair().click();

    assertThat(ana.confirmacaoDeSaida()).isVisible();
  }

  @Test
  void semTelaCheiaComZoom(Participantes participantes) {
    // 640×360: a largura de celular de um zoom de 400% numa tela de 1280×720, mas baixa demais.
    TerracoPagina ana = participantes.novo(640, 360, ReducedMotion.REDUCE);
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();

    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(ana.app()).not().hasAttribute("data-tela-cheia", "true");
    assertThat(ana.botao("Ver o painel")).not().isVisible();
  }

  @Test
  void telaCheiaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = revelarComAnaNoCelular(participantes);

    assertSemViolacoes(ana.page(), "tela cheia da noite no celular");
  }

  private TerracoPagina revelarComAnaNoCelular(Participantes participantes) {
    TerracoPagina ana = participantes.novoCelular();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-tela-cheia", "true");
    return ana;
  }
}
