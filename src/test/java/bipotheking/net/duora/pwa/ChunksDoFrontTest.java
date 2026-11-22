package bipotheking.net.duora.pwa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A divisão do bundle que os testes de navegador supõem: o laço de polling num chunk próprio (a
 * sonda do PollingIT o reconhece pela pilha) e CSS sem {@code :has()}, que celulares com navegador
 * mais antigo não entendem.
 */
class ChunksDoFrontTest {

  @Test
  void pollingTemChunkProprio() {
    List<String> chunks =
        Estaticos.arquivos().stream()
            .filter(caminho -> caminho.matches("app/polling-[^/]+\\.js"))
            .toList();

    assertThat(chunks).hasSize(1);
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
}
