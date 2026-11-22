package bipotheking.net.duora.game;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Um lugar de um cenário, dentro de uma zona. */
public record Lugar(String id, Zona zona) {

  /** Fachada provisória dos seis lugares do terraço: some quando o cenário fornecer os lugares. */
  public static final Lugar C1 = new Lugar("C1", Zona.CENTRO);

  public static final Lugar C2 = new Lugar("C2", Zona.CENTRO);
  public static final Lugar L1 = new Lugar("L1", Zona.LATERAL);
  public static final Lugar L2 = new Lugar("L2", Zona.LATERAL);
  public static final Lugar R1 = new Lugar("R1", Zona.LATERAL);
  public static final Lugar R2 = new Lugar("R2", Zona.LATERAL);

  private static final Lugar[] TERRACO = {C1, C2, L1, L2, R1, R2};

  public Lugar {
    Objects.requireNonNull(id);
    Objects.requireNonNull(zona);
    if (id.isBlank()) {
      throw new IllegalArgumentException("id de lugar vazio");
    }
  }

  public static Lugar[] values() {
    return TERRACO.clone();
  }

  public static Optional<Lugar> deId(String id) {
    return Arrays.stream(TERRACO).filter(lugar -> lugar.id.equals(id)).findFirst();
  }

  public static List<Lugar> da(Zona zona) {
    return Arrays.stream(TERRACO).filter(lugar -> lugar.zona.equals(zona)).toList();
  }

  /** Lugar do terraço pelo nome da antiga constante (igual ao id), como no {@code Enum.valueOf}. */
  public static Lugar valueOf(String nome) {
    return deId(nome)
        .orElseThrow(() -> new IllegalArgumentException("lugar desconhecido: " + nome));
  }
}
