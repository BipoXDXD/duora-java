package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import java.util.Objects;
import java.util.Optional;

/**
 * O que qualquer conta vê de outra no encontro: apelido, foto borrada, pronomes e o selo de
 * verificação. Nunca bio, idade, intenções, gênero, interesses, orientação nem localização.
 */
public record PerfilPublico(
    ContaId conta,
    String apelido,
    Optional<String> fotoBorradaUrl,
    Optional<String> pronomes,
    boolean verificado) {

  public PerfilPublico {
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(apelido, "apelido");
    Objects.requireNonNull(fotoBorradaUrl, "fotoBorradaUrl");
    Objects.requireNonNull(pronomes, "pronomes");
  }
}
