package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VerificacaoHumanaDeTesteTest {

  private final VerificacaoHumanaDeTeste duble = new VerificacaoHumanaDeTeste();

  static Stream<Arguments> recusados() {
    return Stream.of(
        Arguments.of((String) null),
        Arguments.of(""),
        Arguments.of(" "),
        Arguments.of("a".repeat(2049)),
        Arguments.of(VerificacaoHumanaDeTeste.TOKEN_RECUSADO));
  }

  @ParameterizedTest
  @MethodSource("recusados")
  void recusaOQueOTurnstileRecusaria(String token) {
    assertThat(duble.verificar(token, "203.0.113.7")).isFalse();
  }

  @Test
  void aceitaOTokenDoWidgetDeTeste() {
    assertThat(duble.verificar("XXXX.DUMMY.TOKEN.XXXX", "203.0.113.7")).isTrue();
  }

  @Test
  void contaAsChamadasAteZerar() {
    duble.verificar("a", null);
    duble.verificar("b", null);
    int antes = duble.chamadas();
    duble.zerar();

    assertThat(antes).isEqualTo(2);
    assertThat(duble.chamadas()).isZero();
  }
}
