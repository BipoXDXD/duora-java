package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.ImageTooLargeException;
import software.amazon.awssdk.services.rekognition.model.InvalidImageFormatException;
import software.amazon.awssdk.services.rekognition.model.InvalidParameterException;
import software.amazon.awssdk.services.rekognition.model.RekognitionException;
import software.amazon.awssdk.services.textract.model.AccessDeniedException;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.DocumentTooLargeException;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.InternalServerErrorException;
import software.amazon.awssdk.services.textract.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.textract.model.Query;
import software.amazon.awssdk.services.textract.model.ThrottlingException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

/**
 * A conferência na AWS contra o {@link TextractFalso} e o {@link RekognitionFalso}: a data lida
 * pelas queries do Textract, o rosto pelo {@code CompareFaces}, o que vira não reconhecido, o que
 * vira {@link FornecedorIndisponivel}, as métricas e o que nunca vai para o log. Nada vai à AWS.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConferenciaDeDocumentoAwsTest {

  private static final String FRENTE = "frente-de-teste";
  private static final String VERSO = "verso-de-teste";
  private static final String SELFIE = "selfie-de-teste";
  private static final String NASCIMENTO = "10/05/1990";
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
  private static final LocalDate DATA = LocalDate.of(1990, 5, 10);

  private final TextractFalso textract = new TextractFalso();
  private final RekognitionFalso rekognition = RekognitionFalso.paraComparar();
  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @Test
  void aprovaComDataLegivelERostoQueConfere() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.comparando(97f);

    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado.documentoAceito()).isTrue();
    assertThat(resultado.dataDeNascimento()).contains(DATA);
    assertThat(resultado.rostoConfere()).isTrue();
  }

  @Test
  void perguntaANascimentoComQueriesEBytesInline() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);

    conferir();

    AnalyzeDocumentRequest pedido = textract.pedidos().getFirst();
    assertThat(pedido.featureTypes()).containsExactly(FeatureType.QUERIES);
    assertThat(pedido.queriesConfig().queries())
        .extracting(Query::text)
        .containsExactly("Qual é a data de nascimento?", "Date of birth?");
    assertThat(TextractFalso.conteudo(pedido.document().bytes())).isEqualTo(FRENTE);
    assertThat(pedido.document().s3Object()).isNull();
  }

  @Test
  void comparaAFrenteComASelfiePeloLimiar() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);

    conferir();

    CompareFacesRequest pedido = rekognition.pedidosDeCompareFaces().getFirst();
    assertThat(TextractFalso.conteudo(pedido.sourceImage().bytes())).isEqualTo(FRENTE);
    assertThat(TextractFalso.conteudo(pedido.targetImage().bytes())).isEqualTo(SELFIE);
    assertThat(pedido.sourceImage().s3Object()).isNull();
    assertThat(pedido.targetImage().s3Object()).isNull();
    assertThat(pedido.similarityThreshold())
        .isEqualTo(ConferenciaDeDocumentoAws.LIMIAR_DE_SEMELHANCA)
        .isEqualTo(90f);
  }

  @Test
  void recusaORostoQuandoNaoConfere() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);

    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado.documentoAceito()).isTrue();
    assertThat(resultado.dataDeNascimento()).contains(DATA);
    assertThat(resultado.rostoConfere()).isFalse();
  }

  @Test
  void semelhancaAbaixoDoLimiarNaoConfere() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.comparando(89.9f);

    assertThat(conferir().rostoConfere()).isFalse();
  }

  /** Sem rosto na frente, o {@code CompareFaces} responde {@code InvalidParameterException}. */
  @ParameterizedTest
  @MethodSource("imagensQueORekognitionRecusa")
  void semRostoNoDocumentoNaoConfere(RekognitionException recusa, CapturedOutput saida) {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.falhandoNoCompareFaces(recusa);

    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado.documentoAceito()).isTrue();
    assertThat(resultado.rostoConfere()).isFalse();
    assertThat(saida).contains("rosto não confere");
  }

  /** Pedido nosso malformado também chega como InvalidParameter: precisa aparecer no alerta. */
  @Test
  void semRostoLogaEmWarn(CapturedOutput saida) {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.falhandoNoCompareFaces(
        erro(InvalidParameterException.builder(), 400, "InvalidParameterException"));

    conferir();

    assertThat(saida.getAll().lines().filter(l -> l.contains("rosto não confere")))
        .singleElement()
        .asString()
        .contains("WARN")
        .contains("InvalidParameterException")
        .doesNotContain(NASCIMENTO, SELFIE, FRENTE);
  }

  static Stream<RekognitionException> imagensQueORekognitionRecusa() {
    return Stream.of(
        (RekognitionException)
            erro(InvalidParameterException.builder(), 400, "InvalidParameterException"),
        (RekognitionException)
            erro(InvalidImageFormatException.builder(), 400, "InvalidImageFormatException"),
        (RekognitionException)
            erro(ImageTooLargeException.builder(), 400, "ImageTooLargeException"));
  }

  @ParameterizedTest
  @MethodSource("datasLegiveis")
  void leADataNoFormatoBrasileiro(String texto, LocalDate esperada) {
    textract.respondendo(FRENTE, texto, 99f);

    assertThat(conferir().dataDeNascimento()).contains(esperada);
  }

  static Stream<Arguments> datasLegiveis() {
    return Stream.of(
        Arguments.of("10/05/1990", DATA),
        Arguments.of(" 10/05/1990 ", DATA),
        Arguments.of("10.05.1990", DATA),
        Arguments.of("10-05-1990", DATA),
        Arguments.of("10 / 05 / 1990", DATA),
        Arguments.of("29/02/2000", LocalDate.of(2000, 2, 29)),
        Arguments.of("01/12/1975", LocalDate.of(1975, 12, 1)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "ilegível",
        "1990-05-10",
        "10/05/90",
        "31/02/1990",
        "29/02/1999",
        "10/13/1990",
        "1/5/1990",
        "10/05/1990 SP"
      })
  void dataForaDoFormatoNaoReconhece(String texto) {
    textract.respondendo(FRENTE, texto, 99f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
  }

  @Test
  void semDataNaoReconheceNemComparaORosto(CapturedOutput saida) {
    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado).isEqualTo(naoReconhecido());
    assertThat(rekognition.pedidosDeCompareFaces()).isEmpty();
    assertThat(saida).contains("não reconhecido");
  }

  @Test
  void confiancaBaixaNaoReconhece() {
    textract.respondendo(FRENTE, NASCIMENTO, ConferenciaDeDocumentoAws.CONFIANCA_MINIMA - 0.1f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
    assertThat(rekognition.pedidosDeCompareFaces()).isEmpty();
  }

  @Test
  void confiancaNoLimiteReconhece() {
    textract.respondendo(FRENTE, NASCIMENTO, ConferenciaDeDocumentoAws.CONFIANCA_MINIMA);

    assertThat(conferir().dataDeNascimento()).contains(DATA);
  }

  /** PT e EN concordando, a data vale. */
  @Test
  void respostasQueConcordamReconhecem() {
    textract.respondendo(FRENTE, NASCIMENTO, 85f).respondendo(FRENTE, "10.05.1990", 98f);

    assertThat(conferir().dataDeNascimento()).contains(DATA);
  }

  /** PT e EN com datas diferentes: uma delas é outro campo (validade, emissão). */
  @Test
  void respostasQueDiscordamNaoReconhecem() {
    textract.respondendo(FRENTE, "11/06/1991", 85f).respondendo(FRENTE, NASCIMENTO, 98f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
  }

  /** Data futura é a validade, não o nascimento. */
  @Test
  void dataFuturaNaoReconhece() {
    textract.respondendo(FRENTE, texto(LocalDate.now(FUSO).plusDays(1)), 99f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
  }

  @Test
  void dataAnteriorA1900NaoReconhece() {
    textract.respondendo(FRENTE, "31/12/1899", 99f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
  }

  @Test
  void dataEm1900Reconhece() {
    textract.respondendo(FRENTE, "01/01/1900", 99f);

    assertThat(conferir().dataDeNascimento()).contains(LocalDate.of(1900, 1, 1));
  }

  /** Uma das respostas implausível já tira a confiança da outra. */
  @Test
  void respostaImplausivelAoLadoDeUmaPlausivelNaoReconhece() {
    textract
        .respondendo(FRENTE, NASCIMENTO, 99f)
        .respondendo(FRENTE, texto(LocalDate.now(FUSO).plusYears(5)), 90f);

    assertThat(conferir()).isEqualTo(naoReconhecido());
  }

  @Test
  void respostaIlegivelNaoEscondeALegivel() {
    textract.respondendo(FRENTE, "ilegível", 99f).respondendo(FRENTE, NASCIMENTO, 90f);

    assertThat(conferir().dataDeNascimento()).contains(DATA);
  }

  /** O RG antigo traz a data no verso; a foto continua na frente. */
  @Test
  void leOVersoQuandoAFrenteNaoTemData() {
    textract.respondendo(VERSO, NASCIMENTO, 99f);
    rekognition.comparando(95f);

    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado.dataDeNascimento()).contains(DATA);
    assertThat(resultado.rostoConfere()).isTrue();
    assertThat(textract.pedidos())
        .extracting(p -> TextractFalso.conteudo(p.document().bytes()))
        .containsExactly(FRENTE, VERSO);
  }

  @Test
  void naoLeOVersoQuandoAFrenteTemData() {
    textract.respondendo(FRENTE, NASCIMENTO, 99f).respondendo(VERSO, "01/01/2000", 99f);

    conferir();

    assertThat(textract.pedidos()).hasSize(1);
  }

  /** Menor também passa pelo rosto: a decisão olha o rosto antes da idade. */
  @Test
  void menorDeIdadeDevolveADataEComparaORosto() {
    LocalDate menor = LocalDate.now(FUSO).minusYears(15);
    textract.respondendo(FRENTE, texto(menor), 99f);
    rekognition.comparando(99f);

    ResultadoDoDocumento resultado = conferir();

    assertThat(resultado.documentoAceito()).isTrue();
    assertThat(resultado.dataDeNascimento()).contains(menor);
    assertThat(resultado.rostoConfere()).isTrue();
  }

  /** Foto ruim não é falha do fornecedor: o documento fica não reconhecido. */
  @ParameterizedTest
  @MethodSource("documentosQueOTextractRecusa")
  void documentoQueOTextractRecusaNaoReconhece(AwsServiceException recusa, CapturedOutput saida) {
    textract.falhando(FRENTE, recusa).falhando(VERSO, recusa);

    assertThat(conferir()).isEqualTo(naoReconhecido());
    assertThat(saida).contains("não reconhecido");
  }

  static Stream<AwsServiceException> documentosQueOTextractRecusa() {
    return Stream.of(
        erro(BadDocumentException.builder(), 400, "BadDocumentException"),
        erro(UnsupportedDocumentException.builder(), 400, "UnsupportedDocumentException"),
        erro(DocumentTooLargeException.builder(), 400, "DocumentTooLargeException"));
  }

  @ParameterizedTest
  @MethodSource("falhasDaAws")
  void falhaNoTextractViraIndisponivel(RuntimeException falha) {
    textract.falhando(FRENTE, falha);

    FornecedorIndisponivel lancada =
        catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(lancada).hasMessage("AWS indisponível em documento-frente").hasNoCause();
    assertThat(rekognition.pedidosDeCompareFaces()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("falhasDaAws")
  void falhaNoVersoViraIndisponivel(RuntimeException falha) {
    textract.falhando(VERSO, falha);

    FornecedorIndisponivel lancada =
        catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(lancada).hasMessage("AWS indisponível em documento-verso").hasNoCause();
  }

  @ParameterizedTest
  @MethodSource("falhasDoRekognition")
  void falhaNoRekognitionViraIndisponivel(RuntimeException falha) {
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.falhandoNoCompareFaces(falha);

    FornecedorIndisponivel lancada =
        catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(lancada).hasMessage("AWS indisponível em rosto").hasNoCause();
  }

  static Stream<RuntimeException> falhasDaAws() {
    return Stream.of(
        erro(AccessDeniedException.builder(), 400, "AccessDeniedException"),
        erro(AwsServiceException.builder(), 400, "UnrecognizedClientException"),
        erro(AwsServiceException.builder(), 403, "InvalidSignatureException"),
        erro(ThrottlingException.builder(), 400, "ThrottlingException"),
        erro(
            ProvisionedThroughputExceededException.builder(),
            400,
            "ProvisionedThroughputExceededException"),
        erro(InternalServerErrorException.builder(), 500, "InternalServerError"),
        erro(AwsServiceException.builder(), 400, "InvalidParameterException"),
        ApiCallTimeoutException.create(4_000),
        SdkClientException.create("sem rede"));
  }

  static Stream<RuntimeException> falhasDoRekognition() {
    return Stream.of(
        erro(
            software.amazon.awssdk.services.rekognition.model.AccessDeniedException.builder(),
            400,
            "AccessDeniedException"),
        erro(
            software.amazon.awssdk.services.rekognition.model.ThrottlingException.builder(),
            400,
            "ThrottlingException"),
        erro(RekognitionException.builder(), 500, "InternalServerError"),
        ApiCallTimeoutException.create(4_000),
        SdkClientException.create("sem rede"));
  }

  @Test
  void acessoNegadoLogaPassoECodigo(CapturedOutput saida) {
    textract.falhando(FRENTE, erro(AccessDeniedException.builder(), 400, "AccessDeniedException"));

    catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(saida).contains("documento-frente").contains("AccessDeniedException");
  }

  @Test
  void logNaoTrazTextoLidoDataNemImagem(CapturedOutput saida) {
    textract
        .respondendo(FRENTE, "Fulana de Tal", 40f)
        .respondendo(VERSO, NASCIMENTO, 10f)
        .falhando("outro", SdkClientException.create("nada"));
    conferir();
    textract.respondendo(FRENTE, NASCIMENTO, 99f);
    rekognition.falhandoNoCompareFaces(
        erro(InvalidParameterException.builder(), 400, "InvalidParameterException"));
    conferir();

    assertThat(saida.getAll())
        .doesNotContain("Fulana")
        .doesNotContain(NASCIMENTO)
        .doesNotContain("1990")
        .doesNotContain(FRENTE)
        .doesNotContain(VERSO)
        .doesNotContain(SELFIE);
  }

  @Test
  void falhaNaoCarregaMensagemDaAws() {
    textract.falhando(
        FRENTE,
        erro(AccessDeniedException.builder(), 400, "AccessDeniedException", "123456789012"));

    FornecedorIndisponivel lancada =
        catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(lancada.getMessage()).doesNotContain("123456789012");
  }

  @Test
  void medeCadaChamada() {
    textract.respondendo(VERSO, NASCIMENTO, 99f);
    rekognition.comparando(99f);

    conferir();

    assertThat(contagem("documento-frente", "sucesso")).isEqualTo(1);
    assertThat(contagem("documento-verso", "sucesso")).isEqualTo(1);
    assertThat(contagem("rosto", "sucesso")).isEqualTo(1);
  }

  @Test
  void medeAFalha() {
    textract.falhando(FRENTE, SdkClientException.create("sem rede"));

    catchThrowableOfType(FornecedorIndisponivel.class, this::conferir);

    assertThat(contagem("documento-frente", "falha")).isEqualTo(1);
  }

  @Test
  void fecharFechaOsDoisClientes() {
    conferencia().close();

    assertThat(textract.fechado()).isTrue();
    assertThat(rekognition.fechado()).isTrue();
  }

  private ResultadoDoDocumento conferir() {
    return conferencia().conferir(imagem(FRENTE), imagem(VERSO), imagem(SELFIE));
  }

  private ConferenciaDeDocumentoAws conferencia() {
    return new ConferenciaDeDocumentoAws(textract, rekognition, new MedicaoDeFornecedor(metricas));
  }

  private long contagem(String operacao, String desfecho) {
    @Nullable Timer timer =
        metricas
            .find("duora.verificacao.fornecedor")
            .tags("fornecedor", "aws", "operacao", operacao, "desfecho", desfecho)
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private static String texto(LocalDate data) {
    return "%02d/%02d/%d".formatted(data.getDayOfMonth(), data.getMonthValue(), data.getYear());
  }

  private static ResultadoDoDocumento naoReconhecido() {
    return new ResultadoDoDocumento(false, Optional.empty(), false);
  }

  /** Os falsos respondem pelo texto que vem depois do mágico JPEG. */
  private static Imagem imagem(String conteudo) {
    return Imagem.ler(TextractFalso.jpeg(conteudo)).orElseThrow();
  }

  private static AwsServiceException erro(
      AwsServiceException.Builder builder, int status, String codigo) {
    return erro(builder, status, codigo, "mensagem da AWS");
  }

  private static AwsServiceException erro(
      AwsServiceException.Builder builder, int status, String codigo, String mensagem) {
    return builder
        .statusCode(status)
        .message(mensagem)
        .awsErrorDetails(AwsErrorDetails.builder().errorCode(codigo).errorMessage(mensagem).build())
        .build();
  }
}
