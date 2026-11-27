package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.CodigoEmail;
import bipotheking.net.duora.nucleo.EmailMascarado;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * E-mail já normalizado: sem espaços nas pontas e em minúsculas. O construtor recusa um valor fora
 * da forma normalizada; {@link #de(String)} normaliza antes. O {@code toString} mascara o endereço,
 * que assim não vaza para log nem mensagem de exceção.
 */
public record Email(String valor) {

  private static final int MAXIMO_TOTAL = 254;
  private static final int MAXIMO_PARTE_LOCAL = 64;
  private static final Pattern ROTULO = Pattern.compile("[a-z0-9-]+");
  private static final Pattern ESPACO_OU_CONTROLE = Pattern.compile("[\\s\\p{Z}\\p{Cc}\\p{Cf}]");
  private static final Set<String> DOMINIOS_DO_GMAIL = Set.of("gmail.com", "googlemail.com");
  private static final String MENSAGEM = "Confira o e-mail digitado.";

  public Email {
    if (!valido(valor)) {
      throw new ErroDeNegocio(CodigoEmail.EMAIL_INVALIDO, MENSAGEM);
    }
  }

  /** Normaliza (strip e minúsculas) e valida o e-mail digitado. */
  public static Email de(@Nullable String bruto) {
    if (bruto == null) {
      throw new ErroDeNegocio(CodigoEmail.EMAIL_INVALIDO, MENSAGEM);
    }
    return new Email(bruto.strip().toLowerCase(Locale.ROOT));
  }

  public String dominio() {
    return valor.substring(valor.indexOf('@') + 1);
  }

  /**
   * Forma que identifica a mesma caixa de entrada: sem o {@code +tag} e, no Gmail, sem os pontos da
   * parte local e com {@code gmail.com} no lugar de {@code googlemail.com}.
   */
  public String chaveDeBloqueio() {
    String local = valor.substring(0, valor.indexOf('@'));
    String dominio = dominio();
    int mais = local.indexOf('+');
    if (mais > 0) {
      local = local.substring(0, mais);
    }
    if (DOMINIOS_DO_GMAIL.contains(dominio)) {
      local = local.replace(".", "");
      dominio = "gmail.com";
    }
    return local + "@" + dominio;
  }

  @Override
  public String toString() {
    return "Email[" + EmailMascarado.mascarar(valor) + "]";
  }

  private static boolean valido(String valor) {
    if (valor.length() > MAXIMO_TOTAL
        || !valor.equals(valor.strip().toLowerCase(Locale.ROOT))
        || ESPACO_OU_CONTROLE.matcher(valor).find()) {
      return false;
    }
    int arroba = valor.indexOf('@');
    if (arroba < 1 || arroba > MAXIMO_PARTE_LOCAL || arroba != valor.lastIndexOf('@')) {
      return false;
    }
    return dominioValido(valor.substring(arroba + 1));
  }

  private static boolean dominioValido(String dominio) {
    String[] rotulos = dominio.split("\\.", -1);
    if (rotulos.length < 2) {
      return false;
    }
    for (String rotulo : rotulos) {
      if (!ROTULO.matcher(rotulo).matches()) {
        return false;
      }
    }
    return true;
  }
}
