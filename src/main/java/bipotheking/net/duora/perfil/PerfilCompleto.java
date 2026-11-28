package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * O que uma conexão vê: o público, mais idade, intenções, bio e a foto nítida. A orientação só vem
 * quando a pessoa marcou mostrá-la; gênero e interesses nunca. O {@code toString} não carrega a
 * orientação, para nenhum log levar o dado sensível.
 */
public record PerfilCompleto(
    ContaId conta,
    String apelido,
    int idade,
    Set<Intencao> intencao,
    String bio,
    Optional<String> fotoUrl,
    Optional<String> pronomes,
    Optional<String> orientacao,
    boolean verificado) {

  public PerfilCompleto {
    Objects.requireNonNull(conta, "conta");
    Objects.requireNonNull(apelido, "apelido");
    Objects.requireNonNull(intencao, "intencao");
    Objects.requireNonNull(bio, "bio");
    Objects.requireNonNull(fotoUrl, "fotoUrl");
    Objects.requireNonNull(pronomes, "pronomes");
    Objects.requireNonNull(orientacao, "orientacao");
    intencao = Collections.unmodifiableSet(EnumSet.copyOf(intencao));
  }

  @Override
  public String toString() {
    return "PerfilCompleto[conta=%s, ***]".formatted(conta);
  }
}
