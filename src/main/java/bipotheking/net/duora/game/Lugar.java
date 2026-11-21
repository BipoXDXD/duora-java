package bipotheking.net.duora.game;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Os seis lugares do terraço. */
public enum Lugar {
  C1(Zona.CENTRO),
  C2(Zona.CENTRO),
  L1(Zona.LATERAL),
  L2(Zona.LATERAL),
  R1(Zona.LATERAL),
  R2(Zona.LATERAL);

  private final Zona zona;

  Lugar(Zona zona) {
    this.zona = zona;
  }

  public Zona zona() {
    return zona;
  }

  public String id() {
    return name();
  }

  public static Optional<Lugar> deId(String id) {
    return Arrays.stream(values()).filter(lugar -> lugar.name().equals(id)).findFirst();
  }

  public static List<Lugar> da(Zona zona) {
    return Arrays.stream(values()).filter(lugar -> lugar.zona == zona).toList();
  }
}
