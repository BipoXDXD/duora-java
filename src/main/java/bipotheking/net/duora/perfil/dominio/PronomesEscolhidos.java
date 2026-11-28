package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Pronomes;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Pronomes escolhidos: {@code OUTROS} vem sempre com descrição, e só ele. */
public record PronomesEscolhidos(Pronomes pronomes, Optional<DescricaoCurta> descricao) {

  private static final int MAXIMO_DA_DESCRICAO = 20;

  public PronomesEscolhidos {
    Objects.requireNonNull(pronomes, "pronomes");
    Objects.requireNonNull(descricao, "descricao");
    if ((pronomes == Pronomes.OUTROS) != descricao.isPresent()) {
      throw new IllegalArgumentException("Só OUTROS tem descrição, e sempre");
    }
    descricao.ifPresent(d -> {
      if (!DescricaoCurta.cabe(d.valor(), MAXIMO_DA_DESCRICAO)) {
        throw new IllegalArgumentException("Descrição dos pronomes longa demais");
      }
    });
  }

  /**
   * Ambos ausentes: a pessoa não escolheu pronomes. {@code OUTROS} pede descrição de 1 a 20 code
   * points; os outros pronomes, nenhuma.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code IDENTIDADE_INVALIDA} em qualquer
   *     outra combinação
   */
  public static Optional<PronomesEscolhidos> de(
      @Nullable Pronomes pronomes, @Nullable String descricao) {
    if (pronomes == null) {
      if (descricao != null) {
        throw CodigoPerfil.IDENTIDADE_INVALIDA.erro();
      }
      return Optional.empty();
    }
    if (pronomes != Pronomes.OUTROS) {
      if (descricao != null) {
        throw CodigoPerfil.IDENTIDADE_INVALIDA.erro();
      }
      return Optional.of(new PronomesEscolhidos(pronomes, Optional.empty()));
    }
    if (descricao == null) {
      throw CodigoPerfil.IDENTIDADE_INVALIDA.erro();
    }
    DescricaoCurta curta =
        DescricaoCurta.de(descricao, MAXIMO_DA_DESCRICAO, CodigoPerfil.IDENTIDADE_INVALIDA);
    return Optional.of(new PronomesEscolhidos(pronomes, Optional.of(curta)));
  }

  /** O que a tela mostra: a descrição, em {@code OUTROS}; senão, o rótulo dos pronomes. */
  public String paraExibir() {
    return descricao.map(DescricaoCurta::valor).orElse(pronomes.rotulo());
  }
}
