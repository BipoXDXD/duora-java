package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.DefinicaoDeAbertura;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CenariosConfigTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(CenariosConfig.class);

  @Test
  void maximoDaAberturaVemDaPropriedade() {
    contexto
        .withPropertyValues("duora.cenarios.abertura.maximo=PT3S")
        .run(ctx ->
            assertThat(
                    ctx.getBean("terraco", Cenario.class)
                        .abertura()
                        .map(DefinicaoDeAbertura::maximo))
                .contains(Duration.ofSeconds(3)));
  }
}
