package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O terraço segue anônimo com a negação por padrão: sem {@code duora_sessao} e com um cookie de
 * sessão inválido, as rotas de {@code room/} respondem igual e nunca mexem no cookie de sessão.
 */
class TerracoAnonimoTest extends BancoDeTeste {

  private static final String SESSAO = "duora_sessao";
  private static final String ASSENTO = "duora_assento";
  private static final Duration ABERTURA_DO_TERRACO = Duration.ofSeconds(90);

  /** Salas de outros testes deste contexto expiram e deixam de contar no limite de salas. */
  @BeforeEach
  void esgotarSalasAnteriores() {
    relogio.avancar(Duration.ofHours(1));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "invalido")
  void consultarSemAssentoResponde204(@Nullable String sessao) throws Exception {
    MockHttpServletResponse resposta =
        mvc.perform(comSessao(get("/api/sala"), sessao))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andReturn()
            .getResponse();

    assertThat(cookiesDeSessao(resposta)).isEmpty();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "invalido")
  void criarEntrarColocarESairSemConta(@Nullable String sessao) throws Exception {
    List<MockHttpServletResponse> respostas = new ArrayList<>();

    MockHttpServletResponse criada =
        mvc.perform(mutacao(post("/api/salas"), sessao).content("{\"apelido\":\"Ana\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    respostas.add(criada);
    String convite = JsonPath.read(corpo(criada), "$.convite");

    MockHttpServletResponse entrada =
        mvc.perform(
                mutacao(post("/api/salas/entrar"), sessao)
                    .content("{\"convite\":\"%s\",\"apelido\":\"Bruno\"}".formatted(convite)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    respostas.add(entrada);
    Cookie assentoDeBruno = assento(entrada);

    relogio.avancar(ABERTURA_DO_TERRACO);
    MockHttpServletResponse consulta =
        mvc.perform(comSessao(get("/api/sala"), sessao).cookie(assentoDeBruno))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    respostas.add(consulta);
    int versao = JsonPath.read(corpo(consulta), "$.versao");

    respostas.add(
        mvc.perform(
                mutacao(post("/api/sala/pecas"), sessao)
                    .cookie(assentoDeBruno)
                    .content(
                        "{\"peca\":\"banco\",\"lugar\":\"C1\",\"versaoEsperada\":%d}"
                            .formatted(versao)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse());
    respostas.add(
        mvc.perform(mutacao(post("/api/sala/sair"), sessao).cookie(assentoDeBruno))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse());

    assertThat(criada.getHeader(HttpHeaders.SET_COOKIE)).startsWith(ASSENTO + "=");
    assertThat(respostas).allSatisfy(resposta -> assertThat(cookiesDeSessao(resposta)).isEmpty());
  }

  private static MockHttpServletRequestBuilder mutacao(
      MockHttpServletRequestBuilder requisicao, @Nullable String sessao) {
    return comSessao(requisicao, sessao)
        .header(MesmaOrigem.CABECALHO, "1")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder comSessao(
      MockHttpServletRequestBuilder requisicao, @Nullable String sessao) {
    return sessao == null ? requisicao : requisicao.cookie(new Cookie(SESSAO, sessao));
  }

  private static Cookie assento(MockHttpServletResponse resposta) {
    String cookie = resposta.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith(ASSENTO + "=");
    return new Cookie(ASSENTO, cookie.substring(ASSENTO.length() + 1, cookie.indexOf(';')));
  }

  private static List<String> cookiesDeSessao(MockHttpServletResponse resposta) {
    return resposta.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(cookie -> cookie.startsWith(SESSAO + "="))
        .toList();
  }

  private static String corpo(MockHttpServletResponse resposta) throws Exception {
    return resposta.getContentAsString(StandardCharsets.UTF_8);
  }
}
