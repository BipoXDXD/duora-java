package bipotheking.net.duora.push.infra;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Escolhe o {@link bipotheking.net.duora.push.EnvioDePush} por {@code duora.push.habilitado}. Sem a
 * propriedade, o push fica desligado. A {@link ConfiguracaoPush} já recusou ligado sem as chaves.
 */
@Configuration(proxyBeanMethods = false)
class PushConfig {

  private static final String HABILITADO = "duora.push.habilitado";

  @Bean
  @ConditionalOnProperty(name = HABILITADO, havingValue = "true")
  WebPushInterasoAdapter webPushInterasoAdapter(ConfiguracaoPush config) {
    return new WebPushInterasoAdapter(config);
  }

  @Bean
  @ConditionalOnProperty(name = HABILITADO, havingValue = "false", matchIfMissing = true)
  EnvioDePushNoLog envioDePushNoLog() {
    return new EnvioDePushNoLog();
  }
}
