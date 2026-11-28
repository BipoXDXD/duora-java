package bipotheking.net.duora.conta.infra;

import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.ServiceMetadata;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.textract.TextractClient;

/**
 * Monta os clientes síncronos da AWS usados pela verificação de idade. Região fixa {@code
 * us-east-1} (a SCP da conta só libera essa), credencial só da {@link ConfiguracaoAws} (nunca
 * {@code ~/.aws}, {@code AWS_*} nem {@code aws.*} do sistema), endpoint sempre fixado (o público do
 * serviço, sem FIPS nem dual-stack, para {@code AWS_ENDPOINT_URL*} e o {@code endpoint_url} do
 * perfil não desviarem as chamadas), nenhum arquivo de perfil, HTTP do JDK e os timeouts do
 * orçamento: {@code conexao} por conexão, {@code tentativa} por leitura e por tentativa, {@code
 * chamada} para a chamada inteira, com no máximo duas tentativas. O {@code endpoint} só existe nos
 * testes, que apontam para um servidor local.
 */
final class ClientesAws {

  static final Region REGIAO = Region.US_EAST_1;

  private static final int TENTATIVAS = 2;

  /** No lugar do {@code ~/.aws/config} e do {@code ~/.aws/credentials}: nenhum perfil. */
  private static final ProfileFile SEM_PERFIL =
      ProfileFile.builder().content("").type(ProfileFile.Type.CONFIGURATION).build();

  private ClientesAws() {}

  static RekognitionClient rekognition(ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    return rekognition(configuracao, configuracao.credencial(), endpoint);
  }

  static StsClient sts(ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    return sts(configuracao, configuracao.credencial(), endpoint);
  }

  static TextractClient textract(ConfiguracaoAws configuracao, Optional<URI> endpoint) {
    return configurar(
            TextractClient.builder(),
            configuracao,
            configuracao.credencial(),
            endpoint.orElseGet(() -> oficial(TextractClient.serviceMetadata())))
        .build();
  }

  /**
   * Com outra credencial (a curta do navegador, nos smokes), mas com a região, o HTTP e os timeouts
   * da {@link ConfiguracaoAws}.
   */
  static RekognitionClient rekognition(
      ConfiguracaoAws configuracao, AwsCredentialsProvider credencial, Optional<URI> endpoint) {
    return configurar(
            RekognitionClient.builder(),
            configuracao,
            credencial,
            endpoint.orElseGet(() -> oficial(RekognitionClient.serviceMetadata())))
        .build();
  }

  /** Como {@link #rekognition(ConfiguracaoAws, AwsCredentialsProvider, Optional)}, para o STS. */
  static StsClient sts(
      ConfiguracaoAws configuracao, AwsCredentialsProvider credencial, Optional<URI> endpoint) {
    return configurar(
            StsClient.builder(),
            configuracao,
            credencial,
            endpoint.orElseGet(() -> oficial(StsClient.serviceMetadata())))
        .build();
  }

  private static <B extends AwsClientBuilder<B, ?> & AwsSyncClientBuilder<B, ?>> B configurar(
      B builder, ConfiguracaoAws configuracao, AwsCredentialsProvider credencial, URI endpoint) {
    builder
        .region(REGIAO)
        .credentialsProvider(credencial)
        .endpointOverride(endpoint)
        .fipsEnabled(false)
        .dualstackEnabled(false)
        .httpClientBuilder(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(configuracao.conexao())
                .socketTimeout(configuracao.tentativa()))
        .overrideConfiguration(o ->
            o.defaultProfileFile(SEM_PERFIL)
                .apiCallAttemptTimeout(configuracao.tentativa())
                .apiCallTimeout(configuracao.chamada())
                .retryStrategy(r -> r.maxAttempts(TENTATIVAS)));
    return builder;
  }

  /** O endpoint público do serviço na {@link #REGIAO}, por HTTPS. */
  private static URI oficial(ServiceMetadata servico) {
    return URI.create("https://" + servico.endpointFor(REGIAO));
  }
}
