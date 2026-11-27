package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Email;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 da {@link Email#chaveDeBloqueio()} com a chave secreta do servidor. Guarda um e-mail
 * bloqueado sem guardar o e-mail, e sem permitir conferir um palpite sem a chave. Fica na raiz do
 * módulo, fora de {@code dominio/}, porque depende de {@code javax.crypto}.
 */
public final class ResumoDeEmail {

  private static final String ALGORITMO = "HmacSHA256";
  private static final int MINIMO_DE_BYTES_DA_CHAVE = 32;

  private final SecretKeySpec chave;

  public ResumoDeEmail(String segredo) {
    byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MINIMO_DE_BYTES_DA_CHAVE) {
      throw new IllegalArgumentException(
          "A chave do resumo de e-mail precisa de pelo menos 32 bytes");
    }
    this.chave = new SecretKeySpec(bytes, ALGORITMO);
  }

  public byte[] de(Email email) {
    try {
      Mac mac = Mac.getInstance(ALGORITMO);
      mac.init(chave);
      return mac.doFinal(email.chaveDeBloqueio().getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Toda JVM traz HmacSHA256", e);
    }
  }

  /** O resumo em hexadecimal minúsculo. */
  public String hex(Email email) {
    return HexFormat.of().formatHex(de(email));
  }
}
