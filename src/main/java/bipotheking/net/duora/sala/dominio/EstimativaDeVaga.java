package bipotheking.net.duora.sala.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/** Quanto falta para abrir a vaga de quem espera com a capacidade cheia. */
public final class EstimativaDeVaga {

  private EstimativaDeVaga() {}

  /**
   * Cada encontro que termina libera vaga para duas pessoas: a pessoa na posição {@code posicao}
   * espera o {@code ceil(posicao / 2)}-ésimo término. Minutos arredondados para cima; término que
   * já passou dá 0.
   *
   * @param posicao a partir de 1
   * @param terminos os términos previstos, em ordem crescente
   * @return vazio se não há términos bastantes para chegar à posição
   */
  public static OptionalInt minutos(int posicao, List<Instant> terminos, Instant agora) {
    if (posicao < 1) {
      throw new IllegalArgumentException("posição começa em 1: " + posicao);
    }
    int termino = (posicao + 1) / 2;
    if (terminos.size() < termino) {
      return OptionalInt.empty();
    }
    Duration falta = Duration.between(agora, terminos.get(termino - 1));
    if (falta.isNegative() || falta.isZero()) {
      return OptionalInt.of(0);
    }
    long minutos = falta.toMinutes();
    if (falta.compareTo(Duration.ofMinutes(minutos)) > 0) {
      minutos++;
    }
    return OptionalInt.of(Math.toIntExact(minutos));
  }
}
