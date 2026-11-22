package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.CenarioMinimo.FOLHA;
import static bipotheking.net.duora.game.CenarioMinimo.P1;
import static bipotheking.net.duora.game.CenarioMinimo.P2;
import static bipotheking.net.duora.game.CenarioMinimo.PEDRA;
import static bipotheking.net.duora.game.CenarioMinimo.ZONA;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** O construtor do {@link Cenario} recusa dados inconsistentes citando o valor ofensor. */
class CenarioTest {

  @Test
  void idForaDoPadraoNaoConstroi() {
    assertThatThrownBy(() -> cenario("Terraço", List.of(P1, P2), List.of(PEDRA, FOLHA), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Terraço");
  }

  @Test
  void idDeLugarRepetidoNaoConstroi() {
    Lugar outroP1 = new Lugar("P1", ZONA);

    assertThatThrownBy(() -> cenario("minimo", List.of(P1, outroP1), List.of(PEDRA, FOLHA), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("P1");
  }

  @Test
  void idDePecaRepetidoNaoConstroi() {
    Peca outraPedra = new Peca("pedra", Assento.B, ZONA, true);

    assertThatThrownBy(() -> cenario("minimo", List.of(P1, P2), List.of(PEDRA, outraPedra), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pedra");
  }

  @Test
  void zonaSemLugarNaoConstroi() {
    Peca nuvem = new Peca("nuvem", Assento.B, new Zona("ceu"), true);

    assertThatThrownBy(() -> cenario("minimo", List.of(P1, P2), List.of(PEDRA, nuvem), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ceu");
  }

  @Test
  void pecasPorPessoaAlemDoQueOAssentoTemNaoConstroi() {
    assertThatThrownBy(() -> cenario("minimo", List.of(P1, P2), List.of(PEDRA, FOLHA), 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2");
  }

  @Test
  void listasSaoCopiasImutaveis() {
    Cenario cenario = cenario("minimo", List.of(P1, P2), List.of(PEDRA, FOLHA), 1);

    assertThatThrownBy(() -> cenario.lugares().add(new Lugar("P3", ZONA)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static Cenario cenario(
      String id, List<Lugar> lugares, List<Peca> pecas, int pecasPorPessoaCompleta) {
    return new Cenario(
        id,
        "Mínimo",
        lugares,
        pecas,
        1,
        Duration.ofSeconds(20),
        pecasPorPessoaCompleta,
        (composicao, c) -> new Resultado(Map.of()));
  }
}
