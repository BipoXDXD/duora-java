package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Nome que aparece no perfil: uma {@link DescricaoCurta} de até 20 code points. */
public record Apelido(String valor) {

  private static final int MAXIMO = 20;

  public Apelido {
    Objects.requireNonNull(valor, "valor");
    if (!DescricaoCurta.cabe(valor, MAXIMO)) {
      throw new IllegalArgumentException("Apelido fora da forma normalizada");
    }
  }

  /** Normaliza e valida o apelido digitado; ausente ou inválido vira {@code APELIDO_INVALIDO}. */
  public static Apelido de(@Nullable String bruto) {
    if (bruto == null) {
      throw CodigoPerfil.APELIDO_INVALIDO.erro();
    }
    return new Apelido(DescricaoCurta.de(bruto, MAXIMO, CodigoPerfil.APELIDO_INVALIDO).valor());
  }
}
