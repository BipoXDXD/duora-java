package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.CodigoEmail;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EmailTest {

  /** Domínio de 189 caracteres: com a parte local de 64 e o arroba, o total fica em 254. */
  private static final String DOMINIO_LONGO =
      "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(57) + ".com";

  static Stream<String> invalidos() {
    return Stream.of(
        "",
        " ",
        "a@b",
        "a@@b.com",
        "x".repeat(65) + "@a.com",
        "x".repeat(64) + "@x" + DOMINIO_LONGO,
        "x\u0000@a.com",
        "a b@c.com");
  }

  @ParameterizedTest
  @MethodSource("invalidos")
  void emailInvalidoEhRecusado(String bruto) {
    assertThatThrownBy(() -> Email.de(bruto))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> {
              assertThat(erro.codigo()).isEqualTo(CodigoEmail.EMAIL_INVALIDO);
              assertThat(erro.getMessage()).isEqualTo("Confira o e-mail digitado.");
            });
  }

  @Test
  void emailAusenteEhRecusado() {
    assertThatThrownBy(() -> Email.de(null))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoEmail.EMAIL_INVALIDO));
  }

  @Test
  void normalizaEChaveDoGmail() {
    Email email = Email.de(" A.B+promo@GMail.com ");

    assertThat(email.valor()).isEqualTo("a.b+promo@gmail.com");
    assertThat(email.chaveDeBloqueio()).isEqualTo("ab@gmail.com");
  }

  @Test
  void googlemailViraGmailNaChave() {
    assertThat(Email.de("a.b@googlemail.com").chaveDeBloqueio()).isEqualTo("ab@gmail.com");
  }

  @Test
  void chaveForaDoGmailMantemPontos() {
    assertThat(Email.de("a.b+x@outlook.com").chaveDeBloqueio()).isEqualTo("a.b@outlook.com");
  }

  @Test
  void dominioEhOQueVemDepoisDoArroba() {
    assertThat(Email.de("ana@sub.exemplo.com").dominio()).isEqualTo("sub.exemplo.com");
  }

  @Test
  void parteLocalDe64Aceita() {
    String bruto = "x".repeat(64) + "@a.com";

    assertThat(Email.de(bruto).valor()).isEqualTo(bruto);
  }

  @Test
  void totalDe254Aceito() {
    String bruto = "x".repeat(64) + "@" + DOMINIO_LONGO;

    assertThat(bruto).hasSize(254);
    assertThat(Email.de(bruto).valor()).isEqualTo(bruto);
  }

  @Test
  void construtorRecusaValorNaoNormalizado() {
    assertThatThrownBy(() -> new Email("Ana@exemplo.com"))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoEmail.EMAIL_INVALIDO));
  }

  @Test
  void toStringMascaraOEndereco() {
    assertThat(Email.de("ana.silva@exemplo.com").toString())
        .isEqualTo("Email[a***@exemplo.com]")
        .doesNotContain("ana.silva");
  }
}
