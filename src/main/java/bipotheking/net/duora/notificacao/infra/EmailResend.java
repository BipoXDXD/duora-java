package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.EnvioDeEmail;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envio pelo {@code POST /emails} do Resend, com o id da mensagem da outbox no {@code
 * Idempotency-Key}: um retry depois de uma entrega que o Resend aceitou não duplica o e-mail. Não
 * lê o corpo da resposta. Qualquer erro vira {@link FalhaNoEnvio} só com o status ou a classe da
 * causa: o corpo de erro do Resend pode citar o destinatário, e nada aqui cita a chave.
 */
class EmailResend implements EnvioDeEmail {

  private static final Logger LOG = LoggerFactory.getLogger(EmailResend.class);

  static final URI API = URI.create("https://api.resend.com/emails");

  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private final RestClient cliente;
  private final URI destino;
  private final String remetente;
  private final String autorizacao;

  /** Usa o builder como veio: os testes o ligam a um {@code MockRestServiceServer}. */
  EmailResend(RestClient.Builder builder, ConfiguracaoDeEmail config, URI destino) {
    this.cliente = builder.build();
    this.destino = destino;
    this.remetente = config.remetente();
    this.autorizacao = "Bearer " + config.resend().chave();
  }

  /** A instância de produção: fábrica HTTP com os timeouts de conexão e leitura da config. */
  static EmailResend comTimeouts(
      RestClient.Builder builder, ConfiguracaoDeEmail config, URI destino) {
    HttpClientSettings timeouts =
        HttpClientSettings.defaults()
            .withConnectTimeout(config.resend().conexao())
            .withReadTimeout(config.resend().leitura());
    return new EmailResend(
        builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(timeouts)),
        config,
        destino);
  }

  @Override
  public void enviar(MensagemDeEmail mensagem, UUID chaveDeIdempotencia) {
    PedidoDeEnvio pedido =
        new PedidoDeEnvio(
            remetente,
            List.of(mensagem.para()),
            mensagem.assunto(),
            mensagem.texto(),
            mensagem.html());
    try {
      cliente
          .post()
          .uri(destino)
          .header(HttpHeaders.AUTHORIZATION, autorizacao)
          .header(IDEMPOTENCY_KEY, chaveDeIdempotencia.toString())
          .contentType(MediaType.APPLICATION_JSON)
          .body(pedido)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      // Separa chave errada (401/403) de instabilidade (5xx) antes das dez tentativas da outbox.
      int status = e.getStatusCode().value();
      LOG.warn("Resend respondeu {} à mensagem {}", status, chaveDeIdempotencia);
      throw new FalhaNoEnvio("Resend respondeu " + status);
    } catch (RestClientException e) {
      throw new FalhaNoEnvio(
          "Resend indisponível (" + e.getMostSpecificCause().getClass().getSimpleName() + ")");
    }
  }

  /** O corpo do {@code POST /emails}, com os nomes de campo da API. */
  record PedidoDeEnvio(String from, List<String> to, String subject, String text, String html) {}
}
