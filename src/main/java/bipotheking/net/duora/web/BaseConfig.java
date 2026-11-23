package bipotheking.net.duora.web;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Infraestrutura comum a todos os módulos: o relógio e o agendamento. */
@Configuration
@EnableScheduling
class BaseConfig {

  @Bean
  Clock relogio() {
    return Clock.systemUTC();
  }
}
