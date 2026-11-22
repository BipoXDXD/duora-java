package bipotheking.net.duora.game;

import java.time.Instant;
import java.util.Objects;

/**
 * “Que tal mover este para lá?”: pedido de uma pessoa para que o parceiro leve uma peça colocada a
 * um lugar livre da mesma zona.
 */
public record Sinal(Peca peca, Assento deAssento, Lugar destino, Instant expiraEm) {

  public Sinal {
    Objects.requireNonNull(peca);
    Objects.requireNonNull(deAssento);
    Objects.requireNonNull(destino);
    Objects.requireNonNull(expiraEm);
  }

  public boolean ativoEm(Instant agora) {
    return agora.isBefore(expiraEm);
  }
}
