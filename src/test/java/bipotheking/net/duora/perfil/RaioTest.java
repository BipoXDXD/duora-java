package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RaioTest {

  @Test
  void ate5KmTem5() {
    assertThat(Raio.ATE_5_KM.km()).hasValue(5);
  }

  @Test
  void ate20KmTem20() {
    assertThat(Raio.ATE_20_KM.km()).hasValue(20);
  }

  @Test
  void ate50KmTem50() {
    assertThat(Raio.ATE_50_KM.km()).hasValue(50);
  }

  @Test
  void semLimiteNaoTemKm() {
    assertThat(Raio.SEM_LIMITE.km()).isEmpty();
  }
}
