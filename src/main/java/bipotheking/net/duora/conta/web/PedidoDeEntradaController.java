package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.PedidoDeEntrada;
import bipotheking.net.duora.conta.PedidoDeEntrada.RespostaDeEntrada;
import bipotheking.net.duora.nucleo.EmailMascarado;
import bipotheking.net.duora.web.OrigemDaRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas públicas da entrada: o pedido de link mágico e a chave do site do Turnstile. */
@RestController
@RequestMapping("/api/conta")
class PedidoDeEntradaController {

  private static final String CHAVE_DE_IDEMPOTENCIA = "Idempotency-Key";

  private final PedidoDeEntrada pedidoDeEntrada;
  private final OrigemDaRequisicao origem;
  private final String chaveDoSiteTurnstile;

  PedidoDeEntradaController(
      PedidoDeEntrada pedidoDeEntrada,
      OrigemDaRequisicao origem,
      @Value("${duora.turnstile.chave-do-site}") String chaveDoSiteTurnstile) {
    this.pedidoDeEntrada = pedidoDeEntrada;
    this.origem = origem;
    this.chaveDoSiteTurnstile = chaveDoSiteTurnstile;
  }

  /**
   * O Spring MVC registra o corpo em DEBUG: o {@code toString} mascara o e-mail e esconde o token.
   */
  record PedidoDeEntradaJson(@Nullable String email, @Nullable String tokenVerificacao) {

    @Override
    public String toString() {
      return "PedidoDeEntradaJson[email="
          + EmailMascarado.mascarar(email)
          + ", tokenVerificacao=***]";
    }
  }

  record Configuracao(String chaveDoSiteTurnstile) {}

  @PostMapping("/entrar")
  @ResponseStatus(HttpStatus.ACCEPTED)
  RespostaDeEntrada entrar(
      @RequestBody PedidoDeEntradaJson corpo,
      @RequestHeader(name = CHAVE_DE_IDEMPOTENCIA, required = false) @Nullable String chave,
      HttpServletRequest request) {
    return pedidoDeEntrada.pedir(
        corpo.email(),
        corpo.tokenVerificacao(),
        origem.de(request).ip(),
        Optional.ofNullable(chave));
  }

  /** A chave do site é pública: vai no widget do Turnstile, no navegador. */
  @GetMapping("/configuracao")
  Configuracao configuracao() {
    return new Configuracao(chaveDoSiteTurnstile);
  }
}
