package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.notificacao.EnvioDeEmail;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Qual envio de e-mail o contexto monta, e quando ele se recusa a subir. */
class EmailConfigTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  RestClientAutoConfiguration.class))
          .withUserConfiguration(ComEmail.class)
          .withPropertyValues("duora.email.remetente=Duora <entrar@meetduora.com>");

  @Test
  void resendSemChaveFalha() {
    contexto
        .withPropertyValues("duora.email.provedor=resend")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.email.resend.chave"));
  }

  @Test
  void resendComPlaceholderSemResolverFalha() {
    contexto
        .withPropertyValues(
            "duora.email.provedor=resend", "duora.email.resend.chave=${RESEND_API_KEY_INEXISTENTE}")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.email.resend.chave")
                .hasMessageNotContaining("RESEND_API_KEY_INEXISTENTE"));
  }

  @Test
  void resendComChaveEscolheEmailResend() {
    contexto
        .withPropertyValues("duora.email.provedor=resend", "duora.email.resend.chave=re_teste")
        .run(c -> assertThat(c).getBean(EnvioDeEmail.class).isInstanceOf(EmailResend.class));
  }

  @Test
  void logEscolheEmailNoLog() {
    contexto
        .withPropertyValues("duora.email.provedor=log")
        .run(c -> assertThat(c).getBean(EnvioDeEmail.class).isInstanceOf(EmailNoLog.class));
  }

  @Test
  void prodComLogFalha() {
    contexto
        .withPropertyValues("duora.email.provedor=log", "spring.profiles.active=prod")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.email.provedor"));
  }

  @Test
  void provedorDesconhecidoFalha() {
    contexto
        .withPropertyValues("duora.email.provedor=smtp")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.email.provedor"));
  }

  @Test
  void timeoutsPadrao() {
    contexto
        .withPropertyValues("duora.email.provedor=log")
        .run(c -> {
          ConfiguracaoDeEmail.Resend resend = c.getBean(ConfiguracaoDeEmail.class).resend();
          assertThat(resend.conexao()).isEqualTo(Duration.ofSeconds(5));
          assertThat(resend.leitura()).isEqualTo(Duration.ofSeconds(10));
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ConfiguracaoDeEmail.class)
  @Import(EmailConfig.class)
  static class ComEmail {}
}
