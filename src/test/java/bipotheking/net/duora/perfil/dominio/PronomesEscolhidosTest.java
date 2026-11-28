package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Pronomes;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PronomesEscolhidosTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULO",
      textBlock =
          """
          OUTROS,   NULO
          ELU_DELU, ile/dile
          OUTROS,   aaaaaaaaaaaaaaaaaaaaa
          NULO,     ile/dile
          """)
  void combinacaoInvalidaRecusada(@Nullable Pronomes pronomes, @Nullable String descricao) {
    assertThatThrownBy(() -> PronomesEscolhidos.de(pronomes, descricao))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.IDENTIDADE_INVALIDA));
  }

  @Test
  void outrosExibeADescricao() {
    assertThat(PronomesEscolhidos.de(Pronomes.OUTROS, "ile/dile"))
        .map(PronomesEscolhidos::paraExibir)
        .contains("ile/dile");
  }

  @Test
  void pronomesDaListaExibemORotulo() {
    assertThat(PronomesEscolhidos.de(Pronomes.ELU_DELU, null))
        .map(PronomesEscolhidos::paraExibir)
        .contains("elu/delu");
  }

  @Test
  void ambosNulosFicamVazios() {
    assertThat(PronomesEscolhidos.de(null, null)).isEmpty();
  }
}
