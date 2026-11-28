package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A divisão do bundle que os testes de navegador supõem: o laço de polling num chunk próprio (a
 * sonda do PollingIT o reconhece pela pilha), React Router e TanStack Query num chunk da casca de
 * conta que o terraço não baixa, o detector de prova de vida do Amplify num chunk só dele, baixado
 * só na captura da verificação, e CSS sem {@code :has()}, que celulares com navegador mais antigo
 * não entendem.
 */
class ChunksDoFrontTest {

  private static final String CHUNK_DO_AMPLIFY = "app/verificacao-amplify-[^/]+\\.js";
  private static final String CHUNK_DO_FALSO = "app/DetectorFalso-[^/]+\\.js";

  /** Fora o chunk do Amplify, nenhum JS passa do teto de aviso do Vite (500 KB sem gzip). */
  private static final long TETO_DOS_OUTROS_CHUNKS = 512_000;

  @Test
  void pollingTemChunkProprio() {
    List<String> chunks =
        Estaticos.arquivos().stream()
            .filter(caminho -> caminho.matches("app/polling-[^/]+\\.js"))
            .toList();

    assertThat(chunks).hasSize(1);
  }

  @Test
  void cascaDeContaForaDoJsInicial() {
    List<String> chunks =
        Estaticos.arquivos().stream()
            .filter(caminho -> caminho.matches("app/casca-de-conta-[^/]+\\.js"))
            .toList();

    assertThat(chunks).hasSize(1);
    assertThat(Estaticos.jsInicial()).noneMatch(url -> url.contains("casca-de-conta"));
  }

  @Test
  void amplifyNumChunkProprioForaDoJsInicial() {
    List<String> chunks = jsDoApp().stream().filter(c -> c.matches(CHUNK_DO_AMPLIFY)).toList();

    assertThat(chunks).hasSize(1);
    assertThat(Estaticos.texto(chunks.getFirst())).contains("FaceLivenessDetector");
    assertThat(Estaticos.jsInicial()).noneMatch(url -> url.contains("verificacao-amplify"));
    // Nem a casca nem o chunk da rota carregam o Amplify: ele só baixa na captura.
    assertThat(Estaticos.texto(chunkUnico("app/casca-de-conta-[^/]+\\.js")))
        .doesNotContain("FaceLivenessDetector");
    assertThat(Estaticos.texto(chunkUnico("app/VerificacaoPagina-[^/]+\\.js")))
        .doesNotContain("FaceLivenessDetector");
  }

  private static String chunkUnico(String padrao) {
    List<String> achados = jsDoApp().stream().filter(c -> c.matches(padrao)).toList();
    assertThat(achados).as("Chunk %s", padrao).hasSize(1);
    return achados.getFirst();
  }

  @Test
  void soOChunkDoAmplifyPassaDe500KB() {
    List<String> grandes =
        jsDoApp().stream()
            .filter(c -> !c.matches(CHUNK_DO_AMPLIFY))
            .filter(c -> tamanho(c) > TETO_DOS_OUTROS_CHUNKS)
            .toList();

    assertThat(jsDoApp()).anyMatch(c -> c.matches(CHUNK_DO_AMPLIFY));
    assertThat(grandes).as("Chunks acima de %d bytes", TETO_DOS_OUTROS_CHUNKS).isEmpty();
  }

  /** D-F2: o detector falso vem do modo que o servidor manda, nunca de um gancho global. */
  @Test
  void nenhumGanchoGlobalNoBundle() {
    assertThat(jsDoApp()).isNotEmpty();
    assertThat(jsDoApp()).filteredOn(c -> Estaticos.texto(c).contains("__duora")).isEmpty();
  }

  @Test
  void detectorFalsoSoPorImportDinamico() {
    List<String> falsos = jsDoApp().stream().filter(c -> c.matches(CHUNK_DO_FALSO)).toList();

    assertThat(falsos).hasSize(1);
    String arquivo = falsos.getFirst().substring("app/".length());
    // O Rolldown escreve o caminho entre aspas ou crases.
    String caminho = "[\"'`]\\./" + Pattern.quote(arquivo) + "[\"'`]";
    Pattern importDinamico = Pattern.compile("import\\(" + caminho + "\\)");
    Pattern importEstatico = Pattern.compile("(from|import)\\s*" + caminho);
    assertThat(jsDoApp())
        .as("Import dinâmico do detector falso")
        .anyMatch(c -> importDinamico.matcher(Estaticos.texto(c)).find());
    assertThat(jsDoApp())
        .as("Import estático do detector falso")
        .noneMatch(c -> importEstatico.matcher(Estaticos.texto(c)).find());
    assertThat(jsDoApp())
        .filteredOn(c -> !c.equals(falsos.getFirst()))
        .noneMatch(c -> Estaticos.texto(c).contains("Modo de teste: nenhuma câmera é usada."));
    assertThat(Estaticos.jsInicial()).noneMatch(url -> url.contains("DetectorFalso"));
  }

  @Test
  void cssSemHas() {
    List<String> css =
        Estaticos.arquivos().stream()
            .filter(caminho -> caminho.startsWith("app/") && caminho.endsWith(".css"))
            .toList();

    assertThat(css).isNotEmpty();
    assertThat(css).filteredOn(caminho -> Estaticos.texto(caminho).contains(":has(")).isEmpty();
  }

  private static List<String> jsDoApp() {
    return Estaticos.arquivos().stream()
        .filter(caminho -> caminho.startsWith("app/") && caminho.endsWith(".js"))
        .toList();
  }

  private static long tamanho(String caminho) {
    try {
      return Files.size(Estaticos.RAIZ.resolve(caminho));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
