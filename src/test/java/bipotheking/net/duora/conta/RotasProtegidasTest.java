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
        .contains("/api/conta/sair", "/api/conta/exclusao", "/api/teste/so-ativa");
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

  /**
   * As rotas reais que a lista pública deixa passar. Uma rota nova sob um padrão com {@code **}
   * (como {@code /api/sala/**} ou {@code /api/legal/**}) fica pública sem ninguém decidir: este
   * teste obriga a decisão a aparecer aqui.
   */
  @Test
  void rotasPublicasReaisSaoExatamenteAsDecididas() {
    assertThat(rotasDaApi().stream().filter(Rota::publica).map(Rota::toString))
        .containsExactlyInAnyOrder(
            "GET /api/conta",
            "POST /api/conta/entrar",
            "POST /api/conta/confirmar",
            "GET /api/conta/configuracao",
            "POST /api/salas",
            "POST /api/salas/entrar",
            "GET /api/sala",
            "POST /api/sala/pecas",
            "POST /api/sala/sinal",
            "POST /api/sala/pronto",
            "POST /api/sala/ajustar",
            "POST /api/sala/ajustar/recusar",
            "POST /api/sala/abertura",
            "POST /api/sala/sair",
            "GET /api/legal/versoes",
            "GET /api/legal/{documento}",
            "POST /api/denuncias-publicas");
  }

  /**
   * Variações de caminho que um casamento por texto cru confundiria com uma rota pública ou
   * deixaria fora de {@code /api/**}. Sem sessão, nenhuma chega a um handler protegido.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/conta/",
        "/api//conta/sair",
        "/api/conta/%73air",
        "/api/legal/../conta",
        "/api/legal/../conta/sair",
        "/API/conta",
        "//api/conta",
        "/api/teste/so-ativa;x"
      })
  void caminhoDisfarcadoSemSessaoNuncaEhAtendido(String caminho) throws Exception {
    for (HttpMethod metodo : List.of(HttpMethod.GET, HttpMethod.POST)) {
      MockHttpServletResponse resposta =
          mvc.perform(
                  request(metodo, "/")
                      .header(MesmaOrigem.CABECALHO, "1")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}")
                      .with(requisicao -> {
                        requisicao.setRequestURI(caminho);
                        return requisicao;
                      }))
              .andReturn()
              .getResponse();
      assertThat(resposta.getStatus())
          .as(metodo + " " + caminho)
          .matches(codigo -> codigo < 200 || codigo >= 300, "fora de 2xx");
    }
  }

  @Test
  void rotaProtegidaComParametroDeSegmentoRecebe401() throws Exception {
    mvc.perform(
            get("/")
                .with(requisicao -> {
                  requisicao.setRequestURI("/api/teste/so-ativa;x");
                  return requisicao;
                }))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value(NAO_AUTENTICADO));
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
