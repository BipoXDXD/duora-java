package bipotheking.net.duora.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Troca o relógio do sistema pelo {@link RelogioDeTeste} nos testes com banco. */
@TestConfiguration(proxyBeanMethods = false)
public class RelogioDeTesteConfig {

  @Bean
  @Primary
  RelogioDeTeste relogioDeTeste() {
    return new RelogioDeTeste(BancoDeTeste.INICIO);
  }
}
