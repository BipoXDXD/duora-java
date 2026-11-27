package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Códigos de erro dos documentos legais. */
public enum CodigoLegal implements CodigoDeErro {
  DOCUMENTO_INEXISTENTE(TipoDeErro.NAO_ENCONTRADO, "Documento inexistente");

  private final TipoDeErro tipo;
  private final String titulo;

  CodigoLegal(TipoDeErro tipo, String titulo) {
    this.tipo = tipo;
    this.titulo = titulo;
  }

  @Override
  public TipoDeErro tipo() {
    return tipo;
  }

  @Override
  public String titulo() {
    return titulo;
  }
}
