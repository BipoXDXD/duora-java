package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Fala com a AWS de verdade, com as mesmas peças da produção ({@link ClientesAws} e {@link
 * ProvaDeVidaAws}). É manual: só roda com {@code DUORA_AWS_VERIFICACAO_*}, {@code
 * DUORA_AWS_PAPEL_CAPTURA_ARN} e {@code DUORA_SMOKE_ROSTO} (caminho de um JPEG ou PNG de rosto
 * sintético, fora do repositório) e é pulado sem elas, inclusive na CI. Custa menos de US$ 0,02. Os
 * relatórios do failsafe viram artefato da CI, por isso toda falha mostra só contagens, booleanos,
 * status, classes e códigos: nunca a credencial curta, a chave, ARNs nem o corpo das respostas.
 */
@EnabledIfEnvironmentVariable(named = "DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DUORA_SMOKE_ROSTO", matches = ".+")
class VerificacaoAwsSmokeIT {

  private static final String PAPEL_DE_CAPTURA = "assumed-role/duora-java-liveness-captura/";

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
    RekognitionClient rekognition = ClientesAws.rekognition(config, Optional.empty());

    try (ProvaDeVidaAws prova =
        new ProvaDeVidaAws(
            rekognition,
            ClientesAws.sts(config, Optional.empty()),
            papelCapturaArn,
            new MedicaoDeFornecedor(new SimpleMeterRegistry()))) {
      SessaoDeProva sessao = real("criar sessão", () -> prova.criarSessao(tentativa));
      assertThat(sessao.id().length()).as("tamanho do sessionId").isEqualTo(36);

      Instant antes = Instant.now();
      CredencialCurta credencial = real("emitir credencial", () -> prova.emitir(tentativa));
      long minutos = Duration.between(antes, credencial.expiraEm()).toMinutes();
      assertThat(minutos).as("minutos até a credencial curta expirar").isBetween(14L, 16L);

      StaticCredentialsProvider curta =
          StaticCredentialsProvider.create(
              AwsSessionCredentials.create(
                  credencial.accessKeyId(),
                  credencial.secretAccessKey(),
                  credencial.sessionToken()));
      try (StsClient stsCurto = ClientesAws.sts(config, curta, Optional.empty());
          RekognitionClient rekognitionCurto =
              ClientesAws.rekognition(config, curta, Optional.empty())) {
        String sessaoDoPapel =
            PAPEL_DE_CAPTURA + ProvaDeVidaAws.PREFIXO_DA_SESSAO_DO_PAPEL + tentativa;
        String arn = real("GetCallerIdentity", () -> stsCurto.getCallerIdentity().arn());
        boolean papelCerto = arn.endsWith(sessaoDoPapel);
        assertThat(papelCerto)
            .as("a credencial curta é do papel de captura e da tentativa")
            .isTrue();

        Throwable falha = catchThrowable(() -> rekognitionCurto.createFaceLivenessSession(r -> {}));
        assertThat(classe(falha))
            .as("classe da falha ao criar sessão com a credencial curta")
            .isIn("AccessDeniedException", "RekognitionException");
        RekognitionException negado = (RekognitionException) Objects.requireNonNull(falha);
        assertThat(negado.statusCode()).as("status HTTP").isEqualTo(400);
        assertThat(codigo(negado.awsErrorDetails()))
            .as("código de erro")
            .isEqualTo("AccessDeniedException");
      }

      assertThat(real("resultado", () -> prova.resultado(sessao.id())).situacao())
          .isEqualTo(SituacaoDaProva.CRIADA);

      List<FaceDetail> rostos =
          real(
              "DetectFaces",
              () ->
                  rekognition
                      .detectFaces(r ->
                          r.image(i -> i.bytes(SdkBytes.fromByteArray(rosto)))
                              .attributes(Attribute.AGE_RANGE))
                      .faceDetails());
      assertThat(rostos.size()).as("rostos detectados").isEqualTo(1);
      assertThat(rostos.getFirst().ageRange().low())
          .as("idade mínima estimada")
          .isGreaterThanOrEqualTo(18);
    }
  }

  /**
   * Chamada real cuja falha vira {@link AssertionError} só com o passo, a classe, o status e o
   * código, sem a causa: a mensagem de um erro da AWS pode trazer o pedido canônico, com o token da
   * credencial curta.
   */
  private static <T> T real(String passo, Supplier<T> chamada) {
    try {
      return chamada.get();
    } catch (RuntimeException e) {
      String detalhe =
          e instanceof AwsServiceException servico
              ? " (" + servico.statusCode() + " " + codigo(servico.awsErrorDetails()) + ")"
              : "";
      throw new AssertionError(passo + " falhou com " + classe(e) + detalhe);
    }
  }

  /** Só o nome simples da classe: a mensagem pode trazer ARN ou detalhes da conta. */
  private static String classe(@Nullable Throwable falha) {
    return falha == null ? "nenhuma" : falha.getClass().getSimpleName();
  }

  private static String codigo(@Nullable AwsErrorDetails detalhes) {
    return detalhes == null ? "sem detalhes" : String.valueOf(detalhes.errorCode());
  }

  private static String variavel(String nome) {
    return Objects.requireNonNull(System.getenv(nome), nome);
  }
}
