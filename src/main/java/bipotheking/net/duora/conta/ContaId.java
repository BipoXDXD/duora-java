package bipotheking.net.duora.conta;

import java.util.Objects;
import java.util.UUID;

/** Identidade de uma conta. */
public record ContaId(UUID valor) {

  public ContaId {
    Objects.requireNonNull(valor, "valor");
  }

  @Override
  public String toString() {
    return valor.toString();
  }
}
