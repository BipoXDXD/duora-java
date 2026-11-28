package bipotheking.net.duora.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.net.URI;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A allowlist do endpoint de push contra SSRF (Foco 5). */
class EnderecoDePushTest {

  private static final Set<String> HOSTS =
      Set.of(
          "fcm.googleapis.com",
          "updates.push.services.mozilla.com",
          "push.apple.com",
          "notify.windows.com");

  private static final String BASE = "https://fcm.googleapis.com/";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://fcm.googleapis.com/fcm/send/abc",
        "https://web.push.apple.com/x",
        "https://updates.push.services.mozilla.com/wpush/v2/x",
        "https://wns2-bl2p.notify.windows.com/w/?token=x"
      })
  void aceitaEndpointDeServicoConhecido(String bruto) {
    assertThat(EnderecoDePush.validar(bruto, HOSTS)).isEqualTo(URI.create(bruto));
  }

  static Stream<String> recusados() {
    return Stream.of(
        "http://fcm.googleapis.com/x",
        "https://127.0.0.1/x",
        "https://[::1]/x",
        "https://169.254.169.254/x",
        "https://10.0.0.1/x",
        "https://2130706433/x",
        "https://fcm.googleapis.com.evil.example/x",
        "https://evilfcm.googleapis.com.example/x",
        "https://user@fcm.googleapis.com/x",
        "https://fcm.googleapis.com:8443/x",
        "javascript:alert(1)",
        BASE + "a".repeat(1025 - BASE.length()));
  }

  @ParameterizedTest
  @MethodSource("recusados")
  void recusaEndpointForaDaAllowlist(String bruto) {
    assertThatThrownBy(() -> EnderecoDePush.validar(bruto, HOSTS))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPush.INSCRICAO_INVALIDA));
  }

  @Test
  void aceitaExatamente1024Caracteres() {
    String bruto = BASE + "a".repeat(1024 - BASE.length());

    assertThat(EnderecoDePush.validar(bruto, HOSTS)).hasToString(bruto);
  }

  @Test
  void hostLiteralRecusadoMesmoNaAllowlist() {
    assertThatThrownBy(() -> EnderecoDePush.validar("https://10.0.0.1/x", Set.of("10.0.0.1")))
        .isInstanceOf(ErroDeNegocio.class);
  }
}
