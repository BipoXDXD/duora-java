package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FaixaDeDistanciaTest {

  @ParameterizedTest
  @CsvSource({
    "5.0, ATE_5_KM",
    "5.01, ATE_20_KM",
    "20.0, ATE_20_KM",
    "50.0, ATE_50_KM",
    "50.01, MAIS_DE_50_KM"
  })
  void limiteDeCimaFicaNaFaixa(double km, FaixaDeDistancia esperada) {
    assertThat(FaixaDeDistancia.de(km)).isEqualTo(esperada);
  }
}
