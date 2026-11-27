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
 * Os timeouts padrão dos fornecedores cabem na reserva de idempotência: a AWS faz até duas
 * tentativas no {@code resultado} e a Didit confere documento e rosto no {@code conferir}.
 */
class OrcamentoDeTempoTest {

  @Test
  void padroesCabemNaReserva() throws IOException {
    Binder vazio = new Binder(new MapConfigurationPropertySource(Map.of()));
    ConfiguracaoAws aws =
        vazio.bindOrCreate("duora.verificacao.aws", Bindable.of(ConfiguracaoAws.class));
    ConfiguracaoDidit didit =
        vazio.bindOrCreate("duora.verificacao.didit", Bindable.of(ConfiguracaoDidit.class));
    Duration reservaMaxima =
        Duration.parse(
            PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("duora.idempotencia.reserva-maxima"));

    Duration resultado = aws.chamada().multipliedBy(2);
    Duration conferir =
        didit
            .conexao()
            .plus(didit.leituraDoDocumento())
            .plus(didit.conexao())
            .plus(didit.leituraDoRosto());

    assertThat(resultado).isLessThanOrEqualTo(Duration.ofSeconds(10));
    assertThat(conferir).isLessThanOrEqualTo(Duration.ofSeconds(15));
    assertThat(resultado.plus(conferir)).isLessThan(reservaMaxima);
  }
}
