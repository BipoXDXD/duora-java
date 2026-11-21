package bipotheking.net.duora.room;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class SalasConfig {

  @Bean
  Clock relogio() {
    return Clock.systemUTC();
  }

  @Bean
  LimitesSala limitesSala() {
    return LimitesSala.PADRAO;
  }
}
