package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cada rota da casca de conta, aberta direto no navegador (link de e-mail, recarga), recebe o
 * index.html sem cache e com a CSP do app; só a /verificacao recebe a da prova de vida. O resto
 * continua 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RotasDoSpaTest {

  private static final Path CAMINHOS_DO_FRONT =
      Path.of("frontend", "src", "rotas", "caminhosDaCasca.ts");
  private static final Pattern LITERAL_DE_CAMINHO = Pattern.compile("\"(/[^\"]*)\"");

  @Autowired private MockMvc mockMvc;

  @ParameterizedTest
  @FieldSource("bipotheking.net.duora.web.RotasDoSpa#CAMINHOS")
  void cadaRotaDoSpaEncaminhaParaOIndex(String caminho) throws Exception {
    String csp =
        caminho.equals("/verificacao")
            ? CabecalhosDeSeguranca.CSP_VERIFICACAO
            : CabecalhosDeSeguranca.CSP;
    mockMvc
        .perform(get(caminho))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"))
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(header().string("Content-Security-Policy", csp));
  }

  @Test
  void confirmarComTokenNaQueryNaoTemEfeito() throws Exception {
    mockMvc
        .perform(get("/entrar/confirmar").queryParam("t", "abc"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void rotaDesconhecidaContinua404() throws Exception {
    mockMvc.perform(get("/nao-existe")).andExpect(status().isNotFound());
  }

  @Test
  void nenhumaRotaComecaPorApp() {
    assertThat(RotasDoSpa.CAMINHOS).noneMatch(caminho -> caminho.startsWith("/app/"));
  }

  @Test
  void rotasIguaisAsDoFront() throws IOException {
    Set<String> doFront =
        LITERAL_DE_CAMINHO
            .matcher(Files.readString(CAMINHOS_DO_FRONT))
            .results()
            .map(literal -> literal.group(1))
            .collect(Collectors.toSet());

    assertThat(doFront).as("literais de %s", CAMINHOS_DO_FRONT).isNotEmpty();
    assertThat(Set.copyOf(RotasDoSpa.CAMINHOS)).isEqualTo(doFront);
  }
}
