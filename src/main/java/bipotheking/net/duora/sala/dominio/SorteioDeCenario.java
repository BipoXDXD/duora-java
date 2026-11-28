package bipotheking.net.duora.sala.dominio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Sorteio do cenário de um encontro, preferindo o que o par ainda não jogou. */
public final class SorteioDeCenario {

  private SorteioDeCenario() {}

  /**
   * Vence o sorteável jogado por menos pessoas do par (0, depois 1, depois 2); entre os empatados,
   * sorteio uniforme. Para quem cria um convite sozinho, {@code jogadosB} é vazio.
   *
   * @throws IllegalStateException sem cenário sorteável
   */
  public static String sortear(
      Set<String> jogadosA,
      Set<String> jogadosB,
      List<String> sorteaveis,
      RandomGenerator aleatorio) {
    if (sorteaveis.isEmpty()) {
      throw new IllegalStateException("nenhum cenário sorteável");
    }
    int menor = Integer.MAX_VALUE;
    List<String> empatados = new ArrayList<>();
    for (String cenario : sorteaveis) {
      int jogadoPor = (jogadosA.contains(cenario) ? 1 : 0) + (jogadosB.contains(cenario) ? 1 : 0);
      if (jogadoPor < menor) {
        menor = jogadoPor;
        empatados.clear();
      }
      if (jogadoPor == menor) {
        empatados.add(cenario);
      }
    }
    return empatados.get(aleatorio.nextInt(empatados.size()));
  }
}
