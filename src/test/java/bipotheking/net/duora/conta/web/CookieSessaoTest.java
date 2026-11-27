package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class CookieSessaoTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(CookieSessao.class);

  @Test
  void semSecureQuandoOCookieSeguroEstaDesligado() {
    String cookie = new CookieSessao(false, new MockEnvironment()).emitir("token");

    assertThat(cookie)
        .startsWith("duora_sessao=token;")
        .contains("HttpOnly", "SameSite=Lax", "Max-Age=2592000")
        .doesNotContain("Secure");
  }

  @Test
  void expirarApagaOMesmoCookieComTodosOsAtributos() {
    String cookie = new CookieSessao(true, new MockEnvironment()).expirar();

    assertThat(cookie)
        .startsWith("duora_sessao=;")
        .contains("; Path=/;", "; Max-Age=0;", "; Secure", "; HttpOnly", "; SameSite=Lax");
  }

  @Test
  void prodNaoSobeComOCookieSeguroDesligado() {
    contexto
        .withPropertyValues("duora.sessao.cookie-seguro=false")
        .withInitializer(aplicacao -> aplicacao.getEnvironment().setActiveProfiles("prod"))
        .run(aplicacao ->
            assertThat(aplicacao)
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.sessao.cookie-seguro"));
  }

  @Test
  void prodSobeComOCookieSeguroPorPadrao() {
    contexto
        .withInitializer(aplicacao -> aplicacao.getEnvironment().setActiveProfiles("prod"))
        .run(aplicacao -> assertThat(aplicacao).hasNotFailed());
  }

  @Test
  void foraDeProdOCookieSeguroPodeSerDesligado() {
    contexto
        .withPropertyValues("duora.sessao.cookie-seguro=false")
        .run(aplicacao -> assertThat(aplicacao).hasNotFailed());
  }
}
