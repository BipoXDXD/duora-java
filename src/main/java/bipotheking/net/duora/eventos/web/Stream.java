package bipotheking.net.duora.eventos.web;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.EstadoInicial;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.eventos.dominio.EventoGravado;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import io.micrometer.core.instrument.Counter;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Uma conexão SSE aberta. Um escritor só, numa virtual thread, escreve no emissor: abre o stream
 * (reenvio pelo banco ou estado inicial), depois atende a fila. Os produtores ({@code acordar},
 * {@code efemero}) só enfileiram e nunca esperam; a fila cheia fecha o stream, e o cliente volta
 * com o último {@code id} recebido e lê o resto do banco.
 */
final class Stream {

  /** Itens na fila de um stream; além disso, o produtor estoura. */
  static final int CAPACIDADE = 100;

  /** Quantos eventos gravados uma leitura do banco traz por vez. */
  static final int LOTE = 100;

  /** Quantos eventos a abertura reenvia, no máximo; além disso, manda o estado inicial. */
  static final int REENVIO_MAXIMO = 500;

  static final String BATIMENTO = "stream.batimento";
  static final String SUBSTITUIDO = "stream.substituido";
  private static final String VAZIO = "{}";
  private static final Logger LOG = LoggerFactory.getLogger(Stream.class);

  /** O que o escritor faz a seguir. */
  private sealed interface Item {}

  /** Ler do banco o que veio depois do último enviado. */
  private record Puxar() implements Item {}

  private record Efemero(Evento evento) implements Item {}

  /** Mandar o aviso, se houver, e terminar a resposta. */
  private record Fim(@Nullable String aviso) implements Item {}

  private static final Puxar PUXAR = new Puxar();

  /** O que é igual para todos os streams. */
  record Ambiente(
      RepositorioDeEventos repositorio,
      List<EstadoInicial> estadosIniciais,
      JsonMapper json,
      Duration batimento,
      Counter estouros) {}

  private final ContaId conta;
  private final SseEmitter emissor;
  private final Ambiente ambiente;
  private final int retry;
  private final Consumer<Stream> aoConcluir;
  private final BlockingQueue<Item> fila = new ArrayBlockingQueue<>(CAPACIDADE);
  private final AtomicBoolean puxarPendente = new AtomicBoolean();
  private final AtomicBoolean aceitando = new AtomicBoolean(true);
  private final AtomicBoolean concluido = new AtomicBoolean();

  /** Só o escritor lê e escreve. */
  private long ultimoEnviado;

  private boolean primeiroEnviado;

  Stream(
      ContaId conta,
      SseEmitter emissor,
      Ambiente ambiente,
      int retry,
      Consumer<Stream> aoConcluir) {
    this.conta = conta;
    this.emissor = emissor;
    this.ambiente = ambiente;
    this.retry = retry;
    this.aoConcluir = aoConcluir;
    emissor.onCompletion(this::concluir);
    emissor.onError(erro -> concluir());
    emissor.onTimeout(this::terminarJa);
  }

  ContaId conta() {
    return conta;
  }

  SseEmitter emissor() {
    return emissor;
  }

  /** Começa o escritor; {@code ultimo} é o último {@code id} que o cliente diz ter recebido. */
  void iniciar(OptionalLong ultimo) {
    Thread.ofVirtual().name("eventos-sse").start(() -> escrever(ultimo));
  }

  /** Há eventos gravados novos: junta com uma leitura já pendente. */
  void acordar() {
    if (puxarPendente.compareAndSet(false, true)) {
      oferecer(PUXAR);
    }
  }

  void efemero(Evento evento) {
    oferecer(new Efemero(evento));
  }

  /**
   * Termina o stream pelo escritor, depois do que ele estiver escrevendo; com aviso, o cliente
   * recebe um último evento de transporte ({@code stream.*}).
   */
  void encerrar(@Nullable String aviso) {
    if (!aceitando.compareAndSet(true, false)) {
      return;
    }
    fila.clear();
    if (!fila.offer(new Fim(aviso))) {
      terminarJa();
    }
  }

  private void oferecer(Item item) {
    if (!aceitando.get()) {
      return;
    }
    if (!fila.offer(item)) {
      ambiente.estouros().increment();
      LOG.info("Fila do stream da conta {} cheia: stream fechado", conta);
      terminarJa();
    }
  }

