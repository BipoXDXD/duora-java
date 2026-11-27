package bipotheking.net.duora.nucleo;

/**
 * Forma do e-mail que pode ir para log e mensagem de erro: a primeira letra do usuário e o domínio
 * ({@code ana@exemplo.com} vira {@code a***@exemplo.com}). Função pura.
 */
public final class EmailMascarado {

  private static final String MASCARA = "***";

  private EmailMascarado() {}

  /** Sem {@code @}, não há domínio a manter: devolve só a máscara. */
  public static String mascarar(String email) {
    int arroba = email.lastIndexOf('@');
    if (arroba < 0) {
      return MASCARA;
    }
    String inicio = arroba == 0 ? "" : email.substring(0, email.offsetByCodePoints(0, 1));
    return inicio + MASCARA + email.substring(arroba);
  }
}
