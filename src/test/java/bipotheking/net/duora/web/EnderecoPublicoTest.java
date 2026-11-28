package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EnderecoPublicoTest {

  private final EnderecoPublico publico = new EnderecoPublico("https://meetduora.com");

  @ParameterizedTest
  @ValueSource(strings = {"meetduora.com", "MeetDuora.com", "meetduora.com:443"})
  void hostPublicoNaPortaDaUrlEhAceito(String host) {
    assertThat(publico.hostPermitido(host)).isTrue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "evil.com",
        "meetduora.com.evil.com",
        "evilmeetduora.com",
        "www.meetduora.com",
        "meetduora.com.",
        "meetduora.com:8080",
        "meetduora.com:80",
        "evil.com@meetduora.com",
        "meetduora.com@evil.com"
      })
  void hostForaDoPublicoEhRecusado(@Nullable String host) {
    assertThat(publico.hostPermitido(host)).isFalse();
  }

  @Test
  void hostComPortaExplicitaNaUrlExigeEssaPorta() {
    EnderecoPublico dev = new EnderecoPublico("http://127.0.0.1:8080");

    assertThat(dev.hostPermitido("127.0.0.1:8080")).isTrue();
    assertThat(dev.hostPermitido("127.0.0.1")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://meetduora.com", "https://MEETDUORA.com"})
  void origemPublicaEhAceita(String origem) {
    assertThat(publico.origemPermitida(origem)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://meetduora.com",
        "https://meetduora.com.evil.com",
        "https://evilmeetduora.com",
        "https://www.meetduora.com",
        "https://meetduora.com:8443",
        "https://meetduora.com/",
        "https://x@meetduora.com",
        "null",
        ""
      })
  void origemForaDaPublicaEhRecusada(String origem) {
    assertThat(publico.origemPermitida(origem)).isFalse();
  }

  @Test
  void origemComPortaExplicitaNaUrlExigeEssaPorta() {
    EnderecoPublico dev = new EnderecoPublico("http://127.0.0.1:8080");

    assertThat(dev.origemPermitida("http://127.0.0.1:8080")).isTrue();
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
        "https://meetduora.com?a=1",
        "https://meetduora.com#x",
        "https://x@meetduora.com",
        "https://meet duora.com"
      })
  void urlPublicaMalformadaDerrubaOBoot(@Nullable String url) {
    assertThatThrownBy(() -> new EnderecoPublico(url))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("duora.url-publica");
  }
}
