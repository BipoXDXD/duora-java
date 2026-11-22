package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Um convite aberto na mesma aba, sem recarregar: o do próprio terraço só avisa; o de outra sala
 * pede a troca, que encerra o terraço atual para os dois.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class TrocaDeConviteIT {

  private static final String CARLA = "Carla";

  @Value("${local.server.port}")
  private int porta;

  private TerracoPagina ana;
  private TerracoPagina bruno;
  private TerracoPagina carla;

  @BeforeEach
  void abrirParticipantes(Participantes participantes) {
    ana = participantes.novo();
    bruno = participantes.novo();
    carla = participantes.novo();
  }

  @Test
  void conviteNovoNaMesmaAbaTrocaDeSala() {
    entrarEmPar(ana, bruno, raiz(porta));
    carla.abrir(raiz(porta)).criarTerraco(CARLA);
    String conviteDeCarla = carla.linkDoConvite();

    ana.abrir(conviteDeCarla);

    assertThat(ana.page().getByText("Entrar neste convite encerra o terraço atual para os dois."))
        .isVisible();
    assertThat(ana.botaoContinuarNoTerraco()).isVisible();
    assertThat(ana.page().getByLabel("Seu apelido")).isFocused();
    Axe.assertSemViolacoes(ana.page(), "troca de sala");
    ana.capturar("troca-de-sala");

    ana.entrarNoTerraco(ANA);

    assertThat(ana.page().locator("#par")).containsText(CARLA);
    assertThat(carla.page().locator("#par")).containsText(ANA);
    assertThat(bruno.tituloEncerrado()).isVisible();
  }

  @Test
  void continuarNoTerracoVoltaAoParSemSair() {
    entrarEmPar(ana, bruno, raiz(porta));
    carla.abrir(raiz(porta)).criarTerraco(CARLA);

    ana.abrir(carla.linkDoConvite());
    ana.botaoContinuarNoTerraco().click();

    assertThat(ana.page().locator("#par")).containsText(Cenarios.BRUNO);
    assertThat(ana.botaoSair()).isVisible();
    assertThat(bruno.tituloEncerrado()).hasCount(0);
  }

  @Test
  void proprioConviteNaMesmaAbaAvisa() {
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    String proprio = ana.linkDoConvite();

    ana.abrir(proprio);

    assertThat(ana.aviso()).hasText("Este é o convite do seu terraço. Mande para a outra pessoa.");
    assertThat(ana.page().getByLabel("Link para a outra pessoa")).isVisible();
    assertThat(ana.page().getByLabel("Seu apelido")).hasCount(0);
    Axe.assertSemViolacoes(ana.page(), "convite próprio na mesma aba");
  }
}
