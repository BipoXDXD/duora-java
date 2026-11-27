package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** O {@code RestClient.Builder} do Boot, com o mesmo Jackson estrito da aplicação. */
class TurnstileComBuilderDoBootTest {

  /** Builder do Boot com o JSON estrito da aplicação ({@code application.properties}). */
  static final ApplicationContextRunner CONTEXTO =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  RestClientAutoConfiguration.class))
          .withPropertyValues("spring.jackson.deserialization.fail-on-unknown-properties=true");

  private static final URI URL =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");

  @Test
  void respostaRealCompletaDaTrue() {
    CONTEXTO.run(contexto -> {
      RestClient.Builder builder = contexto.getBean(RestClient.Builder.class);
      MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
      server
          .expect(requestTo(URL))
          .andRespond(
              withSuccess(
                  """
                  {"success": true, "challenge_ts": "2026-11-27T12:00:00.000Z",
                   "hostname": "meetduora.com", "error-codes": [], "action": "entrar",
                   "cdata": "sessao-123"}
                  """,
                  MediaType.APPLICATION_JSON));
      TurnstileVerificacao verificacao =
          new TurnstileVerificacao(builder, new TurnstileConfig("chave", "segredo", URL));

      assertThat(verificacao.verificar("token", "203.0.113.7")).isTrue();
      server.verify();
    });
  }

  @Test
  void segredoComPlaceholderSemResolverDerrubaOContexto() {
    new ApplicationContextRunner()
        .withUserConfiguration(ComTurnstileConfig.class)
        .withPropertyValues(
            "duora.turnstile.chave-do-site=1x00000000000000000000AA",
            "duora.turnstile.segredo=${TURNSTILE_SECRET_INEXISTENTE}")
        .run(contexto ->
            assertThat(contexto)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.turnstile.segredo"));
  }

  @EnableConfigurationProperties(TurnstileConfig.class)
  static class ComTurnstileConfig {}
}
