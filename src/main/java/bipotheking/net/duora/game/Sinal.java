package bipotheking.net.duora.game;

import java.time.Instant;
import java.util.Objects;

/** “Que tal mover este?”: pedido de uma pessoa sobre uma peça colocada do parceiro. */
public record Sinal(Peca peca, Assento deAssento, Instant expiraEm) {

  public Sinal {
    Objects.requireNonNull(peca);
    Objects.requireNonNull(deAssento);
    Objects.requireNonNull(expiraEm);
  }

  public boolean ativoEm(Instant agora) {
    return agora.isBefore(expiraEm);
  }
}
