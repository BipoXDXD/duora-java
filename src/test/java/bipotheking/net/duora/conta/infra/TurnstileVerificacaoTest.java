package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class TurnstileVerificacaoTest {

  private static final URI URL =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
  private static final String SEGREDO = "segredo-CANARY-teste";
  private static final String TOKEN = "token-CANARY-teste";
  private static final String IP = "203.0.113.7";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final TurnstileVerificacao verificacao =
      new TurnstileVerificacao(builder, new TurnstileConfig("chave-do-site", SEGREDO, URL));

  private void responder(String json) {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
  }

  @Test
  void sucessoTrue() {
    responder(
        """
        {"success": true, "challenge_ts": "2026-11-27T12:00:00Z", "hostname": "meetduora.com",
         "error-codes": [], "action": "", "cdata": ""}
        """);

    assertThat(verificacao.verificar(TOKEN, IP)).isTrue();
    server.verify();
  }

  @Test
  void sucessoFalse() {
    responder(
        """
        {"success": false, "error-codes": ["invalid-input-response"]}
        """);

    assertThat(verificacao.verificar(TOKEN, IP)).isFalse();
    server.verify();
  }

  @Test
  void erro500ViraFalse(CapturedOutput saida) {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThat(verificacao.verificar(TOKEN, IP)).isFalse();
    assertThat(saida.getOut()).contains("WARN").doesNotContain(TOKEN).doesNotContain(SEGREDO);
  }

  @Test
  void excecaoDeTimeoutViraFalse(CapturedOutput saida) {
    server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException()));

    assertThat(verificacao.verificar(TOKEN, IP)).isFalse();
    assertThat(saida.getOut()).contains("WARN").doesNotContain(TOKEN).doesNotContain(SEGREDO);
  }

  @Test
  void corpoTemSecretResponseERemoteip() {
    server
        .expect(requestTo(URL))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(
            content()
                .formDataContains(Map.of("secret", SEGREDO, "response", TOKEN, "remoteip", IP)))
        .andRespond(withSuccess("{\"success\": true}", MediaType.APPLICATION_JSON));

    verificacao.verificar(TOKEN, IP);
    server.verify();
  }

  @Test
  void tokenVazioNaoFazRequisicao() {
    assertThat(verificacao.verificar("", IP)).isFalse();
    server.verify();
  }

  @Test
  void tokenDe2049NaoFazRequisicao() {
    assertThat(verificacao.verificar("a".repeat(2049), IP)).isFalse();
    server.verify();
  }

  @Test
  void respostaQueDemora6sViraFalseEmMenosDe6s() throws IOException {
    Duration atraso = Duration.ofSeconds(6);
    ExecutorService threads = Executors.newCachedThreadPool();
    HttpServer lento =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    lento.setExecutor(threads);
    lento.createContext(
        "/",
        troca -> {
          try (troca) {
            Thread.sleep(atraso);
            byte[] corpo = "{\"success\": true}".getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            troca.getResponseBody().write(corpo);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    lento.start();
    try {
      URI url = URI.create("http://127.0.0.1:" + lento.getAddress().getPort() + "/siteverify");
      TurnstileVerificacao comTimeouts =
          TurnstileVerificacao.comTimeouts(
              RestClient.builder(), new TurnstileConfig("chave-do-site", SEGREDO, url));

      long inicio = System.nanoTime();
      boolean resultado = comTimeouts.verificar(TOKEN, IP);
      Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

      assertThat(resultado).isFalse();
      assertThat(decorrido).isLessThan(atraso);
    } finally {
      lento.stop(0);
      threads.shutdownNow();
    }
  }
}
