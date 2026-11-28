package bipotheking.net.duora.perfil;

import bipotheking.net.duora.perfil.dominio.Coordenada;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Área de cerca de 5 km onde a conta está: o geohash de 5 caracteres da coordenada. É o único dado
 * de localização que o Duora guarda; a distância entre duas contas sai dos centros das células.
 */
public record CelulaGeohash(String valor) {

  private static final String ALFABETO = "0123456789bcdefghjkmnpqrstuvwxyz";
  private static final Pattern FORMATO = Pattern.compile("^[0-9b-hjkmnp-z]{5}$");
  private static final int CARACTERES = 5;
  private static final int BITS_POR_CARACTERE = 5;
  private static final double RAIO_DA_TERRA_KM = 6371.0088;

  public CelulaGeohash {
    Objects.requireNonNull(valor, "valor");
    if (!FORMATO.matcher(valor).matches()) {
      throw new IllegalArgumentException("Célula geohash fora do formato");
    }
  }

  /**
   * A célula que contém o ponto. O ponto em si não sai daqui.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code COORDENADA_INVALIDA} se a coordenada
   *     não é finita ou cai fora do globo
   */
  public static CelulaGeohash de(double latitude, double longitude) {
    Coordenada coordenada = Coordenada.de(latitude, longitude);
    double[] lat = {-90, 90};
    double[] lon = {-180, 180};
    StringBuilder celula = new StringBuilder(CARACTERES);
    boolean bitDeLongitude = true;
    for (int i = 0; i < CARACTERES; i++) {
      int indice = 0;
      for (int bit = 0; bit < BITS_POR_CARACTERE; bit++) {
        double[] intervalo = bitDeLongitude ? lon : lat;
        double valor = bitDeLongitude ? coordenada.longitude() : coordenada.latitude();
        double meio = (intervalo[0] + intervalo[1]) / 2;
        indice <<= 1;
        if (valor >= meio) {
          indice |= 1;
          intervalo[0] = meio;
        } else {
          intervalo[1] = meio;
        }
        bitDeLongitude = !bitDeLongitude;
      }
      celula.append(ALFABETO.charAt(indice));
    }
    return new CelulaGeohash(celula.toString());
  }

  /** Haversine entre os centros das duas células, em km. */
  public double distanciaKm(CelulaGeohash outra) {
    double[] daqui = centro();
    double[] dali = outra.centro();
    double lat1 = Math.toRadians(daqui[0]);
    double lat2 = Math.toRadians(dali[0]);
    double deltaLat = lat2 - lat1;
    double deltaLon = Math.toRadians(dali[1] - daqui[1]);
    double a =
        Math.pow(Math.sin(deltaLat / 2), 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
    return 2 * RAIO_DA_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  public FaixaDeDistancia faixaAte(CelulaGeohash outra) {
    return FaixaDeDistancia.de(distanciaKm(outra));
  }

  /** Latitude e longitude do centro da célula. */
  private double[] centro() {
    double[] lat = {-90, 90};
    double[] lon = {-180, 180};
    boolean bitDeLongitude = true;
    for (int i = 0; i < valor.length(); i++) {
      int indice = ALFABETO.indexOf(valor.charAt(i));
      for (int bit = BITS_POR_CARACTERE - 1; bit >= 0; bit--) {
        double[] intervalo = bitDeLongitude ? lon : lat;
        double meio = (intervalo[0] + intervalo[1]) / 2;
        if ((indice >> bit & 1) == 1) {
          intervalo[0] = meio;
        } else {
          intervalo[1] = meio;
        }
        bitDeLongitude = !bitDeLongitude;
      }
    }
    return new double[] {(lat[0] + lat[1]) / 2, (lon[0] + lon[1]) / 2};
  }
}
