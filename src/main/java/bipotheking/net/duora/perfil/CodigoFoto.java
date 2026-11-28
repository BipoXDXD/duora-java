package bipotheking.net.duora.perfil;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas das fotos de perfil. */
public enum CodigoFoto implements CodigoDeErro {
  FOTO_INEXISTENTE(TipoDeErro.NAO_ENCONTRADO, "Foto inexistente", "Foto não encontrada.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoFoto(TipoDeErro tipo, String titulo, String mensagem) {
    this.tipo = tipo;
    this.titulo = titulo;
    this.mensagem = mensagem;
  }

  @Override
  public TipoDeErro tipo() {
    return tipo;
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
