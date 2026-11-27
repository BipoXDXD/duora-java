package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Códigos de erro do e-mail de uma conta. */
public enum CodigoEmail implements CodigoDeErro {
  EMAIL_INVALIDO(TipoDeErro.REGRA_DE_NEGOCIO, "E-mail inválido"),
  EMAIL_DESCARTAVEL(TipoDeErro.REGRA_DE_NEGOCIO, "E-mail descartável");

  private final TipoDeErro tipo;
  private final String titulo;

  CodigoEmail(TipoDeErro tipo, String titulo) {
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