  /** Termina a resposta agora, sem esperar o escritor (que pode estar preso no banco). */
  private void terminarJa() {
    aceitando.set(false);
    fila.clear();
    emissor.complete();
  }

  /** A resposta acabou, por qualquer motivo: tira o stream do registro e solta o escritor. */
  private void concluir() {
    if (concluido.compareAndSet(false, true)) {
      aceitando.set(false);
      fila.clear();
      fila.offer(new Fim(null));
      aoConcluir.accept(this);
    }
  }

  private void escrever(OptionalLong ultimo) {
    try {
      abrir(ultimo);
      while (!concluido.get()) {
        Item item = fila.poll(ambiente.batimento().toMillis(), TimeUnit.MILLISECONDS);
        switch (item) {
          case null -> enviar(BATIMENTO, VAZIO, null);
          case Puxar puxar -> {
            puxarPendente.set(false);
            puxar();
          }
          case Efemero efemero -> enviar(efemero.evento());
          case Fim fim -> {
            if (fim.aviso() != null && !concluido.get()) {
              enviar(fim.aviso(), VAZIO, null);
            }
            if (!concluido.get()) {
              emissor.complete();
            }
            return;
          }
        }
      }
    } catch (IOException e) {
      // O cliente foi embora: nada a escrever.
      concluir();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      terminarJa();
    } catch (RuntimeException e) {
      // Depois de terminada, a resposta recusa o envio (IllegalStateException): não é falha.
      if (aceitando.get()) {
        LOG.warn("Falha no stream da conta {}", conta, e);
      }
      terminarJa();
    }
  }

  /**
   * Com um {@code ultimo} ainda guardado, reenvia o que veio depois (até {@link #REENVIO_MAXIMO});
   * senão, ou se sobrou, manda o estado inicial e segue do evento mais novo. O stream já está no
   * registro: o que for gravado depois da leitura do mais novo chega pela fila.
   */
  private void abrir(OptionalLong ultimo) throws IOException {
    RepositorioDeEventos repositorio = ambiente.repositorio();
    OptionalLong menor = repositorio.menorSeq(conta);
    OptionalLong maior = repositorio.maiorSeq(conta);
    boolean reenviavel =
        ultimo.isPresent()
            && menor.isPresent()
            && ultimo.getAsLong() >= menor.getAsLong()
            && ultimo.getAsLong() <= maior.getAsLong();
    boolean estadoInicial = !reenviavel;
    if (reenviavel) {
      ultimoEnviado = ultimo.getAsLong();
      List<EventoGravado> lote = repositorio.depoisDe(conta, ultimoEnviado, REENVIO_MAXIMO + 1);
      for (EventoGravado gravado : lote.subList(0, Math.min(lote.size(), REENVIO_MAXIMO))) {
        enviar(gravado);
      }
      if (lote.size() > REENVIO_MAXIMO) {
        estadoInicial = true;
        maior = repositorio.maiorSeq(conta);
      }
    }
    if (estadoInicial) {
      ultimoEnviado = maior.orElse(0);
      for (EstadoInicial estado : ambiente.estadosIniciais()) {
        for (Evento evento : estado.para(conta)) {
          enviar(evento);
        }
      }
    }
    if (!primeiroEnviado) {
      emissor.send(SseEmitter.event().reconnectTime(retry));
      primeiroEnviado = true;
    }
  }

  /** Lê do banco o que veio depois do último enviado, em lotes, até acabar. */
  private void puxar() throws IOException {
    List<EventoGravado> lote;
    do {
      lote = ambiente.repositorio().depoisDe(conta, ultimoEnviado, LOTE);
      for (EventoGravado gravado : lote) {
        enviar(gravado);
      }
    } while (lote.size() == LOTE);
  }

  private void enviar(EventoGravado gravado) throws IOException {
    enviar(gravado.nome(), gravado.dadosJson(), gravado.seq());
    ultimoEnviado = gravado.seq();
  }

  private void enviar(Evento evento) throws IOException {
    enviar(evento.nome(), ambiente.json().writeValueAsString(evento.dados()), null);
  }

  private void enviar(String nome, String dadosJson, @Nullable Long id) throws IOException {
    SseEmitter.SseEventBuilder evento = SseEmitter.event().name(nome);
    if (id != null) {
      evento.id(Long.toString(id));
    }
    if (!primeiroEnviado) {
      evento.reconnectTime(retry);
      primeiroEnviado = true;
    }
    emissor.send(evento.data(dadosJson));
  }
}
