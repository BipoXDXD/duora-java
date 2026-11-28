package bipotheking.net.duora.conta.infra;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;

/**
 * Didit falsa: {@code HttpServer} em {@code 127.0.0.1}, porta efêmera. Responde por caminho com o
 * status, o corpo JSON e o atraso combinados (caminho sem resposta recebe 404) e grava cada
 * requisição (método, caminho, cabeçalhos e corpo cru) antes de responder. Fechar interrompe as
 * respostas ainda dormindo.
 */
final class ServidorDiditFalso implements AutoCloseable {

  private static final String HOST = "127.0.0.1";

  /** Uma requisição recebida, com o corpo como chegou. */
  record Requisicao(String metodo, String caminho, Headers cabecalhos, byte[] corpo) {

    /** Valor do cabeçalho, ignorando maiúsculas, ou {@code null}. */
    @Nullable String cabecalho(String nome) {
      return cabecalhos.getFirst(nome);
    }

    /** O corpo byte a byte em ISO-8859-1: binário e texto ASCII sobrevivem intactos. */
    String corpoComoTexto() {
      return new String(corpo, StandardCharsets.ISO_8859_1);
    }

    /**
     * Valor do campo de texto {@code nome} do corpo multipart: o que vem depois da linha em branco
     * que fecha os cabeçalhos da parte, até a fronteira seguinte. Falha com {@link AssertionError}
     * se o campo não existir ou se a parte estiver malformada.
     */
    String campo(String nome) {
      String texto = corpoComoTexto();
      int parte = texto.indexOf("name=\"" + nome + "\"\r\n");
      if (parte < 0) {
        throw new AssertionError("O corpo não tem o campo " + nome);
      }
      int fimDosCabecalhos = texto.indexOf("\r\n\r\n", parte);
      if (fimDosCabecalhos < 0) {
        throw new AssertionError(
            "A parte " + nome + " não fecha os cabeçalhos com linha em branco");
      }
      int valor = fimDosCabecalhos + 4;
      int fronteira = texto.indexOf("\r\n--", valor);
      if (fronteira < 0) {
        throw new AssertionError("A parte " + nome + " não termina numa fronteira");
      }
      return texto.substring(valor, fronteira);
    }

    /**
     * Bytes do arquivo {@code nome} do corpo multipart (parte com {@code filename}), entre a linha
     * em branco dos cabeçalhos e a fronteira seguinte. Falha com {@link AssertionError} se a parte
     * não existir ou estiver malformada.
     */
    byte[] arquivo(String nome) {
      String texto = corpoComoTexto();
      int parte = texto.indexOf("name=\"" + nome + "\"; filename=");
      if (parte < 0) {
        throw new AssertionError("O corpo não tem o arquivo " + nome);
      }
      int fimDosCabecalhos = texto.indexOf("\r\n\r\n", parte);
      int fronteira = fimDosCabecalhos < 0 ? -1 : texto.indexOf("\r\n--", fimDosCabecalhos + 4);
      if (fronteira < 0) {
        throw new AssertionError("A parte " + nome + " está malformada");
      }
      return texto.substring(fimDosCabecalhos + 4, fronteira).getBytes(StandardCharsets.ISO_8859_1);
    }
  }

  private record Resposta(int status, String corpo, Duration atraso) {}

  private final HttpServer servidor;
  private final ExecutorService threads;
  private final Map<String, Resposta> respostas = new ConcurrentHashMap<>();
  private final List<Requisicao> requisicoes = new CopyOnWriteArrayList<>();

  private ServidorDiditFalso(HttpServer servidor, ExecutorService threads) {
    this.servidor = servidor;
    this.threads = threads;
  }

  static ServidorDiditFalso iniciar() {
    ExecutorService threads = Executors.newCachedThreadPool();
    try {
      HttpServer servidor = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
      servidor.setExecutor(threads);
      ServidorDiditFalso falso = new ServidorDiditFalso(servidor, threads);
      servidor.createContext("/", falso::atender);
      servidor.start();
      return falso;
    } catch (IOException e) {
      threads.shutdownNow();
      throw new UncheckedIOException("Não foi possível abrir a Didit falsa", e);
    }
  }

  /** A raiz do servidor, sem barra no fim ({@code http://127.0.0.1:<porta>}). */
  URI uri() {
    return URI.create("http://" + HOST + ":" + servidor.getAddress().getPort());
  }

  ServidorDiditFalso responder(String caminho, int status, String corpo) {
    return responder(caminho, status, corpo, Duration.ZERO);
  }

  ServidorDiditFalso responder(String caminho, int status, String corpo, Duration atraso) {
    respostas.put(caminho, new Resposta(status, corpo, atraso));
    return this;
  }

  List<Requisicao> requisicoes() {
    return List.copyOf(requisicoes);
  }

  /** A única requisição recebida em {@code caminho}; falha se houver zero ou mais de uma. */
  Requisicao requisicaoEm(String caminho) {
    List<Requisicao> doCaminho =
        requisicoes.stream().filter(r -> r.caminho().equals(caminho)).toList();
    if (doCaminho.size() != 1) {
      throw new AssertionError(
          "Esperava 1 requisição em " + caminho + ", chegaram " + doCaminho.size());
    }
    return doCaminho.getFirst();
  }

  @Override
  public void close() {
    servidor.stop(0);
    threads.shutdownNow();
  }

  private void atender(HttpExchange troca) throws IOException {
    try (troca) {
      String caminho = troca.getRequestURI().getPath();
      byte[] corpoDoPedido = troca.getRequestBody().readAllBytes();
      Headers copia = new Headers();
      copia.putAll(troca.getRequestHeaders());
      requisicoes.add(new Requisicao(troca.getRequestMethod(), caminho, copia, corpoDoPedido));
      Resposta resposta = respostas.getOrDefault(caminho, new Resposta(404, "{}", Duration.ZERO));
      Thread.sleep(resposta.atraso());
      byte[] corpo = resposta.corpo().getBytes(StandardCharsets.UTF_8);
      troca.getResponseHeaders().add("Content-Type", "application/json");
      troca.sendResponseHeaders(resposta.status(), corpo.length);
      troca.getResponseBody().write(corpo);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
