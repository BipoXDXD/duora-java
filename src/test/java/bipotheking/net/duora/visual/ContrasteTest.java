package bipotheking.net.duora.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Contraste WCAG 2.x dos pares de cor em uso, lidos dos tokens de {@code static/css/app.css}. Texto
 * normal exige 4,5:1; o anel de foco (componente de interface) exige 3:1.
 */
class ContrasteTest {

  private static final String CSS = "static/css/app.css";
  private static final Pattern TOKEN =
      Pattern.compile("--([a-z0-9-]+)\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;");

  private static Map<String, String> tokens;

  @BeforeAll
  static void lerTokens() throws IOException {
    String css;
    try (InputStream entrada = ContrasteTest.class.getClassLoader().getResourceAsStream(CSS)) {
      if (entrada == null) {
        fail("Arquivo " + CSS + " não está no classpath.");
      }
      css = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    }
    tokens = new HashMap<>();
    Matcher m = TOKEN.matcher(css);
    while (m.find()) {
      tokens.putIfAbsent(m.group(1), m.group(2));
    }
  }

  @ParameterizedTest(name = "{0} sobre {1} >= {2}:1")
  @CsvSource({
    "marfim, noite-800, 4.5",
    "marfim, noite-700, 4.5",
    "marfim, noite-900, 4.5",
    "marfim, noite-950, 4.5",
    "texto-2, noite-800, 4.5",
    "texto-2, noite-700, 4.5",
    "texto-2, noite-900, 4.5",
    "texto-2, noite-950, 4.5",
    "texto-3, noite-800, 4.5",
    "texto-3, noite-700, 4.5",
    "texto-3, noite-900, 4.5",
    "texto-3, noite-950, 4.5",
    "ambar-tinta, ambar, 4.5",
    "verde-agua, noite-800, 4.5",
    "coral, noite-800, 4.5",
    "noite-950, coral, 4.5",
    "ambar, noite-900, 3.0",
  })
  void parDeCoresAtingeOContrasteMinimo(String frente, String fundo, double minimo) {
    double razao = razaoDeContraste(cor(frente), cor(fundo));

    assertTrue(
        razao >= minimo,
        () ->
            String.format(
                "--%s sobre --%s tem contraste %.2f:1; o mínimo é %.1f:1.",
                frente, fundo, razao, minimo));
  }

  /** Sanidade da fórmula contra valores de referência do WCAG (independe do CSS). */
  @ParameterizedTest
  @CsvSource({
    "#000000, #ffffff, 21.0",
    "#ffffff, #ffffff, 1.0",
    "#777777, #ffffff, 4.48",
  })
  void formulaReproduzValoresDeReferencia(String frente, String fundo, double esperado) {
    assertEquals(esperado, razaoDeContraste(frente, fundo), 0.01);
  }

  private static String cor(String nome) {
    String hex = tokens.get(nome);
    if (hex == null) {
      fail("O token --" + nome + " não foi encontrado como cor #rrggbb em " + CSS + ".");
    }
    return hex;
  }

  private static double razaoDeContraste(String a, String b) {
    double la = luminancia(a);
    double lb = luminancia(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  }

  /** Luminância relativa (WCAG 2.x) de uma cor #rrggbb em sRGB. */
  private static double luminancia(String hex) {
    int rgb = Integer.parseInt(hex.substring(1), 16);
    double r = linear((rgb >> 16) & 0xff);
    double g = linear((rgb >> 8) & 0xff);
    double b = linear(rgb & 0xff);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }

  private static double linear(int canal) {
    double c = canal / 255.0;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }
}
