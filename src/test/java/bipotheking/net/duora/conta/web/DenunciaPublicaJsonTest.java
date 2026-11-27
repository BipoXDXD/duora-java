package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.web.DenunciaPublicaController.DenunciaPublicaJson;
import org.junit.jupiter.api.Test;

/**
 * O corpo da denúncia vai para o log do Spring MVC em DEBUG: o toString não mostra nada do que
 * veio.
 */
class DenunciaPublicaJsonTest {

  @Test
  void toStringEscondeTodosOsCampos() {
    DenunciaPublicaJson corpo =
        new DenunciaPublicaJson(
            "GOLPE", "descricao-secreta", "referencia-secreta", "ana@exemplo.com", "token-secreto");

    assertThat(corpo.toString())
        .isEqualTo(
            "DenunciaPublicaJson[motivo=***, descricao=***, referencia=***,"
                + " emailContato=***, tokenVerificacao=***]");
  }
}
