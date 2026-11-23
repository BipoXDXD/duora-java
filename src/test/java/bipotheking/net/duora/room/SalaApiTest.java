package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

// O terraço conclui a abertura apagada já na entrada, como na suíte e2e: a abertura por HTTP fica
// com o CenarioApiTest (minimo-abertura) e o AberturaIT.
@SpringBootTest(properties = "duora.cenarios.abertura.maximo=PT0S")
@AutoConfigureMockMvc
@Import(SalaApiTest.RelogioControlavel.class)
@ExtendWith(OutputCaptureExtension.class)
class SalaApiTest {

  private static final String PROBLEMA = "application/problem+json";

  @TestConfiguration
  static class RelogioControlavel {
    @Bean
    @Primary
    RelogioDeTeste relogioDeTeste() {
      return new RelogioDeTeste(Instant.parse("2026-11-20T21:00:00Z"));
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private RelogioDeTeste relogio;
  @Autowired private Varredor varredor;

  /** Salas de outros testes expiram e deixam de contar no limite. */
  @BeforeEach
  void esgotarSalasAnteriores() {
    relogio.avancar(Duration.ofHours(1));
  }

  // Criar, entrar e consultar

  @Test
  void criarSalaDevolve201ComAVisaoDeAEOConvite() throws Exception {
    criarRequisicao("Ana")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fase").value("AGUARDANDO_PAR"))
        .andExpect(jsonPath("$.versao").value(0))
        .andExpect(jsonPath("$.voce.assento").value("A"))
        .andExpect(jsonPath("$.voce.nome").value("Ana"))
        .andExpect(jsonPath("$.parceiro").value(nullValue()))
        .andExpect(jsonPath("$.convite").value(matchesPattern("[A-Za-z0-9_-]{22}")))
        .andExpect(jsonPath("$.pecas", hasSize(6)))
        .andExpect(jsonPath("$.confirmacao.faltam").value(4))
        .andExpect(jsonPath("$.sinal").value(nullValue()))
        .andExpect(jsonPath("$.resultado").value(nullValue()));
  }

  @Test
  void visaoTemExatamenteOsCamposDoContrato() throws Exception {
    Pessoa ana = criar("Ana");
    String corpo = entrar(ana.convite(), "Bruno").corpo();

    Set<String> chaves = JsonPath.read(corpo, "$.keys()");
    Set<String> pecaDoParceiro = JsonPath.read(corpo, "$.pecas[0].keys()");
    Set<String> pecaPropria = JsonPath.read(corpo, "$.pecas[3].keys()");

    assertThat(JsonPath.<String>read(corpo, "$.pecas[0].peca")).isEqualTo("telescopio");
    assertThat(JsonPath.<String>read(corpo, "$.pecas[3].peca")).isEqualTo("banco");
    assertThat(chaves)
        .containsExactlyInAnyOrder(
            "cenario",
            "fase",
            "versao",
            "voce",
            "parceiro",
            "convite",
            "pecas",
            "prontos",
            "confirmacao",
            "sinal",
            "resultado",
            "ajuste",
            "abertura",
            "conviteDaSala");
    assertThat(pecaPropria)
        .containsExactlyInAnyOrder("peca", "assento", "lugar", "lugaresPermitidos", "bloqueio");
    assertThat(pecaDoParceiro)
        .containsExactlyInAnyOrder("peca", "assento", "lugar", "destinosSugeriveis");
  }

  @Test
  void cookieDeAssentoEhHttpOnlySameSiteStrictRestritoAApi() throws Exception {
    criarRequisicao("Ana")
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("duora_assento=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")));
  }

  @Test
  void entrarComConviteDevolveAVisaoDeB() throws Exception {
    Pessoa ana = criar("Ana");

    entrarRequisicao(ana.convite(), "Bruno")
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(jsonPath("$.fase").value("MONTANDO"))
        .andExpect(jsonPath("$.voce.assento").value("B"))
        .andExpect(jsonPath("$.voce.nome").value("Bruno"))
        .andExpect(jsonPath("$.parceiro.nome").value("Ana"))
        .andExpect(jsonPath("$.parceiro.conectado").value(true))
        .andExpect(jsonPath("$.convite").value(nullValue()));
  }

