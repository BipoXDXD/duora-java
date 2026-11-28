package bipotheking.net.duora.sala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.eventos.EntregaEspia;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.Localizacoes;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /api/fila} com as portas dos encontros falsas: a ocupação e quem já está em encontro são o
 * que cada teste configura. A entrada simultânea vai pelo Tomcat de verdade, em duas conexões.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({EncontrosFalsos.class, EntregaEspia.class, FilaApiTest.OutraPonte.class})
class FilaApiTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1995, 3, 4);

  @Value("${local.server.port}")
  private int porta;

  @Autowired private ContasDeTeste contas;
  @Autowired private EncontrosFalsos encontros;
  @Autowired private EntregaEspia espia;
  @Autowired private Localizacoes localizacoes;
  @Autowired private EstadoInicialDaFila estadoInicial;
  @Autowired private JsonMapper json;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @BeforeEach
  void preparar() {
    encontros.esquecer();
    espia.esquecer();
  }

  @AfterEach
  void encerrarExecutor() {
    executor.shutdownNow();
  }

  @Test
  void semSessao401() throws Exception {
    mvc.perform(
            post("/api/fila")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void entrarComPerfilProntoAguardaCompativel() throws Exception {
    ContaDeTeste lia = pronta("Lia");

    String corpo =
        entrar(lia, "{}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    JsonNode estado = json.readTree(corpo);
    assertThat(estado.propertyNames())
        .containsExactlyInAnyOrder(
            "situacao", "desde", "posicao", "estimativaMinutos", "encontroId", "cenario");
    assertThat(estado.get("situacao").asString()).isEqualTo("AGUARDANDO_COMPATIVEL");
    assertThat(Instant.parse(estado.get("desde").asString())).isEqualTo(INICIO);
    assertThat(estado.get("posicao").isNull()).isTrue();
    assertThat(estado.get("estimativaMinutos").isNull()).isTrue();
    assertThat(estado.get("encontroId").isNull()).isTrue();
    assertThat(estado.get("cenario").isNull()).isTrue();
  }

  @Test
  void entrarPublicaFilaEstado() throws Exception {
    ContaDeTeste lia = pronta("Lia");

    entrar(lia, "{}").andExpect(status().isOk());

    assertThat(eventosDeFila(lia)).isEqualTo(1);
    assertThat(espia.acordadas()).containsExactly(lia.id());
  }

  @Test
  void entrarDuasVezesMantemUmaEntradaEOEntrouEm() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    entrar(lia, "{}").andExpect(status().isOk());
    relogio.avancar(Duration.ofMinutes(1));

    entrar(lia, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.desde").value(INICIO.toString()));

    assertThat(linhasNaFila()).isEqualTo(1);
    assertThat(entrouEm(lia)).isEqualTo(INICIO);
    assertThat(eventosDeFila(lia)).isEqualTo(1);
  }

  @Test
  void duasEntradasSimultaneasUmaLinha() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    CountDownLatch largada = new CountDownLatch(1);
    HttpClient cliente = HttpClient.newHttpClient();
    List<Future<Integer>> respostas = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      respostas.add(
          executor.submit(() -> {
            largada.await();
            return cliente
                .send(entradaHttp(lia), HttpResponse.BodyHandlers.discarding())
                .statusCode();
          }));
    }

    largada.countDown();

    for (Future<Integer> resposta : respostas) {
      assertThat(resposta.get(10, TimeUnit.SECONDS)).isEqualTo(200);
    }
    assertThat(linhasNaFila()).isEqualTo(1);
    assertThat(eventosDeFila(lia)).isEqualTo(1);
  }

  @Test
  void perfilIncompletoRecebe422ENadaGrava() throws Exception {
    ContaDeTeste semPerfil = contas.criarVerificada();

    entrar(semPerfil, "{}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("PERFIL_INCOMPLETO"));

    assertThat(linhasNaFila()).isZero();
    assertThat(eventosDeFila(semPerfil)).isZero();
  }

  @Test
  void contaPausadaRecebe422ContaPausada() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    mvc.perform(post("/api/perfil/pausa").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(lia)))
        .andExpect(status().isNoContent());

    entrar(lia, "{}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONTA_PAUSADA"))
        .andExpect(
            jsonPath("$.mensagem")
                .value(
                    "Sua conta está pausada. Volte a aparecer em Meu perfil para entrar na fila."));

    assertThat(linhasNaFila()).isZero();
  }

  @Test
  void jaEmEncontroRecebe409() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    encontros.emEncontro(lia.id(), "encontro-1");

    entrar(lia, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("JA_EM_ENCONTRO"));

    assertThat(linhasNaFila()).isZero();
  }

  @Test
  void decimaPrimeiraEntradaNoMinutoRecebe429() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    for (int i = 0; i < 10; i++) {
      entrar(lia, "{}").andExpect(status().isOk());
    }

    entrar(lia, "{}")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  void coordenadaComConsentimentoAtualizaACelula() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    mvc.perform(
            put("/api/perfil/localizacao")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":-22.9,\"longitude\":-43.2}")
                .cookie(cookieDe(lia)))
        .andExpect(status().isNoContent());

    entrar(lia, "{\"latitude\":-23.55,\"longitude\":-46.63}").andExpect(status().isOk());

    assertThat(localizacoes.celula(lia.id())).contains(CelulaGeohash.de(-23.55, -46.63));
    assertThat(
            jdbc.sql(
                    """
                    select column_name from information_schema.columns
                    where table_schema = 'public' and table_name = 'fila_espera'
                    """)
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder(
            "conta_id",
            "entrou_em",
            "de_teste",
            "ausente_desde",
            "encontro_id",
            "cenario_id",
            "pareado_em");
  }

  @Test
  void coordenadaSemConsentimentoEDescartadaEEntra() throws Exception {
    ContaDeTeste lia = pronta("Lia");

    entrar(lia, "{\"latitude\":-23.55,\"longitude\":-46.63}").andExpect(status().isOk());

    assertThat(localizacoes.celula(lia.id())).isEmpty();
    assertThat(linhasNaFila()).isEqualTo(1);
  }

  @Test
  void soLatitudeRecebe422() throws Exception {
    ContaDeTeste lia = pronta("Lia");

    entrar(lia, "{\"latitude\":-23.55}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("COORDENADA_INVALIDA"));

    assertThat(linhasNaFila()).isZero();
  }

  @Test
  void capacidadeCheiaMostraPosicaoEEstimativa() throws Exception {
    encontros.ocupar(10, List.of(daqui(4), daqui(9), daqui(14)));
    ContaDeTeste lia = pronta("Lia");
    ContaDeTeste rui = pronta("Rui");
    ContaDeTeste ana = pronta("Ana");
    entrar(lia, "{}").andExpect(status().isOk());
    entrar(rui, "{}").andExpect(status().isOk());

    entrar(ana, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.situacao").value("AGUARDANDO_VAGA"))
        .andExpect(jsonPath("$.posicao").value(3))
        .andExpect(jsonPath("$.estimativaMinutos").value(9));
  }

  @Test
  void desistirTiraDaFilaEEIdempotente() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    entrar(lia, "{}").andExpect(status().isOk());

    desistir(lia).andExpect(status().isNoContent());
    desistir(lia).andExpect(status().isNoContent());

    mvc.perform(get("/api/fila").cookie(cookieDe(lia)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("FORA_DA_FILA"));
    assertThat(linhasNaFila()).isZero();
  }

  @Test
  void estadoInicialDoStreamTrazAFila() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    ContaDeTeste rui = pronta("Rui");
    entrar(lia, "{}").andExpect(status().isOk());

    List<Evento> naFila = estadoInicial.para(lia.id());
    List<Evento> foraDaFila = estadoInicial.para(rui.id());

    assertThat(naFila).extracting(Evento::nome).containsExactly("fila.estado");
    assertThat(naFila.getFirst().dados()).isEqualTo(EstadoNaFila.aguardandoCompativel(INICIO));
    assertThat(foraDaFila).isEmpty();
  }

  @Test
  void encontrosFalsosVencemAPonteDaAplicacao() throws Exception {
    ContaDeTeste lia = pronta("Lia");
    encontros.ocupar(10, List.of());

    entrar(lia, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.situacao").value("AGUARDANDO_VAGA"));
  }

  /** Outra implementação das portas, como a ponte da {@code room}; qualquer chamada falha. */
  @TestConfiguration(proxyBeanMethods = false)
  static class OutraPonte {

    @Bean
    AberturaDeEncontro outraAbertura() {
      return (AberturaDeEncontro)
          Proxy.newProxyInstance(
              AberturaDeEncontro.class.getClassLoader(),
              new Class<?>[] {AberturaDeEncontro.class, OcupacaoDeEncontros.class},
              (proxy, metodo, argumentos) -> {
                if (metodo.getDeclaringClass() == Object.class) {
                  return switch (metodo.getName()) {
                    case "equals" -> proxy == argumentos[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "OutraPonte";
                  };
                }
                throw new AssertionError("a ponte da aplicação não devia ser chamada");
              });
    }
  }

  private ContaDeTeste pronta(String apelido) {
    return contas.criar(apelido, NASCIMENTO);
  }

  private ResultActions entrar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        post("/api/fila")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private ResultActions desistir(ContaDeTeste conta) throws Exception {
    return mvc.perform(
        delete("/api/fila").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)));
  }

  private HttpRequest entradaHttp(ContaDeTeste conta) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/fila"))
        .header(MesmaOrigem.CABECALHO, "1")
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header("Cookie", conta.cookie())
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
  }

  private long linhasNaFila() {
    return jdbc.sql("select count(*) from fila_espera").query(Long.class).single();
  }

  private Instant entrouEm(ContaDeTeste conta) {
    return jdbc.sql("select entrou_em from fila_espera where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query((rs, linha) -> rs.getObject("entrou_em", OffsetDateTime.class).toInstant())
        .single();
  }

  private long eventosDeFila(ContaDeTeste conta) {
    return jdbc.sql(
            "select count(*) from evento_conta where conta_id = :conta and nome = 'fila.estado'")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private Instant daqui(long minutos) {
    return INICIO.plus(Duration.ofMinutes(minutos));
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
