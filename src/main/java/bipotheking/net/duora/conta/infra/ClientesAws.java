package bipotheking.net.duora.conta.infra;

import static bipotheking.net.duora.conta.infra.Exigencias.exigir;

import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Monta os clientes síncronos da AWS usados pela verificação de idade. Região fixa {@code
 * us-east-1} (a SCP da conta só libera essa), credencial só da {@link ConfiguracaoAws} (nunca
 * {@code ~/.aws}, {@code AWS_*} nem {@code aws.*} do sistema), HTTP do JDK e os timeouts do
 * orçamento: {@code conexao} por conexão, {@code tentativa} por leitura e por tentativa, {@code
 * chamada} para a chamada inteira, com no máximo duas tentativas. O {@code endpoint} só existe nos
 * testes, que apontam para um servidor local.
 */
final class ClientesAws {

  static final Region REGIAO = Region.US_EAST_1;

  private static final int TENTATIVAS = 2;
  private static final String PREFIXO = "duora.verificacao.aws.";

  private ClientesAws() {}

  static RekognitionClient rekognition(ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    return configurar(RekognitionClient.builder(), configuracao, endpoint).build();
  }

  static StsClient sts(ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    return configurar(StsClient.builder(), configuracao, endpoint).build();
  }

  private static <B extends AwsClientBuilder<B, ?> & AwsSyncClientBuilder<B, ?>> B configurar(
      B builder, ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    builder
        .region(REGIAO)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    exigir(configuracao.accessKeyId(), PREFIXO + "access-key-id"),
                    exigir(configuracao.secretAccessKey(), PREFIXO + "secret-access-key"))))
        .httpClientBuilder(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(configuracao.conexao())
                .socketTimeout(configuracao.tentativa()))
        .overrideConfiguration(o ->
            o.apiCallAttemptTimeout(configuracao.tentativa())
                .apiCallTimeout(configuracao.chamada())
                .retryStrategy(r -> r.maxAttempts(TENTATIVAS)));
    endpoint.ifPresent(builder::endpointOverride);
    return builder;
  }
}
