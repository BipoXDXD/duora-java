package bipotheking.net.duora.sala.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A linha da conta na fila (tabela {@code fila_espera}, V53). Pareada, traz o encontro, o cenário e
 * quando o par se formou; esperando, os três ficam nulos.
 */
public record EntradaNaFila(
    ContaId conta,
    Instant entrouEm,
    boolean deTeste,
    @Nullable Instant ausenteDesde,
    @Nullable String encontroId,
    @Nullable String cenarioId,
    @Nullable Instant pareadoEm) {

  public EntradaNaFila {
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(entrouEm, "entrouEm");
    if ((encontroId == null) != (pareadoEm == null)
        || (encontroId == null) != (cenarioId == null)) {
      throw new IllegalArgumentException("encontro, cenário e pareadoEm vêm juntos");
    }
  }

  public boolean pareada() {
    return encontroId != null;
  }
}
