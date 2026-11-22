package bipotheking.net.duora.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O que a revelação mostra, como pares chave → valor definidos pelo {@link Desfecho} do cenário.
 * Preserva a ordem de inserção: o JSON da visão e o ETag dependem dela.
 */
public record Resultado(Map<String, String> valores) {

  public Resultado {
    valores = Collections.unmodifiableMap(new LinkedHashMap<>(valores));
  }
}
