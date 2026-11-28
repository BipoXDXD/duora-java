package bipotheking.net.duora.perfil;

import java.util.Objects;
import java.util.UUID;

/** Identidade de uma foto de perfil; o mesmo id nomeia os arquivos no armazém. */
public record FotoId(UUID valor) {

  public FotoId {
    Objects.requireNonNull(valor, "valor");
  }

  @Override
  public String toString() {
    return valor.toString();
  }
}
