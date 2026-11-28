package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Os filtros atrás do proxy: {@code Host} e {@code Origin} chegam com o domínio público, e a porta
 * local (8080) não bate com a da URL, porque o TLS termina no Traefik.
 */
class EnderecoPublicoNosFiltrosTest {

  private static final int PORTA_LOCAL = 8080;

  private final JsonMapper json = JsonMapper.builder().build();
  private final EnderecoPublico publico = new EnderecoPublico("https://meetduora.com");
  private final HostLocal host = new HostLocal(json, publico);
  private final MesmaOrigem origem = new MesmaOrigem(json, publico);

  @Test
  void hostPublicoEhAceito() throws Exception {
    MockHttpServletResponse resposta =
        filtrarHost(requisicao("GET", "/index.html", "meetduora.com"));

    assertThat(resposta.getStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"evil.com", "meetduora.com.evil.com", "evilmeetduora.com", "meetduora.com:8080"})
  void hostForaDoPublicoRecebeHostRecusado(String cabecalho) throws Exception {
    MockHttpServletResponse resposta = filtrarHost(requisicao("GET", "/index.html", cabecalho));

    assertThat(resposta.getStatus()).isEqualTo(421);
    assertThat(resposta.getContentAsString()).contains("\"codigo\":\"HOST_RECUSADO\"");
  }

  @Test
  void hostDeLoopbackNaPortaLocalContinuaAceito() throws Exception {
    MockHttpServletResponse resposta =
        filtrarHost(requisicao("GET", "/actuator/health", "127.0.0.1:8080"));

    assertThat(resposta.getStatus()).isEqualTo(200);
  }

  @Test
  void origemPublicaEhAceitaNumaMutacao() throws Exception {
    MockHttpServletResponse resposta = filtrarOrigem(mutacaoCom("https://meetduora.com"));

    assertThat(resposta.getStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://evil.com",
        "https://meetduora.com.evil.com",
        "https://evilmeetduora.com",
        "http://meetduora.com",
        "null"
      })
  void origemForaDaPublicaRecebeOrigemRecusada(String cabecalho) throws Exception {
    MockHttpServletResponse resposta = filtrarOrigem(mutacaoCom(cabecalho));

    assertThat(resposta.getStatus()).isEqualTo(403);
    assertThat(resposta.getContentAsString()).contains("\"codigo\":\"ORIGEM_RECUSADA\"");
  }

  @Test
  void origemDeLoopbackNaPortaLocalContinuaAceita() throws Exception {
    MockHttpServletResponse resposta = filtrarOrigem(mutacaoCom("http://127.0.0.1:8080"));

    assertThat(resposta.getStatus()).isEqualTo(200);
  }

  private static MockHttpServletRequest requisicao(String metodo, String caminho, String host) {
    MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, caminho);
    requisicao.addHeader(HttpHeaders.HOST, host);
    requisicao.setLocalPort(PORTA_LOCAL);
    return requisicao;
  }

  private static MockHttpServletRequest mutacaoCom(String cabecalhoOrigin) {
    MockHttpServletRequest requisicao = requisicao("POST", "/api/salas", "meetduora.com");
    requisicao.addHeader(MesmaOrigem.CABECALHO, "1");
    requisicao.addHeader(HttpHeaders.ORIGIN, cabecalhoOrigin);
    return requisicao;
  }

  private MockHttpServletResponse filtrarHost(MockHttpServletRequest requisicao)
      throws ServletException, IOException {
    MockHttpServletResponse resposta = new MockHttpServletResponse();
    host.doFilter(requisicao, resposta, new MockFilterChain());
    return resposta;
  }

  private MockHttpServletResponse filtrarOrigem(MockHttpServletRequest requisicao)
      throws ServletException, IOException {
    MockHttpServletResponse resposta = new MockHttpServletResponse();
    origem.doFilter(requisicao, resposta, new MockFilterChain());
    return resposta;
  }
}
