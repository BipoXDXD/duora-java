package bipotheking.net.duora.nucleo;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Forma do e-mail que pode ir para log e mensagem de erro: a primeira letra do usuário e o domínio
 * ({@code ana@exemplo.com} vira {@code a***@exemplo.com}). Função pura e total: roda em caminho de
 * erro, onde uma exceção esconderia o erro original.
 */
public final class EmailMascarado {

  private static final String MASCARA = "***";
  private static final Pattern QUEBRA_DE_LINHA = Pattern.compile("[\\r\\n]");

  private EmailMascarado() {}

  /**
   * Nulo ou sem {@code @} devolve só a máscara. Quebras de linha viram {@code _}: o resultado vai
   * para o log, onde forjariam linhas.
   */
  public static String mascarar(@Nullable String email) {
    if (email == null) {
      return MASCARA;
    }
    int arroba = email.lastIndexOf('@');
    if (arroba < 0) {
      return MASCARA;
    }
    String inicio = arroba == 0 ? "" : email.substring(0, email.offsetByCodePoints(0, 1));
    return QUEBRA_DE_LINHA.matcher(inicio + MASCARA + email.substring(arroba)).replaceAll("_");
  }
}
