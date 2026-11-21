package bipotheking.net.duora.room;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * Proteção contra CSRF: toda mutação na API exige {@code X-Duora: 1}; {@code Origin}, se veio, tem
 * de ser de loopback na porta local; {@code Sec-Fetch-Site}, se veio, tem de ser same-origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class MesmaOrigem extends OncePerRequestFilter {

  static final String CABECALHO = "X-Duora";
  static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
  private static final String VALOR_ESPERADO = "1";
  private static final String MESMA_ORIGEM = "same-origin";
  private static final String PREFIXO_API = "/api/";
  private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

  private final JsonMapper json;

  MesmaOrigem(JsonMapper json) {
    this.json = json;
  }

  static boolean ehApi(HttpServletRequest request) {
    return request.getRequestURI().startsWith(PREFIXO_API);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (ehApi(request) && !METODOS_SEGUROS.contains(request.getMethod()) && !aceita(request)) {
      RespostaDeProblema.escrever(
          response, json, Codigo.ORIGEM_RECUSADA, "Pedido recusado: origem não confirmada.");
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean aceita(HttpServletRequest request) {
    String origem = request.getHeader(HttpHeaders.ORIGIN);
    String site = request.getHeader(SEC_FETCH_SITE);
    return VALOR_ESPERADO.equals(request.getHeader(CABECALHO))
        && (origem == null || EnderecoLocal.origemPermitida(origem, request.getLocalPort()))
        && (site == null || MESMA_ORIGEM.equals(site));
  }
}
