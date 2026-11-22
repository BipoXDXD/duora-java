package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

/**
 * A abertura do terraço: cada pessoa toca a própria lanterna e, juntas, as lanternas acendem e a
 * montagem começa; sozinha, a abertura termina apagada no máximo de 90 s. Só este IT liga a
 * abertura: o resto da suíte e2e roda com o máximo zero (perfil {@code e2e} do pom).
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "duora.cenarios.abertura.maximo=PT90S")
@ExtendWith(NavegadorExtension.class)
class AberturaIT {

  @Value("${local.server.port}")
  private int porta;

  @Autowired private RelogioAdiantavel relogio;

  @TestConfiguration
  static class RelogioConfig {
    @Bean
    @Primary
    RelogioAdiantavel relogioAdiantavel() {
      return new RelogioAdiantavel();
    }
  }

  /** Ana cria o terraço e Bruno entra; os dois veem a própria lanterna e nenhuma peça. */
  private void entrarNaAbertura(TerracoPagina ana, TerracoPagina bruno) {
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);
    assertThat(ana.botaoDaLanterna()).isVisible();
    assertThat(bruno.botaoDaLanterna()).isVisible();
    assertThat(ana.itemDoInventario(TELESCOPIO)).isHidden();
    assertThat(bruno.itemDoInventario(BANCO)).isHidden();
  }

  @Test
  void osDoisAcendemEMontam(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarNaAbertura(ana, bruno);

    TerracoPagina.passarAbertura(ana, bruno);

    assertThat(ana.luzDasLanternas()).hasCount(2);
    assertThat(bruno.luzDasLanternas()).hasCount(2);
    assertThat(ana.aviso()).hasText("As lanternas acenderam.");
    assertThat(bruno.aviso()).hasText("As lanternas acenderam.");
  }

  @Test
  void soUmTocaEMontaDepoisDe90s(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarNaAbertura(ana, bruno);
    ana.botaoDaLanterna().click();
    assertThat(ana.botaoDaLanterna()).hasAttribute("aria-pressed", "true");

    relogio.adiantar(Duration.ofSeconds(90));

    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    assertThat(bruno.itemDoInventario(BANCO)).isVisible();
    assertThat(ana.luzDasLanternas()).hasCount(0);
    assertThat(bruno.luzDasLanternas()).hasCount(0);
    assertThat(ana.aviso()).not().hasText("As lanternas acenderam.");
    assertThat(bruno.aviso()).not().hasText("As lanternas acenderam.");
  }

  @Test
  void aberturaPorTeclado(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarNaAbertura(ana, bruno);

    ana.tabularAte(ana.botaoDaLanterna());
    ana.page().keyboard().press("Enter");

    assertThat(ana.botaoDaLanterna()).hasAttribute("aria-pressed", "true");
  }

  @Test
  void aberturaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarNaAbertura(ana, bruno);
    Axe.assertSemViolacoes(ana.page(), "abertura");

    ana.botaoDaLanterna().click();
    assertThat(ana.botaoDaLanterna()).hasAttribute("aria-pressed", "true");
    Axe.assertSemViolacoes(ana.page(), "abertura-tocada");

    bruno.botaoDaLanterna().click();
    assertThat(ana.luzDasLanternas()).hasCount(2);
    Axe.assertSemViolacoes(ana.page(), "abertura-acesa");
  }
}
