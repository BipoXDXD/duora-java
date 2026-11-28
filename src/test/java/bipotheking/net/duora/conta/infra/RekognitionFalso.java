package bipotheking.net.duora.conta.infra;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionResponse;
import software.amazon.awssdk.services.rekognition.model.DetectFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsRequest;
import software.amazon.awssdk.services.rekognition.model.GetFaceLivenessSessionResultsResponse;

/**
 * Rekognition em memória: guarda os pedidos de {@code CreateFaceLivenessSession}, {@code
 * GetFaceLivenessSessionResults} e {@code DetectFaces} e responde o combinado ou lança a falha
 * combinada. Nada sai da JVM; qualquer outra operação cai no {@code UnsupportedOperationException}
 * padrão da interface.
 */
final class RekognitionFalso implements RekognitionClient {

  private final @Nullable String sessaoId;
  private final @Nullable RuntimeException falha;
  private @Nullable CreateFaceLivenessSessionRequest pedido;
  private @Nullable GetFaceLivenessSessionResultsResponse resultado;
  private @Nullable RuntimeException falhaNoResultado;
  private @Nullable GetFaceLivenessSessionResultsRequest pedidoDoResultado;
  private List<FaceDetail> rostos = List.of();
  private @Nullable RuntimeException falhaNoDetectFaces;
  private final List<DetectFacesRequest> pedidosDeDetectFaces = new ArrayList<>();
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

  /** Responde o {@code GetFaceLivenessSessionResults}; o {@code DetectFaces} não acha rosto. */
  static RekognitionFalso comResultado(GetFaceLivenessSessionResultsResponse resultado) {
    RekognitionFalso falso = new RekognitionFalso(null, null);
    falso.resultado = resultado;
    return falso;
  }

  static RekognitionFalso falhandoNoResultado(RuntimeException falha) {
    RekognitionFalso falso = new RekognitionFalso(null, null);
    falso.falhaNoResultado = falha;
    return falso;
  }

  /** Rostos que o {@code DetectFaces} devolve. */
  RekognitionFalso detectando(FaceDetail... rostos) {
    this.rostos = List.of(rostos);
    return this;
  }

  RekognitionFalso falhandoNoDetectFaces(RuntimeException falha) {
    this.falhaNoDetectFaces = falha;
    return this;
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

  @Override
  public GetFaceLivenessSessionResultsResponse getFaceLivenessSessionResults(
      GetFaceLivenessSessionResultsRequest pedido) {
    this.pedidoDoResultado = pedido;
    if (falhaNoResultado != null) {
      throw falhaNoResultado;
    }
    if (resultado == null) {
      throw new AssertionError("GetFaceLivenessSessionResults sem resposta combinada");
    }
    return resultado;
  }

  @Override
  public DetectFacesResponse detectFaces(DetectFacesRequest pedido) {
    pedidosDeDetectFaces.add(pedido);
    if (falhaNoDetectFaces != null) {
      throw falhaNoDetectFaces;
    }
    return DetectFacesResponse.builder().faceDetails(rostos).build();
  }

  /** O último pedido recebido; falha o teste se nenhum chegou. */
  CreateFaceLivenessSessionRequest pedido() {
    if (pedido == null) {
      throw new AssertionError("CreateFaceLivenessSession não foi chamado");
    }
    return pedido;
  }

  /** O último pedido de resultado; falha o teste se nenhum chegou. */
  GetFaceLivenessSessionResultsRequest pedidoDoResultado() {
    if (pedidoDoResultado == null) {
      throw new AssertionError("GetFaceLivenessSessionResults não foi chamado");
    }
    return pedidoDoResultado;
  }

  List<DetectFacesRequest> pedidosDeDetectFaces() {
    return List.copyOf(pedidosDeDetectFaces);
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
