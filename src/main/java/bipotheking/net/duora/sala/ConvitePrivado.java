package bipotheking.net.duora.sala;

/**
 * Um encontro privado à espera do convidado. O {@code convite} é o token de uso único do link:
 * nunca vai para o log, e o {@link #toString()} o esconde.
 */
public record ConvitePrivado(String encontroId, String convite, String cenarioId) {

  @Override
  public String toString() {
    return "ConvitePrivado[encontroId="
        + encontroId
        + ", convite=***, cenarioId="
        + cenarioId
        + "]";
  }
}
