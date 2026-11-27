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
  void construtorRecusaDescricaoForaDaFormaNormalizada() {
    assertThatThrownBy(
            () -> new DenunciaPublica(MotivoDeDenunciaPublica.GOLPE, " " + DESCRICAO, null, null))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Conte o que aconteceu em 10 a 2000 caracteres.");
  }

  @Test
  void construtorRecusaDescricaoCurta() {
    assertThatThrownBy(
            () -> new DenunciaPublica(MotivoDeDenunciaPublica.GOLPE, "curta", null, null))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Conte o que aconteceu em 10 a 2000 caracteres.");
  }

  @Test
  void construtorRecusaReferenciaEmBranco() {
    assertThatThrownBy(
            () -> new DenunciaPublica(MotivoDeDenunciaPublica.GOLPE, DESCRICAO, "", null))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Use até 200 caracteres no apelido ou link.");
  }

  /** O Postgres não guarda o caractere nulo em {@code text}: sem esta recusa, viraria 500. */
  @Test
  void caractereNuloNaDescricaoRecusado() {
    assertThatThrownBy(() -> DenunciaPublica.de("GOLPE", DESCRICAO + "\0", null, null))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Conte o que aconteceu em 10 a 2000 caracteres.");
  }

  @Test
  void caractereNuloNaReferenciaRecusado() {
    assertThatThrownBy(() -> DenunciaPublica.de("GOLPE", DESCRICAO, "ape\0lido", null))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Use até 200 caracteres no apelido ou link.");
  }

  @Test
  void caractereNuloNoEmailDeContatoRecusado() {
    assertThatThrownBy(() -> DenunciaPublica.de("GOLPE", DESCRICAO, null, "ana\0@exemplo.com"))
        .isInstanceOf(ErroDeNegocio.class)
        .hasMessage("Confira o e-mail para resposta.");
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
