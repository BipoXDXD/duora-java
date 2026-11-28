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
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proteção contra CSRF: toda mutação na API exige {@code X-Duora: 1}; {@code Origin}, se veio, tem
 * de ser a de loopback na porta local ou exatamente a da URL pública ({@link EnderecoPublico}:
 * esquema, host e porta efetiva, sem prefixo, subdomínio nem {@code null}); {@code Sec-Fetch-Site},
 * se veio, tem de ser same-origin. O proxy precisa repassar o {@code Origin} do navegador sem
 * reescrever, ou esta checagem perde o efeito.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class MesmaOrigem extends OncePerRequestFilter {

  public static final String CABECALHO = "X-Duora";
  public static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
  private static final String VALOR_ESPERADO = "1";
  private static final String MESMA_ORIGEM = "same-origin";
  private static final PathPattern API = PathPatternParser.defaultInstance.parse("/api/**");
  private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS");

  private final JsonMapper json;
  private final EnderecoPublico publico;

  MesmaOrigem(JsonMapper json, EnderecoPublico publico) {
    this.json = json;
    this.publico = publico;
  }

  /**
   * Casa o caminho como o handler mapping o vê (decodificado, sem parâmetros de segmento), não o
   * texto cru: {@code /%61pi/salas} e {@code /api;x/salas} também são a API.
   */
  static boolean ehApi(HttpServletRequest request) {
    return API.matches(
        RequestPath.parse(request.getRequestURI(), request.getContextPath())
            .pathWithinApplication());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (ehApi(request) && !METODOS_SEGUROS.contains(request.getMethod()) && !aceita(request)) {
      RespostaDeProblema.escrever(
          response, json, CodigoWeb.ORIGEM_RECUSADA, "Pedido recusado: origem não confirmada.");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean aceita(HttpServletRequest request) {
    String origem = request.getHeader(HttpHeaders.ORIGIN);
    String site = request.getHeader(SEC_FETCH_SITE);
    return VALOR_ESPERADO.equals(request.getHeader(CABECALHO))
        && (origem == null
            || EnderecoLocal.origemPermitida(origem, request.getLocalPort())
            || publico.origemPermitida(origem))
        && (site == null || MESMA_ORIGEM.equals(site));
  }
}
