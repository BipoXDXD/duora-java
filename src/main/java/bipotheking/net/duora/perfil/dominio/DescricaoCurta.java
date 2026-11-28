package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import java.util.Objects;
import java.util.Set;

/**
 * Texto curto digitado pela pessoa (apelido, "Prefiro descrever"): sem espaços nas pontas, de 1 ao
 * máximo de code points, sem caracteres de controle nem marcas que embaralham a direção do texto ao
 * redor. É a única fonte dessa regra no perfil.
 */
public record DescricaoCurta(String valor) {

  /** Marcas de direção e separadores de linha e parágrafo, fora das faixas abaixo. */
  private static final Set<Integer> FORMATACAO_PROIBIDA =
      Set.of(0x200E, 0x200F, 0x061C, 0x2028, 0x2029);

  public DescricaoCurta {
    Objects.requireNonNull(valor, "valor");
    if (!cabe(valor, Integer.MAX_VALUE)) {
      throw new IllegalArgumentException("Descrição fora da forma normalizada");
    }
  }

  /**
   * Tira os espaços das pontas e valida.
   *
   * @throws ErroDeNegocio com o {@code codigo} passado, se ficar vazia, passar do máximo ou tiver
   *     caractere proibido
   */
  public static DescricaoCurta de(String bruto, int maximo, CodigoDeErro codigo) {
    String valor = bruto.strip();
    if (!cabe(valor, maximo)) {
      throw new ErroDeNegocio(codigo, mensagemDe(codigo));
    }
    return new DescricaoCurta(valor);
  }

  /** O valor já normalizado tem de 1 ao máximo de code points e nenhum caractere proibido. */
  static boolean cabe(String valor, int maximo) {
    int tamanho = valor.codePointCount(0, valor.length());
    return tamanho >= 1
        && tamanho <= maximo
        && valor.equals(valor.strip())
        && valor.codePoints().noneMatch(DescricaoCurta::proibido);
  }

  private static boolean proibido(int codePoint) {
    return Character.isISOControl(codePoint)
        || FORMATACAO_PROIBIDA.contains(codePoint)
        || (codePoint >= 0x202A && codePoint <= 0x202E)
        || (codePoint >= 0x2066 && codePoint <= 0x2069);
  }

  /** {@link CodigoDeErro} não traz mensagem; os códigos do perfil trazem a do contrato. */
  private static String mensagemDe(CodigoDeErro codigo) {
    return codigo instanceof CodigoPerfil perfil ? perfil.mensagem() : codigo.titulo();
  }
}
