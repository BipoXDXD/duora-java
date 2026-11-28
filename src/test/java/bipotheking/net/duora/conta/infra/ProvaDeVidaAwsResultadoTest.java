package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.DetectFacesRequest;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;
import software.amazon.awssdk.services.rekognition.model.ImageTooLargeException;
import software.amazon.awssdk.services.rekognition.model.InternalServerErrorException;
import software.amazon.awssdk.services.rekognition.model.InvalidImageFormatException;
import software.amazon.awssdk.services.rekognition.model.LivenessSessionStatus;
import software.amazon.awssdk.services.rekognition.model.SessionNotFoundException;
import software.amazon.awssdk.services.rekognition.model.ThrottlingException;
import software.amazon.awssdk.services.sts.model.Credentials;

/** Resultado da prova de vida e faixa de idade pelo {@code DetectFaces}, contra clientes falsos. */
@ExtendWith(OutputCaptureExtension.class)
class ProvaDeVidaAwsResultadoTest {

  private static final String SESSAO = "sessao-canario-7f3a9c";
  private static final byte[] REFERENCIA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @Test
  void umRostoViraFaixa() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f)).detectando(rosto(23, 31));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.CONCLUIDA);
    assertThat(resultado.confianca()).isEqualTo(97.5);
    assertThat(resultado.faixa()).contains(new FaixaEstimada(23, 31));
    assertThat(resultado.referencia()).isPresent();
    assertThat(rekognition.pedidoDoResultado().sessionId()).isEqualTo(SESSAO);
    DetectFacesRequest pedido = rekognition.pedidosDeDetectFaces().getFirst();
    assertThat(pedido.attributes()).containsExactly(Attribute.AGE_RANGE);
    assertThat(pedido.image().bytes().asByteArray()).isEqualTo(REFERENCIA);
  }

  @Test
  void doisRostosViramVazio() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f)).detectando(rosto(23, 31), rosto(8, 12));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.CONCLUIDA);
    assertThat(resultado.referencia()).isPresent();
  }

  @Test
  void nenhumRostoViraVazio() {
    RekognitionFalso rekognition = RekognitionFalso.comResultado(concluida(97.5f)).detectando();

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isPresent();
  }

  @ParameterizedTest
  @CsvSource({
    "CREATED, CRIADA",
    "IN_PROGRESS, EM_ANDAMENTO",
    "FAILED, FALHOU",
    "EXPIRED, EXPIRADA"
  })
  void sessaoNaoConcluidaNaoChamaDetectFaces(
      LivenessSessionStatus estado, SituacaoDaProva esperada) {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(
                GetFaceLivenessSessionResultsResponse.builder()
                    .sessionId(SESSAO)
                    .status(estado)
                    .confidence(97.5f)
                    .referenceImage(i -> i.bytes(SdkBytes.fromByteArray(REFERENCIA)))
                    .build())
            .detectando(rosto(23, 31));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(esperada);
    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isEmpty();
    assertThat(rekognition.pedidosDeDetectFaces()).isEmpty();
  }

  @Test
  void estadoDesconhecidoViraFalhouComAviso(CapturedOutput saida) {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(
            GetFaceLivenessSessionResultsResponse.builder()
                .sessionId(SESSAO)
                .status("PAUSED")
                .confidence(97.5f)
                .build());

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.FALHOU);
    assertThat(rekognition.pedidosDeDetectFaces()).isEmpty();
    assertThat(saida).contains("WARN").contains("resultado");
  }

  @Test
  void confiancaNulaViraZero() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(
            GetFaceLivenessSessionResultsResponse.builder()
                .sessionId(SESSAO)
                .status(LivenessSessionStatus.FAILED)
                .build());

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.confianca()).isZero();
  }

  @Test
  void concluidaSemReferenciaViraVazio() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(
                GetFaceLivenessSessionResultsResponse.builder()
                    .sessionId(SESSAO)
                    .status(LivenessSessionStatus.SUCCEEDED)
                    .confidence(97.5f)
                    .build())
            .detectando(rosto(23, 31));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.CONCLUIDA);
    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isEmpty();
    assertThat(rekognition.pedidosDeDetectFaces()).isEmpty();
  }

  /** Bytes que o domínio não aceita como JPEG ou PNG não chegam ao {@code DetectFaces}. */
  @Test
  void referenciaQueNaoEImagemViraVazio() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(
                GetFaceLivenessSessionResultsResponse.builder()
                    .sessionId(SESSAO)
                    .status(LivenessSessionStatus.SUCCEEDED)
                    .confidence(97.5f)
                    .referenceImage(i -> i.bytes(SdkBytes.fromByteArray(new byte[] {1, 2, 3})))
                    .build())
            .detectando(rosto(23, 31));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isEmpty();
    assertThat(rekognition.pedidosDeDetectFaces()).isEmpty();
  }

  /** {@code SessionNotFoundException} chega como HTTP 400: não é pedido malformado nem acesso. */
  @Test
  void sessaoInexistenteViraExpirada(CapturedOutput saida) {
    RekognitionFalso rekognition =
        RekognitionFalso.falhandoNoResultado(
            erroDaAws(
                SessionNotFoundException.builder(),
                400,
                "SessionNotFoundException",
                "Session " + SESSAO + " not found"));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.EXPIRADA);
    assertThat(resultado.confianca()).isZero();
    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isEmpty();
    assertThat(saida).contains("INFO").doesNotContain("ERROR");
  }

  @ParameterizedTest
  @ValueSource(strings = {"InvalidImageFormatException", "ImageTooLargeException"})
  void imagemRecusadaPeloDetectFacesViraFaixaVazia(String codigo, CapturedOutput saida) {
    AwsServiceException.Builder builder =
        codigo.equals("ImageTooLargeException")
            ? ImageTooLargeException.builder()
            : InvalidImageFormatException.builder();
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f))
            .falhandoNoDetectFaces(erroDaAws(builder, 400, codigo, "imagem recusada"));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.situacao()).isEqualTo(SituacaoDaProva.CONCLUIDA);
    assertThat(resultado.faixa()).isEmpty();
    assertThat(resultado.referencia()).isPresent();
    assertThat(saida).contains("WARN").doesNotContain("ERROR");
  }

  @Test
  void referenciaVemComOsBytes() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f)).detectando(rosto(23, 31));

    ResultadoDaProva resultado = adapter(rekognition).resultado(SESSAO);

    assertThat(resultado.referencia().map(Imagem::bytes)).hasValue(REFERENCIA);
  }

  @Test
  void falhaNoGetViraIndisponivel() {
    RekognitionFalso rekognition =
        RekognitionFalso.falhandoNoResultado(
            erroDaAws(
                InternalServerErrorException.builder(),
                500,
                "InternalServerError",
                "erro interno"));

    Throwable falha = catchThrowable(() -> adapter(rekognition).resultado(SESSAO));

    assertThat(falha).isExactlyInstanceOf(FornecedorIndisponivel.class).hasNoCause();
  }

  @Test
  void throttlingNoDetectFacesViraIndisponivel() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f))
            .falhandoNoDetectFaces(
                erroDaAws(
                    ThrottlingException.builder(), 400, "ThrottlingException", "Rate exceeded"));

    Throwable falha = catchThrowable(() -> adapter(rekognition).resultado(SESSAO));

    assertThat(falha).isExactlyInstanceOf(FornecedorIndisponivel.class).hasNoCause();
  }

  @Test
  void metricaMedeResultadoEEstimativa() {
    RekognitionFalso rekognition =
        RekognitionFalso.comResultado(concluida(97.5f)).detectando(rosto(23, 31));

    adapter(rekognition).resultado(SESSAO);

    assertThat(contagem("resultado", "sucesso")).isEqualTo(1);
    assertThat(contagem("estimativa", "sucesso")).isEqualTo(1);
  }

  /** Os caminhos que logam (WARN e INFO) não levam o id da sessão nem os bytes da referência. */
  @Test
  void logNaoTrazBytesNemSessao(CapturedOutput saida) {
    adapter(RekognitionFalso.comResultado(concluida(97.5f)).detectando(rosto(23, 31), rosto(8, 12)))
        .resultado(SESSAO);
    adapter(
            RekognitionFalso.comResultado(concluida(97.5f))
                .falhandoNoDetectFaces(
                    erroDaAws(
                        InvalidImageFormatException.builder(),
                        400,
                        "InvalidImageFormatException",
                        "imagem " + SESSAO)))
        .resultado(SESSAO);
    adapter(
            RekognitionFalso.falhandoNoResultado(
                erroDaAws(
                    SessionNotFoundException.builder(),
                    400,
                    "SessionNotFoundException",
                    "Session " + SESSAO + " not found")))
        .resultado(SESSAO);

    assertThat(saida)
        .contains("WARN")
        .contains("INFO")
        .doesNotContain(SESSAO)
        .doesNotContain(Base64.getEncoder().encodeToString(REFERENCIA))
        .doesNotContain(Arrays.toString(REFERENCIA));
  }

  private ProvaDeVidaAws adapter(RekognitionFalso rekognition) {
    StsFalso sts =
        StsFalso.respondendo(
            Credentials.builder()
                .accessKeyId("ASIAFALSA")
                .secretAccessKey("segredo-falso")
                .sessionToken("token-falso")
                .expiration(Instant.parse("2026-11-27T12:15:00Z"))
                .build());
    return new ProvaDeVidaAws(
        rekognition,
        sts,
        "arn:aws:iam::000000000000:role/duora-captura",
        new MedicaoDeFornecedor(metricas));
  }

  private static GetFaceLivenessSessionResultsResponse concluida(float confianca) {
    return GetFaceLivenessSessionResultsResponse.builder()
        .sessionId(SESSAO)
        .status(LivenessSessionStatus.SUCCEEDED)
        .confidence(confianca)
        .referenceImage(i -> i.bytes(SdkBytes.fromByteArray(REFERENCIA)))
        .auditImages(List.of())
        .build();
  }

  private static FaceDetail rosto(int baixa, int alta) {
    return FaceDetail.builder().ageRange(a -> a.low(baixa).high(alta)).build();
  }

  private long contagem(String operacao, String desfecho) {
    @Nullable Timer timer =
        metricas
            .find("duora.verificacao.fornecedor")
            .tags("fornecedor", "aws", "operacao", operacao, "desfecho", desfecho)
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private static AwsServiceException erroDaAws(
      AwsServiceException.Builder builder, int status, String codigo, String mensagem) {
    return builder
        .statusCode(status)
        .message(mensagem)
        .awsErrorDetails(AwsErrorDetails.builder().errorCode(codigo).errorMessage(mensagem).build())
        .build();
  }
}
