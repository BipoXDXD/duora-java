package bipotheking.net.duora.nucleo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Resumos criptográficos do texto em UTF-8. Funções puras. */
public final class Resumos {

  private Resumos() {}

  public static byte[] sha256(String texto) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Toda JVM traz SHA-256", e);
    }
  }

  /** SHA-256 em hexadecimal minúsculo. */
  public static String sha256Hex(String texto) {
    return HexFormat.of().formatHex(sha256(texto));
  }
}
