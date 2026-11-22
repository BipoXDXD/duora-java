package bipotheking.net.duora.game;

import java.util.Objects;

/**
 * Uma peça de um cenário, de um dono, que só vai para lugares da sua zona. No terraço, cada pessoa
 * tem uma obrigatória (zona centro) e duas secundárias (zona lateral), das quais só uma pode estar
 * no terraço.
 */
public record Peca(String id, Assento dono, Zona zona, boolean obrigatoria) {

  public Peca {
    Objects.requireNonNull(id);
    Objects.requireNonNull(dono);
    Objects.requireNonNull(zona);
    if (id.isBlank()) {
      throw new IllegalArgumentException("id de peça vazio");
    }
  }

  public boolean aceita(Lugar lugar) {
    return lugar.zona().equals(zona);
  }
}
