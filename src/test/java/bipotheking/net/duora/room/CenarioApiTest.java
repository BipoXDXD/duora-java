package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.CenarioMinimo;
import bipotheking.net.duora.game.DefinicaoDeAbertura;
import bipotheking.net.duora.game.Resultado;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Sala com cenário: o pedido escolhe o cenário e os ids seguem o cenário da sala. A sala {@code
 * minimo-abertura} cobre a abertura (janela de 5 s, máximo de 90 s).
 */
// O terraço conclui a abertura já na entrada, como na suíte e2e.
@SpringBootTest(properties = "duora.cenarios.abertura.maximo=PT0S")
@AutoConfigureMockMvc
class CenarioApiTest {

  private static final String PROBLEMA = "application/problem+json";

  @TestConfiguration
  static class ComCenarioMinimo {
    @Bean
    Cenario minimo() {
      return CenarioMinimo.CENARIO;
    }

    @Bean
    Cenario minimoComAbertura() {
      return CenarioMinimo.COM_ABERTURA;
    }

    /** Como o perfil e2e: máximo zero, a abertura conclui apagada já na entrada. */
    @Bean
    Cenario minimoComAberturaInstantanea() {
      return new Cenario(
          "minimo-abertura-zero",
          "Mínimo com abertura instantânea",
          List.of(CenarioMinimo.P1, CenarioMinimo.P2),
          List.of(CenarioMinimo.PEDRA, CenarioMinimo.FOLHA),
          1,
          Duration.ofSeconds(20),
          1,
          (composicao, cenario) -> new Resultado(Map.of("juntos", "sim")),
          Optional.of(
              new DefinicaoDeAbertura("acender-junto", Duration.ofSeconds(5), Duration.ZERO)));
    }

    @Bean
    @Primary
    RelogioDeTeste relogioDeTeste() {
      return new RelogioDeTeste(Instant.parse("2026-11-20T21:00:00Z"));
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private Salas salas;
  @Autowired private RelogioDeTeste relogio;

  /** Salas de outros testes expiram e deixam de contar no limite. */
  @BeforeEach
  void esgotarSalasAnteriores() {
    relogio.avancar(Duration.ofHours(1));
  }

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

  // Abertura

  @Test
  void aberturaPorHttpAcende() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    acao(ana, "/api/sala/abertura", "").andExpect(status().isOk());

    acao(bruno, "/api/sala/abertura", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abertura.acesa").value(true))
        .andExpect(jsonPath("$.abertura.ativa").value(false));
  }

  @Test
  void colocarDuranteAberturaResponde409ComVisao() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    entrar(ana.convite(), "Bruno");

    colocar(ana, "pedra", "P1", 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ABERTURA_EM_ANDAMENTO"))
        .andExpect(jsonPath("$.visao.abertura.ativa").value(true));
  }

  @Test
  void visaoDaAberturaPorLado() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    Pessoa bruno = entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/abertura", "")
        .andExpect(jsonPath("$.abertura.voceTocou").value(true))
        .andExpect(jsonPath("$.abertura.outroTocou").value(false));
    consultar(bruno)
        .andExpect(jsonPath("$.abertura.ativa").value(true))
        .andExpect(jsonPath("$.abertura.voceTocou").value(false))
        .andExpect(jsonPath("$.abertura.outroTocou").value(true));
  }

  @Test
  void aberturaConcluiPeloTempoNaConsulta() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    String etag = etag(consultar(ana).andExpect(jsonPath("$.abertura.ativa").value(true)));
    relogio.avancar(Duration.ofSeconds(89));
    consultar(bruno).andExpect(jsonPath("$.abertura.ativa").value(true));
    relogio.avancar(Duration.ofSeconds(1));

    consultar(ana, etag)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, not(etag)))
        .andExpect(jsonPath("$.abertura.ativa").value(false))
        .andExpect(jsonPath("$.abertura.acesa").value(false));
  }

  @Test
  void colocarDepoisDoMaximoSemConsultaEAceito() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    entrar(ana.convite(), "Bruno");
    relogio.avancar(Duration.ofSeconds(90));

    colocar(ana, "pedra", "P1", 0)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abertura.ativa").value(false))
        .andExpect(jsonPath("$.pecas[0].lugar").value("P1"));
  }

  @Test
  void avancarAberturaNaoRenovaExpiracao() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura");
    entrar(ana.convite(), "Bruno");
    relogio.avancar(Duration.ofMinutes(29));
    consultar(ana).andExpect(status().isOk()).andExpect(jsonPath("$.abertura.ativa").value(false));
    relogio.avancar(Duration.ofMinutes(1));

    consultar(ana).andExpect(status().isGone()).andExpect(jsonPath("$.motivo").value("EXPIROU"));
  }

  @Test
  void aberturaComMaximoZeroConcluiNaRespostaDaEntrada() throws Exception {
    Pessoa ana = criar("Ana", "minimo-abertura-zero");

    entrarRequisicao(ana.convite(), "Bruno")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abertura.ativa").value(false))
        .andExpect(jsonPath("$.abertura.acesa").value(false));
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

  private ResultActions consultar(Pessoa pessoa) throws Exception {
    return mvc.perform(get("/api/sala").cookie(new Cookie(CookieAssento.NOME, pessoa.token())));
  }

  private ResultActions consultar(Pessoa pessoa, String etag) throws Exception {
    return mvc.perform(
        get("/api/sala")
            .cookie(new Cookie(CookieAssento.NOME, pessoa.token()))
            .header(HttpHeaders.IF_NONE_MATCH, etag));
  }

  private static String etag(ResultActions resposta) {
    return resposta.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
  }

  private static Pessoa pessoa(MvcResult resultado) throws Exception {
    String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith(CookieAssento.NOME + "=");
    String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    return new Pessoa(token, resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
