package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Os timeouts padrão da AWS cabem na reserva de idempotência: duas chamadas no {@code resultado} da
 * prova de vida e, no {@code conferir} do documento, até três (frente, verso e rosto), cada uma com
 * as tentativas dentro do teto da chamada.
 */
class OrcamentoDeTempoTest {

  @Test
  void padroesCabemNaReserva() throws IOException {
    Binder vazio = new Binder(new MapConfigurationPropertySource(Map.of()));
    ConfiguracaoAws aws =
        vazio.bindOrCreate("duora.verificacao.aws", Bindable.of(ConfiguracaoAws.class));
    Duration reservaMaxima =
        Duration.parse(
            PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("duora.idempotencia.reserva-maxima"));

    Duration resultado = aws.chamada().multipliedBy(2);
    Duration conferir = aws.chamada().multipliedBy(3);

    assertThat(resultado).isLessThanOrEqualTo(Duration.ofSeconds(10));
    assertThat(conferir).isLessThanOrEqualTo(Duration.ofSeconds(15));
    assertThat(resultado.plus(conferir)).isLessThan(reservaMaxima);
  }
}
