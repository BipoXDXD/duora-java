package bipotheking.net.duora.perfil.dominio;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Os ajustes com a versão gravada; versão nula quando a conta ainda não salvou nenhum. */
public record AjustesVersionados(AjustesDeBusca ajustes, @Nullable Long versao) {

  public AjustesVersionados {
    Objects.requireNonNull(ajustes, "ajustes");
  }
}
