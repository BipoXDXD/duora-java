package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FaixaEtariaTest {

  @ParameterizedTest
  @CsvSource({"18, 99", "25, 25"})
  void faixaValidaAceita(int minima, int maxima) {
    FaixaEtaria faixa = new FaixaEtaria(minima, maxima);

    assertThat(faixa.minima()).isEqualTo(minima);
    assertThat(faixa.maxima()).isEqualTo(maxima);
  }

  @ParameterizedTest
  @CsvSource({"17, 30", "30, 29", "18, 100"})
  void faixaInvalidaRecusada(int minima, int maxima) {
    assertThatThrownBy(() -> new FaixaEtaria(minima, maxima))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.FAIXA_ETARIA_INVALIDA));
  }

  @ParameterizedTest
  @CsvSource({"24, false", "25, true", "30, true", "31, false"})
  void contemSoAsIdadesDaFaixa(int idade, boolean contem) {
    assertThat(new FaixaEtaria(25, 30).contem(idade)).isEqualTo(contem);
  }

  @Test
  void todasVaiDe18A99() {
    assertThat(FaixaEtaria.TODAS).isEqualTo(new FaixaEtaria(18, 99));
  }
}
