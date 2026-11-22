package bipotheking.net.duora.room;

import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.Terraco;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registro dos cenários jogáveis: um cenário novo é um {@code @Bean Cenario} a mais. */
@Configuration
class CenariosConfig {

  @Bean
  Cenario terraco() {
    return Terraco.CENARIO;
  }

  @Bean
  CatalogoDeCenarios catalogoDeCenarios(List<Cenario> cenarios) {
    return new CatalogoDeCenarios(cenarios);
  }
}
