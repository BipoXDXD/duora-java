package bipotheking.net.duora.perfil;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas da localização. A mensagem nunca cita a coordenada recebida. */
public enum CodigoLocalizacao implements CodigoDeErro {
  COORDENADA_INVALIDA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Coordenada inválida",
      "Não conseguimos usar essa localização. Tente de novo."),
  LOCALIZACAO_SEM_CONSENTIMENTO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Localização sem consentimento",
      "Ative a localização antes de atualizá-la.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoLocalizacao(TipoDeErro tipo, String titulo, String mensagem) {
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
