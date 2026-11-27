package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.web.ConfirmacaoController.PedidoDeConfirmacaoJson;
import org.junit.jupiter.api.Test;

/** O corpo do pedido vai para o log do Spring MVC em DEBUG: o toString não pode expor o token. */
class PedidoDeConfirmacaoJsonTest {

  @Test
  void toStringMascaraOToken() {
    PedidoDeConfirmacaoJson corpo =
        new PedidoDeConfirmacaoJson("Zm9vYmFyLXRva2VuLWRlLXRlc3RlLWNvbS00My1jaGFy");

    assertThat(corpo.toString())
        .isEqualTo("PedidoDeConfirmacaoJson[token=***]")
        .doesNotContain("Zm9vYmFy");
  }

  @Test
  void toStringAceitaTokenNulo() {
    assertThat(new PedidoDeConfirmacaoJson(null).toString())
        .isEqualTo("PedidoDeConfirmacaoJson[token=***]");
  }
}
