package bipotheking.net.duora.conta.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Credencial temporária com que o navegador fala direto com o fornecedor da prova de vida. Só vive
 * em memória durante a requisição. O {@code toString} mostra só {@code accessKeyId} e {@code
 * expiraEm}: segredo e token nunca vão para log.
 */
public record CredencialCurta(
    String accessKeyId, String secretAccessKey, String sessionToken, Instant expiraEm) {

  public CredencialCurta {
    Objects.requireNonNull(accessKeyId, "accessKeyId");
    Objects.requireNonNull(secretAccessKey, "secretAccessKey");
    Objects.requireNonNull(sessionToken, "sessionToken");
    Objects.requireNonNull(expiraEm, "expiraEm");
  }

  @Override
  public String toString() {
    return "CredencialCurta[accessKeyId=" + accessKeyId + ", expiraEm=" + expiraEm + "]";
  }
}
