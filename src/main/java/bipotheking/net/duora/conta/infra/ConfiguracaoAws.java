package bipotheking.net.duora.conta.infra;

import static bipotheking.net.duora.conta.infra.Exigencias.exigir;

import java.time.Duration;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Acesso do back à AWS ({@code duora.verificacao.aws.*}). As chaves só existem com o provedor
 * {@code aws}: o bean real chama {@link #exigirCompleta()} antes de montar os clientes, e dev e
 * test sobem sem elas. Os timeouts somam o orçamento travado pelo {@code OrcamentoDeTempoTest}:
 * {@code conexao} por conexão, {@code tentativa} por tentativa e {@code chamada} para a chamada
 * inteira, repetição incluída.
 */
@ConfigurationProperties("duora.verificacao.aws")
record ConfiguracaoAws(
    @Nullable String accessKeyId,
    @Nullable String secretAccessKey,
    @Nullable String papelCapturaArn,
    @DefaultValue("PT1S") Duration conexao,
    @DefaultValue("PT2S") Duration tentativa,
    @DefaultValue("PT4S") Duration chamada) {

  private static final String PREFIXO = "duora.verificacao.aws.";
  private static final Pattern ARN_DE_PAPEL =
      Pattern.compile("^arn:aws:iam::\\d{12}:role/[\\w+=,.@-]+$");

  /**
   * Recusa chave ausente ou ARN fora do formato; a mensagem cita só a propriedade. Devolve o ARN do
   * papel de captura já validado.
   */
  String exigirCompleta() {
    credencial();
    String arn = exigir(papelCapturaArn, PREFIXO + "papel-captura-arn");
    if (!ARN_DE_PAPEL.matcher(arn).matches()) {
      throw new IllegalArgumentException(
          PREFIXO + "papel-captura-arn fora do formato arn:aws:iam::<conta>:role/<nome>");
    }
    return arn;
  }

  /**
   * A credencial do back, só desta configuração; chave ausente é recusada como em {@link
   * #exigirCompleta()}.
   */
  AwsCredentialsProvider credencial() {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
            exigir(accessKeyId, PREFIXO + "access-key-id"),
            exigir(secretAccessKey, PREFIXO + "secret-access-key")));
  }

  /** Sem chave, segredo nem ARN (o ARN traz o número da conta). */
  @Override
  public String toString() {
    return "ConfiguracaoAws[accessKeyId=***, secretAccessKey=***, papelCapturaArn=***, conexao="
        + conexao
        + ", tentativa="
        + tentativa
        + ", chamada="
        + chamada
        + "]";
  }
}
