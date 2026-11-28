package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.DocumentoLegal;
import bipotheking.net.duora.conta.VersaoDeDocumento;
import java.time.Instant;

/** Porta dos aceites dos documentos legais, um por conta, documento e versão. */
public interface RepositorioDeAceites {

  /** Grava o aceite; o da mesma conta, documento e versão que já existe fica como está. */
  void gravar(ContaId conta, DocumentoLegal documento, VersaoDeDocumento versao, Instant agora);

  /**
   * Apaga todos os aceites da conta.
   *
   * @return quantos apagou
   */
  int apagarDaConta(ContaId conta);
}
