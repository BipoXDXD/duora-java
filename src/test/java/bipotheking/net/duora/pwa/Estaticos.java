package bipotheking.net.duora.pwa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Leitura dos arquivos servidos como o build os deixa em {@code target/classes/static}: os de
 * {@code src/main/resources/static} junto da saída do Vite ({@code target/vite}).
 */
final class Estaticos {

  /** O Surefire roda na raiz do módulo, então o caminho relativo aponta para a saída do build. */
  static final Path RAIZ = Path.of("target", "classes", "static");

  private static final Path DADOS_TS = Path.of("frontend", "src", "dominio", "dados.ts");

  private static final Pattern ITEM_ENTRE_ASPAS = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern ATRIBUTO = Pattern.compile("\\s([a-z-]+)=\"([^\"]*)\"");

  private Estaticos() {}

  static String texto(String caminhoRelativo) {
    return ler(RAIZ.resolve(caminhoRelativo));
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
    String lista = grupo(ler(DADOS_TS), "export const VARIANTES = \\[([^\\]]*)\\];");
    return Stream.of(lista.split(",")).map(String::strip).map(Integer::valueOf).toList();
  }

  /**
   * Os atributos de cada tag {@code <nome>} do {@code index.html} gerado, na ordem do HTML.
   * Atributos sem valor (como {@code crossorigin}) ficam de fora; o Vite escreve os demais entre
   * aspas duplas.
   */
  static List<Map<String, String>> tagsDoIndex(String nome) {
    return Pattern.compile("<" + nome + "\\b[^>]*>")
        .matcher(texto("index.html"))
        .results()
        .map(tag -> atributos(tag.group()))
        .toList();
  }

  private static Map<String, String> atributos(String tag) {
    return ATRIBUTO
        .matcher(tag)
        .results()
        .collect(Collectors.toMap(m -> m.group(1), m -> m.group(2), (a, b) -> a));
  }

  private static String ler(Path arquivo) {
    try {
      return Files.readString(arquivo, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
