package bipotheking.net.duora.sala;

/**
 * O ponto que o aparelho mandou ao entrar na fila. Só em trânsito: nunca é gravado nem logado, e o
 * {@code toString} esconde os números.
 */
public record Coordenadas(double latitude, double longitude) {

  @Override
  public String toString() {
    return "Coordenadas[***]";
  }
}
