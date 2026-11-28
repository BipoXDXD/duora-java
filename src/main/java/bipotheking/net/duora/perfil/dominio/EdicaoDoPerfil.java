package bipotheking.net.duora.perfil.dominio;

import java.util.Objects;
import java.util.Optional;

/** Os campos do perfil que a pessoa edita, já validados. */
public record EdicaoDoPerfil(
    Apelido apelido, Optional<PronomesEscolhidos> pronomes, Intencoes intencoes, Bio bio) {

  public EdicaoDoPerfil {
    Objects.requireNonNull(apelido, "apelido");
    Objects.requireNonNull(pronomes, "pronomes");
    Objects.requireNonNull(intencoes, "intencoes");
    Objects.requireNonNull(bio, "bio");
  }
}
