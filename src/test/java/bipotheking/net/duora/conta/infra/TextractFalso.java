package bipotheking.net.duora.conta.infra;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeDocumentResponse;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;

/**
 * Textract em memória: guarda os pedidos de {@code AnalyzeDocument} e responde pelo conteúdo do
 * documento (o texto dos bytes) com as respostas de query combinadas, ou lança a falha combinada.
 * Documento sem nada combinado volta sem resposta de query. Nada sai da JVM; qualquer outra
 * operação cai no {@code UnsupportedOperationException} padrão da interface.
 */
final class TextractFalso implements TextractClient {

  private static final byte[] MAGICO_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  private final Map<String, List<Block>> respostas = new HashMap<>();
  private final Map<String, RuntimeException> falhas = new HashMap<>();
  private final List<AnalyzeDocumentRequest> pedidos = new ArrayList<>();
  private int blocos;
  private boolean fechado;

  /** Uma query do documento responde {@code texto} com essa confiança (0 a 100). */
  TextractFalso respondendo(String documento, String texto, float confianca) {
    respostas
        .computeIfAbsent(documento, d -> new ArrayList<>())
        .add(
            Block.builder()
                .blockType(BlockType.QUERY_RESULT)
                .id("resposta-" + blocos++)
                .text(texto)
                .confidence(confianca)
                .build());
    return this;
  }

  TextractFalso falhando(String documento, RuntimeException falha) {
    falhas.put(documento, falha);
    return this;
  }

  @Override
  public AnalyzeDocumentResponse analyzeDocument(AnalyzeDocumentRequest pedido) {
    pedidos.add(pedido);
    String documento = conteudo(pedido.document().bytes());
    @Nullable RuntimeException falha = falhas.get(documento);
    if (falha != null) {
      throw falha;
    }
    List<Block> resposta = new ArrayList<>();
    resposta.add(Block.builder().blockType(BlockType.PAGE).id("pagina").build());
    resposta.add(
        Block.builder()
            .blockType(BlockType.LINE)
            .id("linha")
            .text(documento)
            .confidence(99f)
            .build());
    resposta.addAll(respostas.getOrDefault(documento, List.of()));
    return AnalyzeDocumentResponse.builder().blocks(resposta).build();
  }

  /** Os bytes de uma imagem de teste sem o mágico JPEG ({@code FF D8 FF}) que a abre. */
  static String conteudo(SdkBytes bytes) {
    byte[] tudo = bytes.asByteArray();
    return new String(tudo, MAGICO_JPEG.length, tudo.length - MAGICO_JPEG.length, UTF_8);
  }

  /** Imagem de teste: o mágico JPEG seguido do texto que os falsos reconhecem. */
  static byte[] jpeg(String conteudo) {
    byte[] texto = conteudo.getBytes(UTF_8);
    byte[] tudo = Arrays.copyOf(MAGICO_JPEG, MAGICO_JPEG.length + texto.length);
    System.arraycopy(texto, 0, tudo, MAGICO_JPEG.length, texto.length);
    return tudo;
  }

  List<AnalyzeDocumentRequest> pedidos() {
    return List.copyOf(pedidos);
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
