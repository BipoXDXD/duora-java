package bipotheking.net.duora.room;

import java.util.Set;

/** Nome que a pessoa escolhe: 1 a 20 caracteres após o trim, sem caracteres de controle. */
record Apelido(String valor) {

  private static final int MAXIMO = 20;

  /** Converte a entrada do cliente; recusa com {@code APELIDO_INVALIDO}. */
  static Apelido de(String bruto) {
    if (bruto == null) {
      throw invalido();
    }
    String valor = bruto.strip();
    int tamanho = valor.codePointCount(0, valor.length());
    if (tamanho == 0 || tamanho > MAXIMO || valor.codePoints().anyMatch(Apelido::proibido)) {
      throw invalido();
    }
    return new Apelido(valor);
  }

  /** Marcas de direção e separadores de linha/parágrafo, que embaralham o texto ao redor. */
  private static final Set<Integer> FORMATACAO_PROIBIDA =
      Set.of(0x200E, 0x200F, 0x061C, 0x2028, 0x2029);

  private static boolean proibido(int codePoint) {
    return Character.isISOControl(codePoint)
        || FORMATACAO_PROIBIDA.contains(codePoint)
        || (codePoint >= 0x202A && codePoint <= 0x202E)
        || (codePoint >= 0x2066 && codePoint <= 0x2069);
  }

  private static ErroApi invalido() {
    return new ErroApi(Codigo.APELIDO_INVALIDO, "O apelido precisa ter de 1 a 20 caracteres.");
  }
}
