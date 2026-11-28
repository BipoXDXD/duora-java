package bipotheking.net.duora.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.eventos.dominio.EventoGravado;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/eventos} no Tomcat de verdade: o SSE depende do servidor embutido (flush, fim da
 * resposta, cliente que some), que o MockMvc não simula. O batimento cai para 200 ms para o teste
 * ver o evento e notar o cliente que fechou.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "duora.eventos.batimento=PT0.2S")
class EventosApiTest {

  private static final Duration ESPERA = Duration.ofSeconds(5);
  private static final String INICIAL = "fila.inicialDeTeste";
  private static final String MARCADOR = "fila.marcador";
  private static final String BATIMENTO = "stream.batimento";

  @Value("${local.server.port}")
  private int porta;

  @Autowired private ContasDeTeste contas;
  @Autowired private CanalDeEventos canal;
  @Autowired private EntregaDeEventos entrega;
  @Autowired private RepositorioDeEventos repositorio;
  @Autowired private TransactionTemplate transacao;
  @Autowired private JdbcClient jdbc;
  @Autowired private MeterRegistry metricas;
  @Autowired private JsonMapper json;

  private final List<ClienteSse> abertos = new ArrayList<>();
  private ContaDeTeste conta;

  /** Estado inicial de teste: um efêmero que mostra quando o stream abriu sem reenvio. */
  @TestConfiguration(proxyBeanMethods = false)
  static class EstadoInicialDeTeste {

    @Bean
    EstadoInicial estadoInicialDeTeste() {
      return contaId -> List.of(new Evento(INICIAL, Map.of("conta", contaId.toString())));
    }
  }

  @BeforeEach
  void preparar() {
    conta = contas.criarAtiva();
  }

  @AfterEach
  void fecharClientes() {
    abertos.forEach(ClienteSse::close);
  }

  @Test
  void semSessaoRecebe401() throws Exception {
    HttpResponse<String> resposta =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(uri("/api/eventos"))
                    .header("Accept", "text/event-stream")
                    .timeout(ESPERA)
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(resposta.statusCode()).isEqualTo(401);
  }

  @Test
  void eventoChegaComNomeIdEDados() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    assertThat(cliente.proximo().evento()).isEqualTo(INICIAL);

    publicar(conta.id(), "fila.estado", Map.of("situacao", "AGUARDANDO_COMPATIVEL"));

