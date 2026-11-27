package bipotheking.net.duora.notificacao.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Escolhe o {@link bipotheking.net.duora.notificacao.EnvioDeEmail} por {@code
 * duora.email.provedor}. A {@link ConfiguracaoDeEmail} já recusou provedor desconhecido e Resend
 * sem chave.
 */
@Configuration(proxyBeanMethods = false)
class EmailConfig {

  private static final String PROVEDOR = "duora.email.provedor";
  private static final String PROD = "prod";

  @Bean
  @ConditionalOnProperty(name = PROVEDOR, havingValue = ConfiguracaoDeEmail.RESEND)
  EmailResend emailResend(RestClient.Builder builder, ConfiguracaoDeEmail config) {
    return EmailResend.comTimeouts(builder, config, EmailResend.API);
  }

  /** Recusa o perfil {@code prod}: lá, o e-mail com o link de entrada sumiria no log. */
  @Bean
  @ConditionalOnProperty(name = PROVEDOR, havingValue = ConfiguracaoDeEmail.LOG)
  EmailNoLog emailNoLog(Environment ambiente) {
    if (ambiente.matchesProfiles(PROD)) {
      throw new IllegalStateException(
          PROVEDOR + "=" + ConfiguracaoDeEmail.LOG + " não é permitido no perfil " + PROD);
    }
    return new EmailNoLog();
  }
}
