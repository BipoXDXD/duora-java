package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImagemTest {

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  @Test
  void jpegValido() {
    assertThat(Imagem.ler(JPEG).map(Imagem::tipo)).contains(TipoDeImagem.JPEG);
  }

  @Test
  void pngValido() {
    assertThat(Imagem.ler(PNG).map(Imagem::tipo)).contains(TipoDeImagem.PNG);
  }

  @Test
  void pdfRecusado() {
    assertThat(Imagem.ler("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
  }

  @Test
  void vazioRecusado() {
    assertThat(Imagem.ler(new byte[0])).isEmpty();
  }

  @Test
  void cincoMbExatosAceitos() {
    assertThat(Imagem.ler(jpegDe(5_242_880))).isPresent();
  }

  @Test
  void umByteAMaisRecusado() {
    assertThat(Imagem.ler(jpegDe(5_242_881))).isEmpty();
  }

  @Test
  void bytesDevolveCopia() {
    byte[] entrada = JPEG.clone();
    Imagem imagem = Imagem.ler(entrada).orElseThrow();

    entrada[3] = 0;
    imagem.bytes()[3] = 0;

    assertThat(imagem.bytes()).containsExactly(JPEG);
  }

  @Test
  void toStringSemConteudo() {
    assertThat(Imagem.ler(JPEG).orElseThrow()).hasToString("Imagem[JPEG, 4 bytes]");
  }

  private static byte[] jpegDe(int tamanho) {
    byte[] bytes = new byte[tamanho];
    System.arraycopy(JPEG, 0, bytes, 0, JPEG.length);
    return bytes;
  }
}
