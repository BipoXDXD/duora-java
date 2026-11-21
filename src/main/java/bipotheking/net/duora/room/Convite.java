package bipotheking.net.duora.room;

import java.util.Objects;

/** Convite de uso único. O {@code toString} não revela o valor, para nunca parar num log. */
record Convite(String valor) {

  Convite {
    Objects.requireNonNull(valor);
  }

  static Convite novo() {
    return new Convite(Segredos.novoToken(Segredos.BYTES_CONVITE));
  }

  String resumo() {
    return Segredos.resumo(valor);
  }

  @Override
  public String toString() {
    return "Convite[***]";
  }
}
