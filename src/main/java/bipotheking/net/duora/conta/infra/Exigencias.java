package bipotheking.net.duora.conta.infra;

import org.jspecify.annotations.Nullable;

/**
 * Regra das chaves obrigatórias dos fornecedores: nula, em branco ou com placeholder sem resolver é
 * recusada. O Binder deixa passar {@code ${...}} literal quando a variável de ambiente falta. Vale
 * para o Turnstile e para a AWS e a Didit.
 */
final class Exigencias {

  private Exigencias() {}

  /** A mensagem cita só a propriedade, nunca o valor (pode ser o segredo). */
  static String exigir(@Nullable String valor, String propriedade) {
    if (valor == null || valor.isBlank() || valor.startsWith("${")) {
      throw new IllegalArgumentException(propriedade + " ausente ou com placeholder sem resolver");
    }
    return valor;
  }
}
