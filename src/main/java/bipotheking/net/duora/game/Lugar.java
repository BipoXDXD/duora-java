package bipotheking.net.duora.game;

import java.util.Objects;

/** Um lugar de um cenário, dentro de uma zona. */
public record Lugar(String id, Zona zona) {

  public Lugar {
    Objects.requireNonNull(id);
    Objects.requireNonNull(zona);
    if (id.isBlank()) {
      throw new IllegalArgumentException("id de lugar vazio");
    }
  }
}
