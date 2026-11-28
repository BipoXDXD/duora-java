package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Intencao;
import java.util.EnumSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class IntencoesTest {

  @ParameterizedTest
  @NullAndEmptySource
  void semIntencaoRecusado(@Nullable List<Intencao> brutas) {
    assertThatThrownBy(() -> Intencoes.de(brutas))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.INTENCAO_INVALIDA));
  }

  @Test
  void repetidasViramUmConjuntoNaOrdemDoEnum() {
    Intencoes intencoes =
        Intencoes.de(List.of(Intencao.NAMORO, Intencao.AMIZADE, Intencao.AMIZADE));

    assertThat(intencoes.valores())
        .isEqualTo(EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO))
        .containsExactly(Intencao.AMIZADE, Intencao.NAMORO);
  }

  @Test
  void soAmizadeNaoQuerNamoro() {
    assertThat(Intencoes.de(List.of(Intencao.AMIZADE)).querNamoro()).isFalse();
  }
}
