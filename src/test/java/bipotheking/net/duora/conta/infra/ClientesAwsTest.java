package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.web.CabecalhosDeSeguranca;
import bipotheking.net.duora.web.ServidorHttpLento;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Prova a montagem dos clientes da AWS contra servidores locais (nada chega à AWS): região de
 * assinatura, credencial só da configuração e desistência pelo timeout da chamada.
 */
class ClientesAwsTest {

  /**
   * O navegador abre o WebSocket da prova de vida na região dos clientes: a CSP da verificação
   * precisa liberar essa mesma origem, senão a selfie para no navegador.
   */
  @Test
  void cspDaVerificacaoLiberaOStreamingDaRegiaoDosClientes() {
    assertThat(CabecalhosDeSeguranca.CSP_VERIFICACAO)
        .contains("wss://streaming-rekognition." + ClientesAws.REGIAO.id() + ".amazonaws.com");
  }

  private static final StaticCredentialsProvider CURTA =
      StaticCredentialsProvider.create(
          AwsSessionCredentials.create("ASIACURTA", "segredo-curto", "token-curto"));

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
  void credencialInformadaAssinaNoLugarDaConfiguracao() {
    try (ServidorQueGuardaAssinatura servidor =
            ServidorQueGuardaAssinatura.iniciar("application/x-amz-json-1.1", "{}");
        RekognitionClient rekognition =
            ClientesAws.rekognition(configuracao(), CURTA, Optional.of(servidor.uri()))) {

      rekognition.getFaceLivenessSessionResults(r -> r.sessionId("sessao"));

      assertThat(servidor.assinatura())
          .contains("Credential=ASIACURTA/")
          .contains(ASSINATURA_EM_US_EAST_1 + "rekognition/aws4_request");
    }
  }

  @Test
  void credencialInformadaMantemOTimeout() {
    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(Duration.ofSeconds(3));
        StsClient sts = ClientesAws.sts(configuracaoApertada(), CURTA, Optional.of(lento.uri()))) {

      long inicio = System.nanoTime();
      assertThatThrownBy(sts::getCallerIdentity).isInstanceOf(SdkClientException.class);
      Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

      assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
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
      restaurar("aws.accessKeyId", anterior);
    }
  }

  /** {@code AWS_ENDPOINT_URL*} e {@code aws.endpointUrl*} não desviam o cliente de produção. */
  @Test
  void endpointDoAmbienteNaoDesviaOCliente() {
    String anterior = System.getProperty("aws.endpointUrl");
    System.setProperty("aws.endpointUrl", "http://127.0.0.1:9/");
    try (RekognitionClient rekognition = ClientesAws.rekognition(configuracao(), Optional.empty());
        StsClient sts = ClientesAws.sts(configuracao(), Optional.empty())) {

      assertThat(rekognition.serviceClientConfiguration().endpointOverride())
          .contains(URI.create("https://rekognition.us-east-1.amazonaws.com"));
      assertThat(sts.serviceClientConfiguration().endpointOverride())
          .contains(URI.create("https://sts.us-east-1.amazonaws.com"));
    } finally {
      restaurar("aws.endpointUrl", anterior);
    }
  }

  /**
   * O {@code ~/.aws/config} (aqui, pelo {@code aws.configFile}) não liga FIPS, dual-stack nem
   * endpoint: o cliente chama o endpoint informado como se o arquivo não existisse.
   */
  @Test
  void arquivoDeConfiguracaoDaAwsNaoMudaOCliente(@TempDir Path pasta) throws IOException {
    Path arquivo = pasta.resolve("config");
    Files.writeString(
        arquivo,
        """
        [default]
        use_fips_endpoint = true
        use_dualstack_endpoint = true
        endpoint_url = http://127.0.0.1:9/
        """);
    String anterior = System.getProperty("aws.configFile");
    System.setProperty("aws.configFile", arquivo.toString());
    try (ServidorQueGuardaAssinatura servidor =
            ServidorQueGuardaAssinatura.iniciar("application/x-amz-json-1.1", "{}");
        RekognitionClient rekognition =
            ClientesAws.rekognition(configuracao(), Optional.of(servidor.uri()))) {

      rekognition.getFaceLivenessSessionResults(r -> r.sessionId("sessao"));

      assertThat(servidor.assinatura()).contains(ASSINATURA_EM_US_EAST_1 + "rekognition/");
    } finally {
      restaurar("aws.configFile", anterior);
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

  private static void restaurar(String propriedade, @Nullable String anterior) {
    if (anterior == null) {
      System.clearProperty(propriedade);
    } else {
      System.setProperty(propriedade, anterior);
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
