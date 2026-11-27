package bipotheking.net.duora.conta;

import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * IPs de origem para os testes de API. Os limites por IP valem para o {@code remoteAddr}; cada
 * requisição com um IP próprio não esbarra no limite de outra.
 */
public final class OrigemDeTeste {

  private OrigemDeTeste() {}

  /** Um {@code remoteAddr} {@code 10.x.y.z} tirado de um UUID aleatório. */
  public static RequestPostProcessor ipAleatorio() {
    long bits = UUID.randomUUID().getLeastSignificantBits();
    String ip = "10." + (bits & 0xFF) + "." + ((bits >> 8) & 0xFF) + "." + ((bits >> 16) & 0xFF);
    return ip(ip);
  }

  /** Um {@code remoteAddr} fixo. */
  public static RequestPostProcessor ip(String ip) {
    return requisicao -> {
      requisicao.setRemoteAddr(ip);
      return requisicao;
    };
  }
}
