package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Quem chega sem assento vê a entrada sem barulho: o {@code GET /api/sala} sem cookie responde 204,
 * e nem o console nem a rede registram erro.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class PrimeiraCargaIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void semErroNoConsoleNemRespostaDeErro(Participantes participantes) {
    List<String> errosNoConsole = new ArrayList<>();
    List<String> respostasDeErro = new ArrayList<>();
    TerracoPagina ana =
        participantes.novo(page -> {
          page.onConsoleMessage(mensagem -> {
            if ("error".equals(mensagem.type())) {
              errosNoConsole.add(mensagem.text());
            }
          });
          page.onResponse(resposta -> {
            if (resposta.status() >= 400) {
              respostasDeErro.add(resposta.status() + " " + resposta.url());
            }
          });
        });
    Page page = ana.page();

    // O heading também vem do esqueleto estático: só depois da resposta da sala a entrada é do app.
    page.waitForResponse(
        resposta -> resposta.url().endsWith("/api/sala"), () -> ana.abrir(raiz(porta)));
    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Antes da primeira estrela")))
        .isVisible();
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertThat(errosNoConsole).isEmpty();
    assertThat(respostasDeErro).isEmpty();
  }
}
