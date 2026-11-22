package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/**
 * O JS que a primeira tela baixa (o módulo de entrada e os modulepreload do index gerado) cabe no
 * orçamento, medido em gzip nível 9. A meta é 80 KB; os contratos aceitam até 90 KB antes de
 * avaliar o preact/compat. O valor fica em target/tamanho-js-inicial.txt para o resumo da etapa.
 */
class TamanhoDoJsInicialTest {

  private static final long LIMITE_BYTES = 92_160;
  private static final Path RELATORIO = Path.of("target", "tamanho-js-inicial.txt");

  @Test
  void jsInicialCabeNaMeta() throws IOException {
    List<String> iniciais = jsInicial();
    long bytes = 0;
    for (String url : iniciais) {
      bytes += tamanhoGzip(Files.readAllBytes(Estaticos.RAIZ.resolve(url.substring(1))));
    }
    Files.writeString(
        RELATORIO,
        String.format(
            Locale.ROOT, "%d bytes (%.2f KB) gzip: %s%n", bytes, bytes / 1024.0, iniciais),
        StandardCharsets.UTF_8);

    assertThat(iniciais).isNotEmpty();
    assertThat(bytes)
        .as("JS inicial em gzip (bytes): %s", iniciais)
        .isLessThanOrEqualTo(LIMITE_BYTES);
  }

  private static List<String> jsInicial() {
    Stream<String> modulos =
        Estaticos.tagsDoIndex("script").stream()
            .filter(tag -> "module".equals(tag.get("type")) && tag.containsKey("src"))
            .map(tag -> tag.get("src"));
    Stream<String> preloads =
        Estaticos.tagsDoIndex("link").stream()
            .filter(tag -> "modulepreload".equals(tag.get("rel")) && tag.containsKey("href"))
            .map(tag -> tag.get("href"));
    return Stream.concat(modulos, preloads).toList();
  }

  private static long tamanhoGzip(byte[] conteudo) throws IOException {
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    try (GzipNivel9 gzip = new GzipNivel9(saida)) {
      gzip.write(conteudo);
    }
    return saida.size();
  }

  /** O GZIPOutputStream só expõe o nível de compressão pelo Deflater protegido. */
  private static final class GzipNivel9 extends GZIPOutputStream {
    GzipNivel9(ByteArrayOutputStream saida) throws IOException {
      super(saida);
      def.setLevel(Deflater.BEST_COMPRESSION);
    }
  }
}
