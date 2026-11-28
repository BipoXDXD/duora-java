package bipotheking.net.duora.nucleo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Resumos criptográficos do texto em UTF-8 ou de bytes crus. Funções puras. */
public final class Resumos {

  private Resumos() {}

  public static byte[] sha256(String texto) {
    return sha256(texto.getBytes(StandardCharsets.UTF_8));
  }

  public static byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Toda JVM traz SHA-256", e);
    }
  }

  /** SHA-256 em hexadecimal minúsculo. */
  public static String sha256Hex(String texto) {
    return HexFormat.of().formatHex(sha256(texto));
  }

  /** SHA-256 dos bytes como vieram, em hexadecimal minúsculo (impressão de um arquivo enviado). */
  public static String sha256Hex(byte[] bytes) {
    return HexFormat.of().formatHex(sha256(bytes));
  }
}
