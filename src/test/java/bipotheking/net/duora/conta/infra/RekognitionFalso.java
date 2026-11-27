package bipotheking.net.duora.conta.infra;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;

/**
 * Rekognition em memória: guarda o último pedido de {@code CreateFaceLivenessSession} e responde a
 * sessão combinada ou lança a falha combinada. Nada sai da JVM; qualquer outra operação cai no
 * {@code UnsupportedOperationException} padrão da interface.
 */
final class RekognitionFalso implements RekognitionClient {

  private final @Nullable String sessaoId;
  private final @Nullable RuntimeException falha;
  private @Nullable CreateFaceLivenessSessionRequest pedido;
  private boolean fechado;

  private RekognitionFalso(@Nullable String sessaoId, @Nullable RuntimeException falha) {
    this.sessaoId = sessaoId;
    this.falha = falha;
  }

  static RekognitionFalso respondendo(String sessaoId) {
    return new RekognitionFalso(sessaoId, null);
  }

  static RekognitionFalso falhando(RuntimeException falha) {
    return new RekognitionFalso(null, falha);
  }

  @Override
  public CreateFaceLivenessSessionResponse createFaceLivenessSession(
      CreateFaceLivenessSessionRequest pedido) {
    this.pedido = pedido;
    if (falha != null) {
      throw falha;
    }
    return CreateFaceLivenessSessionResponse.builder().sessionId(sessaoId).build();
  }

  /** O último pedido recebido; falha o teste se nenhum chegou. */
  CreateFaceLivenessSessionRequest pedido() {
    if (pedido == null) {
      throw new AssertionError("CreateFaceLivenessSession não foi chamado");
    }
    return pedido;
  }

  boolean fechado() {
    return fechado;
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void close() {
    fechado = true;
  }
}
