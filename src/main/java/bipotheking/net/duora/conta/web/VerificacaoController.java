package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.CodigoCadastro;
import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.DesfechoDaVerificacao;
import bipotheking.net.duora.conta.EstadoDaVerificacao;
import bipotheking.net.duora.conta.SessaoDeCaptura;
import bipotheking.net.duora.conta.SituacaoDaVerificacao;
import bipotheking.net.duora.conta.VerificacaoDeIdade;
import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificação de idade da conta da sessão, antes da ativação: situação, abertura da sessão de prova
 * de vida e resultado da selfie. O {@code Cache-Control: no-store} vem do filtro de cabeçalhos.
 */
@RestController
@RequestMapping("/api/conta/verificacao")
class VerificacaoController {

  private final VerificacaoDeIdade verificacao;
  private final CookieSessao cookie;
  private final Clock relogio;

  VerificacaoController(VerificacaoDeIdade verificacao, CookieSessao cookie, Clock relogio) {
    this.verificacao = verificacao;
    this.cookie = cookie;
    this.relogio = relogio;
  }

  /**
   * Corpo do pedido de sessão; campo extra recusa o corpo inteiro. Campo ausente chega nulo:
   * consentimento ou versão ausente é recusado pelo caso de uso; finalidade ausente vira {@code
   * SELFIE}. A versão é a do texto do consentimento que o front exibiu.
   */
  record PedidoDeSessaoJson(
      @Nullable Boolean consentimentoBiometrico,
      @Nullable String versaoConsentimento,
      @Nullable FinalidadeDaTentativa finalidade) {

    FinalidadeDaTentativa finalidadeOuSelfie() {
      return finalidade == null ? FinalidadeDaTentativa.SELFIE : finalidade;
    }
  }

  record SituacaoJson(
      EstadoDaVerificacao estado,
      int tentativasRestantesHoje,
      int enviosDeDocumentoRestantesHoje,
      String modo) {}

  record SessaoJson(String sessaoId, String regiao, String modo, CredencialJson credencial) {}

  /**
   * A credencial vai para o navegador, nunca para o log: o {@code toString} só mostra o {@code
   * accessKeyId} e a validade. {@code expiraEm} sai em ISO-8601, como o front espera.
   */
  record CredencialJson(
      String accessKeyId, String secretAccessKey, String sessionToken, String expiraEm) {

    static CredencialJson de(CredencialCurta credencial) {
      return new CredencialJson(
          credencial.accessKeyId(),
          credencial.secretAccessKey(),
          credencial.sessionToken(),
          credencial.expiraEm().toString());
    }

    @Override
    public String toString() {
      return "CredencialJson[accessKeyId=" + accessKeyId + ", expiraEm=" + expiraEm + "]";
    }
  }

  @GetMapping
  SituacaoJson situacao(@ContaAtual(cadastroConcluido = false) ContaId conta) {
    SituacaoDaVerificacao situacao = verificacao.situacao(conta);
    return new SituacaoJson(
        situacao.estado(),
        situacao.tentativasRestantesHoje(),
        situacao.enviosDeDocumentoRestantesHoje(),
        modo(situacao.modo()));
  }

  /**
   * Abre a sessão de captura. O cabeçalho {@code Date} vem do relógio da aplicação: o front mede
   * com ele o desvio do relógio do aparelho, que a assinatura da AWS não tolera.
   */
  @PostMapping("/sessao")
  ResponseEntity<SessaoJson> iniciar(
      @ContaAtual(cadastroConcluido = false) ContaId conta, @RequestBody PedidoDeSessaoJson corpo) {
    SessaoDeCaptura sessao =
        verificacao.iniciar(
            conta,
            corpo.finalidadeOuSelfie(),
            Boolean.TRUE.equals(corpo.consentimentoBiometrico()),
            corpo.versaoConsentimento());
    HttpHeaders cabecalhos = new HttpHeaders();
    cabecalhos.setDate(relogio.millis());
    return ResponseEntity.status(HttpStatus.CREATED)
        .headers(cabecalhos)
        .body(
            new SessaoJson(
                sessao.sessaoId(),
                sessao.regiao(),
                modo(sessao.modo()),
                CredencialJson.de(sessao.credencial())));
  }

  /**
   * Menor de idade recebe 422 com o cookie expirado: a conta já foi excluída e as sessões,
   * revogadas. O {@code setHeader} substitui um cookie que a renovação acabou de reemitir.
   */
  @PostMapping("/sessao/{sessaoId}/resultado")
  DesfechoDaVerificacao concluirSelfie(
      @ContaAtual(cadastroConcluido = false) ContaId conta,
      @PathVariable String sessaoId,
      HttpServletResponse response) {
    DesfechoDaVerificacao desfecho = verificacao.concluirSelfie(conta, sessaoId);
    if (desfecho.resultado() == ResultadoDaVerificacao.RECUSADA) {
      response.setHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
      throw CodigoCadastro.MENOR_DE_IDADE.erro();
    }
    return desfecho;
  }

  private static String modo(ModoDeCaptura modo) {
    return modo.name().toLowerCase(Locale.ROOT);
  }
}
