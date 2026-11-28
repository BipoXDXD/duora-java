package bipotheking.net.duora.perfil;

import java.util.Objects;
import java.util.Optional;

/**
 * O que o pareamento precisa de uma conta pronta para a fila: idade (anos completos no fuso {@code
 * America/Sao_Paulo}), preferências, célula, se houver, e a marca de conta de teste. Nunca sai do
 * servidor; o {@code toString} não carrega as preferências nem a célula.
 */
public record PerfilDeFila(
    int idade, Preferencias preferencias, Optional<CelulaGeohash> celula, boolean deTeste) {

  public PerfilDeFila {
    Objects.requireNonNull(preferencias, "preferencias");
    Objects.requireNonNull(celula, "celula");
  }

  @Override
  public String toString() {
    return "PerfilDeFila[idade=%d, deTeste=%s, ***]".formatted(idade, deTeste);
  }
}
