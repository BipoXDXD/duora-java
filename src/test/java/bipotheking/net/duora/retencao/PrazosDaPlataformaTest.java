package bipotheking.net.duora.retencao;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Os prazos da plataforma batem com a tabela da política de privacidade. */
class PrazosDaPlataformaTest extends BancoDeTeste {

  @Autowired private ExpiracaoDeDados expiracao;

  @Test
  void prazosDaPlataformaBatemComAPoliticaDePrivacidade() {
    assertThat(expiracao.prazos())
        .containsEntry("outbox-enviada", Duration.parse("P7D"))
        .containsEntry("outbox-falhada", Duration.parse("P30D"))
        .containsEntry("chaves-de-idempotencia", Duration.parse("P1D"));
  }
}
