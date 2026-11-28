package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Raio;
import java.util.Objects;

/**
 * Faixa etária e raio da busca. Com {@link Raio#SEM_LIMITE} não há raio a cumprir, então {@code
 * raioObrigatorio} vira falso.
 */
public record AjustesDeBusca(FaixaEtaria faixa, Raio raio, boolean raioObrigatorio) {

  public static final AjustesDeBusca PADRAO =
      new AjustesDeBusca(FaixaEtaria.TODAS, Raio.SEM_LIMITE, false);

  public AjustesDeBusca {
    Objects.requireNonNull(faixa, "faixa");
    Objects.requireNonNull(raio, "raio");
    raioObrigatorio = raioObrigatorio && raio != Raio.SEM_LIMITE;
  }
}
