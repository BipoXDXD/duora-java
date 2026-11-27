package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * Prova de vida pelo Amazon Rekognition Face Liveness. Abre a sessão sem imagens de auditoria e sem
 * S3 ({@code AuditImagesLimit=0}, sem {@code OutputConfig} nem {@code KmsKeyId}) e emite, pelo
 * {@code AssumeRole} do papel de captura, a credencial de 15 minutos com que o navegador só pode
 * iniciar o streaming da sessão.
 *
 * <p>Falha de rede, timeout, 5xx, 401, 403 e limitação viram {@link FornecedorIndisponivel}, sem a
 * exceção do SDK como causa: a mensagem da AWS traz ARN e conta. O log diz só a operação, o status,
 * o código do erro e o id da requisição. Outro 4xx é pedido malformado nosso e sobe como veio (bug,
 * 500).
 */
final class ProvaDeVidaAws implements ProvaDeVida, CredencialDoNavegador, AutoCloseable {

  /** Só o que o navegador precisa: iniciar o streaming da prova de vida, nada mais. */
  static final String POLITICA_DE_SESSAO =
      "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
          + "\"Action\":\"rekognition:StartFaceLivenessSession\",\"Resource\":\"*\"}]}";

  static final int DURACAO_DA_CREDENCIAL_EM_SEGUNDOS = 900;

  private static final Logger LOG = LoggerFactory.getLogger(ProvaDeVidaAws.class);

  private static final String FORNECEDOR = "aws";
  private static final String CRIAR_SESSAO = "criar-sessao";
  private static final String CREDENCIAL = "credencial";
  private static final String PREFIXO_DA_SESSAO_DO_PAPEL = "duora-";

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
    throw new UnsupportedOperationException("o resultado da prova de vida chega na A6");
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

  private <T> T chamar(String operacao, Supplier<T> chamada) {
    try {
      return medicao.medir(FORNECEDOR, operacao, chamada);
    } catch (AwsServiceException e) {
      throw traduzir(operacao, e);
    } catch (SdkClientException e) {
      LOG.warn("AWS fora de alcance em {} ({})", operacao, e.getClass().getSimpleName());
      throw indisponivel(operacao);
    }
  }

  private static FornecedorIndisponivel traduzir(String operacao, AwsServiceException e) {
    int status = e.statusCode();
    String codigo = codigo(e.awsErrorDetails());
    if (status == 401 || status == 403) {
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
    throw e;
  }

  private static String codigo(@Nullable AwsErrorDetails detalhes) {
    return detalhes == null || detalhes.errorCode() == null ? "sem código" : detalhes.errorCode();
  }

  private static FornecedorIndisponivel indisponivel(String operacao) {
    return new FornecedorIndisponivel("AWS indisponível em " + operacao, null);
  }
}
