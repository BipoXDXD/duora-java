package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.nucleo.Origem;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OrigemDaRequisicaoTest {

  private final OrigemDaRequisicao origemDaRequisicao = new OrigemDaRequisicao();

  private static MockHttpServletRequest requisicaoDe(String ip, int porta) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ip);
    request.setRemotePort(porta);
    return request;
  }

  @Test
  void usaEnderecoEPortaRemotos() {
    Origem origem = origemDaRequisicao.de(requisicaoDe("203.0.113.7", 51234));

    assertThat(origem).isEqualTo(new Origem("203.0.113.7", 51234));
  }

  @Test
  void ignoraXForwardedFor() {
    MockHttpServletRequest request = requisicaoDe("203.0.113.7", 51234);
    request.addHeader("X-Forwarded-For", "198.51.100.9");

    assertThat(origemDaRequisicao.de(request).ip()).isEqualTo("203.0.113.7");
  }

  @Test
  void ignoraCfConnectingIp() {
    MockHttpServletRequest request = requisicaoDe("203.0.113.7", 51234);
    request.addHeader("CF-Connecting-IP", "198.51.100.9");

    assertThat(origemDaRequisicao.de(request).ip()).isEqualTo("203.0.113.7");
  }
}
