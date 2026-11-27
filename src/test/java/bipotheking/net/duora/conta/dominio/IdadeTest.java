package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdadeTest {

  @ParameterizedTest(name = "hoje {0}, nascimento {1} -> {2}")
  @CsvSource({
    "2026-11-22, 2008-11-22, true",
    "2026-11-22, 2008-11-21, true",
    "2026-11-22, 2008-11-23, false",
    "2026-11-22, 1990-01-01, true",
    "2026-02-28, 2008-02-29, false",
    "2026-03-01, 2008-02-29, true"
  })
  void maiorDeIdadeAPartirDosDezoitoAnosCompletos(
      LocalDate hoje, LocalDate nascimento, boolean esperado) {
    assertThat(Idade.maiorDeIdade(nascimento, hoje)).isEqualTo(esperado);
  }
}
