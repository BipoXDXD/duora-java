package bipotheking.net.duora.sala.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimativaDeVagaTest {

  private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");

  @Test
  void terceiraPessoaEsperaOSegundoTermino() {
    List<Instant> terminos = List.of(daqui(4), daqui(9), daqui(14));

    assertThat(EstimativaDeVaga.minutos(3, terminos, AGORA)).hasValue(9);
  }

  @Test
  void arredondaParaCima() {
    List<Instant> terminos = List.of(AGORA.plus(Duration.ofMinutes(8).plusSeconds(1)));

    assertThat(EstimativaDeVaga.minutos(1, terminos, AGORA)).hasValue(9);
  }

  @Test
  void faltamTerminosVazio() {
    List<Instant> terminos = List.of(daqui(4));

    assertThat(EstimativaDeVaga.minutos(3, terminos, AGORA)).isEmpty();
  }

  @Test
  void terminoJaPassadoDaZero() {
    List<Instant> terminos = List.of(AGORA.minusSeconds(30));

    assertThat(EstimativaDeVaga.minutos(2, terminos, AGORA)).hasValue(0);
  }

  private static Instant daqui(long minutos) {
    return AGORA.plus(Duration.ofMinutes(minutos));
  }
}
