package bipotheking.net.duora.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumosTest {

  @Test
  void sha256HexDeAbcSegueOVetorDaNorma() {
    assertThat(Resumos.sha256Hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void sha256TemTrintaEDoisBytes() {
    assertThat(Resumos.sha256("abc")).hasSize(32);
  }
}
