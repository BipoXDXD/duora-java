package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Uma tentativa de verificação de idade como o banco a guarda: sem sessão enquanto é só reserva,
 * sem resultado enquanto não foi concluída.
 */
public record Tentativa(
    UUID id,
    ContaId conta,
    FinalidadeDaTentativa finalidade,
    @Nullable String sessaoId,
    Instant criadaEm,
    @Nullable ResultadoDaVerificacao resultado) {

  public Tentativa {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(finalidade, "finalidade");
    Objects.requireNonNull(criadaEm, "criadaEm");
  }
}
