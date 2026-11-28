package bipotheking.net.duora.sala.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SorteioDeCenarioTest {

  private final SplittableRandom aleatorio = new SplittableRandom(42);

  @Test
  void preferePrimeiroOQueNinguemJogou() {
    String sorteado =
        SorteioDeCenario.sortear(
            Set.of("terraco"), Set.of("praia"), List.of("terraco", "praia", "museu"), aleatorio);

    assertThat(sorteado).isEqualTo("museu");
  }

  @Test
  void semIneditoEscolheOQueSoUmJogou() {
    String sorteado =
        SorteioDeCenario.sortear(
            Set.of("terraco", "praia"), Set.of("terraco"), List.of("terraco", "praia"), aleatorio);

    assertThat(sorteado).isEqualTo("praia");
  }

  @Test
  void todosJogadosAindaSorteia() {
    Set<String> todos = Set.of("terraco", "praia");

    String sorteado =
        SorteioDeCenario.sortear(todos, todos, List.of("terraco", "praia"), aleatorio);

    assertThat(sorteado).isIn("terraco", "praia");
  }

  @Test
  void empateUsaOAleatorio() {
    Map<String, Integer> contagem = new HashMap<>();

    for (int i = 0; i < 1000; i++) {
      String sorteado =
          SorteioDeCenario.sortear(Set.of(), Set.of(), List.of("terraco", "praia"), aleatorio);
      contagem.merge(sorteado, 1, Integer::sum);
    }

    assertThat(contagem).containsOnlyKeys("terraco", "praia");
    assertThat(contagem.values()).allSatisfy(vezes -> assertThat(vezes).isBetween(400, 600));
  }

  @Test
  void listaVaziaFalha() {
    List<String> nenhum = List.of();
    Set<String> vazio = Set.of();

    assertThatThrownBy(() -> SorteioDeCenario.sortear(vazio, vazio, nenhum, aleatorio))
        .isInstanceOf(IllegalStateException.class);
  }
}
