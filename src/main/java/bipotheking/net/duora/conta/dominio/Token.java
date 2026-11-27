package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.nucleo.Resumos;
import java.util.Base64;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Segredo de 32 bytes em base64url sem padding (43 caracteres). Quem o recebe guarda só o {@link
 * #resumo()}; o {@code toString} não mostra o valor.
 */
public final class Token {

  private static final int BYTES = 32;
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private final String valor;

  private Token(String valor) {
    this.valor = valor;
  }

  /** Token novo com 32 bytes do gerador, que precisa ser criptográfico (o bean da plataforma). */
  public static Token novo(RandomGenerator aleatorio) {
    byte[] bytes = new byte[BYTES];
    aleatorio.nextBytes(bytes);
    return new Token(BASE64URL.encodeToString(bytes));
  }

  /**
   * Token recebido de fora (cookie, link). Não confere o formato: um valor forjado só não acha
   * resumo igual no banco.
   */
  public static Token de(String valor) {
    return new Token(Objects.requireNonNull(valor, "valor"));
  }

  public String valor() {
    return valor;
  }

  /** SHA-256 do valor: o que o banco guarda. */
  public byte[] resumo() {
    return Resumos.sha256(valor);
  }

  @Override
  public String toString() {
    return "Token[***]";
  }
}
