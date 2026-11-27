package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.SessaoAtiva;
import bipotheking.net.duora.conta.Sessoes;
import bipotheking.net.duora.web.OrigemDaRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Único ponto que valida o cookie de sessão de uma requisição e mantém o navegador em dia: apaga o
 * cookie que não vale mais e reemite o que a renovação deslizante estendeu.
 */
@Component
class SessaoDaRequisicao {

  private final CookieSessao cookie;
  private final Sessoes sessoes;
  private final OrigemDaRequisicao origem;

  SessaoDaRequisicao(CookieSessao cookie, Sessoes sessoes, OrigemDaRequisicao origem) {
    this.cookie = cookie;
    this.sessoes = sessoes;
    this.origem = origem;
  }

  /**
   * A sessão do cookie. Sem cookie: vazio, sem tocar na resposta. Cookie inválido ou vencido: vazio
   * e {@code Set-Cookie} expirado. Sessão renovada agora: o cookie sai de novo com 30 dias, e a
   * origem da requisição fica no registro de acesso.
   */
  Optional<SessaoAtiva> ler(HttpServletRequest request, HttpServletResponse response) {
    Optional<String> token = cookie.ler(request);
    if (token.isEmpty()) {
      return Optional.empty();
    }
    Optional<SessaoAtiva> sessao = sessoes.validar(token.get(), origem.de(request));
    if (sessao.isEmpty()) {
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
    } else if (sessao.get().renovada()) {
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.emitir(token.get()));
    }
    return sessao;
  }
}
