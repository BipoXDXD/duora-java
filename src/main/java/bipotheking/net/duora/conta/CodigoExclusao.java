package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas da exclusão de conta. Cada código traz a mensagem do contrato. */
public enum CodigoExclusao implements CodigoDeErro {
  CONFIRMACAO_NECESSARIA(
      "Confirmação necessária", "Confirme que entende que a exclusão apaga seus dados.");

  private final String titulo;
  private final String mensagem;

  CodigoExclusao(String titulo, String mensagem) {
    this.titulo = titulo;
    this.mensagem = mensagem;
  }

  @Override
  public TipoDeErro tipo() {
    return TipoDeErro.REGRA_DE_NEGOCIO;
  }

  @Override
  public String titulo() {
    return titulo;
  }

  /** O texto para quem usa o app. */
  public String mensagem() {
    return mensagem;
  }

  /** A recusa com a mensagem do contrato. */
  public ErroDeNegocio erro() {
    return new ErroDeNegocio(this, mensagem);
  }
}
