package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.web.ModeracaoDeImagem.Veredito;
import bipotheking.net.duora.web.ServidorHttpLento;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.rekognition.model.ThrottlingException;

/** Só a resposta da AWS sem rótulo bloqueado libera; toda falha deixa a foto para a revisão. */
class ModeracaoDeImagemRekognitionTest {

  private static final Duration SEM_RESPOSTA = Duration.ofMinutes(10);
  private static final int REDE_DE_SEGURANCA_EM_SEGUNDOS = 30;

  @TempDir private Path dir;

  @Test
  void semRotuloLibera() throws IOException {
    ModeracaoFalsa aws = ModeracaoFalsa.respondendo();

    Veredito veredito = new ModeracaoDeImagemRekognition(aws).avaliar(jpeg());

    assertThat(veredito).isEqualTo(Veredito.LIBERADA);
    DetectModerationLabelsRequest pedido = aws.pedido();
    assertThat(pedido).isNotNull();
    assertThat(pedido.minConfidence()).isEqualTo(ModeracaoDeImagemRekognition.LIMIAR);
    assertThat(pedido.image().bytes().asByteArray()).isEqualTo(Files.readAllBytes(jpeg()));
  }

  @ParameterizedTest
  @CsvSource({
    "Explicit Nudity, Explicit, 99",
    "Exposed Female Genitalia, Explicit Nudity, 60",
    "Partially Exposed Female Breast, Non-Explicit Nudity of Intimate parts and Kissing, 50",
    "Graphic Violence, Violence, 80",
    "Death and Emaciation, Visually Disturbing, 70",
    "Nazi Party, Hate Symbols, 90"
  })
  void rotuloBloqueadoSinaliza(String nome, String pai, float confianca) throws IOException {
    ModeracaoFalsa aws = ModeracaoFalsa.respondendo(rotulo(nome, pai, confianca));

    assertThat(new ModeracaoDeImagemRekognition(aws).avaliar(jpeg()))
        .isEqualTo(Veredito.SINALIZADA);
  }

  @ParameterizedTest
  @CsvSource({
    "Swimwear or Underwear, '', 99",
    "Exposed Male Nipple, Non-Explicit Nudity, 95",
    "Weapons, Violence, 90",
    "Alcoholic Beverages, Alcohol, 99",
    "Explicit Nudity, Explicit, 49.9"
  })
  void rotuloForaDaPoliticaOuAbaixoDoLimiarLibera(String nome, String pai, float confianca)
      throws IOException {
    ModeracaoFalsa aws = ModeracaoFalsa.respondendo(rotulo(nome, pai, confianca));

    assertThat(new ModeracaoDeImagemRekognition(aws).avaliar(jpeg())).isEqualTo(Veredito.LIBERADA);
  }

  @Test
  void acessoNegadoDeixaPendente() throws IOException {
    AwsServiceException negado =
        RekognitionException.builder()
            .statusCode(403)
            .awsErrorDetails(
                AwsErrorDetails.builder()
                    .errorCode("AccessDeniedException")
                    .errorMessage("arn:aws:iam::000000000000:user/x não pode")
                    .build())
            .build();

    Veredito veredito =
        new ModeracaoDeImagemRekognition(ModeracaoFalsa.falhando(negado)).avaliar(jpeg());

    assertThat(veredito).isEqualTo(Veredito.INDISPONIVEL);
  }

  @Test
  void limitacaoDeixaPendente() throws IOException {
    AwsServiceException limitada = ThrottlingException.builder().statusCode(400).build();

    assertThat(new ModeracaoDeImagemRekognition(ModeracaoFalsa.falhando(limitada)).avaliar(jpeg()))
        .isEqualTo(Veredito.INDISPONIVEL);
  }

  @Test
  void arquivoIlegivelDeixaPendente() {
    ModeracaoFalsa aws = ModeracaoFalsa.respondendo();

    assertThat(new ModeracaoDeImagemRekognition(aws).avaliar(dir.resolve("sumiu.jpg")))
        .isEqualTo(Veredito.INDISPONIVEL);
    assertThat(aws.pedido()).isNull();
  }

  @Test
  @Timeout(REDE_DE_SEGURANCA_EM_SEGUNDOS)
  void awsLentaDeixaPendentePeloTimeout() throws IOException {
    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(SEM_RESPOSTA);
        ModeracaoDeImagemRekognition moderacao =
            new ModeracaoDeImagemRekognition(
                ClientesAws.rekognition(configuracaoApertada(), Optional.of(lento.uri())))) {

      assertThat(moderacao.avaliar(jpeg())).isEqualTo(Veredito.INDISPONIVEL);
    }
  }

  private Path jpeg() throws IOException {
    Path arquivo = dir.resolve("m.jpg");
    if (!Files.exists(arquivo)) {
      Files.write(arquivo, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }
    return arquivo;
  }

  private static ModerationLabel rotulo(String nome, String pai, float confianca) {
    return ModerationLabel.builder().name(nome).parentName(pai).confidence(confianca).build();
  }

  private static ConfiguracaoAws configuracaoApertada() {
    return new ConfiguracaoAws(
        "AKIAMODERACAO",
        "segredo-de-teste",
        "arn:aws:iam::000000000000:role/duora-captura",
        Duration.ofSeconds(1),
        Duration.ofMillis(200),
        Duration.ofMillis(500));
  }

  /** Só o {@code DetectModerationLabels}: responde os rótulos combinados ou lança a falha. */
  private static final class ModeracaoFalsa implements RekognitionClient {

    private final List<ModerationLabel> rotulos;
    private final @Nullable RuntimeException falha;
    private @Nullable DetectModerationLabelsRequest pedido;

    private ModeracaoFalsa(List<ModerationLabel> rotulos, @Nullable RuntimeException falha) {
      this.rotulos = rotulos;
      this.falha = falha;
    }

    static ModeracaoFalsa respondendo(ModerationLabel... rotulos) {
      return new ModeracaoFalsa(List.of(rotulos), null);
    }

    static ModeracaoFalsa falhando(RuntimeException falha) {
      return new ModeracaoFalsa(List.of(), falha);
    }

    @Nullable DetectModerationLabelsRequest pedido() {
      return pedido;
    }

    @Override
    public DetectModerationLabelsResponse detectModerationLabels(
        Consumer<DetectModerationLabelsRequest.Builder> pedido) {
      DetectModerationLabelsRequest.Builder construtor = DetectModerationLabelsRequest.builder();
      pedido.accept(construtor);
      this.pedido = construtor.build();
      if (falha != null) {
        throw falha;
      }
      return DetectModerationLabelsResponse.builder().moderationLabels(rotulos).build();
    }

    @Override
    public String serviceName() {
      return "rekognition";
    }

    @Override
    public void close() {}
  }
}
