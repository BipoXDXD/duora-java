package bipotheking.net.duora.push;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Allowlist do endpoint de push contra SSRF: o servidor só fala com os serviços de push conhecidos.
 * Exige {@code https}, recusa userinfo, porta explícita e host literal (IPv4, IPv6 ou decimal), e
 * aceita só um host permitido ou um subdomínio dele (com o ponto antes).
 */
public final class EnderecoDePush {

  static final int TAMANHO_MAXIMO = 1024;

  /** Só dígitos e pontos: IPv4 ({@code 10.0.0.1}) ou inteiro ({@code 2130706433}). */
  private static final Pattern NUMERICO = Pattern.compile("[0-9.]+");

  private EnderecoDePush() {}

  /**
   * O endpoint como URI, se passar na allowlist.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@link CodigoPush#INSCRICAO_INVALIDA}
   */
  public static URI validar(@Nullable String bruto, Set<String> hostsPermitidos) {
    if (bruto == null || bruto.length() > TAMANHO_MAXIMO) {
      throw CodigoPush.INSCRICAO_INVALIDA.erro();
    }
    URI uri;
    try {
      uri = new URI(bruto);
    } catch (URISyntaxException e) {
      throw CodigoPush.INSCRICAO_INVALIDA.erro();
    }
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getRawUserInfo() != null
        || uri.getPort() != -1
        || host == null) {
      throw CodigoPush.INSCRICAO_INVALIDA.erro();
    }
    String normalizado = host.toLowerCase(Locale.ROOT);
    if (literal(normalizado) || !permitido(normalizado, hostsPermitidos)) {
      throw CodigoPush.INSCRICAO_INVALIDA.erro();
    }
    return uri;
  }

  private static boolean literal(String host) {
    return host.startsWith("[") || host.contains(":") || NUMERICO.matcher(host).matches();
  }

  private static boolean permitido(String host, Set<String> hostsPermitidos) {
    for (String permitido : hostsPermitidos) {
      String base = permitido.toLowerCase(Locale.ROOT);
      if (host.equals(base) || host.endsWith("." + base)) {
        return true;
      }
    }
    return false;
  }
}
