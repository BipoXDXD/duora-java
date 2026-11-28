package bipotheking.net.duora.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * As duas variantes WebP de uma imagem e a cópia da nítida para a moderação, em arquivos
 * temporários. Quem recebe é dono dos arquivos: {@link #close()} apaga os três (use com
 * try-with-resources depois de guardá-los). Só a nítida e a borrada são guardadas.
 *
 * @param paraModeracao a nítida em JPEG, sem metadados (o Rekognition não lê WebP); nunca guardada
 * @param largura largura da nítida, em pixels
 * @param altura altura da nítida, em pixels
 */
public record ImagemProcessada(
    Path nitida, Path borrada, Path paraModeracao, int largura, int altura)
    implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ImagemProcessada.class);

  /**
   * Apaga os temporários. Uma falha só vai para o log: a retenção de temporários varre o que
   * sobrar, e uma exceção aqui esconderia a do bloco que usou a imagem.
   */
  @Override
  public void close() {
    for (Path arquivo : new Path[] {nitida, borrada, paraModeracao}) {
      try {
        Files.deleteIfExists(arquivo);
      } catch (IOException e) {
        LOG.warn("Não foi possível apagar um temporário de imagem", e);
      }
    }
  }
}
