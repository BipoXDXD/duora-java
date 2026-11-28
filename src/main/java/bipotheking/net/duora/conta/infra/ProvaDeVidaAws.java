package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.AgeRange;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.AuditImage;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;
import software.amazon.awssdk.services.rekognition.model.ImageTooLargeException;
import software.amazon.awssdk.services.rekognition.model.InvalidImageFormatException;
import software.amazon.awssdk.services.rekognition.model.SessionNotFoundException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * Prova de vida pelo Amazon Rekognition Face Liveness. Abre a sessão sem imagens de auditoria e sem
 * S3 ({@code AuditImagesLimit=0}, sem {@code OutputConfig} nem {@code KmsKeyId}) e emite, pelo
 * {@code AssumeRole} do papel de captura, a credencial de 15 minutos com que o navegador só pode
 * iniciar o streaming da sessão.
 *
 * <p>Falha de rede, timeout, 5xx, acesso negado (401, 403 ou código de credencial recusada, já que
 * o Rekognition o devolve como 400) e limitação viram {@link FornecedorIndisponivel}. Outro 4xx é
 * pedido malformado nosso e vira {@link IllegalStateException} (bug, 500). Em nenhum caso a exceção
 * do SDK vai como causa nem sua mensagem é copiada: ela traz ARN e conta. O log diz só a operação,
 * o status, o código do erro e o id da requisição.
 *
 * <p>O resultado só estima a idade numa sessão concluída com imagem de referência: o {@code
 * DetectFaces} com {@code AGE_RANGE} roda sobre ela, que só vive em memória. Sessão inexistente ou
 * vencida ({@code SessionNotFoundException}) vira {@link SituacaoDaProva#EXPIRADA}; imagem que o
 * {@code DetectFaces} recusa ({@code InvalidImageFormatException}, {@code ImageTooLargeException})
 * deixa a faixa vazia. As três chegam como HTTP 400 e são tratadas antes da tradução genérica.
 * Esses logs não levam o id da sessão nem os bytes da imagem.
 */
final class ProvaDeVidaAws implements ProvaDeVida, CredencialDoNavegador, AutoCloseable {

  /** Só o que o navegador precisa: iniciar o streaming da prova de vida, nada mais. */
  static final String POLITICA_DE_SESSAO =
      "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
          + "\"Action\":\"rekognition:StartFaceLivenessSession\",\"Resource\":\"*\"}]}";

  static final int DURACAO_DA_CREDENCIAL_EM_SEGUNDOS = 900;

  private static final Logger LOG = LoggerFactory.getLogger(ProvaDeVidaAws.class);

  /** Credencial, assinatura ou permissão recusada; {@code ExpiredToken*} entra pelo prefixo. */
  private static final Set<String> CODIGOS_DE_ACESSO_NEGADO =
      Set.of(
          "AccessDeniedException",
          "AccessDenied",
          "UnrecognizedClientException",
          "InvalidClientTokenId",
          "InvalidSignatureException",
          "SignatureDoesNotMatch");

  private static final String FORNECEDOR = "aws";
  private static final String CRIAR_SESSAO = "criar-sessao";
  private static final String CREDENCIAL = "credencial";
  private static final String RESULTADO = "resultado";
  private static final String ESTIMATIVA = "estimativa";
  // Package-private: o VerificacaoAwsSmokeIT confere o RoleSessionName inteiro.
  static final String PREFIXO_DA_SESSAO_DO_PAPEL = "duora-";

  private final RekognitionClient rekognition;
  private final StsClient sts;
  private final String papelCapturaArn;
  private final MedicaoDeFornecedor medicao;

  ProvaDeVidaAws(
      RekognitionClient rekognition,
      StsClient sts,
      String papelCapturaArn,
      MedicaoDeFornecedor medicao) {
    this.rekognition = Objects.requireNonNull(rekognition, "rekognition");
    this.sts = Objects.requireNonNull(sts, "sts");
    this.papelCapturaArn = Objects.requireNonNull(papelCapturaArn, "papelCapturaArn");
    this.medicao = Objects.requireNonNull(medicao, "medicao");
  }

  @Override
  public ModoDeCaptura modo() {
    return ModoDeCaptura.AWS;
  }

  @Override
  public SessaoDeProva criarSessao(UUID tentativa) {
    String sessaoId =
        chamar(
            CRIAR_SESSAO,
            () ->
                rekognition
                    .createFaceLivenessSession(r ->
                        r.clientRequestToken(tentativa.toString())
                            .settings(s -> s.auditImagesLimit(0)))
                    .sessionId());
    return new SessaoDeProva(sessaoId, ClientesAws.REGIAO.id());
  }

  @Override
  public ResultadoDaProva resultado(String sessaoId) {
    GetFaceLivenessSessionResultsResponse resposta;
    try {
      resposta =
          chamar(
              RESULTADO,
              () -> rekognition.getFaceLivenessSessionResults(r -> r.sessionId(sessaoId)),
              SessionNotFoundException.class::isInstance);
    } catch (SessionNotFoundException e) {
      LOG.info("AWS não achou a sessão em {}: inexistente ou vencida", RESULTADO);
      return new ResultadoDaProva(
          SituacaoDaProva.EXPIRADA, 0.0, Optional.empty(), Optional.empty());
    }
    SituacaoDaProva situacao = situacao(resposta);
    double confianca = resposta.confidence() == null ? 0.0 : resposta.confidence();
    Optional<Imagem> referencia =
        situacao == SituacaoDaProva.CONCLUIDA ? referencia(resposta) : Optional.empty();
    Optional<FaixaEstimada> faixa = referencia.flatMap(this::estimarFaixa);
    return new ResultadoDaProva(situacao, confianca, faixa, referencia);
  }

  @Override
  public CredencialCurta emitir(UUID tentativa) {
    Credentials credencial =
        chamar(
            CREDENCIAL,
            () ->
                sts.assumeRole(r ->
                        r.roleArn(papelCapturaArn)
                            .roleSessionName(PREFIXO_DA_SESSAO_DO_PAPEL + tentativa)
                            .durationSeconds(DURACAO_DA_CREDENCIAL_EM_SEGUNDOS)
                            .policy(POLITICA_DE_SESSAO))
                    .credentials());
    return new CredencialCurta(
        credencial.accessKeyId(),
        credencial.secretAccessKey(),
        credencial.sessionToken(),
        credencial.expiration());
  }

  @Override
  public void close() {
    try {
      rekognition.close();
    } finally {
      sts.close();
    }
  }

  private static SituacaoDaProva situacao(GetFaceLivenessSessionResultsResponse resposta) {
    return switch (resposta.status()) {
      case CREATED -> SituacaoDaProva.CRIADA;
      case IN_PROGRESS -> SituacaoDaProva.EM_ANDAMENTO;
      case SUCCEEDED -> SituacaoDaProva.CONCLUIDA;
      case FAILED -> SituacaoDaProva.FALHOU;
      case EXPIRED -> SituacaoDaProva.EXPIRADA;
      case null, default -> {
        LOG.warn("AWS devolveu estado desconhecido em {}: vale como falha", RESULTADO);
        yield SituacaoDaProva.FALHOU;
      }
    };
  }

  private static Optional<Imagem> referencia(GetFaceLivenessSessionResultsResponse resposta) {
    @Nullable AuditImage imagem = resposta.referenceImage();
    @Nullable SdkBytes bytes = imagem == null ? null : imagem.bytes();
    if (bytes == null) {
      return Optional.empty();
    }
    Optional<Imagem> referencia = Imagem.ler(bytes.asByteArray());
    if (referencia.isEmpty()) {
      LOG.warn("AWS devolveu referência que não é JPEG nem PNG de até 5 MB em {}", RESULTADO);
    }
    return referencia;
  }

  /** Faixa só com exatamente um rosto na referência; imagem recusada deixa a faixa vazia. */
  private Optional<FaixaEstimada> estimarFaixa(Imagem referencia) {
    List<FaceDetail> rostos;
    try {
      rostos =
          chamar(
              ESTIMATIVA,
              () ->
                  rekognition
                      .detectFaces(r ->
                          r.image(i -> i.bytes(SdkBytes.fromByteArrayUnsafe(referencia.bytes())))
                              .attributes(Attribute.AGE_RANGE))
                      .faceDetails(),
              e -> e instanceof InvalidImageFormatException || e instanceof ImageTooLargeException);
    } catch (InvalidImageFormatException | ImageTooLargeException e) {
      LOG.warn("AWS recusou a imagem de referência em {}: faixa vazia", ESTIMATIVA);
      return Optional.empty();
    }
    if (rostos.size() != 1) {
      LOG.warn("AWS achou {} rostos na referência em {}: faixa vazia", rostos.size(), ESTIMATIVA);
      return Optional.empty();
    }
    @Nullable AgeRange idade = rostos.getFirst().ageRange();
    if (idade == null || idade.low() == null || idade.high() == null) {
      LOG.warn("AWS não estimou a idade em {}: faixa vazia", ESTIMATIVA);
      return Optional.empty();
    }
    return Optional.of(new FaixaEstimada(idade.low(), idade.high()));
  }

  private <T> T chamar(String operacao, Supplier<T> chamada) {
    return chamar(operacao, chamada, e -> false);
  }

  /**
   * Como {@link #chamar(String, Supplier)}, mas as falhas do serviço que {@code esperada} aceita
   * voltam intactas para quem chamou tratar, sem passar pela tradução genérica.
   */
  private <T> T chamar(
      String operacao, Supplier<T> chamada, Predicate<AwsServiceException> esperada) {
    try {
      return medicao.medir(FORNECEDOR, operacao, chamada);
    } catch (AwsServiceException e) {
      if (esperada.test(e)) {
        throw e;
      }
      throw traduzir(operacao, e);
    } catch (SdkClientException e) {
      LOG.warn("AWS fora de alcance em {} ({})", operacao, e.getClass().getSimpleName());
      throw indisponivel(operacao);
    }
  }

  /** Devolve a exceção a lançar; nunca a do SDK, cuja mensagem traz ARN e conta. */
  private static RuntimeException traduzir(String operacao, AwsServiceException e) {
    int status = e.statusCode();
    String codigo = codigo(e.awsErrorDetails());
    if (acessoNegado(status, codigo)) {
      LOG.error(
          "AWS negou {} ({} {}, requisição {}): confira IAM do usuário da verificação e do papel"
              + " de captura",
          operacao,
          status,
          codigo,
          e.requestId());
      return indisponivel(operacao);
    }
    if (status >= 500 || status == 429 || e.isThrottlingException()) {
      LOG.warn(
          "AWS indisponível em {} ({} {}, requisição {})", operacao, status, codigo, e.requestId());
      return indisponivel(operacao);
    }
    LOG.error(
        "AWS recusou o pedido em {} ({} {}, requisição {}): pedido malformado",
        operacao,
        status,
        codigo,
        e.requestId());
    return new IllegalStateException(
        "AWS recusou o pedido em " + operacao + " (" + status + " " + codigo + ")");
  }

  /**
   * O Rekognition devolve o acesso negado como HTTP 400, e o STS usa 400 e 403 conforme o código:
   * vale o status ou o código do erro.
   */
  private static boolean acessoNegado(int status, String codigo) {
    return status == 401
        || status == 403
        || CODIGOS_DE_ACESSO_NEGADO.contains(codigo)
        || codigo.startsWith("ExpiredToken");
  }

  private static String codigo(@Nullable AwsErrorDetails detalhes) {
    return detalhes == null || detalhes.errorCode() == null ? "sem código" : detalhes.errorCode();
  }

  private static FornecedorIndisponivel indisponivel(String operacao) {
    return new FornecedorIndisponivel("AWS indisponível em " + operacao, null);
  }
}
