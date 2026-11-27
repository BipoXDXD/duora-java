package bipotheking.net.duora.e2e;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import com.microsoft.playwright.options.Cookie;
import java.util.List;

/** Passos de conta das jornadas que precisam de alguém logado sem passar pelo link mágico. */
final class Contas {

  private Contas() {}

  /** Põe o cookie da sessão da conta no navegador da página, antes de ela abrir o site. */
  static TerracoPagina comConta(TerracoPagina pagina, ContaDeTeste conta, String raiz) {
    String[] cookie = conta.cookie().split("=", 2);
    pagina.page().context().addCookies(List.of(new Cookie(cookie[0], cookie[1]).setUrl(raiz)));
    return pagina;
  }
}
