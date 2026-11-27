package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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

  private static final Set<String> ERROS_DE_CONFIGURACAO =
      Set.of("missing-input-secret", "invalid-input-secret");

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
  public boolean verificar(@Nullable String token, @Nullable String ip) {
    if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
      return false;
    }
    MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
    formulario.add("secret", config.segredo());
    formulario.add("response", token);
    if (ip != null && !ip.isBlank()) {
      formulario.add("remoteip", ip);
    }
    try {
      RespostaSiteverify resposta =
          cliente
              .post()
              .uri(config.url())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(formulario)
              .retrieve()
              .body(RespostaSiteverify.class);
      if (resposta == null) {
        return false;
      }
      avisarSeOSegredoFoiRecusado(resposta);
      return resposta.success();
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

  /** Segredo ausente ou inválido é erro de configuração, não de robô: merece aviso. */
  private static void avisarSeOSegredoFoiRecusado(RespostaSiteverify resposta) {
    List<String> deConfiguracao =
        resposta.codigosDeErro().stream().filter(ERROS_DE_CONFIGURACAO::contains).toList();
    if (!deConfiguracao.isEmpty()) {
      LOG.warn("Turnstile recusou o segredo {}: confira duora.turnstile.segredo", deConfiguracao);
    }
  }

  /**
   * Só os campos que interessam. A resposta real traz {@code challenge_ts}, {@code hostname},
   * {@code action} e {@code cdata}, e o Jackson da aplicação recusa campo desconhecido.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RespostaSiteverify(
      boolean success, @JsonProperty("error-codes") List<String> codigosDeErro) {

    /** {@code error-codes} ausente vira lista vazia. */
    RespostaSiteverify {
      codigosDeErro = codigosDeErro == null ? List.of() : List.copyOf(codigosDeErro);
    }
  }
}
