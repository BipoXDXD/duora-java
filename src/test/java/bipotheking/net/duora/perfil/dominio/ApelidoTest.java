package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApelidoTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "aaaaaaaaaaaaaaaaaaaaa", "a‮b", "a\u0007b"})
  void apelidoInvalidoRecusado(@Nullable String bruto) {
    assertThatThrownBy(() -> Apelido.de(bruto))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.APELIDO_INVALIDO));
  }

  @Test
  void vinteCodePointsComEmojiAceitos() {
    String bruto = "a".repeat(19) + "😀";

    assertThat(Apelido.de(bruto).valor()).isEqualTo(bruto);
  }

  @Test
  void espacosNasPontasSaem() {
    assertThat(Apelido.de("  Bia  ").valor()).isEqualTo("Bia");
  }
}
