package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ReducedMotion;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Ver o que o outro fez: a peça que o parceiro acabou de colocar ganha um destaque passageiro na
 * cena, e o status com o que ele fez fica logo abaixo dos nomes, visível sem rolar (no celular
 * alto, preso abaixo da cena).
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class VerOQueOOutroFezIT {

  /** Vazio se a caixa do elemento cabe inteira na janela; senão, diz onde ela vaza. */
  private static final String FORA_DA_JANELA =
      """
      (el) => {
        const c = el.getBoundingClientRect();
        const dentro = c.width > 0 && c.height > 0 && c.left >= 0 && c.top >= 0
            && c.right <= innerWidth + 1 && c.bottom <= innerHeight + 1;
        return dentro ? "" : `${c.left},${c.top} até ${c.right},${c.bottom} em ${innerWidth}×${innerHeight}`;
      }
      """;

  /** Vazio se o elemento começa abaixo do topo, da cena e do bloco do par e do aviso. */
  private static final String COBERTO_PELO_QUE_FICA_PRESO =
      """
      (el) => {
        const limite = Math.max(...[".topo", ".cena", ".par-e-aviso"]
            .map((s) => document.querySelector(s).getBoundingClientRect().bottom));
        const c = el.getBoundingClientRect();
        return c.top >= limite - 1 ? "" : `item em ${c.top}..${c.bottom}, presos até ${limite}`;
      }
      """;

  private static final String STATUS_DO_BANCO = "Bruno colocou o banco";

  @Value("${local.server.port}")
  private int porta;

  @Test
  void pecaQueOParceiroColocouGanhaODestaque(Participantes participantes) {
    TerracoPagina ana = participantes.novo(page -> page.clock().install());
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));

    bruno.colocar(BANCO, C2, BRUNO);

    Page page = ana.page();
    Locator emDestaque =
        page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Banco de Bruno, centro, à direita\\..*Mudou agora$")));
    assertThat(emDestaque).isVisible();
    // O axe usa timers da página: com o relógio parado ele não termina. Ele roda com o tempo
    // correndo, só na cena (anel e selo) e no bloco do par e do aviso, para caber folgado nos
    // 2,4 s; a peça ainda em destaque depois dele prova que a varredura pegou o selo.
    Axe.assertSemViolacoesEm(
        page, "peça do parceiro em destaque", List.of(".cena", ".par-e-aviso"));
    assertThat(emDestaque).hasAttribute("data-destaque", "true");

    page.clock().runFor(2500);

    Locator banco = ana.pecaNoTerraco(BANCO, BRUNO, C2);
    assertThat(banco).isVisible();
    assertThat(banco).not().hasAccessibleName(Pattern.compile("Mudou agora"));
  }

  @ParameterizedTest(name = "{0}×{1}")
  @CsvSource({"1440, 835", "1440, 900"})
  void statusVisivelSemRolar(int largura, int altura, Participantes participantes) {
    TerracoPagina ana = participantes.novo(largura, altura, ReducedMotion.REDUCE);
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));

    bruno.colocar(BANCO, C2, BRUNO);

    assertThat(ana.aviso()).containsText(STATUS_DO_BANCO);
    assertEquals("", ana.aviso().evaluate(FORA_DA_JANELA));
  }

  @Test
  void statusVisivelNoCelularRolado(Participantes participantes) {
    TerracoPagina ana = participantes.novoCelular();
    TerracoPagina bruno = participantes.novo();
    entrarEmPar(ana, bruno, raiz(porta));
    ana.page().evaluate("() => window.scrollTo(0, document.documentElement.scrollHeight)");

    bruno.colocar(BANCO, C2, BRUNO);

    assertThat(ana.aviso()).containsText(STATUS_DO_BANCO);
    assertEquals("", ana.aviso().evaluate(FORA_DA_JANELA));
  }

  @Test
  void focoNoUltimoItemNaoFicaCobertoNoCelular(Participantes participantes) {
    TerracoPagina ana = participantes.novoCelular();
    entrarEmPar(ana, participantes.novo(), raiz(porta));
    Locator ultimoItem =
        ana.page()
            .getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Suas peças"))
            .getByRole(AriaRole.BUTTON)
            .last();

    // Do fim da página para cima: o Shift+Tab traz o item pela borda de cima, onde ficam o topo, a
    // cena e o bloco presos; só o scroll-padding-top o leva para baixo deles.
    ana.page().evaluate("() => window.scrollTo(0, document.documentElement.scrollHeight)");
    ana.botaoPronto().focus();
    voltarAte(ana.page(), ultimoItem);

    assertEquals("", ultimoItem.evaluate(COBERTO_PELO_QUE_FICA_PRESO));
  }

  private static void voltarAte(Page page, Locator alvo) {
    for (int i = 0; i < 40; i++) {
      page.keyboard().press("Shift+" + Navegador.TAB);
      if (alvo.evaluate("el => el === document.activeElement").equals(Boolean.TRUE)) {
        return;
      }
    }
    throw new AssertionError("O Shift+Tab não chegou ao alvo em 40 passos.");
  }
}
