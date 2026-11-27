package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.web.ServidorHttpLento;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Prova, com um servidor de verdade, que a fábrica HTTP recebe os timeouts da configuração. */
class EmailResendTimeoutTest {

  @Test
  void leituraMaisLentaQueOTimeoutViraFalhaAntesDaResposta() {
    ConfiguracaoDeEmail configuracao =
        new ConfiguracaoDeEmail(
            "resend",
            "Duora <entrar@meetduora.com>",
            new ConfiguracaoDeEmail.Resend(
                "re_teste", Duration.ofSeconds(5), Duration.ofMillis(200)));
    MensagemDeEmail mensagem =
        new MensagemDeEmail("ana@exemplo.com", "Assunto", "Texto", "<p>Texto</p>");

    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(Duration.ofSeconds(1))) {
      EmailResend resend =
          EmailResend.comTimeouts(
              RestClient.builder(), configuracao, lento.uri().resolve("emails"));

      long inicio = System.nanoTime();
      assertThatThrownBy(() -> resend.enviar(mensagem, UUID.randomUUID()))
          .isInstanceOf(FalhaNoEnvio.class);
      Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

      assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
    }
  }
}
