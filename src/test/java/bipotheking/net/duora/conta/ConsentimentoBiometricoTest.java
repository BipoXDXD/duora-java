package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * O aceite biométrico guarda o SHA-256 de {@code legal/biometria.md}, a única fonte do texto. A
 * caixa que a pessoa marca na verificação repete esse texto no front: os dois precisam ser iguais,
 * senão o aceite gravado apontaria para um texto que ninguém viu. A versão que o front manda ao
 * abrir a sessão também repete a do documento: com ela diferente, o servidor recusaria toda sessão.
 */
class ConsentimentoBiometricoTest {

  private static final String DOCUMENTO = "/legal/biometria.md";
  private static final Path TEXTOS_DO_FRONT =
      Path.of("frontend", "src", "telas", "verificacao", "textos.ts");
  private static final Pattern FRONT_MATTER =
      Pattern.compile("\\A---\\n.*?\\n---\\n", Pattern.DOTALL);
  private static final Pattern CONSENTIMENTO =
      Pattern.compile("^\\s*consentimento:\\s*\"([^\"]+)\",$", Pattern.MULTILINE);
  private static final Pattern VERSAO_NO_FRONT =
      Pattern.compile("^export const VERSAO_DO_CONSENTIMENTO = \"([^\"]+)\";$", Pattern.MULTILINE);

  @Test
  void caixaDoFrontTrazOTextoDoDocumento() throws IOException {
    Matcher noFront = CONSENTIMENTO.matcher(Files.readString(TEXTOS_DO_FRONT));

    assertThat(noFront.find()).as("chave consentimento em %s", TEXTOS_DO_FRONT).isTrue();
    assertThat(noFront.group(1)).isEqualTo(corpoDoDocumento());
  }

  @Test
  void versaoDoFrontEADoDocumento() throws IOException {
    Matcher noFront = VERSAO_NO_FRONT.matcher(Files.readString(TEXTOS_DO_FRONT));

    assertThat(noFront.find()).as("VERSAO_DO_CONSENTIMENTO em %s", TEXTOS_DO_FRONT).isTrue();
    assertThat(noFront.group(1)).isEqualTo(PedidosDeSessao.VERSAO_DO_CONSENTIMENTO);
  }

  /** O texto do documento sem o front matter: o que a pessoa lê ao lado da caixa. */
  private static String corpoDoDocumento() throws IOException {
    String documento;
    try (InputStream entrada = ConsentimentoBiometricoTest.class.getResourceAsStream(DOCUMENTO)) {
      documento =
          new String(Objects.requireNonNull(entrada).readAllBytes(), StandardCharsets.UTF_8);
    }
    return FRONT_MATTER.matcher(documento).replaceFirst("").strip();
  }
}