  @Test
  void consultarComCookieDevolveAVisaoDeQuemPede() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    consultar(ana)
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.ETAG))
        .andExpect(jsonPath("$.voce.nome").value("Ana"))
        .andExpect(jsonPath("$.parceiro.nome").value("Bruno"))
        .andExpect(jsonPath("$.convite").value(nullValue()));
  }

  @Test
  void obterSalaSemCookieRespondeSemConteudo() throws Exception {
    mvc.perform(get("/api/sala"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist(HttpHeaders.ETAG))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
  }

  @Test
  void obterSalaComCookieVazioRespondeSemConteudo() throws Exception {
    mvc.perform(get("/api/sala").cookie(new Cookie(CookieAssento.NOME, "")))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist(HttpHeaders.ETAG))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/sala/pecas",
    "POST, /api/sala/sinal",
    "POST, /api/sala/pronto",
    "POST, /api/sala/ajustar",
    "POST, /api/sala/ajustar/recusar",
    "POST, /api/sala/abertura",
    "POST, /api/sala/sair"
  })
  void mutacaoSemCookieContinua401(String metodo, String caminho) throws Exception {
    mvc.perform(
            request(HttpMethod.valueOf(metodo), caminho)
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("SEM_ASSENTO"));
  }

  @Test
  void tokenDesconhecidoNaoEncontraSala() throws Exception {
    consultar(new Pessoa("desconhecido", ""))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SALA_INEXISTENTE"));
  }

  @Test
  void conviteJaUsadoNaoValeParaUmaTerceiraPessoa() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    entrarRequisicao(ana.convite(), "Carla")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void conviteDesconhecidoEhInvalido() throws Exception {
    entrarRequisicao("conviteQueNaoExiste", "Bruno")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"));
  }

  @Test
  void conviteReutilizadoDepoisQueASalaEncerraEhInvalido() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    acao(ana, "/api/sala/sair", "").andExpect(status().isNoContent());

    entrarRequisicao(ana.convite(), "Carla")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("CONVITE_INVALIDO"));
  }

  // Regras pela API

  @Test
  void mexerEmPecaAlheiaEhProibidoSemExporOOutro() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");

    String corpo =
        acao(
                bruno,
                "/api/sala/pecas",
                "{\"peca\":\"telescopio\",\"lugar\":\"C1\",\"versaoEsperada\":0}")
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
            .andExpect(jsonPath("$.codigo").value("PECA_ALHEIA"))
            .andExpect(jsonPath("$.visao.voce.assento").value("B"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(corpo).doesNotContain(ana.token(), bruno.token(), ana.convite());
    consultar(ana).andExpect(jsonPath("$.versao").value(0));
  }

  @Test
  void lugarOcupadoTrazAVisaoEONomeDeQuemUsou() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(bruno, "banco", "C1", 0).andExpect(status().isOk());

    colocar(ana, "telescopio", "C1", 1)
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Lugar ocupado"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.codigo").value("LUGAR_OCUPADO"))
        .andExpect(jsonPath("$.detail").value("Bruno acabou de usar este lugar."))
        .andExpect(jsonPath("$.visao.versao").value(1))
        .andExpect(jsonPath("$.visao.pecas[?(@.peca == 'banco')].lugar").value("C1"));
  }

  @Test
  void versaoObsoletaTrazAVisaoAtual() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(bruno, "banco", "C1", 0).andExpect(status().isOk());

    colocar(ana, "telescopio", "C2", 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERSAO_OBSOLETA"))
        .andExpect(jsonPath("$.visao.versao").value(1));
  }

  @Test
  void colocarNaEsperaPeloParEhForaDeFase() throws Exception {
    Pessoa ana = criar("Ana");

    colocar(ana, "telescopio", "C1", 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("FASE_INVALIDA"));
  }

  @Test
  void segundaPecaAntesDoParceiroMostraBloqueio() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());

    colocar(ana, "manta", "L1", 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ESPERE_PRIMEIRA_PECA"))
        .andExpect(
            jsonPath("$.visao.pecas[?(@.peca == 'manta')].bloqueio").value("ESPERE_PRIMEIRA_PECA"));
  }

  @Test
  void retirarComLugarNuloDevolvePecaAoInventario() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());

    acao(ana, "/api/sala/pecas", "{\"peca\":\"telescopio\",\"lugar\":null,\"versaoEsperada\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versao").value(2))
        .andExpect(jsonPath("$.pecas[0].peca").value("telescopio"))
        .andExpect(jsonPath("$.pecas[0].lugar").value(nullValue()));
  }

  @Test
  void jornadaCompletaAteARevelacaoEAjusteDeNovo() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());
    colocar(bruno, "banco", "C2", 1).andExpect(status().isOk());
    colocar(ana, "manta", "L1", 2).andExpect(status().isOk());
    colocar(bruno, "lanterna", "R1", 3)
        .andExpect(jsonPath("$.confirmacao.podeConfirmar").value(true))
        .andExpect(jsonPath("$.confirmacao.faltam").value(0));
    acao(ana, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}")
        .andExpect(jsonPath("$.fase").value("MONTANDO"))
        .andExpect(jsonPath("$.prontos.voce").value(true));

    acao(bruno, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.resultado.secundariaA").value("manta"))
        .andExpect(jsonPath("$.resultado.secundariaB").value("lanterna"))
        .andExpect(jsonPath("$.resultado.ladoTelescopio").value("esquerdo"));
    acao(ana, "/api/sala/ajustar", "").andExpect(status().isOk());
    acao(bruno, "/api/sala/ajustar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("MONTANDO"))
        .andExpect(jsonPath("$.versao").value(5))
        .andExpect(jsonPath("$.prontos.voce").value(false))
        .andExpect(jsonPath("$.resultado").value(nullValue()));
  }

  @Test
  void pedidoDeAjusteApareceNaVisaoDosDois() throws Exception {
    Dupla dupla = revelada();

    acao(dupla.ana(), "/api/sala/ajustar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.ajuste.deAssento").value("A"))
        .andExpect(jsonPath("$.versao").value(4));

    consultar(dupla.bruno())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.ajuste.deAssento").value("A"))
        .andExpect(jsonPath("$.versao").value(4));
  }

  @Test
  void etagMudaComOPedidoDeAjuste() throws Exception {
    Dupla dupla = revelada();
    ResultActions antes = consultar(dupla.bruno());
    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());

    ResultActions depois = consultar(dupla.bruno());

    assertThat(etag(depois)).isNotEqualTo(etag(antes));
    assertThat(JsonPath.<Integer>read(corpo(depois), "$.versao"))
        .isEqualTo(JsonPath.<Integer>read(corpo(antes), "$.versao"));
  }

  @Test
  void pedidosCruzadosViramAceite() throws Exception {
    Dupla dupla = revelada();

    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());
    acao(dupla.bruno(), "/api/sala/ajustar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("MONTANDO"))
        .andExpect(jsonPath("$.versao").value(5))
        .andExpect(jsonPath("$.ajuste").value(nullValue()));
  }

  @Test
  void recusarAjusteLimpaOPedido() throws Exception {
    Dupla dupla = revelada();
    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());

    acao(dupla.bruno(), "/api/sala/ajustar/recusar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.versao").value(4))
        .andExpect(jsonPath("$.ajuste").value(nullValue()));
    consultar(dupla.ana()).andExpect(jsonPath("$.ajuste").value(nullValue()));
  }

  @Test
  void repetirOPedidoDeAjusteRenovaAExpiracao() throws Exception {
    Dupla dupla = revelada();
    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());
    relogio.avancar(Duration.ofMinutes(29));

    acao(dupla.ana(), "/api/sala/ajustar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.ajuste.deAssento").value("A"));
    relogio.avancar(Duration.ofMinutes(29));

    consultar(dupla.ana()).andExpect(status().isOk());
  }

  @Test
  void recusarDeNovoRenovaAExpiracao() throws Exception {
    Dupla dupla = revelada();
    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());
    acao(dupla.bruno(), "/api/sala/ajustar/recusar", "").andExpect(status().isOk());
    relogio.avancar(Duration.ofMinutes(29));

    acao(dupla.bruno(), "/api/sala/ajustar/recusar", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.ajuste").value(nullValue()));
    relogio.avancar(Duration.ofMinutes(29));

    consultar(dupla.ana()).andExpect(status().isOk());
  }

  @Test
  void ajustarDuranteAMontagemResponde409() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/ajustar", "")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("FASE_INVALIDA"))
        .andExpect(jsonPath("$.visao.fase").value("MONTANDO"));
  }

  @Test
  void sairComPedidoPendenteEncerra() throws Exception {
    Dupla dupla = revelada();
    acao(dupla.ana(), "/api/sala/ajustar", "").andExpect(status().isOk());

    acao(dupla.bruno(), "/api/sala/sair", "").andExpect(status().isNoContent());

    consultar(dupla.ana())
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.codigo").value("SALA_ENCERRADA"))
        .andExpect(jsonPath("$.motivo").value("SAIU"));
  }

  @Test
  void sinalComDestinoApareceParaODono() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(bruno, "banco", "C2", 0).andExpect(status().isOk());

    acao(ana, "/api/sala/sinal", "{\"peca\":\"banco\",\"lugar\":\"C1\"}")
        .andExpect(status().isOk());

    consultar(bruno)
        .andExpect(jsonPath("$.sinal.peca").value("banco"))
        .andExpect(jsonPath("$.sinal.lugar").value("C1"))
        .andExpect(jsonPath("$.sinal.deAssento").value("A"))
        .andExpect(jsonPath("$.versao").value(1));
  }

  @Test
  void sinalSomeDaVisaoNaRevelacao() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());
    colocar(bruno, "banco", "C2", 1).andExpect(status().isOk());
    colocar(ana, "manta", "L1", 2).andExpect(status().isOk());
    colocar(bruno, "lanterna", "R1", 3).andExpect(status().isOk());
    acao(ana, "/api/sala/sinal", "{\"peca\":\"lanterna\",\"lugar\":\"R2\"}")
        .andExpect(status().isOk());
    consultar(bruno).andExpect(jsonPath("$.sinal.lugar").value("R2"));

    acao(ana, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}").andExpect(status().isOk());
    acao(bruno, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}")
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.sinal").value(nullValue()));

    consultar(bruno)
        .andExpect(jsonPath("$.fase").value("REVELADO"))
        .andExpect(jsonPath("$.sinal").value(nullValue()));
  }

  @Test
  void visaoDoParceiroTrazDestinosSugeriveis() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(bruno, "lanterna", "R2", 0).andExpect(status().isOk());

    consultar(ana)
        .andExpect(jsonPath("$.pecas[4].peca").value("lanterna"))
        .andExpect(jsonPath("$.pecas[4].destinosSugeriveis", contains("L1", "L2", "R1")))
        .andExpect(jsonPath("$.pecas[3].destinosSugeriveis", empty()));
  }

  @Test
  void sinalSemLugarResponde422() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/sinal", "{\"peca\":\"lanterna\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LUGAR_INVALIDO"));
  }

  @Test
  void sinalComLugarDesconhecidoResponde422() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/sinal", "{\"peca\":\"lanterna\",\"lugar\":\"Z9\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LUGAR_INVALIDO"));
  }

  @Test
  void pecaDesconhecidaNoSinalVemAntesDoLugar() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/sinal", "{\"peca\":\"foguete\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PECA_INVALIDA"));
  }

  @Test
  void sinalComLugarInvalidoEmSalaEncerradaResponde422() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    acao(ana, "/api/sala/sair", "").andExpect(status().isNoContent());

    acao(bruno, "/api/sala/sinal", "{\"peca\":\"telescopio\",\"lugar\":\"Z9\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LUGAR_INVALIDO"));
  }

  // Entrada malformada

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "123456789012345678901", "Ana\\u0000", "Ana\\nB"})
  void apelidoInvalidoDevolve422(String apelido) throws Exception {
    criarRequisicao(apelido)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("APELIDO_INVALIDO"));
  }

  @ParameterizedTest
  @CsvSource({
    "foguete, C1, PECA_INVALIDA",
    "TELESCOPIO, C1, PECA_INVALIDA",
    "telescopio, Z9, LUGAR_INVALIDO",
    "telescopio, L1, LUGAR_INVALIDO"
  })
  void pecaOuLugarInvalidoDevolve422(String peca, String lugar, String codigo) throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");

    colocar(ana, peca, lugar, 0)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(codigo));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"apelido\":\"Ana\",\"assento\":\"B\"}", "{\"apelido\":", "[]"})
  void corpoMalformadoOuComCampoExtraDevolve400(String corpo) throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"))
        .andExpect(content().string(not(containsString("Exception"))))
        .andExpect(content().string(not(containsString("bipotheking"))));
  }

  // Mesma origem e cabeçalhos

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/salas",
        "/api/salas/entrar",
        "/api/sala/pecas",
        "/api/sala/sinal",
        "/api/sala/pronto",
        "/api/sala/ajustar",
        "/api/sala/ajustar/recusar",
        "/api/sala/abertura",
        "/api/sala/sair"
      })
  void mutacaoSemXDuoraEhRecusada(String caminho) throws Exception {
    mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.status").value(403));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://evil.example",
        "null",
        "http://localhost:8080",
        "https://localhost",
        "http://localhost.evil.example",
        "ftp://localhost",
        "::"
      })
  void origemEstrangeiraEhRecusada(String origem) throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "1")
                .header(HttpHeaders.ORIGIN, origem)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void xDuoraComValorErradoEhRecusado() throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"));
  }

  @Test
  void origemEstrangeiraEhRecusadaMesmoComHostLocal() throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(HttpHeaders.HOST, "localhost")
                .header(MesmaOrigem.CABECALHO, "1")
                .header(HttpHeaders.ORIGIN, "http://rebind.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"cross-site", "same-site", "none"})
  void secFetchSiteDiferenteDeSameOriginEhRecusado(String site) throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "1")
                .header(MesmaOrigem.SEC_FETCH_SITE, site)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"));
  }

  @Test
  void secFetchSiteSameOriginEhAceito() throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "1")
                .header(MesmaOrigem.SEC_FETCH_SITE, "same-origin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isCreated());
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /index.html, evil.example",
    "GET, /api/sala, rebind.example:80",
    "POST, /api/salas, localhost:8080",
    "GET, /index.html, 127.0.0.2"
  })
  void hostForaDaAllowlistLocalRecebe421(String metodo, String caminho, String host)
      throws Exception {
    mvc.perform(
            request(HttpMethod.valueOf(metodo), caminho)
                .header(HttpHeaders.HOST, host)
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().is(421))
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.status").value(421))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
        .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP));
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost", "127.0.0.1", "[::1]", "LOCALHOST:80"})
  void hostDeLoopbackNaPortaLocalEhAceito(String host) throws Exception {
    mvc.perform(get("/index.html").header(HttpHeaders.HOST, host)).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost", "http://127.0.0.1", "http://[::1]:80"})
  void origemPropriaEhAceita(String origem) throws Exception {
    mvc.perform(
            post("/api/salas")
                .header(MesmaOrigem.CABECALHO, "1")
                .header(HttpHeaders.ORIGIN, origem)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void respostasDaApiTrazemCabecalhosDeSegurancaESemCache() throws Exception {
    mvc.perform(get("/api/sala"))
        .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
  }

  @Test
  void respostaDeErroDaApiTrazCabecalhosDeSegurancaESemCache() throws Exception {
    consultar(new Pessoa("desconhecido", ""))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
  }

  @Test
  void paginaTrazCabecalhosDeSeguranca() throws Exception {
    mvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; object-src 'none'; "
                        + "base-uri 'none'; "
                        + "frame-ancestors 'none'; form-action 'self'"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
  }

  // ETag e presença

  @Test
  void consultaSemMudancaDevolve304() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    String etag = etag(consultar(ana));

    consultar(ana, etag)
        .andExpect(status().isNotModified())
        .andExpect(header().string(HttpHeaders.ETAG, etag))
        .andExpect(content().string(""));
  }

  @Test
  void etagMudaQuandoOParceiroColocaUmaPeca() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    String etag = etag(consultar(ana));
    colocar(bruno, "banco", "C1", 0).andExpect(status().isOk());

    consultar(ana, etag)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, not(etag)));
  }

  @Test
  void parceiroSemContatoHa14SegundosContinuaConectado() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    relogio.avancar(Duration.ofSeconds(14));

    consultar(ana).andExpect(jsonPath("$.parceiro.conectado").value(true));
  }

  @Test
  void parceiroSemContatoHa15SegundosApareceDesconectado() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    String etag = etag(consultar(ana).andExpect(jsonPath("$.parceiro.conectado").value(true)));
    relogio.avancar(Duration.ofSeconds(15));

    consultar(ana, etag)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parceiro.conectado").value(false));
  }

  // Expiração, saída e limite

  @Test
  void salaSemAcaoPor30MinutosExpira() throws Exception {
    Pessoa ana = criar("Ana");
    relogio.avancar(Duration.ofMinutes(30));

    consultar(ana)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.codigo").value("SALA_ENCERRADA"))
        .andExpect(jsonPath("$.motivo").value("EXPIROU"));
  }

  @Test
  void consultasNaoRenovamAExpiracao() throws Exception {
    Pessoa ana = criar("Ana");
    relogio.avancar(Duration.ofMinutes(29));
    consultar(ana).andExpect(status().isOk());
    relogio.avancar(Duration.ofMinutes(1));

    consultar(ana).andExpect(status().isGone()).andExpect(jsonPath("$.motivo").value("EXPIROU"));
  }

  @Test
  void acaoValidaRenovaAExpiracao() throws Exception {
    Pessoa ana = criar("Ana");
    entrar(ana.convite(), "Bruno");
    relogio.avancar(Duration.ofMinutes(29));
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());
    relogio.avancar(Duration.ofMinutes(29));

    consultar(ana).andExpect(status().isOk());
  }

  @Test
  void lapideSomeDepoisDe5Minutos() throws Exception {
    Pessoa ana = criar("Ana");
    relogio.avancar(Duration.ofMinutes(30));
    varredor.varrer();
    consultar(ana).andExpect(status().isGone());
    relogio.avancar(Duration.ofMinutes(5));

    varredor.varrer();

    consultar(ana)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SALA_INEXISTENTE"));
  }

  @Test
  void sairEncerraASalaParaOOutro() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");

    acao(ana, "/api/sala/sair", "")
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    consultar(bruno)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.codigo").value("SALA_ENCERRADA"))
        .andExpect(jsonPath("$.motivo").value("SAIU"));
  }

  @Test
  void sairDeSalaJaEncerradaTambemDevolve204() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    acao(ana, "/api/sala/sair", "").andExpect(status().isNoContent());

    acao(bruno, "/api/sala/sair", "").andExpect(status().isNoContent());
  }

  @Test
  void vigesimaPrimeiraSalaAtivaEhRecusada() throws Exception {
    for (int i = 0; i < LimitesSala.PADRAO.maximoSalas(); i++) {
      criar("Ana");
    }

    criarRequisicao("Ana")
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("LIMITE_SALAS"));
  }

  @Test
  void salasExpiradasLiberamVagas() throws Exception {
    for (int i = 0; i < LimitesSala.PADRAO.maximoSalas(); i++) {
      criar("Ana");
    }
    relogio.avancar(Duration.ofMinutes(30));

    criarRequisicao("Ana").andExpect(status().isCreated());
  }

  // Segredos

  @Test
  void osDoisVeemOResumoDoConviteDaSalaEmTodaFase() throws Exception {
    Pessoa ana = criar("Ana");
    String resumo = sha256Hex(ana.convite());
    assertThat(JsonPath.<String>read(ana.corpo(), "$.conviteDaSala")).isEqualTo(resumo);

    Pessoa bruno = entrar(ana.convite(), "Bruno");

    assertThat(JsonPath.<String>read(bruno.corpo(), "$.conviteDaSala")).isEqualTo(resumo);
    consultar(ana)
        .andExpect(jsonPath("$.fase").value("MONTANDO"))
        .andExpect(jsonPath("$.convite").value(nullValue()))
        .andExpect(jsonPath("$.conviteDaSala").value(resumo));
  }

  private static String sha256Hex(String texto) throws Exception {
    byte[] resumo =
        MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(resumo);
  }

  @Test
  void nenhumTokenAparecesNasVisoes() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");

    String visaoDeAna = corpo(consultar(ana));
    String visaoDeBruno = corpo(consultar(bruno));

    assertThat(ana.corpo()).doesNotContain(ana.token());
    assertThat(bruno.corpo()).doesNotContain(ana.token(), bruno.token(), ana.convite());
    assertThat(visaoDeAna).doesNotContain(ana.token(), bruno.token(), ana.convite());
    assertThat(visaoDeBruno).doesNotContain(ana.token(), bruno.token(), ana.convite());
  }

  @Test
  void nenhumTokenOuConviteApareceNosLogs(CapturedOutput saida) throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    entrarRequisicao(ana.convite(), "Carla").andExpect(status().isNotFound());
    colocar(bruno, "telescopio", "C1", 0).andExpect(status().isForbidden());
    acao(ana, "/api/sala/sair", "").andExpect(status().isNoContent());
    consultar(bruno).andExpect(status().isGone());

    assertThat(saida.getAll()).contains("criada", "encerrada");
    assertThat(saida.getAll()).doesNotContain(ana.token(), bruno.token(), ana.convite());
  }

  // Apoio

  private record Pessoa(String token, String corpo) {
    String convite() {
      return JsonPath.read(corpo, "$.convite");
    }
  }

  private record Dupla(Pessoa ana, Pessoa bruno) {}

  /** Telescópio C1, banco C2, manta L1, lanterna R1 confirmados pelos dois: versão 4. */
  private Dupla revelada() throws Exception {
    Pessoa ana = criar("Ana");
    Pessoa bruno = entrar(ana.convite(), "Bruno");
    colocar(ana, "telescopio", "C1", 0).andExpect(status().isOk());
    colocar(bruno, "banco", "C2", 1).andExpect(status().isOk());
    colocar(ana, "manta", "L1", 2).andExpect(status().isOk());
    colocar(bruno, "lanterna", "R1", 3).andExpect(status().isOk());
    acao(ana, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}").andExpect(status().isOk());
    acao(bruno, "/api/sala/pronto", "{\"versao\":4,\"pronto\":true}")
        .andExpect(jsonPath("$.fase").value("REVELADO"));
    return new Dupla(ana, bruno);
  }

  private Pessoa criar(String apelido) throws Exception {
    return pessoa(criarRequisicao(apelido).andExpect(status().isCreated()).andReturn());
  }

  private ResultActions criarRequisicao(String apelido) throws Exception {
    return mvc.perform(
        post("/api/salas")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"apelido\":\"" + apelido + "\"}"));
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

  private static Pessoa pessoa(MvcResult resultado) throws Exception {
    String cookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(cookie).startsWith(CookieAssento.NOME + "=");
    String token = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    return new Pessoa(token, resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static String etag(ResultActions resposta) {
    return resposta.andReturn().getResponse().getHeader(HttpHeaders.ETAG);
  }

  private static String corpo(ResultActions resposta) throws Exception {
    return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
