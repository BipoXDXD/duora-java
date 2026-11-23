package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.GARRAFA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.RADIO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Varredura automática do axe-core (WCAG 2.0, 2.1 e 2.2, níveis A e AA) em quatro estados da
 * jornada e na tela de erro do cenário, pelo {@link Axe}. Complementa os ITs de acessibilidade
 * escritos à mão. Os participantes usam movimento reduzido, como o {@link Axe} exige.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class AxeIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void entradaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo().abrir(raiz(porta));
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertSemViolacoes(ana.page(), "entrada");
  }

  @Test
  void conviteSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo().abrir(raiz(porta));
    ana.criarTerraco(ANA);
    ana.esperarLinkDoConvite();

    assertSemViolacoes(ana.page(), "convite");
  }

  @Test
  void montagemComPecaSelecionadaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.selecionar(TELESCOPIO);

    assertSemViolacoes(ana.page(), "montagem com peça selecionada");
  }

  @Test
  void reveladoSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");

    assertSemViolacoes(ana.page(), "revelado");
  }

  /**
   * A outra metade dos efeitos noturnos: o reveladoSemViolacoes já tem lanterna, manta e banco;
   * aqui ficam o rádio (notas, sem elemento) e a garrafa (vapor), que não cabem na mesma noite.
   */
  @Test
  void reveladoComRadioEVaporSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(RADIO, L2, ANA);
    assertThat(bruno.pecaNoTerraco(RADIO, ANA, L2)).isVisible();
    bruno.colocar(GARRAFA, R1, BRUNO);
    assertThat(ana.pecaNoTerraco(GARRAFA, BRUNO, R1)).isVisible();
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(ana.page().locator("[data-efeito=vapor]")).hasCount(1);

    assertSemViolacoes(ana.page(), "revelado com rádio e vapor");
  }

  @Test
  void erroDeCenarioSemViolacoes(Participantes participantes) {
    TerracoPagina ana =
        participantes
            .novo(page ->
                page.route(
                    "**/assets/cenarios/terraco/cenario.json",
                    r -> r.fulfill(new Route.FulfillOptions().setStatus(404))))
            .abrir(raiz(porta));
    assertThat(ana.page().getByRole(AriaRole.ALERT)).isVisible();

    assertSemViolacoes(ana.page(), "erro de cenário");
  }
}
