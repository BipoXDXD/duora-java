package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.Cadastro;
import bipotheking.net.duora.conta.CodigoCadastro;
import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.ResultadoDoCadastro;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro da conta da sessão, ainda {@code PENDENTE}. */
@RestController
@RequestMapping("/api/conta")
class CadastroController {

  private static final RespostaDeCadastro AGUARDANDO_VERIFICACAO =
      new RespostaDeCadastro(EstadoConta.AGUARDANDO_VERIFICACAO.cadastroConcluido(), "VERIFICACAO");

  private final Cadastro cadastro;
  private final CookieSessao cookie;

  CadastroController(Cadastro cadastro, CookieSessao cookie) {
    this.cadastro = cadastro;
    this.cookie = cookie;
  }

  /** Campo ausente chega nulo; o caso de uso recusa com o código do contrato. */
  record PedidoDeCadastroJson(
      @Nullable LocalDate dataDeNascimento,
      @Nullable String versaoTermos,
      @Nullable String versaoPrivacidade,
      @Nullable Boolean aceito) {}

  record RespostaDeCadastro(boolean cadastroConcluido, String proximoPasso) {}

  /**
   * Menor de idade recebe 422 com o cookie expirado: a conta já foi excluída e as sessões,
   * revogadas. O {@code setHeader} substitui um cookie que a renovação acabou de reemitir.
   */
  @PostMapping("/cadastro")
  ResponseEntity<RespostaDeCadastro> cadastrar(
      @ContaAtual(cadastroConcluido = false) ContaId conta,
      @RequestBody PedidoDeCadastroJson corpo,
      HttpServletResponse response) {
    ResultadoDoCadastro resultado =
        cadastro.concluir(
            conta,
            corpo.dataDeNascimento(),
            corpo.versaoTermos(),
            corpo.versaoPrivacidade(),
            Boolean.TRUE.equals(corpo.aceito()));
    return switch (resultado) {
      case ResultadoDoCadastro.Concluido _ -> ResponseEntity.ok(AGUARDANDO_VERIFICACAO);
      case ResultadoDoCadastro.RecusadoPorIdade _ -> {
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
        throw CodigoCadastro.MENOR_DE_IDADE.erro();
      }
    };
  }
}
