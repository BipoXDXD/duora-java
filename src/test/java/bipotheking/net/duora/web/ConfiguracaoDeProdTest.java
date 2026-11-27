package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Lê o {@code application-prod.properties} empacotado, sem subir o Spring. */
class ConfiguracaoDeProdTest {

  private static final Pattern MARCADOR = Pattern.compile("\\$\\{([^}]*)}");

  @Test
  void segredosSemPadrao() throws IOException {
    List<String> marcadores =
        carregar().values().stream()
            .map(String::valueOf)
            .flatMap(valor -> MARCADOR.matcher(valor).results())
            .map(MatchResult::group)
            .toList();

    assertThat(marcadores).isNotEmpty().noneMatch(marcador -> marcador.contains(":"));
  }

  @Test
  void composeDesligado() throws IOException {
    assertThat(carregar().getProperty("spring.docker.compose.enabled")).isEqualTo("false");
  }

  @Test
  void logEmEcs() throws IOException {
    assertThat(carregar().getProperty("logging.structured.format.console")).isEqualTo("ecs");
  }

  private static Properties carregar() throws IOException {
    try (InputStream entrada =
        ConfiguracaoDeProdTest.class.getResourceAsStream("/application-prod.properties")) {
      assertThat(entrada).as("application-prod.properties no classpath").isNotNull();
      Properties propriedades = new Properties();
      propriedades.load(entrada);
      return propriedades;
    }
  }
}
