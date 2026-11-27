package bipotheking.net.duora.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrigemTest {

  @Test
  void toStringNaoMostraOIp() {
    assertThat(new Origem("203.0.113.7", 51234)).hasToString("Origem[ip=***, porta=51234]");
  }
}
