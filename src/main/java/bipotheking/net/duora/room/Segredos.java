package bipotheking.net.duora.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração de tokens aleatórios e o resumo SHA-256 que o servidor guarda no lugar deles. */
final class Segredos {

  static final int BYTES_ASSENTO = 32;
  static final int BYTES_CONVITE = 16;
  private static final int BYTES_ID_SALA = 8;

  private static final SecureRandom ALEATORIO = new SecureRandom();
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private Segredos() {}

  static String novoToken(int bytes) {
    byte[] valor = new byte[bytes];
    ALEATORIO.nextBytes(valor);
    return BASE64_URL.encodeToString(valor);
  }

  /** Identificador de sala para logs; não dá acesso a nada. */
  static String novoIdDeSala() {
    byte[] valor = new byte[BYTES_ID_SALA];
    ALEATORIO.nextBytes(valor);
    return HexFormat.of().formatHex(valor);
  }

  static String resumo(String token) {
    return HexFormat.of().formatHex(sha256(token.getBytes(StandardCharsets.UTF_8)));
  }

  static byte[] sha256(byte[] dados) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(dados);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
    }
  }
}
