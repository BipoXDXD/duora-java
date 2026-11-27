package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A rota /verificacao, e só ela, recebe a CSP e a Permissions-Policy da prova de vida: WASM do
 * detector, CDN e streaming da Rekognition e a câmera. Os valores ficam em literais aqui, nunca
 * lidos de {@link CabecalhosDeSeguranca}, para uma mudança na constante não passar calada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CspDaVerificacaoTest {

  private static final String VERIFICACAO = "/verificacao";

  @Autowired private MockMvc mockMvc;

  @Test
  void verificacaoTrazAPoliticaPropriaInteira() throws Exception {
    assertThat(resposta(VERIFICACAO).getHeader("Content-Security-Policy"))
        .isEqualTo(
            "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; "
                + "img-src 'self'; font-src 'self'; media-src 'self'; "
                + "connect-src 'self' blob: https://cdn.liveness.rekognition.amazonaws.com "
                + "wss://streaming-rekognition.us-east-1.amazonaws.com; manifest-src 'self'; "
                + "worker-src 'none'; base-uri 'none'; form-action 'self'; "
                + "frame-ancestors 'none'; object-src 'none'; "
                + "require-trusted-types-for 'script'; trusted-types 'none'");
  }

  @Test
  void verificacaoTrazUmSoCabecalhoDeCsp() throws Exception {
    assertThat(resposta(VERIFICACAO).getHeaders("Content-Security-Policy")).hasSize(1);
  }

  @Test
  void verificacaoLiberaSoACamera() throws Exception {
    assertThat(resposta(VERIFICACAO).getHeader("Permissions-Policy"))
        .isEqualTo("camera=(self), microphone=()");
  }

  @Test
  void origensExternasSaoSoOCdnEOStreaming() throws Exception {
    String csp = resposta(VERIFICACAO).getHeader("Content-Security-Policy");
    assertThat(csp).isNotNull();

    List<String> externas =
        Arrays.stream(csp.split(";"))
            .map(String::strip)
            .filter(diretiva -> diretiva.startsWith("connect-src "))
            .flatMap(diretiva -> Arrays.stream(diretiva.split(" ")))
            .filter(fonte -> fonte.startsWith("https://") || fonte.startsWith("wss://"))
            .toList();

    assertThat(externas)
        .containsExactlyInAnyOrder(
            "https://cdn.liveness.rekognition.amazonaws.com",
            "wss://streaming-rekognition.us-east-1.amazonaws.com");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/entrar",
        "/conta",
        "/api/conta",
        "/verificacao/",
        "/verificacao.html",
        "/app/qualquer.js"
      })
  void outraRotaFicaComAPoliticaDoApp(String caminho) throws Exception {
    MockHttpServletResponse resposta = resposta(caminho);

    assertThat(resposta.getHeader("Content-Security-Policy"))
        .isEqualTo(CabecalhosDeSeguranca.CSP)
        .doesNotContain("wasm-unsafe-eval", "amazonaws");
    assertThat(resposta.getHeader("Permissions-Policy")).isEqualTo("camera=(), microphone=()");
  }

  @Test
  void verificacaoServeOIndexSemCache() throws Exception {
    mockMvc
        .perform(get(VERIFICACAO))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"))
        .andExpect(header().string("Cache-Control", "no-cache"));
  }

  private MockHttpServletResponse resposta(String caminho) throws Exception {
    return mockMvc.perform(get(caminho)).andReturn().getResponse();
  }
}
