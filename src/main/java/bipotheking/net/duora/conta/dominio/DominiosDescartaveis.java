package bipotheking.net.duora.conta.dominio;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Domínios de e-mail descartável. Um subdomínio de um domínio da lista também conta. */
public final class DominiosDescartaveis {

  private final Set<String> dominios;

  /** Recebe as linhas da lista; ignora linhas vazias e comentários com {@code #}. */
  public DominiosDescartaveis(Collection<String> linhas) {
    this.dominios =
        linhas.stream()
            .map(linha -> linha.strip().toLowerCase(Locale.ROOT))
            .filter(linha -> !linha.isEmpty() && !linha.startsWith("#"))
            .collect(Collectors.toUnmodifiableSet());
  }

  public boolean contem(String dominio) {
    String sufixo = dominio;
    while (!sufixo.isEmpty()) {
      if (dominios.contains(sufixo)) {
        return true;
      }
      int ponto = sufixo.indexOf('.');
      sufixo = ponto < 0 ? "" : sufixo.substring(ponto + 1);
    }
    return false;
  }
}
