package bipotheking.net.duora.web;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * Toda requisição, inclusive estáticos, precisa vir com um {@code Host} conhecido: o de loopback na
 * porta local (dev, testes e o {@code HEALTHCHECK} da imagem em {@code 127.0.0.1:8080}) ou o da URL
 * pública ({@link EnderecoPublico}), que o Traefik repassa sem reescrever. Barra DNS rebinding: um
 * domínio estrangeiro que resolva para este servidor chega com o próprio nome no {@code Host}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class HostLocal extends OncePerRequestFilter {

  private final JsonMapper json;
  private final EnderecoPublico publico;

  HostLocal(JsonMapper json, EnderecoPublico publico) {
    this.json = json;
    this.publico = publico;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!EnderecoLocal.permitido(
            request.getServerName(), request.getServerPort(), request.getLocalPort())
        && !publico.hostPermitido(request.getHeader(HttpHeaders.HOST))) {
      RespostaDeProblema.escrever(
          response, json, CodigoWeb.HOST_RECUSADO, "Acesse pelo endereço do site.");
      return;
    }
    chain.doFilter(request, response);
  }
}
