package bipotheking.net.duora.pareamento;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.Preferencias;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Quem está na fila, com o que o pareamento precisa para decidir. Só para uso interno do servidor:
 * o {@code toString} não carrega as preferências nem a célula.
 */
public record Candidato(
    ContaId conta,
    Instant entrouEm,
    int idade,
    Preferencias preferencias,
    Optional<CelulaGeohash> celula,
    boolean deTeste) {

  public Candidato {
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(entrouEm, "entrouEm");
    Objects.requireNonNull(preferencias, "preferencias");
    Objects.requireNonNull(celula, "celula");
  }

  @Override
  public String toString() {
    return "Candidato[conta=%s, entrouEm=%s, idade=%d, deTeste=%s, ***]"
        .formatted(conta, entrouEm, idade, deTeste);
  }
}
