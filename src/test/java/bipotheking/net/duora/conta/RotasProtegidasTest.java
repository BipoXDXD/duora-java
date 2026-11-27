package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** Negação por padrão: sem sessão, só as rotas de {@link RotasPublicas} passam. */
class RotasProtegidasTest extends BancoDeTeste {

  private static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";
  private static final List<PathPattern> PUBLICAS =
      RotasPublicas.PADROES.stream().map(PathPatternParser.defaultInstance::parse).toList();

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mapeamentos;

  @Test
  void rotaProtegidaSemSessaoRecebe401() throws Exception {
    List<Rota> protegidas = rotasDaApi().stream().filter(rota -> !rota.publica()).toList();

    assertThat(protegidas)
        .extracting(Rota::caminho)
        .contains("/api/conta/sair", "/api/teste/so-ativa");
    for (Rota rota : protegidas) {
      MockHttpServletResponse resposta = chamarSemSessao(rota);
      assertThat(resposta.getStatus()).as(rota.toString()).isEqualTo(401);
      assertThat(corpo(resposta)).as(rota.toString()).contains(NAO_AUTENTICADO);
    }
  }

  @Test
  void nenhumaRotaPublicaRecebe401NaoAutenticado() throws Exception {
    List<Rota> publicas = rotasDaApi().stream().filter(Rota::publica).toList();

    assertThat(publicas).extracting(Rota::caminho).contains("/api/conta", "/api/sala");
    for (Rota rota : publicas) {
      assertThat(corpo(chamarSemSessao(rota))).as(rota.toString()).doesNotContain(NAO_AUTENTICADO);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/qualquer", "/api/conta/qualquer"})
  void rotaInexistenteRecebe401(String caminho) throws Exception {
    mvc.perform(get(caminho))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value(NAO_AUTENTICADO));
  }

  @Test
  void getContaEhPublicaSoNoCaminhoExato() throws Exception {
    mvc.perform(get("/api/conta")).andExpect(status().isNoContent());
    for (String caminho : List.of("/api/conta/sair", "/api/conta/cadastro")) {
      mvc.perform(
              post(caminho)
                  .header(MesmaOrigem.CABECALHO, "1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.codigo").value(NAO_AUTENTICADO));
    }
  }

  /** Cada par (método, padrão) de {@code /api/**}, com as variáveis do caminho trocadas por x. */
  private List<Rota> rotasDaApi() {
    return mapeamentos.getHandlerMethods().keySet().stream()
        .flatMap(info ->
            info.getPatternValues().stream()
                .filter(padrao -> padrao.startsWith("/api/"))
                .flatMap(padrao -> metodos(info).stream().map(m -> new Rota(m, padrao))))
        .toList();
  }

  private static Set<HttpMethod> metodos(RequestMappingInfo info) {
    Set<RequestMethod> declarados = info.getMethodsCondition().getMethods();
    if (declarados.isEmpty()) {
      return Set.of(HttpMethod.GET);
    }
    return declarados.stream().map(RequestMethod::asHttpMethod).collect(Collectors.toSet());
  }

  private MockHttpServletResponse chamarSemSessao(Rota rota) throws Exception {
    return mvc.perform(
            request(rota.metodo(), rota.caminho())
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andReturn()
        .getResponse();
  }

  private static String corpo(MockHttpServletResponse resposta) throws Exception {
    return resposta.getContentAsString(StandardCharsets.UTF_8);
  }

  private record Rota(HttpMethod metodo, String padrao) {

    String caminho() {
      return padrao.replaceAll("\\{[^}]+}", "x");
    }

    boolean publica() {
      PathContainer caminho = PathContainer.parsePath(caminho());
      return PUBLICAS.stream().anyMatch(publico -> publico.matches(caminho));
    }

    @Override
    public String toString() {
      return metodo + " " + padrao;
    }
  }
}
