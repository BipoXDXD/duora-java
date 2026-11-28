package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesMatch;
import software.amazon.awssdk.services.rekognition.model.ImageTooLargeException;
import software.amazon.awssdk.services.rekognition.model.InvalidImageFormatException;
import software.amazon.awssdk.services.rekognition.model.InvalidParameterException;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DocumentTooLargeException;
import software.amazon.awssdk.services.textract.model.FeatureType;
import software.amazon.awssdk.services.textract.model.Query;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

/**
 * Conferência do documento na AWS ({@code us-east-1}), sem guardar nada: as imagens vão inline
 * ({@code Bytes}), nunca pelo S3. O Textract {@code AnalyzeDocument} com {@code QUERIES} pergunta a
 * data de nascimento na frente e, se ela não trouxer, no verso (o RG antigo a traz atrás). Vale a
 * resposta de maior confiança que tenha pelo menos {@link #CONFIANCA_MINIMA} e esteja em {@code
 * dd/MM/aaaa} (aceita {@code .} ou {@code -} no lugar da barra), com data que existe. Sem ela, o
 * documento fica não reconhecido, como uma foto ruim. Com ela, o Rekognition {@code CompareFaces}
 * compara a frente com a selfie pelo {@link #LIMIAR_DE_SEMELHANCA}; sem rosto na frente, ou imagem
 * que ele recusa, o rosto não confere. Data de menor não pula o rosto: a decisão olha o rosto antes
 * da idade.
 *
 * <p>A AWS não avalia a autenticidade do documento: {@code documentoAceito} quer dizer só que a
 * data foi lida (ADR 0007).
 *
 * <p>Documento que o Textract recusa ({@code BadDocument}, {@code UnsupportedDocument}, {@code
 * DocumentTooLarge}) é foto ruim e fica não reconhecido. Qualquer outro erro da AWS (acesso negado,
 * limitação, 5xx, timeout, rede ou pedido recusado) vira {@link FornecedorIndisponivel}, sem causa
 * e com motivo fixo: a mensagem do SDK traz conta e ARN. Os logs dizem só o passo, o status, o
 * código e o id da requisição; nunca o texto lido, a data ou as imagens.
 */
final class ConferenciaDeDocumentoAws implements ConferenciaDeDocumento, AutoCloseable {

  /**
   * Semelhança mínima (0 a 100) entre o rosto do documento e o da selfie. O padrão da AWS é 80, e
   * ela recomenda 99 quando um acerto errado tem consequência grave sem revisão humana. Aqui o
   * rosto que não confere vai para a dúvida, não para a recusa, e o retrato do documento costuma
   * ser antigo e impresso: 90 corta quem usa o documento de outra pessoa sem empurrar o dono do
   * documento para a dúvida a cada foto de RG antiga.
   */
  static final float LIMIAR_DE_SEMELHANCA = 90f;

  /**
   * Confiança mínima (0 a 100) da resposta do Textract. Uma data mal lida que passe daqui ainda vai
   * para a dúvida quando não bate com a declarada; abaixo dela, o documento fica não reconhecido.
   */
  static final float CONFIANCA_MINIMA = 80f;

  static final List<Query> PERGUNTAS =
      List.of(
          Query.builder().text("Qual é a data de nascimento?").alias("NASCIMENTO_PT").build(),
          Query.builder().text("Date of birth?").alias("NASCIMENTO_EN").build());

  private static final Logger LOG = LoggerFactory.getLogger(ConferenciaDeDocumentoAws.class);

  private static final String FORNECEDOR = "aws";
  private static final String FRENTE = "documento-frente";
  private static final String VERSO = "documento-verso";
  private static final String ROSTO = "rosto";

  /** Dia e mês com dois dígitos, ano com quatro, barra, ponto ou hífen, espaços em volta. */
  private static final Pattern DATA =
      Pattern.compile("^\\s*(\\d{2})\\s*[/.-]\\s*(\\d{2})\\s*[/.-]\\s*(\\d{4})\\s*$");

  private static final DateTimeFormatter DIA_MES_ANO =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  private static final ResultadoDoDocumento NAO_RECONHECIDO =
      new ResultadoDoDocumento(false, Optional.empty(), false);

  /** Credencial, assinatura ou permissão recusada; {@code ExpiredToken*} entra pelo prefixo. */
  private static final Set<String> CODIGOS_DE_ACESSO_NEGADO =
      Set.of(
          "AccessDeniedException",
          "AccessDenied",
          "UnrecognizedClientException",
          "InvalidClientTokenId",
          "InvalidSignatureException",
          "SignatureDoesNotMatch");

  private final TextractClient textract;
  private final RekognitionClient rekognition;
  private final MedicaoDeFornecedor medicao;

  ConferenciaDeDocumentoAws(
      TextractClient textract, RekognitionClient rekognition, MedicaoDeFornecedor medicao) {
    this.textract = Objects.requireNonNull(textract, "textract");
    this.rekognition = Objects.requireNonNull(rekognition, "rekognition");
    this.medicao = Objects.requireNonNull(medicao, "medicao");
  }

