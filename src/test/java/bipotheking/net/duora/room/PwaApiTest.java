package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Arquivos do PWA servidos pelo Tomcat de verdade: o tipo do manifest e o cache dependem do
 * servidor embutido, que o MockMvc não usa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PwaApiTest {

  private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  private final HttpClient cliente = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int porta;

  @Test
  void manifestTemContentTypeDeManifest() throws Exception {
    HttpResponse<String> resposta = buscarTexto("/manifest.webmanifest");

    assertThat(resposta.statusCode()).isEqualTo(200);
    assertThat(resposta.headers().firstValue("Content-Type"))
        .hasValueSatisfying(tipo -> assertThat(tipo).startsWith("application/manifest+json"));
  }

  @Test
  void serviceWorkerNaoFicaEmCacheHttp() throws Exception {
    HttpResponse<String> resposta = buscarTexto("/sw.js");

    assertThat(resposta.statusCode()).isEqualTo(200);
    assertThat(resposta.headers().firstValue("Cache-Control"))
        .hasValueSatisfying(cache -> assertThat(cache).contains("no-cache"));
  }

  @Test
  void iconesDoManifestExistem() throws Exception {
    List<Map<String, String>> icones =
        JsonPath.read(buscarTexto("/manifest.webmanifest").body(), "$.icons");
    assertThat(icones).isNotEmpty();

    for (Map<String, String> icone : icones) {
      HttpResponse<byte[]> resposta = buscarBytes(icone.get("src"));
      assertThat(resposta.statusCode()).as(icone.get("src")).isEqualTo(200);
      assertThat(resposta.headers().firstValue("Content-Type")).hasValue("image/png");
      assertThat(dimensoesDoPng(resposta.body()))
          .as(icone.get("src"))
          .isEqualTo(icone.get("sizes"));
    }
  }

  /** Largura e altura do cabeçalho IHDR, no formato do {@code sizes} do manifest ("192x192"). */
  private static String dimensoesDoPng(byte[] png) {
    assertThat(png).startsWith(ASSINATURA_PNG);
    ByteBuffer ihdr = ByteBuffer.wrap(png, 16, 8);
    return ihdr.getInt() + "x" + ihdr.getInt();
  }

  private HttpResponse<String> buscarTexto(String caminho)
      throws IOException, InterruptedException {
    return cliente.send(pedido(caminho), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<byte[]> buscarBytes(String caminho)
      throws IOException, InterruptedException {
    return cliente.send(pedido(caminho), HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpRequest pedido(String caminho) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho)).build();
  }
}
