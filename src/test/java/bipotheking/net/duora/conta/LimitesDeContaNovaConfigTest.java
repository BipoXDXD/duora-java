package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LimitesDeContaNovaConfigTest {

  @Test
  void aceitaLimitesPositivos() {
    assertThat(new LimitesDeContaNovaConfig(200, 10, 3).convitesPorDia()).isEqualTo(3);
  }

  @ParameterizedTest
  @CsvSource({
    "0, 10, 3, duora.conta-nova.mensagens-por-dia",
    "200, 0, 3, duora.conta-nova.imagens-por-dia",
    "200, 10, -1, duora.conta-nova.convites-por-dia"
  })
  void recusaLimiteMenorQueUm(int mensagens, int imagens, int convites, String propriedade) {
    assertThatThrownBy(() -> new LimitesDeContaNovaConfig(mensagens, imagens, convites))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(propriedade);
  }
}
