package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Texto livre do perfil: até 280 code points, sem espaços nas pontas. Segue a regra de caracteres
 * da {@link DescricaoCurta}, mas aceita a quebra de linha ({@code \n}) e pode ficar vazia.
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
   *     points ou com caractere proibido
   */
  public static Bio de(@Nullable String bruto) {
    if (bruto == null) {
      return VAZIA;
    }
    String valor = DescricaoCurta.semEspacosNasPontas(bruto.replace("\r\n", "\n"));
    if (!valida(valor)) {
      throw CodigoPerfil.BIO_INVALIDA.erro();
    }
    return new Bio(valor);
  }

  private static boolean valida(String valor) {
    return valor.codePointCount(0, valor.length()) <= MAXIMO
        && valor.equals(DescricaoCurta.semEspacosNasPontas(valor))
        && valor.codePoints().noneMatch(c -> c != '\n' && DescricaoCurta.proibido(c));
  }
}
