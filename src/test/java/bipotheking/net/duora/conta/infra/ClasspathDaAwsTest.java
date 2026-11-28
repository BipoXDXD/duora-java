package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Trava o cliente HTTP do SDK da AWS: só o {@code url-connection-client} do JDK, sem Apache nem
 * Netty puxados por {@code rekognition} e {@code sts}.
 */
class ClasspathDaAwsTest {

  @Test
  void semClienteApache() {
    assertThatThrownBy(() -> Class.forName("software.amazon.awssdk.http.apache.ApacheHttpClient"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  /** Desde a 2.59, o pom pai dos serviços puxa o Apache 5 (não mais o Apache 4) em runtime. */
  @Test
  void semClienteApache5() {
    assertThatThrownBy(() -> Class.forName("software.amazon.awssdk.http.apache5.Apache5HttpClient"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void semClienteNetty() {
    assertThatThrownBy(
            () -> Class.forName("software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void comClienteUrlConnection() {
    assertThatCode(() ->
            Class.forName("software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient"))
        .doesNotThrowAnyException();
  }
}
