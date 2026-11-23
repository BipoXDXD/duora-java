package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * A casca de conta na primeira carga: o Spring encaminha a rota ao index.html, o despacho baixa o
 * chunk {@code casca-de-conta} só fora do terraço e não toca no fragmento do link mágico.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class CascaDeContaIT {

  private static final String CHUNK_DA_CASCA = "/app/casca-de-conta";

  /**
   * Rota encaminhada que ainda não tem tela: a casca cai na {@code NaoEncontrada}. Quando a tela do
   * encarregado chegar, troque por outra rota sem tela ou apague o teste do axe.
   */
  private static final String ROTA_SEM_TELA = "encarregado";

  @Value("${local.server.port}")
  private int porta;

  @Test
  void linkDeConfirmarCarregaACascaSemPerderOFragmento(Participantes participantes) {
    Page page = participantes.novo().page();

    Response chunk =
        page.waitForResponse(
            resposta -> resposta.url().contains(CHUNK_DA_CASCA),
            () -> page.navigate(raiz(porta) + "entrar/confirmar#t=abc"));
    assertThat(chunk.status()).isEqualTo(200);
    assertThat(page.getByRole(AriaRole.MAIN)).isVisible();
    assertThat(page.locator("#raiz")).not().hasAttribute("aria-busy", "true");

    assertThat(page.url()).isEqualTo(raiz(porta) + "entrar/confirmar#t=abc");
  }

  /** O MockMvc só registra o forward; aqui o servidor de verdade o executa. */
  @Test
  void rotaEncaminhadaChegaSemCache(Participantes participantes) {
    Response resposta = participantes.novo().page().navigate(raiz(porta) + "conta");

    assertThat(resposta.status()).isEqualTo(200);
    assertThat(resposta.headerValue("cache-control")).isEqualTo("no-cache");
  }

  @Test
  void terracoNaoBaixaOChunkDaCasca(Participantes participantes) {
    List<String> pedidos = new ArrayList<>();
    TerracoPagina ana =
        participantes.novo(page -> page.onRequest(pedido -> pedidos.add(pedido.url())));
    Page page = ana.page();

    page.waitForResponse(
        resposta -> resposta.url().endsWith("/api/sala"), () -> ana.abrir(raiz(porta)));
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertThat(pedidos).isNotEmpty().noneMatch(url -> url.contains(CHUNK_DA_CASCA));
  }

  @Test
  void naoEncontradaSemViolacoes(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta) + ROTA_SEM_TELA).page();
    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Página não encontrada")))
        .isVisible();

    assertSemViolacoes(page, "página não encontrada da casca");
  }
}
