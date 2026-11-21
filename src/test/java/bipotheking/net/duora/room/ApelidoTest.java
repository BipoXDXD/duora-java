package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApelidoTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "123456789012345678901",
        "Ana\u0000",
        "Ana\nBruno",
        "Ana\u0007",
        "\u202EanA",
        "Ana\u2066",
        "Ana\u200E",
        "\u200FAna",
        "Ana\u061C",
        "Ana\u2028B",
        "Ana\u2029B"
      })
  void apelidoInvalidoEhRecusado(String bruto) {
    assertThatThrownBy(() -> Apelido.de(bruto))
        .isInstanceOfSatisfying(
            ErroApi.class, erro -> assertThat(erro.codigo()).isEqualTo(Codigo.APELIDO_INVALIDO));
  }

  @Test
  void apelidoAusenteEhRecusado() {
    assertThatThrownBy(() -> Apelido.de(null)).isInstanceOf(ErroApi.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "12345678901234567890", "Ana Luísa", "🌙 Lua"})
  void apelidoDe1A20CaracteresEhAceito(String bruto) {
    assertThat(Apelido.de(bruto).valor()).isEqualTo(bruto);
  }

  @Test
  void espacosNasPontasSaoRemovidos() {
    assertThat(Apelido.de("  12345678901234567890  ").valor()).isEqualTo("12345678901234567890");
  }

  @Test
  void apelidoContaCaracteresENaoUnidadesUtf16() {
    String vinteLuas = "🌙".repeat(20);

    assertThat(Apelido.de(vinteLuas).valor()).isEqualTo(vinteLuas);
  }
}
