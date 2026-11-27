package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.web.ServidorHttpLento;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Prova a montagem dos clientes da AWS contra servidores locais (nada chega à AWS): região de
 * assinatura, credencial só da configuração e desistência pelo timeout da chamada.
 */
class ClientesAwsTest {

  private static final String CHAVE = "AKIACONFIG";
  private static final String ASSINATURA_EM_US_EAST_1 = "/us-east-1/";

  private static final String RESPOSTA_DO_STS =
      """
      <GetCallerIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
        <GetCallerIdentityResult>
          <Arn>arn:aws:iam::000000000000:user/teste</Arn>
          <UserId>TESTE</UserId>
          <Account>000000000000</Account>
        </GetCallerIdentityResult>
        <ResponseMetadata><RequestId>1</RequestId></ResponseMetadata>
      </GetCallerIdentityResponse>
      """;

  @Test
  void rekognitionAssinaEmUsEast1() {
    try (ServidorQueGuardaAssinatura servidor =
            ServidorQueGuardaAssinatura.iniciar("application/x-amz-json-1.1", "{}");
        RekognitionClient rekognition =
            ClientesAws.rekognition(configuracao(), Optional.of(servidor.uri()))) {

      rekognition.getFaceLivenessSessionResults(r -> r.sessionId("sessao"));

      assertThat(servidor.assinatura())
          .contains("Credential=" + CHAVE + "/")
          .contains(ASSINATURA_EM_US_EAST_1 + "rekognition/aws4_request");
    }
  }

  @Test
  void stsAssinaEmUsEast1() {
    try (ServidorQueGuardaAssinatura servidor =
            ServidorQueGuardaAssinatura.iniciar("text/xml", RESPOSTA_DO_STS);
        StsClient sts = ClientesAws.sts(configuracao(), Optional.of(servidor.uri()))) {

      sts.getCallerIdentity();

      assertThat(servidor.assinatura())
          .contains("Credential=" + CHAVE + "/")
          .contains(ASSINATURA_EM_US_EAST_1 + "sts/aws4_request");
    }
  }

  @Test
  void credencialVemSoDaConfiguracao() {
    String anterior = System.getProperty("aws.accessKeyId");
    System.setProperty("aws.accessKeyId", "AKIAOUTRA");
    try (RekognitionClient rekognition =
        ClientesAws.rekognition(configuracao(), Optional.empty())) {

      String resolvida =
          rekognition
              .serviceClientConfiguration()
              .credentialsProvider()
              .resolveIdentity()
              .join()
              .accessKeyId();

      assertThat(resolvida).isEqualTo(CHAVE);
    } finally {
      restaurar(anterior);
    }
  }

  @Test
  void rekognitionLentoFalhaPeloTimeout() {
    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(Duration.ofSeconds(3));
        RekognitionClient rekognition =
            ClientesAws.rekognition(configuracaoApertada(), Optional.of(lento.uri()))) {

      long inicio = System.nanoTime();
      assertThatThrownBy(() -> rekognition.getFaceLivenessSessionResults(r -> r.sessionId("s")))
          .isInstanceOf(SdkClientException.class);
      Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

      assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
    }
  }

  @Test
  void stsLentoFalhaPeloTimeout() {
    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(Duration.ofSeconds(3));
        StsClient sts = ClientesAws.sts(configuracaoApertada(), Optional.of(lento.uri()))) {

      long inicio = System.nanoTime();
      assertThatThrownBy(sts::getCallerIdentity).isInstanceOf(SdkClientException.class);
      Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

      assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
    }
  }

  private static ConfiguracaoAws configuracao() {
    return comTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  private static ConfiguracaoAws configuracaoApertada() {
    return comTimeouts(Duration.ofMillis(200), Duration.ofMillis(500));
  }

  private static ConfiguracaoAws comTimeouts(Duration tentativa, Duration chamada) {
    return new ConfiguracaoAws(
        CHAVE,
        "segredo-de-teste",
        "arn:aws:iam::000000000000:role/duora-captura",
        Duration.ofSeconds(1),
        tentativa,
        chamada);
  }

  private static void restaurar(@Nullable String anterior) {
    if (anterior == null) {
      System.clearProperty("aws.accessKeyId");
    } else {
      System.setProperty("aws.accessKeyId", anterior);
    }
  }

  /** Servidor local que responde de imediato e guarda o cabeçalho {@code Authorization}. */
  private static final class ServidorQueGuardaAssinatura implements AutoCloseable {

    private final HttpServer servidor;
    private final AtomicReference<String> assinatura;

    private ServidorQueGuardaAssinatura(HttpServer servidor, AtomicReference<String> assinatura) {
      this.servidor = servidor;
      this.assinatura = assinatura;
    }

    static ServidorQueGuardaAssinatura iniciar(String tipo, String corpo) {
      AtomicReference<String> assinatura = new AtomicReference<>("");
      try {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> responder(troca, assinatura, tipo, corpo));
        servidor.start();
        return new ServidorQueGuardaAssinatura(servidor, assinatura);
      } catch (IOException e) {
        throw new UncheckedIOException("Não foi possível abrir o servidor HTTP local", e);
      }
    }

    URI uri() {
      return URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
    }

    String assinatura() {
      return assinatura.get();
    }

    @Override
    public void close() {
      servidor.stop(0);
    }

    private static void responder(
        HttpExchange troca, AtomicReference<String> assinatura, String tipo, String corpo)
        throws IOException {
      try (troca) {
        assinatura.set(String.valueOf(troca.getRequestHeaders().getFirst("Authorization")));
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", tipo);
        troca.sendResponseHeaders(200, bytes.length);
        troca.getResponseBody().write(bytes);
      }
    }
  }
}
