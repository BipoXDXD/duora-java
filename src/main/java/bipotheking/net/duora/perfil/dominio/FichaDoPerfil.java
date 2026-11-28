package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CelulaGeohash;
import java.util.Objects;
import java.util.Optional;

/**
 * Tudo o que o perfil de uma conta guarda, lido de uma vez: a base das projeções por audiência. Sem
 * ajustes gravados, {@code ajustes} é {@link AjustesDeBusca#PADRAO}. O {@code toString} não carrega
 * namoro, orientação nem célula, para nenhum log levar dado sensível.
 */
public record FichaDoPerfil(
    Perfil perfil,
    AjustesDeBusca ajustes,
    Optional<DadosDeNamoro> namoro,
    Optional<DadosDeOrientacao> orientacao,
    Optional<CelulaGeohash> celula,
    boolean pausada) {

  public FichaDoPerfil {
    Objects.requireNonNull(perfil, "perfil");
    Objects.requireNonNull(ajustes, "ajustes");
    Objects.requireNonNull(namoro, "namoro");
    Objects.requireNonNull(orientacao, "orientacao");
    Objects.requireNonNull(celula, "celula");
  }

  @Override
  public String toString() {
    return "FichaDoPerfil[conta=%s, pausada=%s, ***]".formatted(perfil.conta(), pausada);
  }
}
