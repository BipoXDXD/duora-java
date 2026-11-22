package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * O {@code CACHE_VERSION} do sw.js segue a impressão digital de {@code static/}: mudou um arquivo
 * do app, muda a versão, e o activate apaga o cache antigo. Sem isso, um asset trocado com o mesmo
 * nome continuaria servido do cache velho.
 */
class ServiceWorkerVersaoTest {

  @Test
  void versaoDoCacheAcompanhaAImpressaoDosArquivosEstaticos() throws Exception {
    String esperada = "duora-" + impressao().substring(0, 12);

    assertThat(Estaticos.versaoDoCache())
        .as("Atualize em sw.js: const CACHE_VERSION = \"%s\";", esperada)
        .isEqualTo(esperada);
  }

  /**
   * SHA-256 de cada arquivo de {@code static/} menos o sw.js (que guarda o resultado), em ordem de
   * caminho: caminho relativo, um byte zero e o conteúdo. O .gitattributes fixa LF nos textos para
   * a impressão ser a mesma em qualquer sistema.
   */
  private static String impressao() throws IOException, NoSuchAlgorithmException {
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    for (String caminho : Estaticos.arquivos()) {
      if (caminho.equals("sw.js")) {
        continue;
      }
      sha.update(caminho.getBytes(StandardCharsets.UTF_8));
      sha.update((byte) 0);
      sha.update(Files.readAllBytes(Estaticos.RAIZ.resolve(caminho)));
    }
    return HexFormat.of().formatHex(sha.digest());
  }
}
