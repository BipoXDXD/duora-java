package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Infraestrutura comum a todos os módulos: relógio, aleatório, ids e agendamento. */
@Configuration
@EnableScheduling
class BaseConfig {

  @Bean
  Clock relogio() {
    return Clock.systemUTC();
  }

  @Bean
  RandomGenerator aleatorio() {
    return new SecureRandom();
  }

  @Bean
  GeradorDeIds geradorDeIds(Clock relogio, RandomGenerator aleatorio) {
    return new GeradorDeIds(relogio, aleatorio);
  }
}
