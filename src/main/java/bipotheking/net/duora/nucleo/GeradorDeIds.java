package bipotheking.net.duora.nucleo;

import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Gera UUID v7 (RFC 9562, §6.2, método 1): 48 bits de milissegundos, 12 bits de sequência e 62 bits
 * aleatórios. Ids da mesma instância saem sempre crescentes, mesmo com relógio parado ou que volta;
 * se a sequência estoura, o milissegundo avança.
 */
public final class GeradorDeIds {

  private static final int SEQUENCIA_MAXIMA = 0xFFF;
  private static final long VERSAO_7 = 0x7000L;
  private static final long SEM_VARIANTE = 0x3FFF_FFFF_FFFF_FFFFL;
  private static final long VARIANTE_RFC = 0x8000_0000_0000_0000L;

  private final Clock relogio;
  private final RandomGenerator aleatorio;
  private long ultimoMs = Long.MIN_VALUE;
  private int sequencia;

  public GeradorDeIds(Clock relogio, RandomGenerator aleatorio) {
    this.relogio = relogio;
    this.aleatorio = aleatorio;
  }

  public synchronized UUID novo() {
    long ms = Math.max(relogio.millis(), ultimoMs);
    if (ms == ultimoMs) {
      sequencia++;
      if (sequencia > SEQUENCIA_MAXIMA) {
        ms++;
        sequencia = 0;
      }
    } else {
      sequencia = 0;
    }
    ultimoMs = ms;
    long msb = (ms << 16) | VERSAO_7 | sequencia;
    long lsb = (aleatorio.nextLong() & SEM_VARIANTE) | VARIANTE_RFC;
    return new UUID(msb, lsb);
  }
}
