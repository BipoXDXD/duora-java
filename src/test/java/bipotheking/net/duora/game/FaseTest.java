package bipotheking.net.duora.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FaseTest {

  @Test
  void nomesNaRedeSaoOsDeHoje() {
    assertThat(new Fase.AguardandoPar().nome()).isEqualTo("AGUARDANDO_PAR");
    assertThat(new Fase.Montando().nome()).isEqualTo("MONTANDO");
    assertThat(new Fase.Revelado().nome()).isEqualTo("REVELADO");
  }

  @Test
  void switchExaustivoCobreAsTres() {
    assertThat(rotulo(new Fase.AguardandoPar())).isEqualTo("espera");
    assertThat(rotulo(new Fase.Montando())).isEqualTo("montagem");
    assertThat(rotulo(new Fase.Revelado())).isEqualTo("revelação");
  }

  @Test
  void igualdadePorValor() {
    assertThat(new Fase.Montando()).isEqualTo(new Fase.Montando());
  }

  /** Sem {@code default}: só compila enquanto cobre todas as variantes de {@link Fase}. */
  private static String rotulo(Fase fase) {
    return switch (fase) {
      case Fase.AguardandoPar _ -> "espera";
      case Fase.Montando _ -> "montagem";
      case Fase.Revelado _ -> "revelação";
    };
  }
}
