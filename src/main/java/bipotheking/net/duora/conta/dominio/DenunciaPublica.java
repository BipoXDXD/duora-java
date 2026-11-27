package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.CodigoDenuncia;
import bipotheking.net.duora.conta.CodigoDenuncia.Campo;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Denúncia feita sem conta, já validada. Os tamanhos contam caracteres (code points), como o {@code
 * char_length} do banco. O {@code toString} só mostra o motivo: a descrição, a referência e o
 * e-mail de contato nunca vão para log nem mensagem de exceção.
 *
 * @param referencia apelido ou link de quem é denunciado, se houver
 * @param emailContato para responder a quem denunciou, se houver
 */
public record DenunciaPublica(
    MotivoDeDenunciaPublica motivo,
    String descricao,
    @Nullable String referencia,
    @Nullable Email emailContato) {

  private static final int DESCRICAO_MINIMA = 10;
  private static final int DESCRICAO_MAXIMA = 2000;
  private static final int REFERENCIA_MAXIMA = 200;

  /**
   * Valida o que chegou do formulário: sem espaços nas pontas, e opcional em branco vira ausente.
   *
   * @throws ErroDeNegocio {@code DENUNCIA_PUBLICA_INVALIDA}, com a mensagem do primeiro campo
   *     recusado
   */
  public static DenunciaPublica de(
      @Nullable String motivo,
      @Nullable String descricao,
      @Nullable String referencia,
      @Nullable String emailContato) {
    return new DenunciaPublica(
        motivo(motivo), descricao(descricao), referencia(referencia), emailContato(emailContato));
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

  private static String descricao(@Nullable String bruta) {
    String descricao = bruta == null ? "" : bruta.strip();
    int tamanho = caracteres(descricao);
    if (tamanho < DESCRICAO_MINIMA || tamanho > DESCRICAO_MAXIMA) {
      throw recusa(Campo.DESCRICAO);
    }
    return descricao;
  }

  private static @Nullable String referencia(@Nullable String bruta) {
    if (bruta == null || bruta.isBlank()) {
      return null;
    }
    String referencia = bruta.strip();
    if (caracteres(referencia) > REFERENCIA_MAXIMA) {
      throw recusa(Campo.REFERENCIA);
    }
    return referencia;
  }

  private static @Nullable Email emailContato(@Nullable String bruto) {
    if (bruto == null || bruto.isBlank()) {
      return null;
    }
    try {
      return Email.de(bruto);
    } catch (ErroDeNegocio emailInvalido) {
      throw recusa(Campo.EMAIL_CONTATO);
    }
  }

  private static int caracteres(String texto) {
    return texto.codePointCount(0, texto.length());
  }

  private static ErroDeNegocio recusa(Campo campo) {
    return CodigoDenuncia.DENUNCIA_PUBLICA_INVALIDA.erro(campo);
  }
}
