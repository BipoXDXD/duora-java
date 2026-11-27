package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas da confirmação do link mágico. */
public enum CodigoEntrada implements CodigoDeErro {
  /** Token ausente, vazio, vencido, usado, inexistente ou de e-mail bloqueado: a mesma resposta. */
  LINK_INVALIDO("Link inválido");

  private final String titulo;

  CodigoEntrada(String titulo) {
    this.titulo = titulo;
  }

  @Override
  public TipoDeErro tipo() {
    return TipoDeErro.REGRA_DE_NEGOCIO;
  }

  @Override
  public String titulo() {
    return titulo;
  }
}
