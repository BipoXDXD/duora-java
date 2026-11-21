package bipotheking.net.duora.room;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cabeçalhos de segurança em toda resposta; a API nunca é guardada em cache. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CabecalhosDeSeguranca extends OncePerRequestFilter {

  static final String CSP =
      "default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; "
          + "frame-ancestors 'none'; form-action 'self'";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    if (MesmaOrigem.ehApi(request)) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
    chain.doFilter(request, response);
  }
}
