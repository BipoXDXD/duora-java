package bipotheking.net.duora.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Endereço público do site ({@code duora.url-publica}), o único nome além do loopback que {@link
 * HostLocal} e {@link MesmaOrigem} aceitam. Em produção o Traefik repassa o {@code Host} e o {@code
 * Origin} do navegador sem reescrever; o TLS termina nele, então a porta local (8080) nunca bate
 * com a da URL.
 *
 * <p>As duas comparações são por igualdade exata, nunca por prefixo ou sufixo: {@code
 * meetduora.com.evil.com}, {@code evilmeetduora.com}, subdomínios e {@code null} ficam de fora. URL
 * ausente, com placeholder sem resolver ou fora da forma {@code http(s)://host[:porta]} derruba o
 * boot.
 */
@Component
class EnderecoPublico {

  private static final String PROPRIEDADE = "duora.url-publica";
  private static final Set<String> ESQUEMAS = Set.of("http", "https");
  private static final int PORTA_HTTP = 80;
  private static final int PORTA_HTTPS = 443;

  /** {@code Host} sem porta, que o navegador manda quando a porta é a padrão do esquema. */
  private final @Nullable String hostSemPorta;

  /** {@code Host} com a porta efetiva explícita. */
  private final String hostComPorta;

  /** Origem serializada como o navegador a manda: a porta só aparece se não for a padrão. */
  private final String origem;

  EnderecoPublico(@Value("${" + PROPRIEDADE + "}") @Nullable String url) {
    URI uri = exigirUrlAbsoluta(url);
    String esquema = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    int portaPadrao = "https".equals(esquema) ? PORTA_HTTPS : PORTA_HTTP;
    int porta = uri.getPort() == -1 ? portaPadrao : uri.getPort();
    boolean portaPadraoEfetiva = porta == portaPadrao;
    this.hostSemPorta = portaPadraoEfetiva ? host : null;
    this.hostComPorta = host + ":" + porta;
    this.origem = esquema + "://" + (portaPadraoEfetiva ? host : hostComPorta);
  }

  /**
   * O {@code Host} cru da requisição é o host público, sem porta ou com a porta efetiva da URL. Sem
   * porta, vale a padrão do esquema da URL: {@code meetduora.com} e {@code meetduora.com:443}
   * passam para {@code https://meetduora.com}; {@code meetduora.com:8080} não.
   */
  boolean hostPermitido(@Nullable String cabecalhoHost) {
    if (cabecalhoHost == null) {
      return false;
    }
    String host = cabecalhoHost.toLowerCase(Locale.ROOT);
    return host.equals(hostComPorta) || host.equals(hostSemPorta);
  }

  /** {@code Origin} igual à origem da URL pública: esquema, host e porta efetiva. */
  boolean origemPermitida(String cabecalhoOrigin) {
    return origem.equalsIgnoreCase(cabecalhoOrigin);
  }

  private static URI exigirUrlAbsoluta(@Nullable String valor) {
    if (valor == null || valor.isBlank() || valor.contains("${")) {
      throw new IllegalArgumentException(PROPRIEDADE + " ausente ou com placeholder sem resolver");
    }
    URI uri;
    try {
      uri = new URI(valor);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(PROPRIEDADE + " não é uma URL válida", e);
    }
    if (uri.getScheme() == null
        || !ESQUEMAS.contains(uri.getScheme().toLowerCase(Locale.ROOT))
        || uri.getHost() == null
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new IllegalArgumentException(
          PROPRIEDADE
              + " precisa ser uma URL http(s) absoluta, sem usuário, consulta nem fragmento");
    }
    return uri;
  }
}
