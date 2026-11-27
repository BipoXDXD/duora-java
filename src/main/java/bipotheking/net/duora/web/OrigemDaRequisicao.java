package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.Origem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Lê a origem da conexão direta ({@code remoteAddr} e {@code remotePort}). Cabeçalhos como {@code
 * X-Forwarded-For} e {@code CF-Connecting-IP} são ignorados de propósito: qualquer cliente os
 * forja. Quando houver proxy confiável na frente, só esta classe muda.
 */
@Component
public class OrigemDaRequisicao {

  public Origem de(HttpServletRequest request) {
    return new Origem(request.getRemoteAddr(), request.getRemotePort());
  }
}
