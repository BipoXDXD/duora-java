package bipotheking.net.duora.perfil;

import java.util.OptionalInt;

/** Até onde procurar companhia. Qualquer raio menos {@link #SEM_LIMITE} exige a localização. */
public enum Raio {
  ATE_5_KM(5),
  ATE_20_KM(20),
  ATE_50_KM(50),
  SEM_LIMITE(0);

  private final int km;

  Raio(int km) {
    this.km = km;
  }

  /** O raio em quilômetros; vazio em {@link #SEM_LIMITE}. */
  public OptionalInt km() {
    return this == SEM_LIMITE ? OptionalInt.empty() : OptionalInt.of(km);
  }
}
