package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.SessaoAtiva;
import bipotheking.net.duora.conta.Sessoes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A conta da sessão, como o front a vê, e o logout. */
@RestController
@RequestMapping("/api/conta")
class ContaController {

  private final SessaoDaRequisicao sessaoDaRequisicao;
  private final CookieSessao cookie;
  private final Sessoes sessoes;
  private final ConsultaDeContas contas;

  ContaController(
      SessaoDaRequisicao sessaoDaRequisicao,
      CookieSessao cookie,
      Sessoes sessoes,
      ConsultaDeContas contas) {
    this.sessaoDaRequisicao = sessaoDaRequisicao;
    this.cookie = cookie;
    this.sessoes = sessoes;
    this.contas = contas;
  }

  /** O que o front lê da conta: exatamente estes três campos. */
  record EstadoDaConta(EstadoConta estado, boolean cadastroConcluido, boolean verificada) {}

  /**
   * Rota pública com sessão opcional: o terraço a chama a cada carga, então anônimo recebe 204, não
   * erro. Conta indisponível recebe 403; nunca 401.
   */
  @GetMapping
  ResponseEntity<EstadoDaConta> consultar(
      HttpServletRequest request, HttpServletResponse response) {
    Optional<SessaoAtiva> sessao = sessaoDaRequisicao.ler(request, response);
    if (sessao.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    EstadoConta estado = sessao.get().estado();
    if (!estado.disponivel()) {
      throw ExigeSessao.recusa(CodigoSessao.CONTA_INDISPONIVEL);
    }
    boolean verificada =
        contas.porId(sessao.get().conta()).map(ContaResumo::verificada).orElse(false);
    return ResponseEntity.ok(new EstadoDaConta(estado, estado.cadastroConcluido(), verificada));
  }

  /**
   * Apaga a sessão e o cookie. O {@code setHeader} substitui um cookie que a renovação acabou de
   * reemitir nesta mesma requisição.
   */
  @PostMapping("/sair")
  ResponseEntity<Void> sair(
      @ContaAtual(cadastroConcluido = false) ContaId conta,
      HttpServletRequest request,
      HttpServletResponse response) {
    cookie.ler(request).ifPresent(token -> sessoes.sair(conta, token));
    response.setHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
    return ResponseEntity.noContent().build();
  }
}
