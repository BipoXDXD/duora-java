package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import java.util.Objects;
import java.util.Set;

/**
 * Texto curto digitado pela pessoa (apelido, "Prefiro descrever"): sem espaços nas pontas, de 1 ao
 * máximo de code points, com ao menos um caractere visível, sem caracteres de controle nem de
 * formatação invisível (a não ser o ZWJ das sequências de emoji). É a única fonte dessa regra no
 * perfil; o front repete a mesma em {@code frontend/src/telas/perfil/textoDigitado.ts}.
 */
public record DescricaoCurta(String valor) {

  private static final int ZWJ = 0x200D;

  private static final Set<Integer> QUEBRAS_E_TABULACOES = Set.of(0x09, 0x0A, 0x0B, 0x0C, 0x0D);

  /** Separadores de linha e parágrafo: no meio do texto, quebram a linha sem ser {@code \n}. */
  private static final Set<Integer> SEPARADORES_PROIBIDOS = Set.of(0x2028, 0x2029);

  /** Letras de preenchimento que a fonte desenha em branco. */
  private static final Set<Integer> PREENCHIMENTOS_INVISIVEIS =
      Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

  public DescricaoCurta {
    Objects.requireNonNull(valor, "valor");
    if (!cabe(valor, Integer.MAX_VALUE)) {
      throw new IllegalArgumentException("Descrição fora da forma normalizada");
    }
  }

  /**
   * Tira os espaços das pontas e valida.
   *
   * @throws ErroDeNegocio com o {@code codigo} passado, se ficar sem caractere visível, passar do
   *     máximo ou tiver caractere proibido
   */
  public static DescricaoCurta de(String bruto, int maximo, CodigoDeErro codigo) {
    String valor = semEspacosNasPontas(bruto);
    if (!cabe(valor, maximo)) {
      throw new ErroDeNegocio(codigo, mensagemDe(codigo));
    }
    return new DescricaoCurta(valor);
  }

  /** O valor já normalizado tem de 1 ao máximo de code points, um visível e nenhum proibido. */
  static boolean cabe(String valor, int maximo) {
    int tamanho = valor.codePointCount(0, valor.length());
    return tamanho >= 1
        && tamanho <= maximo
        && valor.equals(semEspacosNasPontas(valor))
        && valor.codePoints().noneMatch(DescricaoCurta::proibido)
        && valor.codePoints().anyMatch(DescricaoCurta::visivel);
  }

  /**
   * Tira das pontas todo espaço Unicode (inclusive o sem quebra e o ideográfico) e as quebras, como
   * o front.
   */
  static String semEspacosNasPontas(String texto) {
    int inicio = 0;
    int fim = texto.length();
    while (inicio < fim && espaco(texto.codePointAt(inicio))) {
      inicio += Character.charCount(texto.codePointAt(inicio));
    }
    while (fim > inicio && espaco(texto.codePointBefore(fim))) {
      fim -= Character.charCount(texto.codePointBefore(fim));
    }
    return texto.substring(inicio, fim);
  }

  /** Controle, formatação invisível (salvo o ZWJ) e separador de linha ou parágrafo. */
  static boolean proibido(int codePoint) {
    return Character.isISOControl(codePoint)
        || (Character.getType(codePoint) == Character.FORMAT && codePoint != ZWJ)
        || SEPARADORES_PROIBIDOS.contains(codePoint);
  }

  /** Espaço Unicode (Zs, Zl, Zp) e as quebras e tabulações ASCII, como o {@code \s} do front. */
  private static boolean espaco(int codePoint) {
    return Character.isSpaceChar(codePoint) || QUEBRAS_E_TABULACOES.contains(codePoint);
  }

  private static boolean visivel(int codePoint) {
    return !espaco(codePoint)
        && !proibido(codePoint)
        && codePoint != ZWJ
        && !PREENCHIMENTOS_INVISIVEIS.contains(codePoint);
  }

  /** {@link CodigoDeErro} não traz mensagem; os códigos do perfil trazem a do contrato. */
  private static String mensagemDe(CodigoDeErro codigo) {
    return codigo instanceof CodigoPerfil perfil ? perfil.mensagem() : codigo.titulo();
  }
}
