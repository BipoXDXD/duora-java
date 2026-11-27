package bipotheking.net.duora.conta.infra;

import static bipotheking.net.duora.conta.infra.Exigencias.exigir;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Acesso à Didit ({@code duora.verificacao.didit.*}). A chave só existe com o provedor {@code
 * didit}: o bean real chama {@link #exigirCompleta()}, e dev e test sobem sem ela. A leitura do
 * documento e a do rosto têm timeouts próprios; os dois passos, com a conexão de cada um, somam o
 * orçamento travado pelo {@code OrcamentoDeTempoTest}.
 */
@ConfigurationProperties("duora.verificacao.didit")
record ConfiguracaoDidit(
    @DefaultValue("https://verification.didit.me") URI url,
    @Nullable String apiKey,
    @DefaultValue("30") double limiarRosto,
    @DefaultValue("PT1S") Duration conexao,
    @DefaultValue("PT9S") Duration leituraDoDocumento,
    @DefaultValue("PT4S") Duration leituraDoRosto) {

  private static final String PREFIXO = "duora.verificacao.didit.";

  /** Recusa chave ausente ou URL sem {@code https}; a mensagem cita só a propriedade. */
  void exigirCompleta() {
    exigir(apiKey, PREFIXO + "api-key");
    if (!"https".equals(url.getScheme())) {
      throw new IllegalArgumentException(PREFIXO + "url precisa usar https");
    }
  }

  /** Sem a chave da API. */
  @Override
  public String toString() {
    return "ConfiguracaoDidit[url="
        + url
        + ", apiKey=***, limiarRosto="
        + limiarRosto
        + ", conexao="
        + conexao
        + ", leituraDoDocumento="
        + leituraDoDocumento
        + ", leituraDoRosto="
        + leituraDoRosto
        + "]";
  }
}
