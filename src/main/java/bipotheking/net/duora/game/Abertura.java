package bipotheking.net.duora.game;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A abertura do encontro, uma vez por encontro, no começo da montagem: as duas pessoas tocam e, se
 * os toques caem na mesma janela, as lanternas acendem. Sem isso, ela termina sozinha, apagada, no
 * máximo do cenário ({@link DefinicaoDeAbertura}).
 *
 * @param iniciadaEm a entrada do par
 * @param toques o último toque de cada assento
 * @param concluidaEm quando acendeu ou chegou ao máximo; vazia enquanto ativa
 * @param acesa se os dois tocaram na mesma janela; só uma abertura concluída pode estar acesa
 */
public record Abertura(
    Instant iniciadaEm,
    Map<Assento, Instant> toques,
    Optional<Instant> concluidaEm,
    boolean acesa) {

  public Abertura {
    Objects.requireNonNull(iniciadaEm);
    toques = Map.copyOf(toques);
    Objects.requireNonNull(concluidaEm);
    if (acesa && concluidaEm.isEmpty()) {
      throw new IllegalArgumentException("abertura acesa sem conclusão");
    }
  }

  /** Abertura recém-começada, sem toques. */
  static Abertura iniciada(Instant agora) {
    return new Abertura(agora, Map.of(), Optional.empty(), false);
  }

  /** Enquanto não acendeu nem chegou ao máximo. */
  public boolean ativa() {
    return concluidaEm.isEmpty();
  }

  /**
   * Registra o toque de quem; se o outro tocou há no máximo {@code janela} (inclusive), acende. Uma
   * abertura concluída não muda.
   */
  Abertura tocada(Assento quem, Instant agora, Duration janela) {
    if (!ativa()) {
      return this;
    }
    Map<Assento, Instant> novos = new EnumMap<>(Assento.class);
    novos.putAll(toques);
    novos.put(quem, agora);
    boolean juntos =
        Optional.ofNullable(toques.get(quem.parceiro()))
            .filter(doOutro -> !doOutro.plus(janela).isBefore(agora))
            .isPresent();
    return new Abertura(iniciadaEm, novos, juntos ? Optional.of(agora) : Optional.empty(), juntos);
  }

  /**
   * Conclui apagada, no instante do máximo, quando {@code agora} já chegou a ele. Sem mudança,
   * devolve esta mesma instância.
   */
  Abertura avancada(Instant agora, Duration maximo) {
    Instant fim = iniciadaEm.plus(maximo);
    if (!ativa() || agora.isBefore(fim)) {
      return this;
    }
    return new Abertura(iniciadaEm, toques, Optional.of(fim), false);
  }
}
