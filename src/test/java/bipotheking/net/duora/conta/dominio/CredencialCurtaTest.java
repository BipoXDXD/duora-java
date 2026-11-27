package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CredencialCurtaTest {

  @Test
  void toStringSemSegredoNemToken() {
    var credencial =
        new CredencialCurta(
            "AKIA-TESTE", "segredo-x", "token-y", Instant.parse("2026-11-22T12:15:00Z"));

    assertThat(credencial.toString())
        .contains("AKIA-TESTE")
        .doesNotContain("segredo-x")
        .doesNotContain("token-y");
  }
}
