package bipotheking.net.duora.game;

import org.junit.jupiter.params.converter.TypedArgumentConverter;

/** Converte o id de um lugar do terraço no {@code @CsvSource} (ex. {@code "C1"}). */
final class LugarDoTerraco extends TypedArgumentConverter<String, Lugar> {

  LugarDoTerraco() {
    super(String.class, Lugar.class);
  }

  @Override
  protected Lugar convert(String texto) {
    return Terraco.CENARIO.lugar(texto).orElseThrow();
  }
}
