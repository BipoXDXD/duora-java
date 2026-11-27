package bipotheking.net.duora.web;

import bipotheking.net.duora.web.LimitesDeTaxa.Regra;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Limite de taxa em memória: um balde (token bucket) por regra e chave, que enche de novo inteiro a
 * cada período. O balde some do cache depois de um período sem uso, quando já estaria cheio.
 */
@Component
public class LimiteDeTaxa {

  private static final long MAXIMO_DE_BALDES = 100_000;

  private final LimitesDeTaxa limites;
  private final RelogioDoBucket relogioDoBucket;
  private final Cache<ChaveDoBalde, Bucket> baldes;

  LimiteDeTaxa(LimitesDeTaxa limites, Clock relogio) {
    this.limites = limites;
    this.relogioDoBucket = new RelogioDoBucket(relogio);
    this.baldes =
        Caffeine.newBuilder()
            .maximumSize(MAXIMO_DE_BALDES)
            .expireAfter(
                Expiry.accessing((ChaveDoBalde chave, Bucket balde) -> chave.regra().periodo()))
            .ticker(relogioDoBucket::currentTimeNanos)
            .build();
  }

  /**
   * Gasta uma ficha da regra para a chave.
   *
   * @throws LimiteExcedido se o balde estiver vazio
   * @throws IllegalStateException se a regra não estiver configurada
   */
  public void consumir(String regra, String chave) {
    ChaveDoBalde chaveDoBalde = new ChaveDoBalde(regra, regraConfigurada(regra), chave);
    ConsumptionProbe tentativa =
        baldes.get(chaveDoBalde, this::novoBalde).tryConsumeAndReturnRemaining(1);
    if (!tentativa.isConsumed()) {
      throw new LimiteExcedido(tentativa.getNanosToWaitForRefill());
    }
  }

  private Regra regraConfigurada(String regra) {
    Regra configurada = limites.regras().get(regra);
    if (configurada == null) {
      throw new IllegalStateException("Regra de limite de taxa não configurada: " + regra);
    }
    return configurada;
  }

  private Bucket novoBalde(ChaveDoBalde chave) {
    long capacidade = (long) chave.regra().capacidade() * limites.multiplicador();
    return Bucket.builder()
        .addLimit(l -> l.capacity(capacidade).refillIntervally(capacidade, chave.regra().periodo()))
        .withCustomTimePrecision(relogioDoBucket)
        .build();
  }

  /** Equivale a {@code nome:chave}; a regra vai junto para o cache saber quando expirar. */
  private record ChaveDoBalde(String nome, Regra regra, String chave) {}
}
