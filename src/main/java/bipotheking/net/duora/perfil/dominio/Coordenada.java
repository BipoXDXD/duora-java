package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoLocalizacao;

/**
 * Ponto exato enviado pelo aparelho. Vive só durante o pedido: vira {@link
 * bipotheking.net.duora.perfil.CelulaGeohash} e é descartado, nunca gravado, logado nem devolvido.
 * Por isso o {@link #toString()} esconde os números.
 */
public record Coordenada(double latitude, double longitude) {

  private static final double LATITUDE_MAXIMA = 90;
  private static final double LONGITUDE_MAXIMA = 180;

  public Coordenada {
    if (!valida(latitude, LATITUDE_MAXIMA) || !valida(longitude, LONGITUDE_MAXIMA)) {
      throw CodigoLocalizacao.COORDENADA_INVALIDA.erro();
    }
  }

  /**
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code COORDENADA_INVALIDA} se algum dos
   *     números não é finito ou cai fora de [-90, 90] (latitude) ou [-180, 180] (longitude)
   */
  public static Coordenada de(double latitude, double longitude) {
    return new Coordenada(latitude, longitude);
  }

  /** {@code NaN} falha nas duas comparações; infinito, na primeira ou na segunda. */
  private static boolean valida(double valor, double maximo) {
    return valor >= -maximo && valor <= maximo;
  }

  @Override
  public String toString() {
    return "Coordenada[***]";
  }
}
