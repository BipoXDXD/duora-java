package bipotheking.net.duora.notificacao.infra;

import java.time.Duration;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * Espera até a próxima tentativa: {@code min(2 s × 2^tentativas × fator, 15 min)}, com fator de
 * jitter entre 0,8 e 1,2 aplicado antes do teto.
 */
@Component
class Backoff {

  private static final double BASE_MS = 2_000;
  private static final long TETO_MS = Duration.ofMinutes(15).toMillis();
  // 2 s × 2^20 já passa muito do teto; limitar o expoente evita estourar o cálculo.
  private static final int EXPOENTE_MAXIMO = 20;

  private final RandomGenerator aleatorio;

  Backoff(RandomGenerator aleatorio) {
    this.aleatorio = aleatorio;
  }

  public Duration proxima(int tentativas) {
    if (tentativas < 0) {
      throw new IllegalArgumentException("tentativas negativas: " + tentativas);
    }
    double fator = 0.8 + 0.4 * aleatorio.nextDouble();
    double espera = BASE_MS * Math.pow(2, Math.min(tentativas, EXPOENTE_MAXIMO)) * fator;
    return Duration.ofMillis(Math.min(Math.round(espera), TETO_MS));
  }
}
