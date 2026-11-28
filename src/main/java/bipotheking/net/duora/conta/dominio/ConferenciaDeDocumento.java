package bipotheking.net.duora.conta.dominio;

/** Porta da conferência do documento (RG ou CNH) contra a selfie da prova de vida. */
public interface ConferenciaDeDocumento {

  /**
   * Lê o documento e compara o rosto dele com a selfie. Nenhuma imagem é guardada.
   *
   * @param frente frente do documento
   * @param verso verso do documento, sempre enviado
   * @param selfie imagem de referência da prova de vida
   * @throws FornecedorIndisponivel se o fornecedor falhar
   */
  ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie);
}
