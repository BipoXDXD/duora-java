package bipotheking.net.duora.game;

import java.util.Locale;

/** Lado do céu para onde o telescópio aponta na revelação. */
public enum Lado {
  ESQUERDO,
  DIREITO;

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}
