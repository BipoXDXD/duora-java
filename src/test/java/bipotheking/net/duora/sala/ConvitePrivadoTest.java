package bipotheking.net.duora.sala;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConvitePrivadoTest {

  @Test
  void toStringNaoMostraOConvite() {
    ConvitePrivado convite = new ConvitePrivado("enc-1", "token-secreto", "terraco");

    assertThat(convite.toString()).doesNotContain("token-secreto").contains("enc-1", "terraco");
  }
}
