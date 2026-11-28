package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** O leitor de campos multipart da Didit falsa falha com mensagem clara em parte malformada. */
class ServidorDiditFalsoTest {

  @Test
  void campoSemLinhaEmBrancoFalhaComMensagemClara() {
    ServidorDiditFalso.Requisicao requisicao =
        requisicao("--b\r\nContent-Disposition: form-data; name=\"save_api_request\"\r\nfalse");

    assertThatThrownBy(() -> requisicao.campo("save_api_request"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("save_api_request")
        .hasMessageContaining("linha em branco");
  }

  @Test
  void campoSemFronteiraFalhaComMensagemClara() {
    ServidorDiditFalso.Requisicao requisicao =
        requisicao("--b\r\nContent-Disposition: form-data; name=\"save_api_request\"\r\n\r\nfalse");

    assertThatThrownBy(() -> requisicao.campo("save_api_request"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("save_api_request")
        .hasMessageContaining("fronteira");
  }

  private static ServidorDiditFalso.Requisicao requisicao(String corpo) {
    return new ServidorDiditFalso.Requisicao(
        "POST", "/v3/id-verification/", new Headers(), corpo.getBytes(StandardCharsets.ISO_8859_1));
  }
}
