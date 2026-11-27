package bipotheking.net.duora.conta;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereço público do site ({@code duora.url-publica}), base dos links que vão por e-mail. Valor
 * ausente, com placeholder sem resolver ou fora da forma de URL absoluta derruba o boot: o Binder
 * deixa passar {@code ${...}} literal, e todo link sairia quebrado sem aviso.
 *
 * @param urlPublica {@code http} ou {@code https}, com host, sem barra final, consulta nem
 *     fragmento
 */
@ConfigurationProperties("duora")
record SitePublico(String urlPublica) {

  private static final String PROPRIEDADE = "duora.url-publica";
  private static final Set<String> ESQUEMAS = Set.of("http", "https");

  SitePublico {
    exigirUrlAbsoluta(urlPublica);
  }

  /** O endereço público seguido do caminho, que começa por {@code /}. */
  String endereco(String caminho) {
    return urlPublica + caminho;
  }

  private static void exigirUrlAbsoluta(@Nullable String valor) {
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
        || !ESQUEMAS.contains(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || valor.endsWith("/")) {
      throw new IllegalArgumentException(
          PROPRIEDADE
              + " precisa ser uma URL http(s) absoluta, sem barra final, consulta nem fragmento");
    }
  }
}
