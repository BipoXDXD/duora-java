package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A CSP libera só o script e o iframe do Turnstile (challenges.cloudflare.com), na casca de conta e
 * no terraço, sem afrouxar o resto da política.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CspTest {

  private static final String TURNSTILE = "https://challenges.cloudflare.com";

  @Autowired private MockMvc mockMvc;

  @ParameterizedTest
  @ValueSource(strings = {"/entrar", "/"})
  void scriptDoTurnstileLiberado(String caminho) throws Exception {
    assertThat(csp(caminho)).contains("script-src 'self' " + TURNSTILE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/entrar", "/"})
  void iframeDoTurnstileLiberado(String caminho) throws Exception {
    assertThat(csp(caminho)).contains("frame-src " + TURNSTILE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/entrar", "/"})
  void restoDaPoliticaMantido(String caminho) throws Exception {
    assertThat(csp(caminho))
        .contains("default-src 'self'", "object-src 'none'", "frame-ancestors 'none'");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/entrar", "/"})
  void nadaInseguro(String caminho) throws Exception {
    assertThat(csp(caminho)).doesNotContain("unsafe-");
  }

  private String csp(String caminho) throws Exception {
    String valor =
        mockMvc
            .perform(get(caminho))
            .andReturn()
            .getResponse()
            .getHeader("Content-Security-Policy");
    assertThat(valor).as("CSP de %s", caminho).isNotNull();
    return valor;
  }
}
