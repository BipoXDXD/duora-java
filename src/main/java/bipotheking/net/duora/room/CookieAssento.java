package bipotheking.net.duora.room;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/** Cookie que carrega o token de assento: só o servidor lê, só a API recebe. */
final class CookieAssento {

  static final String NOME = "duora_assento";
  private static final String CAMINHO = "/api";

  private CookieAssento() {}

  static String emitir(String token) {
    return base(token).build().toString();
  }

  static String expirar() {
    return base("").maxAge(Duration.ZERO).build().toString();
  }

  private static ResponseCookie.ResponseCookieBuilder base(String valor) {
    return ResponseCookie.from(NOME, valor).httpOnly(true).sameSite("Strict").path(CAMINHO);
  }
}