    Bloco bloco = cliente.proximo();
    EventoGravado gravado = repositorio.depoisDe(conta.id(), 0, 10).getFirst();
    assertThat(bloco.evento()).isEqualTo("fila.estado");
    assertThat(bloco.id()).isEqualTo(Long.toString(gravado.seq()));
    assertThat(arvore(bloco.dados())).isEqualTo(arvore("{\"situacao\":\"AGUARDANDO_COMPATIVEL\"}"));
  }

  @Test
  void primeiroEventoTrazRetryEntre2000E5000() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());

    Bloco primeiro = cliente.proximoBloco();

    assertThat(primeiro.retry()).isNotNull().isBetween(2000, 5000);
  }

  @Test
  void respostaNaoVaiParaCacheNemBuffer() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());

    assertThat(cliente.status()).isEqualTo(200);
    assertThat(cliente.cabecalhos().firstValue("Content-Type"))
        .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("text/event-stream"));
    assertThat(cliente.cabecalhos().firstValue("Cache-Control")).hasValue("no-store");
    assertThat(cliente.cabecalhos().firstValue("X-Accel-Buffering")).hasValue("no");
  }

  @Test
  void rollbackNaoEntregaNada() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    cliente.proximo();

    transacao.executeWithoutResult(status -> {
      canal.publicar(conta.id(), new Evento("fila.estado", Map.of()));
      status.setRollbackOnly();
    });
    canal.publicarEfemero(conta.id(), new Evento(MARCADOR, Map.of()));

    assertThat(cliente.proximo().evento()).isEqualTo(MARCADOR);
  }

  @Test
  void lastEventIdReenviaDoBanco() throws Exception {
    List<Long> seqs = publicarTres(conta.id());

    ClienteSse cliente =
        abrir(conta, "/api/eventos", Map.of("Last-Event-ID", Long.toString(seqs.get(0))));

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::id)
        .containsExactly(Long.toString(seqs.get(1)), Long.toString(seqs.get(2)), null);
  }

  @Test
  void ultimoIdNaQueryFazOMesmo() throws Exception {
    List<Long> seqs = publicarTres(conta.id());

    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=" + seqs.get(0), Map.of());

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::id)
        .containsExactly(Long.toString(seqs.get(1)), Long.toString(seqs.get(2)), null);
  }

  @Test
  void cabecalhoVenceAQuery() throws Exception {
    List<Long> seqs = publicarTres(conta.id());

    ClienteSse cliente =
        abrir(
            conta,
            "/api/eventos?ultimoId=" + seqs.get(0),
            Map.of("Last-Event-ID", Long.toString(seqs.get(1))));

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::id)
        .containsExactly(Long.toString(seqs.get(2)), null);
  }

  @Test
  void ultimoIdIgualAoMenorGuardadoReenviaOsSeguintes() throws Exception {
    List<Long> seqs = publicarTres(conta.id());
    long menor = repositorio.menorSeq(conta.id()).orElseThrow();
    assertThat(menor).isEqualTo(seqs.get(0));

    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=" + menor, Map.of());

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::id)
        .containsExactly(Long.toString(seqs.get(1)), Long.toString(seqs.get(2)), null);
  }

  @Test
  void ultimoIdEmDiaAbreSoComRetry() throws Exception {
    List<Long> seqs = publicarTres(conta.id());

    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=" + seqs.get(2), Map.of());

    Bloco primeiro = cliente.proximoBloco();
    assertThat(primeiro.evento()).isNull();
    assertThat(primeiro.retry()).isNotNull().isBetween(2000, 5000);
    canal.publicarEfemero(conta.id(), new Evento(MARCADOR, Map.of()));
    assertThat(cliente.proximo().evento()).isEqualTo(MARCADOR);
  }

  @Test
  void ultimoIdAnteriorAoGuardadoMandaEstadoInicial() throws Exception {
    List<Long> seqs = publicarTres(conta.id());

    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=" + (seqs.get(0) - 1), Map.of());

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::evento)
        .containsExactly(INICIAL, MARCADOR);
  }

  @Test
  void ultimoIdInvalidoEIgnorado() throws Exception {
    publicarTres(conta.id());

    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=-1", Map.of("Last-Event-ID", "x"));

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::evento)
        .containsExactly(INICIAL, MARCADOR);
  }

  @Test
  void semUltimoIdMandaEstadoInicial() throws Exception {
    publicarTres(conta.id());

    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());

    assertThat(cliente.ateOMarcador(conta.id()))
        .extracting(Bloco::evento)
        .containsExactly(INICIAL, MARCADOR);
  }

  @Test
  void eventoDeOutraContaNaoChega() throws Exception {
    ContaDeTeste outra = contas.criarAtiva();
    publicarTres(outra.id());
    ClienteSse cliente = abrir(conta, "/api/eventos?ultimoId=0", Map.of());
    assertThat(cliente.proximo().evento()).isEqualTo(INICIAL);

    publicar(outra.id(), "fila.estado", Map.of());
    canal.publicarEfemero(conta.id(), new Evento(MARCADOR, Map.of()));

    assertThat(cliente.proximo().evento()).isEqualTo(MARCADOR);
  }

  @Test
  void efemeroChegaSemId() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    cliente.proximo();

    canal.publicarEfemero(conta.id(), new Evento("fila.pareado", Map.of("encontroId", "e1")));

    Bloco bloco = cliente.proximo();
    assertThat(bloco.evento()).isEqualTo("fila.pareado");
    assertThat(bloco.id()).isNull();
    assertThat(bloco.dados()).isEqualTo("{\"encontroId\":\"e1\"}");
  }

  @Test
  void filaCheiaFechaOStreamEAReconexaoTrazORestoDoBanco() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    cliente.proximo();
    publicar(conta.id(), "fila.estado", Map.of("n", 0));
    String ultimoRecebido = cliente.proximo().id();
    double estourosAntes = metricas.counter("duora.eventos.estouros").count();

    // A tabela travada prende o escritor na leitura do banco; os efêmeros enchem a fila.
    transacao.executeWithoutResult(status -> {
      jdbc.sql("lock table evento_conta in access exclusive mode").update();
      for (int n = 1; n <= 3; n++) {
        canal.publicar(conta.id(), new Evento("fila.estado", Map.of("n", n)));
      }
      entrega.acordar(conta.id());
      for (int n = 0; n <= 100; n++) {
        canal.publicarEfemero(conta.id(), new Evento(MARCADOR, Map.of()));
      }
    });

    cliente.esperarFim();
    assertThat(metricas.counter("duora.eventos.estouros").count()).isEqualTo(estourosAntes + 1);
    ClienteSse reconexao =
        abrir(
            conta, "/api/eventos", Map.of("Last-Event-ID", Objects.requireNonNull(ultimoRecebido)));
    assertThat(reconexao.ateOMarcador(conta.id()))
        .extracting(bloco -> arvore(bloco.dados()))
        .containsExactly(
            arvore("{\"n\":1}"), arvore("{\"n\":2}"), arvore("{\"n\":3}"), arvore("{}"));
  }

  @Test
  void quartoStreamAvisaESubstituiOMaisAntigo() throws Exception {
    List<ClienteSse> clientes = new ArrayList<>();
    for (int n = 0; n < 3; n++) {
      ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
      cliente.proximo();
      clientes.add(cliente);
    }

    ClienteSse quarto = abrir(conta, "/api/eventos", Map.of());
    quarto.proximo();

    Bloco aviso = clientes.getFirst().proximo();
    assertThat(aviso.evento()).isEqualTo("stream.substituido");
    assertThat(aviso.id()).isNull();
    assertThat(aviso.dados()).isEqualTo("{}");
    clientes.getFirst().esperarFim();
    canal.publicarEfemero(conta.id(), new Evento(MARCADOR, Map.of()));
    for (ClienteSse restante : List.of(clientes.get(1), clientes.get(2), quarto)) {
      assertThat(restante.proximo().evento()).isEqualTo(MARCADOR);
    }
  }

  @Test
  void conectadaAcompanhaOStream() throws Exception {
    assertThat(canal.conectada(conta.id())).isFalse();
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    cliente.proximo();
    assertThat(canal.conectada(conta.id())).isTrue();

    cliente.close();

    esperarAte(() -> !canal.conectada(conta.id()));
  }

  @Test
  void batimentoChegaComoEvento() throws Exception {
    ClienteSse cliente = abrir(conta, "/api/eventos", Map.of());
    cliente.proximo();

    Bloco batimento = cliente.proximoBatimento();

    assertThat(batimento.id()).isNull();
    assertThat(batimento.dados()).isEqualTo("{}");
  }

  @Test
  void encerrarStreamsFechaTodosDaConta() throws Exception {
    ClienteSse primeiro = abrir(conta, "/api/eventos", Map.of());
    ClienteSse segundo = abrir(conta, "/api/eventos", Map.of());
    primeiro.proximo();
    segundo.proximo();

    canal.encerrarStreams(conta.id());

    primeiro.esperarFim();
    segundo.esperarFim();
    esperarAte(() -> !canal.conectada(conta.id()));
  }

  @Test
  void aberturaContaNaMetrica() throws Exception {
    double antes = metricas.counter("duora.eventos.aberturas").count();

    abrir(conta, "/api/eventos", Map.of()).proximo();

    assertThat(metricas.counter("duora.eventos.aberturas").count()).isEqualTo(antes + 1);
    assertThat(metricas.get("duora.eventos.streams").gauge().value()).isPositive();
  }

  /** Três eventos duráveis gravados para a conta, em transações separadas; devolve os seqs. */
  private List<Long> publicarTres(ContaId para) {
    for (int n = 1; n <= 3; n++) {
      publicar(para, "fila.estado", Map.of("n", n));
    }
    return repositorio.depoisDe(para, 0, 10).stream().map(EventoGravado::seq).toList();
  }

  private void publicar(ContaId para, String nome, Object dados) {
    transacao.executeWithoutResult(status -> canal.publicar(para, new Evento(nome, dados)));
  }

  private ClienteSse abrir(ContaDeTeste dono, String caminho, Map<String, String> cabecalhos)
      throws IOException, InterruptedException {
    ClienteSse cliente = new ClienteSse(uri(caminho), dono.cookie(), cabecalhos, canal, dono.id());
    abertos.add(cliente);
    return cliente;
  }

  /** O JSON lido: o durável sai do {@code jsonb} com espaços que o efêmero não tem. */
  private JsonNode arvore(@Nullable String dados) {
    return json.readTree(Objects.requireNonNull(dados));
  }

  private URI uri(String caminho) {
    return URI.create("http://127.0.0.1:" + porta + caminho);
  }

  private static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
    Instant limite = Instant.now().plus(ESPERA);
    while (!condicao.getAsBoolean()) {
      if (Instant.now().isAfter(limite)) {
        fail("A condição não se cumpriu em " + ESPERA);
      }
      Thread.sleep(20);
    }
  }

  /** Um bloco SSE já separado em campos; {@code evento} nulo num bloco só com {@code retry:}. */
  record Bloco(
      @Nullable String evento,
      @Nullable String id,
      @Nullable String dados,
      @Nullable Integer retry) {}

  /** Cliente SSE mínimo: lê as linhas numa virtual thread e entrega os blocos por uma fila. */
  static final class ClienteSse implements AutoCloseable {

    private static final Bloco FIM = new Bloco(null, null, null, null);

    private final HttpClient http = HttpClient.newHttpClient();
    private final BlockingQueue<Bloco> blocos = new LinkedBlockingQueue<>();
    private final CanalDeEventos canal;
    private final ContaId conta;
    private final int status;
    private final HttpHeaders cabecalhos;

    ClienteSse(
        URI uri, String cookie, Map<String, String> extras, CanalDeEventos canal, ContaId conta)
        throws IOException, InterruptedException {
      this.canal = canal;
      this.conta = conta;
      HttpRequest.Builder pedido =
          HttpRequest.newBuilder(uri)
              .header("Accept", "text/event-stream")
              .header("Cookie", cookie)
              .timeout(ESPERA);
      extras.forEach(pedido::header);
      HttpResponse<Stream<String>> resposta =
          http.send(pedido.build(), HttpResponse.BodyHandlers.ofLines());
      status = resposta.statusCode();
      cabecalhos = resposta.headers();
      Thread.ofVirtual().start(() -> ler(resposta.body()));
    }

    int status() {
      return status;
    }

    HttpHeaders cabecalhos() {
      return cabecalhos;
    }

    /** O próximo bloco, qualquer que seja. */
    Bloco proximoBloco() throws InterruptedException {
      return proximoQue(bloco -> true);
    }

    /** O próximo evento, pulando batimentos e blocos só com {@code retry:}. */
    Bloco proximo() throws InterruptedException {
      return proximoQue(bloco -> bloco.evento() != null && !BATIMENTO.equals(bloco.evento()));
    }

    Bloco proximoBatimento() throws InterruptedException {
      return proximoQue(bloco -> BATIMENTO.equals(bloco.evento()));
    }

    /**
     * O primeiro bloco que passa no filtro, dentro de {@link #ESPERA} contada do começo: os
     * batimentos descartados não renovam o prazo.
     */
    private Bloco proximoQue(Predicate<Bloco> filtro) throws InterruptedException {
      Instant limite = Instant.now().plus(ESPERA);
      while (true) {
        long restante = Duration.between(Instant.now(), limite).toMillis();
        Bloco bloco = restante > 0 ? blocos.poll(restante, TimeUnit.MILLISECONDS) : null;
        if (bloco == null) {
          return fail("Nenhum bloco esperado em " + ESPERA);
        }
        if (bloco == FIM) {
          return fail("O stream terminou");
        }
        if (filtro.test(bloco)) {
          return bloco;
        }
      }
    }

    /**
     * Manda um efêmero {@code fila.marcador} à conta e devolve os eventos até ele, inclusive. Só
     * vale com o stream já registrado: espera o primeiro evento antes de mandar o marcador.
     */
    List<Bloco> ateOMarcador(ContaId para) throws InterruptedException {
      List<Bloco> recebidos = new ArrayList<>();
      recebidos.add(proximo());
      canal.publicarEfemero(para, new Evento(MARCADOR, Map.of()));
      while (!MARCADOR.equals(recebidos.getLast().evento())) {
        recebidos.add(proximo());
      }
      return recebidos;
    }

    /** Espera o servidor terminar a resposta, descartando o que ainda chegar. */
    void esperarFim() throws InterruptedException {
      Instant limite = Instant.now().plus(ESPERA);
      while (Instant.now().isBefore(limite)) {
        Bloco bloco = blocos.poll(50, TimeUnit.MILLISECONDS);
        if (bloco == FIM) {
          return;
        }
      }
      fail("O stream da conta " + conta + " não terminou; conectada=" + canal.conectada(conta));
    }

    private void ler(Stream<String> linhas) {
      String evento = null;
      String id = null;
      String dados = null;
      Integer retry = null;
      try (linhas) {
        for (String linha : (Iterable<String>) linhas::iterator) {
          if (linha.isEmpty()) {
            if (evento != null || dados != null || retry != null) {
              blocos.add(new Bloco(evento, id, dados, retry));
            }
            evento = null;
            id = null;
            dados = null;
            retry = null;
          } else if (linha.startsWith("event:")) {
            evento = valor(linha, 6);
          } else if (linha.startsWith("id:")) {
            id = valor(linha, 3);
          } else if (linha.startsWith("data:")) {
            dados = valor(linha, 5);
          } else if (linha.startsWith("retry:")) {
            retry = Integer.valueOf(valor(linha, 6));
          }
        }
      } catch (RuntimeException e) {
        // Cliente fechado pelo teste: a leitura cai com IOException embrulhada.
      } finally {
        blocos.add(FIM);
      }
    }

    /** O valor do campo, sem o espaço opcional depois dos dois-pontos. */
    private static String valor(String linha, int inicio) {
      String valor = linha.substring(inicio);
      return valor.startsWith(" ") ? valor.substring(1) : valor;
    }

    @Override
    public void close() {
      http.shutdownNow();
    }
  }
}
