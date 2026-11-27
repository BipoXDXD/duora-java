package bipotheking.net.duora.conta.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie {@code duora_sessao}: só o servidor lê ({@code HttpOnly}), vale para o site todo e não
 * viaja em requisição de outro site, salvo navegação ({@code SameSite=Lax}). Perde o {@code Secure}
 * só com {@code duora.sessao.cookie-seguro=false}.
 */
@Component
class CookieSessao {

  static final String NOME = "duora_sessao";
  private static final Duration DURACAO = Duration.ofDays(30);

  private final boolean seguro;

  /** Em {@code prod} o cookie sem {@code Secure} derruba o boot: ele viajaria em http aberto. */
  CookieSessao(@Value("${duora.sessao.cookie-seguro:true}") boolean seguro, Environment ambiente) {
    if (!seguro && ambiente.matchesProfiles("prod")) {
      throw new IllegalStateException(
          "duora.sessao.cookie-seguro=false não é aceito no perfil prod");
    }
    this.seguro = seguro;
  }

  /** O valor do cabeçalho {@code Set-Cookie} que grava o token por 30 dias. */
  String emitir(String token) {
    return base(token).maxAge(DURACAO).build().toString();
  }

  /** O valor do cabeçalho {@code Set-Cookie} que apaga o cookie no navegador. */
  String expirar() {
    return base("").maxAge(Duration.ZERO).build().toString();
  }

  /** O token do cookie; ausente ou vazio dá vazio. */
  Optional<String> ler(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> NOME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(valor -> valor != null && !valor.isEmpty())
        .findFirst();
  }

  private ResponseCookie.ResponseCookieBuilder base(String valor) {
    return ResponseCookie.from(NOME, valor).httpOnly(true).secure(seguro).sameSite("Lax").path("/");
  }
}
