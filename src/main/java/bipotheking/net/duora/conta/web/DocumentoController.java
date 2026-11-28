package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.CodigoCadastro;
import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.DesfechoDaVerificacao;
import bipotheking.net.duora.conta.VerificacaoPorDocumento;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Envio do documento na faixa de dúvida: {@code sessaoId} da sessão {@code DOCUMENTO}, frente e
 * verso num {@code multipart/form-data}. O multipart só é lido quando os parâmetros são resolvidos,
 * depois de o {@link ExigeSessao} conferir a sessão; as imagens ficam em memória e não saem desta
 * requisição.
 */
@RestController
@RequestMapping("/api/conta/verificacao")
class DocumentoController {

  private final VerificacaoPorDocumento verificacao;
  private final CookieSessao cookie;

  DocumentoController(VerificacaoPorDocumento verificacao, CookieSessao cookie) {
    this.verificacao = verificacao;
    this.cookie = cookie;
  }

  /**
   * Menor de idade recebe 422 com o cookie expirado: a conta já foi excluída e as sessões,
   * revogadas. O {@code setHeader} substitui um cookie que a renovação acabou de reemitir. Sem
   * {@code consumes}: outro {@code Content-Type} daria 415 no mapeamento, antes do 401 da sessão.
   */
  @PostMapping("/documento")
  DesfechoDaVerificacao enviar(
      @ContaAtual(cadastroConcluido = false) ContaId conta,
      @RequestParam String sessaoId,
      @RequestPart MultipartFile frente,
      @RequestPart MultipartFile verso,
      HttpServletResponse response)
      throws IOException {
    DesfechoDaVerificacao desfecho =
        verificacao.concluir(conta, sessaoId, frente.getBytes(), verso.getBytes());
    if (desfecho.resultado() == ResultadoDaVerificacao.RECUSADA) {
      response.setHeader(HttpHeaders.SET_COOKIE, cookie.expirar());
      throw CodigoCadastro.MENOR_DE_IDADE.erro();
    }
    return desfecho;
  }
}
