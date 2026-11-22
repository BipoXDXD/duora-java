package bipotheking.net.duora.visual;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Compara duas capturas PNG pixel a pixel. Um pixel conta como diferente quando algum canal (A, R,
 * G ou B) muda mais de {@value #LIMITE_POR_CANAL}; abaixo disso é ruído de antialiasing.
 */
final class ComparadorDeCapturas {

  /** Mudança máxima, por canal, que ainda conta como o mesmo pixel. */
  static final int LIMITE_POR_CANAL = 32;

  /** Fração máxima de pixels diferentes para uma captura passar. */
  static final double FRACAO_MAXIMA = 0.001;

  private static final int VERMELHO = 0xFFFF0000;

  /**
   * Com dimensões diferentes, a comparação cobre a maior largura e a maior altura: o que só existe
   * numa das capturas conta como diferente.
   */
  record Resultado(boolean mesmasDimensoes, long diferentes, long total) {

    boolean passa() {
      return mesmasDimensoes && diferentes <= total * FRACAO_MAXIMA;
    }
  }

  private ComparadorDeCapturas() {}

  /** Grava em {@code diff} a captura de depois, esmaecida, com os pixels diferentes em vermelho. */
  static Resultado comparar(Path antes, Path depois, Path diff) throws IOException {
    BufferedImage a = ler(antes);
    BufferedImage b = ler(depois);
    int largura = Math.max(a.getWidth(), b.getWidth());
    int altura = Math.max(a.getHeight(), b.getHeight());
    BufferedImage marcas = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
    long diferentes = 0;
    for (int y = 0; y < altura; y++) {
      for (int x = 0; x < largura; x++) {
        boolean nasDuas = dentro(a, x, y) && dentro(b, x, y);
        if (!nasDuas || diferem(a.getRGB(x, y), b.getRGB(x, y))) {
          diferentes++;
          marcas.setRGB(x, y, VERMELHO);
        } else {
          marcas.setRGB(x, y, esmaecido(b.getRGB(x, y)));
        }
      }
    }
    if (diferentes > 0) {
      Files.createDirectories(diff.toAbsolutePath().getParent());
      ImageIO.write(marcas, "png", diff.toFile());
    }
    boolean mesmasDimensoes = a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight();
    return new Resultado(mesmasDimensoes, diferentes, (long) largura * altura);
  }

  private static BufferedImage ler(Path arquivo) throws IOException {
    BufferedImage imagem = ImageIO.read(arquivo.toFile());
    if (imagem == null) {
      throw new IOException("não é uma imagem legível: " + arquivo);
    }
    return imagem;
  }

  private static boolean dentro(BufferedImage imagem, int x, int y) {
    return x < imagem.getWidth() && y < imagem.getHeight();
  }

  private static boolean diferem(int argbA, int argbB) {
    for (int deslocamento = 0; deslocamento <= 24; deslocamento += 8) {
      int canalA = argbA >>> deslocamento & 0xFF;
      int canalB = argbB >>> deslocamento & 0xFF;
      if (Math.abs(canalA - canalB) > LIMITE_POR_CANAL) {
        return true;
      }
    }
    return false;
  }

  /** Cinza claro proporcional à luminância: o contexto aparece sem competir com o vermelho. */
  private static int esmaecido(int argb) {
    int r = argb >>> 16 & 0xFF;
    int g = argb >>> 8 & 0xFF;
    int b = argb & 0xFF;
    int cinza = 160 + (r * 299 + g * 587 + b * 114) / 1000 * 95 / 255;
    return 0xFF << 24 | cinza << 16 | cinza << 8 | cinza;
  }
}
