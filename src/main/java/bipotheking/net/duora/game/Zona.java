package bipotheking.net.duora.game;

import java.util.Objects;

/** Área de um cenário: no terraço, o centro recebe as obrigatórias; as laterais, as secundárias. */
public record Zona(String id) {

  public Zona {
    Objects.requireNonNull(id);
    if (id.isBlank()) {
      throw new IllegalArgumentException("id de zona vazio");
    }
  }
}
