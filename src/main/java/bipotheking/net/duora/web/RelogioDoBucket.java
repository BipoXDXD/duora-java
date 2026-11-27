package bipotheking.net.duora.web;

import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Tempo do Bucket4j (e do Caffeine) tirado do {@link Clock} injetado, para os testes o moverem. */
final class RelogioDoBucket implements TimeMeter {

  private final Clock relogio;

  RelogioDoBucket(Clock relogio) {
    this.relogio = relogio;
  }

  @Override
  public long currentTimeNanos() {
    Instant agora = relogio.instant();
    return TimeUnit.SECONDS.toNanos(agora.getEpochSecond()) + agora.getNano();
  }

  @Override
  public boolean isWallClockBased() {
    return true;
  }
}
