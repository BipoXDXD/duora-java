package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
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
 * Sugestão com destino: tocar a peça do parceiro abre os destinos "Que tal aqui?"; o parceiro vê o
 * lugar sugerido e aceita pelo "Mover …" do inventário, ou ignora e a sugestão expira.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class SinalComDestinoIT {

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

  /** Ana com o telescópio em C1; Bruno com a lanterna em R2, vista pelos dois. */
  private static void lanternaDeBrunoEmR2(TerracoPagina ana, TerracoPagina bruno) {
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(LANTERNA, R2, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R2)).isVisible();
  }

  /** Ana sugere a lanterna de Bruno em R1, e Bruno vê o "Mover …". */
  private static void anaSugereR1(TerracoPagina ana, TerracoPagina bruno) {
    ana.pecaNoTerraco(LANTERNA, BRUNO, R2).click();
    ana.destinoSugerivel(LANTERNA, R1).click();
    assertThat(bruno.botaoMover(LANTERNA, R1)).isVisible();
  }

  @Test
  void sugestaoAceitaMoveAPecaParaODestino(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    lanternaDeBrunoEmR2(ana, bruno);

    ana.pecaNoTerraco(LANTERNA, BRUNO, R2).click();
    ana.destinoSugerivel(LANTERNA, R1).click();
    assertThat(ana.aviso()).hasText("Você sugeriu que Bruno mova a lanterna: fundo, à direita.");
    assertThat(bruno.aviso()).hasText("Ana sugeriu mover a lanterna: fundo, à direita.");
    bruno.botaoMover(LANTERNA, R1).click();

    assertThat(bruno.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
  }

  @Test
  void lugarOcupadoNaoApareceComoSugestao(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    lanternaDeBrunoEmR2(ana, bruno);
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();

    ana.pecaNoTerraco(LANTERNA, BRUNO, R2).click();

    assertThat(ana.destinoSugerivel(LANTERNA, L1)).isVisible();
    assertThat(ana.destinoSugerivel(LANTERNA, L2)).hasCount(0);
  }

  @Test
  void sugestaoIgnoradaExpira(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    lanternaDeBrunoEmR2(ana, bruno);
    anaSugereR1(ana, bruno);

    relogio.adiantar(Duration.ofSeconds(21));

    assertThat(bruno.botaoMover(LANTERNA, R1)).hasCount(0);
  }

  @Test
  void sugerindoSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    lanternaDeBrunoEmR2(ana, bruno);

    ana.pecaNoTerraco(LANTERNA, BRUNO, R2).click();
    assertThat(ana.destinoSugerivel(LANTERNA, R1)).isVisible();

    Axe.assertSemViolacoes(ana.page(), "sugerindo");
  }

  @Test
  void sugestaoRecebidaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    lanternaDeBrunoEmR2(ana, bruno);

    anaSugereR1(ana, bruno);

    Axe.assertSemViolacoes(bruno.page(), "sugestao-recebida");
  }
}
