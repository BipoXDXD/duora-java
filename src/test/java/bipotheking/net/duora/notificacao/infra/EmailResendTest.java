package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class EmailResendTest {

  private static final String API = "https://api.resend.com/emails";
  private static final String REMETENTE = "Duora <entrar@meetduora.com>";
  private static final UUID ID_DA_MENSAGEM =
      UUID.fromString("0190a6b2-7c1e-7d3a-9f00-0000000000a1");
  private static final MensagemDeEmail MENSAGEM =
      new MensagemDeEmail(
          "ana@exemplo.com",
          "Seu link de entrada",
          "Entre pelo link: https://meetduora.com/entrar/confirmar#t=abc",
          "<p>Entre pelo <a href=\"https://meetduora.com/entrar/confirmar#t=abc\">link</a></p>");
  private static final String CORPO_ESPERADO =
      """
      {"from": "Duora <entrar@meetduora.com>",
       "to": ["ana@exemplo.com"],
       "subject": "Seu link de entrada",
       "text": "Entre pelo link: https://meetduora.com/entrar/confirmar#t=abc",
       "html": "<p>Entre pelo <a href=\\"https://meetduora.com/entrar/confirmar#t=abc\\">link</a></p>"}
      """;
  private static final String RESPOSTA_REAL =
      """
      {"id": "49a3999c-0ce1-4ea6-ab68-afcd6dc2e794"}
      """;

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  static ConfiguracaoDeEmail configuracao(String chave) {
    return new ConfiguracaoDeEmail(
        "resend",
        REMETENTE,
        new ConfiguracaoDeEmail.Resend(chave, Duration.ofSeconds(5), Duration.ofSeconds(10)));
  }

  private static EmailResend resend(RestClient.Builder builder, String chave) {
    return new EmailResend(builder, configuracao(chave), EmailResend.API);
  }

  @Test
  void postaNaApiComAChaveEAIdempotencia() {
    server
        .expect(requestTo(API))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer re_teste"))
        .andExpect(header("Idempotency-Key", ID_DA_MENSAGEM.toString()))
        .andRespond(withSuccess(RESPOSTA_REAL, MediaType.APPLICATION_JSON));

    resend(builder, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM);

    server.verify();
  }

  @Test
  void corpoTemFromToSubjectTextEHtml() {
    server
        .expect(requestTo(API))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json(CORPO_ESPERADO, JsonCompareMode.STRICT))
        .andRespond(withSuccess(RESPOSTA_REAL, MediaType.APPLICATION_JSON));

    resend(builder, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM);

    server.verify();
  }

  @Test
  void erro500ViraFalhaNoEnvio() {
    server.expect(requestTo(API)).andRespond(withServerError());

    assertThatThrownBy(() -> resend(builder, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM))
        .isInstanceOf(FalhaNoEnvio.class)
        .hasMessageContaining("500");
  }

  @Test
  void erro422ViraFalhaNoEnvioSemODestinatario() {
    server
        .expect(requestTo(API))
        .andRespond(
            withStatus(HttpStatusCode.valueOf(422))
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"statusCode": 422, "name": "validation_error",
                     "message": "Invalid `to` field: ana@exemplo.com"}
                    """));

    assertThatThrownBy(() -> resend(builder, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM))
        .isInstanceOf(FalhaNoEnvio.class)
        .hasMessageContaining("422")
        .hasMessageNotContaining("ana@exemplo.com");
  }

  @Test
  void timeoutViraFalhaNoEnvio() {
    server.expect(requestTo(API)).andRespond(withException(new SocketTimeoutException()));

    assertThatThrownBy(() -> resend(builder, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM))
        .isInstanceOf(FalhaNoEnvio.class)
        .hasMessageNotContaining("ana@exemplo.com");
  }

  @Test
  void chaveNaoApareceNoLogNemNaFalha(CapturedOutput saida) {
    String canario = "re_CANARY-" + UUID.randomUUID();
    server
        .expect(requestTo(API))
        .andExpect(header("Authorization", "Bearer " + canario))
        .andRespond(withStatus(HttpStatusCode.valueOf(401)));

    assertThatThrownBy(() -> resend(builder, canario).enviar(MENSAGEM, ID_DA_MENSAGEM))
        .isInstanceOf(FalhaNoEnvio.class)
        .satisfies(
            falha -> assertThat(falha.toString()).doesNotContain(canario).doesNotContain("CANARY"));
    server.verify();
    assertThat(saida.getAll()).doesNotContain("CANARY");
  }

  @Test
  void builderDoBootComJsonEstritoAceitaARespostaReal() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class,
                HttpMessageConvertersAutoConfiguration.class,
                RestClientAutoConfiguration.class))
        .withPropertyValues("spring.jackson.deserialization.fail-on-unknown-properties=true")
        .run(contexto -> {
          RestClient.Builder doBoot = contexto.getBean(RestClient.Builder.class);
          MockRestServiceServer servidor = MockRestServiceServer.bindTo(doBoot).build();
          servidor
              .expect(requestTo(API))
              .andExpect(content().json(CORPO_ESPERADO, JsonCompareMode.STRICT))
              .andRespond(withSuccess(RESPOSTA_REAL, MediaType.APPLICATION_JSON));

          resend(doBoot, "re_teste").enviar(MENSAGEM, ID_DA_MENSAGEM);

          servidor.verify();
        });
  }
}
