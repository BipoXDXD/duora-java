package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TurnstileConfigTest {

  private static final URI URL =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "${TURNSTILE_SECRET}"})
  void segredoAusenteEhRecusado(String segredo) {
    assertThatThrownBy(() -> new TurnstileConfig("1x00000000000000000000AA", segredo, URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.turnstile.segredo");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "${TURNSTILE_SITE_KEY}"})
  void chaveDoSiteAusenteEhRecusada(String chave) {
    assertThatThrownBy(() -> new TurnstileConfig(chave, "1x0000000000000000000000000000000AA", URL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.turnstile.chave-do-site");
  }
}
