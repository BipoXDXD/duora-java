package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.random.RandomGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackoffTest {

  @ParameterizedTest(name = "aleatório {0}, tentativas {1} → {2}")
  @CsvSource({
    "0.5, 0, PT2S",
    "0.5, 1, PT4S",
    "0.5, 5, PT64S",
    "0.5, 20, PT15M",
    "0.0, 0, PT1.6S",
    "0.0, 8, PT409.6S",
    "1.0, 0, PT2.4S",
    "1.0, 9, PT15M",
  })
  void esperaDobraComJitterEAteOTeto(double aleatorio, int tentativas, Duration esperada) {
    Backoff backoff = new Backoff(fixo(aleatorio));

    assertThat(backoff.proxima(tentativas)).isEqualTo(esperada);
  }

  @ParameterizedTest
  @CsvSource({"63", "64", "2147483647"})
  void expoenteGrandeNaoEstouraOTeto(int tentativas) {
    assertThat(new Backoff(fixo(1.0)).proxima(tentativas)).isEqualTo(Duration.ofMinutes(15));
  }

  /** {@code nextDouble} fixo; o resto do gerador não deve ser usado. */
  private static RandomGenerator fixo(double valor) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        throw new UnsupportedOperationException();
      }

      @Override
      public double nextDouble() {
        return valor;
      }
    };
  }
}
