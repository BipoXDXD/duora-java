package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.CodigoDenuncia;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import org.junit.jupiter.api.Test;

/** As regras da denúncia pública que a API não alcança por inteiro. */
class DenunciaPublicaTest {

  private static final String DESCRICAO = "Alguém pediu dinheiro na sala.";

  /** O banco conta caracteres ({@code char_length}), não unidades UTF-16. */
  @Test
  void descricaoContaCaracteresNaoUnidadesUtf16() {
    String cincoEmojis = "😀".repeat(5);

    assertThatThrownBy(() -> DenunciaPublica.de("GOLPE", cincoEmojis, null, null))
        .isInstanceOf(ErroDeNegocio.class)
        .extracting(erro -> ((ErroDeNegocio) erro).codigo())
        .isEqualTo(CodigoDenuncia.DENUNCIA_PUBLICA_INVALIDA);
    assertThat(DenunciaPublica.de("GOLPE", "😀".repeat(2000), null, null).descricao())
        .hasSize(4000);
  }

  @Test
  void opcionaisEmBrancoViramAusentes() {
    DenunciaPublica denuncia = DenunciaPublica.de("GOLPE", DESCRICAO, "  ", "");

    assertThat(denuncia.referencia()).isNull();
    assertThat(denuncia.emailContato()).isNull();
  }

  @Test
  void emailDeContatoSaiNormalizado() {
    DenunciaPublica denuncia = DenunciaPublica.de("GOLPE", DESCRICAO, null, " Ana@Exemplo.com ");

    assertThat(denuncia.emailContato()).isEqualTo(Email.de("ana@exemplo.com"));
  }

  @Test
  void toStringSoMostraOMotivo() {
    DenunciaPublica denuncia =
        DenunciaPublica.de("ASSEDIO", DESCRICAO, "apelido-secreto", "ana@exemplo.com");

    assertThat(denuncia.toString())
        .isEqualTo("DenunciaPublica[motivo=ASSEDIO]")
        .doesNotContain("dinheiro", "apelido-secreto", "ana");
  }
}
