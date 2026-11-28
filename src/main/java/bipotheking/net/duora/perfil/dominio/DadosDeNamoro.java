package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Genero;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * O gênero da pessoa e quem ela quer conhecer no namoro. {@link Genero#OUTRO} vem com descrição de
 * 1 a 40 caracteres, e só ele; ao menos um interesse, na ordem do enum, sem repetição.
 */
public record DadosDeNamoro(
    Genero genero, Optional<DescricaoCurta> descricao, Set<Genero> interesses) {

  private static final int MAXIMO_DA_DESCRICAO = 40;

  public DadosDeNamoro {
    Objects.requireNonNull(genero, "genero");
    Objects.requireNonNull(descricao, "descricao");
    Objects.requireNonNull(interesses, "interesses");
    if ((genero == Genero.OUTRO) != descricao.isPresent() || interesses.isEmpty()) {
      throw new IllegalArgumentException("Dados de namoro fora da forma validada");
    }
    interesses = Collections.unmodifiableSet(EnumSet.copyOf(interesses));
  }

  /**
   * Valida o que veio do formulário.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code GENERO_INVALIDO} sem gênero, com
   *     {@code OUTRO} sem descrição (ou descrição sem {@code OUTRO}), descrição fora de 1 a 40
   *     caracteres, ou interesses ausentes, vazios ou com item nulo
   */
  public static DadosDeNamoro de(
      @Nullable Genero genero,
      @Nullable String descricao,
      @Nullable Collection<Genero> interesses) {
    if (genero == null
        || (genero == Genero.OUTRO) != (descricao != null)
        || interesses == null
        || interesses.isEmpty()
        || interesses.stream().anyMatch(Objects::isNull)) {
      throw CodigoPerfil.GENERO_INVALIDO.erro();
    }
    Optional<DescricaoCurta> descricaoCurta =
        descricao == null
            ? Optional.empty()
            : Optional.of(
                DescricaoCurta.de(descricao, MAXIMO_DA_DESCRICAO, CodigoPerfil.GENERO_INVALIDO));
    return new DadosDeNamoro(genero, descricaoCurta, EnumSet.copyOf(interesses));
  }
}
