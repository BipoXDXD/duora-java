package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Verificação humana pelo {@code siteverify} do Cloudflare Turnstile. Falha fechada: token
 * impossível, erro de rede, timeout ou resposta de erro viram {@code false}. Nunca loga o token nem
 * o segredo.
 */
class TurnstileVerificacao implements VerificacaoHumana {

  private static final Logger LOG = LoggerFactory.getLogger(TurnstileVerificacao.class);

  /** Limite documentado pela Cloudflare para o token do widget. */
  private static final int TAMANHO_MAXIMO_DO_TOKEN = 2048;

  private static final Duration TIMEOUT_DE_CONEXAO = Duration.ofSeconds(3);
  private static final Duration TIMEOUT_DE_LEITURA = Duration.ofSeconds(5);

  private final RestClient cliente;
  private final TurnstileConfig config;

  /** Usa o builder como veio: os testes o ligam a um {@code MockRestServiceServer}. */
  TurnstileVerificacao(RestClient.Builder builder, TurnstileConfig config) {
    this.cliente = builder.build();
    this.config = config;
  }

  /** A instância de produção: fábrica HTTP com conexão de 3 s e leitura de 5 s. */
  static TurnstileVerificacao comTimeouts(RestClient.Builder builder, TurnstileConfig config) {
    HttpClientSettings timeouts =
        HttpClientSettings.defaults()
            .withConnectTimeout(TIMEOUT_DE_CONEXAO)
            .withReadTimeout(TIMEOUT_DE_LEITURA);
    return new TurnstileVerificacao(
        builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(timeouts)), config);
  }

  @Override
  public boolean verificar(String token, String ip) {
    if (token.isBlank() || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
      return false;
    }
    MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
    formulario.add("secret", config.segredo());
    formulario.add("response", token);
    formulario.add("remoteip", ip);
    try {
      RespostaSiteverify resposta =
          cliente
              .post()
              .uri(config.url())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(formulario)
              .retrieve()
              .body(RespostaSiteverify.class);
      return resposta != null && resposta.success();
    } catch (RestClientResponseException e) {
      LOG.warn("Turnstile respondeu {}: verificação humana recusada", e.getStatusCode().value());
      return false;
    } catch (RestClientException e) {
      LOG.warn(
          "Turnstile indisponível ({}): verificação humana recusada",
          e.getMostSpecificCause().getClass().getSimpleName());
      return false;
    }
  }

  /** Só o campo que decide; os demais ({@code error-codes}, {@code hostname}…) são ignorados. */
  record RespostaSiteverify(boolean success) {}
}
