package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * O envio da foto reserva a chave de idempotência antes de processar: se a fila e o processamento
 * passassem da reserva, outra requisição com a mesma chave poderia retomá-la no meio.
 */
@SpringBootTest
class OrcamentoDeTempoDaFotoTest {

  @Autowired private ConfigDeImagens imagens;

  @Value("${duora.idempotencia.reserva-maxima}")
  private Duration reservaMaxima;

  @Test
  void prazoMaisEsperaCabemNaReservaDeIdempotencia() {
    assertThat(imagens.prazo().plus(imagens.espera())).isLessThan(reservaMaxima);
  }
}
