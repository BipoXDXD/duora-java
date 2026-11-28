package bipotheking.net.duora.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EventoTest {

  @ParameterizedTest
  @ValueSource(strings = {"fila.pareado", "encontro.x", "voz.sinal"})
  void nomeComPrefixoDeDominioEAceito(String nome) {
    assertThat(new Evento(nome, Map.of()).nome()).isEqualTo(nome);
  }

  @ParameterizedTest
  @ValueSource(strings = {"pareado", "Fila.x", "sala.x", "fila.", "stream.batimento"})
  void nomeForaDoPadraoERecusado(String nome) {
    assertThatThrownBy(() -> new Evento(nome, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
