package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FaixaEstimadaTest {

  @Test
  void baixaNegativaRecusada() {
    assertThatThrownBy(() -> new FaixaEstimada(-1, 5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void altaMenorQueBaixaRecusada() {
    assertThatThrownBy(() -> new FaixaEstimada(30, 29))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void faixaDeUmAnoAceita() {
    FaixaEstimada faixa = new FaixaEstimada(20, 20);

    assertThat(faixa.baixa()).isEqualTo(20);
    assertThat(faixa.alta()).isEqualTo(20);
  }
}
