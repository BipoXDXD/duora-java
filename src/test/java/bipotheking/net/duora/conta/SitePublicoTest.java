package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SitePublicoTest {

  @Test
  void enderecoJuntaACaminho() {
    assertThat(new SitePublico("https://meetduora.com").endereco("/entrar"))
        .isEqualTo("https://meetduora.com/entrar");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        " ",
        "${DUORA_URL_PUBLICA}",
        "meetduora.com",
        "/entrar",
        "ftp://meetduora.com",
        "https://meetduora.com/",
        "https://meetduora.com?a=1",
        "https://meetduora.com#x",
        "https://meet duora.com"
      })
  void urlForaDaFormaDerrubaOBoot(@Nullable String url) {
    assertThatThrownBy(() -> new SitePublico(url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("duora.url-publica");
  }
}
