package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.web.PedidoDeEntradaController.PedidoDeEntradaJson;
import org.junit.jupiter.api.Test;

/** O corpo do pedido vai para o log do Spring MVC em DEBUG: o toString não pode expô-lo. */
class PedidoDeEntradaJsonTest {

  @Test
  void toStringMascaraEmailEToken() {
    PedidoDeEntradaJson corpo = new PedidoDeEntradaJson("ana.silva@exemplo.com", "token-secreto");

    assertThat(corpo.toString())
        .isEqualTo("PedidoDeEntradaJson[email=a***@exemplo.com, tokenVerificacao=***]")
        .doesNotContain("ana.silva", "token-secreto");
  }

  @Test
  void toStringAceitaCamposNulos() {
    assertThat(new PedidoDeEntradaJson(null, null).toString())
        .isEqualTo("PedidoDeEntradaJson[email=***, tokenVerificacao=***]");
  }
}
