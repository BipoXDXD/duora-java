package bipotheking.net.duora.game;

import java.util.Locale;
import org.junit.jupiter.params.converter.TypedArgumentConverter;

/** Converte o nome de uma peça do terraço no {@code @CsvSource} (ex. {@code "TELESCOPIO"}). */
final class PecaDoTerraco extends TypedArgumentConverter<String, Peca> {

  PecaDoTerraco() {
    super(String.class, Peca.class);
  }

  @Override
  protected Peca convert(String texto) {
    return Terraco.CENARIO.peca(texto.toLowerCase(Locale.ROOT)).orElseThrow();
  }
}
