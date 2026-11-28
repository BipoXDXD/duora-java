package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Texto livre do perfil: até 280 code points, sem espaços nas pontas. Aceita quebra de linha
 * ({@code \n}); qualquer outro caractere de controle é recusado.
 */
public record Bio(String valor) {

  public static final Bio VAZIA = new Bio("");

  private static final int MAXIMO = 280;

  public Bio {
    Objects.requireNonNull(valor, "valor");
    if (!valida(valor)) {
      throw new IllegalArgumentException("Bio fora da forma normalizada");
    }
  }

  /**
   * Ausente vira vazia; {@code \r\n} vira {@code \n} e as pontas perdem os espaços.
   *
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code BIO_INVALIDA} acima de 280 code
   *     points ou com outro caractere de controle
   */
  public static Bio de(@Nullable String bruto) {
    if (bruto == null) {
      return VAZIA;
    }
    String valor = bruto.replace("\r\n", "\n").strip();
    if (!valida(valor)) {
      throw CodigoPerfil.BIO_INVALIDA.erro();
    }
    return new Bio(valor);
  }

  private static boolean valida(String valor) {
    return valor.codePointCount(0, valor.length()) <= MAXIMO
        && valor.equals(valor.strip())
        && valor.codePoints().noneMatch(c -> c != '\n' && Character.isISOControl(c));
  }
}
