package bipotheking.net.duora.conta.dominio;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * O que a conferência do documento devolveu. O {@code toString} esconde a data de nascimento.
 *
 * @param documentoAceito o fornecedor leu o documento e o considerou autêntico
 * @param dataDeNascimento data lida no documento, se legível
 * @param rostoConfere o rosto do documento é o da selfie
 */
public record ResultadoDoDocumento(
    boolean documentoAceito, Optional<LocalDate> dataDeNascimento, boolean rostoConfere) {

  public ResultadoDoDocumento {
    Objects.requireNonNull(dataDeNascimento, "dataDeNascimento");
  }

  @Override
  public String toString() {
    return "ResultadoDoDocumento[documentoAceito="
        + documentoAceito
        + ", dataDeNascimento=***, rostoConfere="
        + rostoConfere
        + "]";
  }
}
