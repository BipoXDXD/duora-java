package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.TipoDeLimiteNovo;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegraDeContaNovaTest {

  private static final Instant ATIVADA_EM = Instant.parse("2026-11-22T12:00:00Z");

  @Test
  void contaCom6Dias23h59min59sAindaENova() {
    Instant agora = ATIVADA_EM.plus(Duration.parse("P6DT23H59M59S"));

    assertThat(RegraDeContaNova.emPeriodoDeContaNova(ATIVADA_EM, agora)).isTrue();
  }

  @Test
  void contaCom7DiasExatosNaoENova() {
    Instant agora = ATIVADA_EM.plus(Duration.ofDays(7));

    assertThat(RegraDeContaNova.emPeriodoDeContaNova(ATIVADA_EM, agora)).isFalse();
  }

  @Test
  void relogioAntesDaAtivacaoContaComoNova() {
    Instant agora = ATIVADA_EM.minusSeconds(1);

    assertThat(RegraDeContaNova.emPeriodoDeContaNova(ATIVADA_EM, agora)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"MENSAGEM, 200", "IMAGEM, 10", "CONVITE, 3"})
  void limiteDeCadaTipo(TipoDeLimiteNovo tipo, int esperado) {
    assertThat(RegraDeContaNova.limite(tipo, 200, 10, 3)).isEqualTo(esperado);
  }
}
