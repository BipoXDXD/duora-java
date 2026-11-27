package bipotheking.net.duora.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP local, em porta efêmera, que espera um atraso antes de responder 200 com {@code {}}
 * em JSON. Serve para provar que um cliente desiste pelo timeout de leitura. Fechar interrompe as
 * respostas ainda dormindo.
 */
public final class ServidorHttpLento implements AutoCloseable {

  private static final String HOST = "127.0.0.1";

  private final HttpServer servidor;
  private final ExecutorService threads;

  private ServidorHttpLento(HttpServer servidor, ExecutorService threads) {
    this.servidor = servidor;
    this.threads = threads;
  }

  /** Sobe o servidor; toda requisição, em qualquer caminho, espera {@code atraso}. */
  public static ServidorHttpLento iniciar(Duration atraso) {
    ExecutorService threads = Executors.newCachedThreadPool();
    try {
      HttpServer servidor = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
      servidor.setExecutor(threads);
      servidor.createContext("/", troca -> responderDepois(troca, atraso));
      servidor.start();
      return new ServidorHttpLento(servidor, threads);
    } catch (IOException e) {
      threads.shutdownNow();
      throw new UncheckedIOException("Não foi possível abrir o servidor HTTP lento", e);
    }
  }

  /** A raiz do servidor ({@code http://127.0.0.1:<porta>/}). */
  public URI uri() {
    return URI.create("http://" + HOST + ":" + servidor.getAddress().getPort() + "/");
  }

  @Override
  public void close() {
    servidor.stop(0);
    threads.shutdownNow();
  }

  private static void responderDepois(HttpExchange troca, Duration atraso) throws IOException {
    try (troca) {
      Thread.sleep(atraso);
      byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
      troca.getResponseHeaders().add("Content-Type", "application/json");
      troca.sendResponseHeaders(200, corpo.length);
      troca.getResponseBody().write(corpo);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
