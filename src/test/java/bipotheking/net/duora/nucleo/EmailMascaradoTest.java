package bipotheking.net.duora.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailMascaradoTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "ana@exemplo.com, a***@exemplo.com",
        "a@x.io, a***@x.io",
        "sem-arroba, ***",
        "'', ***",
        "NULO, ***",
        "@x.com, ***@x.com",
        "ana@, a***@",
        "a@b@x.com, a***@x.com"
      },
      nullValues = "NULO")
  void mascaraOUsuarioEMantemODominio(String email, String esperado) {
    assertThat(EmailMascarado.mascarar(email)).isEqualTo(esperado);
  }

  @Test
  void quebraDeLinhaNaoSobreviveAoLog() {
    assertThat(EmailMascarado.mascarar("\rana@x.com\nINFO falso"))
        .isEqualTo("_***@x.com_INFO falso")
        .doesNotContain("\r", "\n");
  }
}
