package bipotheking.net.duora.room;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Allowlist de nomes de loopback aceitos em {@code Host} e {@code Origin}. Barra DNS rebinding: um
 * domínio estrangeiro que resolva para 127.0.0.1 chega com o próprio nome no {@code Host}.
 */
final class EnderecoLocal {

  private static final Set<String> NOMES = Set.of("127.0.0.1", "localhost", "[::1]", "::1");
  private static final int PORTA_HTTP = 80;
  private static final int PORTA_HTTPS = 443;

  private EnderecoLocal() {}

  static boolean permitido(String nome, int porta, int portaLocal) {
    return nome != null && NOMES.contains(nome.toLowerCase(Locale.ROOT)) && porta == portaLocal;
  }

  /** {@code Origin} serializada pelo navegador, como {@code http://localhost:8080}. */
  static boolean origemPermitida(String origem, int portaLocal) {
    URI uri;
    try {
      uri = new URI(origem);
    } catch (java.net.URISyntaxException e) {
      // Origin malformada é tratada como estrangeira: o pedido é recusado com 403.
      return false;
    }
    String esquema = uri.getScheme();
    if (!"http".equals(esquema) && !"https".equals(esquema)) {
      return false;
    }
    int porta = uri.getPort() != -1 ? uri.getPort() : portaPadrao(esquema);
    return permitido(uri.getHost(), porta, portaLocal);
  }

  private static int portaPadrao(String esquema) {
    return "https".equals(esquema) ? PORTA_HTTPS : PORTA_HTTP;
  }
}
