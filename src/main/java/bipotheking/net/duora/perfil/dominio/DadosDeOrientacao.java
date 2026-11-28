package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Orientacao;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A orientação da pessoa, se ela quer mostrá-la às conexões e quando consentiu. {@link
 * Orientacao#OUTRA} vem com descrição de 1 a 40 caracteres, e só ela. O {@code toString} não
 * carrega a orientação, para nenhum log levar o dado sensível.
 */
public record DadosDeOrientacao(
    Orientacao orientacao,
    Optional<DescricaoCurta> descricao,
    boolean mostrar,
    Instant consentidaEm) {

  private static final int MAXIMO_DA_DESCRICAO = 40;

  public DadosDeOrientacao {
    Objects.requireNonNull(orientacao, "orientacao");
    Objects.requireNonNull(descricao, "descricao");
    Objects.requireNonNull(consentidaEm, "consentidaEm");
    if ((orientacao == Orientacao.OUTRA) != descricao.isPresent()) {
      throw new IllegalArgumentException("Orientação fora da forma validada");
    }
  }

  /**
   * Valida o que veio do formulário.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code IDENTIDADE_INVALIDA} sem orientação,
   *     com {@code OUTRA} sem descrição (ou descrição sem {@code OUTRA}) ou com descrição fora de 1
   *     a 40 caracteres
   */
  public static DadosDeOrientacao de(
      @Nullable Orientacao orientacao,
      @Nullable String descricao,
      boolean mostrar,
      Instant consentidaEm) {
    if (orientacao == null || (orientacao == Orientacao.OUTRA) != (descricao != null)) {
      throw CodigoPerfil.IDENTIDADE_INVALIDA.erro();
    }
    Optional<DescricaoCurta> descricaoCurta =
        descricao == null
            ? Optional.empty()
            : Optional.of(
                DescricaoCurta.de(
                    descricao, MAXIMO_DA_DESCRICAO, CodigoPerfil.IDENTIDADE_INVALIDA));
    return new DadosDeOrientacao(orientacao, descricaoCurta, mostrar, consentidaEm);
  }

  /** O rótulo da orientação ou, em {@code OUTRA}, a descrição da pessoa. */
  public String paraExibir() {
    return descricao.map(DescricaoCurta::valor).orElseGet(orientacao::rotulo);
  }

  @Override
  public String toString() {
    return "DadosDeOrientacao[***]";
  }
}
