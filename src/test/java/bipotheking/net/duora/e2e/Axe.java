package bipotheking.net.duora.e2e;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import java.util.List;

/**
 * Varredura do axe-core (WCAG 2.0, 2.1 e 2.2, níveis A e AA) compartilhada pelos ITs. O céu, os
 * meteoros e a cidade são decorativos ({@code aria-hidden}) e ficam fora da análise: cada seletor é
 * excluído à parte, porque {@code AxeBuilder.exclude(List)} trata a lista como um único caminho de
 * iframes. A página precisa estar com movimento reduzido, para a análise de contraste não pegar
 * elementos no meio de uma transição: o helper confere isso antes de varrer.
 */
final class Axe {

  private static final List<String> TAGS =
      List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
  private static final List<String> DECORATIVOS = List.of(".ceu", "#meteoros", ".cidade");

  private Axe() {}

  /** Falha com a lista de regras violadas; {@code estado} nomeia a tela na mensagem. */
  static void assertSemViolacoes(Page pagina, String estado) {
    analisar(pagina, estado, List.of());
  }

  /**
   * Como {@link #assertSemViolacoes(Page, String)}, só dentro dos seletores dados (o axe varre a
   * união deles numa execução): para estados passageiros, em que varrer a página inteira pode
   * passar do tempo do estado.
   */
  static void assertSemViolacoesEm(Page pagina, String estado, List<String> escopo) {
    analisar(pagina, estado, escopo);
  }

  private static void analisar(Page pagina, String estado, List<String> escopo) {
    assertTrue(
        (Boolean) pagina.evaluate("() => matchMedia('(prefers-reduced-motion: reduce)').matches"),
        () -> "o axe em " + estado + " precisa de movimento reduzido na página");
    AxeBuilder axe = new AxeBuilder(pagina).withTags(TAGS);
    escopo.forEach(axe::include);
    DECORATIVOS.forEach(axe::exclude);
    List<Rule> violacoes = axe.analyze().getViolations();
    assertEquals(List.of(), violacoes, () -> descrever(estado, violacoes));
  }

  private static String descrever(String estado, List<Rule> violacoes) {
    return violacoes.stream()
        .map(regra ->
            regra.getId()
                + " -> "
                + regra.getNodes().stream()
                    .map(no -> String.valueOf(no.getTarget()))
                    .collect(joining(", ")))
        .collect(joining("; ", "violações do axe em " + estado + ": ", ""));
  }
}
