package bipotheking.net.duora.room;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SalasConfig {

  @Bean
  LimitesSala limitesSala() {
    return LimitesSala.PADRAO;
  }
}
