package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Primeiro filtro da cadeia: dá a toda requisição um correlation ID, inclusive às recusadas pelos
 * filtros seguintes. Aceita o {@code X-Request-Id} recebido se for seguro para log; senão gera um
 * UUID v7. O id vai para o MDC durante a requisição e volta no cabeçalho da resposta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdDeCorrelacao extends OncePerRequestFilter {

  public static final String CABECALHO = "X-Request-Id";
  private static final Pattern VALOR_ACEITO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

  private final GeradorDeIds ids;

  IdDeCorrelacao(GeradorDeIds ids) {
    this.ids = ids;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = idDe(request);
    response.setHeader(CABECALHO, id);
    MDC.put(Correlacao.CHAVE_MDC, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(Correlacao.CHAVE_MDC);
    }
  }

  private String idDe(HttpServletRequest request) {
    String recebido = request.getHeader(CABECALHO);
    if (recebido != null && VALOR_ACEITO.matcher(recebido).matches()) {
      return recebido;
    }
    return ids.novo().toString();
  }
}
