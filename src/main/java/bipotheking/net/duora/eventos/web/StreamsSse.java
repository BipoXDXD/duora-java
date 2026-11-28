package bipotheking.net.duora.eventos.web;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.EntregaDeEventos;
import bipotheking.net.duora.eventos.EstadoInicial;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link EntregaDeEventos} desta instância: os streams SSE abertos, até {@value
 * #MAXIMO_POR_CONTA} por conta. O quarto stream de uma conta avisa o mais antigo ({@code
 * stream.substituido}) e o fecha. No desligamento, fecha todos antes de o servidor esperar as
 * requisições em andamento: os clientes reconectam na instância nova sem perder nada.
 */
@Component
public class StreamsSse implements EntregaDeEventos, SmartLifecycle {

  static final int MAXIMO_POR_CONTA = 3;
  private static final int RETRY_MINIMO_MS = 2000;
  private static final int RETRY_MAXIMO_MS = 5000;

  private final ConcurrentMap<ContaId, List<Stream>> porConta = new ConcurrentHashMap<>();
  private final Stream.Ambiente ambiente;
  private final RandomGenerator aleatorio;
  private final Duration duracaoMaxima;
  private final Counter aberturas;
  private volatile boolean rodando;

  StreamsSse(
      RepositorioDeEventos repositorio,
      List<EstadoInicial> estadosIniciais,
      JsonMapper json,
      RandomGenerator aleatorio,
      MeterRegistry metricas,
      @Value("${duora.eventos.duracao-maxima}") Duration duracaoMaxima,
      @Value("${duora.eventos.batimento}") Duration batimento) {
    this.aleatorio = aleatorio;
    this.duracaoMaxima = duracaoMaxima;
    this.aberturas =
        Counter.builder("duora.eventos.aberturas")
            .description("Streams SSE abertos (inclui reconexões)")
            .register(metricas);
    Counter estouros =
        Counter.builder("duora.eventos.estouros")
            .description("Streams SSE fechados porque a fila encheu")
            .register(metricas);
    this.ambiente =
        new Stream.Ambiente(repositorio, List.copyOf(estadosIniciais), json, batimento, estouros);
    Gauge.builder("duora.eventos.streams", this, StreamsSse::abertos)
        .description("Streams SSE abertos nesta instância")
        .register(metricas);
  }

  /**
   * Abre um stream para a conta, já registrado: o que for gravado a partir daqui o acorda.
   *
   * @param ultimo o último {@code id} que o cliente diz ter recebido
   */
  SseEmitter abrir(ContaId conta, OptionalLong ultimo) {
    int retry = RETRY_MINIMO_MS + aleatorio.nextInt(RETRY_MAXIMO_MS - RETRY_MINIMO_MS + 1);
    Stream novo =
        new Stream(conta, new SseEmitter(duracaoMaxima.toMillis()), ambiente, retry, this::remover);
    List<Stream> substituidos = new ArrayList<>();
    porConta.compute(
        conta,
        (chave, atuais) -> {
          List<Stream> lista = atuais == null ? new ArrayList<>() : new ArrayList<>(atuais);
          lista.add(novo);
          while (lista.size() > MAXIMO_POR_CONTA) {
            substituidos.add(lista.removeFirst());
          }
          return List.copyOf(lista);
        });
    substituidos.forEach(antigo -> antigo.encerrar(Stream.SUBSTITUIDO));
    aberturas.increment();
    novo.iniciar(ultimo);
    return novo.emissor();
  }

  @Override
  public void acordar(ContaId conta) {
    streamsDa(conta).forEach(Stream::acordar);
  }

  @Override
  public void efemero(ContaId conta, Evento evento) {
    streamsDa(conta).forEach(stream -> stream.efemero(evento));
  }

  @Override
  public boolean conectada(ContaId conta) {
    return porConta.containsKey(conta);
  }

  @Override
  public void encerrar(ContaId conta) {
    streamsDa(conta).forEach(stream -> stream.encerrar(null));
  }

  @Override
  public void start() {
    rodando = true;
  }

  /** Fase padrão: para antes do desligamento gracioso do servidor, que esperaria os streams. */
  @Override
  public void stop() {
    rodando = false;
    porConta.values().forEach(streams -> streams.forEach(stream -> stream.encerrar(null)));
  }

  @Override
  public boolean isRunning() {
    return rodando;
  }

  private List<Stream> streamsDa(ContaId conta) {
    return porConta.getOrDefault(conta, List.of());
  }

  private void remover(Stream stream) {
    porConta.computeIfPresent(
        stream.conta(),
        (chave, atuais) -> {
          List<Stream> lista = atuais.stream().filter(atual -> atual != stream).toList();
          return lista.isEmpty() ? null : lista;
        });
  }

  private int abertos() {
    return porConta.values().stream().mapToInt(List::size).sum();
  }
}
