package bipotheking.net.duora.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Toda requisição, inclusive estáticos, precisa vir com {@code Host} de loopback e a porta local.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class HostLocal extends OncePerRequestFilter {

  private final JsonMapper json;

  HostLocal(JsonMapper json) {
    this.json = json;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!EnderecoLocal.permitido(
        request.getServerName(), request.getServerPort(), request.getLocalPort())) {
      RespostaDeProblema.escrever(
          response, json, CodigoWeb.HOST_RECUSADO, "Acesse pelo endereço local do servidor.");
      return;
    }
    chain.doFilter(request, response);
  }
}
