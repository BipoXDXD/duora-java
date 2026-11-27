package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.ConfirmacaoDeEntrada;
import bipotheking.net.duora.conta.ConfirmacaoDeEntrada.ResultadoDaConfirmacao;
import bipotheking.net.duora.web.OrigemDaRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota pública que troca o token do link mágico por uma sessão. Só {@code POST}: abrir o link, ou
 * um leitor de e-mail que o pré-carrega, não gasta o token, que viaja no fragmento.
 */
@RestController
@RequestMapping("/api/conta")
class ConfirmacaoController {

  private final ConfirmacaoDeEntrada confirmacao;
  private final CookieSessao cookie;
  private final OrigemDaRequisicao origem;

  ConfirmacaoController(
      ConfirmacaoDeEntrada confirmacao, CookieSessao cookie, OrigemDaRequisicao origem) {
    this.confirmacao = confirmacao;
    this.cookie = cookie;
    this.origem = origem;
  }

  record PedidoDeConfirmacaoJson(@Nullable String token) {}

  record RespostaDeConfirmacao(boolean cadastroConcluido) {}

  /** O cookie de sessão que vier junto é o da sessão anterior deste navegador, que se encerra. */
  @PostMapping("/confirmar")
  ResponseEntity<RespostaDeConfirmacao> confirmar(
      @RequestBody PedidoDeConfirmacaoJson corpo, HttpServletRequest request) {
    ResultadoDaConfirmacao resultado =
        confirmacao.confirmar(corpo.token(), origem.de(request), cookie.ler(request));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.emitir(resultado.tokenDaSessao()))
        .body(new RespostaDeConfirmacao(resultado.cadastroConcluido()));
  }
}