  @Override
  public ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie) {
    Optional<LocalDate> data = lerData(frente, FRENTE).or(() -> lerData(verso, VERSO));
    if (data.isEmpty()) {
      LOG.info("AWS não leu a data de nascimento no documento: não reconhecido");
      return NAO_RECONHECIDO;
    }
    return new ResultadoDoDocumento(true, data, rostoConfere(frente, selfie));
  }

  @Override
  public void close() {
    try {
      textract.close();
    } finally {
      rekognition.close();
    }
  }

  private Optional<LocalDate> lerData(Imagem documento, String passo) {
    List<Block> blocos;
    try {
      blocos =
          chamar(
              passo,
              () ->
                  textract
                      .analyzeDocument(r ->
                          r.document(d -> d.bytes(bytes(documento)))
                              .featureTypes(FeatureType.QUERIES)
                              .queriesConfig(q -> q.queries(PERGUNTAS)))
                      .blocks(),
              e ->
                  e instanceof BadDocumentException
                      || e instanceof UnsupportedDocumentException
                      || e instanceof DocumentTooLargeException);
    } catch (BadDocumentException | UnsupportedDocumentException | DocumentTooLargeException e) {
      LOG.info("AWS recusou o documento em {} ({})", passo, codigo(e.awsErrorDetails()));
      return Optional.empty();
    }
    return blocos.stream()
        .filter(b -> b.blockType() == BlockType.QUERY_RESULT)
        .filter(b -> b.confidence() != null && b.confidence() >= CONFIANCA_MINIMA)
        .sorted(Comparator.comparing(Block::confidence).reversed())
        .map(b -> data(b.text()))
        .flatMap(Optional::stream)
        .findFirst();
  }

  /** {@code dd/MM/aaaa} com data que existe; qualquer outra coisa fica vazia. */
  static Optional<LocalDate> data(@Nullable String texto) {
    if (texto == null) {
      return Optional.empty();
    }
    Matcher partes = DATA.matcher(texto);
    if (!partes.matches()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          LocalDate.parse(
              partes.group(1) + "/" + partes.group(2) + "/" + partes.group(3), DIA_MES_ANO));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private boolean rostoConfere(Imagem frente, Imagem selfie) {
    List<CompareFacesMatch> semelhantes;
    try {
      semelhantes =
          chamar(
              ROSTO,
              () ->
                  rekognition
                      .compareFaces(r ->
                          r.sourceImage(i -> i.bytes(bytes(frente)))
                              .targetImage(i -> i.bytes(bytes(selfie)))
                              .similarityThreshold(LIMIAR_DE_SEMELHANCA))
                      .faceMatches(),
              e ->
                  e instanceof InvalidParameterException
                      || e instanceof InvalidImageFormatException
                      || e instanceof ImageTooLargeException);
    } catch (InvalidParameterException | InvalidImageFormatException | ImageTooLargeException e) {
      LOG.info(
          "AWS não achou rosto ou recusou a imagem em {} ({}): rosto não confere",
          ROSTO,
          codigo(e.awsErrorDetails()));
      return false;
    }
    return semelhantes.stream()
        .anyMatch(m -> m.similarity() != null && m.similarity() >= LIMIAR_DE_SEMELHANCA);
  }

  private static SdkBytes bytes(Imagem imagem) {
    return SdkBytes.fromByteArrayUnsafe(imagem.bytes());
  }

  /**
   * Mede a chamada e traduz as falhas; as falhas do serviço que {@code esperada} aceita voltam
   * intactas para quem chamou tratar.
   */
  private <T> T chamar(String passo, Supplier<T> chamada, Predicate<AwsServiceException> esperada) {
    try {
      return medicao.medir(FORNECEDOR, passo, chamada);
    } catch (AwsServiceException e) {
      if (esperada.test(e)) {
        throw e;
      }
      throw traduzir(passo, e);
    } catch (SdkClientException e) {
      LOG.warn("AWS fora de alcance em {} ({})", passo, e.getClass().getSimpleName());
      throw indisponivel(passo);
    }
  }

  /** Loga o status e o código (nunca a mensagem do SDK) e devolve a falha a lançar. */
  private static FornecedorIndisponivel traduzir(String passo, AwsServiceException e) {
    int status = e.statusCode();
    String codigo = codigo(e.awsErrorDetails());
    if (status == 401
        || status == 403
        || CODIGOS_DE_ACESSO_NEGADO.contains(codigo)
        || codigo.startsWith("ExpiredToken")) {
      LOG.error(
          "AWS negou {} ({} {}, requisição {}): confira o IAM do usuário da verificação",
          passo,
          status,
          codigo,
          e.requestId());
    } else if (status >= 500 || status == 429 || e.isThrottlingException()) {
      LOG.warn(
          "AWS indisponível em {} ({} {}, requisição {})", passo, status, codigo, e.requestId());
    } else {
      LOG.error(
          "AWS recusou o pedido em {} ({} {}, requisição {}): pedido malformado",
          passo,
          status,
          codigo,
          e.requestId());
    }
    return indisponivel(passo);
  }

  private static String codigo(@Nullable AwsErrorDetails detalhes) {
    return detalhes == null || detalhes.errorCode() == null ? "sem código" : detalhes.errorCode();
  }

  private static FornecedorIndisponivel indisponivel(String passo) {
    return new FornecedorIndisponivel("AWS indisponível em " + passo, null);
  }
}
