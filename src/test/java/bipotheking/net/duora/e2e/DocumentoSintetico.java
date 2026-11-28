package bipotheking.net.duora.e2e;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * A foto de um documento que não existe: um cartão de 1200 × 760 px (a proporção de um RG ou de uma
 * CNH) com "ESPÉCIME SINTÉTICO" escrito, gerado na hora. Nenhum dado de pessoa real entra no
 * repositório nem nos testes.
 */
final class DocumentoSintetico {

  private static final int LARGURA = 1200;
  private static final int ALTURA = 760;

  private DocumentoSintetico() {}

  /** Os bytes de um JPEG do cartão, com o rótulo (por exemplo, "frente") embaixo do aviso. */
  static byte[] jpeg(String rotulo) {
    BufferedImage imagem = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
    Graphics2D pincel = imagem.createGraphics();
    try {
      pincel.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      pincel.setColor(new Color(0xE8, 0xEE, 0xF2));
      pincel.fillRect(0, 0, LARGURA, ALTURA);
      pincel.setColor(new Color(0x2B, 0x4A, 0x6F));
      pincel.setStroke(new BasicStroke(12));
      pincel.drawRoundRect(24, 24, LARGURA - 48, ALTURA - 48, 48, 48);
      pincel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
      pincel.drawString("ESPÉCIME SINTÉTICO", 120, 340);
      pincel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 56));
      pincel.drawString(rotulo, 120, 460);
    } finally {
      pincel.dispose();
    }
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(imagem, "jpg", saida)) {
        throw new IllegalStateException("sem codificador JPEG no ImageIO");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return saida.toByteArray();
  }
}
