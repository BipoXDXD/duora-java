package bipotheking.net.duora.room;

import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.Terraco;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Registro dos cenários jogáveis: um cenário novo é um {@code @Bean Cenario} a mais. */
@Configuration
class CenariosConfig {

  /**
   * Troca o máximo da abertura dos cenários (ex.: {@code PT0S} conclui apagada já na entrada, como
   * na suíte e2e). Sem a propriedade, vale o máximo que o cenário declara.
   */
  private static final String MAXIMO_DA_ABERTURA = "duora.cenarios.abertura.maximo";

  @Bean
  Cenario terraco(Environment ambiente) {
    return Binder.get(ambiente)
        .bind(MAXIMO_DA_ABERTURA, Duration.class)
        .map(Terraco.CENARIO::comMaximoDeAbertura)
        .orElse(Terraco.CENARIO);
  }

  @Bean
  CatalogoDeCenarios catalogoDeCenarios(List<Cenario> cenarios) {
    return new CatalogoDeCenarios(cenarios);
  }
}
