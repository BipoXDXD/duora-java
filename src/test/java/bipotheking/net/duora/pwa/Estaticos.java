package bipotheking.net.duora.pwa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Leitura dos arquivos de {@code src/main/resources/static} como estão no repositório. */
final class Estaticos {

  /** O Surefire roda na raiz do módulo, então o caminho relativo aponta para o código-fonte. */
  static final Path RAIZ = Path.of("src", "main", "resources", "static");

  private static final Pattern ITEM_ENTRE_ASPAS = Pattern.compile("\"([^\"]+)\"");

  private Estaticos() {}

  static String texto(String caminhoRelativo) {
    try {
      return Files.readString(RAIZ.resolve(caminhoRelativo), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Caminhos relativos à raiz, com "/", em ordem; ignora arquivos ocultos como .DS_Store. */
  static List<String> arquivos() {
    try (Stream<Path> todos = Files.walk(RAIZ)) {
      return todos
          .filter(Files::isRegularFile)
          .filter(caminho -> !caminho.getFileName().toString().startsWith("."))
          .map(caminho -> RAIZ.relativize(caminho).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String versaoDoCache() {
    return grupo(texto("sw.js"), "const CACHE_VERSION = \"([^\"]+)\";");
  }

  static List<String> shell() {
    return itensEntreAspas(grupo(texto("sw.js"), "(?s)const SHELL = \\[(.*?)\\];"));
  }

  static List<Integer> variantesDoDadosJs() {
    String lista = grupo(texto("js/dados.js"), "export const VARIANTES = \\[([^\\]]*)\\];");
    return Stream.of(lista.split(",")).map(String::strip).map(Integer::valueOf).toList();
  }

  private static List<String> itensEntreAspas(String trecho) {
    return ITEM_ENTRE_ASPAS.matcher(trecho).results().map(m -> m.group(1)).toList();
  }

  private static String grupo(String texto, String regex) {
    Matcher m = Pattern.compile(regex).matcher(texto);
    if (!m.find()) {
      throw new IllegalStateException("Trecho não encontrado: " + regex);
    }
    return m.group(1);
  }
}
