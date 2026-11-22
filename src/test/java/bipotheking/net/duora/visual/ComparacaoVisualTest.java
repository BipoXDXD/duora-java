package bipotheking.net.duora.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comparação das capturas visuais de antes e depois da migração do front. Os testes do comparador
 * rodam sempre; a comparação das capturas, só com as duas pastas:
 *
 * <pre>
 * ./mvnw -q test -Dtest=ComparacaoVisualTest -Dvisual.antes=&lt;dir&gt; -Dvisual.depois=&lt;dir&gt;
 * </pre>
 *
 * As imagens de diferença ficam em {@code <depois>/diff/}.
 */
class ComparacaoVisualTest {

  @TempDir Path pasta;

  @Test
  void imagensIguaisTemZeroDiferencas() throws IOException {
    Path antes = gravar("antes.png", imagem());
    Path depois = gravar("depois.png", imagem());

    var resultado = ComparadorDeCapturas.comparar(antes, depois, pasta.resolve("diff.png"));

    assertEquals(new ComparadorDeCapturas.Resultado(true, 0, 100), resultado);
  }

  @Test
  void umPixelMudadoAlemDoLimiteConta() throws IOException {
    BufferedImage mudada = imagem();
    mudada.setRGB(2, 3, rgb(100 + 40, 100, 100));
    mudada.setRGB(7, 8, rgb(100 + 20, 100, 100));
    Path antes = gravar("antes.png", imagem());
    Path depois = gravar("depois.png", mudada);

    var resultado = ComparadorDeCapturas.comparar(antes, depois, pasta.resolve("diff.png"));

    assertEquals(1, resultado.diferentes());
  }

  @Test
  void capturasIguaisApagamODiffDeUmaRodadaAnterior() throws IOException {
    Path antes = gravar("antes.png", imagem());
    Path depois = gravar("depois.png", imagem());
    Path diffAntigo = gravar("diff.png", imagem());

    ComparadorDeCapturas.comparar(antes, depois, diffAntigo);

    assertFalse(Files.exists(diffAntigo), "diff antigo continua em " + diffAntigo);
  }

  @TestFactory
  @EnabledIfSystemProperty(named = "visual.antes", matches = ".+")
  @EnabledIfSystemProperty(named = "visual.depois", matches = ".+")
  Stream<DynamicTest> capturasDeDepoisBatemComAsDeAntes() throws IOException {
    Path antes = Path.of(System.getProperty("visual.antes"));
    Path depois = Path.of(System.getProperty("visual.depois"));
    List<Path> referencias;
    try (Stream<Path> arquivos = Files.list(antes)) {
      referencias =
          arquivos.filter(arquivo -> arquivo.toString().endsWith(".png")).sorted().toList();
    }
    assertFalse(referencias.isEmpty(), "nenhum PNG em " + antes);
    return referencias.stream()
        .map(referencia -> {
          String nome = referencia.getFileName().toString();
          return DynamicTest.dynamicTest(
              nome,
              () -> {
                var resultado =
                    ComparadorDeCapturas.comparar(
                        referencia, depois.resolve(nome), depois.resolve("diff").resolve(nome));
                assertTrue(resultado.passa(), nome + ": " + resultado);
              });
        });
  }

  /** 10×10 cinza-médio opaco: os dois lados partem da mesma cor. */
  private static BufferedImage imagem() {
    BufferedImage imagem = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 10; y++) {
      for (int x = 0; x < 10; x++) {
        imagem.setRGB(x, y, rgb(100, 100, 100));
      }
    }
    return imagem;
  }

  private static int rgb(int r, int g, int b) {
    return 0xFF << 24 | r << 16 | g << 8 | b;
  }

  private Path gravar(String nome, BufferedImage imagem) throws IOException {
    Path arquivo = pasta.resolve(nome);
    if (!ImageIO.write(imagem, "png", arquivo.toFile())) {
      throw new IOException("sem escritor PNG");
    }
    return arquivo;
  }
}
