package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A lista SHELL do sw.js e os sprites do cena.json conferem com os arquivos servidos: um item
 * faltando quebra o install do worker inteiro, e um arquivo do index fora do shell deixa o app sem
 * abrir offline.
 */
class ShellDoServiceWorkerTest {

  /** O terraço não está em "props" do cena.json, mas a cena pede as mesmas larguras dele. */
  private static final String TERRACO = "terraco";

  /** Os {@code rel} de {@code <link>} que o app precisa para abrir (os ícones ficam de fora). */
  private static final Set<String> CARREGADOS_PELO_INDEX = Set.of("modulepreload", "stylesheet");

  @Test
  void todaEntradaDoShellExisteEmStatic() {
    List<String> ausentes =
        Estaticos.shell().stream()
            .filter(url -> !Files.isRegularFile(Estaticos.RAIZ.resolve(arquivoDaUrl(url))))
            .toList();

    assertThat(ausentes).as("Entradas do SHELL sem arquivo em static/").isEmpty();
  }

  @Test
  void todoArquivoCitadoNoIndexEstaNoShell() {
    Stream<String> scripts =
        Estaticos.tagsDoIndex("script").stream()
            .map(tag -> tag.get("src"))
            .filter(Objects::nonNull);
    Stream<String> links =
        Estaticos.tagsDoIndex("link").stream()
            .filter(tag -> CARREGADOS_PELO_INDEX.contains(tag.get("rel")))
            .map(tag -> tag.get("href"));
    List<String> citados = Stream.concat(scripts, links).toList();

    assertThat(citados).isNotEmpty();
    assertThat(Estaticos.shell()).containsAll(citados);
  }

  @Test
  void variantesDoDadosJsSaoAsDoCenaJson() {
    List<Integer> doCenaJson = JsonPath.read(cenaJson(), "$.variantes");

    assertThat(Estaticos.variantesDoDadosJs()).isEqualTo(doCenaJson);
  }

  @Test
  void cadaSpriteTemArquivoEmCadaMomentoELargura() {
    String cena = cenaJson();
    Map<String, Map<String, Object>> props = JsonPath.read(cena, "$.props");
    List<Integer> variantes = JsonPath.read(cena, "$.variantes");
    Set<String> momentos = new TreeSet<>();
    props.values().forEach(porMomento -> momentos.addAll(porMomento.keySet()));

    List<String> esperados = new ArrayList<>();
    for (String sprite : sprites(props.keySet())) {
      for (String momento : momentos) {
        for (int largura : variantes) {
          esperados.add("assets/cena/%s-%s-%d.webp".formatted(sprite, momento, largura));
        }
      }
    }

    assertThat(props).isNotEmpty();
    assertThat(momentos).containsExactly("crepusculo", "noite");
    assertThat(esperados)
        .filteredOn(caminho -> !Files.isRegularFile(Estaticos.RAIZ.resolve(caminho)))
        .as("Sprites sem arquivo")
        .isEmpty();
  }

  private static Set<String> sprites(Set<String> props) {
    Set<String> todos = new TreeSet<>(props);
    todos.add(TERRACO);
    return todos;
  }

  /** O servidor entrega o index.html na raiz. */
  private static String arquivoDaUrl(String url) {
    return url.equals("/") ? "index.html" : url.substring(1);
  }

  private static String cenaJson() {
    return Estaticos.texto("assets/cena/cena.json");
  }
}
