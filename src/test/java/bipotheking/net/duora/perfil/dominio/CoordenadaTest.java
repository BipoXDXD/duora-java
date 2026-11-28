package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoLocalizacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordenadaTest {

  @ParameterizedTest
  @CsvSource({"90, 180", "-90, -180"})
  void extremosAceitos(double latitude, double longitude) {
    Coordenada coordenada = Coordenada.de(latitude, longitude);

    assertThat(coordenada.latitude()).isEqualTo(latitude);
    assertThat(coordenada.longitude()).isEqualTo(longitude);
  }

  @ParameterizedTest
  @CsvSource({"90.0000001, 0", "0, -180.0000001", "NaN, 0", "0, Infinity"})
  void foraDoIntervaloOuNaoFinitaRecusada(double latitude, double longitude) {
    assertThatThrownBy(() -> Coordenada.de(latitude, longitude))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoLocalizacao.COORDENADA_INVALIDA));
  }

  @Test
  void toStringSemDigitos() {
    assertThat(Coordenada.de(-23.5505, -46.6333).toString())
        .isEqualTo("Coordenada[***]")
        .doesNotContainPattern("\\d");
  }
}
