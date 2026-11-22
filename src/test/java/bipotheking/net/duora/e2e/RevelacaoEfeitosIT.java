package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Efeitos noturnos por peça na revelação, lidos do {@code revelacao} do cenario.json: cada peça
 * colocada acende o seu à noite. Os efeitos são decorativos ({@code aria-hidden}), então o teste os
 * acha pelo {@code data-efeito}.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class RevelacaoEfeitosIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void cadaPecaColocadaTemSeuEfeitoNaNoite(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));

    // Telescópio (meteoros, sem elemento por peça), banco e manta (brilho), lanterna (luz e chama).
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");

    Page pagina = ana.page();
    assertThat(pagina.locator("[data-efeito=brilho]")).hasCount(2);
    assertThat(pagina.locator("[data-efeito=tremular]")).hasCount(1);
    assertThat(pagina.locator("[data-efeito=brilho-piso]")).hasCount(1);
    assertThat(pagina.locator("[data-efeito=halo]")).hasCount(1);
  }
}
