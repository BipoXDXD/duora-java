package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Varredura automática do axe-core (WCAG 2.0, 2.1 e 2.2, níveis A e AA) em quatro estados da
 * jornada. Complementa os ITs de acessibilidade escritos à mão. O céu, os meteoros e a cidade são
 * decorativos ({@code aria-hidden}) e ficam fora da análise: cada seletor é excluído à parte,
 * porque {@code AxeBuilder.exclude(List)} trata a lista como um único caminho de iframes. Os
 * participantes usam movimento reduzido: a análise de contraste não pega elementos no meio de uma
 * transição.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class AxeIT {

  private static final List<String> TAGS =
      List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
  private static final List<String> DECORATIVOS = List.of(".ceu", "#meteoros", ".cidade");

  @Value("${local.server.port}")
  private int porta;

  @Test
  void entradaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo().abrir(raiz(porta));
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertSemViolacoes(ana);
  }

  @Test
  void conviteSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo().abrir(raiz(porta));
    ana.criarTerraco(ANA);
    ana.esperarLinkDoConvite();

    assertSemViolacoes(ana);
  }

  @Test
  void montagemComPecaSelecionadaSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    ana.selecionar(TELESCOPIO);

    assertSemViolacoes(ana);
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

    assertSemViolacoes(ana);
  }

  private static void assertSemViolacoes(TerracoPagina pagina) {
    AxeBuilder axe = new AxeBuilder(pagina.page()).withTags(TAGS);
    DECORATIVOS.forEach(axe::exclude);
    List<Rule> violacoes = axe.analyze().getViolations();
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
}
