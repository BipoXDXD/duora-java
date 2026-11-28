package bipotheking.net.duora.push.infra;

import bipotheking.net.duora.push.EnderecoDePush;
import bipotheking.net.duora.push.EnvioDePush;
import bipotheking.net.duora.push.InscricaoDePush;
import bipotheking.net.duora.push.ResultadoDoEnvio;
import bipotheking.net.duora.push.TipoDePush;
import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web Push pela interaso (ADR 0009): a biblioteca só cifra o payload (RFC 8291) e monta os
 * cabeçalhos VAPID (RFC 8292); o envio é do nosso {@link HttpClient}, sem seguir redirecionamento e
 * com timeouts curtos. O endpoint passa de novo pela allowlist antes de cada envio. O payload leva
 * só o tipo (H4). Logs citam só o id da inscrição, nunca o endpoint.
 */
final class WebPushInterasoAdapter implements EnvioDePush, AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WebPushInterasoAdapter.class);

  static final Duration CONEXAO = Duration.ofSeconds(5);
  static final Duration REQUISICAO = Duration.ofSeconds(10);
  static final int TTL_SEGUNDOS = 3600;

  private final WebPush webPush;
  private final HttpClient http;
  private final Function<String, URI> validador;

  /** A instância de produção, com a allowlist da config. */
  WebPushInterasoAdapter(ConfiguracaoPush config) {
    this(config, bruto -> EnderecoDePush.validar(bruto, config.hostsPermitidos()));
  }

  /** Os testes trocam a allowlist para falar com um servidor HTTP local. */
  WebPushInterasoAdapter(ConfiguracaoPush config, Function<String, URI> validador) {
    // A config ligada já garantiu as duas chaves e o assunto; chave malformada derruba o boot aqui.
    this.webPush =
        new WebPush(
            Objects.requireNonNull(config.assunto()),
            VapidKeys.fromUncompressedBytes(
                Objects.requireNonNull(config.chavePublica()),
                Objects.requireNonNull(config.chavePrivada())));
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(CONEXAO)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    this.validador = validador;
  }

  @Override
  public ResultadoDoEnvio enviar(InscricaoDePush inscricao, TipoDePush tipo) {
    try {
      URI endpoint = validador.apply(inscricao.endpoint().toString());
      byte[] corpo =
          webPush.getBody(
              payload(tipo), decodificar(inscricao.p256dh()), decodificar(inscricao.auth()));
      Map<String, String> cabecalhos =
          webPush.getHeaders(endpoint.toString(), TTL_SEGUNDOS, tipo.tag(), WebPush.Urgency.Normal);
      HttpRequest.Builder pedido =
          HttpRequest.newBuilder(endpoint)
              .timeout(REQUISICAO)
              .POST(HttpRequest.BodyPublishers.ofByteArray(corpo));
      cabecalhos.forEach(pedido::header);
      int status = http.send(pedido.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
      ResultadoDoEnvio resultado = resultado(status);
      if (resultado != ResultadoDoEnvio.ENTREGUE) {
        LOG.warn("Push para a inscrição {} respondeu {}", inscricao.id(), status);
      }
      return resultado;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Push para a inscrição {} interrompido", inscricao.id());
      return ResultadoDoEnvio.FALHOU;
    } catch (Exception e) {
      // Exception, não só RuntimeException: a interaso (Kotlin) lança exceções verificadas sem
      // declará-las. Só a classe da causa vai para o log; a mensagem pode citar o endpoint.
      LOG.warn("Push para a inscrição {} falhou: {}", inscricao.id(), e.getClass().getSimpleName());
      return ResultadoDoEnvio.FALHOU;
    }
  }

  /** 2xx entrega; o resto a interaso classifica, e o que ela não classifica como expirado falha. */
  private ResultadoDoEnvio resultado(int status) {
    if (status >= 200 && status < 300) {
      return ResultadoDoEnvio.ENTREGUE;
    }
    try {
      return webPush.getSubscriptionState(status, "") == WebPush.SubscriptionState.EXPIRED
          ? ResultadoDoEnvio.EXPIRADA
          : ResultadoDoEnvio.FALHOU;
    } catch (Exception e) {
      // WebPushStatusException (verificada, não declarada) para status sem classificação.
      return ResultadoDoEnvio.FALHOU;
    }
  }

  private static byte[] payload(TipoDePush tipo) {
    return ("{\"tipo\":\"" + tipo.valor() + "\"}").getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] decodificar(String base64Url) {
    return Base64.getUrlDecoder().decode(base64Url);
  }

  @Override
  public void close() {
    http.close();
  }
}
