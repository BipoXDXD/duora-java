package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.CodigoLegal;
import bipotheking.net.duora.conta.DocumentoLegal;
import bipotheking.net.duora.conta.DocumentosLegais;
import bipotheking.net.duora.conta.EncarregadoDeDados;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API pública dos documentos legais, sem sessão. O {@code Cache-Control: no-store} vem do filtro de
 * {@code /api}: o texto muda de versão sem aviso e o cadastro precisa aceitar a atual.
 */
@RestController
@RequestMapping("/api/legal")
class LegalController {

  private final DocumentosLegais documentos;

  LegalController(DocumentosLegais documentos) {
    this.documentos = documentos;
  }

  @GetMapping("/versoes")
  Versoes versoes() {
    return new Versoes(
        new Versao(documentos.atual(DocumentoLegal.TERMOS).versao()),
        new Versao(documentos.atual(DocumentoLegal.PRIVACIDADE).versao()),
        documentos.contato(),
        documentos.encarregado());
  }

  @GetMapping("/{documento}")
  Documento documento(@PathVariable String documento) {
    DocumentoLegal legal =
        DocumentoLegal.doCaminho(documento)
            .orElseThrow(() ->
                new ErroDeNegocio(CodigoLegal.DOCUMENTO_INEXISTENTE, "Esse documento não existe."));
    return new Documento(documentos.atual(legal).versao(), documentos.html(legal));
  }

  record Versao(String versao) {}

  record Versoes(
      Versao termos, Versao privacidade, String contato, EncarregadoDeDados encarregado) {}

  record Documento(String versao, String html) {}
}
