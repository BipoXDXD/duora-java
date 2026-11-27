package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CookieSessaoTest {

  @Test
  void semSecureQuandoOCookieSeguroEstaDesligado() {
    String cookie = new CookieSessao(false).emitir("token");

    assertThat(cookie)
        .startsWith("duora_sessao=token;")
        .contains("HttpOnly", "SameSite=Lax", "Max-Age=2592000")
        .doesNotContain("Secure");
  }
}
