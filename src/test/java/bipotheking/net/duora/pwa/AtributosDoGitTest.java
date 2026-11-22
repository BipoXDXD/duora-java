package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * O .gitattributes normaliza os textos de {@code static/} para LF (a impressão do CACHE_VERSION
 * precisa ser a mesma em qualquer sistema) e deixa os binários intocados.
 */
class AtributosDoGitTest {

  private static final String MANIFEST = "src/main/resources/static/manifest.webmanifest";
  private static final String TERRACO =
      "src/main/resources/static/assets/cena/terraco-crepusculo-1800.webp";

  @Test
  void textoDeStaticNormalizaParaLf() throws Exception {
    String saida = checkAttr(MANIFEST);

    assertThat(saida).contains(MANIFEST + ": text: auto").contains(MANIFEST + ": eol: lf");
  }

  @Test
  void webpEBinario() throws Exception {
    assertThat(checkAttr(TERRACO)).contains(TERRACO + ": text: unset");
  }

  private static String checkAttr(String caminho) throws IOException, InterruptedException {
    Process git;
    try {
      git =
          new ProcessBuilder("git", "check-attr", "text", "eol", "--", caminho)
              .redirectErrorStream(true)
              .start();
    } catch (IOException semGit) {
      assumeTrue(false, "git não encontrado: " + semGit.getMessage());
      throw semGit;
    }
    String saida = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assumeTrue(git.waitFor(30, TimeUnit.SECONDS), "git check-attr não terminou");
    assumeTrue(git.exitValue() == 0, "fora de um repositório git: " + saida);
    return saida;
  }
}
