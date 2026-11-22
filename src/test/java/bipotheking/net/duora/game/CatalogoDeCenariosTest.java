package bipotheking.net.duora.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogoDeCenariosTest {

  private final CatalogoDeCenarios catalogo =
      new CatalogoDeCenarios(List.of(Terraco.CENARIO, CenarioMinimo.CENARIO));

  @Test
  void porIdAchaOCenario() {
    assertThat(catalogo.porId("minimo")).containsSame(CenarioMinimo.CENARIO);
  }

  @Test
  void porIdDesconhecidoVazio() {
    assertThat(catalogo.porId("vulcao")).isEmpty();
  }

  @Test
  void todosNaOrdemDeRegistro() {
    assertThat(catalogo.todos()).containsExactly(Terraco.CENARIO, CenarioMinimo.CENARIO);
  }

  @Test
  void idRepetidoNaoConstroi() {
    List<Cenario> repetidos = List.of(Terraco.CENARIO, Terraco.CENARIO);

    assertThatThrownBy(() -> new CatalogoDeCenarios(repetidos))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("terraco");
  }

  @Test
  void listaVaziaNaoConstroi() {
    List<Cenario> nenhum = List.of();

    assertThatThrownBy(() -> new CatalogoDeCenarios(nenhum))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
