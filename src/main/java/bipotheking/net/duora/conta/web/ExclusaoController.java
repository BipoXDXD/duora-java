package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ExclusaoDeConta;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exclusão da conta da sessão, em qualquer estado disponível. */
@RestController
@RequestMapping("/api/conta")
class ExclusaoController {

  private final ExclusaoDeConta exclusao;
  private final CookieSessao cookie;

  ExclusaoController(ExclusaoDeConta exclusao, CookieSessao cookie) {
    this.exclusao = exclusao;
    this.cookie = cookie;
  }

  /** Campo ausente chega nulo; o caso de uso recusa com o código do contrato. */
  record PedidoDeExclusaoJson(@Nullable Boolean confirmo) {}

  /**
   * Exclui a conta e expira o cookie: as sessões já foram revogadas. O {@code setHeader} substitui
   * um cookie que a renovação acabou de reemitir nesta mesma requisição.
   */
  @PostMapping("/exclusao")
  ResponseEntity<Void> excluir(
      @ContaAtual(cadastroConcluido = false) ContaId conta,
      @RequestBody PedidoDeExclusaoJson corpo,
      HttpServletResponse response) {
    exclusao.excluir(conta, Boolean.TRUE.equals(corpo.confirmo()));
    response.setHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
    return ResponseEntity.noContent().build();
  }
}
