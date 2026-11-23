package bipotheking.net.duora.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança em toda resposta. A API nunca é guardada em cache; o index.html, o
 * service worker e o manifest são revalidados a cada busca, para uma versão nova chegar no próximo
 * carregamento. O cache imutável dos bundles de /app/ fica em {@link BundlesComHash}, que só o põe
 * quando o arquivo existe.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class CabecalhosDeSeguranca extends OncePerRequestFilter {

  public static final String CSP =
      "default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; "
          + "frame-ancestors 'none'; form-action 'self'";

  private static final Set<String> SEMPRE_REVALIDADOS =
      Set.of("/", "/index.html", "/sw.js", "/manifest.webmanifest");

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
    } else if (SEMPRE_REVALIDADOS.contains(request.getRequestURI())) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    }
    chain.doFilter(request, response);
  }
}
