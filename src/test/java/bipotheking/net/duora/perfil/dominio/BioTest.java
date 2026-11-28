package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import org.junit.jupiter.api.Test;

class BioTest {

  @Test
  void bioCom281Recusada() {
    assertThatThrownBy(() -> Bio.de("a".repeat(281)))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.BIO_INVALIDA));
  }

  @Test
  void bioCom280Aceita() {
    assertThat(Bio.de("a".repeat(280)).valor()).hasSize(280);
  }

  @Test
  void quebraDeLinhaAceita() {
    assertThat(Bio.de("a\nb").valor()).isEqualTo("a\nb");
  }

  @Test
  void quebraDoWindowsViraQuebraSimples() {
    assertThat(Bio.de("a\r\nb").valor()).isEqualTo("a\nb");
  }

  @Test
  void outroControleRecusado() {
    assertThatThrownBy(() -> Bio.de("a\u0007"))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.BIO_INVALIDA));
  }

  @Test
  void bioNulaViraVazia() {
    assertThat(Bio.de(null).valor()).isEmpty();
  }
}
