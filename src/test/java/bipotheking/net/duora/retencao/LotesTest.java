package bipotheking.net.duora.retencao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;

class LotesTest {

  /** Devolve os tamanhos em ordem e conta as chamadas. */
  private static final class LotesFalsos implements IntSupplier {

    private final Queue<Integer> tamanhos;
    private int chamadas;

    LotesFalsos(Integer... tamanhos) {
      this.tamanhos = new ArrayDeque<>(List.of(tamanhos));
    }

    @Override
    public int getAsInt() {
      chamadas++;
      return tamanhos.remove();
    }

    int chamadas() {
      return chamadas;
    }
  }

  @Test
  void somaOsLotesAteZero() {
    LotesFalsos lotes = new LotesFalsos(1000, 1000, 500, 0);

    assertThat(Lotes.apagarAteAcabar(lotes)).isEqualTo(2500);
    assertThat(lotes.chamadas()).isEqualTo(4);
  }

  @Test
  void primeiroLoteVazioDevolveZero() {
    LotesFalsos lotes = new LotesFalsos(0);

    assertThat(Lotes.apagarAteAcabar(lotes)).isZero();
    assertThat(lotes.chamadas()).isEqualTo(1);
  }

  @Test
  void loteNegativoFalha() {
    LotesFalsos lotes = new LotesFalsos(10, -1);

    assertThatThrownBy(() -> Lotes.apagarAteAcabar(lotes))
        .isInstanceOf(IllegalStateException.class);
  }
}
