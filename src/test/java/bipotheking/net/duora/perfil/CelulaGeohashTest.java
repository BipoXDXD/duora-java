package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CelulaGeohashTest {

  @ParameterizedTest
  @CsvSource({
    "-23.5505, -46.6333, 6gyf4",
    "-22.9068, -43.1729, 75cm9",
    "0, 0, s0000",
    "90, 180, zzzzz",
    "-90, -180, 00000"
  })
  void codificaACelulaDe5Caracteres(double latitude, double longitude, String esperada) {
    assertThat(CelulaGeohash.de(latitude, longitude)).isEqualTo(new CelulaGeohash(esperada));
  }

  @ParameterizedTest
  @CsvSource({"6gycf, 4.887", "6gyft, 16.621", "6gysh, 47.462", "6gxp9, 58.034", "75cm9, 361.276"})
  void distanciaEntreCentrosDasCelulas(String outra, double km) {
    assertThat(new CelulaGeohash("6gyf4").distanciaKm(new CelulaGeohash(outra)))
        .isCloseTo(km, within(0.05));
  }

  @Test
  void mesmaCelulaFicaAZero() {
    assertThat(new CelulaGeohash("6gyf4").distanciaKm(new CelulaGeohash("6gyf4"))).isZero();
  }

  @Test
  void letraForaDoAlfabetoRecusada() {
    assertThatThrownBy(() -> new CelulaGeohash("6gyfa"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
