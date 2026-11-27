package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.CodigoDenuncia;
import bipotheking.net.duora.conta.CodigoDenuncia.Campo;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Denúncia feita sem conta, já validada. O construtor recusa o que o banco recusaria: texto fora da
 * forma normalizada (sem espaços nas pontas, opcional nunca em branco), fora dos tamanhos ou com o
 * caractere nulo, que o Postgres não guarda em {@code text}; {@link #de} normaliza antes. Os
 * tamanhos contam caracteres (code points), como o {@code char_length} do banco. O {@code toString}
 * só mostra o motivo: a descrição, a referência e o e-mail de contato nunca vão para log nem
 * mensagem de exceção.
 *
 * @param referencia apelido ou link de quem é denunciado, se houver
 * @param emailContato para responder a quem denunciou, se houver
 * @throws ErroDeNegocio {@code DENUNCIA_PUBLICA_INVALIDA}, com a mensagem do primeiro campo
 *     recusado
 */
public record DenunciaPublica(
    MotivoDeDenunciaPublica motivo,
    String descricao,
    @Nullable String referencia,
    @Nullable Email emailContato) {

  private static final int DESCRICAO_MINIMA = 10;
  private static final int DESCRICAO_MAXIMA = 2000;
  private static final int REFERENCIA_MAXIMA = 200;
  private static final char NULO = '\0';

  public DenunciaPublica {
    Objects.requireNonNull(motivo, "motivo");
    if (!textoValido(descricao, DESCRICAO_MINIMA, DESCRICAO_MAXIMA)) {
      throw recusa(Campo.DESCRICAO);
    }
    if (referencia != null && !textoValido(referencia, 1, REFERENCIA_MAXIMA)) {
      throw recusa(Campo.REFERENCIA);
    }
  }

  /**
   * Valida o que chegou do formulário: sem espaços nas pontas, e opcional em branco vira ausente.
   *
   * @throws ErroDeNegocio {@code DENUNCIA_PUBLICA_INVALIDA}, com a mensagem de um campo recusado (o
   *     motivo e o e-mail são lidos antes da checagem da descrição e da referência)
   */
  public static DenunciaPublica de(
      @Nullable String motivo,
      @Nullable String descricao,
      @Nullable String referencia,
      @Nullable String emailContato) {
    return new DenunciaPublica(
        motivo(motivo),
        descricao == null ? "" : descricao.strip(),
        opcional(referencia),
        emailContato(emailContato));
  }

  @Override
  public String toString() {
    return "DenunciaPublica[motivo=" + motivo + "]";
  }

  private static MotivoDeDenunciaPublica motivo(@Nullable String bruto) {
    return Arrays.stream(MotivoDeDenunciaPublica.values())
        .filter(motivo -> motivo.name().equals(bruto))
        .findFirst()
        .orElseThrow(() -> recusa(Campo.MOTIVO));
  }

  private static @Nullable String opcional(@Nullable String bruto) {
    return bruto == null || bruto.isBlank() ? null : bruto.strip();
  }

  /** O {@code Email} já recusa caractere de controle, o nulo incluído. */
  private static @Nullable Email emailContato(@Nullable String bruto) {
    String email = opcional(bruto);
    if (email == null) {
      return null;
    }
    try {
      return Email.de(email);
    } catch (ErroDeNegocio emailInvalido) {
      throw recusa(Campo.EMAIL_CONTATO);
    }
  }

  private static boolean textoValido(String texto, int minimo, int maximo) {
    int caracteres = texto.codePointCount(0, texto.length());
    return texto.equals(texto.strip())
        && texto.indexOf(NULO) < 0
        && caracteres >= minimo
        && caracteres <= maximo;
  }

  private static ErroDeNegocio recusa(Campo campo) {
    return CodigoDenuncia.DENUNCIA_PUBLICA_INVALIDA.erro(campo);
  }
}
