package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Fala com a AWS de verdade, com as mesmas peças da produção ({@link ClientesAws} e {@link
 * ProvaDeVidaAws}). É manual: só roda com {@code DUORA_AWS_VERIFICACAO_*}, {@code
 * DUORA_AWS_PAPEL_CAPTURA_ARN} e {@code DUORA_SMOKE_ROSTO} (caminho de um JPEG ou PNG de rosto
 * sintético, fora do repositório) e é pulado sem elas, inclusive na CI. Custa menos de US$ 0,02.
 * Nenhuma asserção imprime a credencial curta, a chave ou o corpo das respostas: as falhas mostram
 * só booleanos, contagens, status e códigos de erro.
 */
@EnabledIfEnvironmentVariable(named = "DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DUORA_SMOKE_ROSTO", matches = ".+")
class VerificacaoAwsSmokeIT {

  private static final String PAPEL_ASSUMIDO = "assumed-role/duora-java-liveness-captura/duora-";

  @Test
  void sessaoRealCredencialCurtaEFaixaDeAdulto() throws IOException {
    ConfiguracaoAws config =
        new ConfiguracaoAws(
            System.getenv("DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID"),
            System.getenv("DUORA_AWS_VERIFICACAO_SECRET_ACCESS_KEY"),
            System.getenv("DUORA_AWS_PAPEL_CAPTURA_ARN"),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4));
    String papelCapturaArn = config.exigirCompleta();
    byte[] rosto = Files.readAllBytes(Path.of(variavel("DUORA_SMOKE_ROSTO")));
    UUID tentativa = UUID.randomUUID();

    try (RekognitionClient rekognition = ClientesAws.rekognition(config, Optional.empty());
        ProvaDeVidaAws prova =
            new ProvaDeVidaAws(
                ClientesAws.rekognition(config, Optional.empty()),
                ClientesAws.sts(config, Optional.empty()),
                papelCapturaArn,
                new MedicaoDeFornecedor(new SimpleMeterRegistry()))) {
      SessaoDeProva sessao = prova.criarSessao(tentativa);
      assertThat(sessao.id()).as("sessionId").hasSize(36);

      Instant antes = Instant.now();
      CredencialCurta credencial = prova.emitir(tentativa);
      assertThat(credencial.expiraEm())
          .as("expiração da credencial curta")
          .isBetween(antes.plus(Duration.ofMinutes(14)), antes.plus(Duration.ofMinutes(16)));

      StaticCredentialsProvider curta =
          StaticCredentialsProvider.create(
              AwsSessionCredentials.create(
                  credencial.accessKeyId(),
                  credencial.secretAccessKey(),
                  credencial.sessionToken()));
      try (StsClient stsCurto =
              StsClient.builder()
                  .region(ClientesAws.REGIAO)
                  .credentialsProvider(curta)
                  .httpClientBuilder(UrlConnectionHttpClient.builder())
                  .build();
          RekognitionClient rekognitionCurto =
              RekognitionClient.builder()
                  .region(ClientesAws.REGIAO)
                  .credentialsProvider(curta)
                  .httpClientBuilder(UrlConnectionHttpClient.builder())
                  .build()) {
        boolean papelCerto = stsCurto.getCallerIdentity().arn().contains(PAPEL_ASSUMIDO);
        assertThat(papelCerto).as("a credencial curta é do papel de captura").isTrue();

        RekognitionException negado =
            catchThrowableOfType(
                RekognitionException.class,
                () -> rekognitionCurto.createFaceLivenessSession(r -> {}));
        assertThat(negado).as("a credencial curta não cria sessão").isNotNull();
        assertThat(negado.statusCode()).isEqualTo(400);
        assertThat(Objects.requireNonNull(negado.awsErrorDetails()).errorCode())
            .isEqualTo("AccessDeniedException");
      }

      assertThat(prova.resultado(sessao.id()).situacao()).isEqualTo(SituacaoDaProva.CRIADA);

      List<FaceDetail> rostos =
          rekognition
              .detectFaces(r ->
                  r.image(i -> i.bytes(SdkBytes.fromByteArray(rosto)))
                      .attributes(Attribute.AGE_RANGE))
              .faceDetails();
      assertThat(rostos).as("rostos no rosto sintético").hasSize(1);
      assertThat(rostos.getFirst().ageRange().low())
          .as("idade mínima estimada")
          .isGreaterThanOrEqualTo(18);
    }
  }

  private static String variavel(String nome) {
    return Objects.requireNonNull(System.getenv(nome), nome);
  }
}
