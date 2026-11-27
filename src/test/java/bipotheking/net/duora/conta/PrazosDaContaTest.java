package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.retencao.ExpiracaoDeDados;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** As políticas de retenção do módulo de contas, com os nomes e prazos que a expiração expõe. */
class PrazosDaContaTest extends BancoDeTeste {

  @Autowired private ExpiracaoDeDados expiracao;

  @Test
  void prazosDaContaExpostos() {
    assertThat(expiracao.prazos())
        .containsEntry("links-de-entrada", Duration.parse("P1D"))
        .containsEntry("sessoes-expiradas", Duration.parse("PT0S"))
        .containsEntry("registros-de-acesso", Duration.parse("P183D"))
        .containsEntry("uso-conta-nova", Duration.parse("P8D"))
        .containsEntry("denuncias-publicas", Duration.parse("P183D"));
  }
}
