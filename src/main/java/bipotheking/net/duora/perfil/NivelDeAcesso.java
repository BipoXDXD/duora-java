package bipotheking.net.duora.perfil;

import bipotheking.net.duora.web.Variante;

/** Quanto uma conta vê das fotos de outra, do menos para o mais: a ordem importa. */
public enum NivelDeAcesso {
  NENHUM,
  BORRADA,
  NITIDA;

  /** A borrada pede ao menos {@code BORRADA}; a nítida, {@code NITIDA}. */
  public boolean permite(Variante variante) {
    return switch (variante) {
      case BORRADA -> compareTo(BORRADA) >= 0;
      case NITIDA -> this == NITIDA;
    };
  }
}
