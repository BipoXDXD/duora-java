package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.CenarioMinimo;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Sala com cenário: o pedido escolhe o cenário e os ids seguem o cenário da sala. */
@SpringBootTest
@AutoConfigureMockMvc
class CenarioApiTest {

  private static final String PROBLEMA = "application/problem+json";

  @TestConfiguration
  static class ComCenarioMinimo {
    @Bean
    Cenario minimo() {
      return CenarioMinimo.CENARIO;
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private Salas salas;

  @Test
  void criarSemCenarioUsaOTerraco() throws Exception {
    criarRequisicao("Ana", null)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cenario").value("terraco"))
        .andExpect(jsonPath("$.pecas", hasSize(6)));
  }

  @Test
  void criarComCenarioDesconhecidoRecusaSemCriarSala() throws Exception {
    int antes = salas.total();

    criarRequisicao("Ana", "vulcao")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentType(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("CENARIO_INVALIDO"));

    assertThat(salas.total()).isEqualTo(antes);
  }

  @Test
  void cenarioForaDoPadraoERecusado() throws Exception {
    criarRequisicao("Ana", "../terraco")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CENARIO_INVALIDO"));
  }

  @Test
  void apelidoInvalidoVemAntesDoCenario() throws Exception {
    criarRequisicao("", "vulcao")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("APELIDO_INVALIDO"));
  }

  @Test
  void cenarioMinimoDePontaAPonta() throws Exception {
    Pessoa ana =
        pessoa(
            criarRequisicao("Ana", "minimo")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cenario").value("minimo"))
                .andExpect(jsonPath("$.pecas[*].peca", contains("pedra", "folha")))
                .andExpect(jsonPath("$.confirmacao.faltam").value(2))
                .andReturn());
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(ana, "pedra", "P1", 0).andExpect(status().isOk());
    colocar(bruno, "folha", "P2", 1).andExpect(status().isOk());
    acao(ana, "/api/sala/pronto", "{\"versao\":2,\"pronto\":true}").andExpect(status().isOk());

    String corpo =
        acao(bruno, "/api/sala/pronto", "{\"versao\":2,\"pronto\":true}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fase").value("REVELADO"))
            .andExpect(jsonPath("$.resultado.juntos").value("sim"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    Set<String> chaves = JsonPath.read(corpo, "$.resultado.keys()");
    assertThat(chaves).containsExactly("juntos");
  }

  @Test
  void pecaDoMinimoNaSalaDoTerracoEPecaInvalida() throws Exception {
    Pessoa ana = criar("Ana", null);
    entrar(ana.convite(), "Bruno");

    colocar(ana, "pedra", "C1", 0)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PECA_INVALIDA"));
  }

  @Test
  void pecaDoTerracoNaSalaDoMinimoEPecaInvalida() throws Exception {
    Pessoa ana = criar("Ana", "minimo");
    entrar(ana.convite(), "Bruno");

    colocar(ana, "telescopio", "P1", 0)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PECA_INVALIDA"));
  }

  @Test
  void lugarDeOutroCenarioELugarInvalido() throws Exception {
    Pessoa ana = criar("Ana", "minimo");
    entrar(ana.convite(), "Bruno");

    colocar(ana, "pedra", "C1", 0)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LUGAR_INVALIDO"))
        .andExpect(jsonPath("$.detail").value("Lugar desconhecido."));
  }

  @Test
  void sinalComPecaDeOutroCenarioEPecaInvalida() throws Exception {
    Pessoa ana = criar("Ana", "minimo");
    entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/sinal", "{\"peca\":\"banco\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PECA_INVALIDA"));
  }

  @Test
  void resultadoDoTerracoSaiNaOrdemDoContrato() throws Exception {
    Pessoa ana = criar("Ana", null);
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());
    colocar(bruno, "banco", "C2", 1).andExpect(status().isOk());
    colocar(ana, "manta", "L1", 2).andExpect(status().isOk());
    colocar(bruno, "lanterna", "R1", 3).andExpect(status().isOk());
    acao(ana, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}").andExpect(status().isOk());

    String corpo =
        acao(bruno, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}")
            .andExpect(jsonPath("$.fase").value("REVELADO"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    int secundariaA = corpo.indexOf("\"secundariaA\"");
    int secundariaB = corpo.indexOf("\"secundariaB\"");
    int ladoTelescopio = corpo.indexOf("\"ladoTelescopio\"");
    assertThat(secundariaA).isNotNegative().isLessThan(secundariaB);
    assertThat(secundariaB).isLessThan(ladoTelescopio);
  }

  // Apoio: copiado de SalaApiTest, com o cenário opcional na criação. Dívida aceita: extrair um
  // apoio comum quando um terceiro teste de API precisar destes passos.

  private record Pessoa(String token, String corpo) {
    String convite() {
      return JsonPath.read(corpo, "$.convite");
    }
  }

  private Pessoa criar(String apelido, @Nullable String cenario) throws Exception {
    return pessoa(criarRequisicao(apelido, cenario).andExpect(status().isCreated()).andReturn());
  }

  private ResultActions criarRequisicao(String apelido, @Nullable String cenario) throws Exception {
    String campoCenario = cenario == null ? "" : ",\"cenario\":\"" + cenario + "\"";
    return mvc.perform(
        post("/api/salas")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"apelido\":\"" + apelido + "\"" + campoCenario + "}"));
  }

  private Pessoa entrar(String convite, String apelido) throws Exception {
    return pessoa(entrarRequisicao(convite, apelido).andExpect(status().isOk()).andReturn());
  }

  private ResultActions entrarRequisicao(String convite, String apelido) throws Exception {
    return mvc.perform(
        post("/api/salas/entrar")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"convite\":\"" + convite + "\",\"apelido\":\"" + apelido + "\"}"));
  }

  private ResultActions colocar(Pessoa pessoa, String peca, String lugar, long versao)
      throws Exception {
    return acao(
        pessoa,
        "/api/sala/pecas",
        "{\"peca\":\"%s\",\"lugar\":\"%s\",\"versaoEsperada\":%d}".formatted(peca, lugar, versao));
  }

  private ResultActions acao(Pessoa pessoa, String caminho, String corpo) throws Exception {
    var requisicao =
        post(caminho)
            .header(MesmaOrigem.CABECALHO, "1")
            .cookie(new Cookie(CookieAssento.NOME, pessoa.token()));
    if (!corpo.isEmpty()) {
      requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }
    return mvc.perform(requisicao);
  }

  private static Pessoa pessoa(MvcResult resultado) throws Exception {
    String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith(CookieAssento.NOME + "=");
    String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    return new Pessoa(token, resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
