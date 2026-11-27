package bipotheking.net.duora.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailMascaradoTest {

  @ParameterizedTest
  @CsvSource({
    "ana@exemplo.com, a***@exemplo.com",
    "a@x.io, a***@x.io",
    "sem-arroba, ***",
    "'', ***"
  })
  void mascaraOUsuarioEMantemODominio(String email, String esperado) {
    assertThat(EmailMascarado.mascarar(email)).isEqualTo(esperado);
  }
}
