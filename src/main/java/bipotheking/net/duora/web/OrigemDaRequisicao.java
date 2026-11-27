package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.Origem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Lê a origem da conexão direta ({@code remoteAddr} e {@code remotePort}). Cabeçalhos como {@code
 * X-Forwarded-For} e {@code CF-Connecting-IP} são ignorados de propósito: qualquer cliente os
 * forja. Quando houver proxy confiável na frente, só esta classe muda.
 *
 * <p>O zone id de um IPv6 ({@code fe80::1%en0}) sai: o {@code inet} do Postgres não o aceita, e ele
 * só diz por qual interface local a conexão chegou.
 */
@Component
public class OrigemDaRequisicao {

  public Origem de(HttpServletRequest request) {
    return new Origem(semZoneId(request.getRemoteAddr()), request.getRemotePort());
  }

  private static String semZoneId(String ip) {
    int zona = ip.indexOf('%');
    return zona < 0 ? ip : ip.substring(0, zona);
  }
}
