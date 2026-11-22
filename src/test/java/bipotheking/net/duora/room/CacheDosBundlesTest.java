package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Os arquivos de /app/ têm o hash do conteúdo no nome: ficam em cache para sempre. O index.html,
 * que aponta para eles, não pode ficar, senão um deploy novo nunca chegaria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CacheDosBundlesTest {

  private static final Pattern MODULO_DO_INDEX =
      Pattern.compile("<script\\b[^>]*\\ssrc=\"(/app/[^\"]+\\.js)\"");

  private final HttpClient cliente = HttpClient.newHttpClient();

  @Value("${local.server.port}")
  private int porta;

  @Test
  void bundleComHashEImutavel() throws Exception {
    HttpResponse<String> index = buscar("/index.html");
    Matcher modulo = MODULO_DO_INDEX.matcher(index.body());
    assertThat(modulo.find()).as("<script src=\"/app/...\"> no index.html").isTrue();

    HttpResponse<String> bundle = buscar(modulo.group(1));

    assertThat(bundle.statusCode()).isEqualTo(200);
    assertThat(bundle.headers().firstValue("Cache-Control"))
        .hasValueSatisfying(cache -> assertThat(cache).contains("immutable"));
    assertThat(index.headers().firstValue("Cache-Control").orElse("")).doesNotContain("immutable");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "/index.html"})
  void indexERevalidadoACadaBusca(String caminho) throws Exception {
    HttpResponse<String> index = buscar(caminho);

    assertThat(index.statusCode()).isEqualTo(200);
    assertThat(index.headers().firstValue("Cache-Control")).hasValue("no-cache");
  }

  private HttpResponse<String> buscar(String caminho) throws IOException, InterruptedException {
    HttpRequest pedido =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho)).build();
    return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
  }
}
