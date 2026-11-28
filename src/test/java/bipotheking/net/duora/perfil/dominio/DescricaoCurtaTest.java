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

  /** Só invisíveis: espaço sem quebra, ideográfico, de largura zero, BOM, hífen suave, filler. */
  @ParameterizedTest
  @ValueSource(
      strings = {"\u00A0", "\u3000", "\u200B", "\uFEFF", "\u00AD", "\u3164", "\u3000\u3164"})
  void soInvisiveisRecusados(String bruto) {
    assertThatThrownBy(() -> DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.APELIDO_INVALIDO))
        .isInstanceOf(ErroDeNegocio.class);
  }

  /** Formatação no meio (Cf) é recusada, salvo o ZWJ das sequências de emoji. */
  @ParameterizedTest
  @ValueSource(
      strings = {"Lu\u200Ba", "Lu\uFEFFa", "Lu\u00ADa", "Lu\u202Ea", "Lu\u2066a", "Lu\u200Ea"})
  void formatacaoNoMeioRecusada(String bruto) {
    assertThatThrownBy(() -> DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.APELIDO_INVALIDO))
        .isInstanceOf(ErroDeNegocio.class);
  }

  @Test
  void zwjDoEmojiAceito() {
    String familia = "\uD83D\uDC69\u200D\uD83D\uDC67";

    assertThat(DescricaoCurta.de(familia, MAXIMO, CodigoPerfil.APELIDO_INVALIDO).valor())
        .isEqualTo(familia);
  }

  /** As pontas perdem todo espaço Unicode, como o trim do front. */
  @ParameterizedTest
  @ValueSource(strings = {"\u00A0Lua\u00A0", "\u3000Lua\u2007", "\u202FLua\u205F", " Lua\n"})
  void espacosUnicodeNasPontasCortados(String bruto) {
    assertThat(DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.APELIDO_INVALIDO).valor())
        .isEqualTo("Lua");
  }
}
