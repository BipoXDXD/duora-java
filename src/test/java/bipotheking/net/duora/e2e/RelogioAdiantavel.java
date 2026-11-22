package bipotheking.net.duora.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relógio do sistema mais um adiantamento acumulado, para o servidor dos ITs: expirações do
 * servidor (presença, sinal) passam sem esperar o tempo real. Cada IT o registra com
 * {@code @TestConfiguration} + {@code @Bean @Primary}. Não controla os timers do navegador (para
 * esses, {@code page.clock()}).
 */
public final class RelogioAdiantavel extends Clock {

  private final AtomicReference<Duration> adiantamento = new AtomicReference<>(Duration.ZERO);

  void adiantar(Duration duracao) {
    adiantamento.updateAndGet(atual -> atual.plus(duracao));
  }

  @Override
  public Instant instant() {
    return Instant.now().plus(adiantamento.get());
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}
