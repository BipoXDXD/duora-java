package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DescricaoCurtaTest {

  private static final int MAXIMO = 40;

  @Test
  void quarentaCodePointsAceitosComMaximo40() {
    String bruto = "😀".repeat(MAXIMO);

    assertThat(DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.IDENTIDADE_INVALIDA).valor())
        .isEqualTo(bruto);
  }

  @ParameterizedTest
  @ValueSource(strings = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "  "})
  void foraDoLimiteRecusadaComOCodigoPassado(String bruto) {
    assertThatThrownBy(() -> DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.GENERO_INVALIDO))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.GENERO_INVALIDO));
  }
}
